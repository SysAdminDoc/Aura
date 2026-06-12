package com.freevibe.service

import android.content.Context
import android.content.SharedPreferences
import com.yausername.youtubedl_android.YoutubeDL
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YtDlpUpdateManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun `updateStable keeps rollback backup until first extraction validates`() = runTest(dispatcher) {
        val fixture = createFixture()
        fixture.runtimeFile.writeText("old")
        val fakeRuntime = FakeYtDlpRuntime(
            updateAction = {
                fixture.runtimeFile.writeText("new")
                activeVersionName = "2026.06.12"
            },
        )
        val manager = fixture.manager(fakeRuntime)

        val update = manager.updateStable()

        assertEquals(YtDlpUpdateStatus.UPDATED_PENDING_VALIDATION, update.status)
        assertEquals("new", fixture.runtimeFile.readText())
        assertTrue(fixture.rollbackFile.exists())
        assertTrue(manager.snapshot().pendingValidation)

        manager.recordExtractionSuccess()

        assertEquals(YtDlpUpdateStatus.VALIDATED, manager.snapshot().lastStatus)
        assertFalse(manager.snapshot().pendingValidation)
        assertFalse(fixture.rollbackDir.exists())
    }

    @Test
    fun `first extraction failure restores previous runtime and version prefs`() = runTest(dispatcher) {
        val fixture = createFixture()
        fixture.runtimeFile.writeText("old")
        fixture.libraryPrefs.edit()
            .putString("dlpVersion", "old-version")
            .putString("dlpVersionName", "old-name")
            .apply()
        val fakeRuntime = FakeYtDlpRuntime(
            updateAction = {
                fixture.runtimeFile.writeText("new")
                fixture.libraryPrefs.edit()
                    .putString("dlpVersion", "new-version")
                    .putString("dlpVersionName", "new-name")
                    .apply()
            },
        )
        val manager = fixture.manager(fakeRuntime)

        manager.updateStable()
        val restored = manager.recordExtractionFailure(IllegalStateException("po token failed"))

        assertTrue(restored)
        assertEquals("old", fixture.runtimeFile.readText())
        assertEquals("old-version", fixture.libraryPrefs.getString("dlpVersion", null))
        assertEquals("old-name", fixture.libraryPrefs.getString("dlpVersionName", null))
        assertEquals(1, fakeRuntime.initYtDlpCalls)
        assertEquals(YtDlpUpdateStatus.ROLLED_BACK, manager.snapshot().lastStatus)
        assertFalse(manager.snapshot().pendingValidation)
    }

    @Test
    fun `failed update restores the previous runtime`() = runTest(dispatcher) {
        val fixture = createFixture()
        fixture.runtimeFile.writeText("old")
        val fakeRuntime = FakeYtDlpRuntime(
            updateAction = {
                fixture.runtimeFile.writeText("partial")
                throw IllegalStateException("network unavailable")
            },
        )
        val manager = fixture.manager(fakeRuntime)

        val update = manager.updateStable()

        assertEquals(YtDlpUpdateStatus.FAILED, update.status)
        assertEquals("old", fixture.runtimeFile.readText())
        assertEquals(YtDlpUpdateStatus.FAILED, manager.snapshot().lastStatus)
        assertFalse(manager.snapshot().pendingValidation)
    }

    private fun createFixture(): Fixture {
        val root = createTempDirectory("ytdlp-update").toFile().also(tempDirs::add)
        val noBackupDir = File(root, "no-backup").apply { mkdirs() }
        val ytdlpRoot = File(noBackupDir, "youtubedl-android").apply { mkdirs() }
        val runtimeDir = File(ytdlpRoot, "yt-dlp").apply { mkdirs() }
        val rollbackDir = File(ytdlpRoot, "yt-dlp.rollback")
        val metadataPrefs = inMemoryPrefs()
        val libraryPrefs = inMemoryPrefs()
        val context = mockk<Context>().also {
            every { it.noBackupFilesDir } returns noBackupDir
            every {
                it.getSharedPreferences("freevibe_ytdlp_update", Context.MODE_PRIVATE)
            } returns metadataPrefs
            every {
                it.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE)
            } returns libraryPrefs
        }
        return Fixture(
            context = context,
            metadataPrefs = metadataPrefs,
            libraryPrefs = libraryPrefs,
            runtimeDir = runtimeDir,
            runtimeFile = File(runtimeDir, "yt-dlp"),
            rollbackDir = rollbackDir,
            rollbackFile = File(rollbackDir, "yt-dlp"),
        )
    }

    private fun Fixture.manager(fakeRuntime: FakeYtDlpRuntime): YtDlpUpdateManager =
        YtDlpUpdateManager(
            context = context,
            ioDispatcher = dispatcher,
        ).also {
            it.runtime = fakeRuntime
        }

    private class Fixture(
        val context: Context,
        @Suppress("unused") val metadataPrefs: SharedPreferences,
        val libraryPrefs: SharedPreferences,
        @Suppress("unused") val runtimeDir: File,
        val runtimeFile: File,
        val rollbackDir: File,
        val rollbackFile: File,
    )

    private class FakeYtDlpRuntime(
        private val updateAction: FakeYtDlpRuntime.() -> Unit,
    ) : YtDlpRuntime {
        var activeVersionName: String? = "bundled"
        var initYtDlpCalls: Int = 0

        override fun init(context: Context) = Unit

        override fun updateStable(context: Context): YoutubeDL.UpdateStatus {
            updateAction()
            return YoutubeDL.UpdateStatus.DONE
        }

        override fun version(context: Context): String? = null

        override fun versionName(context: Context): String? = activeVersionName

        override fun initYtDlp(context: Context, runtimeDir: File) {
            initYtDlpCalls += 1
        }
    }

    private fun inMemoryPrefs(): SharedPreferences {
        val values = linkedMapOf<String, Any?>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            values[firstArg<String>()] = secondArg<String?>()
            editor
        }
        every { editor.putLong(any(), any()) } answers {
            values[firstArg<String>()] = secondArg<Long>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg<String>()] = secondArg<Boolean>()
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg<String>())
            editor
        }
        every { editor.apply() } returns Unit
        return mockk<SharedPreferences>().also { prefs ->
            every { prefs.getString(any(), any()) } answers {
                values[firstArg<String>()] as? String ?: secondArg<String?>()
            }
            every { prefs.getLong(any(), any()) } answers {
                values[firstArg<String>()] as? Long ?: secondArg<Long>()
            }
            every { prefs.getBoolean(any(), any()) } answers {
                values[firstArg<String>()] as? Boolean ?: secondArg<Boolean>()
            }
            every { prefs.edit() } returns editor
        }
    }
}
