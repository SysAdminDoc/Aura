package com.freevibe.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.freevibe.data.local.CollectionDao
import com.freevibe.data.model.MAX_SHARED_COLLECTION_DOCUMENT_BYTES
import com.freevibe.data.model.MAX_SHARED_COLLECTION_ITEMS
import com.freevibe.data.model.SharedCollectionInput
import com.freevibe.data.repository.CommunityCallableClient
import com.freevibe.data.repository.CommunityCallableException
import com.freevibe.data.repository.awaitFirebaseRead
import com.freevibe.util.rethrowIfCancelled
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperCollectionItemEntity
import com.google.firebase.database.FirebaseDatabase
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collection sharing/import. Exports collection JSON for attachment-based sharing and can
 * publish the same payload to Firebase RTDB for compact `aura://collection/import/{token}`
 * links that fit comfortably in QR codes.
 */
private val FILENAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")
private val SHARE_TOKEN_REGEX = Regex(
    "^[A-Za-z0-9_-]{${LibraryTransferContract.MIN_SHARE_TOKEN_CHARS}," +
        "${LibraryTransferContract.MAX_SHARE_TOKEN_CHARS}}$"
)
private const val CURRENT_VERSION = 1
private const val TAG = "CollectionExporter"
internal const val EXPIRED_COLLECTION_LINK_MESSAGE = "Collection link is expired or unavailable."
internal const val SHARED_COLLECTION_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000

/**
 * Until `publishSharedCollection` is deployed, the link is written straight to the database the way
 * older builds do. Any other backend refusal (quota, size, App Check) still fails the link.
 */
internal suspend fun publishShareWithFallback(
    publishViaBackend: suspend () -> String,
    publishDirectly: suspend () -> String,
): String = try {
    publishViaBackend()
} catch (e: CommunityCallableException) {
    if (!e.isMissingEndpoint()) throw e
    publishDirectly()
}

/** The same fields `publishSharedCollection` stores, so expiry and pruning treat both alike. */
internal fun directSharedCollectionRecord(
    json: String,
    collectionName: String,
    itemCount: Int,
    creatorUid: String,
    nowMillis: Long,
): Map<String, Any> = mapOf(
    "version" to CURRENT_VERSION,
    "payload" to json,
    "collectionName" to collectionName,
    "itemCount" to itemCount,
    "createdAt" to nowMillis,
    "expiresAt" to nowMillis + SHARED_COLLECTION_TTL_MILLIS,
    "createdByUid" to creatorUid,
)

