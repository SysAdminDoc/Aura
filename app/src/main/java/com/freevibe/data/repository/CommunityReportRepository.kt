package com.freevibe.data.repository

import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportRecord
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityReportResolutionStatus
import com.freevibe.data.model.CommunityTakedownAction
import com.freevibe.data.model.CommunityUploadKind
import com.freevibe.data.model.buildCommunityReportPayload
import com.freevibe.data.model.buildCommunityReportResolutionPayload
import com.freevibe.data.model.buildCommunityTakedownReceiptPayload
import com.freevibe.data.model.buildCommunityUploadDeleteUpdates
import com.freevibe.data.model.communityReportReasonFromStorage
import com.freevibe.data.model.communityReportStatusFromStorage
import com.freevibe.data.model.communityTakedownUploadIdFromContentId
import com.freevibe.data.model.communityTakedownUploadKind
import com.freevibe.data.model.communityUploadMetadataPath
import com.freevibe.data.model.normalizeCommunityReportText
import com.freevibe.data.model.sanitizeCommunityReportKey
import com.freevibe.service.CommunityIdentityProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
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
    private val storage by lazy {
        try { FirebaseStorage.getInstance() } catch (_: Exception) { null }
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
        val reportSnapshot = db.child("community_reports").child(reportKey).get().await()
        val resolution = buildCommunityReportResolutionPayload(
            reportId = reportKey,
            status = status,
            resolverUid = resolverUid,
            resolvedAt = resolvedAt,
            note = note,
        )
        val updates = mutableMapOf<String, Any>(
            "/community_reports/$reportKey/status" to status.storageValue,
            "/community_reports/$reportKey/resolverUid" to resolverUid,
            "/community_reports/$reportKey/resolvedAt" to resolvedAt,
            "/community_report_resolutions/$reportKey" to resolution,
        )
        resolveTakedownTargetOrNull(
            db = db,
            reportKey = reportKey,
            reportSnapshot = reportSnapshot,
            status = status,
            action = CommunityTakedownAction.HIDE,
            resolverUid = resolverUid,
            resolvedAt = resolvedAt,
            note = note,
        )?.let { target ->
            updates["/community_takedown_receipts/$reportKey"] = target.receipt
        }
        db.updateChildren(updates).await()
        Result.success(Unit)
    } catch (e: Exception) {
        e.rethrowIfCancelled()
        Result.failure(e)
    }

    suspend fun deleteReportedCommunityUpload(
        reportId: String,
        note: String = "Deleted after rights review",
    ): Result<Unit> = try {
        val db = database ?: throw IllegalStateException("Firebase Database not available")
        val storageInstance = storage ?: throw IllegalStateException("Firebase Storage not available")
        val reportKey = sanitizeCommunityReportKey(reportId)
        val resolverUid = identityProvider.ensureSignedIn()
        val startedAt = System.currentTimeMillis()
        val reportSnapshot = db.child("community_reports").child(reportKey).get().await()
        val target = resolveTakedownTargetOrNull(
            db = db,
            reportKey = reportKey,
            reportSnapshot = reportSnapshot,
            status = CommunityReportResolutionStatus.HIDDEN,
            action = CommunityTakedownAction.DELETE,
            resolverUid = resolverUid,
            resolvedAt = startedAt,
            note = note,
        ) ?: throw IllegalArgumentException("Report does not reference a deletable rights upload")
        val resolution = buildCommunityReportResolutionPayload(
            reportId = reportKey,
            status = CommunityReportResolutionStatus.HIDDEN,
            resolverUid = resolverUid,
            resolvedAt = startedAt,
            note = note,
        )
        db.updateChildren(
            mapOf(
                "/community_reports/$reportKey/status" to CommunityReportResolutionStatus.HIDDEN.storageValue,
                "/community_reports/$reportKey/resolverUid" to resolverUid,
                "/community_reports/$reportKey/resolvedAt" to startedAt,
                "/community_report_resolutions/$reportKey" to resolution,
                "/community_takedown_receipts/$reportKey" to target.receipt.withDeleteState("STARTED"),
                "/moderation/${target.contentId}" to true,
            ),
        ).await()

        try {
            storageInstance.deleteCommunityStoragePathIfPresent(target.storagePath)
            val deletedAt = System.currentTimeMillis()
            val deleteUpdates = buildCommunityUploadDeleteUpdates(
                kind = target.kind,
                ownerUid = target.uploaderUid,
                uploadId = target.uploadId,
            ).toMutableMap()
            deleteUpdates["/community_takedown_receipts/$reportKey/deleteState"] = "SUCCEEDED"
            deleteUpdates["/community_takedown_receipts/$reportKey/deletedAt"] = deletedAt
            deleteUpdates["/community_takedown_receipts/$reportKey/storageDeleted"] = true
            deleteUpdates["/community_takedown_receipts/$reportKey/metadataDeleted"] = true
            db.updateChildren(deleteUpdates).await()
            Result.success(Unit)
        } catch (deleteError: Exception) {
            deleteError.rethrowIfCancelled()
            runCatching {
                val failedAt = System.currentTimeMillis()
                db.updateChildren(
                    mapOf(
                        "/community_takedown_receipts/$reportKey/deleteState" to "FAILED",
                        "/community_takedown_receipts/$reportKey/failedAt" to failedAt,
                        "/community_takedown_receipts/$reportKey/failureStage" to "DELETE",
                        "/community_takedown_receipts/$reportKey/failureMessage" to normalizeDeleteFailure(deleteError),
                    ),
                ).await()
            }
            throw deleteError
        }
    } catch (e: Exception) {
        e.rethrowIfCancelled()
        Result.failure(e)
    }
}

