package com.freevibe.service

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.WallpaperHistoryEntity
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val THEME_PACK_VERSION = 1
private const val THEME_PACK_MANIFEST_ENTRY = "theme-pack.json"
private const val THEME_PACK_MAX_MANIFEST_CHARS = 1_000_000
private const val THEME_PACK_MAX_ASSET_BYTES = 64L * 1024L * 1024L
private const val THEME_PACK_MAX_TOTAL_ASSET_BYTES = 128L * 1024L * 1024L
private const val THEME_PACK_MAX_ENTRIES = 512

/**
 * Bounds for the only untrusted archive Aura expands. A theme pack is one
 * manifest plus a handful of wallpaper/sound assets, so 512 entries is already
 * generous; anything past it is a malformed or hostile pack.
 */
internal val THEME_PACK_EXTRACTION_LIMITS = ArchiveExtractionLimits(
    maxEntries = THEME_PACK_MAX_ENTRIES,
    maxEntryBytes = THEME_PACK_MAX_ASSET_BYTES,
    maxTotalBytes = THEME_PACK_MAX_TOTAL_ASSET_BYTES,
    maxCompressionRatio = 200L,
)
private const val WIDGET_PREFS = "freevibe_widget"
private const val LIVE_WALLPAPER_PREFS = "freevibe_live_wp"
private val VIDEO_IMPORT_KEYS = setOf("video_path", "scale_mode")
private val WIDGET_IMPORT_KEYS = setOf("tint_vibrant", "tint_accent", "tint_dominant", "shuffle_count")