@Singleton
class CollectionExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collectionDao: CollectionDao,
    private val moshi: Moshi,
    private val identityProvider: CommunityIdentityProvider,
    private val callableClient: CommunityCallableClient,
) {
    private val adapter = moshi.adapter(CollectionExportFile::class.java).indent("  ")
    private val database by lazy {
        try { FirebaseDatabase.getInstance().reference } catch (_: Exception) { null }
    }

    suspend fun prepareShareBundle(collectionId: Long, collectionName: String): Result<CollectionShareBundle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = buildExportFile(collectionId, collectionName)
                val json = serialize(file)
                val uri = writeShareFile(collectionId, file.collectionName, json)
                // The file always shares. The link is a bonus that can hit its quota or size limit.
                val shareLink = runCatching { buildShareLink(publishPayload(json, file.collectionName, file.items.size)) }
                    .onFailure {
                        it.rethrowIfCancelled()
                        Log.w(TAG, "Collection link not created; sharing the file only", it)
                    }
                    .getOrNull()
                CollectionShareBundle(
                    uri = uri,
                    collectionName = file.collectionName,
                    shareLink = shareLink,
                    itemCount = file.items.size,
                )
            }.onFailure { it.rethrowIfCancelled() }
        }

    suspend fun prepareShareUri(collectionId: Long, collectionName: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val file = buildExportFile(collectionId, collectionName)
            writeShareFile(collectionId, file.collectionName, serialize(file))
        }.onFailure { it.rethrowIfCancelled() }
    }

    suspend fun publishShareLink(collectionId: Long, collectionName: String): Result<CollectionShareLink> =
        withContext(Dispatchers.IO) {
            runCatching {
            val file = buildExportFile(collectionId, collectionName)
            val token = publishPayload(serialize(file), file.collectionName, file.items.size)
                CollectionShareLink(
                    token = token,
                    link = buildShareLink(token),
                    collectionName = file.collectionName,
                    itemCount = file.items.size,
                )
            }.onFailure { it.rethrowIfCancelled() }
        }

    suspend fun importFromTokenOrLink(input: String): Result<CollectionImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val token = extractCollectionShareToken(input)
                ?: throw IllegalArgumentException("Paste an Aura collection link or share token.")
            val db = database ?: throw IllegalStateException("Firebase Database not available")
            val snapshot = try {
                awaitFirebaseRead("Shared collection") {
                    db.child("shared_collections")
                        .child(token)
                        .child("payload")
                        .get()
                        .await()
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                // The rules refuse reads of a share past its 30 days.
                if (isFirebasePermissionDenied(e)) throw IllegalStateException(EXPIRED_COLLECTION_LINK_MESSAGE, e)
                throw e
            }
            val json = snapshot.getValue(String::class.java)
                ?: throw IllegalStateException(EXPIRED_COLLECTION_LINK_MESSAGE)
            importJson(json)
        }.onFailure { it.rethrowIfCancelled() }
    }

    suspend fun importFromUri(uri: Uri): Result<CollectionImportResult> = withContext(Dispatchers.IO) {
        runCatching { importJson(readTextFromUri(uri)) }
            .onFailure { it.rethrowIfCancelled() }
    }

    suspend fun importFromQrImage(uri: Uri): Result<CollectionImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val link = decodeQrText(uri)
            importFromTokenOrLink(link).getOrThrow()
        }.onFailure { it.rethrowIfCancelled() }
    }

    fun buildShareIntent(bundle: CollectionShareBundle): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, bundle.uri)
            putExtra(Intent.EXTRA_SUBJECT, "Aura collection: ${bundle.collectionName}")
            putExtra(Intent.EXTRA_TEXT, buildCollectionShareText(bundle))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Draws the QR on a background thread and fills the bitmap in one call, so the dialog never waits on it. */
    suspend fun buildQrBitmap(text: String, sizePx: Int = 768): Bitmap = withContext(Dispatchers.Default) {
        val matrix = encodeCollectionQr(text, sizePx)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(qrPixels(matrix), 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }
    }

    private suspend fun buildExportFile(collectionId: Long, collectionName: String): CollectionExportFile {
        val items = collectionDao.getCollectionItems(collectionId).first()
        if (items.isEmpty()) throw IllegalStateException("This collection is empty - add a wallpaper first.")
        requireWithinTransferLimit(
            "Collection export items",
            items.size,
            LibraryTransferContract.MAX_COLLECTION_ITEMS,
        )

        return CollectionExportFile(
            version = CURRENT_VERSION,
            exportedAt = System.currentTimeMillis(),
            collectionName = sanitizeImportedCollectionName(collectionName),
            items = items.map {
                CollectionExportItem(
                    wallpaperId = it.wallpaperId,
                    source = it.source,
                    thumbnailUrl = it.thumbnailUrl,
                    fullUrl = it.fullUrl,
                    width = it.width,
                    height = it.height,
                )
            },
        )
    }

    private fun serialize(file: CollectionExportFile): String {
        val json = adapter.toJson(file)
        requireWithinTransferLimit(
            "Collection document bytes",
            json.toByteArray(Charsets.UTF_8).size,
            LibraryTransferContract.MAX_COLLECTION_DOCUMENT_BYTES,
        )
        return json
    }

    private fun writeShareFile(collectionId: Long, collectionName: String, json: String): Uri {
        ShareOutbox.pruneStaleFiles(context)
        val shareDir = ShareOutbox.directory(context)
        val safeName = collectionName.replace(FILENAME_SANITIZE_REGEX, "_").ifBlank { "collection" }
        val shareFile = File(shareDir, "aura_${safeName}_${collectionId}.json")
        publishAtomicLocalFile(shareFile, json.toByteArray(Charsets.UTF_8))
        return ShareOutbox.uriFor(context, shareFile)
    }

    /** The backend stores the share under a token it picks, with a quota and a 30-day expiry. */
    private suspend fun publishPayload(json: String, collectionName: String, itemCount: Int): String {
        requireShareableAsLink(json, itemCount)
        val creatorUid = identityProvider.ensureSignedIn()
        return publishShareWithFallback(
            publishViaBackend = {
                val result = callableClient.publishSharedCollection(
                    SharedCollectionInput(document = json, collectionName = collectionName),
                )
                result.targetId().takeIf { it.matches(SHARE_TOKEN_REGEX) }
                    ?: throw IllegalStateException("Collection link could not be created.")
            },
            publishDirectly = {
                Log.w(TAG, "publishSharedCollection is not deployed; writing the link directly")
                val db = database ?: throw IllegalStateException("Firebase Database not available")
                val token = UUID.randomUUID().toString().replace("-", "")
                db.child("shared_collections")
                    .child(token)
                    .setValue(
                        directSharedCollectionRecord(
                            json,
                            collectionName,
                            itemCount,
                            creatorUid,
                            System.currentTimeMillis(),
                        ),
                    )
                    .await()
                token
            },
        )
    }

    private fun buildShareLink(token: String): String = "aura://collection/import/$token"

    private fun readTextFromUri(uri: Uri): String {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > LibraryTransferContract.MAX_COLLECTION_DOCUMENT_BYTES) {
                    throw IllegalArgumentException("Collection file is too large to import safely.")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("Could not read the selected collection file.")
        return bytes.toString(Charsets.UTF_8)
    }

    private suspend fun importJson(json: String): CollectionImportResult {
        if (
            json.toByteArray(Charsets.UTF_8).size >
            LibraryTransferContract.MAX_COLLECTION_DOCUMENT_BYTES
        ) {
            throw IllegalArgumentException("Collection file is too large to import safely.")
        }
        val file = adapter.fromJson(json)
            ?: throw IllegalArgumentException("This is not a valid Aura collection file.")
        if (file.version != CURRENT_VERSION) {
            throw IllegalArgumentException("This collection format is not supported by this Aura version.")
        }
        requireWithinTransferLimit(
            "Collection import items",
            file.items.size,
            LibraryTransferContract.MAX_COLLECTION_ITEMS,
        )
        val importPlan = buildCollectionImportPlan(file.items)
        if (importPlan.items.isEmpty()) {
            throw IllegalArgumentException("This collection does not contain importable wallpapers.")
        }

        val name = sanitizeImportedCollectionName(file.collectionName).ifBlank { "Imported collection" }
        val importedName = "$name (Imported)"
        val collectionId = collectionDao.importCollection(
            collection = WallpaperCollectionEntity(name = importedName),
            items = importPlan.items,
        )
        return CollectionImportResult(
            collectionId = collectionId,
            collectionName = importedName,
            itemCount = importPlan.items.size,
            skippedCount = importPlan.skippedCount,
            failedCount = importPlan.failedCount,
        )
    }

    private fun decodeQrText(uri: Uri): String {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                readStreamCapped(input, LibraryTransferContract.MAX_QR_IMAGE_BYTES)
            }
        } catch (e: MediaIngestionLimitExceeded) {
            throw IllegalArgumentException("QR image is too large to import safely.", e)
        } ?: throw IllegalArgumentException("Could not read the selected QR image.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        val pixels = width.toLong() * height.toLong()
        if (
            width <= 0 ||
            height <= 0 ||
            width > LibraryTransferContract.MAX_QR_IMAGE_DIMENSION ||
            height > LibraryTransferContract.MAX_QR_IMAGE_DIMENSION ||
            pixels > LibraryTransferContract.MAX_QR_IMAGE_PIXELS
        ) {
            throw IllegalArgumentException("QR image is too large to import safely.")
        }
        // Shrink before allocating: a 12 MP photo decodes at a quarter edge, and ZXing reads one
        // byte of luma per pixel instead of a full ARGB copy.
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = qrDecodeSampleSize(width, height) }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw IllegalArgumentException("Could not decode the selected QR image.")
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val luma = qrLuminance(w, h) { y, row -> bitmap.getPixels(row, 0, w, 0, y, w, 1) }
            bitmap.recycle()
            decodeCollectionQr(luma, w, h)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}

