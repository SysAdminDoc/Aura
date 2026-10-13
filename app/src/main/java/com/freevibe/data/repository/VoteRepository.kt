package com.freevibe.data.repository

import android.content.Context
import android.util.Log
import com.freevibe.data.local.PreferencesManager
import com.freevibe.service.CommunityIdentityProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private val FIREBASE_KEY_REGEX = Regex("[.#$\\[\\]/]")

private const val HIDDEN_LABELS_KEY = "hidden_labels"

internal fun sanitizeVoteKey(id: String): String =
    id.replace(FIREBASE_KEY_REGEX, "_")

/** Public per-item counts. Rules allow single-row reads and the [TOP_VOTED_MAX_LIMIT] leaderboard query. */
internal const val VOTE_COUNTS_PATH = "vote_counts"

/** Private `{uid}/{contentId}` markers. Rules let only the owning account read its own subtree. */
internal const val VOTE_MARKERS_PATH = "vote_markers"

/** Pre-migration tallies at `/votes/{contentId}/upvotes`. Read only until `/vote_counts` has the row. */
internal const val LEGACY_VOTES_PATH = "votes"

/** Mirrors the `limitToLast <= 200` bound in database.rules.json for `/vote_counts`. */
internal const val TOP_VOTED_MAX_LIMIT = 200

/** Leaderboard rows from an ascending `orderByChild("upvotes")` read: positive counts, highest first. */
internal fun topVotedRows(rows: List<Pair<String, Int>>, limit: Int): List<Pair<String, Int>> =
    rows.filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(limit.coerceIn(0, TOP_VOTED_MAX_LIMIT))

