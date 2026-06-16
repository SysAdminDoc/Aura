package com.freevibe.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.freevibe.util.rethrowIfCancelled
import android.webkit.MimeTypeMap
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.COMMUNITY_GUIDELINES_REQUIRED_MESSAGE
import com.freevibe.data.model.CommunityUploadDeleteReason
import com.freevibe.data.model.CommunityUploadKind
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.CommunitySoundUploadMetadataInput
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.model.buildCommunityUploadDeleteUpdates
import com.freevibe.data.model.isCommunityUserBlocked
import com.freevibe.data.model.sanitizeCommunityUploadKey
import com.freevibe.data.model.validateCommunityUploadRights
import com.freevibe.service.CommunityIdentityProvider
import com.freevibe.service.MediaFamily
import com.freevibe.service.SourceMetrics
import com.freevibe.service.copyStreamCapped
import com.freevibe.service.requireSniffedMediaFile
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val UPLOAD_NAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_\\- ]")
private val UPLOAD_TAG_SANITIZE_REGEX = Regex("[^a-z0-9_\\- ]")
private val WHITESPACE_REGEX = Regex("\\s+")
private val STORAGE_SEGMENT_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")
private val ALLOWED_UPLOAD_AUDIO_MIMES = setOf(
    "audio/mpeg",
    "audio/mp3",
    "audio/wav",
    "audio/x-wav",
    "audio/ogg",
    "audio/flac",
    "audio/aac",
    "audio/mp4",
    "audio/x-m4a",
    "audio/m4a",
)
private val ALLOWED_UPLOAD_CATEGORIES = setOf("ringtone", "notification", "alarm")
private const val MAX_AUDIO_UPLOAD_BYTES = 20L * 1024L * 1024L
private const val MAX_UPLOAD_TAGS = 8
private const val MAX_UPLOAD_TAG_LENGTH = 24
private const val SOURCE_COMMUNITY = "community"

