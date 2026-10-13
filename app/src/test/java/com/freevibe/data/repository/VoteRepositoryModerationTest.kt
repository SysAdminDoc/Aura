package com.freevibe.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.local.PreferencesManager
import com.freevibe.service.CommunityIdentityProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VoteRepositoryModerationTest {

    private fun newRepository(): VoteRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = mockk<PreferencesManager>(relaxed = true) {
            every { communityProviderEnabled } returns flowOf(true)
            every { communityGuidelinesAccepted } returns flowOf(true)
        }
        return VoteRepository(context, mockk<CommunityIdentityProvider>(relaxed = true), mockk(relaxed = true), prefs)
    }

    @Test
    fun `a cancelled moderation listener marks the list degraded and keeps it`() = runTest {
        val repo = newRepository()
        awaitAttached(repo)
        repo.onModerationSnapshot(setOf("wallpaper-1"))

        repo.onModerationCancelled("Permission denied")

        assertTrue(repo.moderationSyncDegraded.value)
        assertTrue("moderated items stay hidden while the list is stale", repo.isHidden("wallpaper-1"))
        assertFalse("a cancelled listener frees its slot so it can attach again", repo.isModerationListenerAttached())
    }

    /**
     * The repository attaches from its own scope once consent reads true. Cancelling before that
     * lands would let the late attach fill the slot again and hide what the test is checking.
     */
    private fun awaitAttached(repo: VoteRepository) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!repo.isModerationListenerAttached()) {
            check(System.nanoTime() < deadline) { "the moderation listener never attached" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `a fresh snapshot clears the degraded mark`() = runTest {
        val repo = newRepository()
        repo.onModerationCancelled("Disconnected")

        repo.onModerationSnapshot(setOf("wallpaper-2"))

        assertFalse(repo.moderationSyncDegraded.value)
        assertTrue(repo.hiddenIds.first().contains("wallpaper-2"))
    }
}
