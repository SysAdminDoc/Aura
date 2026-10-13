package com.freevibe.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.COMMUNITY_GUIDELINES_REQUIRED_MESSAGE
import com.freevibe.data.model.CommunityUploadDeleteReason
import com.freevibe.data.model.CommunityUploadKind
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.CommunityWallpaperUploadMetadataInput
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.buildCommunityUploadDeleteUpdates
import com.freevibe.data.model.isCommunityUserBlocked
import com.freevibe.data.model.sanitizeCommunityUploadKey
import com.freevibe.data.model.validateCommunityUploadRights
import com.freevibe.service.ColorExtractor
import com.freevibe.service.CommunityIdentityProvider
import com.freevibe.service.MediaIngestionImageFlow
import com.freevibe.service.SourceMetrics
import com.freevibe.service.decodeImageUri
import com.freevibe.service.imageFormatSupportForFlow
import com.freevibe.service.imageMimeTypeFromExtension
import com.freevibe.util.rethrowIfCancelled
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private val WALLPAPER_UPLOAD_NAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_\\- ]")
private val WALLPAPER_UPLOAD_TAG_SANITIZE_REGEX = Regex("[^a-z0-9_\\- ]")
private val WALLPAPER_UPLOAD_WHITESPACE_REGEX = Regex("\\s+")
private val WALLPAPER_UPLOAD_STORAGE_SEGMENT_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")
private val ALLOWED_WALLPAPER_UPLOAD_CATEGORIES = setOf(
    "abstract",
    "amoled",
    "nature",
    "minimal",
    "city",
    "space",
    "other",
)
private const val MAX_WALLPAPER_UPLOAD_BYTES = 4L * 1024L * 1024L
private const val MAX_WALLPAPER_LONG_EDGE = 2560
private const val MAX_WALLPAPER_TAGS = 8
private const val MAX_WALLPAPER_TAG_LENGTH = 24
private const val FALLBACK_PHONE_ASPECT = 9f / 16f
private const val SOURCE_COMMUNITY = "community"

