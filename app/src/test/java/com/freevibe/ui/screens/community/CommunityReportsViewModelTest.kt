package com.freevibe.ui.screens.community

import android.content.Context
import com.freevibe.R
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityReportRecord
import com.freevibe.data.model.CommunityReportResolutionStatus
import com.freevibe.data.repository.CommunityBlockRepository
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

        val viewModel = createViewModel(reportRepo, voteRepo)

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

        val viewModel = createViewModel(reportRepo, voteRepo)

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

        val viewModel = createViewModel(reportRepo, voteRepo)

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

        val viewModel = createViewModel(reportRepo, voteRepo)

        viewModel.deleteUpload(report)
        advanceUntilIdle()

        assertEquals("Upload deleted", viewModel.state.value.message)
        coVerify { voteRepo.moderateHide(report.contentId) }
        coVerify { reportRepo.deleteReportedCommunityUpload(report.id, any()) }
        coVerify(exactly = 0) { reportRepo.resolveReport(report.id, any(), any()) }
    }

    @Test
    fun `block reported uploader writes private block`() = runTest(dispatcher) {
        val report = testReport(uploaderUid = "uploader-1")
        val reportRepo = mockk<CommunityReportRepository>()
        val voteRepo = mockk<VoteRepository>()
        val blockRepo = mockk<CommunityBlockRepository>()
        every { voteRepo.isAdmin } returns true
        every { reportRepo.reports(CommunityReportResolutionStatus.OPEN, any()) } returns flowOf(listOf(report))
        coEvery { blockRepo.blockUser("uploader-1", CommunityBlockReason.OTHER) } returns Result.success(Unit)

        val viewModel = createViewModel(reportRepo, voteRepo, blockRepo)

        viewModel.blockReportedUploader(report)
        advanceUntilIdle()

        assertEquals("Creator blocked", viewModel.state.value.message)
        coVerify(exactly = 1) { blockRepo.blockUser("uploader-1", CommunityBlockReason.OTHER) }
        coVerify(exactly = 0) { reportRepo.resolveReport(any(), any(), any()) }
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

        val viewModel = createViewModel(reportRepo, voteRepo)
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
    fun `failed refresh keeps the last good reports and surfaces the error`() = runTest(dispatcher) {
        val report = testReport()
        val reportRepo = mockk<CommunityReportRepository>()
        val voteRepo = mockk<VoteRepository>()
        every { voteRepo.isAdmin } returns true
        every { reportRepo.reports(CommunityReportResolutionStatus.OPEN, any()) } returnsMany listOf(
            flowOf(listOf(report)),
            flow<List<CommunityReportRecord>> { throw IllegalStateException("Permission denied") },
        )

        val viewModel = createViewModel(reportRepo, voteRepo)
        val job = backgroundScope.launch { viewModel.reports.collect { } }
        advanceUntilIdle()
        assertEquals(listOf(report), viewModel.reports.value)
        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.lastUpdatedAt)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("stale rows stay visible", listOf(report), viewModel.reports.value)
        assertEquals(ReportsLoadErrorKind.DENIED, viewModel.state.value.loadError?.kind)
        assertFalse(viewModel.state.value.isLoading)
        job.cancel()
    }

    @Test
    fun `offline and denied failures get different messages`() {
        val offline = classifyReportsLoadError(java.io.IOException("Client is offline"))
        val disconnected = classifyReportsLoadError(
            IllegalStateException("The operation had to be aborted due to a network disconnect"),
        )
        val denied = classifyReportsLoadError(IllegalStateException("Permission denied"))
        val other = classifyReportsLoadError(IllegalStateException("Quota exceeded"))

        assertEquals(ReportsLoadErrorKind.OFFLINE, offline.kind)
        assertEquals(ReportsLoadErrorKind.OFFLINE, disconnected.kind)
        assertEquals(ReportsLoadErrorKind.DENIED, denied.kind)
        assertNotEquals(offline.kind, denied.kind)
        assertEquals(ReportsLoadErrorKind.OTHER, other.kind)
        assertEquals("Quota exceeded", other.detail)
    }

    @Test
    fun `the localized message leads and the raw reason sits under it`() {
        fun message(error: ReportsLoadError?) =
            reportsLoadErrorMessage(error, offline = "Offline", denied = "Denied", generic = "Couldn't load reports")

        assertEquals("Couldn't load reports\nQuota exceeded", message(ReportsLoadError(ReportsLoadErrorKind.OTHER, "Quota exceeded")))
        assertEquals("Couldn't load reports", message(ReportsLoadError(ReportsLoadErrorKind.OTHER, " ")))
        assertEquals("Couldn't load reports", message(null))
        assertEquals("Offline", message(ReportsLoadError(ReportsLoadErrorKind.OFFLINE, "Client is offline")))
        assertEquals("Denied", message(ReportsLoadError(ReportsLoadErrorKind.DENIED, "Permission denied")))
    }

    @Test
    fun `failed load on a new status tab does not show the previous tab's rows`() = runTest(dispatcher) {
        val openReport = testReport(id = "report-open")
        val reportRepo = mockk<CommunityReportRepository>()
        val voteRepo = mockk<VoteRepository>()
        every { voteRepo.isAdmin } returns true
        every { reportRepo.reports(CommunityReportResolutionStatus.OPEN, any()) } returns flowOf(listOf(openReport))
        every { reportRepo.reports(CommunityReportResolutionStatus.HIDDEN, any()) } returns
            flow<List<CommunityReportRecord>> { throw IllegalStateException("offline") }

        val viewModel = createViewModel(reportRepo, voteRepo)
        val job = backgroundScope.launch { viewModel.reports.collect { } }
        advanceUntilIdle()

        viewModel.selectStatus(CommunityReportResolutionStatus.HIDDEN)
        advanceUntilIdle()

        assertEquals(emptyList<CommunityReportRecord>(), viewModel.reports.value)
        assertEquals(ReportsLoadErrorKind.OFFLINE, viewModel.state.value.loadError?.kind)
        job.cancel()
    }

    @Test
    fun `non admin does not subscribe to private reports feed`() = runTest(dispatcher) {
        val reportRepo = mockk<CommunityReportRepository>()
        val voteRepo = mockk<VoteRepository>()
        every { voteRepo.isAdmin } returns false

        val viewModel = createViewModel(reportRepo, voteRepo)
        advanceUntilIdle()

        assertEquals(emptyList<CommunityReportRecord>(), viewModel.reports.value)
        coVerify(exactly = 0) { reportRepo.resolveReport(any(), any(), any()) }
        coVerify(exactly = 0) { reportRepo.deleteReportedCommunityUpload(any(), any()) }
    }

    private fun testReport(
        id: String = "report-1",
        status: CommunityReportResolutionStatus = CommunityReportResolutionStatus.OPEN,
        uploaderUid: String = "",
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
        uploaderUid = uploaderUid,
        reporterUid = "reporter-1",
        reportedAt = 123L,
        status = status,
    )

    private fun createViewModel(
        reportRepo: CommunityReportRepository,
        voteRepo: VoteRepository,
        blockRepo: CommunityBlockRepository = mockk(relaxed = true),
    ): CommunityReportsViewModel = CommunityReportsViewModel(localizedContext(), reportRepo, voteRepo, blockRepo)

    private fun localizedContext(): Context = mockk<Context>(relaxed = true).also { context ->
        every { context.getString(R.string.reports_action_hidden) } returns "Report hidden"
        every { context.getString(R.string.reports_action_dismissed) } returns "Report dismissed"
        every { context.getString(R.string.reports_action_restored) } returns "Report restored"
        every { context.getString(R.string.feedback_upload_deleted) } returns "Upload deleted"
        every { context.getString(R.string.feedback_creator_blocked) } returns "Creator blocked"
    }
}
