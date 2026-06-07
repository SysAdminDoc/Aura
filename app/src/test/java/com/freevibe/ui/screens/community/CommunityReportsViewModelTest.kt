package com.freevibe.ui.screens.community

import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityReportRecord
import com.freevibe.data.model.CommunityReportResolutionStatus
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.VoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityReportsViewModelTest {
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
    fun `hide report writes moderation hide and resolution metadata`() = runTest(dispatcher) {
        val report = testReport()
        val reportRepo = mockk<CommunityReportRepository>()
        val voteRepo = mockk<VoteRepository>()
        every { voteRepo.isAdmin } returns true
        every { reportRepo.reports(CommunityReportResolutionStatus.OPEN, any()) } returns flowOf(listOf(report))
        coEvery { voteRepo.moderateHide(report.contentId) } returns Unit
        coEvery {
            reportRepo.resolveReport(report.id, CommunityReportResolutionStatus.HIDDEN, any())
        } returns Result.success(Unit)

        val viewModel = CommunityReportsViewModel(reportRepo, voteRepo)

        viewModel.hide(report)
        advanceUntilIdle()

        assertTrue(viewModel.isAdmin)
        assertEquals("Report hidden", viewModel.state.value.message)
        coVerify { voteRepo.moderateHide(report.contentId) }
        coVerify { reportRepo.resolveReport(report.id, CommunityReportResolutionStatus.HIDDEN, any()) }
    }

    @Test
    fun `dismiss report resolves without moderation hide`() = runTest(dispatcher) {
        val report = testReport()
        val reportRepo = mockk<CommunityReportRepository>()
        val voteRepo = mockk<VoteRepository>()
        every { voteRepo.isAdmin } returns true
        every { reportRepo.reports(CommunityReportResolutionStatus.OPEN, any()) } returns flowOf(listOf(report))
        coEvery {
            reportRepo.resolveReport(report.id, CommunityReportResolutionStatus.DISMISSED, any())
        } returns Result.success(Unit)

        val viewModel = CommunityReportsViewModel(reportRepo, voteRepo)

        viewModel.dismiss(report)
        advanceUntilIdle()

        assertEquals("Report dismissed", viewModel.state.value.message)
        coVerify(exactly = 0) { voteRepo.moderateHide(any()) }
        coVerify { reportRepo.resolveReport(report.id, CommunityReportResolutionStatus.DISMISSED, any()) }
    }

    @Test
    fun `restore report removes moderation hide and resolves restored`() = runTest(dispatcher) {
        val report = testReport()
        val reportRepo = mockk<CommunityReportRepository>()
        val voteRepo = mockk<VoteRepository>()
        every { voteRepo.isAdmin } returns true
        every { reportRepo.reports(CommunityReportResolutionStatus.OPEN, any()) } returns flowOf(listOf(report))
        coEvery { voteRepo.moderateUnhide(report.contentId) } returns Unit
        coEvery {
            reportRepo.resolveReport(report.id, CommunityReportResolutionStatus.RESTORED, any())
        } returns Result.success(Unit)

        val viewModel = CommunityReportsViewModel(reportRepo, voteRepo)

        viewModel.restore(report)
        advanceUntilIdle()

        assertEquals("Report restored", viewModel.state.value.message)
        coVerify { voteRepo.moderateUnhide(report.contentId) }
        coVerify { reportRepo.resolveReport(report.id, CommunityReportResolutionStatus.RESTORED, any()) }
    }

    @Test
    fun `delete upload hides content and deletes reported community upload`() = runTest(dispatcher) {
        val report = testReport()
        val reportRepo = mockk<CommunityReportRepository>()
        val voteRepo = mockk<VoteRepository>()
        every { voteRepo.isAdmin } returns true
        every { reportRepo.reports(CommunityReportResolutionStatus.OPEN, any()) } returns flowOf(listOf(report))
        coEvery { voteRepo.moderateHide(report.contentId) } returns Unit
        coEvery { reportRepo.deleteReportedCommunityUpload(report.id, any()) } returns Result.success(Unit)

        val viewModel = CommunityReportsViewModel(reportRepo, voteRepo)

        viewModel.deleteUpload(report)
        advanceUntilIdle()

        assertEquals("Upload deleted", viewModel.state.value.message)
        coVerify { voteRepo.moderateHide(report.contentId) }
        coVerify { reportRepo.deleteReportedCommunityUpload(report.id, any()) }
        coVerify(exactly = 0) { reportRepo.resolveReport(report.id, any(), any()) }
    }

    @Test
    fun `select status switches report feed to closed queue`() = runTest(dispatcher) {
        val openReport = testReport(id = "report-open", status = CommunityReportResolutionStatus.OPEN)
        val hiddenReport = testReport(id = "report-hidden", status = CommunityReportResolutionStatus.HIDDEN)
        val reportRepo = mockk<CommunityReportRepository>()
        val voteRepo = mockk<VoteRepository>()
        every { voteRepo.isAdmin } returns true
        every { reportRepo.reports(CommunityReportResolutionStatus.OPEN, any()) } returns flowOf(listOf(openReport))
        every { reportRepo.reports(CommunityReportResolutionStatus.HIDDEN, any()) } returns flowOf(listOf(hiddenReport))

        val viewModel = CommunityReportsViewModel(reportRepo, voteRepo)
        val job = backgroundScope.launch { viewModel.reports.collect { } }

        advanceUntilIdle()
        assertEquals(CommunityReportResolutionStatus.OPEN, viewModel.selectedStatus.value)
        assertEquals(listOf(openReport), viewModel.reports.value)

        viewModel.selectStatus(CommunityReportResolutionStatus.HIDDEN)
        advanceUntilIdle()

        assertEquals(CommunityReportResolutionStatus.HIDDEN, viewModel.selectedStatus.value)
        assertEquals(listOf(hiddenReport), viewModel.reports.value)
        verify { reportRepo.reports(CommunityReportResolutionStatus.OPEN, any()) }
        verify { reportRepo.reports(CommunityReportResolutionStatus.HIDDEN, any()) }
        job.cancel()
    }

    @Test
    fun `non admin does not subscribe to private reports feed`() = runTest(dispatcher) {
        val reportRepo = mockk<CommunityReportRepository>()
        val voteRepo = mockk<VoteRepository>()
        every { voteRepo.isAdmin } returns false

        val viewModel = CommunityReportsViewModel(reportRepo, voteRepo)
        advanceUntilIdle()

        assertEquals(emptyList<CommunityReportRecord>(), viewModel.reports.value)
        coVerify(exactly = 0) { reportRepo.resolveReport(any(), any(), any()) }
        coVerify(exactly = 0) { reportRepo.deleteReportedCommunityUpload(any(), any()) }
    }

    private fun testReport(
        id: String = "report-1",
        status: CommunityReportResolutionStatus = CommunityReportResolutionStatus.OPEN,
    ) = CommunityReportRecord(
        id = id,
        contentId = "WALLPAPER::COMMUNITY::cw_1",
        contentKey = "WALLPAPER::COMMUNITY::cw_1",
        contentType = "WALLPAPER",
        contentSource = "COMMUNITY",
        reason = CommunityReportReason.RIGHTS,
        note = "Bad license",
        sourceUrl = "https://example.com/source",
        license = "CC BY",
        uploaderName = "Creator",
        reporterUid = "reporter-1",
        reportedAt = 123L,
        status = status,
    )
}
