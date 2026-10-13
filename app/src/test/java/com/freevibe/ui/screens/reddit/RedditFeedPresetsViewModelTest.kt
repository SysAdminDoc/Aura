package com.freevibe.ui.screens.reddit

import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.RedditFeedMediaType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RedditFeedPresetsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a feed counts as changed only after the screen has seen one`() {
        val prefs = mockk<PreferencesManager> {
            every { redditFeedPresets } returns emptyFlow()
            every { showNsfwContent } returns flowOf(false)
            every { redditProviderEnabled } returns flowOf(true)
        }
        val viewModel = RedditFeedPresetsViewModel(prefs)

        assertFalse("the tab already loaded the first feed", viewModel.feedChanged(RedditFeedMediaType.WALLPAPER, "a"))
        assertFalse(viewModel.feedChanged(RedditFeedMediaType.WALLPAPER, "a"))
        assertTrue("Settings switched feeds while the tab was away", viewModel.feedChanged(RedditFeedMediaType.WALLPAPER, "b"))
        assertFalse("video keeps its own record", viewModel.feedChanged(RedditFeedMediaType.VIDEO, "b"))
    }
}