/** Imports are sampled down until the longer edge is under twice this; a 768 px export stays readable. */
internal const val QR_DECODE_EDGE = 1_024
private const val QR_DARK = 0xFF000000.toInt()
private const val QR_LIGHT = 0xFFFFFFFF.toInt()

internal fun encodeCollectionQr(text: String, sizePx: Int): BitMatrix {
    requireWithinTransferLimit(
        "Collection QR dimensions",
        sizePx,
        LibraryTransferContract.MAX_QR_IMAGE_DIMENSION,
    )
    return QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(EncodeHintType.MARGIN to 1),
    )
}

/** Black and white ARGB pixels for [matrix], row by row, ready for a single `Bitmap.setPixels`. */
internal fun qrPixels(matrix: BitMatrix): IntArray {
    val width = matrix.width
    val pixels = IntArray(width * matrix.height)
    for (y in 0 until matrix.height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (matrix[x, y]) QR_DARK else QR_LIGHT
        }
    }
    return pixels
}

/**
 * Power-of-two `inSampleSize` that brings the longer edge down to between [targetEdge] and twice
 * that, so the largest import allowed (4096 px, 12 MP) decodes at about 1024 px.
 */
internal fun qrDecodeSampleSize(width: Int, height: Int, targetEdge: Int = QR_DECODE_EDGE): Int {
    val longest = maxOf(width, height)
    var sample = 1
    while (longest / (sample * 2) >= targetEdge) sample *= 2
    return sample
}

