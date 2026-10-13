package com.freevibe.service

import android.content.ContentUris
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A ringtone, notification or alarm sound that's already on the phone. */
data class DeviceSound(
    val title: String,
    val uri: String,
    /** Shipped with the phone, as opposed to one the user or an app added. */
    val builtIn: Boolean,
)

/** Reads the sounds Android itself offers for a sound type: the built-in ones and the ones added since. */
@Singleton
class DeviceSoundCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun list(target: SoundShuffleTarget): Result<List<DeviceSound>> = withContext(Dispatchers.IO) {
        runCatching {
            val manager = RingtoneManager(context).apply { setType(target.ringtoneManagerType) }
            // This manager is ours alone, so its cursor is ours to close.
            val cursor = manager.cursor ?: return@runCatching emptyList<DeviceSound>()
            cursor.use {
                buildList {
                    while (cursor.moveToNext()) {
                        val base = cursor.getString(RingtoneManager.URI_COLUMN_INDEX) ?: continue
                        val uri = ContentUris.withAppendedId(Uri.parse(base), cursor.getLong(RingtoneManager.ID_COLUMN_INDEX))
                        add(
                            DeviceSound(
                                title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX).orEmpty(),
                                uri = uri.toString(),
                                builtIn = isBuiltInSound(uri.toString()),
                            ),
                        )
                    }
                }.let(::orderDeviceSounds)
            }
        }.onFailure { it.rethrowIfCancelled() }
    }
}

internal fun isBuiltInSound(uri: String): Boolean = uri.startsWith("content://media/internal/")

/** The user's own sounds first, then the built-in ones, each by title. A sound listed twice shows once. */
internal fun orderDeviceSounds(sounds: List<DeviceSound>): List<DeviceSound> =
    sounds
        .distinctBy { it.uri }
        .sortedWith(compareBy<DeviceSound> { it.builtIn }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
