package com.freevibe.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.model.SoundAction
import com.freevibe.data.model.soundLicenseCapabilities
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the sound named in a backup into a ringtone on this phone.
 *
 * A ringtone Aura saved earlier is reused when it is still here under the same file name. Otherwise
 * the sound is fetched again from its source, the same way the contact screen fetches it. A sound
 * that came from the old phone's own storage has no source to fetch from and stays unresolved.
 *
 * The restore says how much may be downloaded for each sound. With no allowance nothing is asked of
 * any source, because finding where a sound lives can use the connection as well.
 */
@Singleton
class ContactRingtoneSoundRestorer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bundledContent: BundledContentProvider,
    private val soundUrlResolver: SoundUrlResolver,
    private val soundApplier: SoundApplier,
) : ContactRingtoneSoundResolver {

    override suspend fun resolve(
        entry: ContactRingtoneExportEntry,
        downloadAllowance: Long,
    ): ResolvedContactRingtone? {
        findSavedRingtone(entry.soundFileName)?.let { return ResolvedContactRingtone(it, savedNow = false) }
        if (downloadAllowance <= 0) return null
        val sound = soundFor(entry) ?: return null
        // A source Aura no longer applies sounds from isn't applied through a restore either.
        if (!sound.soundLicenseCapabilities().canUse(SoundAction.APPLY)) return null
        val url = soundUrlResolver.resolve(sound)?.takeIf { it.isNotBlank() } ?: return null
        val saved = soundApplier.downloadOnly(url, sound.name, ContentType.RINGTONE, maxBytes = downloadAllowance)
            .getOrNull() ?: return null
        return ResolvedContactRingtone(saved.toString(), savedNow = true, bytes = sizeOf(saved))
    }

    /** Zero when the size can't be read. The count of downloads still bounds the restore then. */
    private suspend fun sizeOf(ringtone: Uri): Long = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(ringtone, arrayOf(MediaStore.Audio.Media.SIZE), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
                ?: 0L
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            0L
        }
    }

    override suspend fun discard(ringtoneUri: String) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.delete(Uri.parse(ringtoneUri), null, null)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
            }
        }
    }

    override suspend fun fileNameOf(ringtoneUri: String): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(
                Uri.parse(ringtoneUri),
                arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }.orEmpty()
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            ""
        }
    }

    private fun soundFor(entry: ContactRingtoneExportEntry): Sound? {
        val described = entry.toSoundOrNull() ?: return null
        if (described.source != ContentSource.BUNDLED) return described
        // Aura's own tones ship inside the app, so the id is all that is needed to find one.
        return listOf(
            bundledContent.getRingtones(),
            bundledContent.getNotifications(),
            bundledContent.getAlarms(),
        ).flatten().firstOrNull { it.id == described.id }
    }

    /** Aura can only see audio it saved itself, so a match here is a ringtone Aura put on this phone. */
    private suspend fun findSavedRingtone(fileName: String): String? = withContext(Dispatchers.IO) {
        if (fileName.isBlank()) return@withContext null
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.IS_RINGTONE} != 0",
                arrayOf(fileName),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(0)).toString()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            null
        }
    }
}
