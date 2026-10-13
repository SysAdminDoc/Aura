package com.freevibe.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.freevibe.data.local.FreeVibeDatabase
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.RedditFeedMediaType
import com.freevibe.data.model.RedditFeedPreset
import com.freevibe.data.model.RedditFeedPresetStore
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperCollectionItemEntity
import com.freevibe.data.model.FitCanvasPreferences
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.LocalMediaStatus
import com.freevibe.data.model.LocalWallpaperEntity
import com.freevibe.data.model.LocalWallpaperFolderEntity
import com.freevibe.data.model.LocalWallpaperFolderScanStatus
import com.freevibe.data.model.RotationExclusionIndex
import com.freevibe.data.model.WallpaperHistoryEntity
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.activate
import com.freevibe.data.model.bindRotation
import com.freevibe.data.model.decodeRedditFeedPresetStore
import com.freevibe.data.model.defaultRedditFeedPresetStore
import com.freevibe.data.model.normalized
import com.freevibe.data.model.rotationIdentity
import com.freevibe.data.model.toRotationExclusion
import com.freevibe.data.model.upsert
import com.freevibe.data.repository.CollectionRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Restore is the one operation that can destroy a user's library, so it is
 * covered end to end: version gating, migration reporting, non-portable asset
 * reporting, and full rollback after an injected mid-write failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryImportPlanTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private lateinit var db: FreeVibeDatabase
    private lateinit var exporter: LibraryExporter
    private lateinit var prefs: PreferencesManager

    private val packJson = MutableStateFlow("")
    private val profilesJson = MutableStateFlow("")
    private val soundShufflePoolsJson = MutableStateFlow("")
    private val redditPresetsJson = MutableStateFlow("")
    private val contactLedgerJson = MutableStateFlow("")
    private val contactWriter = FakeContactRingtoneWriter()
    private val contactSounds = FakeContactRingtoneSounds()
    private val savedRingtone = "content://media/external/audio/media/41"
    private var fitCanvasPreferences = FitCanvasPreferences()
    private val presetAdapter = Moshi.Builder().build().adapter(RedditFeedPresetStore::class.java)
    private val presetDefaults = defaultRedditFeedPresetStore(name = { "name-$it" })

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FreeVibeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = mockk()
        every { prefs.wallpaperPackJson } returns packJson
        every { prefs.soundProfilesJson } returns profilesJson
        every { prefs.soundShufflePoolsJson } returns soundShufflePoolsJson
        coEvery { prefs.setWallpaperPackJson(any()) } answers { packJson.value = firstArg() }
        coEvery { prefs.setSoundProfilesJson(any()) } answers { profilesJson.value = firstArg() }
        coEvery { prefs.setSoundShufflePoolsJson(any()) } answers { soundShufflePoolsJson.value = firstArg() }
        coEvery { prefs.fitCanvasPreferencesSnapshot() } answers { fitCanvasPreferences }
        coEvery { prefs.restoreFitCanvasPreferences(any()) } answers { fitCanvasPreferences = firstArg() }
        every { prefs.redditFeedPresetsJson } returns redditPresetsJson
        coEvery { prefs.setRedditFeedPresetsJson(any()) } answers { redditPresetsJson.value = firstArg() }
        // Same read, transform and normalize the real DataStore edit does.
        coEvery { prefs.updateRedditFeedPresets(any()) } answers {
            val transform = firstArg<(RedditFeedPresetStore) -> RedditFeedPresetStore>()
            val current = decodeRedditFeedPresetStore(presetAdapter, redditPresetsJson.value, presetDefaults)
            transform(current).normalized(presetDefaults).also { redditPresetsJson.value = presetAdapter.toJson(it) }
        }
        every { prefs.contactRingtoneLedgerJson } returns contactLedgerJson
        coEvery { prefs.updateContactRingtoneLedgerJson(any()) } answers {
            contactLedgerJson.value = firstArg<(String) -> String>().invoke(contactLedgerJson.value)
        }
        every { prefs.autoWallpaperRequiresWiFiOnly } returns flowOf(false)
        exporter = LibraryExporter(
            context = context,
            database = db,
            favoriteDao = db.favoriteDao(),
            collectionRepo = CollectionRepository(db.collectionDao()),
            searchHistoryDao = db.searchHistoryDao(),
            rotationExclusionDao = db.rotationExclusionDao(),
            prefs = prefs,
            moshi = Moshi.Builder().build(),
            soundShufflePoolManager = mockk(relaxed = true),
            contactRingtoneBackup = ContactRingtoneBackupManager(
                prefs = prefs,
                moshi = Moshi.Builder().build(),
                writer = contactWriter,
                sounds = contactSounds,
                networkGate = RotationNetworkGate { RotationNetworkState(connected = true, metered = false) },
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun contactRingtoneJson(vararg entries: ContactRingtoneExportEntry): String =
        Moshi.Builder().build()
            .adapter<List<ContactRingtoneExportEntry>>(
                Types.newParameterizedType(List::class.java, ContactRingtoneExportEntry::class.java),
            )
            .toJson(entries.toList())

    /** Stands in for a picture the user chose with the document picker on this install. */
    private fun grantRead(locator: String) =
        context.contentResolver.takePersistableUriPermission(Uri.parse(locator), Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun packBackup(vararg slots: DaypartSlot, extra: String = ""): String {
        val pack = serializePack(WallpaperPack(id = "main", name = "Mine", slots = slots.toList()))
        return payload(extra = ""","wallpaperPackJson":${JSONObject.quote(pack)}""" + extra)
    }

    private fun backupFile(json: String): File =
        File(context.cacheDir, "library-contacts-${System.nanoTime()}.json").apply { writeText(json) }

    private fun payload(
        version: String = "2",
        favorites: String = "[]",
        collections: String = "[]",
        searchHistory: String = "[]",
        rotationExclusions: String = "[]",
        extra: String = "",
    ): String = """
        {
          "version": $version,
          "favorites": $favorites,
          "collections": $collections,
          "searchHistory": $searchHistory,
          "rotationExclusions": $rotationExclusions$extra
        }
    """.trimIndent()

    private val portableFavorite = """
        {"id":"wp-1","source":"WALLHAVEN","type":"WALLPAPER",
         "thumbnailUrl":"https://w.example/t.jpg","fullUrl":"https://w.example/f.jpg","name":"Blue"}
    """.trimIndent()

    private val localFavorite = """
        {"id":"ai-1","source":"AI_GENERATED","type":"WALLPAPER",
         "thumbnailUrl":"file:/data/user/0/com.freevibe/files/ai_wallpapers/ai-1.png",
         "fullUrl":"file:/data/user/0/com.freevibe/files/ai_wallpapers/ai-1.png","name":"Dream"}
    """.trimIndent()

    // -- version gating --

    @Test
    fun `a payload without a version is refused`() = runTest {
        val failure = runCatching { exporter.buildPlan("""{"favorites":[]}""") }.exceptionOrNull()

        assertTrue(failure is LibraryImportUnsupportedVersionException)
        assertTrue(failure!!.message!!.contains("version"))
    }

    @Test
    fun `a future payload version is refused instead of partially read`() = runTest {
        val failure = runCatching {
            exporter.buildPlan(payload(version = "99", favorites = "[$portableFavorite]"))
        }.exceptionOrNull()

        assertTrue(failure is LibraryImportUnsupportedVersionException)
        assertTrue(failure!!.message!!.contains("newer version"))
    }

    @Test
    fun `a corrupt payload is refused`() = runTest {
        assertTrue(runCatching { exporter.buildPlan("not json at all") }.isFailure)
    }

    @Test
    fun `a v1 payload is migrated and its downloads are reported not silently dropped`() = runTest {
        val plan = exporter.buildPlan(
            payload(
                version = "1",
                favorites = "[$portableFavorite]",
                extra = ""","downloads":[{"id":"d-1","name":"Sunset","localPath":"/sdcard/old.jpg"}]""",
            ),
        )

        assertEquals(1, plan.sourceVersion)
        assertEquals(1, plan.favorites.size)
        val migrationSkips = plan.skipped.filter {
            it.reason == LibraryImportSkipReason.DROPPED_BY_MIGRATION
        }
        assertEquals(listOf("Sunset"), migrationSkips.map { it.label })
    }

    @Test
    fun `a v2 payload is accepted`() = runTest {
        val plan = exporter.buildPlan(payload(favorites = "[$portableFavorite]"))

        assertEquals(2, plan.sourceVersion)
        assertEquals(listOf("wp-1"), plan.favorites.map { it.id })
    }

    @Test
    fun `v4 sound pools restore schedules and locator free assignments`() = runTest {
        val soundStoreJson = Moshi.Builder().build().adapter(SoundShuffleStore::class.java).toJson(
            SoundShuffleStore(
                legacyMigrated = true,
                pools = listOf(
                    SoundShufflePool(
                        poolId = "pool-1",
                        name = "Workday",
                        enabled = true,
                        targets = listOf(SoundShuffleTarget.NOTIFICATION),
                        intervalHours = 6,
                        items = listOf(
                            SoundShufflePoolItem(
                                itemId = "local:saved",
                                sourceKind = SoundShuffleSourceKind.LOCAL,
                                sourceId = "saved",
                                name = "Ping",
                                localMediaStatus = LocalMediaStatus.MISSING,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val json = Moshi.Builder().build().adapter(LibraryExportFile::class.java).toJson(
            LibraryExportFile(version = 4, soundShufflePoolsJson = soundStoreJson),
        )

        val plan = exporter.buildPlan(json)
        exporter.applyPlan(plan)

        assertEquals(4, plan.sourceVersion)
        val restored = parseSoundShuffleStoreStrict(Moshi.Builder().build(), soundShufflePoolsJson.value)
        assertEquals("Workday", restored.pools.single().name)
        assertEquals(SoundShuffleTarget.NOTIFICATION, restored.pools.single().targets.single())
        assertEquals(LocalMediaStatus.MISSING, restored.pools.single().items.single().localMediaStatus)
    }

    @Test
    fun `v3 restores local metadata and associations as relinkable records`() = runTest {
        val hash = "ab".repeat(32)
        val plan = exporter.buildPlan(
            payload(
                version = "3",
                favorites = """
                    [{"id":"local-photo","source":"LOCAL","type":"WALLPAPER",
                      "name":"Family photo","width":1080,"height":1920,"tags":"family, portrait",
                      "fileSize":1234,"fileType":"image/png","localMedia":true,
                      "localMediaSha256":"$hash"}]
                """.trimIndent(),
                collections = """
                    [{"id":1,"name":"Family","items":[
                      {"wallpaperId":"local-photo","source":"LOCAL","width":1080,
                       "height":1920,"localMedia":true}
                    ]}]
                """.trimIndent(),
                rotationExclusions = """
                    [{"mediaType":"WALLPAPER","source":"LOCAL","contentId":"local-photo",
                      "contentHash":"$hash","title":"Family photo","excludedAt":7}]
                """.trimIndent(),
                extra = """,
                  "localWallpapers":[
                    {"id":"local-photo","displayName":"family.png","mimeType":"image/png",
                     "sizeBytes":1234,"contentHash":"$hash","width":1080,"height":1920,
                     "tags":"family, portrait","target":"LOCK","addedAt":9}
                  ],
                  "wallpaperHistory":[
                    {"wallpaperId":"local-photo","source":"LOCAL","width":1080,"height":1920,
                     "target":"LOCK","appliedAt":11,"localMedia":true}
                  ]""",
            ),
        )

        assertEquals(3, plan.sourceVersion)
        assertEquals(1, plan.localWallpapers.size)
        assertEquals(1, plan.localWallpaperFolders.size)
        assertEquals(1, plan.wallpaperHistory.size)
        assertTrue(plan.skipped.isEmpty())

        exporter.applyPlan(plan)

        val favorite = db.favoriteDao().getByIdentity("local-photo", "LOCAL", "WALLPAPER")!!
        assertEquals(LocalMediaStatus.MISSING, favorite.localMediaStatus)
        assertEquals("", favorite.fullUrl)
        assertEquals("family, portrait", favorite.tags)
        val localItem = db.localWallpaperDao().getByStableId("local-photo")!!
        assertEquals(LocalMediaStatus.MISSING, localItem.localMediaStatus)
        assertEquals(WallpaperTarget.LOCK.name, db.localWallpaperFolderDao().get(localItem.folderUri)!!.target)
        assertEquals("", db.collectionDao().getCollectionItems(1).first().single().fullUrl)
        assertEquals(WallpaperTarget.LOCK.name, db.wallpaperHistoryDao().getRecentSnapshot(1).single().target)
        assertTrue(
            RotationExclusionIndex(db.rotationExclusionDao().getAll())
                .contains(localItem.rotationIdentity()),
        )
    }

    @Test
    fun `export replaces private local locators with a portable relink identity`() = runTest {
        val privateLocator = "content://private.provider/tree/family/photo.png"
        val hash = "cd".repeat(32)
        db.localWallpaperFolderDao().upsert(
            LocalWallpaperFolderEntity(
                folderUri = "content://private.provider/tree/family",
                displayName = "Family",
                target = WallpaperTarget.HOME.name,
                scanStatus = LocalWallpaperFolderScanStatus.READY,
            ),
        )
        val localItem = LocalWallpaperEntity(
            documentUri = privateLocator,
            stableId = privateLocator,
            folderUri = "content://private.provider/tree/family",
            documentId = "photo.png",
            displayName = "photo.png",
            mimeType = "image/png",
            sizeBytes = 1234,
            modifiedAt = 5,
            contentHash = hash,
            width = 1080,
            height = 1920,
        )
        db.localWallpaperDao().upsertAll(listOf(localItem))
        db.favoriteDao().insert(
            FavoriteEntity(
                id = privateLocator,
                source = ContentSource.LOCAL.name,
                type = "WALLPAPER",
                thumbnailUrl = privateLocator,
                fullUrl = privateLocator,
                offlinePath = privateLocator,
                name = "Family photo",
                localMediaSha256 = hash,
            ),
        )
        db.wallpaperHistoryDao().insert(
            WallpaperHistoryEntity(
                wallpaperId = privateLocator,
                source = ContentSource.LOCAL.name,
                thumbnailUrl = privateLocator,
                fullUrl = privateLocator,
                target = WallpaperTarget.HOME.name,
            ),
        )
        db.rotationExclusionDao().upsert(localItem.rotationIdentity().toRotationExclusion())
        val destination = File(context.cacheDir, "library-local-${System.nanoTime()}.json")

        try {
            val result = exporter.exportLibrary(Uri.fromFile(destination))

            assertTrue(result.isSuccess)
            val json = destination.readText()
            val parsed = Moshi.Builder().build().adapter(LibraryExportFile::class.java).fromJson(json)!!
            assertEquals(4, parsed.version)
            assertTrue(parsed.favorites.single().localMedia)
            assertTrue(parsed.localWallpapers.single().id.startsWith("local-"))
            assertFalse(parsed.localWallpapers.single().id.endsWith(hash))
            assertTrue(!json.contains(privateLocator))
            assertTrue(!json.contains("private.provider"))
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `identical local files retain distinct portable library identities`() = runTest {
        val firstLocator = "content://private.provider/tree/one/photo.png"
        val secondLocator = "content://private.provider/tree/two/photo.png"
        val folderUri = "content://private.provider/tree"
        val hash = "ef".repeat(32)
        db.localWallpaperFolderDao().upsert(
            LocalWallpaperFolderEntity(
                folderUri = folderUri,
                displayName = "Private",
                scanStatus = LocalWallpaperFolderScanStatus.READY,
            ),
        )
        db.localWallpaperDao().upsertAll(
            listOf(
                LocalWallpaperEntity(
                    documentUri = firstLocator,
                    stableId = firstLocator,
                    folderUri = folderUri,
                    documentId = "one",
                    displayName = "one.png",
                    mimeType = "image/png",
                    sizeBytes = 100,
                    modifiedAt = 1,
                    contentHash = hash,
                ),
                LocalWallpaperEntity(
                    documentUri = secondLocator,
                    stableId = secondLocator,
                    folderUri = folderUri,
                    documentId = "two",
                    displayName = "two.png",
                    mimeType = "image/png",
                    sizeBytes = 100,
                    modifiedAt = 2,
                    contentHash = hash,
                ),
            ),
        )
        val destination = File(context.cacheDir, "library-duplicate-bytes-${System.nanoTime()}.json")

        try {
            assertTrue(exporter.exportLibrary(Uri.fromFile(destination)).isSuccess)
            val parsed = Moshi.Builder().build()
                .adapter(LibraryExportFile::class.java)
                .fromJson(destination.readText())!!

            assertEquals(2, parsed.localWallpapers.size)
            assertEquals(2, parsed.localWallpapers.map { it.id }.toSet().size)
            assertFalse(destination.readText().contains(firstLocator))
            assertFalse(destination.readText().contains(secondLocator))
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `an over-limit favorites section refuses the whole backup`() = runTest {
        val entries = List(LibraryTransferContract.MAX_FAVORITES + 1) { index ->
            FavoriteExportEntry(
                id = "favorite-$index",
                source = "REDDIT",
                type = "WALLPAPER",
                thumbnailUrl = "https://i.example/$index.jpg",
                fullUrl = "https://preview.example/$index.jpg",
            )
        }
        val json = Moshi.Builder().build()
            .adapter(LibraryExportFile::class.java)
            .toJson(LibraryExportFile(favorites = entries))

        val failure = runCatching { exporter.buildPlan(json) }.exceptionOrNull()

        assertTrue(failure is LibraryTransferLimitExceededException)
        assertEquals(0, db.favoriteDao().count().first())
    }

    @Test
    fun `local hash exclusion survives a backup restore and path relink`() = runTest {
        val hash = "ab".repeat(32)
        val plan = exporter.buildPlan(
            payload(
                rotationExclusions = """
                    [{"mediaType":"WALLPAPER","source":"LOCAL","contentId":"old-document-id",
                      "contentHash":"$hash","title":"Family photo","excludedAt":7}]
                """.trimIndent(),
            ),
        )

        exporter.applyPlan(plan)

        val restored = db.rotationExclusionDao().getAll().single()
        val relinked = com.freevibe.data.model.rotationIdentity(
            mediaType = com.freevibe.data.model.ROTATION_MEDIA_WALLPAPER,
            source = "LOCAL",
            contentId = "new-document-id",
            contentHash = hash,
            locator = "content://new-tree/photo.jpg",
        )
        assertTrue(restored.matches(relinked))
        assertEquals(7, restored.excludedAt)
    }

    @Test
    fun `invalid rotation exclusion is reported and never written`() = runTest {
        val plan = exporter.buildPlan(
            payload(
                rotationExclusions = """
                    [{"mediaType":"AUDIO","source":"REDDIT","contentId":"post-1",
                      "locatorDigest":"not-a-digest","title":"Bad row"}]
                """.trimIndent(),
            ),
        )

        assertTrue(plan.rotationExclusions.isEmpty())
        assertEquals(listOf(LibraryImportSkipReason.INVALID), plan.skipped.map { it.reason })
    }

    // -- non-portable reporting --

    @Test
    fun `device-local favorites are reported as non-portable rather than dropped`() = runTest {
        val plan = exporter.buildPlan(payload(favorites = "[$portableFavorite,$localFavorite]"))

        assertEquals(listOf("wp-1"), plan.favorites.map { it.id })
        assertEquals(
            listOf("Dream" to LibraryImportSkipReason.NON_PORTABLE),
            plan.nonPortable.map { it.label to it.reason },
        )
    }

    @Test
    fun `device-local collection items are reported as non-portable`() = runTest {
        val plan = exporter.buildPlan(
            payload(
                collections = """
                    [{"id":1,"name":"Night","items":[
                      {"wallpaperId":"ai-2","source":"AI_GENERATED",
                       "thumbnailUrl":"content://media/external/images/2",
                       "fullUrl":"content://media/external/images/2"}
                    ]}]
                """.trimIndent(),
            ),
        )

        assertEquals(1, plan.collections.size)
        assertTrue(plan.collections.single().items.isEmpty())
        assertEquals(
            listOf(LibraryImportSkipReason.NON_PORTABLE),
            plan.nonPortable.map { it.reason },
        )
    }

    @Test
    fun `a locator is only portable when it is https`() {
        assertTrue(isNonPortableLocator("file:/data/x.png"))
        assertTrue(isNonPortableLocator("content://media/external/images/1"))
        assertTrue(isNonPortableLocator("/storage/emulated/0/x.png"))
        assertEquals(false, isNonPortableLocator("https://example.com/x.png"))
        assertEquals(false, isNonPortableLocator(""))
    }

    // -- conflict planning --

    @Test
    fun `an item already in the target collection is reported as a duplicate`() = runTest {
        val collectionId = db.collectionDao().createCollection(WallpaperCollectionEntity(name = "Night"))
        db.collectionDao().addItem(
            WallpaperCollectionItemEntity(
                collectionId = collectionId,
                wallpaperId = "wp-9",
                thumbnailUrl = "https://w.example/t.jpg",
                fullUrl = "https://w.example/f.jpg",
                source = "WALLHAVEN",
            ),
        )

        val plan = exporter.buildPlan(
            payload(
                collections = """
                    [{"id":1,"name":"Night","items":[
                      {"wallpaperId":"wp-9","source":"WALLHAVEN",
                       "thumbnailUrl":"https://w.example/t.jpg","fullUrl":"https://w.example/f.jpg"}
                    ]}]
                """.trimIndent(),
            ),
        )

        assertEquals(collectionId, plan.collections.single().existingId)
        assertTrue(plan.collections.single().items.isEmpty())
        assertEquals(
            listOf(
                LibraryImportSkipReason.DUPLICATE,
                LibraryImportSkipReason.DUPLICATE,
            ),
            plan.skipped.map { it.reason },
        )
        assertEquals(0, plan.writeCount)
    }

    @Test
    fun `collection identity includes source when IDs match`() = runTest {
        val collectionId = db.collectionDao().createCollection(WallpaperCollectionEntity(name = "Night"))
        db.collectionDao().addItem(
            WallpaperCollectionItemEntity(
                collectionId = collectionId,
                wallpaperId = "shared-id",
                thumbnailUrl = "https://w.example/t.jpg",
                fullUrl = "https://w.example/f.jpg",
                source = "WALLHAVEN",
            ),
        )

        val plan = exporter.buildPlan(
            payload(
                collections = """
                    [{"id":1,"name":"Night","items":[
                      {"wallpaperId":"shared-id","source":"REDDIT",
                       "thumbnailUrl":"https://r.example/t.jpg","fullUrl":"https://r.example/f.jpg"}
                    ]}]
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("REDDIT"), plan.collections.single().items.map { it.source.name })
        assertEquals(1, plan.writeCount)
    }

    @Test
    fun `duplicate favorite identities are reported and imported once`() = runTest {
        val duplicate = portableFavorite.replace("\"name\":\"Blue\"", "\"name\":\"Duplicate\"")

        val plan = exporter.buildPlan(payload(favorites = "[$portableFavorite,$duplicate]"))

        assertEquals(listOf("wp-1"), plan.favorites.map { it.id })
        assertEquals(
            listOf(LibraryImportSkipReason.DUPLICATE),
            plan.skipped.map { it.reason },
        )
    }

    @Test
    fun `result counts separate duplicates from failed rows`() = runTest {
        val duplicate = portableFavorite.replace("\"name\":\"Blue\"", "\"name\":\"Duplicate\"")

        val plan = exporter.buildPlan(
            payload(favorites = "[$portableFavorite,$duplicate,$localFavorite]"),
        )
        val outcome = LibraryImportOutcome(
            sourceVersion = plan.sourceVersion,
            written = plan.writeCount,
            skipped = plan.skipped,
        )

        assertEquals(1, outcome.written)
        assertEquals(1, outcome.skippedCount)
        assertEquals(1, outcome.failed)
    }

    @Test
    fun `planning writes nothing`() = runTest {
        exporter.buildPlan(
            payload(
                favorites = "[$portableFavorite]",
                collections = """[{"id":1,"name":"Night","items":[]}]""",
            ),
        )

        assertEquals(0, db.favoriteDao().count().first())
        assertEquals(0, db.collectionDao().getAllCollections().first().size)
    }

    // -- atomicity --

    @Test
    fun `an injected mid-write failure leaves the pre-import state intact`() = runTest {
        packJson.value = "original-pack"
        profilesJson.value = "original-profiles"
        soundShufflePoolsJson.value = "original-pools"
        val originalFitCanvasPreferences = FitCanvasPreferences(
            staticPresentation = "fit",
            staticStyle = FitCanvasStyle(FitCanvasMode.CUSTOM_COLOR, 0xFF123456.toInt()),
        )
        fitCanvasPreferences = originalFitCanvasPreferences
        exporter.failBeforeCommit = { throw IllegalStateException("disk full") }

        val plan = exporter.buildPlan(
            payload(
                favorites = "[$portableFavorite]",
                collections = """[{"id":1,"name":"Night","items":[]}]""",
                searchHistory = """[{"query":"blue","type":"WALLPAPER","searchedAt":1}]""",
                rotationExclusions = """
                    [{"mediaType":"WALLPAPER","source":"REDDIT","contentId":"blocked-post"}]
                """.trimIndent(),
                extra = ""","wallpaperPackJson":"new-pack","soundProfilesJson":"new-profiles",
                    "soundShufflePoolsJson":"{\"version\":1,\"legacyMigrated\":true,\"pools\":[],\"history\":[]}",
                    "fitCanvasPreferences":{
                      "staticPresentation":"fill","staticCanvasMode":"amoled_black",
                      "staticCanvasColor":-16777216,"videoPresentation":"fit",
                      "videoCanvasMode":"blurred_edge","videoCanvasColor":-15584170
                    }""".trimIndent(),
            ),
        )

        val failure = runCatching { exporter.applyPlan(plan) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)

        assertEquals(0, db.favoriteDao().count().first())
        assertEquals(0, db.collectionDao().getAllCollections().first().size)
        assertEquals(0, db.searchHistoryDao().count("WALLPAPER"))
        assertTrue(db.rotationExclusionDao().getAll().isEmpty())
        assertEquals("original-pack", packJson.value)
        assertEquals("original-profiles", profilesJson.value)
        assertEquals("original-pools", soundShufflePoolsJson.value)
        assertEquals(originalFitCanvasPreferences, fitCanvasPreferences)
    }

    @Test
    fun `a restored 24H pack gets its worker back when the switch is on`() = runTest {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        every { prefs.wallpaperPackEnabled } returns flowOf(true)
        coEvery { prefs.setWallpaperPackLastAppliedDaypart(any()) } returns Unit
        grantRead("content://a")
        val pack = serializePack(
            WallpaperPack(id = "main", name = "Mine", slots = listOf(DaypartSlot(Daypart.NIGHT, "content://a"))),
        )
        val plan = exporter.buildPlan(payload(extra = ""","wallpaperPackJson":${JSONObject.quote(pack)}"""))

        exporter.applyPlan(plan)

        assertEquals(pack, packJson.value)
        assertTrue(
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(WallpaperPackWorker.WORK_NAME).get().isNotEmpty(),
        )
    }

    @Test
    fun `a pack picture this install can't open is left out and reported for a re-pick`() = runTest {
        grantRead("content://picked/night")

        val plan = exporter.buildPlan(
            packBackup(
                DaypartSlot(Daypart.MORNING, "content://old-phone/morning", label = "beach.jpg"),
                DaypartSlot(Daypart.NIGHT, "content://picked/night", label = "stars.jpg"),
            ),
        )

        assertEquals(listOf(Daypart.NIGHT), parsePack(plan.wallpaperPackJson)!!.slots.map { it.daypart })
        assertEquals(
            listOf(Triple("wallpaperPackSlot", "beach.jpg", LibraryImportSkipReason.NEEDS_REPICK)),
            plan.skipped.map { Triple(it.section, it.label, it.reason) },
        )
        assertEquals("the summary counts it as not restored", 1, plan.failedCount)
    }

    @Test
    fun `a pack with no picture this install can open is not restored over the one already here`() = runTest {
        packJson.value = "the pack on this phone"

        val plan = exporter.buildPlan(packBackup(DaypartSlot(Daypart.DAY, "content://old-phone/day")))
        exporter.applyPlan(plan)

        assertEquals("", plan.wallpaperPackJson)
        assertEquals(0, plan.writeCount)
        assertEquals("the pack on this phone", packJson.value)
        assertEquals(
            listOf("Day" to LibraryImportSkipReason.NEEDS_REPICK),
            plan.skipped.map { it.label to it.reason },
        )
    }

    @Test
    fun `a restore forgets what each worker last applied and starts a run right away`() = runTest {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        val workManager = WorkManager.getInstance(context)
        val lastDaypart = MutableStateFlow("NIGHT")
        val lastProfile = MutableStateFlow("night")
        every { prefs.wallpaperPackEnabled } returns flowOf(true)
        every { prefs.soundProfilesEnabled } returns flowOf(true)
        coEvery { prefs.setWallpaperPackLastAppliedDaypart(any()) } answers { lastDaypart.value = firstArg() }
        coEvery { prefs.setSoundProfileLastAppliedId(any()) } answers { lastProfile.value = firstArg() }
        grantRead("content://a")
        // The schedule on a phone where the pack was already on, part way through its 15 minutes.
        val waiting = PeriodicWorkRequestBuilder<WallpaperPackWorker>(15L, TimeUnit.MINUTES)
            .setInitialDelay(1L, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WallpaperPackWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            waiting,
        ).result.get()
        val profiles = serializeProfiles(
            listOf(SoundProfile(id = "night", name = "Night", ringtoneUri = "content://media/internal/audio/media/11")),
        )

        val plan = exporter.buildPlan(
            packBackup(
                DaypartSlot(Daypart.NIGHT, "content://a"),
                extra = ""","soundProfilesJson":${JSONObject.quote(profiles)}""",
            ),
        )
        exporter.applyPlan(plan)

        assertEquals("", lastDaypart.value)
        assertEquals("", lastProfile.value)
        val packWork = workManager.getWorkInfosForUniqueWork(WallpaperPackWorker.WORK_NAME).get()
        assertTrue(
            "the old schedule would have sat out the rest of its 15 minutes",
            packWork.none { it.id == waiting.id && !it.state.isFinished },
        )
        assertTrue("a new schedule starts at once", packWork.any { it.id != waiting.id })
        assertTrue(workManager.getWorkInfosForUniqueWork("sound_profile_now").get().isNotEmpty())
    }

    @Test
    fun `reddit feed presets come back the same on a fresh device`() = runTest {
        val custom = RedditFeedPreset(
            id = "p_custom",
            name = "Night walls",
            mediaType = RedditFeedMediaType.WALLPAPER,
            subreddits = listOf("Amoledbackgrounds", "MinimalWallpaper"),
            sort = "top",
            timeWindow = "month",
            minHeight = 2000,
        )
        val motion = presetDefaults.presets.first { it.id == "default_motion" }
        val edited = presetDefaults
            .upsert(custom)
            .upsert(motion.copy(subreddits = listOf("perfectloops"), minDurationSeconds = 8))
            .activate("p_custom")
            .bindRotation("default_amoled")
            .normalized(presetDefaults)
        redditPresetsJson.value = presetAdapter.toJson(edited)
        val destination = File(context.cacheDir, "library-presets-${System.nanoTime()}.json")

        try {
            assertTrue(exporter.exportLibrary(Uri.fromFile(destination)).isSuccess)
            redditPresetsJson.value = ""

            val plan = exporter.buildPlan(destination.readText())
            exporter.applyPlan(plan)

            assertTrue(plan.redditFeedPresetsJson.isNotBlank())
            assertEquals(edited, presetAdapter.fromJson(redditPresetsJson.value))
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `a failed restore puts the reddit feed presets back`() = runTest {
        redditPresetsJson.value = "original-presets"
        exporter.failBeforeCommit = { throw IllegalStateException("disk full") }
        val imported = presetAdapter.toJson(
            RedditFeedPresetStore(
                presets = listOf(
                    RedditFeedPreset("p_x", "Mine", RedditFeedMediaType.WALLPAPER, listOf("iWallpaper")),
                ),
            ),
        )

        val failure = runCatching {
            exporter.applyPlan(LibraryImportPlan(sourceVersion = 4, redditFeedPresetsJson = imported))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("original-presets", redditPresetsJson.value)
    }

    @Test
    fun `a backup with presets this version can't read is refused before anything is written`() = runTest {
        val json = Moshi.Builder().build().adapter(LibraryExportFile::class.java).toJson(
            LibraryExportFile(version = 4, redditFeedPresetsJson = presetAdapter.toJson(RedditFeedPresetStore(version = 9))),
        )

        assertTrue(runCatching { exporter.buildPlan(json) }.isFailure)
        assertEquals("", redditPresetsJson.value)
    }

    @Test
    fun `a partial Fit Canvas preference failure is rolled back`() = runTest {
        val original = FitCanvasPreferences(
            staticPresentation = "fit",
            staticStyle = FitCanvasStyle(FitCanvasMode.CUSTOM_COLOR, 0xFF123456.toInt()),
        )
        val replacement = FitCanvasPreferences(
            videoPresentation = "fit",
            videoStyle = FitCanvasStyle(FitCanvasMode.BLURRED_EDGE),
        )
        fitCanvasPreferences = original
        var restoreCalls = 0
        coEvery { prefs.restoreFitCanvasPreferences(any()) } answers {
            fitCanvasPreferences = firstArg()
            if (restoreCalls++ == 0) throw IllegalStateException("preference write failed")
        }

        val failure = runCatching {
            exporter.applyPlan(
                LibraryImportPlan(sourceVersion = 2, fitCanvasPreferences = replacement),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(2, restoreCalls)
        assertEquals(original, fitCanvasPreferences)
    }

    @Test
    fun `a successful import writes exactly what the plan promised`() = runTest {
        val plan = exporter.buildPlan(
            payload(
                favorites = "[$portableFavorite,$localFavorite]",
                collections = """
                    [{"id":1,"name":"Night","items":[
                      {"wallpaperId":"wp-2","source":"WALLHAVEN",
                       "thumbnailUrl":"https://w.example/t2.jpg","fullUrl":"https://w.example/f2.jpg"}
                    ]}]
                """.trimIndent(),
                searchHistory = """[{"query":"blue","type":"WALLPAPER","searchedAt":1}]""",
                rotationExclusions = """
                    [{"mediaType":"WALLPAPER","source":"REDDIT","contentId":"blocked-post"}]
                """.trimIndent(),
                extra = ""","wallpaperPackJson":"new-pack"""",
            ),
        )

        exporter.applyPlan(plan)

        assertEquals(1, db.favoriteDao().count().first())
        val collections = db.collectionDao().getAllCollections().first()
        assertEquals(listOf("Night"), collections.map { it.name })
        assertEquals(
            listOf("wp-2"),
            db.collectionDao().getCollectionItems(collections.single().collectionId).first()
                .map { it.wallpaperId },
        )
        assertEquals(1, db.searchHistoryDao().count("WALLPAPER"))
        assertEquals(1, db.rotationExclusionDao().getAll().size)
        assertEquals("new-pack", packJson.value)
        // favorites(1) + collection(1) + item(1) + search(1) + exclusion(1) + pack(1)
        assertEquals(6, plan.writeCount)
    }

    @Test
    fun `re-importing the same backup does not duplicate collections`() = runTest {
        val json = payload(
            collections = """
                [{"id":1,"name":"Night","items":[
                  {"wallpaperId":"wp-2","source":"WALLHAVEN",
                   "thumbnailUrl":"https://w.example/t2.jpg","fullUrl":"https://w.example/f2.jpg"}
                ]}]
            """.trimIndent(),
        )

        exporter.applyPlan(exporter.buildPlan(json))
        exporter.applyPlan(exporter.buildPlan(json))

        val collections = db.collectionDao().getAllCollections().first()
        assertEquals(1, collections.size)
        assertEquals(
            1,
            db.collectionDao().getCollectionItems(collections.single().collectionId).first().size,
        )
    }

    // -- contact ringtones --

    @Test
    fun `an older backup without contact ringtones restores as before`() = runTest {
        val plan = exporter.buildPlan(payload(version = "4", favorites = "[$portableFavorite]"))

        assertTrue(plan.contactRingtones.isEmpty())
        assertTrue(plan.skipped.isEmpty())
        assertEquals(1, plan.writeCount)
    }

    @Test
    fun `contact ringtones are exported by lookup key and name and never by a place on the phone`() = runTest {
        // The second row is a ledger someone tampered with. The export cleans it on the way out.
        contactLedgerJson.value = encodeContactRingtoneLedger(
            Moshi.Builder().build(),
            ContactRingtoneLedger(
                assignments = listOf(
                    contactRingtone(),
                    contactRingtone(lookupKey = "key-bob", name = "Bob", soundTitle = "Memo", soundFileName = "Memo.m4a").copy(
                        soundSource = "LOCAL",
                        soundId = savedRingtone,
                        soundUrl = savedRingtone,
                        soundPageUrl = "",
                    ),
                ),
            ),
        )
        val destination = File(context.cacheDir, "library-contact-ringtones-${System.nanoTime()}.json")

        try {
            val outcome = exporter.exportLibrary(Uri.fromFile(destination)).getOrThrow()

            val json = destination.readText()
            val parsed = Moshi.Builder().build().adapter(LibraryExportFile::class.java).fromJson(json)!!
            assertEquals(4, parsed.version)
            assertEquals(listOf("key-ada", "key-bob"), parsed.contactRingtones.map { it.contactLookupKey })
            assertEquals(listOf("Ada Lovelace", "Bob"), parsed.contactRingtones.map { it.contactDisplayName })
            assertEquals(listOf("fs_100", ""), parsed.contactRingtones.map { it.soundId })
            assertEquals(listOf("Bell.mp3", "Memo.m4a"), parsed.contactRingtones.map { it.soundFileName })
            assertEquals(2, outcome.contactRingtones)
            assertFalse(json.contains("content://"))
            val section = json.substringAfter("\"contactRingtones\"")
            assertFalse(section.contains("\"id\""))
            assertFalse(section.contains("\"_id\""))
            assertFalse(section.contains("contactId", ignoreCase = true))
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `a backup restores contact ringtones and names every one it could not`() = runTest {
        val ada = contactWriter.add("key-ada", "Ada Lovelace")
        val grace = contactWriter.add("key-from-the-new-phone", "Grace Hopper")
        val firstSam = contactWriter.add("key-sam-one", "Sam Lee")
        val secondSam = contactWriter.add("key-sam-two", "Sam Lee")
        val bob = contactWriter.add("key-bob", "Bob")
        contactSounds.onPhone["fs_100"] = savedRingtone
        val file = backupFile(
            payload(
                version = "4",
                favorites = "[$portableFavorite]",
                extra = ""","contactRingtones":""" + contactRingtoneJson(
                    contactRingtone(),
                    contactRingtone(lookupKey = "key-grace", name = "Grace Hopper"),
                    contactRingtone(lookupKey = "key-sam", name = "Sam Lee"),
                    contactRingtone(lookupKey = "key-gone", name = "Nobody Here"),
                    contactRingtone(lookupKey = "key-bob", name = "Bob", soundId = "fs_gone"),
                ),
            ),
        )

        try {
            val outcome = exporter.importLibrary(Uri.fromFile(file)).getOrThrow()

            assertEquals(
                mapOf(
                    "Ada Lovelace" to ContactRingtoneRestoreStatus.RESTORED,
                    "Grace Hopper" to ContactRingtoneRestoreStatus.RESTORED,
                    "Sam Lee" to ContactRingtoneRestoreStatus.AMBIGUOUS_CONTACT,
                    "Nobody Here" to ContactRingtoneRestoreStatus.MISSING_CONTACT,
                    "Bob" to ContactRingtoneRestoreStatus.MISSING_SOUND,
                ),
                outcome.contactRingtones.associate { it.contactLabel to it.status },
            )
            assertEquals(savedRingtone, ada.ringtone)
            assertEquals("the changed lookup key fell back to the name", savedRingtone, grace.ringtone)
            assertNull(firstSam.ringtone)
            assertNull(secondSam.ringtone)
            assertNull(bob.ringtone)
            assertEquals("the rest of the backup is restored", 1, db.favoriteDao().count().first())
            // favorite(1) + the two contact ringtones that were set
            assertEquals(3, outcome.written)
            assertEquals(3, outcome.failed)
            assertEquals(
                "the next backup from this phone carries what was restored",
                listOf("Ada Lovelace", "Grace Hopper"),
                parseContactRingtoneLedger(Moshi.Builder().build(), contactLedgerJson.value)
                    .assignments.map { it.contactDisplayName },
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `an unreadable contact ringtone row is reported and a repeated contact is tried once`() = runTest {
        val plan = exporter.buildPlan(
            payload(
                version = "4",
                extra = ""","contactRingtones":""" + contactRingtoneJson(
                    contactRingtone(soundId = "fs_old", assignedAt = 1),
                    contactRingtone(soundId = "fs_new", assignedAt = 2),
                    ContactRingtoneExportEntry(soundTitle = "A sound with no contact"),
                ),
            ),
        )

        assertEquals(listOf("fs_new"), plan.contactRingtones.map { it.soundId })
        assertEquals(
            setOf(
                Triple("contactRingtone", "Ada Lovelace", LibraryImportSkipReason.DUPLICATE),
                Triple("contactRingtone", "(unnamed)", LibraryImportSkipReason.INVALID),
            ),
            plan.skipped.map { Triple(it.section, it.label, it.reason) }.toSet(),
        )
        assertEquals("contact ringtones are counted once they are set, not when planned", 0, plan.writeCount)
    }

    @Test
    fun `an over-limit contact ringtone section refuses the whole backup`() = runTest {
        val entries = Array(LibraryTransferContract.MAX_CONTACT_RINGTONES + 1) { index ->
            contactRingtone(lookupKey = "key-$index", name = "Contact $index")
        }

        val failure = runCatching {
            exporter.buildPlan(
                payload(version = "4", extra = ""","contactRingtones":""" + contactRingtoneJson(*entries)),
            )
        }.exceptionOrNull()

        assertTrue(failure is LibraryTransferLimitExceededException)
    }

    @Test
    fun `without contacts access the rest restores and the contacts left out are listed`() = runTest {
        contactWriter.allowed = false
        val ada = contactWriter.add("key-ada", "Ada Lovelace")
        contactSounds.onPhone["fs_100"] = savedRingtone
        val file = backupFile(
            payload(
                version = "4",
                favorites = "[$portableFavorite]",
                extra = ""","contactRingtones":""" + contactRingtoneJson(contactRingtone()),
            ),
        )

        try {
            assertEquals(1, exporter.contactRingtonesAwaitingPermission(Uri.fromFile(file)))

            val outcome = exporter.importLibrary(Uri.fromFile(file)).getOrThrow()

            val result = outcome.contactRingtones.single()
            assertEquals(ContactRingtoneRestoreStatus.PERMISSION_MISSING, result.status)
            assertEquals("Ada Lovelace", result.contactLabel)
            assertNull(ada.ringtone)
            assertEquals(1, db.favoriteDao().count().first())
            assertEquals(1, outcome.written)
            assertEquals(1, outcome.failed)

            contactWriter.allowed = true
            assertEquals(0, exporter.contactRingtonesAwaitingPermission(Uri.fromFile(file)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `nothing waits on contacts access when there is nothing to restore`() = runTest {
        contactWriter.allowed = false
        val withoutSection = backupFile(payload(version = "4", favorites = "[$portableFavorite]"))
        val broken = backupFile("not json at all")
        val fromTheFuture = backupFile(
            payload(version = "99", extra = ""","contactRingtones":""" + contactRingtoneJson(contactRingtone())),
        )

        try {
            assertEquals(0, exporter.contactRingtonesAwaitingPermission(Uri.fromFile(withoutSection)))
            assertEquals(0, exporter.contactRingtonesAwaitingPermission(Uri.fromFile(broken)))
            assertEquals(0, exporter.contactRingtonesAwaitingPermission(Uri.fromFile(fromTheFuture)))
        } finally {
            withoutSection.delete()
            broken.delete()
            fromTheFuture.delete()
        }
    }
}
