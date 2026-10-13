package com.freevibe.service

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.LocalMediaStatus
import com.freevibe.data.model.Sound
import com.freevibe.data.model.normalizeLocalMediaStatus
import com.freevibe.util.rethrowIfCancelled
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

internal const val SOUND_SHUFFLE_STORE_VERSION = 1
internal const val MAX_SOUND_SHUFFLE_POOLS = 100
internal const val MAX_SOUND_SHUFFLE_ITEMS_PER_POOL = 500
internal const val MAX_SOUND_SHUFFLE_HISTORY = 200
internal val SOUND_SHUFFLE_INTERVAL_HOURS = listOf(1L, 6L, 12L, 24L, 72L)

enum class SoundShuffleSourceKind { DOWNLOAD, LOCAL, BUNDLED }
enum class SoundShuffleHistoryStatus { APPLIED, SKIPPED, FAILED }

/** Why a history row exists. Stored by name so the UI can show it in the person's language. */
enum class SoundShuffleReason {
    APPLIED_AUTOMATICALLY,
    WRITE_SETTINGS_MISSING,
    POOL_EMPTY,
    DOWNLOAD_MISSING,
    DOWNLOAD_NOT_AUDIO,
    DOWNLOAD_NEEDS_RELINK,
    ORIGINAL_UNAVAILABLE,
    LOCAL_NEEDS_RELINK,
    FILE_MISSING,
    FILE_UNREADABLE,
    DURATION_INCOMPATIBLE,
    NOT_AUDIO,
    APPLY_FAILED,
}

enum class SoundShuffleTarget(
    val contentType: ContentType,
    val ringtoneManagerType: Int,
) {
    RINGTONE(ContentType.RINGTONE, RingtoneManager.TYPE_RINGTONE),
    NOTIFICATION(ContentType.NOTIFICATION, RingtoneManager.TYPE_NOTIFICATION),
    ALARM(ContentType.ALARM, RingtoneManager.TYPE_ALARM),
}