/** One byte of luma per pixel, read a row at a time so the image is never copied whole. */
internal fun qrLuminance(width: Int, height: Int, readRow: (y: Int, row: IntArray) -> Unit): ByteArray {
    val luma = ByteArray(width * height)
    val row = IntArray(width)
    for (y in 0 until height) {
        readRow(y, row)
        val offset = y * width
        for (x in 0 until width) {
            val argb = row[x]
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            // Same weighting ZXing's RGBLuminanceSource uses.
            luma[offset + x] = ((r + 2 * g + b) / 4).toByte()
        }
    }
    return luma
}

internal fun decodeCollectionQr(luma: ByteArray, width: Int, height: Int): String {
    val source = PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
    return MultiFormatReader().decode(
        BinaryBitmap(HybridBinarizer(source)),
        mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)),
    ).text
}

data class CollectionShareBundle(
    val uri: Uri,
    val collectionName: String,
    val shareLink: String?,
    val itemCount: Int,
)

data class CollectionShareLink(
    val token: String,
    val link: String,
    val collectionName: String,
    val itemCount: Int,
)

data class CollectionImportResult(
    val collectionId: Long,
    val collectionName: String,
    val itemCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
)

internal data class CollectionImportPlan(
    val items: List<WallpaperCollectionItemEntity>,
    val skippedCount: Int,
    val failedCount: Int,
)

