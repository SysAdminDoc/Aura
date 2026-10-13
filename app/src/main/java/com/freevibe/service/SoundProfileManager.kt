package com.freevibe.service

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freevibe.data.local.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Serializable
data class SoundProfile(
    val id: String,
    val name: String,
    val ringtoneUri: String = "",
    val notificationUri: String = "",
    val alarmUri: String = "",
    val startHour: Int = 0,
    val endHour: Int = 24,
    val enabled: Boolean = true,
) {
    fun coversHour(hour: Int): Boolean {
        return if (startHour <= endHour) {
            hour in startHour until endHour
        } else {
            hour >= startHour || hour < endHour
        }
    }
}

internal val soundProfileJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun parseProfiles(raw: String): List<SoundProfile> =
    if (raw.isBlank()) emptyList()
    else runCatching { soundProfileJson.decodeFromString<List<SoundProfile>>(raw) }.getOrDefault(emptyList())

internal fun serializeProfiles(profiles: List<SoundProfile>): String =
    soundProfileJson.encodeToString(profiles)

internal fun activeProfileForHour(profiles: List<SoundProfile>, hour: Int): SoundProfile? =
    profiles.filter { it.enabled }.firstOrNull { it.coversHour(hour) }

/**
 * What one default sound was before a profile replaced it. Kept in preferences, so the profile can
 * give the sound back when its window ends even if the app was closed in between.
 */
@Serializable
internal data class PreProfileSound(
    /** The sound that was set before the profile. Null means the type was silent. */
    val previous: String? = null,
    /** What Aura had recorded as the sound it last applied itself, which the restore after a reboot reads. */
    val previousLastApplied: String = "",
    /** The sound the profile put there. Anything else found later was picked by hand. */
    val applied: String = "",
)

internal fun parsePreProfileSounds(raw: String): Map<SoundShuffleTarget, PreProfileSound> {
    if (raw.isBlank()) return emptyMap()
    val byName = runCatching { soundProfileJson.decodeFromString<Map<String, PreProfileSound>>(raw) }
        .getOrDefault(emptyMap())
    return SoundShuffleTarget.entries.mapNotNull { target -> byName[target.name]?.let { target to it } }.toMap()
}

internal fun serializePreProfileSounds(sounds: Map<SoundShuffleTarget, PreProfileSound>): String =
    if (sounds.isEmpty()) "" else soundProfileJson.encodeToString(sounds.mapKeys { it.key.name })