@Singleton
class UploadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityProvider: CommunityIdentityProvider,
    private val prefs: PreferencesManager,
    private val sourceMetrics: SourceMetrics,
    private val communityBlockRepo: CommunityBlockRepository,
    private val callableClient: CommunityCallableClient,
) {
    private data class UploadFileInfo(
        val baseName: String,
        val originalFileName: String,
        val extension: String,
        val mimeType: String,
    )

    private val storage by lazy {
        try { FirebaseStorage.getInstance() } catch (_: Exception) { null }
    }
    private val database by lazy {
        try { FirebaseDatabase.getInstance() } catch (_: Exception) { null }
    }
    private val uploadsRef by lazy { database?.reference?.child("community_sounds") }

    /**
     * Upload an audio file to Firebase Storage and create metadata in RTDB.
     * Returns the download URL on success.
     */
    suspend fun uploadSound(
        localUri: Uri,
        name: String,
        category: String, // ringtone, notification, alarm
        tags: List<String>,
        rights: CommunityUploadRights,
        onProgress: (Float) -> Unit = {},
    ): Result<String> = try {
        if (!isCommunityProviderEnabled()) {
            sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
            throw IllegalStateException(communityDisabledMessage())
        }
        val storageInstance = storage ?: throw IllegalStateException("Firebase Storage not available")

        // Validate name length
        val sanitizedName = name.trim().take(100)
        if (sanitizedName.isBlank()) {
            throw IllegalArgumentException("Sound name cannot be empty")
        }
        val validatedRights = validateCommunityUploadRights(
            license = rights.license,
            rightsAttested = rights.rightsAttested,
            sourceUrl = rights.sourceUrl,
        )

        val normalizedCategory = normalizeUploadCategory(category)
        val normalizedTags = sanitizeUploadTags(tags)
        val uploadInfo = resolveUploadFileInfo(localUri, sanitizedName)
        val sniffedAudio = sniffAudioUpload(localUri)
        val normalizedMimeType = sniffedAudio.mimeType.lowercase(java.util.Locale.ROOT)
        if (!isSupportedAudioUploadMime(normalizedMimeType)) {
            throw IllegalArgumentException("Unsupported audio format")
        }

        val fileSize = resolveUploadSize(localUri)
        validateUploadSize(fileSize)
        ensureReadableUpload(localUri)

        val timestamp = System.currentTimeMillis()
        val uploaderId = identityProvider.ensureSignedIn()
        val uploaderLabel = identityProvider.currentUploaderLabel()
        val storagePath = "sounds/${sanitizeUploadStorageSegment(uploaderId)}/${timestamp}_${uploadInfo.baseName}.${sniffedAudio.extension}"
        val storageRef = storageInstance.reference.child(storagePath)
        var metadataSaved = false

        try {
            // Upload file
            val uploadTask = storageRef.putFile(localUri)
            uploadTask.addOnProgressListener { snapshot ->
                val totalByteCount = snapshot.totalByteCount.takeIf { it > 0 } ?: 0L
                val progress = if (totalByteCount > 0) {
                    snapshot.bytesTransferred.toFloat() / totalByteCount
                } else {
                    0f
                }
                onProgress(progress.coerceIn(0f, 1f))
            }
            uploadTask.await()

            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()

            finalizeSoundUploadWithCallable(
                name = sanitizedName,
                category = normalizedCategory,
                tags = normalizedTags,
                downloadUrl = downloadUrl,
                storagePath = storagePath,
                fileType = normalizedMimeType,
                originalFileName = uploadInfo.originalFileName,
                uploaderLabel = uploaderLabel,
                rights = validatedRights,
            )
            metadataSaved = true

            Result.success(downloadUrl)
        } finally {
            if (!metadataSaved) {
                runCatching { storageRef.delete().await() }
            }
        }
    } catch (e: Exception) {
        e.rethrowIfCancelled()
        Result.failure(e)
    }

    suspend fun deleteSoundUpload(uploadId: String): Result<Unit> = try {
        val storageInstance = storage ?: throw IllegalStateException("Firebase Storage not available")
        val uploadsRefInstance = uploadsRef ?: throw IllegalStateException("Firebase Database not available")
        val databaseRoot = database?.reference ?: throw IllegalStateException("Firebase Database not available")
        val ownerUid = identityProvider.ensureSignedIn()
        val safeUploadId = sanitizeCommunityUploadKey(uploadId)
        require(safeUploadId.isNotBlank()) { "Sound upload ID is required" }

        val snapshot = uploadsRefInstance.child(safeUploadId).get().await()
        if (snapshot.exists()) {
            val uploaderUid = snapshot.child("uploaderUid").getValue(String::class.java)
                ?: snapshot.child("uploaderId").getValue(String::class.java)
                ?: ""
            require(uploaderUid == ownerUid) { "Only the uploader can delete this sound" }
            val storagePath = snapshot.child("storagePath").getValue(String::class.java).orEmpty()
            require(storagePath.isNotBlank()) { "Sound upload is missing a deletion handle" }

            storageInstance.deleteCommunityStoragePathIfPresent(storagePath)
            val deletedAt = System.currentTimeMillis()
            databaseRoot.updateChildren(
                buildCommunityUploadDeleteUpdates(
                    kind = CommunityUploadKind.SOUND,
                    ownerUid = ownerUid,
                    uploadId = safeUploadId,
                    storagePath = storagePath,
                    deletedByUid = ownerUid,
                    deletedAt = deletedAt,
                    reason = CommunityUploadDeleteReason.OWNER_DELETE,
                ),
            ).await()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        e.rethrowIfCancelled()
        Result.failure(e)
    }

    suspend fun canDeleteSoundUpload(uploadId: String): Boolean {
        return try {
            val uploadsRefInstance = uploadsRef ?: return false
            val ownerUid = identityProvider.ensureSignedIn()
            val safeUploadId = sanitizeCommunityUploadKey(uploadId)
            if (safeUploadId.isBlank()) return false

            val snapshot = uploadsRefInstance.child(safeUploadId).get().await()
            if (!snapshot.exists()) return false
            val uploaderUid = snapshot.child("uploaderUid").getValue(String::class.java)
                ?: snapshot.child("uploaderId").getValue(String::class.java)
                ?: ""
            val storagePath = snapshot.child("storagePath").getValue(String::class.java).orEmpty()
            uploaderUid == ownerUid && storagePath.isNotBlank()
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            false
        }
    }

    /**
     * Get community-uploaded sounds, sorted by votes descending (client-side).
     */
    fun getCommunityUploads(category: String? = null, limit: Int = 30): Flow<List<Sound>> = callbackFlow {
        if (!isCommunityProviderEnabled()) {
            sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val uploadsRefInstance = uploadsRef
        if (uploadsRefInstance == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val ref = if (category != null) {
            uploadsRefInstance.orderByChild("category").equalTo(category).limitToLast(limit)
        } else {
            uploadsRefInstance.limitToLast(limit)
        }
        val blockedUploaderIds = mutableSetOf<String>()
        var lastSnapshot: DataSnapshot? = null

        fun emitSounds(snapshot: DataSnapshot) {
            val blocked = blockedUploaderIds.toSet()
            val votesByKey = snapshot.children.associate { child ->
                (child.key ?: "") to (child.child("votes").getValue(Int::class.java) ?: 0)
            }
            val sounds = snapshot.children.mapNotNull { child ->
                val key = child.key ?: return@mapNotNull null
                val votes = child.child("votes").getValue(Int::class.java) ?: 0
                if (!shouldDisplayCommunityUpload(votes)) return@mapNotNull null
                val nameVal = child.child("name").getValue(String::class.java) ?: return@mapNotNull null
                val downloadUrl = child.child("downloadUrl").getValue(String::class.java) ?: return@mapNotNull null
                val cat = child.child("category").getValue(String::class.java) ?: ""
                val uploaderUid = child.child("uploaderUid").getValue(String::class.java).orEmpty()
                val uploaderId = child.child("uploaderId").getValue(String::class.java).orEmpty()
                if (isCommunityUserBlocked(uploaderUid, uploaderId, blocked)) return@mapNotNull null
                val uploaderLabel = child.child("uploaderLabel").getValue(String::class.java) ?: ""
                val license = child.child("license").getValue(String::class.java)?.ifBlank { null } ?: "User Upload"
                val sourceUrl = child.child("sourceUrl").getValue(String::class.java).orEmpty()
                val tags = child.child("tags").children.mapNotNull { it.getValue(String::class.java) }
                val fileType = child.child("fileType").getValue(String::class.java) ?: ""

                Sound(
                    id = "cu_$key",
                    source = ContentSource.COMMUNITY,
                    name = nameVal,
                    description = cat,
                    previewUrl = downloadUrl,
                    downloadUrl = downloadUrl,
                    duration = 0.0,
                    fileType = fileType,
                    tags = tags,
                    license = license,
                    uploaderName = uploaderLabel.ifBlank { uploaderUid.ifBlank { uploaderId }.take(8) },
                    sourcePageUrl = sourceUrl,
                    communityUploaderId = uploaderUid.ifBlank { uploaderId },
                )
            }.sortedByDescending { sound ->
                votesByKey[sound.id.removePrefix("cu_")] ?: 0
            }.take(limit)

            trySend(sounds)
        }

        val blockJob = launch {
            communityBlockRepo.blockedUserIds().collect { blockedIds ->
                blockedUploaderIds.clear()
                blockedUploaderIds.addAll(blockedIds)
                lastSnapshot?.let(::emitSounds)
            }
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lastSnapshot = snapshot
                emitSounds(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose {
            blockJob.cancel()
            ref.removeEventListener(listener)
        }
    }

    private suspend fun isCommunityProviderEnabled(): Boolean =
        prefs.communityProviderEnabled.first() && prefs.communityGuidelinesAccepted.first()

    private suspend fun communityDisabledMessage(): String =
        if (!prefs.communityProviderEnabled.first()) {
            "Community source is disabled in Settings"
        } else {
            COMMUNITY_GUIDELINES_REQUIRED_MESSAGE
        }

    private suspend fun finalizeSoundUploadWithCallable(
        name: String,
        category: String,
        tags: List<String>,
        downloadUrl: String,
        storagePath: String,
        fileType: String,
        originalFileName: String,
        uploaderLabel: String,
        rights: CommunityUploadRights,
    ) {
        if (identityProvider.currentFirebaseUid().isNullOrBlank()) {
            throw IllegalStateException("Community upload service requires Firebase Auth")
        }
        try {
            val result = callableClient.finalizeCommunitySoundUpload(
                CommunitySoundUploadMetadataInput(
                    name = name,
                    category = category,
                    tags = tags,
                    downloadUrl = downloadUrl,
                    storagePath = storagePath,
                    fileType = fileType,
                    originalFileName = originalFileName,
                    uploaderLabel = uploaderLabel,
                    license = rights.license,
                    rightsAttested = rights.rightsAttested,
                    sourceUrl = rights.sourceUrl,
                ),
            )
            when {
                result.status.equals("accepted", ignoreCase = true) -> Unit
                result.status.equals("duplicate", ignoreCase = true) -> Unit
                else -> throw IllegalStateException("Unexpected sound upload status: ${result.status}")
            }
        } catch (e: CommunityCallableException) {
            throw IllegalStateException("Community upload service is unavailable", e)
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            throw e
        }
    }

    private fun resolveUploadFileInfo(localUri: Uri, fallbackName: String): UploadFileInfo {
        val resolver = context.contentResolver
        val originalFileName = resolver.query(localUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
            ?: (localUri.lastPathSegment?.substringAfterLast('/') ?: fallbackName)

        val inferredExtension = originalFileName.substringAfterLast('.', "")
            .lowercase(java.util.Locale.ROOT)
            .takeIf { it.isNotBlank() }
        val mimeType = resolver.getType(localUri)
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(java.util.Locale.ROOT)
            ?: inferredExtension
                ?.let(MimeTypeMap.getSingleton()::getMimeTypeFromExtension)
                ?.lowercase(java.util.Locale.ROOT)
            ?: ""

        val extension = inferredExtension
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: defaultExtensionForMimeType(mimeType)

        val baseName = originalFileName.substringBeforeLast('.')
            .ifBlank { fallbackName }
            .replace(UPLOAD_NAME_SANITIZE_REGEX, "")
            .take(40)
            .ifBlank { "audio" }

        return UploadFileInfo(
            baseName = baseName,
            originalFileName = originalFileName,
            extension = extension,
            mimeType = mimeType,
        )
    }

    private fun resolveUploadSize(localUri: Uri): Long? {
        val resolver = context.contentResolver
        val descriptorSize = resolver.openAssetFileDescriptor(localUri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it >= 0 } ?: descriptor.parcelFileDescriptor?.statSize?.takeIf { it >= 0 }
        }
        if (descriptorSize != null) return descriptorSize

        return resolver.query(localUri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }

    private fun validateUploadSize(fileSize: Long?) {
        when {
            fileSize == 0L -> throw IllegalArgumentException("Selected audio file is empty")
            fileSize != null && fileSize > MAX_AUDIO_UPLOAD_BYTES -> {
                throw IllegalArgumentException("File too large (max 20MB)")
            }
        }
    }

    private fun ensureReadableUpload(localUri: Uri) {
        val firstByte = context.contentResolver.openInputStream(localUri)?.use { input ->
            input.read()
        } ?: -1
        if (firstByte == -1) {
            throw IllegalArgumentException("Selected audio file is empty or unreadable")
        }
    }

    private fun sniffAudioUpload(localUri: Uri): com.freevibe.service.SniffedMediaType {
        val tempDir = File(context.cacheDir, "upload_probe").apply { mkdirs() }
        val probeFile = File.createTempFile("aura_upload_", ".tmp", tempDir)
        return try {
            context.contentResolver.openInputStream(localUri)?.use { input ->
                probeFile.outputStream().use { output ->
                    copyStreamCapped(input, output, MAX_AUDIO_UPLOAD_BYTES)
                }
            } ?: throw IllegalArgumentException("Selected audio file is empty or unreadable")
            requireSniffedMediaFile(probeFile, MediaFamily.AUDIO, "Selected audio")
        } finally {
            probeFile.delete()
        }
    }

    private fun defaultExtensionForMimeType(mimeType: String): String = when (mimeType.lowercase(java.util.Locale.ROOT)) {
        "audio/ogg" -> "ogg"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/flac" -> "flac"
        "audio/mp4", "audio/aac" -> "m4a"
        else -> "mp3"
    }
}

internal fun normalizeUploadCategory(category: String): String {
    val normalized = category.trim().lowercase(java.util.Locale.ROOT)
    require(normalized in ALLOWED_UPLOAD_CATEGORIES) { "Invalid sound category" }
    return normalized
}

internal fun sanitizeUploadTags(tags: List<String>): List<String> =
    tags.asSequence()
        .map { tag ->
            tag.trim()
                .lowercase(java.util.Locale.ROOT)
                .replace(UPLOAD_TAG_SANITIZE_REGEX, "")
                .replace(WHITESPACE_REGEX, " ")
        }
        .filter { it.length in 2..MAX_UPLOAD_TAG_LENGTH }
        .distinct()
        .take(MAX_UPLOAD_TAGS)
        .toList()

internal fun isSupportedAudioUploadMime(mimeType: String): Boolean =
    mimeType.lowercase(java.util.Locale.ROOT) in ALLOWED_UPLOAD_AUDIO_MIMES

internal fun sanitizeUploadStorageSegment(segment: String): String =
    segment.trim()
        .replace(STORAGE_SEGMENT_SANITIZE_REGEX, "_")
        .trim('_')
        .ifBlank { "user" }

internal fun shouldDisplayCommunityUpload(votes: Int): Boolean = votes >= 0