@JsonClass(generateAdapter = true)
data class CollectionExportFile(
    val version: Int,
    val exportedAt: Long,
    val collectionName: String,
    val items: List<CollectionExportItem>,
)

@JsonClass(generateAdapter = true)
data class CollectionExportItem(
    val wallpaperId: String,
    val source: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
)

internal fun extractCollectionShareToken(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.matches(SHARE_TOKEN_REGEX)) return trimmed

    val prefixes = listOf(
        "aura://collection/import/",
        "aura://collections/import/",
        "https://aura.app/collection/import/",
        "https://aura.app/collections/import/",
    )
    return prefixes.firstNotNullOfOrNull { prefix ->
        val index = trimmed.indexOf(prefix, ignoreCase = true)
        if (index < 0) return@firstNotNullOfOrNull null
        trimmed.substring(index + prefix.length)
            .takeWhile { it.isLetterOrDigit() || it == '_' || it == '-' }
            .takeIf { it.matches(SHARE_TOKEN_REGEX) }
    }
}

internal fun requireShareableAsLink(json: String, itemCount: Int) {
    require(
        itemCount <= MAX_SHARED_COLLECTION_ITEMS &&
            json.toByteArray(Charsets.UTF_8).size <= MAX_SHARED_COLLECTION_DOCUMENT_BYTES,
    ) { "Collection links hold up to $MAX_SHARED_COLLECTION_ITEMS wallpapers. Share it as a file instead." }
}

internal fun isFirebasePermissionDenied(error: Throwable): Boolean =
    generateSequence(error) { it.cause }
        .take(8)
        .any { it.message?.contains("permission denied", ignoreCase = true) == true }

internal fun buildCollectionShareText(bundle: CollectionShareBundle): String =
    listOfNotNull(
        "Aura collection: ${bundle.collectionName}",
        "${bundle.itemCount} wallpapers",
        bundle.shareLink,
    ).joinToString("\n")

internal fun sanitizeImportedCollectionName(name: String): String =
    name.trim()
        .replace(Regex("\\s+"), " ")
        .take(LibraryTransferContract.MAX_COLLECTION_NAME_CHARS)

internal fun buildCollectionImportItems(items: List<CollectionExportItem>): List<WallpaperCollectionItemEntity> =
    buildCollectionImportPlan(items).items

internal fun buildCollectionImportPlan(items: List<CollectionExportItem>): CollectionImportPlan {
    requireWithinTransferLimit(
        "Collection import items",
        items.size,
        LibraryTransferContract.MAX_COLLECTION_ITEMS,
    )
    val seen = mutableSetOf<Pair<String, String>>()
    val imported = mutableListOf<WallpaperCollectionItemEntity>()
    var skipped = 0
    var failed = 0
    items.forEach { item ->
        val wallpaperId = normalizeImportedText(item.wallpaperId).takeIf { it.isNotBlank() }
        val fullUrl = normalizeImportedHttpsUrl(item.fullUrl)
        val thumbnailUrl = fullUrl?.let { fallbackUrl ->
            normalizeImportedHttpsUrl(item.thumbnailUrl, allowBlank = true)?.ifBlank { fallbackUrl }
        }
        val source = normalizeImportedContentSource(item.source, blankDefault = "REDDIT")
        if (wallpaperId == null || fullUrl == null || thumbnailUrl == null || source == null) {
            failed++
            return@forEach
        }
        if (!seen.add(source to wallpaperId)) {
            skipped++
            return@forEach
        }
        imported += WallpaperCollectionItemEntity(
            collectionId = 0L,
            wallpaperId = wallpaperId,
            thumbnailUrl = thumbnailUrl,
            fullUrl = fullUrl,
            source = source,
            width = item.width.coerceAtLeast(0),
            height = item.height.coerceAtLeast(0),
        )
    }
    return CollectionImportPlan(
        items = imported,
        skippedCount = skipped,
        failedCount = failed,
    )
}
