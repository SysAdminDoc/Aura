package com.freevibe.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.freevibe.data.local.FreeVibeDatabase
import com.freevibe.data.local.PreferencesManager
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The picked files (`content` addresses) that the pack in [before] opens and the one in [after]
 * doesn't. A pack that can't be read says nothing about what it still uses, so then nothing counts
 * as dropped.
 */
internal fun droppedPackLocators(before: String, after: String): Set<String> {
    val afterPack = if (after.isBlank()) null else (parsePack(after) ?: return emptySet())
    return pickedLocators(parsePack(before)) - pickedLocators(afterPack)
}

private fun pickedLocators(pack: WallpaperPack?): Set<String> =
    pack?.slots.orEmpty()
        .map { it.wallpaperUri.trim() }
        .filter { it.startsWith("content://", ignoreCase = true) }
        .toSet()

/**
 * Gives back the lasting read access Aura took on a picked file once nothing opens that file any
 * more. Android keeps a limited number of these grants per app and drops the oldest ones past it,
 * so the grants left behind by replaced pictures end up costing a folder or a newer picture its
 * access.
 */
@Singleton
class PickedFileGrants @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FreeVibeDatabase,
    private val prefs: PreferencesManager,
) {
    /** Call once the pack in [after] has replaced the one in [before]. */
    suspend fun releaseDroppedFromPack(before: String, after: String) {
        droppedPackLocators(before, after).forEach { locator ->
            try {
                release(locator)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                // A grant that stays behind costs a place in Android's list and nothing else.
            }
        }
    }

    private suspend fun release(locator: String) {
        val uri = Uri.parse(locator)
        // A folder's grant covers every picture in it. It belongs to the folder, which gives it
        // back when the folder is removed.
        if (DocumentsContract.isTreeUri(uri)) return
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        if (!granted || isOpenedElsewhere(locator)) return
        context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * A grant belongs to an address, not to the feature that asked for it. A relinked download or
     * favorite, a day or night wallpaper or a sound pool can hold the same address as a pack slot,
     * and each of them would lose the file along with the grant.
     */
    private suspend fun isOpenedElsewhere(locator: String): Boolean {
        val locators = listOf(locator)
        return database.downloadDao().countReferencingLocators(locators) > 0 ||
            database.favoriteDao().countReferencingLocators(locators) > 0 ||
            database.collectionDao().countReferencingLocators(locators) > 0 ||
            database.wallpaperHistoryDao().countReferencingLocators(locators) > 0 ||
            database.wallpaperCacheDao().countReferencingLocators(locators) > 0 ||
            prefs.darkModeWallpaperId.first().contains(locator) ||
            prefs.lightModeWallpaperId.first().contains(locator) ||
            prefs.lastNightVariantWallpaperLocator.first().contains(locator) ||
            prefs.soundShufflePoolsJson.first().contains(locator)
    }
}
