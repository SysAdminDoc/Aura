package com.freevibe.service

import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.WallpaperTarget
import java.util.Locale

private const val THEME_PACK_MAX_LOCATOR_CHARS = 4_096
private val THEME_PACK_URI_SCHEMES = setOf("https", "http", "content", "android.resource", "rawresource")
private val THEME_PACK_SCHEME_REGEX = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

/** A theme pack that failed validation. Nothing has been written when this is thrown. */
class ThemePackValidationException(message: String) : IllegalArgumentException(message)

/**
 * Everything a theme-pack import writes, worked out and checked before any of it is
 * written. A null field leaves that setting as it is.
 */
internal data class ThemePackImportPlan(
    val wallpaperPackJson: String? = null,
    val soundProfilesJson: String? = null,
    val ringtoneUri: String? = null,
    val notificationUri: String? = null,
    val alarmUri: String? = null,
    val videoPath: String? = null,
    val widget: ThemePackWidgetState? = null,
    val instructions: List<String> = emptyList(),
) {
    val importedItemCount: Int
        get() = listOfNotNull(
            wallpaperPackJson,
            soundProfilesJson,
            ringtoneUri,
            notificationUri,
            alarmUri,
            videoPath,
            widget,
        ).size
}

/**
 * Validates the whole [recipe] against the files the archive actually held, then
 * returns what the import would store. Throws [ThemePackValidationException] on the
 * first problem, so a pack is either taken whole or not at all.
 *
 * A 24H pack slot and a profile sound are opened later by a worker, with nobody looking, so a web
 * address there is held to what a library restore keeps. A slot may name one only on a provider's
 * own host ([isImportedProviderMediaUrl]). A profile sound may not name one at all, because it is
 * handed to the system as a default sound and the phone would contact that host when it rings.
 * A file the pack carries, or one already on the phone, isn't a web address and passes.
 */
