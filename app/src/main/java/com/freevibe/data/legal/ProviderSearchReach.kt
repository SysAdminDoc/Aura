package com.freevibe.data.legal

import com.freevibe.data.model.ContentSource

/** Provider switches and API keys from Settings, read live by the search and source menus. */
data class ProviderSetup(
    /** A source missing from this map has no switch and counts as on. */
    val enabled: Map<ContentSource, Boolean> = emptyMap(),
    /** Sources with a user API key saved. */
    val keyed: Set<ContentSource> = emptySet(),
)

/** Why a source can't be used right now. */
enum class ProviderBlock { NOT_IN_BUILD, TURNED_OFF, NEEDS_KEY }

/** Null when [source] can be queried with this setup in the given build and channel. */
fun ProviderSetup.blockFor(
    source: ContentSource,
    build: ProviderBuild = currentProviderBuild,
    channel: ProviderChannel = currentProviderChannel,
): ProviderBlock? {
    val capability = providerCapability(source)
    return when {
        !capability.availableIn(build, channel) -> ProviderBlock.NOT_IN_BUILD
        enabled[source] == false -> ProviderBlock.TURNED_OFF
        capability.configuration == ProviderConfiguration.REQUIRED_KEY && source !in keyed -> ProviderBlock.NEEDS_KEY
        else -> null
    }
}

/**
 * Sources a typed search reaches for [mediaType], in feed order: the providers whose registry
 * entry permits [ProviderAction.SEARCH], after the local filters each feed applies to a query.
 * The video feed filters its Reddit page by title, and sound search matches Aura Originals by name.
 */
fun searchSourcesFor(
    mediaType: ProviderMediaType,
    build: ProviderBuild = currentProviderBuild,
    channel: ProviderChannel = currentProviderChannel,
): List<ContentSource> {
    val filtered = when (mediaType) {
        ProviderMediaType.WALLPAPER -> emptyList()
        ProviderMediaType.VIDEO -> listOf(ContentSource.REDDIT)
        ProviderMediaType.SOUND -> listOf(ContentSource.BUNDLED)
    }
    val searchable = orderedProviderCapabilities(mediaType, build, channel)
        .filter { ProviderAction.SEARCH in it.permittedActions }
        .map { it.source }
    return (filtered + searchable).distinct()
}

/** Sources that only add to a feed; on their own the feed drops them (`keepPexelsVideosAsEnhancement`). */
private val ENHANCEMENT_ONLY: Map<ProviderMediaType, Set<ContentSource>> = mapOf(
    ProviderMediaType.VIDEO to setOf(ContentSource.PEXELS),
)

data class SearchReach(
    /** What a search would query right now, in feed order. Empty when [block] is set. */
    val sources: List<ContentSource>,
    /** Why nothing would be queried; a switched-on source missing its key wins, since it's the one to fix. */
    val block: ProviderBlock?,
    /** True when every reachable source works offline. */
    val worksOffline: Boolean,
)

fun ProviderSetup.searchReach(
    mediaType: ProviderMediaType,
    online: Boolean = true,
    build: ProviderBuild = currentProviderBuild,
    channel: ProviderChannel = currentProviderChannel,
): SearchReach {
    val candidates = searchSourcesFor(mediaType, build, channel)
    val usable = candidates
        .filter { blockFor(it, build, channel) == null }
        .filter { online || providerCapability(it).health == ProviderHealth.OFFLINE }
    val enhancementOnly = ENHANCEMENT_ONLY[mediaType].orEmpty()
    if (usable.any { it !in enhancementOnly }) {
        return SearchReach(
            sources = usable,
            block = null,
            worksOffline = usable.all { providerCapability(it).health == ProviderHealth.OFFLINE },
        )
    }
    val blocks = candidates.mapNotNull { blockFor(it, build, channel) }
    return SearchReach(
        sources = emptyList(),
        block = if (ProviderBlock.NEEDS_KEY in blocks) ProviderBlock.NEEDS_KEY else ProviderBlock.TURNED_OFF,
        worksOffline = false,
    )
}

/** The provider's public name, as the disclosures list it. */
fun ContentSource.providerDisplayName(): String =
    providerDisclosuresBySource[this]?.displayName ?: name.lowercase().replaceFirstChar { it.titlecase() }