@JsonClass(generateAdapter = true)
data class SoundShufflePoolItem(
    val itemId: String,
    val sourceKind: SoundShuffleSourceKind,
    val sourceId: String,
    val name: String,
    val locator: String = "",
    val mimeType: String = "",
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val sha256: String = "",
    val localMediaStatus: String = LocalMediaStatus.AVAILABLE,
    val addedAt: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class SoundShufflePool(
    val poolId: String,
    val name: String,
    val enabled: Boolean = false,
    val targets: List<SoundShuffleTarget> = listOf(SoundShuffleTarget.RINGTONE),
    val intervalHours: Long = 24,
    val items: List<SoundShufflePoolItem> = emptyList(),
    val lastAppliedItemIds: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class SoundShuffleHistoryEntry(
    val historyId: String = UUID.randomUUID().toString(),
    val poolId: String,
    val poolName: String,
    val itemId: String = "",
    val itemName: String = "",
    val target: SoundShuffleTarget,
    val status: SoundShuffleHistoryStatus,
    val previousUri: String = "",
    val appliedUri: String = "",
    /** English detail, kept for rows written before [reason] existed and for raw apply errors. */
    val message: String = "",
    /** A [SoundShuffleReason] name; a string so a newer build's reason never breaks parsing. */
    val reason: String = "",
    val happenedAt: Long = System.currentTimeMillis(),
    val undoneAt: Long = 0,
)

@JsonClass(generateAdapter = true)
data class SoundShuffleStore(
    val version: Int = SOUND_SHUFFLE_STORE_VERSION,
    val legacyMigrated: Boolean = false,
    val pools: List<SoundShufflePool> = emptyList(),
    val history: List<SoundShuffleHistoryEntry> = emptyList(),
)

data class SoundShuffleRunOutcome(
    val applied: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val shouldRetry: Boolean = false,
    val summary: String = "",
)

private sealed interface CandidateResolution {
    data class Valid(val item: SoundShufflePoolItem) : CandidateResolution
    data class Invalid(val item: SoundShufflePoolItem, val reason: SoundShuffleReason) : CandidateResolution
}

@Singleton
class SoundShufflePoolManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager,
    private val downloadDao: DownloadDao,
    private val bundledContentProvider: BundledContentProvider,
    private val soundApplier: SoundApplier,
    private val moshi: Moshi,
) {
    private val storeMutex = Mutex()
    private val executionMutex = Mutex()

    val store: Flow<SoundShuffleStore> = prefs.soundShufflePoolsJson
        .map { parseSoundShuffleStore(moshi, it) }
        .distinctUntilChanged()

    val downloads: Flow<List<DownloadEntity>> = downloadDao.getByType("SOUND")

    val originals: List<Sound>
        get() = bundledContentProvider.getRingtones() +
            bundledContentProvider.getNotifications() +
            bundledContentProvider.getAlarms()

    suspend fun createPool(name: String): Result<String> = runCatching {
        val normalizedName = normalizePoolName(name)
        val pool = SoundShufflePool(
            poolId = UUID.randomUUID().toString(),
            name = normalizedName,
        )
        updateStore { current ->
            require(current.pools.size < MAX_SOUND_SHUFFLE_POOLS) {
                "Aura supports up to $MAX_SOUND_SHUFFLE_POOLS sound pools"
            }
            require(current.pools.none { it.name.equals(normalizedName, ignoreCase = true) }) {
                "A sound pool with that name already exists"
            }
            current.copy(pools = current.pools + pool)
        }
        pool.poolId
    }.onFailure { it.rethrowIfCancelled() }

    suspend fun renamePool(poolId: String, name: String): Result<Unit> = mutatePool(poolId) { current, pool ->
        val normalizedName = normalizePoolName(name)
        require(current.pools.none {
            it.poolId != poolId && it.name.equals(normalizedName, ignoreCase = true)
        }) { "A sound pool with that name already exists" }
        pool.copy(name = normalizedName, updatedAt = System.currentTimeMillis())
    }

    suspend fun deletePool(poolId: String): Result<Unit> = runCatching {
        updateStore { current ->
            current.copy(
                pools = current.pools.filterNot { it.poolId == poolId },
                history = current.history.filterNot { it.poolId == poolId },
            )
        }
        RingtoneShuffleWorker.cancel(context, poolId)
    }.onFailure { it.rethrowIfCancelled() }

    suspend fun setEnabled(poolId: String, enabled: Boolean): Result<Unit> = mutatePool(poolId) { _, pool ->
        pool.copy(enabled = enabled, updatedAt = System.currentTimeMillis())
    }

    suspend fun setTargets(poolId: String, targets: Collection<SoundShuffleTarget>): Result<Unit> =
        mutatePool(poolId) { _, pool ->
            pool.copy(
                targets = targets.distinct().sortedBy(SoundShuffleTarget::ordinal),
                updatedAt = System.currentTimeMillis(),
            )
        }

    suspend fun setInterval(poolId: String, intervalHours: Long): Result<Unit> = mutatePool(poolId) { _, pool ->
        require(intervalHours in SOUND_SHUFFLE_INTERVAL_HOURS) { "Unsupported shuffle interval" }
        pool.copy(intervalHours = intervalHours, updatedAt = System.currentTimeMillis())
    }

    suspend fun addDownload(poolId: String, downloadId: String): Result<Unit> = runCatching {
        val download = downloadDao.getById(downloadId)
            ?: throw IllegalArgumentException("That downloaded sound is no longer available")
        require(download.type.equals("SOUND", ignoreCase = true)) { "Only sounds can be added to a sound pool" }
        require(
            normalizeLocalMediaStatus(download.localMediaStatus) == LocalMediaStatus.AVAILABLE &&
                download.localPath.isNotBlank(),
        ) { "Relink that downloaded sound before adding it to a pool" }
        addItem(
            poolId,
            SoundShufflePoolItem(
                itemId = "download:${download.id}",
                sourceKind = SoundShuffleSourceKind.DOWNLOAD,
                sourceId = download.id,
                name = download.name.ifBlank { "Downloaded sound" },
                locator = download.localPath,
                mimeType = download.originalMimeType,
                durationMs = download.originalDurationMs,
                sizeBytes = download.originalSizeBytes,
                sha256 = download.originalSha256,
                localMediaStatus = normalizeLocalMediaStatus(download.localMediaStatus),
            ),
        ).getOrThrow()
    }.onFailure { it.rethrowIfCancelled() }

    suspend fun addOriginal(poolId: String, originalId: String): Result<Unit> = runCatching {
        val original = originals.firstOrNull { it.id == originalId }
            ?: throw IllegalArgumentException("That Aura Original is no longer available")
        addItem(
            poolId,
            SoundShufflePoolItem(
                itemId = "bundled:${original.id}",
                sourceKind = SoundShuffleSourceKind.BUNDLED,
                sourceId = original.id,
                name = original.name,
                locator = original.downloadUrl,
                mimeType = original.fileType,
                durationMs = (original.duration * 1_000).toLong(),
                sizeBytes = original.fileSize,
            ),
        ).getOrThrow()
    }.onFailure { it.rethrowIfCancelled() }

    suspend fun addLocal(poolId: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(uri.scheme.equals("content", ignoreCase = true)) {
                "Choose an audio file from Android's file picker"
            }
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val staged = stageLocalMediaLocator(
                context = context,
                locator = uri.toString(),
                tempDirectoryName = "sound_pool_import",
                prefix = "aura_pool_sound_",
                maxBytes = MAX_SOUND_POOL_ITEM_BYTES,
            )
            try {
                val declaredMime = context.contentResolver.getType(uri).orEmpty()
                val metadata = readMediaTechnicalMetadata(staged, declaredMime)
                require(metadata.mimeType.startsWith("audio/") && metadata.durationMs > 0) {
                    "The selected file is not a decodable audio clip"
                }
                val hash = sha256File(staged)
                val item = SoundShufflePoolItem(
                    itemId = "local:$hash",
                    sourceKind = SoundShuffleSourceKind.LOCAL,
                    sourceId = hash,
                    name = queryDisplayName(uri).ifBlank { "Local sound" },
                    locator = uri.toString(),
                    mimeType = metadata.mimeType,
                    durationMs = metadata.durationMs,
                    sizeBytes = metadata.sizeBytes,
                    sha256 = hash,
                )
                addItem(poolId, item).getOrThrow()
            } finally {
                staged.delete()
            }
        }.onFailure { it.rethrowIfCancelled() }
    }

    suspend fun relinkItem(poolId: String, itemId: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val before = readStore().pools.firstOrNull { it.poolId == poolId }
                ?.items?.firstOrNull { it.itemId == itemId }
                ?: throw IllegalArgumentException("That sound is no longer in this pool")
            require(before.sourceKind != SoundShuffleSourceKind.BUNDLED) {
                "Aura Originals do not need relinking"
            }
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val staged = stageLocalMediaLocator(
                context,
                uri.toString(),
                "sound_pool_relink",
                "aura_pool_relink_",
                MAX_SOUND_POOL_ITEM_BYTES,
            )
            try {
                val metadata = readMediaTechnicalMetadata(staged, context.contentResolver.getType(uri).orEmpty())
                require(metadata.mimeType.startsWith("audio/") && metadata.durationMs > 0) {
                    "The selected file is not a decodable audio clip"
                }
                val hash = sha256File(staged)
                if (before.sha256.isNotBlank()) {
                    require(before.sha256.equals(hash, ignoreCase = true)) {
                        "This file does not match the sound saved in the backup"
                    }
                }
                mutatePool(poolId) { _, pool ->
                    pool.copy(
                        items = pool.items.map { item ->
                            if (item.itemId != itemId) item else item.copy(
                                sourceKind = SoundShuffleSourceKind.LOCAL,
                                sourceId = hash,
                                locator = uri.toString(),
                                mimeType = metadata.mimeType,
                                durationMs = metadata.durationMs,
                                sizeBytes = metadata.sizeBytes,
                                sha256 = hash,
                                localMediaStatus = LocalMediaStatus.AVAILABLE,
                            )
                        },
                        updatedAt = System.currentTimeMillis(),
                    )
                }.getOrThrow()
            } finally {
                staged.delete()
            }
        }.onFailure { it.rethrowIfCancelled() }
    }

    suspend fun removeItem(poolId: String, itemId: String): Result<Unit> = mutatePool(poolId) { _, pool ->
        pool.copy(
            items = pool.items.filterNot { it.itemId == itemId },
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun runNow(poolId: String) = RingtoneShuffleWorker.runNow(context, poolId)

    suspend fun restoreSchedules() {
        val current = readStore()
        current.pools.forEach(::reconcileSchedule)
        RingtoneShuffleWorker.cancelLegacy(context)
    }

    suspend fun migrateLegacyIfNeeded() {
        val current = readStore()
        if (current.legacyMigrated) return
        val ringtoneEnabled = prefs.ringtoneShuffleEnabled.first()
        val alarmEnabled = prefs.alarmShuffleEnabled.first()
        val legacyInterval = prefs.ringtoneShuffleIntervalHours.first()
        val downloads = downloadDao.getByType("SOUND").first()
        val migratedPool = if (ringtoneEnabled || alarmEnabled) {
            SoundShufflePool(
                poolId = UUID.randomUUID().toString(),
                name = "My downloads",
                enabled = true,
                targets = buildList {
                    if (ringtoneEnabled) add(SoundShuffleTarget.RINGTONE)
                    if (alarmEnabled) add(SoundShuffleTarget.ALARM)
                },
                intervalHours = legacyInterval.takeIf { it in SOUND_SHUFFLE_INTERVAL_HOURS } ?: 24,
                items = downloads.take(MAX_SOUND_SHUFFLE_ITEMS_PER_POOL).map { download ->
                    SoundShufflePoolItem(
                        itemId = "download:${download.id}",
                        sourceKind = SoundShuffleSourceKind.DOWNLOAD,
                        sourceId = download.id,
                        name = download.name.ifBlank { "Downloaded sound" },
                        locator = download.localPath,
                        mimeType = download.originalMimeType,
                        durationMs = download.originalDurationMs,
                        sizeBytes = download.originalSizeBytes,
                        sha256 = download.originalSha256,
                        localMediaStatus = normalizeLocalMediaStatus(download.localMediaStatus),
                    )
                },
            )
        } else null
        val migrated = updateStore { latest ->
            if (latest.legacyMigrated) latest else latest.copy(
                legacyMigrated = true,
                pools = migratedPool?.let {
                    latest.pools + it.copy(name = uniqueLegacyPoolName(latest))
                } ?: latest.pools,
            )
        }
        prefs.setRingtoneShuffleEnabled(false)
        prefs.setAlarmShuffleEnabled(false)
        migrated.pools.forEach(::reconcileSchedule)
        RingtoneShuffleWorker.cancelLegacy(context)
    }

    suspend fun runPool(poolId: String): SoundShuffleRunOutcome = executionMutex.withLock {
        runPoolLocked(poolId)
    }

    private suspend fun runPoolLocked(poolId: String): SoundShuffleRunOutcome {
        val pool = readStore().pools.firstOrNull { it.poolId == poolId }
            ?: return SoundShuffleRunOutcome(summary = "Sound pool no longer exists")
        if (!pool.enabled) return SoundShuffleRunOutcome(summary = "Sound pool is disabled")
        if (pool.targets.isEmpty()) return SoundShuffleRunOutcome(summary = "Sound pool has no targets")
        if (!soundApplier.canWriteSettings()) {
            pool.targets.forEach { target ->
                appendHistory(
                    SoundShuffleHistoryEntry(
                        poolId = pool.poolId,
                        poolName = pool.name,
                        target = target,
                        status = SoundShuffleHistoryStatus.FAILED,
                        message = "Allow Aura to modify system settings",
                        reason = SoundShuffleReason.WRITE_SETTINGS_MISSING.name,
                    ),
                )
            }
            return SoundShuffleRunOutcome(
                failed = pool.targets.size,
                summary = "WRITE_SETTINGS permission is missing",
            )
        }

        var applied = 0
        var skipped = 0
        var failed = 0
        var retry = false
        pool.targets.forEach { target ->
            val resolutions = pool.items.map { resolveCandidate(it, target) }
            resolutions.filterIsInstance<CandidateResolution.Invalid>().forEach { invalid ->
                skipped += 1
                appendHistory(
                    SoundShuffleHistoryEntry(
                        poolId = pool.poolId,
                        poolName = pool.name,
                        itemId = invalid.item.itemId,
                        itemName = invalid.item.name,
                        target = target,
                        status = SoundShuffleHistoryStatus.SKIPPED,
                        message = soundShuffleReasonEnglish(invalid.reason, target),
                        reason = invalid.reason.name,
                    ),
                )
            }
            val candidates = resolutions.filterIsInstance<CandidateResolution.Valid>().map { it.item }
            if (candidates.isEmpty()) {
                if (pool.items.isEmpty()) {
                    skipped += 1
                    appendHistory(
                        SoundShuffleHistoryEntry(
                            poolId = pool.poolId,
                            poolName = pool.name,
                            target = target,
                            status = SoundShuffleHistoryStatus.SKIPPED,
                            message = "Add at least one compatible sound",
                            reason = SoundShuffleReason.POOL_EMPTY.name,
                        ),
                    )
                }
                return@forEach
            }
            val latest = readStore()
            val latestPool = latest.pools.firstOrNull { it.poolId == pool.poolId }
            val lastItemId = latestPool?.lastAppliedItemIds?.get(target.name).orEmpty().ifBlank {
                latest.history.firstOrNull {
                    it.poolId == pool.poolId && it.target == target &&
                        it.status == SoundShuffleHistoryStatus.APPLIED
                }?.itemId.orEmpty()
            }
            val chosen = selectSoundShuffleCandidate(candidates, lastItemId)
            val previous = RingtoneManager.getActualDefaultRingtoneUri(
                context,
                target.ringtoneManagerType,
            )?.toString().orEmpty()
            val reusableUri = latest.history.firstOrNull {
                it.poolId == pool.poolId && it.itemId == chosen.itemId && it.target == target &&
                    it.status == SoundShuffleHistoryStatus.APPLIED && it.undoneAt == 0L &&
                    it.appliedUri.isNotBlank()
            }?.appliedUri
            val result = reusableUri?.let { soundApplier.applyExistingUri(Uri.parse(it), target.contentType) }
                ?.map { checkNotNull(it) }
                ?.recoverCatching {
                    applyPoolItem(chosen, target).getOrThrow()
                }
                ?: applyPoolItem(chosen, target)
            result.onSuccess { uri ->
                applied += 1
                appendAppliedHistory(
                    SoundShuffleHistoryEntry(
                        poolId = pool.poolId,
                        poolName = pool.name,
                        itemId = chosen.itemId,
                        itemName = chosen.name,
                        target = target,
                        status = SoundShuffleHistoryStatus.APPLIED,
                        previousUri = previous,
                        appliedUri = uri.toString(),
                        message = "Applied automatically",
                        reason = SoundShuffleReason.APPLIED_AUTOMATICALLY.name,
                    ),
                )
            }.onFailure { error ->
                error.rethrowIfCancelled()
                failed += 1
                retry = retry || error is java.io.IOException
                appendHistory(
                    SoundShuffleHistoryEntry(
                        poolId = pool.poolId,
                        poolName = pool.name,
                        itemId = chosen.itemId,
                        itemName = chosen.name,
                        target = target,
                        status = SoundShuffleHistoryStatus.FAILED,
                        previousUri = previous,
                        message = error.message ?: "Sound could not be applied",
                        reason = SoundShuffleReason.APPLY_FAILED.name,
                    ),
                )
            }
        }
        return SoundShuffleRunOutcome(
            applied = applied,
            skipped = skipped,
            failed = failed,
            shouldRetry = retry && applied == 0,
            summary = "$applied applied, $skipped skipped, $failed failed",
        )
    }

    suspend fun undo(historyId: String): Result<Unit> = runCatching {
        val entry = readStore().history.firstOrNull { it.historyId == historyId }
            ?: throw IllegalArgumentException("That history entry is no longer available")
        require(entry.status == SoundShuffleHistoryStatus.APPLIED && entry.undoneAt == 0L) {
            "This change cannot be undone"
        }
        val current = RingtoneManager.getActualDefaultRingtoneUri(
            context,
            entry.target.ringtoneManagerType,
        )?.toString().orEmpty()
        require(current == entry.appliedUri) {
            "The system sound changed after this shuffle, so Aura left it alone"
        }
        val previous = entry.previousUri.takeIf(String::isNotBlank)?.let(Uri::parse)
        soundApplier.applyExistingUri(previous, entry.target.contentType).getOrThrow()
        updateStore { store ->
            store.copy(
                history = store.history.map { item ->
                    if (item.historyId == historyId) item.copy(
                        undoneAt = System.currentTimeMillis(),
                        message = "Undone",
                    ) else item
                },
            )
        }
        Unit
    }.onFailure { it.rethrowIfCancelled() }

    private suspend fun addItem(poolId: String, item: SoundShufflePoolItem): Result<Unit> =
        mutatePool(poolId) { _, pool ->
            require(pool.items.size < MAX_SOUND_SHUFFLE_ITEMS_PER_POOL || pool.items.any { it.itemId == item.itemId }) {
                "A sound pool can contain up to $MAX_SOUND_SHUFFLE_ITEMS_PER_POOL sounds"
            }
            pool.copy(
                items = pool.items.filterNot { it.itemId == item.itemId } + item,
                updatedAt = System.currentTimeMillis(),
            )
        }

    private suspend fun mutatePool(
        poolId: String,
        transform: (SoundShuffleStore, SoundShufflePool) -> SoundShufflePool,
    ): Result<Unit> = runCatching {
        val updated = updateStore { current ->
            val pool = current.pools.firstOrNull { it.poolId == poolId }
                ?: throw IllegalArgumentException("Sound pool not found")
            val replacement = transform(current, pool)
            current.copy(pools = current.pools.map { if (it.poolId == poolId) replacement else it })
        }
        updated.pools.firstOrNull { it.poolId == poolId }?.let(::reconcileSchedule)
        Unit
    }.onFailure { it.rethrowIfCancelled() }

    private suspend fun updateStore(transform: (SoundShuffleStore) -> SoundShuffleStore): SoundShuffleStore =
        storeMutex.withLock {
            val current = parseSoundShuffleStore(moshi, prefs.soundShufflePoolsJson.first())
            val updated = normalizeSoundShuffleStore(transform(current))
            prefs.setSoundShufflePoolsJson(encodeSoundShuffleStore(moshi, updated))
            updated
        }

    private suspend fun readStore(): SoundShuffleStore =
        parseSoundShuffleStore(moshi, prefs.soundShufflePoolsJson.first())

    private suspend fun appendHistory(entry: SoundShuffleHistoryEntry) {
        updateStore { current -> current.copy(history = listOf(entry) + current.history) }
    }

    private suspend fun appendAppliedHistory(entry: SoundShuffleHistoryEntry) {
        updateStore { current ->
            current.copy(
                pools = current.pools.map { pool ->
                    if (pool.poolId != entry.poolId) pool else pool.copy(
                        lastAppliedItemIds = pool.lastAppliedItemIds + (entry.target.name to entry.itemId),
                    )
                },
                history = listOf(entry) + current.history,
            )
        }
    }

    private suspend fun resolveCandidate(
        stored: SoundShufflePoolItem,
        target: SoundShuffleTarget,
    ): CandidateResolution {
        val item = when (stored.sourceKind) {
            SoundShuffleSourceKind.DOWNLOAD -> {
                val download = downloadDao.getById(stored.sourceId)
                    ?: return CandidateResolution.Invalid(stored, SoundShuffleReason.DOWNLOAD_MISSING)
                if (!download.type.equals("SOUND", ignoreCase = true)) {
                    return CandidateResolution.Invalid(stored, SoundShuffleReason.DOWNLOAD_NOT_AUDIO)
                }
                if (normalizeLocalMediaStatus(download.localMediaStatus) != LocalMediaStatus.AVAILABLE) {
                    return CandidateResolution.Invalid(stored, SoundShuffleReason.DOWNLOAD_NEEDS_RELINK)
                }
                stored.copy(
                    locator = download.localPath,
                    name = download.name.ifBlank { stored.name },
                    mimeType = download.originalMimeType.ifBlank { stored.mimeType },
                    durationMs = download.originalDurationMs.takeIf { it > 0 } ?: stored.durationMs,
                    sizeBytes = download.originalSizeBytes.takeIf { it > 0 } ?: stored.sizeBytes,
                    sha256 = download.originalSha256.ifBlank { stored.sha256 },
                    localMediaStatus = normalizeLocalMediaStatus(download.localMediaStatus),
                )
            }
            SoundShuffleSourceKind.BUNDLED -> {
                val original = originals.firstOrNull { it.id == stored.sourceId }
                    ?: return CandidateResolution.Invalid(stored, SoundShuffleReason.ORIGINAL_UNAVAILABLE)
                stored.copy(
                    name = original.name,
                    locator = original.downloadUrl,
                    mimeType = original.fileType,
                    durationMs = (original.duration * 1_000).toLong(),
                    localMediaStatus = LocalMediaStatus.AVAILABLE,
                )
            }
            SoundShuffleSourceKind.LOCAL -> stored
        }
        if (normalizeLocalMediaStatus(item.localMediaStatus) != LocalMediaStatus.AVAILABLE) {
            return CandidateResolution.Invalid(item, SoundShuffleReason.LOCAL_NEEDS_RELINK)
        }
        if (item.locator.isBlank()) return CandidateResolution.Invalid(item, SoundShuffleReason.FILE_MISSING)
        val normalizedLocator = normalizeSoundLocator(item.locator)
        if (!isReadableLocalSound(normalizedLocator)) {
            return CandidateResolution.Invalid(item, SoundShuffleReason.FILE_UNREADABLE)
        }
        val durationMs = item.durationMs.takeIf { it > 0 } ?: readDurationMs(normalizedLocator)
        if (!isSoundShuffleDurationCompatible(target, durationMs)) {
            return CandidateResolution.Invalid(item, SoundShuffleReason.DURATION_INCOMPATIBLE)
        }
        if (item.mimeType.isNotBlank() && !item.mimeType.startsWith("audio/")) {
            return CandidateResolution.Invalid(item, SoundShuffleReason.NOT_AUDIO)
        }
        return CandidateResolution.Valid(item.copy(locator = normalizedLocator, durationMs = durationMs))
    }

    private suspend fun applyPoolItem(
        item: SoundShufflePoolItem,
        target: SoundShuffleTarget,
    ): Result<Uri> = soundApplier.downloadAndApply(
        url = item.locator,
        fileName = "Aura_${item.name}_${target.name.lowercase(Locale.ROOT)}",
        type = target.contentType,
        savedOriginalId = item.sourceId.takeIf { item.sourceKind == SoundShuffleSourceKind.DOWNLOAD },
    )

    private fun reconcileSchedule(pool: SoundShufflePool) {
        if (pool.enabled && pool.targets.isNotEmpty() && pool.items.isNotEmpty()) {
            RingtoneShuffleWorker.schedule(context, pool.poolId, pool.intervalHours)
        } else {
            RingtoneShuffleWorker.cancel(context, pool.poolId)
        }
    }

    private fun queryDisplayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    private fun isReadableLocalSound(locator: String): Boolean {
        if (!isLocalMediaLocator(locator)) return false
        return runCatching {
            val uri = Uri.parse(locator)
            when (uri.scheme?.lowercase(Locale.ROOT)) {
                "rawresource" -> {
                    val resourceId = uri.schemeSpecificPart.trim('/').toInt()
                    context.resources.openRawResource(resourceId).use { it.read() >= 0 }
                }
                "file" -> File(uri.path.orEmpty()).let { it.isFile && it.canRead() && it.length() > 0 }
                "content", "android.resource" ->
                    context.contentResolver.openInputStream(uri)?.use { it.read() >= 0 } == true
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun normalizeSoundLocator(locator: String): String {
        if (isLocalMediaLocator(locator)) return locator
        val file = File(locator)
        return if (file.isAbsolute) Uri.fromFile(file).toString() else locator
    }

    private fun readDurationMs(locator: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(locator)
            when (uri.scheme?.lowercase(Locale.ROOT)) {
                "content", "android.resource", "rawresource" -> retriever.setDataSource(context, uri)
                "file" -> retriever.setDataSource(uri.path.orEmpty())
                else -> return 0
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (_: Exception) {
            0
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun uniqueLegacyPoolName(store: SoundShuffleStore): String {
        val base = "My downloads"
        if (store.pools.none { it.name.equals(base, ignoreCase = true) }) return base
        var suffix = 2
        while (store.pools.any { it.name.equals("$base $suffix", ignoreCase = true) }) suffix += 1
        return "$base $suffix"
    }

    private companion object {
        const val MAX_SOUND_POOL_ITEM_BYTES = 64L * 1024 * 1024
    }
}

internal fun selectSoundShuffleCandidate(
    candidates: List<SoundShufflePoolItem>,
    lastItemId: String,
    random: Random = Random.Default,
): SoundShufflePoolItem {
    require(candidates.isNotEmpty()) { "No sound candidates" }
    val eligible = if (candidates.size > 1) candidates.filterNot { it.itemId == lastItemId } else candidates
    return eligible[random.nextInt(eligible.size)]
}

internal fun isSoundShuffleDurationCompatible(target: SoundShuffleTarget, durationMs: Long): Boolean =
    when (target) {
        SoundShuffleTarget.RINGTONE -> durationMs in 1_000L..60_000L
        SoundShuffleTarget.NOTIFICATION -> durationMs in 100L..10_000L
        SoundShuffleTarget.ALARM -> durationMs in 5_000L..60_000L
    }

internal fun incompatibleDurationMessage(target: SoundShuffleTarget): String = when (target) {
    SoundShuffleTarget.RINGTONE -> "Ringtones must be 1 to 60 seconds"
    SoundShuffleTarget.NOTIFICATION -> "Notifications must be 0.1 to 10 seconds"
    SoundShuffleTarget.ALARM -> "Alarms must be 5 to 60 seconds"
}

/** The English [SoundShuffleHistoryEntry.message] stored beside a reason for older readers. */
internal fun soundShuffleReasonEnglish(reason: SoundShuffleReason, target: SoundShuffleTarget): String = when (reason) {
    SoundShuffleReason.APPLIED_AUTOMATICALLY -> "Applied automatically"
    SoundShuffleReason.WRITE_SETTINGS_MISSING -> "Allow Aura to modify system settings"
    SoundShuffleReason.POOL_EMPTY -> "Add at least one compatible sound"
    SoundShuffleReason.DOWNLOAD_MISSING -> "Downloaded sound is missing"
    SoundShuffleReason.DOWNLOAD_NOT_AUDIO -> "Downloaded item is not audio"
    SoundShuffleReason.DOWNLOAD_NEEDS_RELINK -> "Downloaded sound needs relinking"
    SoundShuffleReason.ORIGINAL_UNAVAILABLE -> "Aura Original is unavailable in this build"
    SoundShuffleReason.LOCAL_NEEDS_RELINK -> "Local sound needs relinking"
    SoundShuffleReason.FILE_MISSING -> "Sound file is missing"
    SoundShuffleReason.FILE_UNREADABLE -> "Sound file is missing or permission was revoked"
    SoundShuffleReason.DURATION_INCOMPATIBLE -> incompatibleDurationMessage(target)
    SoundShuffleReason.NOT_AUDIO -> "File is not audio"
    SoundShuffleReason.APPLY_FAILED -> "Sound could not be applied"
}

internal fun parseSoundShuffleStore(moshi: Moshi, raw: String): SoundShuffleStore {
    if (raw.isBlank()) return SoundShuffleStore()
    return runCatching { parseSoundShuffleStoreStrict(moshi, raw) }.getOrDefault(SoundShuffleStore())
}

internal fun parseSoundShuffleStoreStrict(moshi: Moshi, raw: String): SoundShuffleStore {
    require(raw.length <= LibraryTransferContract.MAX_SOUND_SHUFFLE_DOCUMENT_CHARS) {
        "Sound pool data is too large"
    }
    val store = moshi.adapter(SoundShuffleStore::class.java).fromJson(raw)
        ?: throw IllegalArgumentException("Invalid sound pool data")
    require(store.version in 1..SOUND_SHUFFLE_STORE_VERSION) { "Unsupported sound pool version" }
    require(store.pools.size <= MAX_SOUND_SHUFFLE_POOLS) { "Too many sound pools" }
    require(store.pools.all { it.items.size <= MAX_SOUND_SHUFFLE_ITEMS_PER_POOL }) {
        "A sound pool contains too many items"
    }
    return normalizeSoundShuffleStore(store)
}

internal fun portableSoundShuffleStoreJson(moshi: Moshi, raw: String): String {
    if (raw.isBlank()) return ""
    val parsed = parseSoundShuffleStoreStrict(moshi, raw)
    if (parsed.pools.isEmpty()) return ""
    val portable = normalizeSoundShuffleStore(
        parsed.copy(
            legacyMigrated = true,
            pools = parsed.pools.map { pool ->
                pool.copy(items = pool.items.map { item ->
                    item.copy(
                        locator = "",
                        localMediaStatus = if (item.sourceKind == SoundShuffleSourceKind.BUNDLED) {
                            LocalMediaStatus.AVAILABLE
                        } else {
                            LocalMediaStatus.MISSING
                        },
                    )
                })
            },
            history = emptyList(),
        ),
    )
    return encodeSoundShuffleStore(moshi, portable)
}

internal fun encodeSoundShuffleStore(moshi: Moshi, store: SoundShuffleStore): String {
    val encoded = moshi.adapter(SoundShuffleStore::class.java).toJson(normalizeSoundShuffleStore(store))
    require(encoded.length <= LibraryTransferContract.MAX_SOUND_SHUFFLE_DOCUMENT_CHARS) {
        "Sound pool data is too large"
    }
    return encoded
}

internal fun mergeSoundShuffleStores(
    existing: SoundShuffleStore,
    imported: SoundShuffleStore,
): SoundShuffleStore {
    val ids = existing.pools.mapTo(mutableSetOf(), SoundShufflePool::poolId)
    val names = existing.pools.mapTo(mutableSetOf()) { it.name.lowercase(Locale.ROOT) }
    val additions = imported.pools.filter { pool ->
        ids.add(pool.poolId) && names.add(pool.name.lowercase(Locale.ROOT))
    }
    return normalizeSoundShuffleStore(
        existing.copy(
            legacyMigrated = true,
            pools = existing.pools + additions,
        ),
    )
}

internal fun normalizeSoundShuffleStore(store: SoundShuffleStore): SoundShuffleStore {
    val seenPools = mutableSetOf<String>()
    val pools = store.pools.asSequence()
        .filter { it.poolId.isNotBlank() && seenPools.add(it.poolId.take(MAX_POOL_ID_CHARS)) }
        .take(MAX_SOUND_SHUFFLE_POOLS)
        .map { pool ->
            val seenItems = mutableSetOf<String>()
            pool.copy(
                poolId = pool.poolId.take(MAX_POOL_ID_CHARS),
                name = pool.name.trim().take(MAX_POOL_NAME_CHARS).ifBlank { "Sound pool" },
                targets = pool.targets.distinct().sortedBy(SoundShuffleTarget::ordinal),
                intervalHours = pool.intervalHours.takeIf { it in SOUND_SHUFFLE_INTERVAL_HOURS } ?: 24,
                lastAppliedItemIds = pool.lastAppliedItemIds.entries
                    .filter { entry ->
                        SoundShuffleTarget.entries.any { it.name == entry.key } && entry.value.isNotBlank()
                    }
                    .associate { it.key to it.value.take(MAX_ITEM_ID_CHARS) },
                items = pool.items.asSequence()
                    .filter { it.itemId.isNotBlank() && seenItems.add(it.itemId.take(MAX_ITEM_ID_CHARS)) }
                    .take(MAX_SOUND_SHUFFLE_ITEMS_PER_POOL)
                    .map { item ->
                        item.copy(
                            itemId = item.itemId.take(MAX_ITEM_ID_CHARS),
                            sourceId = item.sourceId.trim().take(MAX_SOURCE_ID_CHARS),
                            name = item.name.trim().take(MAX_ITEM_NAME_CHARS).ifBlank { "Sound" },
                            locator = item.locator.trim().take(MAX_LOCATOR_CHARS),
                            mimeType = item.mimeType.trim().lowercase(Locale.ROOT).take(MAX_MIME_TYPE_CHARS),
                            durationMs = item.durationMs.coerceAtLeast(0),
                            sizeBytes = item.sizeBytes.coerceAtLeast(0),
                            sha256 = item.sha256.lowercase(Locale.ROOT).takeIf(SHA_256::matches).orEmpty(),
                            localMediaStatus = normalizeLocalMediaStatus(item.localMediaStatus),
                            addedAt = item.addedAt.coerceAtLeast(0),
                        )
                    }
                    .toList(),
                createdAt = pool.createdAt.coerceAtLeast(0),
                updatedAt = pool.updatedAt.coerceAtLeast(0),
            )
        }
        .toList()
    val validPoolIds = pools.mapTo(mutableSetOf(), SoundShufflePool::poolId)
    val history = store.history.asSequence()
        .filter { it.poolId in validPoolIds && it.historyId.isNotBlank() }
        .take(MAX_SOUND_SHUFFLE_HISTORY)
        .map { entry ->
            entry.copy(
                historyId = entry.historyId.take(MAX_POOL_ID_CHARS),
                poolId = entry.poolId.take(MAX_POOL_ID_CHARS),
                poolName = entry.poolName.trim().take(MAX_POOL_NAME_CHARS),
                itemId = entry.itemId.take(MAX_ITEM_ID_CHARS),
                itemName = entry.itemName.trim().take(MAX_ITEM_NAME_CHARS),
                previousUri = entry.previousUri.take(MAX_LOCATOR_CHARS),
                appliedUri = entry.appliedUri.take(MAX_LOCATOR_CHARS),
                message = entry.message.trim().take(MAX_HISTORY_MESSAGE_CHARS),
                reason = entry.reason.trim().take(MAX_REASON_CHARS),
                happenedAt = entry.happenedAt.coerceAtLeast(0),
                undoneAt = entry.undoneAt.coerceAtLeast(0),
            )
        }
        .toList()
    return SoundShuffleStore(
        version = SOUND_SHUFFLE_STORE_VERSION,
        legacyMigrated = store.legacyMigrated,
        pools = pools,
        history = history,
    )
}

private fun normalizePoolName(name: String): String {
    val normalized = name.trim().replace(Regex("\\s+"), " ").take(MAX_POOL_NAME_CHARS)
    require(normalized.isNotBlank()) { "Enter a pool name" }
    return normalized
}

private const val MAX_POOL_ID_CHARS = 128
private const val MAX_POOL_NAME_CHARS = 80
private const val MAX_ITEM_ID_CHARS = 640
private const val MAX_SOURCE_ID_CHARS = 512
private const val MAX_ITEM_NAME_CHARS = 160
private const val MAX_LOCATOR_CHARS = 2_048
private const val MAX_MIME_TYPE_CHARS = 128
private const val MAX_HISTORY_MESSAGE_CHARS = 512
private const val MAX_REASON_CHARS = 64
private val SHA_256 = Regex("^[0-9a-f]{64}$")
