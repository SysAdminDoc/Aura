package com.freevibe.data.repository

import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportResolutionStatus
import com.freevibe.data.model.buildCommunityReportPayload
import com.freevibe.data.model.buildCommunityReportResolutionPayload
import com.freevibe.data.model.sanitizeCommunityReportKey
import com.freevibe.service.CommunityIdentityProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityReportRepository @Inject constructor(
    private val identityProvider: CommunityIdentityProvider,
) {
    private val database by lazy {
        try { FirebaseDatabase.getInstance().reference } catch (_: Exception) { null }
    }
    private val reportsRef get() = database?.child("community_reports")
    private val resolutionsRef get() = database?.child("community_report_resolutions")

    suspend fun submitReport(input: CommunityReportInput): Result<String> = try {
        val reports = reportsRef ?: throw IllegalStateException("Firebase Database not available")
        val reporterUid = identityProvider.ensureSignedIn()
        val reportedAt = System.currentTimeMillis()
        val payload = buildCommunityReportPayload(
            input = input,
            reporterUid = reporterUid,
            reportedAt = reportedAt,
        )
        val ref = reports.push()
        ref.setValue(payload).await()
        Result.success(ref.key.orEmpty())
    } catch (e: Exception) {
        e.rethrowIfCancelled()
        Result.failure(e)
    }

    suspend fun resolveReport(
        reportId: String,
        status: CommunityReportResolutionStatus,
        note: String = "",
    ): Result<Unit> = try {
        val db = database ?: throw IllegalStateException("Firebase Database not available")
        val reportKey = sanitizeCommunityReportKey(reportId)
        val resolverUid = identityProvider.ensureSignedIn()
        val resolvedAt = System.currentTimeMillis()
        val resolution = buildCommunityReportResolutionPayload(
            reportId = reportKey,
            status = status,
            resolverUid = resolverUid,
            resolvedAt = resolvedAt,
            note = note,
        )
        db.updateChildren(
            mapOf(
                "/community_reports/$reportKey/status" to status.storageValue,
                "/community_reports/$reportKey/resolverUid" to resolverUid,
                "/community_reports/$reportKey/resolvedAt" to resolvedAt,
                "/community_report_resolutions/$reportKey" to resolution,
            ),
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        e.rethrowIfCancelled()
        Result.failure(e)
    }
}

private fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}
