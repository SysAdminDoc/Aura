package com.freevibe.service

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CommunityIdentitySummary(
    val authLabel: String = "Local identity",
    val identitySuffix: String = "Not created",
    val deletionRequestCode: String = "",
    val hasFirebaseIdentity: Boolean = false,
)

@Singleton
class CommunityIdentityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val auth by lazy {
        try { FirebaseAuth.getInstance() } catch (_: Exception) { null }
    }
    private val prefs by lazy {
        context.getSharedPreferences("aura_community_identity", Context.MODE_PRIVATE)
    }

    @Suppress("HardwareIds")
    val legacyDeviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    private var cachedFallbackId: String? = null

    @Synchronized
    private fun fallbackId(): String =
        cachedFallbackId
            ?: (prefs.getString(KEY_FALLBACK_ID, null)
                ?: UUID.randomUUID().toString().also {
                    prefs.edit().putString(KEY_FALLBACK_ID, it).apply()
                }).also { cachedFallbackId = it }

    fun currentUserId(): String = auth?.currentUser?.uid ?: fallbackId()

    fun currentFirebaseUid(): String? = auth?.currentUser?.uid

    fun currentUploaderLabel(): String =
        auth?.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth?.currentUser?.uid?.take(8)
            ?: "local-${fallbackId().take(6)}"

    fun currentAuthLabel(): String = when {
        auth?.currentUser?.isAnonymous == true -> "Anonymous Firebase identity"
        auth?.currentUser != null -> "Firebase identity"
        else -> "Local identity"
    }

    fun currentIdentitySummary(): CommunityIdentitySummary {
        val firebaseUid = currentFirebaseUid()
        val localFallbackId = prefs.getString(KEY_FALLBACK_ID, null)
        val displayId = firebaseUid ?: localFallbackId
        return CommunityIdentitySummary(
            authLabel = currentAuthLabel(),
            identitySuffix = displayId?.let(::communityIdentitySuffix) ?: "Not created",
            deletionRequestCode = firebaseUid?.let(::communityDeletionRequestCode).orEmpty(),
            hasFirebaseIdentity = !firebaseUid.isNullOrBlank(),
        )
    }

    fun hasGoogleOAuthClient(): Boolean =
        context.resources.getIdentifier("default_web_client_id", "string", context.packageName) != 0

    fun knownIdentityIds(): List<String> =
        listOf(auth?.currentUser?.uid, fallbackId(), legacyDeviceId)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()

    @Synchronized
    fun clearLocalFallbackIdentity(): Boolean {
        val hadStoredFallback = prefs.contains(KEY_FALLBACK_ID)
        cachedFallbackId = null
        prefs.edit().remove(KEY_FALLBACK_ID).apply()
        return hadStoredFallback
    }

    suspend fun ensureSignedIn(): String {
        auth?.currentUser?.uid?.let { return it }

        val signedInUid = try {
            auth?.signInAnonymously()?.await()?.user?.uid
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }

        return signedInUid?.takeIf { it.isNotBlank() } ?: fallbackId()
    }

    companion object {
        private const val KEY_FALLBACK_ID = "fallback_id"
    }
}

internal fun communityIdentitySuffix(identityId: String): String =
    identityId.trim().takeLast(8).ifBlank { "Not created" }

internal fun communityDeletionRequestCode(identityId: String): String {
    val normalized = identityId.trim()
    if (normalized.isBlank()) return ""
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(12)
        .uppercase(Locale.ROOT)
    return "AURA-$digest"
}
