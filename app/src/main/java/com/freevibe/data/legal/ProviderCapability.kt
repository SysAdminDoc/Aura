package com.freevibe.data.legal

import com.freevibe.BuildConfig
import com.freevibe.data.model.ContentSource
import java.util.Locale

/**
 * Where a source sits in its life: this is what decides whether Aura is allowed
 * to make a network call for it at all, not just how the UI labels it.
 */
enum class ProviderLifecycle {
    /** Fetched today. Must declare at least one network endpoint. */
    ACTIVE,

    /**
     * No active feed. Saved records keep their attribution and stay visible, but
     * no new fetch may originate from this source.
     */
    LEGACY,

    /** User-supplied device media. No remote catalog at all. */
    LOCAL,

    /** Aura's own Firebase-backed community content. */
    COMMUNITY,

    /** Produced on request from a user-configured generator. */
    GENERATED,
}

/** Build flavors a source can operate in. */
enum class ProviderBuild { FULL, FOSS }

/** Distribution channels a source is allowed to ship enabled on. */
enum class ProviderChannel { GITHUB, PLAY }

/** What the user must supply before the source can be used. */
enum class ProviderConfiguration {
    /** Works out of the box. */
    NONE,

    /** Ships with a usable default; a user key raises limits or unlocks extras. */
    OPTIONAL_KEY,

    /** Unusable until the user supplies their own credential. */
    REQUIRED_KEY,
}

/** Runtime permission the source needs before it can do anything. */
enum class ProviderPermission { NONE, APPROXIMATE_LOCATION, MEDIA_ACCESS }

/** Whether per-source health/backoff tracking is meaningful. */
enum class ProviderHealth {
    /** Makes network calls; `SourceMetrics` and backoff apply. */
    NETWORKED,

    /** No network calls; nothing to measure. */
    OFFLINE,
}

/** Media catalogs or personalization surfaces a provider can contribute to. */
enum class ProviderMediaType { WALLPAPER, VIDEO, SOUND }

/**
 * User actions a provider may expose. Item-level licensing and runtime state can
 * still narrow this upper bound for an individual result.
 */
enum class ProviderAction {
    BROWSE,
    SEARCH,
    PREVIEW,
    FAVORITE,
    DOWNLOAD,
    APPLY,
    EDIT,
    SHARE,
    IMPORT,
    GENERATE,
    UPLOAD,
    VOTE,
    REPORT,
    OPEN_SOURCE,
    VIEW_SAVED,
    REMOVE_SAVED,
    BUNDLE,
}

/**
 * The single source of truth for what a [ContentSource] is and is allowed to do.
 *
 * Before this existed, the same facts were spelled out independently in the
 * disclosure list, the runtime-control list, the network-endpoint manifest, the
 * Settings toggles, and the repositories — so a source could read "dormant" in
 * one place while a repository still fetched it, and a build/channel could ship
 * a provider its own policy forbade. `ProviderCapabilityContractTest` fails the
 * build when any of those disagree with this registry.
 */
data class ProviderCapability(
    val source: ContentSource,
    /** Media types this provider can supply or modify. */
    val mediaTypes: Set<ProviderMediaType>,
    /** Lower values appear earlier in each media feed. */
    val defaultPriority: Map<ProviderMediaType, Int>,
    /** Provider-level action ceiling before per-item policy is applied. */
    val permittedActions: Set<ProviderAction>,
    val lifecycle: ProviderLifecycle,
    /** Builds this source can operate in. FOSS drops the Firebase-backed ones. */
    val builds: Set<ProviderBuild>,
    /** Channels this source may ship enabled on. */
    val channels: Set<ProviderChannel>,
    val configuration: ProviderConfiguration,
    val permission: ProviderPermission,
    val health: ProviderHealth,
    /** True when items must carry provider attribution wherever they are shown. */
    val requiresAttribution: Boolean,
    /** Whether the source is on for a fresh install. */
    val enabledByDefault: Boolean,
    /**
     * Preference key that switches the source off, matching the `killSwitch`
     * field of its endpoints. Null when there is nothing to switch off.
     */
    val killSwitchKey: String?,
    /** Ids in `docs/security/network-endpoints.json` this source owns. */
    val endpointIds: Set<String>,
) {
    init {
        require(mediaTypes.isNotEmpty()) { "${source.name} must declare at least one media type" }
        require(defaultPriority.keys == mediaTypes) {
            "${source.name} priorities must cover exactly its declared media types"
        }
        require(defaultPriority.values.all { it >= 0 }) {
            "${source.name} priorities must be non-negative"
        }
        require(permittedActions.isNotEmpty()) { "${source.name} must permit at least one action" }
    }

    /** True when the source may originate a new network request. */
    val canFetch: Boolean
        get() = lifecycle != ProviderLifecycle.LEGACY && health == ProviderHealth.NETWORKED

    fun availableIn(build: ProviderBuild): Boolean = build in builds

    fun availableOn(channel: ProviderChannel): Boolean = channel in channels

    fun availableIn(build: ProviderBuild, channel: ProviderChannel): Boolean =
        availableIn(build) && availableOn(channel) && lifecycle != ProviderLifecycle.LEGACY

    fun priorityFor(mediaType: ProviderMediaType): Int =
        defaultPriority[mediaType] ?: Int.MAX_VALUE
}

