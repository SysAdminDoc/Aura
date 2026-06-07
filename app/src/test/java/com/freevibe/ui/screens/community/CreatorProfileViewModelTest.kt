package com.freevibe.ui.screens.community

import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CreatorProfileDashboard
import com.freevibe.data.repository.CreatorProfileRepository
import com.freevibe.data.repository.CreatorStats
import com.freevibe.data.repository.CreatorUploadRef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreatorProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blockCreator writes block and removes matching creator rows`() = runTest(dispatcher) {
        val repository = mockk<CreatorProfileRepository>()
        val blockRepo = mockk<CommunityBlockRepository>()
        val blocked = creator("creator/1", "Blocked")
        val sameBlocked = creator("creator.1", "Same Blocked")
        val keep = creator("creator-2", "Keep")
        val dashboard = CreatorProfileDashboard(
            currentCreator = creator("current-user", "Me"),
            topCreators = listOf(blocked, sameBlocked, keep),
            followedCreators = listOf(sameBlocked, keep),
            followedUploads = listOf(upload("u1", "creator.1"), upload("u2", "creator-2")),
            authLabel = "Signed in",
            googleSignInAvailable = true,
        )
        coEvery { repository.getDashboard(80) } returns dashboard
        coEvery { blockRepo.blockUser("creator/1", CommunityBlockReason.OTHER) } returns Result.success(Unit)

        val viewModel = CreatorProfileViewModel(repository, blockRepo)
        advanceUntilIdle()

        assertEquals(listOf("creator/1", "creator.1", "creator-2"), viewModel.state.value.dashboard?.topCreators?.map { it.creatorId })

        viewModel.blockCreator(blocked)
        advanceUntilIdle()

        val updated = viewModel.state.value.dashboard
        assertEquals("Creator blocked", viewModel.state.value.message)
        assertEquals(listOf("creator-2"), updated?.topCreators?.map { it.creatorId })
        assertEquals(listOf("creator-2"), updated?.followedCreators?.map { it.creatorId })
        assertEquals(listOf("creator-2"), updated?.followedUploads?.map { it.creatorId })
        coVerify(exactly = 1) { blockRepo.blockUser("creator/1", CommunityBlockReason.OTHER) }
    }

    private fun creator(id: String, label: String): CreatorStats = CreatorStats(
        creatorId = id,
        label = label,
        soundUploads = 1,
        wallpaperUploads = 1,
        totalVotes = 4,
        favoritesCount = 0,
        isFollowed = id != "current-user",
    )

    private fun upload(id: String, creatorId: String): CreatorUploadRef = CreatorUploadRef(
        id = id,
        stableKey = "stable_$id",
        contentType = "sound",
        title = "Upload $id",
        creatorId = creatorId,
        creatorLabel = creatorId,
        thumbnailUrl = "",
        votes = 1,
        uploadedAt = 1L,
    )
}
