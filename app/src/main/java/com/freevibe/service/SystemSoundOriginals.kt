package com.freevibe.service

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** What a default sound was before Aura first replaced it. Kept in preferences as a short string. */
sealed interface SystemSoundOriginal {
    /** Aura had already replaced the sound before it started keeping originals. */
    data object Unknown : SystemSoundOriginal

    /** The type was set to no sound. */
    data object Silent : SystemSoundOriginal

    data class Sound(val uri: String) : SystemSoundOriginal

    fun encode(): String = when (this) {
        Unknown -> "unknown"
        Silent -> "silent"
        is Sound -> "uri:$uri"
    }

    companion object {
        fun decode(raw: String): SystemSoundOriginal? = when {
            raw == "unknown" -> Unknown
            raw == "silent" -> Silent
            raw.startsWith("uri:") && raw.length > 4 -> Sound(raw.removePrefix("uri:"))
            else -> null
        }
    }
}

/** How a "restore original" request ended. */
sealed interface OriginalRestore {
    /** Aura never replaced this sound, so there's nothing to go back to. */
    data object NeverChanged : OriginalRestore

    /** Aura replaced the sound before it started keeping originals. */
    data object Unknown : OriginalRestore

    /** The original sound isn't on the phone any more. Nothing was changed. */
    data object Missing : OriginalRestore

    /** The type is back on [uri], or on no sound when it's null. */
    data class Restored(val uri: String?) : OriginalRestore
}

/**
 * The original to record before Aura writes a default sound, or null when one is already recorded.
 *
 * A current sound that matches what Aura last applied means an older build already replaced the real
 * original, so all that can honestly be recorded is that it's unknown.
 */
internal fun originalToCapture(stored: String, current: String?, lastApplied: String): SystemSoundOriginal? = when {
    SystemSoundOriginal.decode(stored) != null -> null
    sameSystemSound(current, lastApplied) -> SystemSoundOriginal.Unknown
    current.isNullOrEmpty() -> SystemSoundOriginal.Silent
    else -> SystemSoundOriginal.Sound(current)
}

/** Whether two media URIs name the same sound, whatever query Android appended and whichever volume alias it used. */
internal fun sameSystemSound(first: String?, second: String?): Boolean {
    if (first.isNullOrEmpty() || second.isNullOrEmpty()) return false
    return canonicalSoundUri(first) == canonicalSoundUri(second)
}

private fun canonicalSoundUri(uri: String): String =
    uri.substringBefore('?').substringBefore('#').replace("/external_primary/", "/external/")

/** What restoring should do for the recorded original [stored]. Only [OriginalRestore.Restored] writes. */
internal fun planOriginalRestore(stored: String, isReadable: (String) -> Boolean): OriginalRestore =
    when (val original = SystemSoundOriginal.decode(stored)) {
        null -> OriginalRestore.NeverChanged
        SystemSoundOriginal.Unknown -> OriginalRestore.Unknown
        SystemSoundOriginal.Silent -> OriginalRestore.Restored(null)
        is SystemSoundOriginal.Sound ->
            if (isReadable(original.uri)) OriginalRestore.Restored(original.uri) else OriginalRestore.Missing
    }

/** The phone's default sounds, behind a seam so the capture and restore rules run in a plain unit test. */
internal interface SystemSoundStore {
    fun current(target: SoundShuffleTarget): String?
    fun set(target: SoundShuffleTarget, uri: String?)
    fun isReadable(uri: String): Boolean
}

private class AndroidSystemSoundStore(private val context: Context) : SystemSoundStore {
    override fun current(target: SoundShuffleTarget): String? =
        RingtoneManager.getActualDefaultRingtoneUri(context, target.ringtoneManagerType)?.toString()

    override fun set(target: SoundShuffleTarget, uri: String?) =
        RingtoneManager.setActualDefaultRingtoneUri(context, target.ringtoneManagerType, uri?.let(Uri::parse))

    override fun isReadable(uri: String): Boolean = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { true } ?: false
    }.getOrDefault(false)
}

/**
 * The one place Aura changes a default ringtone, notification or alarm sound. Before the first change to a
 * type it records what was set, and it never records over that, so the user can always get back to it.
 */
@Singleton
class SystemSoundWriter internal constructor(
    private val prefs: PreferencesManager,
    private val store: SystemSoundStore,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        prefs: PreferencesManager,
    ) : this(prefs, AndroidSystemSoundStore(context))

    fun current(target: SoundShuffleTarget): String? = runCatching { store.current(target) }.getOrNull()

    /** Sets [target] to [uri], or to no sound when it's null. Throws what RingtoneManager throws. */
    suspend fun write(target: SoundShuffleTarget, uri: Uri?) {
        val type = target.contentType
        originalToCapture(
            stored = prefs.systemSoundOriginal(type).first(),
            current = current(target),
            lastApplied = prefs.lastAppliedSoundUri(type).first(),
        )?.let { prefs.setSystemSoundOriginal(type, it.encode()) }
        store.set(target, uri?.toString())
    }

    /**
     * Puts [target] back on the sound it had before Aura. Aura stops treating the sound as its own, so the
     * restore that runs after a reboot leaves it alone.
     */
    suspend fun restoreOriginal(target: SoundShuffleTarget): OriginalRestore {
        val type = target.contentType
        val outcome = planOriginalRestore(prefs.systemSoundOriginal(type).first(), store::isReadable)
        if (outcome is OriginalRestore.Restored) {
            store.set(target, outcome.uri)
            prefs.setLastAppliedSoundUri(type, "")
        }
        return outcome
    }
}

internal fun soundTargetFor(type: ContentType): SoundShuffleTarget? =
    SoundShuffleTarget.entries.firstOrNull { it.contentType == type }