/** Reads each distinct id once; a null row or a failed read is dropped rather than counted as zero. */
internal suspend fun collectVoteCounts(
    contentIds: List<String>,
    readUpvotes: suspend (String) -> Int?,
): Map<String, Int> = coroutineScope {
    contentIds.distinct().map { id ->
        async {
            val upvotes = try {
                readUpvotes(id)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            upvotes?.let { id to it }
        }
    }.awaitAll().filterNotNull().toMap()
}

/**
 * A backend that hasn't been migrated has no `/vote_counts` rows yet, only the legacy tallies the
 * seeding job copies from. The public count wins whenever it exists.
 */
internal fun preferredUpvotes(current: Int?, legacy: Int?): Int = current ?: legacy ?: 0

/**
 * The legacy read is refused once the private vote rules are deployed. That failure reaches
 * [collectVoteCounts], which leaves the id out.
 */
internal suspend fun upvotesWithLegacyFallback(
    readCurrent: suspend () -> Int?,
    readLegacy: suspend () -> Int?,
): Int? = readCurrent() ?: readLegacy()

/** The legacy leaderboard is read only while the public one is empty. */
internal suspend fun topVotedWithLegacyFallback(
    limit: Int,
    readCurrent: suspend () -> List<Pair<String, Int>>,
    readLegacy: suspend () -> List<Pair<String, Int>>,
): List<Pair<String, Int>> {
    val current = topVotedRows(readCurrent(), limit)
    if (current.isNotEmpty()) return current
    return try {
        topVotedRows(readLegacy(), limit)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        emptyList()
    }
}

/** Live public and legacy counts for one item. Reports nothing until both listeners have answered. */
internal class UpvoteTally {
    private var current: Int? = null
    private var legacy: Int? = null
    private var currentAnswered = false
    private var currentFailed = false
    private var legacyAnswered = false

    fun onCurrent(value: Int?): Int? {
        current = value
        currentAnswered = true
        currentFailed = false
        return settled()
    }

    /**
     * The public count couldn't be read (rules refused it or the socket dropped). Only a legacy
     * tally can stand in; without one nothing is reported, because zero would read as a real count.
     */
    fun onCurrentFailed(): Int? {
        current = null
        currentAnswered = true
        currentFailed = true
        return settled()
    }

    fun onLegacy(value: Int?): Int? {
        legacy = value
        legacyAnswered = true
        return settled()
    }

    private fun settled(): Int? = when {
        !currentAnswered || !legacyAnswered -> null
        currentFailed -> legacy
        else -> preferredUpvotes(current, legacy)
    }
}

/**
 * Pure-JVM admin-precedence rule. Tested by [com.freevibe.data.repository.AdminPrecedenceTest].
 * Roadmap N-2: server-side Custom Claim is always authoritative; legacy device-hash and
 * UID allowlists are migration fallbacks only.
 */
internal fun computeIsAdmin(
    adminFromClaims: Boolean,
    deviceIdHash: String,
    currentUserId: String,
    adminDeviceIdHashes: Set<String>,
    adminUserIds: Set<String>,
): Boolean =
    adminFromClaims ||
        deviceIdHash in adminDeviceIdHashes ||
        currentUserId in adminUserIds

internal fun matchesHiddenIds(hiddenIds: Set<String>, vararg candidateIds: String?): Boolean =
    candidateIds.asSequence()
        .filterNotNull()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .any { candidate ->
            candidate in hiddenIds || sanitizeVoteKey(candidate) in hiddenIds
        }

private fun expandHiddenIds(ids: Set<String>): Set<String> = buildSet(ids.size * 2) {
    ids.forEach { id ->
        val normalized = id.trim()
        if (normalized.isNotEmpty()) {
            add(normalized)
            add(sanitizeVoteKey(normalized))
        }
    }
}

/**
 * Community voting + admin moderation via Firebase Realtime Database.
 *
 * Firebase structure:
 *   /vote_counts/{contentId}/upvotes = Int           (public tally, written only by recordCommunityVote)
 *   /vote_markers/{uid}/{contentId} = true           (private; readable only by that account)
 *   /moderation/{contentId} = true                   (admin global hide — removes for ALL users)
 *
 * The older /votes and /voters trees carried voter UIDs in public nodes and are admin-only now.
 * Until the backend is migrated, counts fall back to `/votes/{contentId}/upvotes` for rows
 * `/vote_counts` doesn't have yet.
 *
 * Regular downvote = local-only hide (SharedPreferences).
 * Admin downvote = global hide via /moderation (visible to no one).
 */
@Singleton
class VoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityProvider: CommunityIdentityProvider,
    private val callableClient: CommunityCallableClient,
    private val prefs: PreferencesManager,
) {
    private val db by lazy {
        try { FirebaseDatabase.getInstance().reference } catch (_: Exception) { null }
    }
    private val voteCountsRef get() = db?.child(VOTE_COUNTS_PATH)
    private val voteMarkersRef get() = db?.child(VOTE_MARKERS_PATH)
    private val legacyVotesRef get() = db?.child(LEGACY_VOTES_PATH)
    private val moderationRef get() = db?.child("moderation")

    /**
     * Admin device IDs stored as SHA-256 hashes so plaintext IDs aren't in the APK.
     *
     * Roadmap N-2: server-side `admin: true` Firebase Custom Claim is the canonical
     * source of truth — checked first by [refreshAdminFromClaims] and cached in
     * [_adminFromClaims]. This hash list remains as a one-cycle migration fallback
     * so existing admin devices keep working until the matching ID token refreshes
     * with the claim attached. Remove the hash list and `adminUserIds` once every
     * admin has rotated through a Custom-Claim-bearing ID token (typical: 1 hour
     * after backend deploys the claim).
     */
    private val adminDeviceIdHashes = setOf(
        "70221777b62eabc52f5d0625fe7fd27f6a96f1a314231f0a33e7db98cb7da49b",
        "8d5c02d2bc8767d04eb1cdc9a662a16a735fb130374d6c98b189ff787b78f80c",
    )

    /** Admin Firebase UIDs can be added here as a legacy fallback alongside custom claims. */
    private val adminUserIds = emptySet<String>()

    private val auth: FirebaseAuth? by lazy {
        try { FirebaseAuth.getInstance() } catch (_: Exception) { null }
    }

    /** Cached Custom Claim state from the user's most-recently-refreshed ID token. */
    private val _adminFromClaims = MutableStateFlow(false)

    private fun sha256(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Force a fresh ID token from Firebase Auth and read the `admin` Custom Claim.
     * Call from a coroutine after sign-in, after a known privilege change, or on
     * app startup. The RTDB security rules are the actual enforcement layer; this
     * client check just controls UI affordances (e.g. showing the moderation menu).
     */
    suspend fun refreshAdminFromClaims(): Boolean {
        val token = try {
            // forceRefresh=true asks Firebase Auth to round-trip a fresh token so newly-set
            // server-side claims become visible without waiting for the 1 h token lifetime.
            auth?.currentUser?.getIdToken(true)?.await()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (com.freevibe.BuildConfig.DEBUG) Log.w("VoteRepo", "refreshAdminFromClaims failed: ${e.message}")
            null
        }
        val isAdminClaim = token?.claims?.get("admin") == true
        _adminFromClaims.value = isAdminClaim
        return isAdminClaim
    }

    /**
     * Best-effort admin check. Order of precedence:
     *  1. Cached `admin` Custom Claim from the user's ID token (Firebase Auth, server-side).
     *  2. Legacy SHA-256 device-ID allowlist (one-cycle migration fallback).
     *  3. Legacy `adminUserIds` Firebase UID allowlist.
     *
     * Always pair with RTDB Security Rules — the client check is spoofable; rules are not.
     */
    val isAdmin: Boolean
        get() = computeIsAdmin(
            adminFromClaims = _adminFromClaims.value,
            deviceIdHash = sha256(identityProvider.legacyDeviceId),
            currentUserId = identityProvider.currentUserId(),
            adminDeviceIdHashes = adminDeviceIdHashes,
            adminUserIds = adminUserIds,
        )

    // ── Local hidden IDs (user's personal downvotes) ──

    private val _localHiddenIds = MutableStateFlow<Set<String>>(emptySet())
    private val _hiddenLabels = MutableStateFlow<Map<String, HiddenItemRecord>>(emptyMap())

    init {
        val prefs = context.getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
        _localHiddenIds.value = prefs.getStringSet("hidden_ids", emptySet()) ?: emptySet()
        _hiddenLabels.value = decodeHiddenItemRecords(prefs.getString(HIDDEN_LABELS_KEY, null))
    }

    /** This device's own hides, without the moderation list. These are the ones a user can restore. */
    val localHiddenIds: StateFlow<Set<String>> = _localHiddenIds.asStateFlow()

    /** Local hides with the label saved when each was hidden, newest first. */
    val localHiddenItems: Flow<List<HiddenItem>> = combine(_localHiddenIds, _hiddenLabels, ::hiddenItemsOf)

    // ── Global moderation list (admin-hidden, synced from Firebase) ──

    private val _moderatedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _moderationSyncDegraded = MutableStateFlow(false)

    /** True while the moderation list may be stale because its listener was cancelled. */
    val moderationSyncDegraded: StateFlow<Boolean> = _moderationSyncDegraded.asStateFlow()

    /**
     * Guarded by [moderationLock]. Non-null exactly while a listener is attached, so the
     * consent collector below is idempotent on repeated emissions of the same value.
     */
    private var moderationListener: ValueEventListener? = null
    private val moderationLock = Any()

    /**
     * Singleton-scoped because the moderation listener outlives any one screen. Cancelled
     * only with the process; [detachModerationListener] is what releases the Firebase
     * listener when consent is withdrawn.
     */
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // The moderation listener opens a Realtime Database socket, so it must not attach
        // until the user has actually opted into community features. Both preferences
        // default to false, and this repository is a @Singleton constructed as soon as any
        // screen that injects it opens — attaching from init would put a non-consenting
        // user on the network for the lifetime of the process.
        repositoryScope.launch {
            combine(
                prefs.communityProviderEnabled,
                prefs.communityGuidelinesAccepted,
            ) { providerEnabled, guidelinesAccepted -> providerEnabled && guidelinesAccepted }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) attachModerationListener() else detachModerationListener()
                }
        }
    }

    private fun attachModerationListener() {
        synchronized(moderationLock) {
            if (moderationListener != null) return
            try {
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        onModerationSnapshot(snapshot.children.mapNotNull { it.key }.toSet())
                    }

                    override fun onCancelled(error: DatabaseError) {
                        onModerationCancelled(error.message)
                    }
                }
                val ref = moderationRef ?: return
                ref.addValueEventListener(listener)
                moderationListener = listener
            } catch (e: Exception) {
                Log.w("VoteRepo", "Moderation listener couldn't attach: ${e.javaClass.simpleName}")
            }
        }
    }

    internal fun onModerationSnapshot(ids: Set<String>) {
        _moderatedIds.value = ids
        _moderationSyncDegraded.value = false
    }

    /**
     * Firebase removes a cancelled listener (rules refused the read or the socket dropped), so the
     * moderated list stops updating. It's kept rather than cleared, since clearing would unhide
     * moderated items, and the listener slot is freed so the next consent change attaches again.
     */
    internal fun onModerationCancelled(message: String) {
        Log.w("VoteRepo", "Moderation listener cancelled: $message")
        synchronized(moderationLock) { moderationListener = null }
        _moderationSyncDegraded.value = true
    }

    private fun detachModerationListener() {
        synchronized(moderationLock) {
            val listener = moderationListener
            moderationListener = null
            if (listener != null) {
                try {
                    moderationRef?.removeEventListener(listener)
                } catch (e: Exception) {
                    Log.w("VoteRepo", "Moderation listener detach failed: ${e.javaClass.simpleName}")
                }
            }
            // Moderation hides are a community-service signal; drop them with the socket so a
            // user who opts out does not keep filtering content from a source they left. That
            // includes a list kept after its listener was cancelled.
            _moderatedIds.value = emptySet()
            _moderationSyncDegraded.value = false
        }
    }

    /** Visible for tests: whether a Firebase moderation listener is currently attached. */
    internal fun isModerationListenerAttached(): Boolean =
        synchronized(moderationLock) { moderationListener != null }

    /** Combined hidden IDs: local downvotes + global moderation */
    val hiddenIds: Flow<Set<String>> = combine(_localHiddenIds, _moderatedIds) { local, moderated ->
        expandHiddenIds(local) + expandHiddenIds(moderated)
    }

    // ── Voting ──

    fun getVoteCount(contentId: String): Flow<Int> = callbackFlow {
        if (!isCommunityAccessEnabled()) { trySend(0); awaitClose {}; return@callbackFlow }
        if (voteCountsRef == null) { trySend(0); awaitClose {}; return@callbackFlow }
        val stop = observeUpvotes(sanitizeKey(contentId)) { trySend(it) }
        awaitClose { stop() }
    }

    suspend fun hasVoted(contentId: String, alreadySanitized: Boolean = false): Boolean {
        if (!isCommunityAccessEnabled()) return false
        val safeId = if (alreadySanitized) contentId else sanitizeKey(contentId)
        // Only the signed-in account's own marker is readable. Votes recorded under an older
        // device ID are still caught server-side, where the callable answers "duplicate".
        val uid = identityProvider.currentFirebaseUid()?.let(::sanitizeKey)?.takeIf { it.isNotBlank() }
            ?: return false
        val markersRefInstance = voteMarkersRef ?: return false
        return try {
            awaitFirebaseRead("Community vote status") {
                markersRefInstance.child(uid).child(safeId).get().await().exists()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    suspend fun upvote(contentId: String): Boolean {
        if (!isCommunityAccessEnabled()) return false
        val safeId = sanitizeKey(contentId)
        identityProvider.ensureSignedIn()
        if (hasVoted(safeId, alreadySanitized = true)) return false

        return upvoteWithCallable(contentId)
    }

    private suspend fun upvoteWithCallable(contentId: String): Boolean =
        try {
            callableClient.recordCommunityVote(contentId).status.equals("accepted", ignoreCase = true)
        } catch (e: CommunityCallableException) {
            if (com.freevibe.BuildConfig.DEBUG) {
                Log.w("VoteRepo", "recordCommunityVote failed: ${e.message}")
            }
            false
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (com.freevibe.BuildConfig.DEBUG) {
                Log.w("VoteRepo", "recordCommunityVote failed: ${e.message}")
            }
            false
        }

    // ── Downvote / Hide ──

    /** Regular user: hide locally. Admin: hide globally for everyone. [label] names it in the hidden list. */
    suspend fun downvote(contentId: String, label: HiddenItemLabel? = null) {
        if (!isCommunityAccessEnabled()) return
        if (com.freevibe.BuildConfig.DEBUG) {
            Log.d("VoteRepo", "downvote($contentId) userId=${identityProvider.currentUserId()} isAdmin=$isAdmin")
        }
        if (isAdmin) {
            moderateHide(contentId, label)
        } else {
            hideLocally(contentId, label)
        }
    }

    /** Local-only hide (regular users) */
    fun hideLocally(contentId: String, label: HiddenItemLabel? = null) {
        hideLocally(listOf(HiddenItem(contentId, label, System.currentTimeMillis())))
    }

    /** Hides each item locally, keeping its label and time; the hidden list's Undo uses this to put them back. */
    fun hideLocally(items: Collection<HiddenItem>) {
        if (items.isEmpty()) return
        val ids = _localHiddenIds.updateAndGet { it + items.map(HiddenItem::id) }
        val labels = _hiddenLabels.updateAndGet { current ->
            current + items.mapNotNull { item -> item.label?.let { item.id to HiddenItemRecord(it, item.hiddenAtMs) } }
        }
        persistLocalHides(ids, labels)
    }

    private fun persistLocalHides(ids: Set<String>, labels: Map<String, HiddenItemRecord>) {
        context.getSharedPreferences("aura_votes", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("hidden_ids", ids)
            .putString(HIDDEN_LABELS_KEY, encodeHiddenItemRecords(labels))
            .apply()
    }

    /** Admin: globally hide content for ALL users via Firebase */
    suspend fun moderateHide(contentId: String, label: HiddenItemLabel? = null) {
        if (!isCommunityAccessEnabled()) return
        val moderationRefInstance = moderationRef
        if (moderationRefInstance == null) { hideLocally(contentId, label); return }
        val safeId = sanitizeKey(contentId)
        if (com.freevibe.BuildConfig.DEBUG) Log.d("VoteRepo", "moderateHide: safeId=$safeId path=moderation/$safeId")
        try {
            moderationRefInstance.child(safeId).setValue(true).await()
            if (com.freevibe.BuildConfig.DEBUG) Log.d("VoteRepo", "Admin moderated OK: $contentId")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (com.freevibe.BuildConfig.DEBUG) Log.e("VoteRepo", "Moderation FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            hideLocally(contentId, label)
        }
    }

    /** Admin: remove global moderation (unhide for everyone) */
    suspend fun moderateUnhide(contentId: String) {
        if (!isCommunityAccessEnabled()) return
        val moderationRefInstance = moderationRef ?: return
        val safeId = sanitizeKey(contentId)
        try {
            moderationRefInstance.child(safeId).removeValue().await()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    /** Unhide locally */
    fun unhideLocally(contentId: String) = unhideLocally(listOf(contentId))

    /** Unhides each ID and forgets its label. Works with community features off, so a hide is never stuck. */
    fun unhideLocally(contentIds: Collection<String>) {
        if (contentIds.isEmpty()) return
        val ids = _localHiddenIds.updateAndGet { it - contentIds.toSet() }
        val labels = _hiddenLabels.updateAndGet { it - contentIds.toSet() }
        persistLocalHides(ids, labels)
    }

    /** Reverse a [downvote]: mirrors its admin/local branch so an accidental hide is undoable. */
    suspend fun undoDownvote(contentId: String) {
        if (!isCommunityAccessEnabled()) return
        if (isAdmin) moderateUnhide(contentId) else unhideLocally(contentId)
    }

    fun isHidden(contentId: String): Boolean =
        matchesHiddenIds(_localHiddenIds.value, contentId) ||
            matchesHiddenIds(_moderatedIds.value, contentId)

    // ── Batch ──

    fun getVoteCounts(contentIds: List<String>): Flow<Map<String, Int>> = callbackFlow {
        if (!isCommunityAccessEnabled()) { trySend(emptyMap()); awaitClose {}; return@callbackFlow }
        if (voteCountsRef == null) { trySend(emptyMap()); awaitClose {}; return@callbackFlow }
        val counts = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val stops = contentIds.take(50).map { id ->
            observeUpvotes(sanitizeKey(id)) { upvotes ->
                counts[id] = upvotes
                trySend(counts.toMap())
            }
        }
        awaitClose { stops.forEach { it() } }
    }

    /**
     * Follows an item's public count and its legacy tally together, so a backend that hasn't been
     * migrated still shows counts. Returns the call that removes both listeners.
     */
    private fun observeUpvotes(safeId: String, onCount: (Int) -> Unit): () -> Unit {
        val currentRef = voteCountsRef?.child(safeId)?.child("upvotes") ?: return {}
        val legacyRef = legacyVotesRef?.child(safeId)?.child("upvotes")
        val tally = UpvoteTally()
        val currentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                tally.onCurrent(snapshot.getValue(Int::class.java))?.let(onCount)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w("VoteRepository", "Vote listener cancelled: ${error.message}")
                tally.onCurrentFailed()?.let(onCount)
            }
        }
        // Refused once the private vote rules are deployed, which is expected.
        val legacyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                tally.onLegacy(snapshot.getValue(Int::class.java))?.let(onCount)
            }
            override fun onCancelled(error: DatabaseError) {
                tally.onLegacy(null)?.let(onCount)
            }
        }
        if (legacyRef == null) tally.onLegacy(null)
        currentRef.addValueEventListener(currentListener)
        legacyRef?.addValueEventListener(legacyListener)
        return {
            currentRef.removeEventListener(currentListener)
            legacyRef?.removeEventListener(legacyListener)
        }
    }

    /**
     * One-shot public counts keyed by the ids passed in, read row by row from `/vote_counts`,
     * then from the legacy tally for a row that isn't there yet. An id with neither, or whose
     * read failed, is left out, so callers keep the count they already had instead of showing zero.
     */
    suspend fun getVoteCountsOnce(contentIds: List<String>): Map<String, Int> {
        if (!isCommunityAccessEnabled()) return emptyMap()
        val countsRefInstance = voteCountsRef ?: return emptyMap()
        val legacyRefInstance = legacyVotesRef
        return collectVoteCounts(contentIds) { id ->
            val key = sanitizeKey(id)
            upvotesWithLegacyFallback(
                readCurrent = {
                    awaitFirebaseRead("Community vote counts") {
                        countsRefInstance.child(key).child("upvotes").get().await()
                    }.getValue(Int::class.java)
                },
                readLegacy = {
                    legacyRefInstance?.let { ref ->
                        awaitFirebaseRead("Legacy community vote counts") {
                            ref.child(key).child("upvotes").get().await()
                        }.getValue(Int::class.java)
                    }
                },
            )
        }
    }

    /** Get top upvoted content IDs globally, sorted by vote count descending */
    suspend fun getTopVotedIds(limit: Int = 50): List<Pair<String, Int>> {
        if (!isCommunityAccessEnabled()) return emptyList()
        val countsRefInstance = voteCountsRef ?: return emptyList()
        val queryLimit = limit.coerceIn(1, TOP_VOTED_MAX_LIMIT)
        return try {
            topVotedWithLegacyFallback(
                limit,
                readCurrent = { leaderboardRows(countsRefInstance, queryLimit, "Community vote leaderboard") },
                readLegacy = {
                    legacyVotesRef?.let { leaderboardRows(it, queryLimit, "Legacy community vote leaderboard") }
                        .orEmpty()
                },
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (com.freevibe.BuildConfig.DEBUG) android.util.Log.e("VoteRepo", "getTopVotedIds failed: ${e.message}")
            emptyList()
        }
    }

    // Rules only answer this exact query shape on `/vote_counts`, so a whole-tree read is refused
    // rather than silently scanning every count.
    private suspend fun leaderboardRows(
        ref: DatabaseReference,
        queryLimit: Int,
        label: String,
    ): List<Pair<String, Int>> {
        val snapshot = awaitFirebaseRead(label) {
            ref.orderByChild("upvotes").limitToLast(queryLimit).get().await()
        }
        return snapshot.children.mapNotNull { child ->
            val key = child.key ?: return@mapNotNull null
            key to (child.child("upvotes").getValue(Int::class.java) ?: 0)
        }
    }

    fun sanitizeKey(id: String): String =
        sanitizeVoteKey(id)

    private suspend fun isCommunityAccessEnabled(): Boolean =
        prefs.communityProviderEnabled.first() && prefs.communityGuidelinesAccepted.first()
}
