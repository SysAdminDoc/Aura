package com.freevibe.data.repository

import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.CommunityFollowInput
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.CreatorProfileUpdateInput
import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.normalizeCreatorProfileInput
import com.freevibe.data.model.stableKey
import com.freevibe.service.CommunityIdentityProvider
import com.freevibe.service.SourceMetrics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CreatorUploadRef(
    val id: String,
    val stableKey: String,
    val contentType: String,
    val title: String,
    val creatorId: String,
    val creatorLabel: String,
    val thumbnailUrl: String,
    val votes: Int,
    val uploadedAt: Long,
)

data class CreatorStats(
    val creatorId: String,
    val label: String,
    val soundUploads: Int,
    val wallpaperUploads: Int,
    val totalVotes: Int,
    val favoritesCount: Int,
    val isFollowed: Boolean = false,
) {
    val uploadCount: Int get() = soundUploads + wallpaperUploads
}

data class CreatorProfileDashboard(
    val currentCreator: CreatorStats,
    val topCreators: List<CreatorStats>,
    val followedCreators: List<CreatorStats>,
    val followedUploads: List<CreatorUploadRef>,
    val authLabel: String,
    val googleSignInAvailable: Boolean,
    val currentProfile: CreatorPublicProfile = CreatorPublicProfile(),
)

data class CreatorPublicProfile(
    val displayName: String = "",
    val bio: String = "",
    val websiteUrl: String = "",
    val avatarUrl: String = "",
)

