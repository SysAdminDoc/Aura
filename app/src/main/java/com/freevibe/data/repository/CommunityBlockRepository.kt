package com.freevibe.data.repository

import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityUserBlockInput
import com.freevibe.data.model.buildCommunityUserBlockUpdates
import com.freevibe.data.model.buildCommunityUserUnblockUpdates
import com.freevibe.data.model.normalizeCommunityBlockedUserIds
import com.freevibe.data.model.sanitizeCommunityOwnerKey
import com.freevibe.service.CommunityIdentityProvider
import com.freevibe.service.SourceMetrics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val SOURCE_COMMUNITY = "community"

data class CommunityBlockedUser(
    val userId: String,
    val reason: CommunityBlockReason,
    val createdAt: Long,
)

@Singleton
class CommunityBlockRepository @Inject constructor(
    private val identityProvider: CommunityIdentityProvider,
    private val prefs: PreferencesManager,
    private val sourceMetrics: SourceMetrics,
    private val callableClient: CommunityCallableClient,
) {
    private val database by lazy {
        try { FirebaseDatabase.getInstance().reference } catch (_: Exception) { null }
    }

    fun blockedUserIds(): Flow<Set<String>> = flow {
        if (!isCommunityProviderEnabled()) {
            sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
            emit(emptySet())
            return@flow
        }
        val currentUid = identityProvider.currentFirebaseUid()
        if (currentUid.isNullOrBlank()) {
            emit(emptySet())
            return@flow
        }
        val db = database
        if (db == null) {
            emit(emptySet())
            return@flow
        }
        emitAll(observeBlockedUserIds(sanitizeCommunityOwnerKey(currentUid)))
    }

    fun blockedUsers(): Flow<List<CommunityBlockedUser>> = flow {
        if (!isCommunityProviderEnabled()) {
            sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
            emit(emptyList())
            return@flow
        }
        val currentUid = identityProvider.currentFirebaseUid()
        if (currentUid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        val db = database
        if (db == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(observeBlockedUsers(sanitizeCommunityOwnerKey(currentUid)))
    }

    suspend fun blockedUserIdsOnce(): Set<String> = withContext(Dispatchers.IO) {
        try {
            if (!isCommunityProviderEnabled()) {
                sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
                return@withContext emptySet()
            }
            val currentUid = identityProvider.currentFirebaseUid() ?: return@withContext emptySet()
            val db = database ?: return@withContext emptySet()
            parseBlockedUserIds(
                db.child("community_user_blocks")
                    .child(sanitizeCommunityOwnerKey(currentUid))
                    .get()
                    .await(),
            )
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            emptySet()
        }
    }

    suspend fun blockUser(
        blockedUid: String,
        reason: CommunityBlockReason,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isCommunityProviderEnabled()) {
                sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
                throw IllegalStateException(communityDisabledMessage())
            }
            identityProvider.ensureSignedIn()
            if (!identityProvider.currentFirebaseUid().isNullOrBlank()) {
                setCommunityUserBlockWithCallableOrNull(blockedUid, reason, blocked = true)?.let {
                    return@runCatching Unit
                }
            }
            blockUserWithDirectDatabase(blockedUid, reason)
            Unit
        }.onFailure { it.rethrowIfCancelled() }
    }

    suspend fun unblockUser(blockedUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isCommunityProviderEnabled()) {
                sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
                throw IllegalStateException(communityDisabledMessage())
            }
            identityProvider.ensureSignedIn()
            if (!identityProvider.currentFirebaseUid().isNullOrBlank()) {
                setCommunityUserBlockWithCallableOrNull(
                    blockedUid = blockedUid,
                    reason = CommunityBlockReason.OTHER,
                    blocked = false,
                )?.let {
                    return@runCatching Unit
                }
            }
            unblockUserWithDirectDatabase(blockedUid)
            Unit
        }.onFailure { it.rethrowIfCancelled() }
    }

    private suspend fun setCommunityUserBlockWithCallableOrNull(
        blockedUid: String,
        reason: CommunityBlockReason,
        blocked: Boolean,
    ): Boolean? =
        try {
            val result = callableClient.setCommunityUserBlock(
                CommunityUserBlockInput(
                    blockedUid = blockedUid,
                    reason = reason,
                    blocked = blocked,
                ),
            )
            when {
                result.status.equals("accepted", ignoreCase = true) -> true
                result.status.equals("duplicate", ignoreCase = true) -> true
                else -> throw IllegalStateException("Unexpected user block status: ${result.status}")
            }
        } catch (e: CommunityCallableException) {
            if (e.isMissingEndpoint()) null else throw e
        }

    private suspend fun blockUserWithDirectDatabase(
        blockedUid: String,
        reason: CommunityBlockReason,
    ) {
        val db = database ?: throw IllegalStateException("Firebase Database not available")
        val blockerUid = identityProvider.ensureSignedIn()
        db.updateChildren(
            buildCommunityUserBlockUpdates(
                blockerUid = blockerUid,
                blockedUid = blockedUid,
                createdAt = System.currentTimeMillis(),
                reason = reason,
            ),
        ).await()
    }

    private suspend fun unblockUserWithDirectDatabase(blockedUid: String) {
        val db = database ?: throw IllegalStateException("Firebase Database not available")
        val blockerUid = identityProvider.ensureSignedIn()
        db.updateChildren(buildCommunityUserUnblockUpdates(blockerUid, blockedUid)).await()
    }

    private fun observeBlockedUserIds(currentUid: String): Flow<Set<String>> = callbackFlow {
        val db = database
        if (db == null || currentUid.isBlank()) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }

        val ref = db.child("community_user_blocks").child(currentUid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(parseBlockedUserIds(snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptySet())
                close()
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun observeBlockedUsers(currentUid: String): Flow<List<CommunityBlockedUser>> = callbackFlow {
        val db = database
        if (db == null || currentUid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val ref = db.child("community_user_blocks").child(currentUid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(parseBlockedUsers(snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
                close()
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private suspend fun isCommunityProviderEnabled(): Boolean = prefs.communityProviderEnabled.first()

    private fun communityDisabledMessage(): String = "Community source is disabled in Settings"
}

internal fun parseBlockedUserIds(snapshot: DataSnapshot): Set<String> =
    normalizeCommunityBlockedUserIds(
        snapshot.children.mapNotNull { child ->
            child.child("blockedUid").getValue(String::class.java) ?: child.key
        },
    )

internal fun parseBlockedUsers(snapshot: DataSnapshot): List<CommunityBlockedUser> =
    snapshot.children.mapNotNull { child ->
        val userId = sanitizeCommunityOwnerKey(
            child.child("blockedUid").getValue(String::class.java) ?: child.key.orEmpty(),
        )
        if (userId.isBlank()) return@mapNotNull null
        CommunityBlockedUser(
            userId = userId,
            reason = communityBlockReasonFromStorage(child.child("reason").getValue(String::class.java)),
            createdAt = child.child("createdAt").getValue(Long::class.java) ?: 0L,
        )
    }.sortedByDescending { it.createdAt }

private fun communityBlockReasonFromStorage(value: String?): CommunityBlockReason =
    CommunityBlockReason.entries.firstOrNull { it.storageValue == value } ?: CommunityBlockReason.OTHER

private fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}
