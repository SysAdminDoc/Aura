package com.freevibe.service

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.freevibe.R
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.SCHEDULER_DAY_NIGHT_MODE_CLOCK
import com.freevibe.data.local.SCHEDULER_DAY_NIGHT_MODE_SYSTEM_THEME
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.WALLPAPER_SOURCE_LOCAL_FOLDER
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.rotationPreset
import com.freevibe.data.model.stableKey
import com.freevibe.data.remote.toWallpaper
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.RotationExclusionRepository
import com.freevibe.data.repository.WallpaperRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoWallpaperWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val wallpaperRepo: WallpaperRepository,
    private val redditRepo: RedditRepository,
    private val favoritesRepo: FavoritesRepository,
    private val collectionRepo: CollectionRepository,
    private val rotationExclusions: RotationExclusionRepository,
    private val wallpaperApplier: WallpaperApplier,
    private val historyManager: WallpaperHistoryManager,
    private val prefs: PreferencesManager,
    private val localWallpaperCatalog: LocalWallpaperCatalog,
    private val receiptStore: BackgroundWorkReceiptStore,
    private val prefetch: RotationPrefetchCache,
    private val network: RotationNetworkGate,
) : CoroutineWorker(appContext, workerParams) {

    /** True once this run changed a wallpaper; a disabled provider returns success without one. */
    private var appliedWallpaper = false

    /** Why this run stopped short of its source: nothing was saved ahead and no connection it may use. */
    private var networkHold: RotationNetworkAccess? = null

    /** True when this run looked for a wallpaper saved ahead of time and had none it could use. */
    private var prefetchMissed = false

    override suspend fun doWork(): Result = rotationRunLock.withLock { runRotation() }

    private suspend fun runRotation(): Result {
        val receiptWorkName = inputData.getString(RECEIPT_WORK_NAME_KEY) ?: WORK_NAME
        return try {
            val schedulerEnabled = prefs.schedulerEnabled.first()
            val legacyEnabled = prefs.autoWallpaperEnabled.first()
            val triggeredRotation = inputData.getBoolean(TRIGGERED_ROTATION_KEY, false)
            val catchUp = inputData.getBoolean(CATCH_UP_KEY, false)

            val result = try {
                when {
                    schedulerEnabled -> doSchedulerWork()
                    shouldRunLegacyRotation(schedulerEnabled, legacyEnabled, triggeredRotation) -> doLegacyWork()
                    else -> Result.success()
                }.also { result ->
                    // An apply that finished counts even if a tap replaced this run meanwhile.
                    withContext(NonCancellable) {
                        val hold = networkHold
                        if (hold != null) {
                            // Waiting for a connection is neither a failure nor a rotation.
                            receiptStore.recordDeferred(receiptWorkName, rotationHoldReason(hold))
                        } else {
                            receiptStore.recordWorkerResult(
                                uniqueWorkName = receiptWorkName,
                                resultClassName = result.javaClass.simpleName,
                                retryReason = "wallpaper source returned no usable item or apply failed; check selected source, saved collection, and wallpaper permission",
                            )
                        }
                    }
                }
            } catch (excluded: AllRotationCandidatesExcludedException) {
                receiptStore.recordFailure(
                    uniqueWorkName = receiptWorkName,
                    errorClass = "AllRotationCandidatesExcluded",
                    deferralReason = "Every item in ${excluded.sourceLabel} is excluded from rotation. Restore one in Settings, Rotation exclusions.",
                )
                // A persistent user choice is not transient work failure. Returning
                // success prevents WorkManager from retrying the same empty pool.
                Result.success()
            }
            val hold = networkHold
            if (hold != null) {
                withContext(NonCancellable) { handOffHeldRotation(hold, receiptWorkName, triggeredRotation, catchUp) }
            } else if (appliedWallpaper && !catchUp) {
                // This change stands in for a rotation that was still waiting on a connection.
                cancelCatchUp(applicationContext)
            }
            // One-shot work runs under its own unique name, so re-enqueueing the
            // periodic work here cannot cancel this run. A run that changed home but
            // found every lock item excluded still changed the wallpaper.
            if (shouldRestartRotationCountdown(
                    requested = inputData.getBoolean(RESTART_COUNTDOWN_KEY, false),
                    // Result.Success is restricted to the WorkManager library, so compare classes.
                    succeeded = result.javaClass == Result.success().javaClass,
                    applied = appliedWallpaper,
                )
            ) {
                withContext(NonCancellable) { restartCountdownAfterManualChange(applicationContext, prefs) }
            }
            result
        } catch (_: java.io.IOException) {
            receiptStore.recordRetry(
                uniqueWorkName = receiptWorkName,
                errorClass = "IOException",
                deferralReason = "network or remote wallpaper source I/O failed; check connection and provider availability",
            )
            Result.retry()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            receiptStore.recordFailure(
                uniqueWorkName = receiptWorkName,
                errorClass = e.javaClass.simpleName,
                deferralReason = "wallpaper rotation worker crashed before completing; include diagnostics bundle",
            )
            Result.failure()
        }
    }

    /** Enhanced scheduler with separate home/lock, collections, day/night */
    private suspend fun doSchedulerWork(): Result {
        val homeEnabled = prefs.schedulerHomeEnabled.first()
        val lockEnabled = prefs.schedulerLockEnabled.first()
        val shuffle = prefs.schedulerShuffle.first()

        val defaultSource = prefs.schedulerSource.first()
        val daySource = prefs.schedulerDaySource.first()
        val nightSource = prefs.schedulerNightSource.first()
        val source = resolveScheduledWallpaperSource(
            defaultSource = defaultSource,
            daySource = daySource,
            nightSource = nightSource,
            mode = prefs.schedulerDayNightMode.first(),
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            dayStartHour = prefs.schedulerDayStartHour.first(),
            nightStartHour = prefs.schedulerNightStartHour.first(),
            isSystemDark = applicationContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
        ).normalizeWallpaperRotationSource()

        if (source == "reddit" && !prefs.redditProviderEnabled.first()) return Result.success()
        if (source == "wallhaven" && !prefs.wallhavenProviderEnabled.first()) return Result.success()
        if (source == "pixabay" && !prefs.pixabayProviderEnabled.first()) return Result.success()
        if (source == "bing" && !prefs.bingProviderEnabled.first()) return Result.success()

        val target = when {
            homeEnabled && !lockEnabled -> WallpaperTarget.HOME
            lockEnabled && !homeEnabled -> WallpaperTarget.LOCK
            else -> null
        }
        if (source == WALLPAPER_SOURCE_LOCAL_FOLDER && homeEnabled && lockEnabled) {
            val homePick = pickLocalScheduledWallpaper(WallpaperTarget.HOME, shuffle)
            val lockPick = pickLocalScheduledWallpaper(WallpaperTarget.LOCK, shuffle)
            if (homePick.wallpaper == null && lockPick.wallpaper == null) {
                if (homePick.allExcluded || lockPick.allExcluded) {
                    throw AllRotationCandidatesExcludedException("the selected local folders")
                }
                return Result.retry()
            }
            // Home and lock change together: a tap that replaces this run can't land between them.
            withContext(NonCancellable) {
                homePick.wallpaper?.let { applyAndRecord(it, WallpaperTarget.HOME) }
                lockPick.wallpaper?.let { applyAndRecord(it, WallpaperTarget.LOCK) }
            }
            if (homePick.allExcluded || lockPick.allExcluded) {
                throw AllRotationCandidatesExcludedException("one selected local-folder target")
            }
            return Result.success()
        }
        val applyTarget = when {
            homeEnabled && lockEnabled -> WallpaperTarget.BOTH
            homeEnabled -> WallpaperTarget.HOME
            lockEnabled -> WallpaperTarget.LOCK
            else -> null
        }
        if (applyTarget != null && applyPrefetched(source, applyTarget)) {
            prefetchNext(source, applyTarget, fetchTarget = target, candidates = null, shuffle = shuffle)
            return Result.success()
        }

        if (!mayReachSource(source)) return Result.success()
        val rawWallpapers = fetchWallpapers(source, target)
        if (rawWallpapers.isEmpty()) return Result.retry()

        val wallpapers = filterRecentRepeats(filterPersistentExclusions(rawWallpapers, source))
        val pick = pickScheduledWallpaper(
            wallpapers = wallpapers,
            shuffle = shuffle,
            recentKeys = recentShuffleKeys(wallpapers.size),
        ) ?: return Result.retry()

        if (homeEnabled && lockEnabled) {
            applyAndRecord(pick, WallpaperTarget.BOTH)
        } else {
            // Only one target can be enabled here; both use the deterministic pick so
            // sequential (no-shuffle) rotation stays sequential in lock-only mode too.
            if (homeEnabled) applyAndRecord(pick, WallpaperTarget.HOME)
            if (lockEnabled) applyAndRecord(pick, WallpaperTarget.LOCK)
        }
        if (applyTarget != null && appliedWallpaper) {
            prefetchNext(source, applyTarget, fetchTarget = target, candidates = wallpapers, shuffle = shuffle)
        }
        return Result.success()
    }

    /** Legacy auto-wallpaper (backward compatible) */
    private suspend fun doLegacyWork(): Result {
        val source = prefs.autoWallpaperSource.first().normalizeWallpaperRotationSource()
        val targetStr = prefs.autoWallpaperTarget.first()
        val target = WallpaperTarget.entries.find { it.name == targetStr } ?: WallpaperTarget.BOTH

        if (source == "reddit" && !prefs.redditProviderEnabled.first()) return Result.success()
        if (source == "wallhaven" && !prefs.wallhavenProviderEnabled.first()) return Result.success()
        if (source == "pixabay" && !prefs.pixabayProviderEnabled.first()) return Result.success()
        if (source == "bing" && !prefs.bingProviderEnabled.first()) return Result.success()

        if (applyPrefetched(source, target)) {
            prefetchNext(source, target, fetchTarget = target, candidates = null, shuffle = true)
            return Result.success()
        }

        if (!mayReachSource(source)) return Result.success()
        val fetched = fetchWallpapers(source, target)
        val wallpapers = filterRecentRepeats(filterPersistentExclusions(fetched, source))
        val wallpaper = pickScheduledWallpaper(
            wallpapers = wallpapers,
            shuffle = true,
            recentKeys = recentShuffleKeys(wallpapers.size),
        ) ?: return Result.retry()

        return applyAndRecord(wallpaper, target).also {
            if (appliedWallpaper) {
                prefetchNext(source, target, fetchTarget = target, candidates = wallpapers, shuffle = true)
            }
        }
    }

    /**
     * Applies the wallpaper saved ahead of time for this setup, with no call to the source. False
     * means nothing usable was saved, and the caller fetches the usual way.
     */
    private suspend fun applyPrefetched(source: String, target: WallpaperTarget): Boolean {
        if (!sourceRequiresNetwork(source)) return false
        val slot = prefetchSlot(source, target)
        val saved = prefetch.peek(slot)
        if (saved == null || !isStillWanted(source, saved.wallpaper)) {
            prefetch.discard(slot)
            prefetchMissed = true
            return false
        }
        val appliedBefore = appliedWallpaper
        appliedWallpaper = false
        applyAndRecord(saved.wallpaper, target, locator = saved.file.absolutePath)
        val applied = appliedWallpaper
        appliedWallpaper = appliedBefore || applied
        // Used or unusable, the file is done with. A picture that wouldn't decode falls back to a fetch.
        prefetch.discard(slot)
        if (applied) {
            prefetch.recordHit()
        } else {
            prefetchMissed = true
        }
        return applied
    }

    /**
     * A saved wallpaper is skipped when it has since been excluded, already shown, or taken out of
     * the favorites or the collection it was saved from.
     */
    private suspend fun isStillWanted(source: String, wallpaper: Wallpaper): Boolean {
        if (rotationExclusions.filter(listOf(wallpaper)).candidates.isEmpty()) return false
        if (!isStillInSource(source, wallpaper)) return false
        return !prefs.avoidRecentRepeats.first() || wallpaper.stableKey() !in prefs.getRecentRotationIds()
    }

    /** Favorites and collections are sets a person edits. Every other source is whatever it returns. */
    private suspend fun isStillInSource(source: String, wallpaper: Wallpaper): Boolean {
        val key = wallpaper.stableKey()
        return when (source) {
            "favorites" -> favoritesRepo.getWallpapers().first().any { it.toWallpaper().stableKey() == key }
            "collection" -> {
                val items = collectionWallpapers()
                // With nothing in the collection the slot is the Discover one, and there is no set to be in.
                items.isEmpty() || items.any { it.stableKey() == key }
            }
            else -> true
        }
    }

    /**
     * The saved-ahead slot for this setup. A collection is named by its id. One with nothing in it
     * rotates through Discover instead, and gets a name of its own for as long as that lasts, so a
     * Discover picture is never taken for one of the collection's or the other way round.
     */
    private suspend fun prefetchSlot(source: String, target: WallpaperTarget): String {
        if (source != "collection") return rotationPrefetchSlot(source, target)
        val collectionId = prefs.schedulerCollectionId.first()
        val contents = if (collectionWallpapers().isEmpty()) "$collectionId:discover" else collectionId.toString()
        return rotationPrefetchSlot(source, target, contents)
    }

    /**
     * The work itself no longer waits for a network, so this is asked before every source call and
     * every download. False means [source] needs a connection this run may not use: there is none,
     * or it is metered and Wi-Fi only is on. A local folder never asks.
     */
    private suspend fun mayReachSource(source: String): Boolean {
        if (!sourceRequiresNetwork(source)) return true
        val access = network.access(wifiOnly = prefs.autoWallpaperRequiresWiFiOnly.first())
        if (access != RotationNetworkAccess.ALLOWED) {
            networkHold = access
            return false
        }
        // Counted only now that the run goes on to fetch. One that waits for a connection didn't.
        if (prefetchMissed) prefetch.recordMiss()
        return true
    }

    /**
     * Ends a run that had nothing saved and no connection it may use. A person who asked for the
     * change is told why nothing happened. The rotation is handed to a one-shot that waits for the
     * network inside WorkManager, so nothing here retries or polls while the phone is offline.
     */
    private suspend fun handOffHeldRotation(
        hold: RotationNetworkAccess,
        receiptWorkName: String,
        triggeredRotation: Boolean,
        catchUp: Boolean,
    ) {
        // A catch-up that is held again stops here, and the next scheduled run starts over.
        if (catchUp) return
        val requested = inputData.getBoolean(RESTART_COUNTDOWN_KEY, false)
        if (requested) showHoldMessage(hold)
        try {
            // Charging and idle are choices about the schedule. A trigger or a tap never waited on them.
            val scheduled = !triggeredRotation && !requested
            enqueueCatchUp(
                context = applicationContext,
                receiptWorkName = receiptWorkName,
                triggeredRotation = triggeredRotation,
                requiresCharging = scheduled && prefs.autoWallpaperRequiresCharging.first(),
                requiresWiFiOnly = prefs.autoWallpaperRequiresWiFiOnly.first(),
                requiresIdle = scheduled && prefs.autoWallpaperRequiresIdle.first(),
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("AutoWallpaperWorker", "Rotation catch-up could not be queued", e)
        }
    }

    /** Posted to the main thread without waiting on it: a worker has no screen of its own to show this on. */
    private fun showHoldMessage(hold: RotationNetworkAccess) {
        val message = if (hold == RotationNetworkAccess.WAITING_FOR_WIFI) {
            R.string.rotation_waiting_for_wifi
        } else {
            R.string.rotation_waiting_offline
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Saves the wallpaper the next rotation would pick. [candidates] is the list this run fetched.
     * After a cache hit there is none, so the source is asked once. Nothing here can fail the run:
     * the rotation already happened, and the next one can still fetch for itself.
     */
    private suspend fun prefetchNext(
        source: String,
        target: WallpaperTarget,
        fetchTarget: WallpaperTarget?,
        candidates: List<Wallpaper>?,
        shuffle: Boolean,
    ) {
        if (!sourceRequiresNetwork(source)) return
        try {
            // A run that applied a saved wallpaper offline, or on mobile data with Wi-Fi only on,
            // must not reach the source or download the next one either.
            val access = network.access(wifiOnly = prefs.autoWallpaperRequiresWiFiOnly.first())
            if (access != RotationNetworkAccess.ALLOWED) {
                prefetch.recordSkipped(
                    if (access == RotationNetworkAccess.OFFLINE) {
                        RotationPrefetchOutcome.DEFERRED_OFFLINE
                    } else {
                        RotationPrefetchOutcome.DEFERRED_METERED
                    },
                )
                return
            }
            if (prefetch.shouldDefer()) return
            val pool = candidates ?: rotationExclusions.filter(fetchWallpapers(source, fetchTarget)).candidates
            val recentIds = if (prefs.avoidRecentRepeats.first()) prefs.getRecentRotationIds().toSet() else emptySet()
            val eligible = excludeRecentWallpapers(pool, recentIds).filter { it.isPrefetchable() }
            val next = pickScheduledWallpaper(
                wallpapers = eligible,
                shuffle = shuffle,
                recentKeys = recentShuffleKeys(eligible.size),
            ) ?: return
            prefetch.store(prefetchSlot(source, target), next) { file ->
                wallpaperApplier.downloadToFile(next.fullUrl, file)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    private suspend fun filterRecentRepeats(wallpapers: List<Wallpaper>): List<Wallpaper> {
        if (!prefs.avoidRecentRepeats.first()) return wallpapers
        val recentIds = prefs.getRecentRotationIds().toSet()
        val remaining = excludeRecentWallpapers(wallpapers, recentIds)
        return if (remaining.isEmpty() && wallpapers.isNotEmpty()) {
            // Every candidate has already been shown — reset the no-repeat cycle.
            // Without this, sequential rotation pins to first() forever while the
            // recent-IDs FIFO keeps re-recording it.
            prefs.clearRecentRotationIds()
            wallpapers
        } else {
            remaining
        }
    }

    private suspend fun recentShuffleKeys(candidateCount: Int): Set<String> {
        val window = shuffleHistoryWindow(candidateCount)
        if (window == 0) return emptySet()
        return historyManager.getRecent(window).first().map { it.rotationKey() }.toSet()
    }

    private suspend fun pickLocalScheduledWallpaper(
        target: WallpaperTarget,
        shuffle: Boolean,
    ): LocalRotationPick {
        val rawWallpapers = fetchWallpapers(WALLPAPER_SOURCE_LOCAL_FOLDER, target)
        if (rawWallpapers.isEmpty()) return LocalRotationPick(null, allExcluded = false)
        val persistent = rotationExclusions.filter(rawWallpapers)
        if (persistent.allExcluded) return LocalRotationPick(null, allExcluded = true)
        val wallpapers = filterRecentRepeats(persistent.candidates)
        return LocalRotationPick(
            wallpaper = pickScheduledWallpaper(
                wallpapers = wallpapers,
                shuffle = shuffle,
                recentKeys = recentShuffleKeys(wallpapers.size),
            ),
            allExcluded = false,
        )
    }

    private suspend fun filterPersistentExclusions(
        wallpapers: List<Wallpaper>,
        sourceLabel: String,
    ): List<Wallpaper> {
        val result = rotationExclusions.filter(wallpapers)
        if (result.allExcluded) throw AllRotationCandidatesExcludedException(sourceLabel)
        return result.candidates
    }

    /** The pictures in the collection rotation is set to. Empty when none is chosen or it has nothing in it. */
    private suspend fun collectionWallpapers(): List<Wallpaper> {
        val collectionId = prefs.schedulerCollectionId.first()
        if (collectionId <= 0) return emptyList()
        return collectionRepo.getItems(collectionId).first().map {
            Wallpaper(
                id = it.wallpaperId,
                source = try { com.freevibe.data.model.ContentSource.valueOf(it.source) }
                    catch (_: Exception) { com.freevibe.data.model.ContentSource.WALLHAVEN },
                thumbnailUrl = it.thumbnailUrl,
                fullUrl = it.fullUrl,
                width = it.width,
                height = it.height,
            )
        }
    }

    private suspend fun fetchWallpapers(source: String, target: WallpaperTarget? = null): List<Wallpaper> {
        return when (source) {
            "collection" -> collectionWallpapers().ifEmpty { wallpaperRepo.getDiscover(page = 1).items }
            "favorites" -> favoritesRepo.getWallpapers().first().map { it.toWallpaper() }
            "wallhaven" -> wallpaperRepo.getWallhaven(page = (1..5).random()).items
            "bing" -> wallpaperRepo.getBingDaily(page = 1).items
            "reddit" -> redditRepo.getMultiSubreddit(preset = prefs.redditFeedPresets.first().rotationPreset()).items
            "pixabay" -> wallpaperRepo.getPixabay(page = (1..5).random()).items
            WALLPAPER_SOURCE_LOCAL_FOLDER -> {
                localWallpaperCatalog.migrateLegacyFolder(prefs.localWallpaperFolderUri.first())
                localWallpaperCatalog.rotationWallpapers(target)
            }
            "discover" -> wallpaperRepo.getDiscover(page = (1..3).random()).items
            else -> wallpaperRepo.getDiscover(page = 1).items
        }
    }

    /**
     * Runs to the end even when a newer tap replaces this work, so a started apply always lands in
     * history. The newer run waits on [rotationRunLock] and applies after it.
     */
    private suspend fun applyAndRecord(
        wallpaper: Wallpaper,
        target: WallpaperTarget,
        /** Where the picture is read from: its own address, or the file saved ahead of time. */
        locator: String = wallpaper.fullUrl,
    ): Result = withContext(NonCancellable) {
        val darkenPercent = prefs.autoWallpaperDarkenPercent.first()
        val nightVariant = shouldUseNightWallpaperVariant(
            enabled = prefs.autoWallpaperNightVariantEnabled.first(),
            schedulerEnabled = prefs.schedulerEnabled.first(),
            schedulerMode = prefs.schedulerDayNightMode.first(),
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            dayStartHour = prefs.schedulerDayStartHour.first(),
            nightStartHour = prefs.schedulerNightStartHour.first(),
            isSystemDark = applicationContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
        )
        wallpaperApplier.applyByLocator(
            locator,
            target,
            darkenPercent = darkenPercent,
            nightVariant = nightVariant,
            imageFlow = MediaIngestionImageFlow.AUTO_ROTATION,
        ).fold(
            onSuccess = {
                appliedWallpaper = true
                historyManager.record(wallpaper, target)
                prefs.setLastNightVariantWallpaper(wallpaper.fullUrl, target.name, darkenPercent)
                if (prefs.avoidRecentRepeats.first()) {
                    prefs.addRecentRotationId(wallpaper.stableKey())
                }
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        /** One rotation at a time, so a replaced run that is already applying finishes before the next one starts. */
        internal val rotationRunLock = Mutex()
        const val WORK_NAME = "auto_wallpaper"
        const val RECEIPT_WORK_NAME_KEY = "receipt_work_name"
        const val TRIGGERED_ROTATION_KEY = "triggered_rotation"
        /** Set on one-shot rotations a person asked for (tile, automation, Run now). */
        const val RESTART_COUNTDOWN_KEY = "restart_countdown"

        /**
         * The one-shot that makes up a rotation the connection held back. It carries the network
         * constraint the periodic and trigger work gave up, so WorkManager does the waiting.
         */
        const val CATCH_UP_WORK_NAME = "auto_wallpaper_catch_up"
        /** Set on the catch-up run itself, so a run that is held again never queues another. */
        const val CATCH_UP_KEY = "catch_up"

        /**
         * Starts the rotation interval over after a wallpaper change a person asked
         * for (browse, widget, tile, automation), so the next automatic change is a
         * full interval away instead of minutes later. Never throws: the change
         * itself already succeeded, and the old countdown keeps running on failure.
         */
        suspend fun restartCountdownAfterManualChange(
            context: Context,
            prefs: PreferencesManager = PreferencesManager(context),
        ) {
            try {
                val intervalMinutes = rotationRestartIntervalMinutes(
                    restartOnManual = prefs.autoWallpaperRestartOnManual.first(),
                    schedulerEnabled = prefs.schedulerEnabled.first(),
                    schedulerIntervalMinutes = prefs.schedulerIntervalMinutes.first(),
                    autoWallpaperEnabled = prefs.autoWallpaperEnabled.first(),
                    autoWallpaperIntervalHours = prefs.autoWallpaperInterval.first(),
                ) ?: return
                schedule(context, intervalMinutes, restartCountdown = true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("AutoWallpaperWorker", "Rotation countdown restart failed", e)
            }
        }

        /**
         * Schedule with minute-based intervals (minimum 15 min, WorkManager floor).
         *
         * Suspends to read constraint prefs from DataStore so we don't block the
         * Main thread (the old non-suspend impl did three sequential runBlocking
         * calls inside a UI-thread coroutine — observable ANR risk on cold starts
         * where DataStore had to hit disk).
         *
         * Charging and idle flow through to WorkManager, which fires the worker
         * only while they hold. Both are off by default and opt-in via Settings.
         * The network is not a constraint here. The worker checks it for itself,
         * so a wallpaper saved ahead of time still applies with the phone offline,
         * and the Wi-Fi only choice is enforced there before anything is fetched.
         */
        suspend fun schedule(
            context: Context,
            intervalMinutes: Long = 360,
            restartCountdown: Boolean = false,
        ) {
            val prefs = PreferencesManager(context)
            scheduleWithConstraints(
                context = context,
                intervalMinutes = intervalMinutes,
                requiresCharging = prefs.autoWallpaperRequiresCharging.first(),
                requiresIdle = prefs.autoWallpaperRequiresIdle.first(),
                restartCountdown = restartCountdown,
                // A restart sets its own delay. Any other re-arm keeps the one the stored work has.
                storedInitialDelayMillis = if (restartCountdown) 0L else readStoredInitialDelayMillis(context),
            )
        }

        /** The first-run delay of the rotation work WorkManager is holding, or 0 when it holds none. */
        private suspend fun readStoredInitialDelayMillis(context: Context): Long = try {
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME).get()
            }.firstOrNull { !it.state.isFinished }?.initialDelayMillis ?: 0L
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("AutoWallpaperWorker", "Stored rotation work could not be read", e)
            0L
        }

        /**
         * Non-suspend variant for the very rare callers that genuinely cannot
         * suspend (e.g. broadcast receivers or non-Hilt entry points). Caller
         * must already have the resolved constraint flags; we never block here.
         * [storedInitialDelayMillis] is the first-run delay of the work this
         * call re-arms, see [rotationInitialDelayMillis].
         */
        fun scheduleWithConstraints(
            context: Context,
            intervalMinutes: Long = 360,
            requiresCharging: Boolean,
            requiresIdle: Boolean,
            restartCountdown: Boolean = false,
            storedInitialDelayMillis: Long = 0L,
        ) {
            val constraints = buildAutoWallpaperConstraints(
                requiresCharging = requiresCharging,
                requiresWiFiOnly = false,
                requiresIdle = requiresIdle,
                requiresNetwork = false,
            )

            val interval = intervalMinutes.coerceAtLeast(15)
            val initialDelayMillis = rotationInitialDelayMillis(
                restartCountdown = restartCountdown,
                intervalMillis = TimeUnit.MINUTES.toMillis(interval),
                storedInitialDelayMillis = storedInitialDelayMillis,
            )
            val request = PeriodicWorkRequestBuilder<AutoWallpaperWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .apply { if (initialDelayMillis > 0L) setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS) }
                // WorkManager refuses to build idle-mode work that sets its own backoff.
                .apply { if (!requiresIdle) setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES) }
                .build()

            // UPDATE keeps the existing period's start time, so it cannot reset the
            // countdown. A manual apply needs a fresh work item, and that item waits a
            // full interval before its first run instead of rotating again at once.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                if (restartCountdown) {
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                } else {
                    ExistingPeriodicWorkPolicy.UPDATE
                },
                request,
            )
            // A person just changed the wallpaper, so a rotation still waiting on a connection is stale.
            if (restartCountdown) cancelCatchUp(context)
        }

        /**
         * Work scheduled by a build that still put the network in WorkManager keeps that constraint
         * until it is enqueued again, and nothing else does that before the next Settings change.
         * Called at app start: re-arms such work once with UPDATE, which swaps the constraints in
         * and leaves the interval and the running countdown alone. Never throws.
         */
        suspend fun relaxStoredNetworkConstraint(context: Context) {
            try {
                val stored = withContext(Dispatchers.IO) {
                    WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME).get()
                }.firstOrNull { !it.state.isFinished } ?: return
                if (stored.constraints.requiredNetworkType == NetworkType.NOT_REQUIRED) return
                val intervalMillis = stored.periodicityInfo?.repeatIntervalMillis ?: return
                schedule(context, TimeUnit.MILLISECONDS.toMinutes(intervalMillis))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("AutoWallpaperWorker", "Rotation constraint update failed", e)
            }
        }

        /**
         * Queues the rotation a held run could not make. [requiresWiFiOnly] picks an unmetered or
         * any connected network. A newer hand-off replaces a waiting one, so the request always
         * carries the current settings and the latest caller's receipt name.
         */
        internal fun enqueueCatchUp(
            context: Context,
            receiptWorkName: String,
            triggeredRotation: Boolean,
            requiresCharging: Boolean,
            requiresWiFiOnly: Boolean,
            requiresIdle: Boolean,
        ) {
            val request = OneTimeWorkRequestBuilder<AutoWallpaperWorker>()
                .setConstraints(
                    buildAutoWallpaperConstraints(
                        requiresCharging = requiresCharging,
                        requiresWiFiOnly = requiresWiFiOnly,
                        requiresIdle = requiresIdle,
                        requiresNetwork = true,
                    ),
                )
                .setInputData(
                    workDataOf(
                        RECEIPT_WORK_NAME_KEY to receiptWorkName,
                        TRIGGERED_ROTATION_KEY to triggeredRotation,
                        CATCH_UP_KEY to true,
                    ),
                )
                .apply { if (!requiresIdle) setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES) }
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(CATCH_UP_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        internal fun cancelCatchUp(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(CATCH_UP_WORK_NAME)
        }

        /** Legacy schedule for backward compatibility */
        suspend fun scheduleHours(context: Context, intervalHours: Long = 12) {
            schedule(context, intervalHours * 60)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            cancelCatchUp(context)
        }
    }
}

/**
 * How long the periodic rotation request waits before its first run.
 *
 * WorkManager runs the first period of a new periodic request at once. A countdown restarted
 * after a person's own change therefore waits out a whole interval here, or the wallpaper they
 * just picked is replaced seconds later.
 *
 * UPDATE keeps the stored enqueue time and period count but takes the new request's delay. So a
 * re-arm for a settings change keeps a restarted countdown by asking for a whole interval again,
 * counted from that same enqueue time. Asking for none would fire the rotation on the spot. Work
 * that never had a delay (rotation just turned on, or scheduled by an older build) still has none.
 */
internal fun rotationInitialDelayMillis(
    restartCountdown: Boolean,
    intervalMillis: Long,
    storedInitialDelayMillis: Long,
): Long = if (restartCountdown || storedInitialDelayMillis > 0L) intervalMillis else 0L

/**
 * The interval a manual change restarts the rotation with, or null when nothing
 * should be rescheduled. The scheduler owns the work whenever it is on, matching
 * how Settings schedules it, so its minute interval wins over the hourly one.
 */
internal fun rotationRestartIntervalMinutes(
    restartOnManual: Boolean,
    schedulerEnabled: Boolean,
    schedulerIntervalMinutes: Long,
    autoWallpaperEnabled: Boolean,
    autoWallpaperIntervalHours: Long,
): Long? = when {
    !restartOnManual -> null
    schedulerEnabled -> schedulerIntervalMinutes
    autoWallpaperEnabled -> autoWallpaperIntervalHours * 60
    else -> null
}

private data class LocalRotationPick(
    val wallpaper: Wallpaper?,
    val allExcluded: Boolean,
)

private class AllRotationCandidatesExcludedException(
    val sourceLabel: String,
) : IllegalStateException("All rotation candidates are excluded")

internal fun shouldRunLegacyRotation(
    schedulerEnabled: Boolean,
    legacyEnabled: Boolean,
    triggeredRotation: Boolean,
): Boolean = !schedulerEnabled && (legacyEnabled || triggeredRotation)

/**
 * A tile or automation rotation restarts the periodic countdown only when it actually
 * changed the wallpaper. A run skipped for a disabled provider still reports success,
 * and restarting then would push the next real rotation a full interval out for nothing.
 */
internal fun shouldRestartRotationCountdown(
    requested: Boolean,
    succeeded: Boolean,
    applied: Boolean,
): Boolean = requested && succeeded && applied

/** What diagnostics show for a run that waited on a connection instead of failing. */
internal fun rotationHoldReason(hold: RotationNetworkAccess): String =
    if (hold == RotationNetworkAccess.WAITING_FOR_WIFI) {
        "Wi-Fi only is on and this connection is metered. The next wallpaper loads on Wi-Fi."
    } else {
        "No connection and no wallpaper saved ahead. The next one loads when the phone is back online."
    }

/**
 * Pure builder for AutoWallpaper rotation constraints. Always sets
 * battery-not-low (energy-floor — was already the default, kept). Charging
 * + idle are pure-additive opt-ins.
 *
 * The periodic work passes [requiresNetwork] false: it has to fire offline to
 * apply a wallpaper saved ahead of time, and [RotationNetworkGate] guards every
 * fetch inside the worker. Only the catch-up one-shot asks for a network, which
 * is CONNECTED and tightens to UNMETERED when [requiresWiFiOnly] is set so
 * cellular billed users aren't charged for background fetches.
 *
 * Visible for unit testing.
 */
internal fun buildAutoWallpaperConstraints(
    requiresCharging: Boolean,
    requiresWiFiOnly: Boolean,
    requiresIdle: Boolean,
    requiresNetwork: Boolean = true,
): Constraints {
    val builder = Constraints.Builder()
        .setRequiredNetworkType(
            when {
                !requiresNetwork -> NetworkType.NOT_REQUIRED
                requiresWiFiOnly -> NetworkType.UNMETERED
                else -> NetworkType.CONNECTED
            },
        )
        .setRequiresBatteryNotLow(true)
    if (requiresCharging) builder.setRequiresCharging(true)
    if (requiresIdle) builder.setRequiresDeviceIdle(true)
    return builder.build()
}

internal fun String.normalizeWallpaperRotationSource(): String = when (lowercase(java.util.Locale.ROOT)) {
    // Locale.ROOT: Turkish locale turns "i" into dotless 'ı', which would desync this
    // comparison from the hardcoded keys used by the scheduler.
    "", "unsplash", "reddit" -> "discover"
    else -> this
}

internal fun sourceRequiresNetwork(source: String): Boolean =
    source.normalizeWallpaperRotationSource() != WALLPAPER_SOURCE_LOCAL_FOLDER

internal fun resolveScheduledWallpaperSource(
    defaultSource: String,
    daySource: String,
    nightSource: String,
    mode: String,
    hour: Int,
    dayStartHour: Int,
    nightStartHour: Int,
    isSystemDark: Boolean,
): String {
    val day = daySource.ifBlank { defaultSource }
    val night = nightSource.ifBlank { defaultSource }
    return when (mode) {
        SCHEDULER_DAY_NIGHT_MODE_CLOCK -> if (
            isHourInScheduledDayWindow(hour, dayStartHour, nightStartHour)
        ) day else night
        SCHEDULER_DAY_NIGHT_MODE_SYSTEM_THEME -> if (isSystemDark) night else day
        else -> defaultSource
    }
}

internal fun isHourInScheduledDayWindow(hour: Int, dayStartHour: Int, nightStartHour: Int): Boolean {
    val normalizedHour = hour.coerceIn(0, 23)
    val dayStart = dayStartHour.coerceIn(0, 23)
    val nightStart = nightStartHour.coerceIn(0, 23)
    if (dayStart == nightStart) return true
    return if (dayStart < nightStart) {
        normalizedHour in dayStart until nightStart
    } else {
        normalizedHour >= dayStart || normalizedHour < nightStart
    }
}

internal fun shouldUseNightWallpaperVariant(
    enabled: Boolean,
    schedulerEnabled: Boolean,
    schedulerMode: String,
    hour: Int,
    dayStartHour: Int,
    nightStartHour: Int,
    isSystemDark: Boolean,
): Boolean {
    if (!enabled) return false
    if (isSystemDark) return true
    return schedulerEnabled &&
        schedulerMode == SCHEDULER_DAY_NIGHT_MODE_CLOCK &&
        !isHourInScheduledDayWindow(hour, dayStartHour, nightStartHour)
}

internal fun pickScheduledWallpaper(
    wallpapers: List<Wallpaper>,
    shuffle: Boolean,
    recentKeys: Set<String> = emptySet(),
): Wallpaper? = when {
    wallpapers.isEmpty() -> null
    shuffle -> {
        val eligible = wallpapers.filterNot { it.stableKey() in recentKeys }
        (eligible.ifEmpty { wallpapers }).random()
    }
    else -> wallpapers.first()
}

/** Keep a small pool of applied items out of shuffle without starving tiny sources. */
internal fun shuffleHistoryWindow(candidateCount: Int): Int = when {
    candidateCount <= 1 -> 0
    else -> (candidateCount / 10).coerceIn(1, 5)
}

private fun com.freevibe.data.model.WallpaperHistoryEntity.rotationKey(): String =
    "WALLPAPER::${source.uppercase(Locale.ROOT)}::$wallpaperId"

/**
 * Filters out recently applied wallpapers. Returns an EMPTY list when every
 * candidate is recent so the caller can reset the no-repeat cycle explicitly.
 */
internal fun excludeRecentWallpapers(
    wallpapers: List<Wallpaper>,
    recentIds: Set<String>,
): List<Wallpaper> {
    if (recentIds.isEmpty()) return wallpapers
    return wallpapers.filter { it.stableKey() !in recentIds }
}

internal fun queryLocalFolderWallpapers(
    context: Context,
    folderUriString: String,
): List<Wallpaper> {
    if (folderUriString.isBlank()) return emptyList()
    val treeUri = runCatching { Uri.parse(folderUriString) }.getOrNull() ?: return emptyList()
    val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
        .getOrNull()
        ?: return emptyList()
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
    )
    return runCatching {
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val documentIdIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val displayNameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeTypeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val wallpapers = mutableListOf<Wallpaper>()
            while (cursor.moveToNext()) {
                val documentId = cursor.stringAt(documentIdIndex).takeUnless { it.isBlank() } ?: continue
                val displayName = cursor.stringAt(displayNameIndex)
                val mimeType = cursor.stringAt(mimeTypeIndex)
                if (!isLocalWallpaperMimeType(displayName, mimeType)) continue
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()
                wallpapers += Wallpaper(
                    id = documentUri,
                    source = ContentSource.LOCAL,
                    thumbnailUrl = documentUri,
                    fullUrl = documentUri,
                    width = 0,
                    height = 0,
                    fileSize = cursor.longAt(sizeIndex),
                    fileType = mimeType,
                    sourcePageUrl = folderUriString,
                    license = "Local User Content",
                    uploaderName = "Local folder",
                )
            }
            wallpapers.sortedWith(compareBy<Wallpaper, String>(String.CASE_INSENSITIVE_ORDER) { it.localSortName() })
        } ?: emptyList()
    }.getOrDefault(emptyList())
}

internal fun isLocalWallpaperMimeType(
    displayName: String?,
    mimeType: String?,
): Boolean {
    val normalizedMime = mimeType?.lowercase(Locale.ROOT).orEmpty()
    if (normalizedMime == "vnd.android.document/directory") return false
    if (normalizedMime.startsWith("image/")) return true
    return displayName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif")
}

private fun android.database.Cursor.stringAt(index: Int): String =
    if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""

private fun android.database.Cursor.longAt(index: Int): Long =
    if (index >= 0 && !isNull(index)) getLong(index).coerceAtLeast(0L) else 0L

private fun Wallpaper.localSortName(): String =
    fullUrl.substringAfterLast('/').ifBlank { id }
