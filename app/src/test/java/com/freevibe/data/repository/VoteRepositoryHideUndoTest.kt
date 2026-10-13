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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the reversibility the hide/undo snackbar relies on: a non-admin downvote hides an
 * item locally, and undoDownvote restores it. Without this, an accidental Hide is unrecoverable.
 */
@RunWith(RobolectricTestRunner::class)
class VoteRepositoryHideUndoTest {

    private fun newRepository(): VoteRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = mockk<PreferencesManager>(relaxed = true) {
            every { communityProviderEnabled } returns flowOf(true)
            every { communityGuidelinesAccepted } returns flowOf(true)
        }
        val identity = mockk<CommunityIdentityProvider>(relaxed = true)
        val callable = mockk<CommunityCallableClient>(relaxed = true)
        return VoteRepository(context, identity, callable, prefs)
    }

    @Test
    fun `local hide then undoDownvote restores the item to the feed`() = runTest {
        val repo = newRepository()
        val id = "sound-abc123"

        assertTrue("feed starts unhidden", repo.hiddenIds.first().isEmpty())

        repo.hideLocally(id)
        assertFalse("item is hidden after downvote", repo.hiddenIds.first().isEmpty())

        repo.undoDownvote(id)
        assertTrue("undo restores the item", repo.hiddenIds.first().isEmpty())
    }

    @Test
    fun `labelled hides survive a restart and restore one or all`() = runTest {
        val first = newRepository()
        val neon = HiddenItemLabel(HiddenItemKind.VIDEO, "Neon rain loop", "https://i.redd.it/a.jpg", "Reddit")
        first.hideLocally("reddit_a", neon)
        first.hideLocally("reddit_b", neon.copy(title = "Ocean waves"))
        // Hides from before labels were saved have only the ID.
        first.hideLocally("legacy-id")

        // A new instance reads the same preferences, the way the app does after a restart.
        val restarted = newRepository()
        val items = restarted.localHiddenItems.first()
        assertEquals(setOf("reddit_a", "reddit_b", "legacy-id"), items.map { it.id }.toSet())
        assertEquals(neon, items.first { it.id == "reddit_a" }.label)
        assertNull(items.first { it.id == "legacy-id" }.label)
        assertEquals("unlabeled hides sort last", "legacy-id", items.last().id)

        restarted.unhideLocally(listOf("reddit_a"))
        assertEquals(setOf("reddit_b", "legacy-id"), newRepository().localHiddenIds.value)

        restarted.unhideLocally(restarted.localHiddenItems.first().map { it.id })
        assertTrue(newRepository().localHiddenItems.first().isEmpty())
        assertTrue(newRepository().hiddenIds.first().isEmpty())
    }

    @Test
    fun `undo puts restored items back with their labels and times`() = runTest {
        val repo = newRepository()
        repo.hideLocally("w1", HiddenItemLabel(HiddenItemKind.WALLPAPER, "Space", "https://example.com/t.jpg", "Wallhaven"))
        repo.hideLocally("s1", HiddenItemLabel(HiddenItemKind.SOUND, "Chime", sourceName = "Aura Originals"))
        val before = repo.localHiddenItems.first()

        repo.unhideLocally(before.map { it.id })
        repo.hideLocally(before)

        assertEquals(before, newRepository().localHiddenItems.first())
    }

    @Test
    fun `a damaged label store still lists every hide`() = runTest {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("hidden_ids", setOf("a"))
            .putString("hidden_labels", "{not json")
            .commit()

        assertEquals(listOf(HiddenItem("a", null, 0L)), newRepository().localHiddenItems.first())
    }

    @Test
    fun `a restore finds the stored hide whether it was saved raw or sanitized`() {
        val local = setOf("reddit/a.b", "yt_c_d", "unrelated")

        assertEquals(setOf("reddit/a.b", "yt_c_d"), localHidesFor(listOf("reddit/a.b", "yt.c#d", "not-hidden"), local))
    }
}
