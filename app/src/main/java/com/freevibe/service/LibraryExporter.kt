package com.freevibe.service

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.local.FreeVibeDatabase
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.SearchHistoryDao
import com.freevibe.data.local.RotationExclusionDao
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasPreferences
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.RedditFeedPresetStore
import com.freevibe.data.model.mergeImported
import com.freevibe.data.model.parseRedditFeedPresetStoreStrict
import com.freevibe.data.model.LocalMediaStatus
import com.freevibe.data.model.LocalWallpaperEntity
import com.freevibe.data.model.LocalWallpaperFolderEntity
import com.freevibe.data.model.LocalWallpaperFolderScanStatus
import com.freevibe.data.model.SearchHistoryEntity
import com.freevibe.data.model.ROTATION_MEDIA_VIDEO
import com.freevibe.data.model.ROTATION_MEDIA_WALLPAPER
import com.freevibe.data.model.RotationExclusionEntity
import com.freevibe.data.model.WallpaperHistoryEntity
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.rotationLocatorDigest
import com.freevibe.data.model.stableLocalMediaId
import com.freevibe.data.model.rotationIdentity
import com.freevibe.data.model.toRotationExclusion
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.util.rethrowIfCancelled
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class LibraryExportFile(
    val version: Int = LIBRARY_EXPORT_VERSION,
    val exportedAt: Long = 0,
    val favorites: List<FavoriteExportEntry> = emptyList(),
    val collections: List<CollectionExportEntry> = emptyList(),
    val searchHistory: List<SearchHistoryExportEntry> = emptyList(),
    val rotationExclusions: List<RotationExclusionExportEntry> = emptyList(),
    val localWallpapers: List<LocalWallpaperExportEntry> = emptyList(),
    val wallpaperHistory: List<WallpaperHistoryExportEntry> = emptyList(),
    val wallpaperPackJson: String = "",
    val soundProfilesJson: String = "",
    val soundShufflePoolsJson: String = "",
    val fitCanvasPreferences: FitCanvasPreferencesExport? = null,
    /** Named Reddit feeds. Blank when the device never changed the defaults. */
    val redditFeedPresetsJson: String = "",
    /** Contact ringtones Aura set, named by lookup key and display name. Empty in older backups. */
    val contactRingtones: List<ContactRingtoneExportEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class FitCanvasPreferencesExport(
    val staticPresentation: String = com.freevibe.data.model.WALLPAPER_PRESENTATION_FILL,
    val staticCanvasMode: String = FitCanvasMode.AMOLED_BLACK.preferenceValue,
    val staticCanvasColor: Int = com.freevibe.data.model.DEFAULT_FIT_CANVAS_COLOR,
    val videoPresentation: String = com.freevibe.data.model.WALLPAPER_PRESENTATION_FILL,
    val videoCanvasMode: String = FitCanvasMode.AMOLED_BLACK.preferenceValue,
    val videoCanvasColor: Int = com.freevibe.data.model.DEFAULT_FIT_CANVAS_COLOR,
) {
    fun toPreferences(): FitCanvasPreferences = FitCanvasPreferences(
        staticPresentation = staticPresentation,
        staticStyle = FitCanvasStyle(FitCanvasMode.fromPreference(staticCanvasMode), staticCanvasColor),
        videoPresentation = videoPresentation,
        videoStyle = FitCanvasStyle(FitCanvasMode.fromPreference(videoCanvasMode), videoCanvasColor),
    ).normalized()

    companion object {
        fun from(preferences: FitCanvasPreferences): FitCanvasPreferencesExport {
            val normalized = preferences.normalized()
            return FitCanvasPreferencesExport(
                staticPresentation = normalized.staticPresentation,
                staticCanvasMode = normalized.staticStyle.mode.preferenceValue,
                staticCanvasColor = normalized.staticStyle.customColor,
                videoPresentation = normalized.videoPresentation,
                videoCanvasMode = normalized.videoStyle.mode.preferenceValue,
                videoCanvasColor = normalized.videoStyle.customColor,
            )
        }
    }
}

/**
 * Only the fields needed to decide *whether* a payload can be restored, plus the
 * v1-only sections this build no longer writes.
 *
 * Parsed separately from [LibraryExportFile] because Moshi silently skips unknown
 * keys: without this, a v1 backup's `downloads` array would vanish with no way to
 * tell the user their downloads were not restored.
 */
@JsonClass(generateAdapter = true)
internal data class LibraryImportEnvelope(
    val version: Int? = null,
    val downloads: List<LegacyDownloadExportEntry> = emptyList(),
    /** Read here too so the app can ask for contacts access before the restore starts. */
    val contactRingtones: List<ContactRingtoneExportEntry> = emptyList(),
)

/** v1-only section. Download rows point at files on the exporting device. */
@JsonClass(generateAdapter = true)
data class LegacyDownloadExportEntry(
    val id: String = "",
    val name: String = "",
    val localPath: String = "",
)

@JsonClass(generateAdapter = true)
data class FavoriteExportEntry(
    val id: String,
    val source: String,
    val type: String,
    val thumbnailUrl: String = "",
    val fullUrl: String = "",
    val name: String = "",
    val addedAt: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Double = 0.0,
    val tags: String = "",
    val category: String = "",
    val uploaderName: String = "",
    val sourcePageUrl: String = "",
    val license: String = "",
    val fileSize: Long = 0,
    val fileType: String = "",
    val localMedia: Boolean = false,
    val localMediaSha256: String = "",
)

@JsonClass(generateAdapter = true)
data class CollectionExportEntry(
    val id: Long,
    val name: String,
    val createdAt: Long = 0,
    val items: List<CollectionItemExportEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CollectionItemExportEntry(
    val wallpaperId: String,
    val source: String = "",
    val thumbnailUrl: String = "",
    val fullUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val localMedia: Boolean = false,
)

/** Locator-free metadata for a local wallpaper that can be repaired after restore. */
@JsonClass(generateAdapter = true)
data class LocalWallpaperExportEntry(
    val id: String,
    val displayName: String,
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val contentHash: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val tags: String = "",
    val target: String = WallpaperTarget.BOTH.name,
    val addedAt: Long = 0,
)

/** History metadata is portable; device file locators are intentionally omitted. */
@JsonClass(generateAdapter = true)
data class WallpaperHistoryExportEntry(
    val wallpaperId: String,
    val source: String,
    val thumbnailUrl: String = "",
    val fullUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val target: String = WallpaperTarget.BOTH.name,
    val appliedAt: Long = 0,
    val localMedia: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class SearchHistoryExportEntry(
    val query: String,
    val type: String = "",
    val searchedAt: Long = 0,
)

/** Portable rotation opt-outs. Raw device paths and source URLs are never written. */
@JsonClass(generateAdapter = true)
data class RotationExclusionExportEntry(
    val mediaType: String,
    val source: String,
    val contentId: String,
    val contentHash: String = "",
    val title: String = "",
    val thumbnailUrl: String = "",
    val locatorDigest: String = "",
    val excludedAt: Long = 0,
)

@Singleton
class LibraryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FreeVibeDatabase,
    private val favoriteDao: FavoriteDao,
    private val collectionRepo: CollectionRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val rotationExclusionDao: RotationExclusionDao,
    private val prefs: PreferencesManager,
    private val moshi: Moshi,
    private val soundShufflePoolManager: SoundShufflePoolManager,
    private val contactRingtoneBackup: ContactRingtoneBackupManager,
) {
    private val adapter = moshi.adapter(LibraryExportFile::class.java)
    private val envelopeAdapter = moshi.adapter(LibraryImportEnvelope::class.java)
    private val redditFeedPresetAdapter = moshi.adapter(RedditFeedPresetStore::class.java)

    /**
     * Test seam: runs at the very end of the write transaction. Throwing from it
     * proves the whole import rolls back, which is the only way to test the
     * atomicity guarantee without corrupting a real database mid-write.
     */
    internal var failBeforeCommit: (suspend () -> Unit)? = null

    suspend fun exportLibrary(outputUri: Uri): Result<LibraryExportOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val favoriteEntities = favoriteDao.getAll().first()
            val localWallpaperEntities = database.localWallpaperDao().getAll()
            val localFoldersByUri = database.localWallpaperFolderDao().getAll()
                .associateBy(LocalWallpaperFolderEntity::folderUri)
            val portableLocalIds = buildPortableLocalIdentityMap(localWallpaperEntities, favoriteEntities)
            val favorites = favoriteEntities.map { it.toExportEntry(portableLocalIds) }
            val collections = exportCollections(portableLocalIds)
            val searchHistory = searchHistoryDao.getAll().map { it.toExportEntry() }
            val rotationExclusions = rotationExclusionDao.getAll().map { it.toExportEntry(portableLocalIds) }
            val localWallpapers = localWallpaperEntities.map { item ->
                item.toExportEntry(
                    portableId = portableLocalIds.localId(ContentSource.LOCAL.name, item.stableLocalMediaId()),
                    target = localFoldersByUri[item.folderUri]?.target ?: WallpaperTarget.BOTH.name,
                )
            }
            val wallpaperHistory = database.wallpaperHistoryDao()
                .getRecentSnapshot(LibraryTransferContract.MAX_WALLPAPER_HISTORY_ITEMS)
                .map { it.toExportEntry(portableLocalIds) }
            val wallpaperPack = prefs.wallpaperPackJson.first()
            val soundProfiles = prefs.soundProfilesJson.first()
            val soundShufflePools = portableSoundShuffleStoreJson(
                moshi,
                prefs.soundShufflePoolsJson.first(),
            )
            val fitCanvasPreferences = prefs.fitCanvasPreferencesSnapshot()
            val redditFeedPresets = portableRedditFeedPresetsJson(prefs.redditFeedPresetsJson.first())
            // Aura's own record of the ringtones it set. Nothing here is read from the contact list.
            val contactRingtones = contactRingtoneBackup.exportEntries()

            requireWithinTransferLimit(
                "Library favorites",
                favorites.size,
                LibraryTransferContract.MAX_FAVORITES,
            )
            requireWithinTransferLimit(
                "Library collections",
                collections.size,
                LibraryTransferContract.MAX_COLLECTIONS,
            )
            requireWithinTransferLimit(
                "Library search history",
                searchHistory.size,
                LibraryTransferContract.MAX_SEARCH_HISTORY_ITEMS,
            )
            requireWithinTransferLimit(
                "Library rotation exclusions",
                rotationExclusions.size,
                LibraryTransferContract.MAX_ROTATION_EXCLUSIONS,
            )
            requireWithinTransferLimit(
                "Library local wallpapers",
                localWallpapers.size,
                LibraryTransferContract.MAX_LOCAL_WALLPAPERS,
            )
            requireWithinTransferLimit(
                "Library wallpaper history",
                wallpaperHistory.size,
                LibraryTransferContract.MAX_WALLPAPER_HISTORY_ITEMS,
            )
            requireWithinTransferLimit(
                "Library contact ringtones",
                contactRingtones.size,
                LibraryTransferContract.MAX_CONTACT_RINGTONES,
            )

            val exportFile = LibraryExportFile(
                version = LIBRARY_EXPORT_VERSION,
                exportedAt = System.currentTimeMillis(),
                favorites = favorites,
                collections = collections,
                searchHistory = searchHistory,
                rotationExclusions = rotationExclusions,
                localWallpapers = localWallpapers,
                wallpaperHistory = wallpaperHistory,
                wallpaperPackJson = wallpaperPack,
                soundProfilesJson = soundProfiles,
                soundShufflePoolsJson = soundShufflePools,
                fitCanvasPreferences = FitCanvasPreferencesExport.from(fitCanvasPreferences),
                redditFeedPresetsJson = redditFeedPresets,
                contactRingtones = contactRingtones,
            )

            val json = adapter.indent("  ").toJson(exportFile)
            requireWithinTransferLimit(
                "Library backup document characters",
                json.length,
                LibraryTransferContract.MAX_LIBRARY_DOCUMENT_CHARS,
            )
            publishStagedDocument(context, outputUri, json.toByteArray(Charsets.UTF_8))

            LibraryExportOutcome(
                exported = favorites.size + collections.size +
                    collections.sumOf { it.items.size } + searchHistory.size +
                    rotationExclusions.size +
                    localWallpapers.size + wallpaperHistory.size +
                    (if (wallpaperPack.isNotBlank()) 1 else 0) +
                    (if (soundProfiles.isNotBlank()) 1 else 0) +
                    (if (soundShufflePools.isNotBlank()) 1 else 0) +
                    (if (redditFeedPresets.isNotBlank()) 1 else 0) +
                    contactRingtones.size +
                    1,
                skipped = 0,
                failed = 0,
                contactRingtones = contactRingtones.size,
            )
        }.onFailure { it.rethrowIfCancelled() }
    }

    /**
     * Reads, version-checks, migrates, and validates a backup without writing
     * anything, so the caller can show the user exactly what a restore would do.
     */
    suspend fun planImport(inputUri: Uri): Result<LibraryImportPlan> = withContext(Dispatchers.IO) {
        runCatching { buildPlan(readPayload(inputUri)) }
            .onFailure { it.rethrowIfCancelled() }
    }

    /**
     * Restores a backup.
     *
     * The payload is fully parsed, migrated, validated, and planned before the
     * first write. The writes then run inside one Room transaction, with the two
     * DataStore-backed JSON blobs written first and restored on failure, so an
     * error anywhere leaves the pre-import state intact rather than a half-merged
     * library.
     */
    suspend fun importLibrary(inputUri: Uri): Result<LibraryImportOutcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                val plan = buildPlan(readPayload(inputUri))
                applyPlan(plan)
                val contactRingtones = restorePlannedContactRingtones(plan)
                LibraryImportOutcome(
                    sourceVersion = plan.sourceVersion,
                    written = plan.writeCount + contactRingtones.count { it.restored },
                    skipped = plan.skipped,
                    contactRingtones = contactRingtones,
                )
            }.onFailure { it.rethrowIfCancelled() }
        }

    /**
     * How many contact ringtones in the backup can't be put back until Aura may update contacts.
     *
     * Zero when the backup has none, when the permission is already held, or when the file can't be
     * restored at all (the import reports that itself).
     */
    suspend fun contactRingtonesAwaitingPermission(inputUri: Uri): Int = withContext(Dispatchers.IO) {
        if (contactRingtoneBackup.canWriteContacts()) return@withContext 0
        try {
            val envelope = envelopeAdapter.fromJson(readPayload(inputUri)) ?: return@withContext 0
            val version = envelope.version ?: return@withContext 0
            if (version !in LIBRARY_EXPORT_MIN_SUPPORTED_VERSION..LIBRARY_EXPORT_VERSION) return@withContext 0
            envelope.contactRingtones.count { normalizeImportedContactRingtone(it) != null }
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            0
        }
    }

    /**
     * Contact ringtones go last and outside the library transaction. They change the phone's
     * contacts and may fetch sounds again, so one that can't be put back is reported and never undoes
     * the rest of the import.
     */
    private suspend fun restorePlannedContactRingtones(plan: LibraryImportPlan): List<ContactRingtoneRestoreResult> {
        if (plan.contactRingtones.isEmpty()) return emptyList()
        return try {
            contactRingtoneBackup.restore(plan.contactRingtones)
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            plan.contactRingtones.map {
                ContactRingtoneRestoreResult(it, ContactRingtoneRestoreStatus.WRITE_FAILED)
            }
        }
    }

    private fun readPayload(inputUri: Uri): String =
        context.contentResolver.openInputStream(inputUri)?.use { input ->
            val reader = java.io.BufferedReader(java.io.InputStreamReader(input))
            val sb = StringBuilder()
            val buffer = CharArray(8192)
            var read: Int
            while (reader.read(buffer).also { read = it } != -1) {
                sb.append(buffer, 0, read)
                if (sb.length > LibraryTransferContract.MAX_LIBRARY_DOCUMENT_CHARS) {
                    throw IllegalStateException(
                        "Import file too large " +
                            "(>${LibraryTransferContract.MAX_LIBRARY_DOCUMENT_CHARS / 1_000_000}MB)"
                    )
                }
            }
            sb.toString()
        } ?: throw IllegalStateException("Failed to open input stream")

    internal suspend fun buildPlan(json: String): LibraryImportPlan {
        val envelope = runCatching { envelopeAdapter.fromJson(json) }.getOrNull()
            ?: throw IllegalStateException("Invalid library backup format")
        val version = envelope.version
            ?: throw LibraryImportUnsupportedVersionException(
                "This file is missing a backup version and cannot be restored safely."
            )
        if (version > LIBRARY_EXPORT_VERSION) {
            throw LibraryImportUnsupportedVersionException(
                "This backup was written by a newer version of Aura (format $version). Update Aura and try again."
            )
        }
        if (version < LIBRARY_EXPORT_MIN_SUPPORTED_VERSION) {
            throw LibraryImportUnsupportedVersionException(
                "Backup format $version is no longer supported."
            )
        }

        val exportFile = adapter.fromJson(json)
            ?: throw IllegalStateException("Invalid library backup format")

        requireWithinTransferLimit(
            "Library favorites",
            exportFile.favorites.size,
            LibraryTransferContract.MAX_FAVORITES,
        )
        requireWithinTransferLimit(
            "Library collections",
            exportFile.collections.size,
            LibraryTransferContract.MAX_COLLECTIONS,
        )
        exportFile.collections.forEach { collection ->
            requireWithinTransferLimit(
                "Collection '${normalizeImportedText(collection.name)}' items",
                collection.items.size,
                LibraryTransferContract.MAX_COLLECTION_ITEMS,
            )
        }
        requireWithinTransferLimit(
            "Library search history",
            exportFile.searchHistory.size,
            LibraryTransferContract.MAX_SEARCH_HISTORY_ITEMS,
        )
        requireWithinTransferLimit(
            "Library rotation exclusions",
            exportFile.rotationExclusions.size,
            LibraryTransferContract.MAX_ROTATION_EXCLUSIONS,
        )
        requireWithinTransferLimit(
            "Library local wallpapers",
            exportFile.localWallpapers.size,
            LibraryTransferContract.MAX_LOCAL_WALLPAPERS,
        )
        requireWithinTransferLimit(
            "Library wallpaper history",
            exportFile.wallpaperHistory.size,
            LibraryTransferContract.MAX_WALLPAPER_HISTORY_ITEMS,
        )
        requireWithinTransferLimit(
            "Library sound pool document characters",
            exportFile.soundShufflePoolsJson.length,
            LibraryTransferContract.MAX_SOUND_SHUFFLE_DOCUMENT_CHARS,
        )
        requireWithinTransferLimit(
            "Library contact ringtones",
            exportFile.contactRingtones.size,
            LibraryTransferContract.MAX_CONTACT_RINGTONES,
        )

        val skipped = mutableListOf<LibraryImportSkip>()

        // v1 -> v2: downloads were removed from the format because their localPath
        // rows only resolve on the exporting device. Report them instead of dropping
        // them silently.
        envelope.downloads.forEach { download ->
            skipped += LibraryImportSkip(
                section = "download",
                label = normalizeImportedText(download.name).ifBlank {
                    normalizeImportedText(download.id).ifBlank { "download" }
                },
                reason = LibraryImportSkipReason.DROPPED_BY_MIGRATION,
            )
        }

        val favorites = planFavorites(exportFile, skipped)
        val collections = planCollections(exportFile, skipped)
        val searchHistory = planSearchHistory(exportFile, skipped)
        val rotationExclusions = planRotationExclusions(exportFile, skipped)
        val localWallpaperPlan = planLocalWallpapers(exportFile, skipped)
        val wallpaperHistory = planWallpaperHistory(exportFile, skipped)
        val contactRingtones = planContactRingtones(exportFile, skipped)

        return LibraryImportPlan(
            sourceVersion = version,
            favorites = favorites,
            collections = collections,
            searchHistory = searchHistory,
            rotationExclusions = rotationExclusions,
            localWallpaperFolders = localWallpaperPlan.first,
            localWallpapers = localWallpaperPlan.second,
            wallpaperHistory = wallpaperHistory,
            wallpaperPackJson = exportFile.wallpaperPackJson,
            soundProfilesJson = exportFile.soundProfilesJson,
            soundShufflePoolsJson = exportFile.soundShufflePoolsJson.takeIf(String::isNotBlank)?.let {
                val normalized = parseSoundShuffleStoreStrict(moshi, it)
                encodeSoundShuffleStore(moshi, normalized)
            }.orEmpty(),
            fitCanvasPreferences = exportFile.fitCanvasPreferences?.toPreferences(),
            redditFeedPresetsJson = portableRedditFeedPresetsJson(exportFile.redditFeedPresetsJson),
            contactRingtones = contactRingtones,
            skipped = skipped,
        )
    }

    /**
     * Checks each contact ringtone row and keeps one per contact. Whether the contact and the sound
     * exist on this phone is only known once the restore tries them, so the import reports that part.
     */
    private fun planContactRingtones(
        exportFile: LibraryExportFile,
        skipped: MutableList<LibraryImportSkip>,
    ): List<ContactRingtoneExportEntry> {
        val planned = mutableListOf<ContactRingtoneExportEntry>()
        // Newest first, so when a contact shows up twice the later choice is the one kept.
        exportFile.contactRingtones.sortedByDescending { it.assignedAt }.forEach { raw ->
            val entry = normalizeImportedContactRingtone(raw)
            if (entry == null) {
                skipped += LibraryImportSkip("contactRingtone", raw.label(), LibraryImportSkipReason.INVALID)
                return@forEach
            }
            if (planned.any { it.sameContactAs(entry) }) {
                skipped += LibraryImportSkip("contactRingtone", entry.label(), LibraryImportSkipReason.DUPLICATE)
                return@forEach
            }
            planned += entry
        }
        return planned.sortedBy { it.assignedAt }
    }

    /** Checks presets strictly and writes them back out clean, or blank when there are none to carry. */
    private fun portableRedditFeedPresetsJson(raw: String): String {
        if (raw.isBlank()) return ""
        val store = parseRedditFeedPresetStoreStrict(redditFeedPresetAdapter, raw)
        return if (store.presets.isEmpty()) "" else redditFeedPresetAdapter.toJson(store)
    }

    private suspend fun planFavorites(
        exportFile: LibraryExportFile,
        skipped: MutableList<LibraryImportSkip>,
    ): List<FavoriteEntity> {
        val seen = favoriteDao.getAll().first().mapTo(mutableSetOf()) { it.favoriteIdentity() }
        return exportFile.favorites.mapNotNull { entry ->
            val entity = entry.toEntity()
            if (entity == null) {
                skipped += LibraryImportSkip(
                    section = "favorite",
                    label = entry.label(),
                    reason = if (
                        isNonPortableLocator(entry.fullUrl) ||
                        isNonPortableLocator(entry.thumbnailUrl)
                    ) {
                        LibraryImportSkipReason.NON_PORTABLE
                    } else {
                        LibraryImportSkipReason.INVALID
                    },
                )
                return@mapNotNull null
            }
            if (!seen.add(entity.favoriteIdentity())) {
                skipped += LibraryImportSkip(
                    section = "favorite",
                    label = entry.label(),
                    reason = LibraryImportSkipReason.DUPLICATE,
                )
                return@mapNotNull null
            }
            entity
        }
    }

    private suspend fun planCollections(
        exportFile: LibraryExportFile,
        skipped: MutableList<LibraryImportSkip>,
    ): List<PlannedCollection> {
        // Merge by name so re-importing the same backup doesn't duplicate
        // collections (favorites already dedupe at the DAO layer).
        val existingByName = collectionRepo.getAll().first()
            .associateBy({ it.name }, { it.collectionId })
        val planned = mutableListOf<PlannedCollection>()
        exportFile.collections.forEach { collection ->
            val name = normalizeImportedText(collection.name)
            if (name.isBlank()) {
                skipped += LibraryImportSkip("collection", "(unnamed)", LibraryImportSkipReason.INVALID)
                collection.items.forEach { item ->
                    skipped += LibraryImportSkip(
                        "collectionItem",
                        "(unnamed) / ${item.label()}",
                        LibraryImportSkipReason.INVALID,
                    )
                }
                return@forEach
            }
            val existingId = existingByName[name]
            if (existingId != null) {
                skipped += LibraryImportSkip(
                    "collection",
                    name,
                    LibraryImportSkipReason.DUPLICATE,
                )
            }
            val existingItemIds = existingId
                ?.let {
                    collectionRepo.getItems(it).first()
                        .map { item -> item.source to item.wallpaperId }
                        .toSet()
                }
                .orEmpty()

            val seen = existingItemIds.toMutableSet()
            val items = collection.items.mapNotNull { item ->
                val wallpaper = item.toWallpaperOrNull()
                if (wallpaper == null) {
                    skipped += LibraryImportSkip(
                        section = "collectionItem",
                        label = "$name / ${item.label()}",
                        reason = if (isNonPortableLocator(item.fullUrl) || isNonPortableLocator(item.thumbnailUrl)) {
                            LibraryImportSkipReason.NON_PORTABLE
                        } else {
                            LibraryImportSkipReason.INVALID
                        },
                    )
                    return@mapNotNull null
                }
                if (!seen.add(wallpaper.source.name to wallpaper.id)) {
                    skipped += LibraryImportSkip(
                        "collectionItem",
                        "$name / ${item.label()}",
                        LibraryImportSkipReason.DUPLICATE,
                    )
                    return@mapNotNull null
                }
                wallpaper
            }
            planned += PlannedCollection(name = name, existingId = existingId, items = items)
        }
        return planned
    }

    private suspend fun planSearchHistory(
        exportFile: LibraryExportFile,
        skipped: MutableList<LibraryImportSkip>,
    ): List<SearchHistoryEntity> {
        val seen = searchHistoryDao.getAll().mapTo(mutableSetOf()) { it.query to it.type }
        return exportFile.searchHistory.mapNotNull { entry ->
            val query = normalizeImportedText(entry.query)
            val type = normalizeImportedText(entry.type).uppercase(java.util.Locale.ROOT)
            if (query.isBlank() || type !in setOf("WALLPAPER", "SOUND", "UNIVERSAL")) {
                skipped += LibraryImportSkip(
                    "search",
                    query.ifBlank { "(blank)" },
                    LibraryImportSkipReason.INVALID,
                )
                return@mapNotNull null
            }
            val entity = SearchHistoryEntity(
                query = query,
                type = type,
                timestamp = entry.searchedAt.coerceAtLeast(0),
            )
            if (!seen.add(entity.query to entity.type)) {
                skipped += LibraryImportSkip(
                    "search",
                    query,
                    LibraryImportSkipReason.DUPLICATE,
                )
                return@mapNotNull null
            }
            entity
        }
    }

    private suspend fun planRotationExclusions(
        exportFile: LibraryExportFile,
        skipped: MutableList<LibraryImportSkip>,
    ): List<RotationExclusionEntity> {
        val seen = rotationExclusionDao.getAll().toMutableList()
        return exportFile.rotationExclusions.mapNotNull { entry ->
            val entity = entry.toEntity()
            if (entity == null) {
                skipped += LibraryImportSkip(
                    section = "rotationExclusion",
                    label = normalizeImportedText(entry.title).ifBlank {
                        normalizeImportedText(entry.contentId).ifBlank { "rotation exclusion" }
                    },
                    reason = LibraryImportSkipReason.INVALID,
                )
                return@mapNotNull null
            }
            if (seen.any { it.sameIdentityAs(entity) }) {
                skipped += LibraryImportSkip(
                    section = "rotationExclusion",
                    label = entity.title.ifBlank { entity.contentId },
                    reason = LibraryImportSkipReason.DUPLICATE,
                )
                return@mapNotNull null
            }
            seen += entity
            entity
        }
    }

    private suspend fun planLocalWallpapers(
        exportFile: LibraryExportFile,
        skipped: MutableList<LibraryImportSkip>,
    ): Pair<List<LocalWallpaperFolderEntity>, List<LocalWallpaperEntity>> {
        val seen = database.localWallpaperDao().getAll()
            .mapTo(mutableSetOf(), LocalWallpaperEntity::stableLocalMediaId)
        val items = exportFile.localWallpapers.mapNotNull { entry ->
            val entity = entry.toEntity()
            if (entity == null) {
                skipped += LibraryImportSkip(
                    section = "localWallpaper",
                    label = normalizeImportedText(entry.displayName).ifBlank { "local wallpaper" },
                    reason = LibraryImportSkipReason.INVALID,
                )
                return@mapNotNull null
            }
            if (!seen.add(entity.stableLocalMediaId())) {
                skipped += LibraryImportSkip(
                    section = "localWallpaper",
                    label = entity.displayName,
                    reason = LibraryImportSkipReason.DUPLICATE,
                )
                return@mapNotNull null
            }
            entity
        }
        val folders = items
            .groupBy(LocalWallpaperEntity::folderUri)
            .map { (folderUri, folderItems) ->
                val target = restoredLocalFolderTarget(folderUri)
                LocalWallpaperFolderEntity(
                    folderUri = folderUri,
                    displayName = "Restored local media (${target.lowercase(java.util.Locale.ROOT)})",
                    target = target,
                    scanStatus = LocalWallpaperFolderScanStatus.PERMISSION_REVOKED,
                    lastError = "Choose this folder again to relink matching files",
                    itemCount = folderItems.size,
                )
            }
        return folders to items
    }

    private suspend fun planWallpaperHistory(
        exportFile: LibraryExportFile,
        skipped: MutableList<LibraryImportSkip>,
    ): List<WallpaperHistoryEntity> {
        val seen = database.wallpaperHistoryDao()
            .getRecentSnapshot(LibraryTransferContract.MAX_WALLPAPER_HISTORY_ITEMS)
            .mapTo(mutableSetOf()) { Triple(it.source, it.wallpaperId, it.appliedAt) }
        return exportFile.wallpaperHistory.mapNotNull { entry ->
            val entity = entry.toEntity()
            if (entity == null) {
                skipped += LibraryImportSkip(
                    section = "wallpaperHistory",
                    label = normalizeImportedText(entry.wallpaperId).ifBlank { "wallpaper history" },
                    reason = LibraryImportSkipReason.INVALID,
                )
                return@mapNotNull null
            }
            if (!seen.add(Triple(entity.source, entity.wallpaperId, entity.appliedAt))) {
                skipped += LibraryImportSkip(
                    section = "wallpaperHistory",
                    label = entity.wallpaperId,
                    reason = LibraryImportSkipReason.DUPLICATE,
                )
                return@mapNotNull null
            }
            entity
        }
    }

    /**
     * Replays a plan. No validation or conflict decisions happen here — by this
     * point every write is already decided, which is what makes the transaction
     * safe to roll back wholesale.
     */
    internal suspend fun applyPlan(plan: LibraryImportPlan) {
        // DataStore has no transaction with Room, so preference sections are
        // written first and restored by hand if any later write fails. Mark the
        // rollback as necessary before each call because Fit Canvas restore spans
        // its static, video, and service-mirror values.
        val previousPack = prefs.wallpaperPackJson.first()
        val previousProfiles = prefs.soundProfilesJson.first()
        val previousSoundShufflePools = prefs.soundShufflePoolsJson.first()
        val previousFitCanvasPreferences = prefs.fitCanvasPreferencesSnapshot()
        // Read only when the backup carries presets, so a restore without them never touches the key.
        val previousRedditFeedPresets = if (plan.redditFeedPresetsJson.isNotBlank()) prefs.redditFeedPresetsJson.first() else null
        var prefsWritten = false
        try {
            if (plan.wallpaperPackJson.isNotBlank()) {
                prefsWritten = true
                prefs.setWallpaperPackJson(plan.wallpaperPackJson)
            }
            if (plan.soundProfilesJson.isNotBlank()) {
                prefsWritten = true
                prefs.setSoundProfilesJson(plan.soundProfilesJson)
            }
            if (plan.soundShufflePoolsJson.isNotBlank()) {
                prefsWritten = true
                val existing = parseSoundShuffleStore(moshi, previousSoundShufflePools)
                val imported = parseSoundShuffleStoreStrict(moshi, plan.soundShufflePoolsJson)
                val merged = mergeSoundShuffleStores(existing, imported)
                prefs.setSoundShufflePoolsJson(encodeSoundShuffleStore(moshi, merged))
            }
            plan.fitCanvasPreferences?.let {
                prefsWritten = true
                prefs.restoreFitCanvasPreferences(it)
            }
            if (plan.redditFeedPresetsJson.isNotBlank()) {
                prefsWritten = true
                val imported = parseRedditFeedPresetStoreStrict(redditFeedPresetAdapter, plan.redditFeedPresetsJson)
                prefs.updateRedditFeedPresets { it.mergeImported(imported) }
            }
            database.withTransaction {
                plan.localWallpaperFolders.forEach { database.localWallpaperFolderDao().upsert(it) }
                if (plan.localWallpapers.isNotEmpty()) {
                    database.localWallpaperDao().upsertAll(plan.localWallpapers)
                }
                if (plan.favorites.isNotEmpty()) {
                    favoriteDao.insertAll(plan.favorites)
                }
                plan.collections.forEach { collection ->
                    val targetId = collection.existingId ?: collectionRepo.create(collection.name)
                    collection.items.forEach { collectionRepo.addWallpaper(targetId, it) }
                }
                plan.searchHistory.forEach { searchHistoryDao.insert(it) }
                if (plan.rotationExclusions.isNotEmpty()) {
                    rotationExclusionDao.upsertAll(plan.rotationExclusions)
                }
                plan.wallpaperHistory.forEach { database.wallpaperHistoryDao().insert(it) }
                if (plan.wallpaperHistory.isNotEmpty()) database.wallpaperHistoryDao().pruneOld()
                failBeforeCommit?.invoke()
            }
        } catch (error: Throwable) {
            if (prefsWritten) {
                runCatching { prefs.setWallpaperPackJson(previousPack) }
                runCatching { prefs.setSoundProfilesJson(previousProfiles) }
                runCatching { prefs.setSoundShufflePoolsJson(previousSoundShufflePools) }
                runCatching { prefs.restoreFitCanvasPreferences(previousFitCanvasPreferences) }
                previousRedditFeedPresets?.let { runCatching { prefs.setRedditFeedPresetsJson(it) } }
            }
            throw error
        }
        if (plan.soundShufflePoolsJson.isNotBlank()) {
            // The import already landed; a schedule that fails to come back is fixed by the next boot or pool edit.
            try {
                soundShufflePoolManager.restoreSchedules()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("LibraryExporter", "Sound pool schedules weren't restored: ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun exportCollections(
        portableLocalIds: Map<LocalExportIdentity, String>,
    ): List<CollectionExportEntry> {
        val collections = collectionRepo.getAll().first()
        requireWithinTransferLimit(
            "Library collections",
            collections.size,
            LibraryTransferContract.MAX_COLLECTIONS,
        )
        return collections.map { collection ->
            val items = collectionRepo.getItems(collection.collectionId).first()
            requireWithinTransferLimit(
                "Collection '${collection.name}' items",
                items.size,
                LibraryTransferContract.MAX_COLLECTION_ITEMS,
            )
            CollectionExportEntry(
                id = collection.collectionId,
                name = collection.name,
                createdAt = collection.createdAt,
                items = items.map { item ->
                    val localMedia = item.isDeviceLocalMedia()
                    CollectionItemExportEntry(
                        wallpaperId = if (localMedia) {
                            portableLocalIds.localId(item.source, item.wallpaperId)
                        } else {
                            item.wallpaperId
                        },
                        source = item.source,
                        thumbnailUrl = item.thumbnailUrl.takeUnless { localMedia }.orEmpty(),
                        fullUrl = item.fullUrl.takeUnless { localMedia }.orEmpty(),
                        width = item.width,
                        height = item.height,
                        localMedia = localMedia,
                    )
                },
            )
        }
    }
}

private fun FavoriteExportEntry.label(): String =
    normalizeImportedText(name).ifBlank { normalizeImportedText(id).ifBlank { "(unnamed)" } }

private fun CollectionItemExportEntry.label(): String =
    normalizeImportedText(wallpaperId).ifBlank { "(unnamed)" }

private fun ContactRingtoneExportEntry.label(): String =
    normalizeImportedText(contactDisplayName).ifBlank { "(unnamed)" }

private data class LocalExportIdentity(val source: String, val id: String)

private fun localExportIdentity(source: String, id: String) =
    LocalExportIdentity(source.trim().uppercase(java.util.Locale.ROOT), id.trim())

private fun buildPortableLocalIdentityMap(
    localWallpapers: List<LocalWallpaperEntity>,
    favorites: List<FavoriteEntity>,
): Map<LocalExportIdentity, String> = buildMap {
    localWallpapers.forEach { item ->
        val stableId = item.stableLocalMediaId()
        put(
            localExportIdentity(ContentSource.LOCAL.name, stableId),
            portableLocalMediaId(ContentSource.LOCAL.name, stableId),
        )
    }
    favorites.filter(FavoriteEntity::isDeviceLocalMedia).forEach { favorite ->
        putIfAbsent(
            localExportIdentity(favorite.source, favorite.id),
            portableLocalMediaId(favorite.source, favorite.id),
        )
    }
}

private fun Map<LocalExportIdentity, String>.localId(source: String, id: String): String =
    get(localExportIdentity(source, id)) ?: portableLocalMediaId(source, id)

internal fun portableLocalMediaId(source: String, id: String): String {
    val normalizedId = id.trim()
    if (SAFE_PORTABLE_LOCAL_ID.matches(normalizedId) && !looksLikeDeviceLocator(normalizedId)) {
        return normalizedId
    }
    val identityDigest = rotationLocatorDigest(
        "${source.uppercase(java.util.Locale.ROOT)}\u001f$normalizedId",
    )
    return "local-$identityDigest"
}

internal fun isSafePortableLocalMediaId(value: String): Boolean =
    SAFE_PORTABLE_LOCAL_ID.matches(value.trim()) && !looksLikeDeviceLocator(value.trim())

private fun looksLikeDeviceLocator(value: String): Boolean =
    value.startsWith('/') || value.startsWith('\\') ||
        value.startsWith("content:", ignoreCase = true) ||
        value.startsWith("file:", ignoreCase = true) ||
        (value.length > 2 && value[0].isLetter() && value[1] == ':' && value[2] in charArrayOf('/', '\\'))

private fun FavoriteEntity.isDeviceLocalMedia(): Boolean =
    source.equals(ContentSource.LOCAL.name, ignoreCase = true) ||
        localMediaStatus != LocalMediaStatus.AVAILABLE ||
        isNonPortableLocator(fullUrl.takeIf(String::isNotBlank) ?: offlinePath)

private fun com.freevibe.data.model.WallpaperCollectionItemEntity.isDeviceLocalMedia(): Boolean =
    source.equals(ContentSource.LOCAL.name, ignoreCase = true) ||
        (fullUrl.isBlank() && thumbnailUrl.isBlank()) ||
        isNonPortableLocator(fullUrl.takeIf(String::isNotBlank) ?: thumbnailUrl)

private fun FavoriteEntity.toExportEntry(portableLocalIds: Map<LocalExportIdentity, String>): FavoriteExportEntry {
    val localMedia = isDeviceLocalMedia()
    val portableHash = localMediaSha256.trim().lowercase(java.util.Locale.ROOT)
        .takeIf(LOCAL_MEDIA_HASH::matches)
        .orEmpty()
    return FavoriteExportEntry(
        id = if (localMedia) portableLocalIds.localId(source, id) else id,
        source = source,
        type = type,
        thumbnailUrl = thumbnailUrl.takeUnless { localMedia }.orEmpty(),
        fullUrl = fullUrl.takeUnless { localMedia }.orEmpty(),
        name = name,
        addedAt = addedAt,
        width = width,
        height = height,
        duration = duration,
        tags = tags.orEmpty(),
        category = category.orEmpty(),
        uploaderName = uploaderName.orEmpty(),
        sourcePageUrl = sourcePageUrl?.takeIf { !isNonPortableLocator(it) }.orEmpty(),
        license = license.orEmpty(),
        fileSize = fileSize ?: 0,
        fileType = fileType.orEmpty(),
        localMedia = localMedia,
        localMediaSha256 = portableHash,
    )
}

private fun LocalWallpaperEntity.toExportEntry(
    portableId: String,
    target: String,
) = LocalWallpaperExportEntry(
    id = portableId,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    contentHash = contentHash.trim().lowercase(java.util.Locale.ROOT)
        .takeIf(LOCAL_MEDIA_HASH::matches)
        .orEmpty(),
    width = width,
    height = height,
    tags = tags,
    target = target,
    addedAt = addedAt,
)

private fun WallpaperHistoryEntity.toExportEntry(
    portableLocalIds: Map<LocalExportIdentity, String>,
): WallpaperHistoryExportEntry {
    val localMedia = source.equals(ContentSource.LOCAL.name, true) ||
        (fullUrl.isBlank() && thumbnailUrl.isBlank()) ||
        isNonPortableLocator(fullUrl.takeIf(String::isNotBlank) ?: thumbnailUrl)
    return WallpaperHistoryExportEntry(
        wallpaperId = if (localMedia) portableLocalIds.localId(source, wallpaperId) else wallpaperId,
        source = source,
        thumbnailUrl = thumbnailUrl.takeUnless { localMedia }.orEmpty(),
        fullUrl = fullUrl.takeUnless { localMedia }.orEmpty(),
        width = width,
        height = height,
        target = target,
        appliedAt = appliedAt,
        localMedia = localMedia,
    )
}

private fun SearchHistoryEntity.toExportEntry() = SearchHistoryExportEntry(
    query = query,
    type = type,
    searchedAt = timestamp,
)

private fun RotationExclusionEntity.toExportEntry(
    portableLocalIds: Map<LocalExportIdentity, String>,
) = RotationExclusionExportEntry(
    mediaType = mediaType,
    source = source,
    contentId = if (source.equals(ContentSource.LOCAL.name, true)) {
        portableLocalIds.localId(source, contentId)
    } else {
        contentId
    },
    contentHash = contentHash.trim().lowercase(java.util.Locale.ROOT)
        .takeIf(LOCAL_MEDIA_HASH::matches)
        .orEmpty(),
    title = title,
    thumbnailUrl = thumbnailUrl.takeIf { !isNonPortableLocator(it) }.orEmpty(),
    locatorDigest = locatorDigest,
    excludedAt = excludedAt,
)

private val ROTATION_EXCLUSION_DIGEST = Regex("^[0-9a-f]{64}$")
private val LOCAL_MEDIA_HASH = Regex("^[0-9a-f]{64}$")
private val SAFE_PORTABLE_LOCAL_ID = Regex("^[A-Za-z0-9._:-]{1,512}$")
private val ROTATION_EXCLUSION_SPECIAL_SOURCES = setOf("LOCATOR")

private fun RotationExclusionExportEntry.toEntity(): RotationExclusionEntity? {
    val normalizedType = mediaType.trim().uppercase(java.util.Locale.ROOT)
    if (normalizedType !in setOf(ROTATION_MEDIA_WALLPAPER, ROTATION_MEDIA_VIDEO)) return null
    val normalizedSource = source.trim().uppercase(java.util.Locale.ROOT)
    if (
        normalizeImportedContentSource(normalizedSource) == null &&
        normalizedSource !in ROTATION_EXCLUSION_SPECIAL_SOURCES
    ) return null
    val normalizedContentId = normalizeImportedText(contentId, LibraryTransferContract.MAX_URL_CHARS)
    if (normalizedContentId.isBlank()) return null
    val normalizedHash = contentHash.trim().lowercase(java.util.Locale.ROOT)
    if (normalizedHash.isNotBlank() && !ROTATION_EXCLUSION_DIGEST.matches(normalizedHash)) return null
    val normalizedLocatorDigest = locatorDigest.trim().lowercase(java.util.Locale.ROOT)
    if (normalizedLocatorDigest.isNotBlank() && !ROTATION_EXCLUSION_DIGEST.matches(normalizedLocatorDigest)) return null
    if (normalizedSource == "LOCATOR" && !ROTATION_EXCLUSION_DIGEST.matches(normalizedContentId)) return null
    val normalizedThumbnail = normalizeImportedHttpsUrl(thumbnailUrl, allowBlank = true) ?: return null
    return rotationIdentity(
        mediaType = normalizedType,
        source = normalizedSource,
        contentId = normalizedContentId,
        contentHash = normalizedHash,
        title = normalizeImportedText(title, 256),
        thumbnailUrl = normalizedThumbnail,
    ).toRotationExclusion(
        excludedAt = excludedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
    ).copy(locatorDigest = normalizedLocatorDigest)
}

private fun RotationExclusionEntity.sameIdentityAs(other: RotationExclusionEntity): Boolean =
    stableId == other.stableId ||
        (
            mediaType == other.mediaType &&
                contentHash.isNotBlank() &&
                contentHash == other.contentHash
        ) ||
        (
            mediaType == other.mediaType &&
                locatorDigest.isNotBlank() &&
                locatorDigest == other.locatorDigest
        )

/**
 * Validated import mapping — mirrors FavoritesExporter.toValidatedEntity: enum-checked
 * source, https-only URLs, bounded text. Unvalidated rows are dropped, not persisted;
 * the caller records why in the import plan.
 */
private fun FavoriteExportEntry.toEntity(): FavoriteEntity? {
    val normalizedId = normalizeImportedText(id)
    val normalizedSource = normalizeImportedContentSource(source) ?: return null
    val normalizedType = type.trim().uppercase(java.util.Locale.ROOT)
    if (normalizedId.isBlank()) return null
    if (localMedia && !isSafePortableLocalMediaId(normalizedId)) return null
    if (normalizedType !in setOf("WALLPAPER", "SOUND")) return null
    val normalizedThumbnailUrl = normalizeImportedHttpsUrl(
        thumbnailUrl,
        allowBlank = localMedia || normalizedType == "SOUND",
    ) ?: return null
    val normalizedFullUrl = normalizeImportedHttpsUrl(fullUrl, allowBlank = localMedia) ?: return null
    if (!localMedia && normalizedFullUrl.isBlank()) return null
    if (!localMedia && normalizedType == "WALLPAPER" && normalizedThumbnailUrl.isBlank()) return null
    val normalizedHash = localMediaSha256.trim().lowercase(java.util.Locale.ROOT)
    if (normalizedHash.isNotBlank() && !LOCAL_MEDIA_HASH.matches(normalizedHash)) return null
    if (!duration.isFinite() || duration < 0.0) return null
    return FavoriteEntity(
        id = normalizedId,
        source = normalizedSource,
        type = normalizedType,
        thumbnailUrl = normalizedThumbnailUrl.takeUnless { localMedia }.orEmpty(),
        fullUrl = normalizedFullUrl.takeUnless { localMedia }.orEmpty(),
        name = normalizeImportedText(name),
        width = width.coerceAtLeast(0),
        height = height.coerceAtLeast(0),
        duration = duration,
        addedAt = if (addedAt > 0) addedAt else System.currentTimeMillis(),
        tags = normalizeImportedText(tags).takeIf(String::isNotBlank),
        category = normalizeImportedText(category).takeIf(String::isNotBlank),
        uploaderName = normalizeImportedText(uploaderName).takeIf(String::isNotBlank),
        sourcePageUrl = normalizeImportedHttpsUrl(sourcePageUrl, allowBlank = true)
            ?.takeIf(String::isNotBlank),
        license = normalizeImportedText(license).takeIf(String::isNotBlank),
        fileSize = fileSize.coerceAtLeast(0).takeIf { it > 0 },
        fileType = normalizeImportedText(fileType, 128).takeIf(String::isNotBlank),
        localMediaStatus = if (localMedia) LocalMediaStatus.MISSING else LocalMediaStatus.AVAILABLE,
        localMediaReason = if (localMedia) "Choose the original file or a replacement" else null,
        localMediaSha256 = normalizedHash,
    )
}

private fun CollectionItemExportEntry.toWallpaperOrNull(): com.freevibe.data.model.Wallpaper? {
    val normalizedId = normalizeImportedText(wallpaperId)
    if (normalizedId.isBlank()) return null
    if (localMedia && !isSafePortableLocalMediaId(normalizedId)) return null
    val normalizedSource = normalizeImportedContentSource(source) ?: return null
    val normalizedThumbnailUrl = normalizeImportedHttpsUrl(thumbnailUrl, allowBlank = localMedia) ?: return null
    val normalizedFullUrl = normalizeImportedHttpsUrl(fullUrl, allowBlank = localMedia) ?: return null
    if (!localMedia && (normalizedThumbnailUrl.isBlank() || normalizedFullUrl.isBlank())) return null
    val contentSource = runCatching {
        com.freevibe.data.model.ContentSource.valueOf(normalizedSource)
    }.getOrNull() ?: return null
    return com.freevibe.data.model.Wallpaper(
        id = normalizedId,
        source = contentSource,
        thumbnailUrl = normalizedThumbnailUrl.takeUnless { localMedia }.orEmpty(),
        fullUrl = normalizedFullUrl.takeUnless { localMedia }.orEmpty(),
        width = width.coerceAtLeast(0),
        height = height.coerceAtLeast(0),
    )
}

private fun LocalWallpaperExportEntry.toEntity(): LocalWallpaperEntity? {
    val normalizedId = normalizeImportedText(id)
    val normalizedName = normalizeImportedText(displayName)
    val normalizedMimeType = normalizeImportedText(mimeType, 128).lowercase(java.util.Locale.ROOT)
    val normalizedHash = contentHash.trim().lowercase(java.util.Locale.ROOT)
    val normalizedTarget = target.trim().uppercase(java.util.Locale.ROOT)
    if (!isSafePortableLocalMediaId(normalizedId) || normalizedName.isBlank()) return null
    if (!normalizedMimeType.startsWith("image/")) return null
    if (normalizedHash.isNotBlank() && !LOCAL_MEDIA_HASH.matches(normalizedHash)) return null
    if (normalizedTarget !in WallpaperTarget.entries.map(WallpaperTarget::name)) return null
    val folderUri = restoredLocalFolderUri(normalizedTarget)
    return LocalWallpaperEntity(
        documentUri = restoredLocalItemUri(normalizedId),
        stableId = normalizedId,
        folderUri = folderUri,
        documentId = normalizedId,
        displayName = normalizedName,
        mimeType = normalizedMimeType,
        sizeBytes = sizeBytes.coerceAtLeast(0),
        modifiedAt = 0,
        contentHash = normalizedHash,
        width = width.coerceAtLeast(0),
        height = height.coerceAtLeast(0),
        localMediaStatus = LocalMediaStatus.MISSING,
        localMediaReason = "Restore this file or repair its folder access",
        tags = normalizeImportedText(tags),
        addedAt = addedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )
}

private fun WallpaperHistoryExportEntry.toEntity(): WallpaperHistoryEntity? {
    val normalizedId = normalizeImportedText(wallpaperId)
    val normalizedSource = normalizeImportedContentSource(source) ?: return null
    val normalizedTarget = target.trim().uppercase(java.util.Locale.ROOT)
    if (normalizedId.isBlank() || normalizedTarget !in WallpaperTarget.entries.map(WallpaperTarget::name)) return null
    if (localMedia && !isSafePortableLocalMediaId(normalizedId)) return null
    val normalizedThumbnail = normalizeImportedHttpsUrl(thumbnailUrl, allowBlank = localMedia) ?: return null
    val normalizedFull = normalizeImportedHttpsUrl(fullUrl, allowBlank = localMedia) ?: return null
    if (!localMedia && normalizedFull.isBlank()) return null
    return WallpaperHistoryEntity(
        wallpaperId = normalizedId,
        source = normalizedSource,
        thumbnailUrl = normalizedThumbnail.takeUnless { localMedia }.orEmpty(),
        fullUrl = normalizedFull.takeUnless { localMedia }.orEmpty(),
        width = width.coerceAtLeast(0),
        height = height.coerceAtLeast(0),
        target = normalizedTarget,
        appliedAt = appliedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )
}

private fun restoredLocalFolderUri(target: String): String =
    "content://com.freevibe.relink/restored-folder/${target.lowercase(java.util.Locale.ROOT)}"

private fun restoredLocalFolderTarget(folderUri: String): String =
    folderUri.substringAfterLast('/').uppercase(java.util.Locale.ROOT)
        .takeIf { it in WallpaperTarget.entries.map(WallpaperTarget::name) }
        ?: WallpaperTarget.BOTH.name

private fun restoredLocalItemUri(id: String): String =
    "content://com.freevibe.relink/restored-item/${Uri.encode(id)}"
