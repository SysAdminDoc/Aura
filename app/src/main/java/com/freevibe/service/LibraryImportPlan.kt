package com.freevibe.service

import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.FitCanvasPreferences
import com.freevibe.data.model.LocalWallpaperEntity
import com.freevibe.data.model.LocalWallpaperFolderEntity
import com.freevibe.data.model.SearchHistoryEntity
import com.freevibe.data.model.RotationExclusionEntity
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperHistoryEntity
import java.net.URI
import java.util.Locale

/** Backup payload versions this build can restore. */
internal const val LIBRARY_EXPORT_MIN_SUPPORTED_VERSION = 1
internal const val LIBRARY_EXPORT_VERSION = 4

/** Why a row in a backup will not be written. */
enum class LibraryImportSkipReason {
    /** Failed validation: blank id, unknown source, malformed URL. */
    INVALID,

    /**
     * Points at a file on the exporting device (`file://`, `content://`, a bare
     * path). The bytes are not in the backup, so restoring the row would produce
     * a permanently broken card on this device.
     */
    NON_PORTABLE,

    /**
     * A 24H pack picture this install won't open, so it has to be picked again here. Either it was
     * chosen with the document picker and the grant to open it stayed behind (grants don't travel in
     * a backup or survive a reinstall), or it sits at an address that isn't one of Aura's providers.
     */
    NEEDS_REPICK,

    /** Already present here; the existing row wins. */
    DUPLICATE,

    /** The field existed in an older payload version and this build no longer restores it. */
    DROPPED_BY_MIGRATION,
}

internal val LibraryImportSkipReason.isFailure: Boolean
    get() = this == LibraryImportSkipReason.INVALID ||
        this == LibraryImportSkipReason.NON_PORTABLE ||
        this == LibraryImportSkipReason.NEEDS_REPICK

/** One row that will not be written, and why. */
data class LibraryImportSkip(
    /** Section the row came from, such as `favorite`, `collectionItem`, or `rotationExclusion`. */
    val section: String,
    /** Short human-readable identity of the row, for the preview report. */
    val label: String,
    val reason: LibraryImportSkipReason,
)

/** A collection that will be created or merged into, with the items to add. */
data class PlannedCollection(
    val name: String,
    val existingId: Long?,
    val items: List<Wallpaper>,
)

/**
 * The complete, validated result of reading a backup — computed before anything
 * is written, so the write phase is a pure replay with no decisions left in it.
 */
data class LibraryImportPlan(
    val sourceVersion: Int,
    val favorites: List<FavoriteEntity> = emptyList(),
    val collections: List<PlannedCollection> = emptyList(),
    val searchHistory: List<SearchHistoryEntity> = emptyList(),
    val rotationExclusions: List<RotationExclusionEntity> = emptyList(),
    val localWallpaperFolders: List<LocalWallpaperFolderEntity> = emptyList(),
    val localWallpapers: List<LocalWallpaperEntity> = emptyList(),
    val wallpaperHistory: List<WallpaperHistoryEntity> = emptyList(),
    val wallpaperPackJson: String = "",
    val soundProfilesJson: String = "",
    val soundShufflePoolsJson: String = "",
    val fitCanvasPreferences: FitCanvasPreferences? = null,
    /** Reddit feed presets, already checked and re-encoded. Blank when the backup has none. */
    val redditFeedPresetsJson: String = "",
    /**
     * Contact ringtones to try, one per contact. They aren't counted in [writeCount]: whether each
     * contact and sound exists on this phone is only known once the restore tries it.
     */
    val contactRingtones: List<ContactRingtoneExportEntry> = emptyList(),
    val skipped: List<LibraryImportSkip> = emptyList(),
) {
    /** Rows that will actually be written. */
    val writeCount: Int
        get() = favorites.size + collections.count { it.existingId == null } +
            collections.sumOf { it.items.size } +
            searchHistory.size +
            rotationExclusions.size +
            localWallpaperFolders.size + localWallpapers.size + wallpaperHistory.size +
            (if (wallpaperPackJson.isNotBlank()) 1 else 0) +
            (if (soundProfilesJson.isNotBlank()) 1 else 0) +
            (if (soundShufflePoolsJson.isNotBlank()) 1 else 0) +
            (if (fitCanvasPreferences != null) 1 else 0) +
            (if (redditFeedPresetsJson.isNotBlank()) 1 else 0)

    val skippedCount: Int
        get() = skipped.count { !it.reason.isFailure }

    val failedCount: Int
        get() = skipped.count { it.reason.isFailure }

    /** Rows the user's backup contained but this device cannot restore. */
    val nonPortable: List<LibraryImportSkip>
        get() = skipped.filter { it.reason == LibraryImportSkipReason.NON_PORTABLE }
}

/** Outcome of a completed import, including the report the UI shows. */
data class LibraryImportOutcome(
    val sourceVersion: Int,
    val written: Int,
    val skipped: List<LibraryImportSkip>,
    /** What happened to every contact ringtone in the backup, restored or not. */
    val contactRingtones: List<ContactRingtoneRestoreResult> = emptyList(),
) {
    val skippedCount: Int
        get() = skipped.count { !it.reason.isFailure }

    val failed: Int
        get() = skipped.count { it.reason.isFailure } + contactRingtones.count { !it.restored }
}

data class LibraryExportOutcome(
    val exported: Int,
    val skipped: Int,
    val failed: Int,
    /** Contacts whose ringtone the backup names. Already part of [exported]. */
    val contactRingtones: Int = 0,
)

/** Raised when a payload cannot be restored at all. Message is user-facing. */
class LibraryImportUnsupportedVersionException(message: String) : IllegalStateException(message)

/**
 * True when a locator names something only the exporting device has.
 *
 * Backups carry metadata, not bytes, so a `file://` AI-generated wallpaper, a
 * `content://` gallery pick, or a bare path cannot survive a device transfer.
 * Before this existed such rows were dropped indistinguishably from corrupt ones,
 * which is why users saw silent losses after a restore.
 */
internal fun isNonPortableLocator(locator: String?): Boolean {
    val trimmed = locator.orEmpty().trim()
    if (trimmed.isEmpty()) return false
    val scheme = runCatching { URI(trimmed).scheme }.getOrNull()?.lowercase(Locale.ROOT)
    return when (scheme) {
        null -> true // bare filesystem path
        "https" -> false
        else -> true // file, content, and anything else device-scoped
    }
}
