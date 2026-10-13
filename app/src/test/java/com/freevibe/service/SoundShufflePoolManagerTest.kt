package com.freevibe.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.LocalMediaStatus
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoundShufflePoolManagerTest {
    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var downloadDao: DownloadDao
    private lateinit var soundApplier: SoundApplier
    private lateinit var manager: SoundShufflePoolManager
    private val moshi = Moshi.Builder().build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        prefs = PreferencesManager(context)
        runBlocking {
            prefs.setSoundShufflePoolsJson("")
            prefs.setRingtoneShuffleEnabled(false)
            prefs.setAlarmShuffleEnabled(false)
        }
        downloadDao = mockk()
        every { downloadDao.getByType("SOUND") } returns flowOf(emptyList())
        soundApplier = mockk(relaxed = true)
        manager = SoundShufflePoolManager(
            context = context,
            prefs = prefs,
            downloadDao = downloadDao,
            bundledContentProvider = BundledContentProvider(),
            soundApplier = soundApplier,
            moshi = moshi,
        )
    }

    @Test
    fun `named pool supports originals target combinations and schedule`() = runTest {
        val poolId = manager.createPool("Morning mix").getOrThrow()
        manager.addOriginal(poolId, "bundled_ringtone_01").getOrThrow()
        manager.setTargets(
            poolId,
            listOf(SoundShuffleTarget.RINGTONE, SoundShuffleTarget.NOTIFICATION, SoundShuffleTarget.ALARM),
        ).getOrThrow()
        manager.setInterval(poolId, 6).getOrThrow()
        manager.setEnabled(poolId, true).getOrThrow()

        val pool = manager.store.first().pools.single()
        assertEquals("Morning mix", pool.name)
        assertEquals(6, pool.intervalHours)
        assertEquals(SoundShuffleTarget.entries, pool.targets)
        assertEquals("bundled:bundled_ringtone_01", pool.items.single().itemId)
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(RingtoneShuffleWorker.workName(poolId)).get()
        assertTrue(work.any { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `disabling a pool cancels its periodic work`() = runTest {
        val poolId = manager.createPool("Quiet hours").getOrThrow()
        manager.addOriginal(poolId, "bundled_notif_01").getOrThrow()
        manager.setTargets(poolId, listOf(SoundShuffleTarget.NOTIFICATION)).getOrThrow()
        manager.setEnabled(poolId, true).getOrThrow()

        manager.setEnabled(poolId, false).getOrThrow()

        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(RingtoneShuffleWorker.workName(poolId)).get()
        assertTrue(work.isNotEmpty())
        assertTrue(work.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `portable store removes device locators and history while retaining assignments`() {
        val raw = moshi.adapter(SoundShuffleStore::class.java).toJson(
            SoundShuffleStore(
                pools = listOf(
                    SoundShufflePool(
                        poolId = "pool-1",
                        name = "Travel",
                        enabled = true,
                        items = listOf(
                            SoundShufflePoolItem(
                                itemId = "local:abc",
                                sourceKind = SoundShuffleSourceKind.LOCAL,
                                sourceId = "abc",
                                name = "Bell",
                                locator = "content://private/audio/1",
                                sha256 = "ab".repeat(32),
                            ),
                            SoundShufflePoolItem(
                                itemId = "bundled:bundled_alarm_01",
                                sourceKind = SoundShuffleSourceKind.BUNDLED,
                                sourceId = "bundled_alarm_01",
                                name = "Sunrise Bells",
                                locator = "rawresource:///123",
                            ),
                        ),
                    ),
                ),
                history = listOf(
                    SoundShuffleHistoryEntry(
                        poolId = "pool-1",
                        poolName = "Travel",
                        target = SoundShuffleTarget.ALARM,
                        status = SoundShuffleHistoryStatus.APPLIED,
                        appliedUri = "content://media/private/2",
                    ),
                ),
            ),
        )

        val portableJson = portableSoundShuffleStoreJson(moshi, raw)
        val restored = parseSoundShuffleStoreStrict(moshi, portableJson)

        assertFalse(portableJson.contains("content://"))
        assertFalse(portableJson.contains("rawresource://"))
        assertTrue(restored.history.isEmpty())
        assertEquals(2, restored.pools.single().items.size)
        assertEquals(LocalMediaStatus.MISSING, restored.pools.single().items.first().localMediaStatus)
        assertEquals(LocalMediaStatus.AVAILABLE, restored.pools.single().items.last().localMediaStatus)
    }

    @Test
    fun `portable export rejects corrupt pool state instead of silently dropping it`() {
        assertThrows(Exception::class.java) {
            portableSoundShuffleStoreJson(moshi, "{not-json")
        }
    }

    @Test
    fun `encoded pool state rejects growth beyond its datastore ceiling`() {
        val oversized = SoundShuffleStore(
            pools = List(5) { poolIndex ->
                SoundShufflePool(
                    poolId = "pool-$poolIndex",
                    name = "Pool $poolIndex",
                    items = List(MAX_SOUND_SHUFFLE_ITEMS_PER_POOL) { itemIndex ->
                        SoundShufflePoolItem(
                            itemId = "item-$poolIndex-$itemIndex",
                            sourceKind = SoundShuffleSourceKind.LOCAL,
                            sourceId = "source-$poolIndex-$itemIndex",
                            name = "Sound $itemIndex",
                            locator = "content://audio/" + "x".repeat(2_040),
                        )
                    },
                )
            },
        )

        assertThrows(IllegalArgumentException::class.java) {
            encodeSoundShuffleStore(moshi, oversized)
        }
    }

    @Test
    fun `merge is duplicate safe by id and case insensitive name`() {
        val existing = SoundShuffleStore(
            pools = listOf(SoundShufflePool("pool-a", "Morning")),
        )
        val imported = SoundShuffleStore(
            pools = listOf(
                SoundShufflePool("pool-a", "Other"),
                SoundShufflePool("pool-b", "morning"),
                SoundShufflePool("pool-c", "Evening"),
            ),
        )

        val merged = mergeSoundShuffleStores(existing, imported)

        assertEquals(listOf("Morning", "Evening"), merged.pools.map { it.name })
    }

    @Test
    fun `selection avoids an immediate repeat when another item is valid`() {
        val items = listOf(
            SoundShufflePoolItem("one", SoundShuffleSourceKind.BUNDLED, "one", "One"),
            SoundShufflePoolItem("two", SoundShuffleSourceKind.BUNDLED, "two", "Two"),
        )

        repeat(20) { seed ->
            assertNotEquals("one", selectSoundShuffleCandidate(items, "one", Random(seed)).itemId)
        }
        assertEquals("one", selectSoundShuffleCandidate(items.take(1), "one", Random(1)).itemId)
    }

    @Test
    fun `target duration policies match feed intent`() {
        assertTrue(isSoundShuffleDurationCompatible(SoundShuffleTarget.RINGTONE, 1_000))
        assertFalse(isSoundShuffleDurationCompatible(SoundShuffleTarget.RINGTONE, 999))
        assertTrue(isSoundShuffleDurationCompatible(SoundShuffleTarget.NOTIFICATION, 580))
        assertFalse(isSoundShuffleDurationCompatible(SoundShuffleTarget.NOTIFICATION, 10_001))
        assertTrue(isSoundShuffleDurationCompatible(SoundShuffleTarget.ALARM, 5_000))
        assertFalse(isSoundShuffleDurationCompatible(SoundShuffleTarget.ALARM, 4_999))
    }

    @Test
    fun `downloaded sound can be assigned by stable library id`() = runTest {
        val download = DownloadEntity(
            id = "youtube:clean-bell",
            source = "YOUTUBE",
            type = "SOUND",
            localPath = "content://media/external/audio/42",
            name = "Clean bell",
            originalMimeType = "audio/mp4",
            originalDurationMs = 8_400,
            originalSizeBytes = 48_000,
            originalSha256 = "ab".repeat(32),
        )
        coEvery { downloadDao.getById(download.id) } returns download
        val poolId = manager.createPool("Downloaded mix").getOrThrow()

        manager.addDownload(poolId, download.id).getOrThrow()

        val item = manager.store.first().pools.single().items.single()
        assertEquals(SoundShuffleSourceKind.DOWNLOAD, item.sourceKind)
        assertEquals(download.id, item.sourceId)
        assertEquals(download.localPath, item.locator)
        assertEquals(download.originalSha256, item.sha256)
    }

    @Test
    fun `missing downloaded sound must be relinked before assignment`() = runTest {
        val download = DownloadEntity(
            id = "missing-download",
            source = "YOUTUBE",
            type = "SOUND",
            localPath = "content://media/external/audio/missing",
            name = "Missing bell",
            localMediaStatus = LocalMediaStatus.MISSING,
        )
        coEvery { downloadDao.getById(download.id) } returns download
        val poolId = manager.createPool("Downloads").getOrThrow()

        val result = manager.addDownload(poolId, download.id)

        assertTrue(result.isFailure)
        assertTrue(manager.store.first().pools.single().items.isEmpty())
    }

    @Test
    fun `run records incompatible and missing sounds before applying a valid member`() = runTest {
        every { soundApplier.canWriteSettings() } returns true
        coEvery { soundApplier.downloadAndApply(any(), any(), any(), any()) } returns
            Result.success(android.net.Uri.parse("content://media/external/audio/applied"))
        val poolId = manager.createPool("Notification mix").getOrThrow()
        manager.addOriginal(poolId, "bundled_ringtone_01").getOrThrow()
        manager.addOriginal(poolId, "bundled_notif_01").getOrThrow()
        manager.setTargets(poolId, listOf(SoundShuffleTarget.NOTIFICATION)).getOrThrow()
        val current = manager.store.first()
        val withMissing = current.copy(
            pools = current.pools.map { pool ->
                if (pool.poolId != poolId) pool else pool.copy(
                    enabled = true,
                    items = pool.items + SoundShufflePoolItem(
                        itemId = "local:missing",
                        sourceKind = SoundShuffleSourceKind.LOCAL,
                        sourceId = "missing",
                        name = "Missing clip",
                        localMediaStatus = LocalMediaStatus.MISSING,
                    ),
                )
            },
        )
        prefs.setSoundShufflePoolsJson(moshi.adapter(SoundShuffleStore::class.java).toJson(withMissing))

        val outcome = manager.runPool(poolId)

        assertEquals(1, outcome.applied)
        assertEquals(2, outcome.skipped)
        assertEquals(0, outcome.failed)
        val history = manager.store.first().history
        assertEquals(3, history.size)
        assertEquals(SoundShuffleHistoryStatus.APPLIED, history[0].status)
        assertEquals(
            "bundled:bundled_notif_01",
            manager.store.first().pools.single().lastAppliedItemIds[SoundShuffleTarget.NOTIFICATION.name],
        )
        assertTrue(history.any { it.status == SoundShuffleHistoryStatus.SKIPPED && it.itemName == "Crystal Chime" })
        assertTrue(history.any { it.status == SoundShuffleHistoryStatus.SKIPPED && it.itemName == "Missing clip" })
        assertEquals(SoundShuffleReason.APPLIED_AUTOMATICALLY.name, history[0].reason)
        assertEquals(
            SoundShuffleReason.DURATION_INCOMPATIBLE.name,
            history.single { it.itemName == "Crystal Chime" }.reason,
        )
        assertEquals(
            SoundShuffleReason.LOCAL_NEEDS_RELINK.name,
            history.single { it.itemName == "Missing clip" }.reason,
        )
        coVerify(exactly = 1) { soundApplier.downloadAndApply(any(), any(), any(), any()) }
    }

    @Test
    fun `pool remembers its last item beyond the bounded history list`() = runTest {
        every { soundApplier.canWriteSettings() } returns true
        coEvery { soundApplier.downloadAndApply(any(), any(), any(), any()) } returns
            Result.success(android.net.Uri.parse("content://media/external/audio/applied"))
        val poolId = manager.createPool("No repeats").getOrThrow()
        manager.addOriginal(poolId, "bundled_notif_01").getOrThrow()
        manager.addOriginal(poolId, "bundled_notif_02").getOrThrow()
        manager.setTargets(poolId, listOf(SoundShuffleTarget.NOTIFICATION)).getOrThrow()
        manager.setEnabled(poolId, true).getOrThrow()

        manager.runPool(poolId)
        val first = manager.store.first().pools.single()
            .lastAppliedItemIds.getValue(SoundShuffleTarget.NOTIFICATION.name)
        val filledHistory = manager.store.first().copy(
            history = List(MAX_SOUND_SHUFFLE_HISTORY) { index ->
                SoundShuffleHistoryEntry(
                    historyId = "other-$index",
                    poolId = poolId,
                    poolName = "No repeats",
                    itemId = "unrelated-$index",
                    itemName = "Unrelated",
                    target = SoundShuffleTarget.RINGTONE,
                    status = SoundShuffleHistoryStatus.SKIPPED,
                )
            },
        )
        prefs.setSoundShufflePoolsJson(moshi.adapter(SoundShuffleStore::class.java).toJson(filledHistory))

        manager.runPool(poolId)

        val second = manager.store.first().pools.single()
            .lastAppliedItemIds.getValue(SoundShuffleTarget.NOTIFICATION.name)
        assertNotEquals(first, second)
    }

    @Test
    fun `legacy toggles migrate once and restore a named scheduled pool`() = runTest {
        val download = DownloadEntity(
            id = "legacy-sound",
            source = "YOUTUBE",
            type = "SOUND",
            localPath = "content://media/external/audio/7",
            name = "Legacy sound",
            originalMimeType = "audio/mp4",
            originalDurationMs = 9_000,
        )
        every { downloadDao.getByType("SOUND") } returns flowOf(listOf(download))
        prefs.setRingtoneShuffleEnabled(true)
        prefs.setAlarmShuffleEnabled(true)
        prefs.setRingtoneShuffleIntervalHours(6)

        manager.migrateLegacyIfNeeded()
        manager.migrateLegacyIfNeeded()

        val store = manager.store.first()
        val pool = store.pools.single()
        assertTrue(store.legacyMigrated)
        assertEquals("My downloads", pool.name)
        assertEquals(6, pool.intervalHours)
        assertEquals(listOf(SoundShuffleTarget.RINGTONE, SoundShuffleTarget.ALARM), pool.targets)
        assertEquals("download:${download.id}", pool.items.single().itemId)
        assertFalse(prefs.ringtoneShuffleEnabled.first())
        assertFalse(prefs.alarmShuffleEnabled.first())
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(RingtoneShuffleWorker.workName(pool.poolId)).get()
        assertTrue(work.any { it.state == WorkInfo.State.ENQUEUED })
    }
}