private val ALL_BUILDS = setOf(ProviderBuild.FULL, ProviderBuild.FOSS)
private val FULL_ONLY = setOf(ProviderBuild.FULL)
private val ALL_CHANNELS = setOf(ProviderChannel.GITHUB, ProviderChannel.PLAY)
private val GITHUB_ONLY = setOf(ProviderChannel.GITHUB)

/**
 * Shorthand for the many sources that are dormant: no feed, no endpoints, no
 * switch, present only so saved records keep their provenance.
 */
private const val LEGACY_PRIORITY = 1_000

private val LEGACY_ACTIONS = setOf(
    ProviderAction.VIEW_SAVED,
    ProviderAction.REMOVE_SAVED,
    ProviderAction.OPEN_SOURCE,
)

private fun legacy(
    source: ContentSource,
    vararg mediaTypes: ProviderMediaType,
) = ProviderCapability(
    source = source,
    mediaTypes = mediaTypes.toSet(),
    defaultPriority = mediaTypes.associateWith { LEGACY_PRIORITY },
    permittedActions = LEGACY_ACTIONS,
    lifecycle = ProviderLifecycle.LEGACY,
    builds = ALL_BUILDS,
    channels = ALL_CHANNELS,
    configuration = ProviderConfiguration.NONE,
    permission = ProviderPermission.NONE,
    health = ProviderHealth.OFFLINE,
    requiresAttribution = true,
    enabledByDefault = false,
    killSwitchKey = null,
    endpointIds = emptySet(),
)