internal fun planThemePackImport(
    recipe: ThemePackRecipe,
    assetsByKey: Map<String, String>,
    isProviderAvailable: (ContentSource) -> Boolean = ::isProviderAvailableInCurrentArtifact,
): ThemePackImportPlan {
    if (recipe.version > THEME_PACK_VERSION) invalidThemePack("Theme pack version ${recipe.version} is not supported yet")
    if (recipe.version < 1) invalidThemePack("Theme pack version ${recipe.version} is not valid")

    val references = recipe.media + listOfNotNull(recipe.videoWallpaper)
    val roles = mutableSetOf<String>()
    references.forEach { reference ->
        val name = reference.label.ifBlank { reference.role }
        if (reference.role.isBlank()) invalidThemePack("Theme pack has a media item with no role")
        if (!roles.add(reference.role)) invalidThemePack("Theme pack lists ${reference.role} twice")
        requireThemePackLocator(reference.locator, name)
        if (reference.assetKey.isNotBlank() && reference.assetKey !in assetsByKey) {
            invalidThemePack("Theme pack is missing the file for $name")
        }
    }

    val wallpaperPackJson = recipe.wallpaperPackJson.takeIf { it.isNotBlank() }?.let { raw ->
        val pack = parsePack(raw) ?: invalidThemePack("Theme pack 24H wallpaper recipe is not valid")
        if (pack.id.isBlank()) invalidThemePack("Theme pack 24H wallpaper recipe has no ID")
        if (WallpaperTarget.entries.none { it.name == pack.target }) {
            invalidThemePack("Theme pack 24H wallpaper recipe has an unknown target ${pack.target}")
        }
        val dayparts = mutableSetOf<Daypart>()
        pack.slots.forEach { slot ->
            if (!dayparts.add(slot.daypart)) {
                invalidThemePack("Theme pack 24H wallpaper recipe has two ${slot.daypart.displayName} slots")
            }
            val name = "${slot.daypart.displayName} wallpaper"
            requireThemePackLocator(slot.wallpaperUri, name)
            // What the worker will be given: the file the pack carried when there is one.
            val stored = remappedLocator(slot.wallpaperUri, references, assetsByKey)
            if (isWebLocator(stored) && !isImportedProviderMediaUrl(stored, isProviderAvailable)) {
                invalidThemePack("Theme pack has a web address for $name that isn't on one of Aura's sources")
            }
        }
        remapWallpaperPackAssetLocators(raw, references, assetsByKey)
    }

    val soundProfilesJson = recipe.soundProfilesJson.takeIf { it.isNotBlank() }?.let { raw ->
        val profiles = runCatching { soundProfileJson.decodeFromString<List<SoundProfile>>(raw) }.getOrNull()
            ?: invalidThemePack("Theme pack sound profiles are not valid")
        val ids = mutableSetOf<String>()
        profiles.forEach { profile ->
            if (profile.id.isBlank()) invalidThemePack("Theme pack has a sound profile with no ID")
            if (!ids.add(profile.id)) invalidThemePack("Theme pack lists sound profile ${profile.id} twice")
            if (profile.startHour !in 0..24 || profile.endHour !in 0..24) {
                invalidThemePack("Theme pack sound profile ${profile.name} has hours outside the day")
            }
            listOf(profile.ringtoneUri, profile.notificationUri, profile.alarmUri)
                .filter { it.isNotBlank() }
                .forEach { locator ->
                    val name = "${profile.name} sound"
                    requireThemePackLocator(locator, name)
                    if (isWebLocator(remappedLocator(locator, references, assetsByKey))) {
                        invalidThemePack(
                            "Theme pack has a web address for $name. A profile sound has to be on the phone or in the pack",
                        )
                    }
                }
        }
        remapSoundProfileAssetLocators(raw, references, assetsByKey)
    }

    fun soundUri(locator: String, name: String): String? {
        if (locator.isBlank()) return null
        requireThemePackLocator(locator, name)
        return remappedLocator(locator, references, assetsByKey)
    }

    val videoPath = recipe.videoWallpaper
        ?.assetKey
        ?.takeIf { it.isNotBlank() }
        ?.let(assetsByKey::get)
        ?.takeIf { it.isNotBlank() }

    return ThemePackImportPlan(
        wallpaperPackJson = wallpaperPackJson,
        soundProfilesJson = soundProfilesJson,
        ringtoneUri = soundUri(recipe.sounds.ringtoneUri, "ringtone"),
        notificationUri = soundUri(recipe.sounds.notificationUri, "notification sound"),
        alarmUri = soundUri(recipe.sounds.alarmUri, "alarm sound"),
        videoPath = videoPath,
        widget = recipe.widget
            .takeIf { it != ThemePackWidgetState() }
            ?.let { it.copy(shuffleCount = it.shuffleCount.coerceAtLeast(0)) },
        instructions = themePackImportInstructions(recipe, assetsByKey),
    )
}

/**
 * Locators Aura can open: web and content URIs, bundled resources, and absolute
 * file paths without parent-directory hops.
 */
internal fun isAcceptableThemePackLocator(locator: String): Boolean {
    if (locator.isBlank() || locator.length > THEME_PACK_MAX_LOCATOR_CHARS) return false
    if (locator != locator.trim() || locator.any { it.isISOControl() }) return false
    val scheme = THEME_PACK_SCHEME_REGEX.find(locator)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        ?: return locator.startsWith("/") && ".." !in locator.split('/')
    // `File.toURI()` writes `file:/path` and `Uri.fromFile` writes `file:///path`. Both are local;
    // `file://host/path` names another machine and is refused.
    if (scheme == "file") {
        val local = locator.startsWith("file:///", ignoreCase = true) ||
            (locator.startsWith("file:/", ignoreCase = true) && !locator.startsWith("file://", ignoreCase = true))
        return local && locator.length > "file:/".length && ".." !in locator.split('/')
    }
    return scheme in THEME_PACK_URI_SCHEMES &&
        locator.startsWith("$scheme://", ignoreCase = true) &&
        locator.length > scheme.length + 3
}

/** True for an address the phone would have to go online to open. */
internal fun isWebLocator(locator: String): Boolean {
    val trimmed = locator.trim()
    return trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)
}

private fun requireThemePackLocator(locator: String, name: String) {
    if (!isAcceptableThemePackLocator(locator)) {
        invalidThemePack("Theme pack has a location Aura can't open for $name")
    }
}

private fun invalidThemePack(message: String): Nothing = throw ThemePackValidationException(message)