@Serializable
data class ThemePackRecipe(
    val version: Int = THEME_PACK_VERSION,
    val id: String,
    val name: String,
    val exportedAt: Long = 0L,
    val media: List<ThemePackMediaReference> = emptyList(),
    val videoWallpaper: ThemePackMediaReference? = null,
    val sounds: ThemePackSoundState = ThemePackSoundState(),
    val widget: ThemePackWidgetState = ThemePackWidgetState(),
    val shortcuts: List<ThemeShortcutRecipe> = emptyList(),
    val wallpaperPackJson: String = "",
    val soundProfilesJson: String = "",
    val assetPolicy: ThemePackAssetPolicy = ThemePackAssetPolicy(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class ThemePackMediaReference(
    val role: String,
    val label: String,
    val locator: String,
    val mimeType: String = "",
    val assetKey: String = "",
    val byteCount: Long = 0L,
    val requiresReselection: Boolean = false,
)

@Serializable
data class ThemePackSoundState(
    val ringtoneUri: String = "",
    val notificationUri: String = "",
    val alarmUri: String = "",
    val profileCount: Int = 0,
)

@Serializable
data class ThemePackWidgetState(
    val primaryTint: Int = 0,
    val accentTint: Int = 0,
    val dominantTint: Int = 0,
    val previewWallpaperUri: String = "",
    val shuffleCount: Int = 0,
)

@Serializable
data class ThemeShortcutRecipe(
    val id: String,
    val label: String,
    val action: String,
    val route: String,
    val supportedOnImport: Boolean = false,
    val manualInstruction: String,
)

@Serializable
data class ThemePackAssetPolicy(
    val maxAssetBytes: Long = THEME_PACK_MAX_ASSET_BYTES,
    val embeddedAssetCount: Int = 0,
    val skippedAssetCount: Int = 0,
    val note: String = "Remote URLs stay as references; readable local file/content assets are embedded up to the pack limits.",
)

data class ThemePackExportReport(
    val exportedItemCount: Int,
    val embeddedAssetCount: Int,
    val skippedAssetCount: Int,
)

data class ThemePackImportReport(
    val importedItemCount: Int,
    val instructions: List<String>,
)

internal val themePackJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

internal fun serializeThemePackRecipe(recipe: ThemePackRecipe): String =
    themePackJson.encodeToString(recipe)

internal fun parseThemePackRecipe(raw: String): ThemePackRecipe? =
    if (raw.isBlank()) null
    else runCatching { themePackJson.decodeFromString<ThemePackRecipe>(raw) }.getOrNull()

internal fun defaultThemeShortcutRecipes(): List<ThemeShortcutRecipe> = listOf(
    ThemeShortcutRecipe(
        id = "shuffle_wallpaper",
        label = "Shuffle wallpaper",
        action = TaskerActionReceiver.ACTION_SHUFFLE_NOW,
        route = "wallpapers",
        manualInstruction = "Long-press the Aura icon and pin Shuffle wallpaper if your launcher supports static shortcuts.",
    ),
    ThemeShortcutRecipe(
        id = "rotate_now",
        label = "Rotate now",
        action = TaskerActionReceiver.ACTION_ROTATE_NOW,
        route = "wallpapers",
        manualInstruction = "Long-press the Aura icon and pin Rotate now if your launcher supports static shortcuts.",
    ),
    ThemeShortcutRecipe(
        id = "search_wallpapers",
        label = "Search wallpapers",
        action = "com.freevibe.action.SEARCH",
        route = "wallpapers",
        manualInstruction = "Long-press the Aura icon and pin Search wallpapers if your launcher supports static shortcuts.",
    ),
    ThemeShortcutRecipe(
        id = "downloads",
        label = "Downloads",
        action = "com.freevibe.action.DOWNLOADS",
        route = "downloads",
        manualInstruction = "Long-press the Aura icon and pin Downloads if your launcher supports static shortcuts.",
    ),
)

internal fun remapWallpaperPackAssetLocators(
    raw: String,
    references: List<ThemePackMediaReference>,
    assetRemaps: Map<String, String>,
): String {
    val pack = parsePack(raw) ?: return raw
    val slots = pack.slots.map { slot ->
        val remapped = remappedLocator(
            locator = slot.wallpaperUri,
            references = references,
            assetRemaps = assetRemaps,
        )
        if (remapped == slot.wallpaperUri) slot else slot.copy(wallpaperUri = remapped)
    }
    return serializePack(pack.copy(slots = slots))
}

internal fun remapSoundProfileAssetLocators(
    raw: String,
    references: List<ThemePackMediaReference>,
    assetRemaps: Map<String, String>,
): String {
    val profiles = parseProfiles(raw)
    if (profiles.isEmpty()) return raw
    val remappedProfiles = profiles.map { profile ->
        profile.copy(
            ringtoneUri = remappedLocator(profile.ringtoneUri, references, assetRemaps),
            notificationUri = remappedLocator(profile.notificationUri, references, assetRemaps),
            alarmUri = remappedLocator(profile.alarmUri, references, assetRemaps),
        )
    }
    return serializeProfiles(remappedProfiles)
}

internal fun themePackImportInstructions(
    recipe: ThemePackRecipe,
    assetRemaps: Map<String, String>,
): List<String> {
    val instructions = linkedSetOf<String>()
    recipe.shortcuts
        .filterNot { it.supportedOnImport }
        .mapTo(instructions) { it.manualInstruction }
    recipe.videoWallpaper?.let { video ->
        if (video.locator.isNotBlank() && video.assetKey !in assetRemaps) {
            instructions += "Re-select the video wallpaper if this pack came from another device; Android does not grant reusable access to missing local video files."
        }
    }
    if (recipe.widget != ThemePackWidgetState()) {
        instructions += "Add or resize the Aura widget from your launcher if it is not already on the home screen; imported tint metadata applies after the widget renders."
    }
    if (recipe.sounds != ThemePackSoundState() || recipe.soundProfilesJson.isNotBlank()) {
        instructions += "Grant Modify system settings before imported ringtone or sound-profile recipes can apply system sounds."
    }
    recipe.media
        .filter { it.requiresReselection && it.assetKey !in assetRemaps }
        .forEach { ref ->
            instructions += "Re-select ${ref.label.ifBlank { ref.role }} if Android cannot read its local URI on this device."
        }
    return instructions.toList()
}

internal fun remappedLocator(
    locator: String,
    references: List<ThemePackMediaReference>,
    assetRemaps: Map<String, String>,
): String {
    if (locator.isBlank()) return locator
    val reference = references.firstOrNull { it.locator == locator && it.assetKey.isNotBlank() }
        ?: return locator
    return assetRemaps[reference.assetKey] ?: locator
}

@Singleton
class ThemePackRecipeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager,
    private val wallpaperHistoryManager: WallpaperHistoryManager,
) {
    suspend fun exportThemePack(outputUri: Uri, name: String = "Aura theme pack"): Result<ThemePackExportReport> =
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = buildRecipeSnapshot(name)
                val preparedAssets = prepareAssets(snapshot.assetCandidates())
                val embeddedByRole = preparedAssets.associateBy { it.reference.role }
                val recipe = snapshot.withEmbeddedAssets(embeddedByRole)
                writeThemePack(outputUri, recipe, preparedAssets)
                ThemePackExportReport(
                    exportedItemCount = recipe.exportedItemCount(),
                    embeddedAssetCount = preparedAssets.size,
                    skippedAssetCount = snapshot.assetCandidates().size - preparedAssets.size,
                )
            }.onFailure { it.rethrowIfCancelled() }
        }

    suspend fun importThemePack(inputUri: Uri): Result<ThemePackImportReport> =
        withContext(Dispatchers.IO) {
            runCatching {
                val imported = readThemePack(inputUri)
                val plan = try {
                    // The whole pack is checked before anything is written, so a bad entry
                    // anywhere leaves settings and files as they were.
                    planThemePackImport(imported.recipe, imported.assetsByKey)
                        .also { commitThemePackImport(it) }
                } catch (e: Throwable) {
                    imported.importDir?.let { runCatching { it.deleteRecursively() } }
                    throw e
                }

                pruneStaleImportDirs(current = imported.importDir)

                ThemePackImportReport(
                    importedItemCount = plan.importedItemCount,
                    instructions = plan.instructions,
                )
            }.onFailure { it.rethrowIfCancelled() }
        }

    /**
     * Deletes previous `theme_packs/import-*` directories that are no longer referenced
     * by any imported configuration. Without this, every import permanently orphans the
     * previous import's assets (nothing else — including Settings "Clear cache", which
     * only touches cacheDir — ever reclaims them).
     */
    private suspend fun pruneStaleImportDirs(current: File?) {
        val root = File(context.filesDir, "theme_packs")
        val dirs = root.listFiles { f -> f.isDirectory && f.name.startsWith("import-") } ?: return
        if (dirs.isEmpty()) return
        val referenced = buildString {
            appendLine(prefs.wallpaperPackJson.first())
            appendLine(prefs.soundProfilesJson.first())
            appendLine(prefs.lastAppliedRingtoneUri.first())
            appendLine(prefs.lastAppliedNotificationUri.first())
            appendLine(prefs.lastAppliedAlarmUri.first())
            appendLine(
                context.getSharedPreferences(LIVE_WALLPAPER_PREFS, Context.MODE_PRIVATE)
                    .getString("video_path", "").orEmpty(),
            )
            // Recent wallpaper-history locators: the global apply-undo flow re-applies
            // the previous wallpaper by locator, which may live in a prior import dir.
            wallpaperHistoryManager.getRecent(limit = 10).first().forEach { entry ->
                appendLine(entry.fullUrl)
                appendLine(entry.thumbnailUrl)
            }
        }
        dirs.forEach { dir ->
            if (current != null && dir.name == current.name) return@forEach
            if (!referenced.contains(dir.absolutePath)) {
                runCatching { dir.deleteRecursively() }
            }
        }
    }

    private suspend fun buildRecipeSnapshot(name: String): ThemePackRecipe {
        val wallpaperPackJson = prefs.wallpaperPackJson.first()
        val soundProfilesJson = prefs.soundProfilesJson.first()
        val recentWallpaper = wallpaperHistoryManager.mostRecent().first()
        val videoPrefs = context.getSharedPreferences(LIVE_WALLPAPER_PREFS, Context.MODE_PRIVATE)
        val videoPath = videoPrefs.getString("video_path", "").orEmpty()
        val soundState = ThemePackSoundState(
            ringtoneUri = prefs.lastAppliedRingtoneUri.first(),
            notificationUri = prefs.lastAppliedNotificationUri.first(),
            alarmUri = prefs.lastAppliedAlarmUri.first(),
            profileCount = parseProfiles(soundProfilesJson).size,
        )
        val wallpaperRefs = buildWallpaperReferences(recentWallpaper, wallpaperPackJson)
        val soundRefs = buildSoundReferences(soundState, soundProfilesJson)
        val videoRef = videoPath.takeIf { it.isNotBlank() }?.let {
            mediaReference(
                role = "video_wallpaper",
                label = "Video wallpaper",
                locator = it,
                mimeType = "video/*",
            )
        }

        return ThemePackRecipe(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Aura theme pack" },
            exportedAt = System.currentTimeMillis(),
            media = wallpaperRefs + soundRefs,
            videoWallpaper = videoRef,
            sounds = soundState,
            widget = exportWidget(recentWallpaper),
            shortcuts = defaultThemeShortcutRecipes(),
            wallpaperPackJson = wallpaperPackJson,
            soundProfilesJson = soundProfilesJson,
            notes = listOf(
                "Theme packs are local files. Remote provider URLs remain references.",
                "Launcher shortcuts and widgets may require manual launcher steps after import.",
            ),
        )
    }

    private fun buildWallpaperReferences(
        recentWallpaper: WallpaperHistoryEntity?,
        wallpaperPackJson: String,
    ): List<ThemePackMediaReference> {
        val refs = mutableListOf<ThemePackMediaReference>()
        recentWallpaper?.fullUrl?.takeIf { it.isNotBlank() }?.let {
            refs += mediaReference(
                role = "current_wallpaper",
                label = "Current wallpaper",
                locator = it,
                mimeType = "image/*",
            )
        }
        parsePack(wallpaperPackJson)?.slots.orEmpty().forEach { slot ->
            if (slot.wallpaperUri.isNotBlank()) {
                refs += mediaReference(
                    role = "wallpaper_pack_${slot.daypart.name.lowercase(Locale.ROOT)}",
                    label = slot.label.ifBlank { "${slot.daypart.displayName} wallpaper" },
                    locator = slot.wallpaperUri,
                    mimeType = "image/*",
                )
            }
        }
        return refs.distinctBy { it.role to it.locator }
    }

    private fun buildSoundReferences(
        soundState: ThemePackSoundState,
        soundProfilesJson: String,
    ): List<ThemePackMediaReference> {
        val refs = mutableListOf<ThemePackMediaReference>()
        listOf(
            "ringtone" to soundState.ringtoneUri,
            "notification" to soundState.notificationUri,
            "alarm" to soundState.alarmUri,
        ).forEach { (role, uri) ->
            if (uri.isNotBlank()) {
                refs += mediaReference(
                    role = "sound_$role",
                    label = role.replaceFirstChar { it.titlecase(Locale.ROOT) },
                    locator = uri,
                    mimeType = "audio/*",
                )
            }
        }
        parseProfiles(soundProfilesJson).forEach { profile ->
            listOf(
                "ringtone" to profile.ringtoneUri,
                "notification" to profile.notificationUri,
                "alarm" to profile.alarmUri,
            ).forEach { (kind, uri) ->
                if (uri.isNotBlank()) {
                    refs += mediaReference(
                        role = "sound_profile_${profile.id}_$kind",
                        label = "${profile.name} $kind",
                        locator = uri,
                        mimeType = "audio/*",
                    )
                }
            }
        }
        return refs.distinctBy { it.role to it.locator }
    }

    private fun mediaReference(
        role: String,
        label: String,
        locator: String,
        mimeType: String,
    ): ThemePackMediaReference {
        val local = isLocalLocator(locator)
        return ThemePackMediaReference(
            role = role,
            label = label,
            locator = locator,
            mimeType = mimeType,
            requiresReselection = local,
        )
    }

    private fun exportWidget(recentWallpaper: WallpaperHistoryEntity?): ThemePackWidgetState {
        val widgetPrefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        return ThemePackWidgetState(
            primaryTint = widgetPrefs.getInt("tint_vibrant", 0),
            accentTint = widgetPrefs.getInt("tint_accent", 0)
                .takeIf { it != 0 } ?: widgetPrefs.getInt("tint_vibrant_light", 0),
            dominantTint = widgetPrefs.getInt("tint_dominant", 0),
            previewWallpaperUri = recentWallpaper?.thumbnailUrl.orEmpty().ifBlank {
                recentWallpaper?.fullUrl.orEmpty()
            },
            shuffleCount = widgetPrefs.getInt("shuffle_count", 0),
        )
    }

    /**
     * Writes a validated plan. The DataStore keys land in one edit. The live-wallpaper and
     * widget SharedPreferences go first and are put back if that edit fails.
     */
    private suspend fun commitThemePackImport(plan: ThemePackImportPlan) {
        val videoPrefs = context.getSharedPreferences(LIVE_WALLPAPER_PREFS, Context.MODE_PRIVATE)
        val widgetPrefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val videoBefore = videoPrefs.all.filterKeys { it in VIDEO_IMPORT_KEYS }
        val widgetBefore = widgetPrefs.all.filterKeys { it in WIDGET_IMPORT_KEYS }
        try {
            plan.videoPath?.let { path ->
                videoPrefs.edit()
                    .putString("video_path", path)
                    .putString("scale_mode", VIDEO_WALLPAPER_SCALE_MODE_ZOOM)
                    .commitOrThrow()
            }
            plan.widget?.let { widget ->
                widgetPrefs.edit()
                    .putInt("tint_vibrant", widget.primaryTint)
                    .putInt("tint_accent", widget.accentTint)
                    .putInt("tint_dominant", widget.dominantTint)
                    .putInt("shuffle_count", widget.shuffleCount)
                    .commitOrThrow()
            }
            prefs.applyThemePackImport(
                wallpaperPackJson = plan.wallpaperPackJson,
                soundProfilesJson = plan.soundProfilesJson,
                ringtoneUri = plan.ringtoneUri,
                notificationUri = plan.notificationUri,
                alarmUri = plan.alarmUri,
            )
            plan.wallpaperPackJson?.let { packJson ->
                runCatching { syncWallpaperPackSchedule(context, prefs.wallpaperPackEnabled.first(), packJson) }
            }
            plan.soundProfilesJson?.let { profilesJson ->
                runCatching {
                    SoundProfileWorker.sync(
                        context,
                        soundProfilesNeedWorker(prefs.soundProfilesEnabled.first(), parseProfiles(profilesJson)),
                    )
                }
            }
        } catch (e: Throwable) {
            videoPrefs.restoreKeys(VIDEO_IMPORT_KEYS, videoBefore)
            widgetPrefs.restoreKeys(WIDGET_IMPORT_KEYS, widgetBefore)
            throw e
        }
    }

    private fun writeThemePack(
        outputUri: Uri,
        recipe: ThemePackRecipe,
        preparedAssets: List<PreparedThemeAsset>,
    ) {
        try {
            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    preparedAssets.forEach { asset ->
                        zip.putNextEntry(ZipEntry(asset.entryName))
                        asset.file.inputStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                zip.write(buffer, 0, read)
                            }
                        }
                        zip.closeEntry()
                    }
                    zip.putNextEntry(ZipEntry(THEME_PACK_MANIFEST_ENTRY))
                    zip.write(serializeThemePackRecipe(recipe).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            } ?: throw IOException("Could not open theme pack output")
        } catch (e: Throwable) {
            // Don't leave a truncated pack at the user's chosen location.
            runCatching {
                android.provider.DocumentsContract.deleteDocument(context.contentResolver, outputUri)
            }
            throw e
        } finally {
            // Temp copies (up to 128 MB) must go regardless of export outcome.
            preparedAssets.forEach { runCatching { it.file.delete() } }
        }
    }

    private fun readThemePack(inputUri: Uri): ImportedThemePack {
        val input = context.contentResolver.openInputStream(inputUri)
            ?: throw IOException("Could not open theme pack")
        input.buffered().use { stream ->
            stream.mark(4)
            val signature = ByteArray(4)
            val count = stream.read(signature)
            stream.reset()
            return if (count >= 2 && signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte()) {
                readZipThemePack(ZipInputStream(stream))
            } else {
                val json = stream.reader(Charsets.UTF_8).readTextCapped(THEME_PACK_MAX_MANIFEST_CHARS)
                ImportedThemePack(
                    recipe = parseThemePackRecipe(json)
                        ?: throw IllegalStateException("Invalid theme pack JSON"),
                    assetsByKey = emptyMap(),
                )
            }
        }
    }

    private fun readZipThemePack(zip: ZipInputStream): ImportedThemePack =
        extractThemePackArchive(
            zip = zip,
            importDir = File(context.filesDir, "theme_packs/import-${System.currentTimeMillis()}"),
        )

    private fun prepareAssets(references: List<ThemePackMediaReference>): List<PreparedThemeAsset> {
        val tempDir = File(context.cacheDir, "theme-pack-export").apply { mkdirs() }
        val prepared = mutableListOf<PreparedThemeAsset>()
        var totalBytes = 0L
        references.filter { isLocalLocator(it.locator) }.forEach { reference ->
            val remaining = THEME_PACK_MAX_TOTAL_ASSET_BYTES - totalBytes
            if (remaining <= 0) return@forEach
            val asset = runCatching {
                prepareAsset(reference, tempDir, remaining.coerceAtMost(THEME_PACK_MAX_ASSET_BYTES))
            }.getOrNull()
            if (asset != null) {
                totalBytes += asset.byteCount
                prepared += asset
            }
        }
        return prepared
    }

    private fun prepareAsset(
        reference: ThemePackMediaReference,
        tempDir: File,
        maxBytes: Long,
    ): PreparedThemeAsset? {
        val input = openLocalLocator(reference.locator) ?: return null
        val extension = extensionForLocator(reference.locator, reference.mimeType)
        val entryName = "assets/${safeFileStem(reference.role)}-${shortHash(reference.locator)}.$extension"
        val temp = File(tempDir, "${shortHash(entryName)}.tmp")
        var copied = 0L
        input.use { source ->
            temp.outputStream().use { target ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    copied += read
                    if (copied > maxBytes) {
                        temp.delete()
                        return null
                    }
                    target.write(buffer, 0, read)
                }
            }
        }
        if (copied == 0L) {
            temp.delete()
            return null
        }
        return PreparedThemeAsset(
            reference = reference.copy(
                assetKey = entryName,
                byteCount = copied,
                requiresReselection = false,
            ),
            file = temp,
            entryName = entryName,
            byteCount = copied,
        )
    }

    private fun openLocalLocator(locator: String): java.io.InputStream? {
        val trimmed = locator.trim()
        if (trimmed.isBlank()) return null
        return when (schemeOf(trimmed)) {
            "content" -> context.contentResolver.openInputStream(Uri.parse(trimmed))
            "file" -> Uri.parse(trimmed).path?.let(::File)?.takeIf { it.isFile }?.inputStream()
            null -> File(trimmed).takeIf { it.isFile }?.inputStream()
            else -> null
        }
    }

    private fun ThemePackRecipe.assetCandidates(): List<ThemePackMediaReference> =
        allMediaReferences().filter { isLocalLocator(it.locator) }

    private fun ThemePackRecipe.withEmbeddedAssets(
        assetsByRole: Map<String, PreparedThemeAsset>,
    ): ThemePackRecipe {
        fun ThemePackMediaReference.withAsset(): ThemePackMediaReference =
            assetsByRole[role]?.reference ?: copy(assetKey = "", byteCount = 0L, requiresReselection = isLocalLocator(locator))
        val media = this.media.map { it.withAsset() }
        val video = videoWallpaper?.withAsset()
        val skipped = assetCandidates().size - assetsByRole.size
        return copy(
            media = media,
            videoWallpaper = video,
            assetPolicy = assetPolicy.copy(
                embeddedAssetCount = assetsByRole.size,
                skippedAssetCount = skipped.coerceAtLeast(0),
            ),
        )
    }

    private fun ThemePackRecipe.allMediaReferences(): List<ThemePackMediaReference> =
        media + listOfNotNull(videoWallpaper)

    private fun ThemePackRecipe.exportedItemCount(): Int =
        media.size +
            listOfNotNull(videoWallpaper).size +
            listOf(sounds.ringtoneUri, sounds.notificationUri, sounds.alarmUri).count { it.isNotBlank() } +
            parseProfiles(soundProfilesJson).size +
            parsePack(wallpaperPackJson)?.slots.orEmpty().size +
            shortcuts.size +
            if (widget != ThemePackWidgetState()) 1 else 0

    private fun isLocalLocator(locator: String): Boolean =
        when (schemeOf(locator)) {
            null, "content", "file" -> locator.isNotBlank()
            else -> false
        }

    private fun schemeOf(locator: String): String? {
        val colon = locator.indexOf(':')
        if (colon <= 0) return null
        return locator.substring(0, colon).lowercase(Locale.ROOT)
    }

    private fun extensionForLocator(locator: String, mimeType: String): String {
        val path = when (schemeOf(locator)) {
            "content" -> queryDisplayName(locator).orEmpty()
            "file" -> Uri.parse(locator).path.orEmpty()
            else -> locator
        }
        val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }
            .take(8)
        if (extension.isNotBlank()) return extension
        return when {
            mimeType.startsWith("audio/") -> "audio"
            mimeType.startsWith("video/") -> "mp4"
            else -> "bin"
        }
    }

    private fun queryDisplayName(locator: String): String? =
        runCatching {
            context.contentResolver.query(
                Uri.parse(locator),
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

    private fun safeFileStem(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .ifBlank { "asset" }
            .take(48)

    private fun shortHash(value: String): String = themePackShortHash(value)

    private data class PreparedThemeAsset(
        val reference: ThemePackMediaReference,
        val file: File,
        val entryName: String,
        val byteCount: Long,
    )

}

private fun SharedPreferences.Editor.commitOrThrow() {
    if (!commit()) throw IOException("Could not save theme pack settings")
}

private fun SharedPreferences.restoreKeys(keys: Set<String>, before: Map<String, *>) {
    val editor = edit()
    keys.forEach { key ->
        when (val value = before[key]) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
            else -> editor.remove(key)
        }
    }
    editor.commit()
}

internal data class ImportedThemePack(
    val recipe: ThemePackRecipe,
    val assetsByKey: Map<String, String>,
    val importDir: File? = null,
)

/**
 * Expands a theme-pack zip into [importDir] under [THEME_PACK_EXTRACTION_LIMITS].
 *
 * Split out of [ThemePackRecipeManager] so the hostile-archive paths (traversal,
 * entry floods, oversize entries, zip bombs) can be exercised as plain JVM tests
 * against real archives instead of only through a device import.
 *
 * On any failure the whole staging directory is removed — a rejected pack must
 * not leave partially expanded assets behind.
 */
internal fun extractThemePackArchive(zip: ZipInputStream, importDir: File): ImportedThemePack {
    importDir.mkdirs()
    try {
        val assetsByKey = mutableMapOf<String, String>()
        var manifest: String? = null
        val guard = ArchiveExtractionGuard.newSession(THEME_PACK_EXTRACTION_LIMITS)
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            // Every entry counts, including directories and ignored names, so a pack
            // cannot burn the entry budget on entries the branches below skip.
            guard.beginEntry(name)
            when {
                entry.isDirectory -> {
                    drainZipEntryCapped(zip, guard.remainingEntryBudget()) {
                        guard.failEntryTooLarge()
                    }.let { bytes ->
                        if (bytes > 0) guard.commitEntry(expandedBytes = bytes, compressedBytes = entry.compressedSize)
                    }
                }
                name == THEME_PACK_MANIFEST_ENTRY -> {
                    manifest = zip.reader(Charsets.UTF_8).readTextCapped(THEME_PACK_MAX_MANIFEST_CHARS)
                }
                name.startsWith("assets/") -> {
                    val safeName = "${themePackShortHash(name)}_${name.substringAfterLast('/').take(60)}"
                    val target = File(importDir, safeName)
                    val bytes = copyZipEntryCapped(zip, target, guard.remainingEntryBudget()) {
                        target.delete()
                        guard.failEntryTooLarge()
                    }
                    guard.commitEntry(expandedBytes = bytes, compressedBytes = entry.compressedSize)
                    assetsByKey[name] = target.absolutePath
                }
                else -> {
                    val bytes = drainZipEntryCapped(zip, guard.remainingEntryBudget()) {
                        guard.failEntryTooLarge()
                    }
                    if (bytes > 0) guard.commitEntry(expandedBytes = bytes, compressedBytes = entry.compressedSize)
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        val recipe = parseThemePackRecipe(manifest.orEmpty())
            ?: throw IllegalStateException("Theme pack manifest missing or invalid")
        return ImportedThemePack(recipe = recipe, assetsByKey = assetsByKey, importDir = importDir)
    } catch (e: Throwable) {
        // A failed import (bad manifest, oversize assets, IO error) must not orphan
        // up to 128 MB of extracted assets in filesDir — nothing else cleans it.
        runCatching { importDir.deleteRecursively() }
        throw e
    }
}

private inline fun drainZipEntryCapped(
    zip: ZipInputStream,
    maxBytes: Long,
    onOverflow: () -> Nothing,
): Long {
    var drained = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = zip.read(buffer)
        if (read == -1) break
        drained += read
        if (drained > maxBytes) onOverflow()
    }
    return drained
}

private inline fun copyZipEntryCapped(
    zip: ZipInputStream,
    target: File,
    maxBytes: Long,
    onOverflow: () -> Nothing,
): Long {
    var copied = 0L
    target.outputStream().use { output ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = zip.read(buffer)
            if (read == -1) break
            copied += read
            if (copied > maxBytes) onOverflow()
            output.write(buffer, 0, read)
        }
    }
    return copied
}

private fun java.io.Reader.readTextCapped(maxChars: Int): String {
    val builder = StringBuilder()
    val buffer = CharArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        builder.append(buffer, 0, read)
        if (builder.length > maxChars) {
            throw IOException("Theme pack manifest is too large")
        }
    }
    return builder.toString()
}

internal fun themePackShortHash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
    return digest.take(6).joinToString("") { "%02x".format(it) }
}