val providerCapabilities: List<ProviderCapability> = listOf(
    ProviderCapability(
        source = ContentSource.WALLHAVEN,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER),
        defaultPriority = mapOf(ProviderMediaType.WALLPAPER to 10),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.SEARCH,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.EDIT,
            ProviderAction.SHARE,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.OPTIONAL_KEY,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = "wallhaven_provider_enabled",
        endpointIds = setOf("wallhaven-api"),
    ),
    legacy(ContentSource.PICSUM, ProviderMediaType.WALLPAPER),
    ProviderCapability(
        source = ContentSource.BING,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER),
        defaultPriority = mapOf(ProviderMediaType.WALLPAPER to 40),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.EDIT,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = "bing_provider_enabled",
        endpointIds = setOf("bing-daily"),
    ),
    ProviderCapability(
        source = ContentSource.WIKIMEDIA,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER),
        defaultPriority = mapOf(ProviderMediaType.WALLPAPER to 50),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.EDIT,
            ProviderAction.SHARE,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = true,
        killSwitchKey = null,
        endpointIds = setOf("wikimedia-potd"),
    ),
    legacy(ContentSource.INTERNET_ARCHIVE, ProviderMediaType.SOUND),
    ProviderCapability(
        source = ContentSource.REDDIT,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER, ProviderMediaType.VIDEO),
        defaultPriority = mapOf(
            ProviderMediaType.WALLPAPER to 0,
            ProviderMediaType.VIDEO to 0,
        ),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.PREVIEW,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.SHARE,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = true,
        killSwitchKey = "reddit_provider_enabled",
        endpointIds = setOf("reddit-rss"),
    ),
    ProviderCapability(
        source = ContentSource.NASA,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER),
        defaultPriority = mapOf(ProviderMediaType.WALLPAPER to 60),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.EDIT,
            ProviderAction.SHARE,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = true,
        killSwitchKey = null,
        endpointIds = setOf("nasa-apod"),
    ),
    ProviderCapability(
        source = ContentSource.FREESOUND,
        mediaTypes = setOf(ProviderMediaType.SOUND),
        defaultPriority = mapOf(ProviderMediaType.SOUND to LEGACY_PRIORITY),
        permittedActions = LEGACY_ACTIONS,
        lifecycle = ProviderLifecycle.LEGACY,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.OFFLINE,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = null,
        endpointIds = setOf("freesound-v2", "openverse-audio"),
    ),
    legacy(ContentSource.JAMENDO, ProviderMediaType.SOUND),
    ProviderCapability(
        source = ContentSource.AUDIUS,
        mediaTypes = setOf(ProviderMediaType.SOUND),
        defaultPriority = mapOf(ProviderMediaType.SOUND to LEGACY_PRIORITY),
        permittedActions = LEGACY_ACTIONS,
        lifecycle = ProviderLifecycle.LEGACY,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.OFFLINE,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = null,
        endpointIds = setOf("audius-api"),
    ),
    ProviderCapability(
        source = ContentSource.CCMIXTER,
        mediaTypes = setOf(ProviderMediaType.SOUND),
        defaultPriority = mapOf(ProviderMediaType.SOUND to LEGACY_PRIORITY),
        permittedActions = LEGACY_ACTIONS,
        lifecycle = ProviderLifecycle.LEGACY,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.OFFLINE,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = null,
        endpointIds = setOf("ccmixter-api"),
    ),
    ProviderCapability(
        source = ContentSource.LOCAL,
        mediaTypes = setOf(
            ProviderMediaType.WALLPAPER,
            ProviderMediaType.VIDEO,
            ProviderMediaType.SOUND,
        ),
        defaultPriority = mapOf(
            ProviderMediaType.WALLPAPER to 90,
            ProviderMediaType.VIDEO to 90,
            ProviderMediaType.SOUND to 90,
        ),
        permittedActions = setOf(
            ProviderAction.IMPORT,
            ProviderAction.PREVIEW,
            ProviderAction.FAVORITE,
            ProviderAction.APPLY,
            ProviderAction.EDIT,
            ProviderAction.SHARE,
            ProviderAction.REMOVE_SAVED,
            ProviderAction.DOWNLOAD,
        ),
        lifecycle = ProviderLifecycle.LOCAL,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.MEDIA_ACCESS,
        health = ProviderHealth.OFFLINE,
        requiresAttribution = false,
        enabledByDefault = true,
        killSwitchKey = null,
        endpointIds = emptySet(),
    ),
    ProviderCapability(
        source = ContentSource.YOUTUBE,
        mediaTypes = setOf(ProviderMediaType.SOUND, ProviderMediaType.VIDEO),
        defaultPriority = mapOf(
            ProviderMediaType.SOUND to 0,
            ProviderMediaType.VIDEO to 10,
        ),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.SEARCH,
            ProviderAction.PREVIEW,
            ProviderAction.IMPORT,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.SHARE,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        // Aura's own YouTube risk profile keeps extraction off Play until the
        // owner records approval evidence; GitHub/Obtainium keeps the capability.
        channels = GITHUB_ONLY,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = true,
        killSwitchKey = "youtube_provider_enabled",
        endpointIds = setOf("youtube-newpipe", "youtube-pot-provider"),
    ),
    ProviderCapability(
        source = ContentSource.TIKTOK,
        mediaTypes = setOf(ProviderMediaType.SOUND),
        defaultPriority = mapOf(ProviderMediaType.SOUND to 5),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.PREVIEW,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.SHARE,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        // Same risk profile as YouTube extraction: GitHub/Obtainium only.
        channels = GITHUB_ONLY,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = true,
        killSwitchKey = null,
        endpointIds = setOf("tiktok-creator-embed", "tiktok-media-cdn"),
    ),
    ProviderCapability(
        source = ContentSource.PEXELS,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER, ProviderMediaType.VIDEO),
        defaultPriority = mapOf(
            ProviderMediaType.WALLPAPER to 20,
            ProviderMediaType.VIDEO to 20,
        ),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.SEARCH,
            ProviderAction.PREVIEW,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.EDIT,
            ProviderAction.SHARE,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.REQUIRED_KEY,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = "pexels_provider_enabled",
        endpointIds = setOf("pexels-api"),
    ),
    ProviderCapability(
        source = ContentSource.PIXABAY,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER, ProviderMediaType.VIDEO),
        defaultPriority = mapOf(
            ProviderMediaType.WALLPAPER to 30,
            ProviderMediaType.VIDEO to 30,
        ),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.SEARCH,
            ProviderAction.PREVIEW,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.EDIT,
            ProviderAction.SHARE,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.REQUIRED_KEY,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = "pixabay_provider_enabled",
        endpointIds = setOf("pixabay-api"),
    ),
    legacy(ContentSource.KLIPY, ProviderMediaType.VIDEO),
    ProviderCapability(
        source = ContentSource.SOUNDCLOUD,
        mediaTypes = setOf(ProviderMediaType.SOUND),
        defaultPriority = mapOf(ProviderMediaType.SOUND to LEGACY_PRIORITY),
        permittedActions = LEGACY_ACTIONS,
        lifecycle = ProviderLifecycle.LEGACY,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.OFFLINE,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = null,
        endpointIds = setOf("soundcloud-api"),
    ),
    ProviderCapability(
        source = ContentSource.COMMUNITY,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER, ProviderMediaType.SOUND),
        defaultPriority = mapOf(
            ProviderMediaType.WALLPAPER to 80,
            ProviderMediaType.SOUND to 20,
        ),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.PREVIEW,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.EDIT,
            ProviderAction.SHARE,
            ProviderAction.UPLOAD,
            ProviderAction.VOTE,
            ProviderAction.REPORT,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.COMMUNITY,
        // Firebase is compiled out of the FOSS flavor.
        builds = FULL_ONLY,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = "community_provider_enabled",
        endpointIds = setOf("firebase-community", "aura-collection-links"),
    ),
    ProviderCapability(
        source = ContentSource.BUNDLED,
        mediaTypes = setOf(ProviderMediaType.SOUND),
        defaultPriority = mapOf(ProviderMediaType.SOUND to 10),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.PREVIEW,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.EDIT,
            ProviderAction.SHARE,
            ProviderAction.BUNDLE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.OFFLINE,
        requiresAttribution = true,
        enabledByDefault = true,
        killSwitchKey = null,
        endpointIds = emptySet(),
    ),
    ProviderCapability(
        source = ContentSource.AI_GENERATED,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER),
        defaultPriority = mapOf(ProviderMediaType.WALLPAPER to 100),
        permittedActions = setOf(
            ProviderAction.GENERATE,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.EDIT,
            ProviderAction.SHARE,
        ),
        lifecycle = ProviderLifecycle.GENERATED,
        builds = FULL_ONLY,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.REQUIRED_KEY,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = "generated_content_provider_enabled",
        endpointIds = setOf("generated-wallpaper-api"),
    ),
    ProviderCapability(
        source = ContentSource.OPEN_METEO,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER),
        defaultPriority = mapOf(ProviderMediaType.WALLPAPER to 110),
        permittedActions = setOf(ProviderAction.APPLY),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.APPROXIMATE_LOCATION,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = false,
        killSwitchKey = "weather_effects_enabled",
        endpointIds = setOf("open-meteo-api"),
    ),
    ProviderCapability(
        source = ContentSource.LEMMY,
        mediaTypes = setOf(ProviderMediaType.WALLPAPER),
        defaultPriority = mapOf(ProviderMediaType.WALLPAPER to 70),
        permittedActions = setOf(
            ProviderAction.BROWSE,
            ProviderAction.FAVORITE,
            ProviderAction.DOWNLOAD,
            ProviderAction.APPLY,
            ProviderAction.SHARE,
            ProviderAction.OPEN_SOURCE,
        ),
        lifecycle = ProviderLifecycle.ACTIVE,
        builds = ALL_BUILDS,
        channels = ALL_CHANNELS,
        configuration = ProviderConfiguration.NONE,
        permission = ProviderPermission.NONE,
        health = ProviderHealth.NETWORKED,
        requiresAttribution = true,
        enabledByDefault = true,
        killSwitchKey = null,
        endpointIds = setOf("lemmy-wallpapers"),
    ),
)