private data class TakedownTarget(
    val kind: CommunityUploadKind,
    val uploadId: String,
    val contentId: String,
    val storagePath: String,
    val uploaderUid: String,
    val receipt: Map<String, Any>,
)

private suspend fun resolveTakedownTargetOrNull(
    db: DatabaseReference,
    reportKey: String,
    reportSnapshot: DataSnapshot,
    status: CommunityReportResolutionStatus,
    action: CommunityTakedownAction,
    resolverUid: String,
    resolvedAt: Long,
    note: String,
): TakedownTarget? {
    if (status != CommunityReportResolutionStatus.HIDDEN || !reportSnapshot.exists()) return null
    val reason = communityReportReasonFromStorage(reportSnapshot.child("reason").getValue(String::class.java))
    if (reason != CommunityReportReason.RIGHTS) return null
    val contentType = reportSnapshot.child("contentType").getValue(String::class.java).orEmpty()
    val contentSource = reportSnapshot.child("contentSource").getValue(String::class.java).orEmpty()
    val kind = communityTakedownUploadKind(contentType, contentSource) ?: return null
    val contentId = reportSnapshot.child("contentId").getValue(String::class.java).orEmpty()
    val uploadId = communityTakedownUploadIdFromContentId(contentId, kind)
    if (uploadId.isBlank()) return null

    val metadataSnapshot = db.child(kind.metadataRoot).child(uploadId).get().await()
    if (!metadataSnapshot.exists()) return null
    val storagePath = metadataSnapshot.child("storagePath").getValue(String::class.java).orEmpty()
    val uploaderUid = metadataSnapshot.child("uploaderUid").getValue(String::class.java)
        ?: metadataSnapshot.child("uploaderId").getValue(String::class.java)
        ?: ""
    if (storagePath.isBlank() || uploaderUid.isBlank()) return null

    return TakedownTarget(
        kind = kind,
        uploadId = uploadId,
        contentId = contentId,
        storagePath = storagePath,
        uploaderUid = uploaderUid,
        receipt = buildCommunityTakedownReceiptPayload(
            reportId = reportKey,
            contentId = contentId,
            contentType = contentType,
            contentSource = contentSource,
            reason = reason,
            action = action,
            status = status,
            uploadId = uploadId,
            metadataPath = communityUploadMetadataPath(kind, uploadId),
            storagePath = storagePath,
            uploaderUid = uploaderUid,
            resolverUid = resolverUid,
            resolvedAt = resolvedAt,
            note = note,
        ),
    )
}

private fun Map<String, Any>.withDeleteState(state: String): Map<String, Any> = this + mapOf("deleteState" to state)

private fun normalizeDeleteFailure(error: Throwable): String =
    normalizeCommunityReportText(error.message ?: "Delete failed", 240).ifBlank { "Delete failed" }

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