@Singleton
class CreatorProfileRepository @Inject constructor(
    private val identityProvider: CommunityIdentityProvider,
    private val voteRepo: VoteRepository,
    private val favoritesRepo: FavoritesRepository,
    private val prefs: PreferencesManager,
    private val sourceMetrics: SourceMetrics,
    private val communityBlockRepo: CommunityBlockRepository,
    private val callableClient: CommunityCallableClient,
) {
    private val database by lazy {
        try { FirebaseDatabase.getInstance().reference } catch (_: Exception) { null }
    }

    suspend fun getDashboard(limit: Int = 80): CreatorProfileDashboard = withContext(Dispatchers.IO) {
        ensureCommunityEnabled()
        val currentUserId = identityProvider.ensureSignedIn()
        val knownIds = identityProvider.knownIdentityIds().toSet() + currentUserId
        val currentProfile = readCreatorProfile(currentUserId)
        val followed = getFollowedCreatorIds()
        val blockedCreatorIds = communityBlockRepo.blockedUserIdsOnce()
        val uploads = filterCreatorUploadsForBlockedUsers(fetchCommunityUploads(limit), blockedCreatorIds)
        val localFavoritesCount = favoritesRepo.count().first()
        val rankedCreators = aggregateCreatorStats(
            uploads = uploads,
            currentUserIds = knownIds,
            followedCreatorIds = followed,
        )
        val current = rankedCreators.firstOrNull { it.creatorId in knownIds }
            ?.let { stats ->
                stats.copy(
                    favoritesCount = localFavoritesCount,
                    label = currentProfile.displayName.ifBlank { stats.label },
                )
            }
            ?: CreatorStats(
                creatorId = currentUserId,
                label = currentProfile.displayName.ifBlank { identityProvider.currentUploaderLabel() },
                soundUploads = 0,
                wallpaperUploads = 0,
                totalVotes = 0,
                favoritesCount = localFavoritesCount,
                isFollowed = false,
            )
        CreatorProfileDashboard(
            currentCreator = current,
            topCreators = rankedCreators
                .filter { it.uploadCount > 0 }
                .sortedWith(compareByDescending<CreatorStats> { it.totalVotes }.thenByDescending { it.uploadCount })
                .take(10),
            followedCreators = rankedCreators
                .filter { it.creatorId in followed }
                .sortedBy { it.label.lowercase(java.util.Locale.ROOT) },
            followedUploads = uploads
                .filter { it.creatorId in followed }
                .sortedByDescending { it.uploadedAt }
                .take(20),
            authLabel = identityProvider.currentAuthLabel(),
            googleSignInAvailable = identityProvider.hasGoogleOAuthClient(),
            currentProfile = currentProfile,
        )
    }

    suspend fun updateCreatorProfile(input: CreatorProfileUpdateInput): Result<CreatorPublicProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureCommunityEnabled()
                val profileUid = identityProvider.ensureSignedIn()
                val normalizedInput = normalizeCreatorProfileInput(input)
                if (!identityProvider.currentFirebaseUid().isNullOrBlank()) {
                    updateCreatorProfileWithCallableOrNull(normalizedInput)?.let {
                        return@runCatching normalizedInput.toPublicProfile()
                    }
                }
                updateCreatorProfileWithDirectDatabase(profileUid, normalizedInput)
            }.onFailure { it.rethrowIfCancelled() }
        }

    suspend fun followCreator(creatorId: String, label: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureCommunityEnabled()
            identityProvider.ensureSignedIn()
            if (!identityProvider.currentFirebaseUid().isNullOrBlank()) {
                setCreatorFollowWithCallableOrNull(creatorId, label, following = true)?.let {
                    return@runCatching Unit
                }
            }
            followCreatorWithDirectDatabase(creatorId, label)
            Unit
        }.onFailure { it.rethrowIfCancelled() }
    }

    suspend fun unfollowCreator(creatorId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureCommunityEnabled()
            identityProvider.ensureSignedIn()
            if (!identityProvider.currentFirebaseUid().isNullOrBlank()) {
                setCreatorFollowWithCallableOrNull(creatorId, label = "", following = false)?.let {
                    return@runCatching Unit
                }
            }
            unfollowCreatorWithDirectDatabase(creatorId)
            Unit
        }.onFailure { it.rethrowIfCancelled() }
    }

    private suspend fun setCreatorFollowWithCallableOrNull(
        creatorId: String,
        label: String,
        following: Boolean,
    ): Boolean? =
        try {
            val result = callableClient.setCreatorFollow(
                CommunityFollowInput(
                    creatorId = creatorId,
                    label = label,
                    following = following,
                ),
            )
            when {
                result.status.equals("accepted", ignoreCase = true) -> true
                result.status.equals("duplicate", ignoreCase = true) -> true
                else -> throw IllegalStateException("Unexpected creator follow status: ${result.status}")
            }
        } catch (e: CommunityCallableException) {
            if (e.isMissingEndpoint()) null else throw e
        }

    private suspend fun updateCreatorProfileWithCallableOrNull(input: CreatorProfileUpdateInput): Boolean? =
        try {
            val result = callableClient.updateCreatorProfile(input)
            when {
                result.status.equals("accepted", ignoreCase = true) -> true
                result.status.equals("duplicate", ignoreCase = true) -> true
                else -> throw IllegalStateException("Unexpected creator profile status: ${result.status}")
            }
        } catch (e: CommunityCallableException) {
            if (e.isMissingEndpoint()) null else throw e
        }

    private suspend fun updateCreatorProfileWithDirectDatabase(
        profileUid: String,
        input: CreatorProfileUpdateInput,
    ): CreatorPublicProfile {
        val db = database ?: throw IllegalStateException("Firebase Database not available")
        val now = System.currentTimeMillis()
        val profileRef = db.child("creator_profiles").child(profileUid)
        val createdAt = profileRef.get().await()
            .child("createdAt")
            .getValue(Long::class.java)
            ?.takeIf { it > 0L }
            ?: now
        profileRef.setValue(
            mapOf(
                "profileUid" to profileUid,
                "displayName" to input.displayName,
                "bio" to input.bio,
                "websiteUrl" to input.websiteUrl,
                "avatarUrl" to input.avatarUrl,
                "createdAt" to createdAt,
                "updatedAt" to now,
            ),
        ).await()
        return input.toPublicProfile()
    }

    private suspend fun followCreatorWithDirectDatabase(creatorId: String, label: String) {
        val db = database ?: throw IllegalStateException("Firebase Database not available")
        val currentUserId = voteRepo.sanitizeKey(identityProvider.ensureSignedIn())
        val safeCreatorId = voteRepo.sanitizeKey(creatorId)
        db.child("creator_follows")
            .child(currentUserId)
            .child(safeCreatorId)
            .setValue(
                mapOf(
                    "creatorId" to creatorId,
                    "label" to label,
                    "followedAt" to System.currentTimeMillis(),
                ),
            )
            .await()
    }

    private suspend fun unfollowCreatorWithDirectDatabase(creatorId: String) {
        val db = database ?: throw IllegalStateException("Firebase Database not available")
        val currentUserId = voteRepo.sanitizeKey(identityProvider.ensureSignedIn())
        val safeCreatorId = voteRepo.sanitizeKey(creatorId)
        db.child("creator_follows").child(currentUserId).child(safeCreatorId).removeValue().await()
    }

    private suspend fun getFollowedCreatorIds(): Set<String> {
        val db = database ?: return emptySet()
        val currentUserId = voteRepo.sanitizeKey(identityProvider.ensureSignedIn())
        return try {
            db.child("creator_follows").child(currentUserId).get().await()
                .children
                .mapNotNull { child ->
                    child.child("creatorId").getValue(String::class.java) ?: child.key
                }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            emptySet()
        }
    }

    private suspend fun readCreatorProfile(profileUid: String): CreatorPublicProfile {
        val db = database ?: return CreatorPublicProfile()
        return try {
            val snapshot = db.child("creator_profiles").child(profileUid).get().await()
            CreatorPublicProfile(
                displayName = snapshot.child("displayName").getValue(String::class.java).orEmpty(),
                bio = snapshot.child("bio").getValue(String::class.java).orEmpty(),
                websiteUrl = snapshot.child("websiteUrl").getValue(String::class.java).orEmpty(),
                avatarUrl = snapshot.child("avatarUrl").getValue(String::class.java).orEmpty(),
            )
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            CreatorPublicProfile()
        }
    }

    private suspend fun ensureCommunityEnabled() {
        if (!prefs.communityProviderEnabled.first()) {
            sourceMetrics.recordDisabled("community")
            throw IllegalStateException("Community source is disabled in Settings")
        }
    }

    private suspend fun fetchCommunityUploads(limit: Int): List<CreatorUploadRef> {
        val db = database ?: return emptyList()
        return coroutineScope {
            val soundTask = async { db.child("community_sounds").limitToLast(limit).get().await().children.mapNotNull(::soundUploadRef) }
            val wallpaperTask = async { db.child("community_wallpapers").limitToLast(limit).get().await().children.mapNotNull(::wallpaperUploadRef) }
            val uploads = (soundTask.await() + wallpaperTask.await())
                .filter { it.creatorId.isNotBlank() }
                .sortedByDescending { it.uploadedAt }
                .take(limit * 2)
            val voteCounts = voteRepo.getVoteCountsOnce(uploads.map { it.stableKey })
            uploads.map { it.copy(votes = voteCounts[it.stableKey] ?: it.votes) }
        }
    }

    private fun soundUploadRef(child: DataSnapshot): CreatorUploadRef? {
        val key = child.key ?: return null
        val url = child.child("downloadUrl").getValue(String::class.java).orEmpty()
        val creatorId = child.child("uploaderId").getValue(String::class.java).orEmpty()
        val stableKey = Sound(
            id = "cu_$key",
            source = ContentSource.COMMUNITY,
            name = child.child("name").getValue(String::class.java).orEmpty(),
            previewUrl = url,
            downloadUrl = url,
        ).stableKey()
        return CreatorUploadRef(
            id = "cu_$key",
            stableKey = stableKey,
            contentType = "sound",
            title = child.child("name").getValue(String::class.java).orEmpty().ifBlank { "Community sound" },
            creatorId = creatorId,
            creatorLabel = child.child("uploaderLabel").getValue(String::class.java).orEmpty().ifBlank { creatorId.take(8) },
            thumbnailUrl = "",
            votes = child.child("votes").getValue(Int::class.java) ?: 0,
            uploadedAt = child.child("uploadedAt").getValue(Long::class.java) ?: 0L,
        )
    }

    private fun wallpaperUploadRef(child: DataSnapshot): CreatorUploadRef? {
        val key = child.key ?: return null
        val url = child.child("thumbnailUrl").getValue(String::class.java)
            ?: child.child("fullUrl").getValue(String::class.java)
            ?: child.child("downloadUrl").getValue(String::class.java)
            ?: ""
        val fullUrl = child.child("fullUrl").getValue(String::class.java)
            ?: child.child("downloadUrl").getValue(String::class.java)
            ?: url
        val creatorId = child.child("uploaderId").getValue(String::class.java).orEmpty()
        val stableKey = Wallpaper(
            id = "cw_$key",
            source = ContentSource.COMMUNITY,
            thumbnailUrl = url,
            fullUrl = fullUrl,
            width = child.child("width").getValue(Int::class.java) ?: 0,
            height = child.child("height").getValue(Int::class.java) ?: 0,
        ).stableKey()
        return CreatorUploadRef(
            id = "cw_$key",
            stableKey = stableKey,
            contentType = "wallpaper",
            title = child.child("name").getValue(String::class.java).orEmpty().ifBlank { "Community wallpaper" },
            creatorId = creatorId,
            creatorLabel = child.child("uploaderLabel").getValue(String::class.java).orEmpty().ifBlank { creatorId.take(8) },
            thumbnailUrl = url,
            votes = child.child("votes").getValue(Int::class.java) ?: 0,
            uploadedAt = child.child("uploadedAt").getValue(Long::class.java) ?: 0L,
        )
    }
}