/**
 * Ids in `docs/security/network-endpoints.json` that belong to the app itself, not to a content
 * source. The update check reads GitHub's release feed whichever sources are switched on.
 */
val appEndpointIds: Set<String> = setOf("aura-update-check")

val providerCapabilitiesBySource: Map<ContentSource, ProviderCapability> =
    providerCapabilities.associateBy { it.source }

/** The registry entry for [source]; every enum value has exactly one. */
fun providerCapability(source: ContentSource): ProviderCapability =
    providerCapabilitiesBySource.getValue(source)

/** Build and channel carried by the installed artifact, not a mutable user preference. */
val currentProviderBuild: ProviderBuild
    get() = if (BuildConfig.FOSS_BUILD) ProviderBuild.FOSS else ProviderBuild.FULL

val currentProviderChannel: ProviderChannel
    get() = providerChannel(BuildConfig.AURA_RELEASE_CHANNEL)

internal fun providerChannel(raw: String): ProviderChannel = when (raw.lowercase(Locale.ROOT)) {
    "github" -> ProviderChannel.GITHUB
    "play" -> ProviderChannel.PLAY
    else -> error("Unsupported provider release channel: $raw")
}

fun isProviderAvailableInCurrentArtifact(source: ContentSource): Boolean =
    providerCapability(source).availableIn(currentProviderBuild, currentProviderChannel)

