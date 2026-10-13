package com.freevibe.data.repository

import android.content.Context
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.service.advertisedLengthExceeds
import com.freevibe.service.copyStreamCapped
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Display names live with the generator UI in the full flavor (AiStyle.labelRes). */
enum class AiStyle(val preset: String) {
    PHOTOGRAPHIC("photographic"),
    ANIME("anime"),
    DIGITAL_ART("digital-art"),
    CINEMATIC("cinematic"),
    FANTASY("fantasy-art"),
    NEON_PUNK("neon-punk"),
    PIXEL_ART("pixel-art"),
    NONE(""),
}

@Singleton
class AiWallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backend: GeneratedWallpaperBackend,
    private val referenceIndex: GeneratedAssetReferenceIndex,
) {
    private val dir: File
        get() = File(context.filesDir, "ai_wallpapers").also { it.mkdirs() }

    suspend fun generate(
        prompt: String,
        style: AiStyle = AiStyle.PHOTOGRAPHIC,
        apiKey: String,
    ): Result<Wallpaper> = withContext(Dispatchers.IO) {
        // Note: `runCatching` here intentionally lets cancellation propagate. Without the
        // explicit rethrow, a ViewModel scope cancel (back-navigation mid-generate) would
        // be captured as a failure Result and surface to the user as a generic error.
        try {
            val body = backend.generate(prompt, style, apiKey).getOrThrow()
            body.use {
                if (advertisedLengthExceeds(body.contentLength(), MAX_GENERATED_WALLPAPER_BYTES)) {
                    throw IllegalStateException("Generated wallpaper is too large")
                }

                val id = UUID.randomUUID().toString()
                val file = File(dir, "$id.png")
                val tmp = File(dir, "$id.tmp")
                try {
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            copyStreamCapped(input, output, MAX_GENERATED_WALLPAPER_BYTES)
                        }
                    }
                    if (!tmp.renameTo(file)) {
                        tmp.copyTo(file, overwrite = true)
                        tmp.delete()
                    }
                } catch (e: Throwable) {
                    runCatching { tmp.delete() }
                    if (e is CancellationException) throw e
                    throw e
                }

                // Reclaim storage *after* the new file lands so an inflight generation never
                // races against eviction. The cap is 50 *unreferenced* files; older ones go
                // first. Pruning is best-effort, but cancellation must still propagate.
                try {
                    pruneOldFilesInternal(MAX_GENERATED_FILES)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Storage reclamation is not worth failing a successful generation.
                }

                Result.success(
                    Wallpaper(
                        id = id,
                        source = ContentSource.AI_GENERATED,
                        thumbnailUrl = file.toURI().toString(),
                        fullUrl = file.toURI().toString(),
                        width = 576,
                        height = 1024,
                        category = "AI Generated",
                        tags = generatedWallpaperTags(style),
                        uploaderName = "AI",
                        isAiGenerated = true,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Delete generated images beyond the most recent [maxCount] to reclaim storage.
     *
     * Files that any store still points at are kept regardless of age — the cap
     * bounds *unreferenced* history, not the user's library.
     */
    suspend fun pruneOldFiles(maxCount: Int = MAX_GENERATED_FILES) = withContext(Dispatchers.IO) {
        pruneOldFilesInternal(maxCount)
    }

    /**
     * Delete a generated wallpaper only once the last managed reference to it is gone.
     *
     * Callers removing a reference of their own (unfavourite, collection removal)
     * must persist that removal first, so their row is not counted as a survivor.
     *
     * @return true when the file was deleted; false when it was kept because
     *   something still references it, or when [locator] is not an Aura-managed
     *   generated asset.
     */
    suspend fun deleteGeneratedWallpaper(locator: String): Boolean = withContext(Dispatchers.IO) {
        val file = generatedWallpaperFile(locator) ?: return@withContext false
        if (!referenceIndex.isUnreferenced(file)) return@withContext false
        runCatching { file.delete() }.getOrDefault(false)
    }

    /**
     * Storage/health snapshot for the managed generated-wallpaper directory:
     * how many files are still referenced, how many are prunable, and how many
     * references now point at a file that is gone.
     */
    suspend fun auditGeneratedAssets(): GeneratedAssetAudit = withContext(Dispatchers.IO) {
        referenceIndex.audit(generatedFiles())
    }

    private fun generatedWallpaperFile(locator: String): File? {
        val file = runCatching {
            val uri = URI(locator)
            when (uri.scheme) {
                "file" -> File(uri)
                null -> File(locator)
                else -> return null
            }
        }.getOrNull() ?: return null
        val root = dir.canonicalFile
        val candidate = file.canonicalFile
        val rootPath = root.path + File.separator
        if (!candidate.path.startsWith(rootPath)) return null
        if (!candidate.isFile || candidate.extension.lowercase(Locale.ROOT) != "png") return null
        return candidate
    }

    private fun generatedFiles(): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Same as [pruneOldFiles] but already on the IO dispatcher (no extra withContext). */
    private suspend fun pruneOldFilesInternal(maxCount: Int) {
        val files = generatedFiles()
        // Sweep .tmp leftovers (interrupted writes) too — they accumulate otherwise.
        // These are never referenced: they only exist between the download and the
        // atomic rename, so no store has ever seen their names.
        dir.listFiles()
            ?.filter { it.isFile && it.extension == "tmp" }
            ?.forEach { runCatching { it.delete() } }
        // Only unreferenced files count against the cap, and only unreferenced
        // files are deleted. A favourited or slotted PNG survives an unbounded
        // number of newer generations.
        var keptUnreferenced = 0
        files.forEach { file ->
            if (!referenceIndex.isUnreferenced(file)) return@forEach
            if (keptUnreferenced < maxCount) {
                keptUnreferenced++
            } else {
                runCatching { file.delete() }
            }
        }
    }

    companion object {
        /** Hard cap on stored generated wallpapers. Surfaced for tests. */
        internal const val MAX_GENERATED_FILES = 50
        private const val MAX_GENERATED_WALLPAPER_BYTES = 32L * 1024L * 1024L

        internal fun generatedWallpaperTags(style: AiStyle): List<String> = buildList {
            add("ai-generated")
            if (style.preset.isNotEmpty()) add(style.preset)
        }

    }
}