@HiltWorker
class SoundProfileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val prefs: PreferencesManager,
    private val receiptStore: BackgroundWorkReceiptStore,
    private val systemSounds: SystemSoundWriter,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val profiles = parseProfiles(prefs.soundProfilesJson.first())
            if (!soundProfilesNeedWorker(prefs.soundProfilesEnabled.first(), profiles)) {
                // Nothing here can change a sound any more. Give back what a profile changed, then
                // stop waking up every 15 minutes. The marker goes too, so switching profiles back
                // on inside the same window applies the profile again.
                if (Settings.System.canWrite(applicationContext)) {
                    handBackAll()
                    clearLastAppliedId()
                }
                cancel(applicationContext)
                return Result.success()
            }

            if (!Settings.System.canWrite(applicationContext)) {
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = "MissingPermission",
                    deferralReason = "WRITE_SETTINGS not granted",
                )
                return Result.failure()
            }

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val active = activeProfileForHour(profiles, hour)
            if (active == null) {
                // The window is over, so the sounds go back to what they were. Clearing the dedup
                // marker lets a single profile (e.g. night-only) apply again when its window comes
                // around tomorrow.
                handBackAll()
                clearLastAppliedId()
                receiptStore.recordSuccess(WORK_NAME)
                return Result.success()
            }

            val lastAppliedId = prefs.soundProfileLastAppliedId.first()
            if (lastAppliedId == active.id) {
                receiptStore.recordSuccess(WORK_NAME)
                return Result.success()
            }

            applyProfile(active)
            prefs.setSoundProfileLastAppliedId(active.id)
            receiptStore.recordSuccess(WORK_NAME)
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            receiptStore.recordFailure(
                uniqueWorkName = WORK_NAME,
                errorClass = e.javaClass.simpleName,
                deferralReason = e.message ?: "unknown error",
            )
            Result.retry()
        }
    }

    /**
     * Sets every sound [profile] names, first noting what each one replaces. A sound the profile
     * leaves alone goes back to what it was, in case an earlier profile had changed it.
     */
    private suspend fun applyProfile(profile: SoundProfile) {
        val replaced = parsePreProfileSounds(prefs.soundProfilePreviousSoundsJson.first()).toMutableMap()
        SoundShuffleTarget.entries.forEach { target ->
            val uri = profile.soundFor(target)
            if (uri.isBlank()) {
                handBack(target, replaced)
                return@forEach
            }
            val current = systemSounds.current(target)
            val earlier = replaced[target]
            // Still on the sound an earlier profile set: the sound to go back to is the one already
            // noted. Anything else is the user's own choice, and that's what comes back later.
            replaced[target] = if (earlier != null && sameSystemSound(current, earlier.applied)) {
                earlier.copy(applied = uri)
            } else {
                PreProfileSound(
                    previous = current,
                    previousLastApplied = prefs.lastAppliedSoundUri(target.contentType).first(),
                    applied = uri,
                )
            }
            // Saved before the write, so a process that dies in between still knows what to give back.
            prefs.setSoundProfilePreviousSoundsJson(serializePreProfileSounds(replaced))
            systemSounds.write(target, Uri.parse(uri))
            // The restore after a reboot or an update reads this, so it has to name the profile's sound
            // and not an older one applied by hand.
            prefs.setLastAppliedSoundUri(target.contentType, uri)
        }
    }

    private suspend fun handBackAll() {
        val replaced = parsePreProfileSounds(prefs.soundProfilePreviousSoundsJson.first()).toMutableMap()
        replaced.keys.toList().forEach { target -> handBack(target, replaced) }
    }

    /**
     * Puts [target] back on the sound it had before a profile changed it. A sound the user picked
     * by hand in the meantime is theirs and stays.
     */
    private suspend fun handBack(
        target: SoundShuffleTarget,
        replaced: MutableMap<SoundShuffleTarget, PreProfileSound>,
    ) {
        val entry = replaced[target] ?: return
        val type = target.contentType
        val lastApplied = prefs.lastAppliedSoundUri(type).first()
        if (sameSystemSound(systemSounds.current(target), entry.applied)) {
            prefs.setLastAppliedSoundUri(type, entry.previousLastApplied)
            systemSounds.write(target, entry.previous?.takeIf { it.isNotBlank() }?.let(Uri::parse))
        } else if (sameSystemSound(lastApplied, entry.applied)) {
            // The user changed the sound outside Aura. Stop calling the profile's sound Aura's own,
            // or the restore after a reboot would put it back over their choice.
            prefs.setLastAppliedSoundUri(type, "")
        }
        replaced.remove(target)
        prefs.setSoundProfilePreviousSoundsJson(serializePreProfileSounds(replaced))
    }

    private suspend fun clearLastAppliedId() {
        if (prefs.soundProfileLastAppliedId.first().isNotBlank()) prefs.setSoundProfileLastAppliedId("")
    }

    companion object {
        const val WORK_NAME = "sound_profile"
        private const val RUN_NOW_WORK_NAME = "sound_profile_now"

        /**
         * Schedules the worker when a profile can change a sound, and cancels it when none can. The
         * cancel is followed by one last run, which gives back the sounds a profile had changed.
         */
        fun sync(context: Context, needed: Boolean) {
            if (needed) {
                schedule(context)
            } else {
                cancel(context)
                runNow(context)
            }
        }

        /** Runs one pass now, so an edit is heard without waiting for the next 15 minute tick. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                RUN_NOW_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SoundProfileWorker>().build(),
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SoundProfileWorker>(
                15L, TimeUnit.MINUTES,
            ).setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15, TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(RUN_NOW_WORK_NAME)
        }
    }
}
