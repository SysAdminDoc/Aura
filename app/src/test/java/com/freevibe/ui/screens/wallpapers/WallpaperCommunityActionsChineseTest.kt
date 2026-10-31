package com.freevibe.ui.screens.wallpapers

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.WallpaperUploadRepository
import com.freevibe.service.MediaIngestionImageFlow
import com.freevibe.service.SourceMetrics
import com.freevibe.service.MediaIngestionImageRejected
import com.freevibe.service.imageFormatSupportForFlow
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The messages the community actions build in Kotlin reach a person through the state, so a
 * Simplified Chinese phone has to read them in Chinese, not as the English that used to be inline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh")
class WallpaperCommunityActionsChineseTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val state = MutableStateFlow(WallpapersUiState())
    private val blockRepo = mockk<CommunityBlockRepository>()
    private val uploadRepo = mockk<WallpaperUploadRepository>()

    private fun actions(communityEnabled: Boolean = true) = WallpaperCommunityActions(
        context = context,
        voteRepo = mockk<VoteRepository>(relaxed = true),
        reportRepo = mockk<CommunityReportRepository>(relaxed = true),
        communityBlockRepo = blockRepo,
        wallpaperUploadRepo = uploadRepo,
        cacheManager = mockk<WallpaperCacheManager>(relaxed = true),
        prefs = mockk<PreferencesManager>(relaxed = true),
        sourceMetrics = SourceMetrics(),
        communityProviderEnabled = MutableStateFlow(communityEnabled),
        communityGuidelinesAccepted = MutableStateFlow(true),
        state = state,
        topVoted = MutableStateFlow(emptyList()),
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        fetchTopVoted = {},
    )

    private fun communityWallpaper() = Wallpaper(
        id = "cw_1",
        source = ContentSource.COMMUNITY,
        thumbnailUrl = "https://example.com/t.jpg",
        fullUrl = "https://example.com/f.jpg",
        width = 1440,
        height = 3200,
        communityUploaderId = "creator-1",
    )

    @Test
    fun `a blocked creator is confirmed in Chinese`() {
        coEvery { blockRepo.blockUser("creator-1", CommunityBlockReason.OTHER) } returns Result.success(Unit)

        actions().blockCommunityWallpaper(communityWallpaper())

        assertEquals("已屏蔽创建者", state.value.applySuccess)
    }

    @Test
    fun `a failed block reads in Chinese with the fallback reason`() {
        coEvery { blockRepo.blockUser("creator-1", CommunityBlockReason.OTHER) } returns
            Result.failure(IllegalStateException())

        actions().blockCommunityWallpaper(communityWallpaper())

        assertEquals("屏蔽失败：重试", state.value.error)
    }

    @Test
    fun `a disabled community source is explained in Chinese`() {
        actions(communityEnabled = false).blockCommunityWallpaper(communityWallpaper())

        assertEquals("社区源已在设置中关闭", state.value.error)
    }

    @Test
    fun `a rejected image format fails the upload in Chinese`() {
        val rejection = imageFormatSupportForFlow(MediaIngestionImageFlow.COMMUNITY_WALLPAPER_UPLOAD, "image/gif")
        coEvery {
            uploadRepo.uploadWallpaper(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(MediaIngestionImageRejected(rejection.message))

        actions().uploadCommunityWallpaper(
            localUri = Uri.parse("content://media/picker/1"),
            name = "Dunes",
            category = "nature",
            tags = emptyList(),
            rights = CommunityUploadRights(license = "CC0", rightsAttested = true),
        )

        val error = state.value.error.orEmpty()
        assertEquals(true, error.startsWith("上传失败：请选择"))
        assertEquals(true, error.contains("JPEG"))
        assertEquals(true, error.contains("或") || error.contains("、"))
        assertEquals(false, error.contains("Choose a"))
    }
}
