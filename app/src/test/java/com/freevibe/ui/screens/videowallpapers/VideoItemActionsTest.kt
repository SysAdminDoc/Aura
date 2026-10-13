package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.legal.ProviderAction
import com.freevibe.data.legal.isProviderActionPermitted
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.buildCommunityReportCallablePayload
import com.freevibe.ui.theme.FreeVibeTheme
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@androidx.annotation.OptIn(UnstableApi::class)
class VideoItemActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val reddit = VideoWallpaperItem(
        id = "reddit_abc123",
        title = "Neon rain loop",
        thumbnailUrl = "https://i.redd.it/abc123.jpg",
        source = "Reddit",
        uploaderName = "u/loopmaker",
        contentSource = ContentSource.REDDIT,
        sourcePageUrl = "https://www.reddit.com/r/Cinemagraphs/comments/abc123/",
    )
    private val youtube = VideoWallpaperItem(
        id = "yt_xyz",
        title = "Ocean waves",
        thumbnailUrl = "https://i.ytimg.com/vi/xyz/hqdefault.jpg",
        source = "YouTube",
        uploaderName = "Calm Channel",
        videoId = "xyz",
        contentSource = ContentSource.YOUTUBE,
        sourcePageUrl = "https://www.youtube.com/watch?v=xyz",
    )
    private val gallery = VideoWallpaperItem(
        id = "local_1",
        title = "My clip",
        thumbnailUrl = "",
        source = "Gallery",
        contentSource = ContentSource.LOCAL,
    )

    @Test
    fun `reddit and youtube offer what their terms allow`() {
        val redditActions = reddit.itemActionAvailability()
        val redditDownload = isProviderActionPermitted(ContentSource.REDDIT, ProviderAction.DOWNLOAD)
        assertEquals(redditDownload, redditActions.canDownload)
        assertEquals("Reddit's terms come up before a download", redditDownload, redditActions.downloadNeedsConfirmation)
        assertEquals(isProviderActionPermitted(ContentSource.REDDIT, ProviderAction.SHARE), redditActions.canShare)
        assertTrue(redditActions.canReport)

        val youtubeActions = youtube.itemActionAvailability()
        val youtubeDownload = isProviderActionPermitted(ContentSource.YOUTUBE, ProviderAction.DOWNLOAD)
        assertEquals(youtubeDownload, youtubeActions.canDownload)
        assertEquals(youtubeDownload, youtubeActions.downloadNeedsConfirmation)
        assertFalse("YouTube videos aren't shared outside Aura", youtubeActions.canShare)
        assertTrue(youtubeActions.canReport)

        val galleryActions = gallery.itemActionAvailability()
        assertFalse("a gallery clip has no source page to share", galleryActions.canShare)
        assertFalse("nobody to report a gallery clip to", galleryActions.canReport)

        assertFalse("reports go to the community queue, which is off", reddit.itemActionAvailability(reportsEnabled = false).canReport)
    }

    @Test
    fun `the card menu has no report while the community provider is off`() {
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                VideoCard(
                    item = reddit,
                    streamUrl = null,
                    mediaSourceFactory = mockk(relaxed = true),
                    shouldPreview = false,
                    isApplying = false,
                    onApply = {},
                    onOpen = {},
                    reportsEnabled = false,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Show quick actions").performClick()
        composeRule.onNodeWithText("Report video").assertDoesNotExist()
    }

    @Test
    fun `a report names the video, where it came from and who made it`() {
        val stream = "https://v.redd.it/abc123/DASH_720.mp4"
        val input = reddit.reportInput(CommunityReportReason.RIGHTS, "Not theirs", stream)

        assertEquals("reddit_abc123", input.contentId)
        assertEquals("VIDEO", input.contentType)
        assertEquals(ContentSource.REDDIT, input.contentSource)
        assertEquals(reddit.sourcePageUrl, input.sourceUrl)
        assertEquals("Reddit", input.license)
        assertEquals("u/loopmaker", input.uploaderName)
        assertEquals("the callable takes it as is", "VIDEO", buildCommunityReportCallablePayload(input)["contentType"])

        val noPage = reddit.copy(sourcePageUrl = "")
        assertEquals("falls back to the stream", stream, noPage.reportInput(CommunityReportReason.SPAM, "", stream).sourceUrl)
        assertEquals("never a local file", "", noPage.reportInput(CommunityReportReason.SPAM, "", "file:///cache/a.mp4").sourceUrl)
    }

    @Test
    fun `shared text keeps the credit with the link`() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources

        assertEquals(
            "Neon rain loop by u/loopmaker on Reddit: https://www.reddit.com/r/Cinemagraphs/comments/abc123/",
            resources.videoShareText(reddit),
        )
        assertEquals(
            "Neon rain loop on Reddit: https://www.reddit.com/r/Cinemagraphs/comments/abc123/",
            resources.videoShareText(reddit.copy(uploaderName = "")),
        )
    }

    @Test
    fun `the card menu reaches download, share and report`() {
        var downloads = 0
        var shares = 0
        var reports = 0
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                VideoCard(
                    item = reddit,
                    streamUrl = null,
                    mediaSourceFactory = mockk(relaxed = true),
                    shouldPreview = false,
                    isApplying = false,
                    onApply = {},
                    onOpen = {},
                    onDownload = { downloads++ },
                    onShare = { shares++ },
                    onReport = { reports++ },
                )
            }
        }
        val actions = reddit.itemActionAvailability()

        composeRule.onNodeWithContentDescription("Show quick actions").performClick()
        composeRule.onNodeWithText("Report video").performClick()
        assertEquals(1, reports)

        if (actions.canShare) {
            composeRule.onNodeWithContentDescription("Show quick actions").performClick()
            composeRule.onNodeWithText("Share source").performClick()
            assertEquals(1, shares)
        }
        if (actions.canDownload) {
            composeRule.onNodeWithContentDescription("Show quick actions").performClick()
            composeRule.onNodeWithText("Download").performClick()
            assertEquals(1, downloads)
        }
    }

    @Test
    fun `the full-screen preview has the same actions and a download keeps one copy`() {
        val screen = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt").readText()
        val model = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt").readText()

        assertTrue(screen.contains("onDownload = { requestDownload(item) }"))
        assertTrue(screen.contains("onDownload = requestDownload"))
        assertTrue(screen.contains("ImmersiveVideoActions("))
        assertTrue(screen.contains("if (item.itemActionAvailability().downloadNeedsConfirmation) downloadConfirmItem = item"))
        // Download and Apply look for the saved original first and share the one fetch path.
        assertEquals(2, Regex("""findSavedDownloadedVideo\(item\.id, item\.source\)""").findAll(model).count())
        assertEquals(1, Regex("""prepareDownloadedVideo\(""").findAll(model).count())
        // A Download tap while Apply saves the same video says so instead of doing nothing.
        assertTrue(model.contains("isApplying == item.id) {"))
        assertTrue(model.contains("R.string.video_wp_download_while_applying"))
        assertTrue(screen.contains("reportsEnabled = reportsEnabled"))
    }
}