internal fun filterCreatorUploadsForBlockedUsers(
    uploads: List<CreatorUploadRef>,
    blockedCreatorIds: Set<String>,
): List<CreatorUploadRef> =
    if (blockedCreatorIds.isEmpty()) {
        uploads
    } else {
        uploads.filterNot { com.freevibe.data.model.isCommunityUserBlocked(it.creatorId, it.creatorId, blockedCreatorIds) }
    }

internal fun aggregateCreatorStats(
    uploads: List<CreatorUploadRef>,
    currentUserIds: Set<String> = emptySet(),
    followedCreatorIds: Set<String> = emptySet(),
): List<CreatorStats> =
    uploads.groupBy { it.creatorId }
        .map { (creatorId, creatorUploads) ->
            CreatorStats(
                creatorId = creatorId,
                label = creatorUploads.firstNotNullOfOrNull { it.creatorLabel.takeIf(String::isNotBlank) }
                    ?: creatorId.take(8),
                soundUploads = creatorUploads.count { it.contentType == "sound" },
                wallpaperUploads = creatorUploads.count { it.contentType == "wallpaper" },
                totalVotes = creatorUploads.sumOf { it.votes.coerceAtLeast(0) },
                favoritesCount = 0,
                isFollowed = creatorId in followedCreatorIds && creatorId !in currentUserIds,
            )
        }
        .sortedWith(compareByDescending<CreatorStats> { it.totalVotes }.thenByDescending { it.uploadCount })

private suspend fun VoteRepository.getVoteCountsOnce(ids: List<String>): Map<String, Int> {
    if (ids.isEmpty()) return emptyMap()
    val db = try { FirebaseDatabase.getInstance().reference.child("votes") } catch (_: Exception) { return emptyMap() }
    return ids.distinct().chunked(50).flatMap { chunk ->
        coroutineScope {
            chunk.map { id ->
                async(Dispatchers.IO) {
                    val safeId = sanitizeKey(id)
                    val count = runCatching {
                        db.child(safeId).child("upvotes").get().await().getValue(Int::class.java) ?: 0
                    }.getOrDefault(0)
                    id to count
                }
            }.awaitAll()
        }
    }.toMap()
}

private fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}

private fun CreatorProfileUpdateInput.toPublicProfile(): CreatorPublicProfile =
    CreatorPublicProfile(
        displayName = displayName,
        bio = bio,
        websiteUrl = websiteUrl,
        avatarUrl = avatarUrl,
    )