@Singleton
class WallpaperUploadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityProvider: CommunityIdentityProvider,
    private val colorExtractor: ColorExtractor,
    private val prefs: PreferencesManager,
    private val sourceMetrics: SourceMetrics,
    private val communityBlockRepo: CommunityBlockRepository,
    private val callableClient: CommunityCallableClient,
) {
    private data class WallpaperUploadInfo(
        val baseName: String,
        val originalFileName: String,
        val mimeType: String,
    )

    private data class PreparedWallpaper(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val colors: List<String>,
    )

    private val storage by lazy {
        try { FirebaseStorage.getInstance() } catch (_: Exception) { null }
    }
    private val database by lazy {
        try { FirebaseDatabase.getInstance() } catch (_: Exception) { null }
    }
    private val wallpapersRef by lazy { database?.reference?.child("community_wallpapers") }

    suspend fun uploadWallpaper(
        localUri: Uri,
        name: String,
        category: String,
        tags: List<String>,
        rights: CommunityUploadRights,
        isAiGenerated: Boolean = false,
        onProgress: (Float) -> Unit = {},
    ): Result<Wallpaper> = withContext(Dispatchers.IO) {
        try {
            if (!isCommunityProviderEnabled()) {
                sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
                throw IllegalStateException(communityDisabledMessage())
            }
            val storageInstance = storage ?: throw IllegalStateException("Firebase Storage not available")
            val sanitizedName = name.trim().take(100)
            if (sanitizedName.isBlank()) throw IllegalArgumentException("Wallpaper name cannot be empty")
            val validatedRights = validateCommunityUploadRights(
                license = rights.license,
                rightsAttested = rights.rightsAttested,
                sourceUrl = rights.sourceUrl,
            )

            val normalizedCategory = normalizeWallpaperUploadCategory(category)
            val normalizedTags = sanitizeWallpaperUploadTags(tags)
            val uploadInfo = resolveUploadInfo(localUri, sanitizedName)
            val imageSupport = imageFormatSupportForFlow(
                MediaIngestionImageFlow.COMMUNITY_WALLPAPER_UPLOAD,
                uploadInfo.mimeType,
            )
            if (!imageSupport.supported) {
                throw IllegalArgumentException(imageSupport.message)
            }

            ensureReadableWallpaper(localUri)
            val prepared = prepareWallpaper(localUri)
            val timestamp = System.currentTimeMillis()
            val uploaderId = identityProvider.ensureSignedIn()
            val uploaderLabel = identityProvider.currentUploaderLabel()
            val storagePath =
                "wallpapers/${sanitizeWallpaperUploadStorageSegment(uploaderId)}/${timestamp}_${uploadInfo.baseName}.jpg"
            val storageRef = storageInstance.reference.child(storagePath)
            var metadataSaved = false

            try {
                val uploadTask = storageRef.putBytes(
                    prepared.bytes,
                    StorageMetadata.Builder().setContentType("image/jpeg").build(),
                )
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
                val downloadUrl = storageRef.downloadUrl.await().toString()
                var uploadKey = ""
                uploadKey = finalizeWallpaperUploadWithCallable(
                    name = sanitizedName,
                    category = normalizedCategory,
                    tags = normalizedTags,
                    colors = prepared.colors,
                    downloadUrl = downloadUrl,
                    storagePath = storagePath,
                    width = prepared.width,
                    height = prepared.height,
                    fileSize = prepared.bytes.size,
                    originalFileName = uploadInfo.originalFileName,
                    uploaderLabel = uploaderLabel,
                    rights = validatedRights,
                    isAiGenerated = isAiGenerated,
                )
                metadataSaved = true

                Result.success(
                    Wallpaper(
                        id = communityWallpaperId(uploadKey),
                        source = ContentSource.COMMUNITY,
                        thumbnailUrl = downloadUrl,
                        fullUrl = downloadUrl,
                        width = prepared.width,
                        height = prepared.height,
                        category = normalizedCategory,
                        tags = normalizedTags,
                        colors = prepared.colors,
                        fileSize = prepared.bytes.size.toLong(),
                        fileType = "image/jpeg",
                        sourcePageUrl = validatedRights.sourceUrl,
                        license = validatedRights.license,
                        uploaderName = uploaderLabel,
                        isAiGenerated = isAiGenerated,
                    ),
                )
            } finally {
                if (!metadataSaved) runCatching { storageRef.delete().await() }
            }
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            Result.failure(e)
        }
    }

    suspend fun deleteWallpaperUpload(uploadId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val storageInstance = storage ?: throw IllegalStateException("Firebase Storage not available")
            val wallpapersRefInstance = wallpapersRef ?: throw IllegalStateException("Firebase Database not available")
            val databaseRoot = database?.reference ?: throw IllegalStateException("Firebase Database not available")
            val ownerUid = identityProvider.ensureSignedIn()
            val safeUploadId = sanitizeCommunityUploadKey(uploadId)
            require(safeUploadId.isNotBlank()) { "Wallpaper upload ID is required" }

            val snapshot = awaitFirebaseRead("Wallpaper upload metadata") {
                wallpapersRefInstance.child(safeUploadId).get().await()
            }
            if (snapshot.exists()) {
                val uploaderUid = snapshot.child("uploaderUid").getValue(String::class.java)
                    ?: snapshot.child("uploaderId").getValue(String::class.java)
                    ?: ""
                require(uploaderUid == ownerUid) { "Only the uploader can delete this wallpaper" }
                val storagePath = snapshot.child("storagePath").getValue(String::class.java).orEmpty()
                require(storagePath.isNotBlank()) { "Wallpaper upload is missing a deletion handle" }

                storageInstance.deleteCommunityStoragePathIfPresent(storagePath)
                val deletedAt = System.currentTimeMillis()
                databaseRoot.updateChildren(
                    buildCommunityUploadDeleteUpdates(
                        kind = CommunityUploadKind.WALLPAPER,
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
    }

    suspend fun canDeleteWallpaperUpload(uploadId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val wallpapersRefInstance = wallpapersRef ?: return@withContext false
            val ownerUid = identityProvider.ensureSignedIn()
            val safeUploadId = sanitizeCommunityUploadKey(uploadId)
            if (safeUploadId.isBlank()) return@withContext false

            val snapshot = awaitFirebaseRead("Wallpaper upload ownership") {
                wallpapersRefInstance.child(safeUploadId).get().await()
            }
            if (!snapshot.exists()) return@withContext false
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

    suspend fun fetchWallpapersByKeys(keys: Set<String>): List<Wallpaper> = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext emptyList()
        val ref = wallpapersRef ?: return@withContext emptyList()
        val blockedUploaderIds = communityBlockRepo.blockedUserIdsOnce()
        coroutineScope {
            keys.map { key ->
                async {
                    try {
                        awaitFirebaseRead("Community wallpaper metadata") {
                            val snapshot = ref.child(key).get().await()
                            if (snapshot.exists()) snapshotToWallpaper(snapshot, blockedUploaderIds) else null
                        }
                    } catch (e: Exception) {
                        e.rethrowIfCancelled()
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun getCommunityWallpapers(limit: Int = 60): SearchResult<Wallpaper> = withContext(Dispatchers.IO) {
        if (!isCommunityProviderEnabled()) {
            sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
            return@withContext SearchResult(emptyList(), 0, 1, false)
        }
        val ref = wallpapersRef ?: return@withContext SearchResult(emptyList(), 0, 1, false)
        val blockedUploaderIds = communityBlockRepo.blockedUserIdsOnce()
        val snapshot = awaitFirebaseRead("Community wallpapers") {
            ref.orderByChild("uploadedAt").limitToLast(limit).get().await()
        }
        val wallpapers = snapshot.children.mapNotNull { child -> snapshotToWallpaper(child, blockedUploaderIds) }
            .sortedWith(
                compareByDescending<Wallpaper> { wallpaper ->
                    snapshot.child(wallpaper.id.removePrefix("cw_")).child("votes").getValue(Int::class.java) ?: 0
                }.thenByDescending { wallpaper ->
                    snapshot.child(wallpaper.id.removePrefix("cw_")).child("uploadedAt").getValue(Long::class.java) ?: 0L
                },
            )
        SearchResult(
            items = wallpapers,
            totalCount = wallpapers.size,
            currentPage = 1,
            hasMore = false,
        )
    }

    private suspend fun isCommunityProviderEnabled(): Boolean =
        prefs.communityProviderEnabled.first() && prefs.communityGuidelinesAccepted.first()

    private suspend fun communityDisabledMessage(): String =
        if (!prefs.communityProviderEnabled.first()) {
            "Community source is disabled in Settings"
        } else {
            COMMUNITY_GUIDELINES_REQUIRED_MESSAGE
        }

    private suspend fun finalizeWallpaperUploadWithCallable(
        name: String,
        category: String,
        tags: List<String>,
        colors: List<String>,
        downloadUrl: String,
        storagePath: String,
        width: Int,
        height: Int,
        fileSize: Int,
        originalFileName: String,
        uploaderLabel: String,
        rights: CommunityUploadRights,
        isAiGenerated: Boolean,
    ): String {
        if (identityProvider.currentFirebaseUid().isNullOrBlank()) {
            throw IllegalStateException("Community upload service requires Firebase Auth")
        }
        try {
            val result = callableClient.finalizeCommunityWallpaperUpload(
                CommunityWallpaperUploadMetadataInput(
                    name = name,
                    category = category,
                    tags = tags,
                    colors = colors,
                    thumbnailUrl = downloadUrl,
                    fullUrl = downloadUrl,
                    downloadUrl = downloadUrl,
                    storagePath = storagePath,
                    width = width,
                    height = height,
                    fileSize = fileSize,
                    fileType = "image/jpeg",
                    originalFileName = originalFileName,
                    uploaderLabel = uploaderLabel,
                    license = rights.license,
                    rightsAttested = rights.rightsAttested,
                    sourceUrl = rights.sourceUrl,
                    isAiGenerated = isAiGenerated,
                ),
            )
            return when {
                result.status.equals("accepted", ignoreCase = true) -> result.targetId()
                result.status.equals("duplicate", ignoreCase = true) -> result.targetId()
                else -> throw IllegalStateException("Unexpected wallpaper upload status: ${result.status}")
            }
        } catch (e: CommunityCallableException) {
            throw IllegalStateException("Community upload service is unavailable", e)
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            throw e
        }
    }

    private fun snapshotToWallpaper(child: DataSnapshot, blockedUploaderIds: Set<String> = emptySet()): Wallpaper? {
        val key = child.key ?: return null
        val votes = child.child("votes").getValue(Int::class.java) ?: 0
        if (!shouldDisplayCommunityWallpaper(votes)) return null
        val fullUrl = child.child("fullUrl").getValue(String::class.java)
            ?: child.child("downloadUrl").getValue(String::class.java)
            ?: return null
        val thumbnailUrl = child.child("thumbnailUrl").getValue(String::class.java) ?: fullUrl
        val uploaderUid = child.child("uploaderUid").getValue(String::class.java).orEmpty()
        val uploaderId = child.child("uploaderId").getValue(String::class.java).orEmpty()
        if (isCommunityUserBlocked(uploaderUid, uploaderId, blockedUploaderIds)) return null
        val uploaderLabel = child.child("uploaderLabel").getValue(String::class.java).orEmpty()
        val license = child.child("license").getValue(String::class.java).orEmpty()
        val sourceUrl = child.child("sourceUrl").getValue(String::class.java).orEmpty()
        return Wallpaper(
            id = communityWallpaperId(key),
            source = ContentSource.COMMUNITY,
            thumbnailUrl = thumbnailUrl,
            fullUrl = fullUrl,
            width = child.child("width").getValue(Int::class.java) ?: 0,
            height = child.child("height").getValue(Int::class.java) ?: 0,
            category = child.child("category").getValue(String::class.java).orEmpty(),
            tags = child.stringList("tags"),
            colors = child.stringList("colors"),
            fileSize = child.child("fileSize").getValue(Long::class.java) ?: 0L,
            fileType = child.child("fileType").getValue(String::class.java).orEmpty(),
            sourcePageUrl = sourceUrl,
            license = license,
            uploaderName = uploaderLabel.ifBlank { uploaderUid.ifBlank { uploaderId }.take(8) },
            communityUploaderId = uploaderUid.ifBlank { uploaderId },
            isAiGenerated = child.child("isAiGenerated").getValue(Boolean::class.java),
        )
    }

    private fun prepareWallpaper(localUri: Uri): PreparedWallpaper {
        val aspect = phoneAspectRatio()
        val cropped = decodeCenterCroppedBitmap(localUri, aspect)
        return try {
            val colors = paletteColorsToHex(colorExtractor.extractFromBitmap(cropped))
            // The server reads the size back from the JPEG header, so declare what was encoded.
            val compressed = compressWallpaperForUpload(cropped)
            PreparedWallpaper(
                bytes = compressed.bytes,
                width = compressed.width,
                height = compressed.height,
                colors = colors,
            )
        } finally {
            cropped.recycle()
        }
    }

    private fun decodeCenterCroppedBitmap(localUri: Uri, targetAspect: Float): Bitmap {
        val decoded = decodeImageUri(context, localUri, maxLongEdge = MAX_WALLPAPER_LONG_EDGE * 2)
            ?: throw IllegalArgumentException("Selected image is unreadable")
        if (decoded.width <= 0 || decoded.height <= 0) {
            decoded.recycle()
            throw IllegalArgumentException("Selected image is not a valid wallpaper")
        }

        val cropBounds = centerCropBounds(decoded.width, decoded.height, targetAspect)
        val cropped = Bitmap.createBitmap(
            decoded,
            cropBounds.left,
            cropBounds.top,
            cropBounds.width(),
            cropBounds.height(),
        )
        if (cropped !== decoded) decoded.recycle()
        return scaleDownLongEdge(cropped, MAX_WALLPAPER_LONG_EDGE)
    }

    private fun scaleDownLongEdge(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxLongEdge) return bitmap
        val scale = maxLongEdge.toFloat() / longEdge.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun resolveUploadInfo(localUri: Uri, fallbackName: String): WallpaperUploadInfo {
        val resolver = context.contentResolver
        val originalFileName = runCatching {
            resolver.query(localUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
            ?: (localUri.lastPathSegment?.substringAfterLast('/') ?: fallbackName)

        val inferredExtension = originalFileName.substringAfterLast('.', "")
            .lowercase(java.util.Locale.ROOT)
            .takeIf { it.isNotBlank() }
        val mimeType = runCatching { resolver.getType(localUri) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(java.util.Locale.ROOT)
            ?: inferredExtension
                ?.let { extension ->
                    imageMimeTypeFromExtension(extension)
                        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                }
                ?.lowercase(java.util.Locale.ROOT)
            ?: ""
        val baseName = originalFileName.substringBeforeLast('.')
            .ifBlank { fallbackName }
            .replace(WALLPAPER_UPLOAD_NAME_SANITIZE_REGEX, "")
            .take(40)
            .ifBlank { "wallpaper" }

        return WallpaperUploadInfo(
            baseName = baseName,
            originalFileName = originalFileName,
            mimeType = mimeType,
        )
    }

    private fun ensureReadableWallpaper(localUri: Uri) {
        val firstByte = context.contentResolver.openInputStream(localUri)?.use { input ->
            input.read()
        } ?: -1
        if (firstByte == -1) throw IllegalArgumentException("Selected image is empty or unreadable")
    }

    private fun phoneAspectRatio(): Float {
        val metrics = context.resources.displayMetrics
        val shortSide = min(metrics.widthPixels, metrics.heightPixels).takeIf { it > 0 } ?: 9
        val longSide = max(metrics.widthPixels, metrics.heightPixels).takeIf { it > 0 } ?: 16
        return (shortSide.toFloat() / longSide.toFloat()).takeIf { it > 0f } ?: FALLBACK_PHONE_ASPECT
    }
}

internal fun normalizeWallpaperUploadCategory(category: String): String {
    val normalized = category.trim().lowercase(java.util.Locale.ROOT)
    require(normalized in ALLOWED_WALLPAPER_UPLOAD_CATEGORIES) { "Invalid wallpaper category" }
    return normalized
}

internal fun sanitizeWallpaperUploadTags(tags: List<String>): List<String> =
    tags.asSequence()
        .map { tag ->
            tag.trim()
                .lowercase(java.util.Locale.ROOT)
                .replace(WALLPAPER_UPLOAD_TAG_SANITIZE_REGEX, "")
                .replace(WALLPAPER_UPLOAD_WHITESPACE_REGEX, " ")
        }
        .filter { it.length in 2..MAX_WALLPAPER_TAG_LENGTH }
        .distinct()
        .take(MAX_WALLPAPER_TAGS)
        .toList()

internal fun isSupportedWallpaperUploadMime(mimeType: String): Boolean =
    isSupportedWallpaperUploadMime(mimeType, Build.VERSION.SDK_INT)

internal fun isSupportedWallpaperUploadMime(mimeType: String, sdkInt: Int): Boolean =
    imageFormatSupportForFlow(
        MediaIngestionImageFlow.COMMUNITY_WALLPAPER_UPLOAD,
        mimeType,
        sdkInt,
    ).supported

internal fun unsupportedWallpaperUploadFormatMessage(mimeType: String, sdkInt: Int = Build.VERSION.SDK_INT): String =
    imageFormatSupportForFlow(
        MediaIngestionImageFlow.COMMUNITY_WALLPAPER_UPLOAD,
        mimeType,
        sdkInt,
    ).message

internal fun sanitizeWallpaperUploadStorageSegment(segment: String): String =
    segment.trim()
        .replace(WALLPAPER_UPLOAD_STORAGE_SEGMENT_SANITIZE_REGEX, "_")
        .trim('_')
        .ifBlank { "user" }

internal fun shouldDisplayCommunityWallpaper(votes: Int): Boolean = votes >= 0

internal data class WallpaperCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}

internal fun centerCropBounds(sourceWidth: Int, sourceHeight: Int, targetAspect: Float): WallpaperCropBounds {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source dimensions must be positive" }
    require(targetAspect > 0f) { "Target aspect must be positive" }
    val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
    return if (sourceAspect > targetAspect) {
        val cropWidth = (sourceHeight * targetAspect).roundToInt().coerceIn(1, sourceWidth)
        val left = ((sourceWidth - cropWidth) / 2f).roundToInt().coerceIn(0, sourceWidth - cropWidth)
        WallpaperCropBounds(left, 0, left + cropWidth, sourceHeight)
    } else {
        val cropHeight = (sourceWidth / targetAspect).roundToInt().coerceIn(1, sourceHeight)
        val top = ((sourceHeight - cropHeight) / 2f).roundToInt().coerceIn(0, sourceHeight - cropHeight)
        WallpaperCropBounds(0, top, sourceWidth, top + cropHeight)
    }
}

internal fun paletteColorsToHex(palette: ColorExtractor.WallpaperPalette): List<String> =
    listOf(
        palette.bestAccentColor,
        palette.dominantColor,
        palette.vibrantColor,
        palette.mutedColor,
        palette.vibrantDark,
        palette.mutedDark,
    )
        .filter { it != 0 }
        .map { "#%06X".format(0xFFFFFF and it) }
        .distinct()
        .take(6)

/** JPEG bytes and the pixel size they were encoded at, which shrinks when the first tries are too big. */
internal class CompressedWallpaper(val bytes: ByteArray, val width: Int, val height: Int)

internal fun compressWallpaperForUpload(
    bitmap: Bitmap,
    maxBytes: Long = MAX_WALLPAPER_UPLOAD_BYTES,
): CompressedWallpaper {
    var working = bitmap
    var ownsWorking = false
    var quality = 92
    var attempts = 0
    val output = ByteArrayOutputStream()

    while (attempts < 16) {
        output.reset()
        working.compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (output.size().toLong() <= maxBytes) {
            val compressed = CompressedWallpaper(output.toByteArray(), working.width, working.height)
            if (ownsWorking) working.recycle()
            return compressed
        }

        if (quality > 72) {
            quality -= 8
        } else {
            val nextWidth = (working.width * 0.86f).roundToInt().coerceAtLeast(720)
            val nextHeight = (working.height * 0.86f).roundToInt().coerceAtLeast(1280)
            if (nextWidth >= working.width || nextHeight >= working.height) break
            val scaled = Bitmap.createScaledBitmap(working, nextWidth, nextHeight, true)
            if (ownsWorking) working.recycle()
            working = scaled
            ownsWorking = true
            quality = 84
        }
        attempts++
    }

    if (ownsWorking) working.recycle()
    throw IllegalArgumentException("Wallpaper could not be compressed under 4MB")
}

internal fun communityWallpaperId(key: String): String = "cw_$key"

private fun DataSnapshot.stringList(childName: String): List<String> =
    child(childName).children.mapNotNull { it.getValue(String::class.java) }
