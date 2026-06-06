package com.freevibe.data.repository

import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportRecord
import com.freevibe.data.model.CommunityReportResolutionStatus
import com.freevibe.data.model.buildCommunityReportPayload
import com.freevibe.data.model.buildCommunityReportResolutionPayload
import com.freevibe.data.model.communityReportReasonFromStorage
import com.freevibe.data.model.communityReportStatusFromStorage
import com.freevibe.data.model.sanitizeCommunityReportKey
import com.freevibe.service.CommunityIdentityProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    fun reports(
        status: CommunityReportResolutionStatus? = CommunityReportResolutionStatus.OPEN,
        limit: Int = 50,
    ): Flow<List<CommunityReportRecord>> = callbackFlow {
        val reports = reportsRef
        if (reports == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val query = if (status == null) {
            reports.limitToLast(limit)
        } else {
            reports.orderByChild("status").equalTo(status.storageValue).limitToLast(limit)
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val records = snapshot.children
                    .mapNotNull(::snapshotToCommunityReport)
                    .sortedByDescending { it.reportedAt }
                trySend(records)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
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

private fun snapshotToCommunityReport(child: DataSnapshot): CommunityReportRecord? {
    val id = child.key ?: return null
    val contentId = child.child("contentId").getValue(String::class.java).orEmpty()
    if (contentId.isBlank()) return null
    return CommunityReportRecord(
        id = id,
        contentId = contentId,
        contentKey = child.child("contentKey").getValue(String::class.java).orEmpty(),
        contentType = child.child("contentType").getValue(String::class.java).orEmpty(),
        contentSource = child.child("contentSource").getValue(String::class.java).orEmpty(),
        reason = communityReportReasonFromStorage(child.child("reason").getValue(String::class.java)),
        note = child.child("note").getValue(String::class.java).orEmpty(),
        sourceUrl = child.child("sourceUrl").getValue(String::class.java).orEmpty(),
        license = child.child("license").getValue(String::class.java).orEmpty(),
        uploaderName = child.child("uploaderName").getValue(String::class.java).orEmpty(),
        reporterUid = child.child("reporterUid").getValue(String::class.java).orEmpty(),
        reportedAt = child.child("reportedAt").getValue(Long::class.java) ?: 0L,
        status = communityReportStatusFromStorage(child.child("status").getValue(String::class.java)),
        resolverUid = child.child("resolverUid").getValue(String::class.java).orEmpty(),
        resolvedAt = child.child("resolvedAt").getValue(Long::class.java) ?: 0L,
    )
}

private fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}