fun isProviderActionPermitted(source: ContentSource, action: ProviderAction): Boolean =
    isProviderActionPermittedIn(
        source = source,
        action = action,
        build = currentProviderBuild,
        channel = currentProviderChannel,
    )

internal fun isProviderActionPermittedIn(
    source: ContentSource,
    action: ProviderAction,
    build: ProviderBuild,
    channel: ProviderChannel,
): Boolean {
    val capability = providerCapability(source)
    if (action !in capability.permittedActions) return false
    return capability.lifecycle == ProviderLifecycle.LEGACY || capability.availableIn(build, channel)
}

/** Providers offered for [mediaType], ordered exactly as their default feed policy. */
fun orderedProviderCapabilities(
    mediaType: ProviderMediaType,
    build: ProviderBuild,
    channel: ProviderChannel,
    includeLegacy: Boolean = false,
): List<ProviderCapability> = providerCapabilities
    .asSequence()
    .filter { mediaType in it.mediaTypes }
    .filter { it.availableIn(build) && it.availableOn(channel) }
    .filter { includeLegacy || it.lifecycle != ProviderLifecycle.LEGACY }
    .sortedWith(compareBy({ it.priorityFor(mediaType) }, { it.source.name }))
    .toList()

/** Provider order used by live feed ranking for the installed artifact. */
fun orderedCurrentProviderCapabilities(
    mediaType: ProviderMediaType,
    includeLegacy: Boolean = false,
): List<ProviderCapability> = orderedProviderCapabilities(
    mediaType = mediaType,
    build = currentProviderBuild,
    channel = currentProviderChannel,
    includeLegacy = includeLegacy,
)

/** Small quality-score bias derived from the manifest priority rather than a second source table. */
fun currentProviderPriorityBonus(source: ContentSource, mediaType: ProviderMediaType): Int {
    val capability = providerCapability(source)
    if (!capability.availableIn(currentProviderBuild, currentProviderChannel) || mediaType !in capability.mediaTypes) {
        return 0
    }
    return (30 - capability.priorityFor(mediaType)).coerceAtLeast(0)
}

/** Stable provider order for user-facing catalog and legal menus. */
val providerCatalogCapabilities: List<ProviderCapability> = providerCapabilities.sortedWith(
    compareBy<ProviderCapability>(
        { if (it.source == ContentSource.REDDIT) 0 else 1 },
        { if (it.lifecycle == ProviderLifecycle.LEGACY) 1 else 0 },
        { it.defaultPriority.values.minOrNull() ?: Int.MAX_VALUE },
        { it.source.name },
    ),
)

/**
 * Disclosure status implied by a lifecycle. Keeps `ProviderDisclosure.status`
 * from drifting away from what the runtime actually allows.
 */
fun ProviderLifecycle.disclosureStatus(): ProviderStatus = when (this) {
    ProviderLifecycle.ACTIVE -> ProviderStatus.ACTIVE
    ProviderLifecycle.LEGACY -> ProviderStatus.LEGACY
    ProviderLifecycle.LOCAL -> ProviderStatus.LOCAL
    ProviderLifecycle.COMMUNITY -> ProviderStatus.COMMUNITY
    ProviderLifecycle.GENERATED -> ProviderStatus.GENERATED
}
