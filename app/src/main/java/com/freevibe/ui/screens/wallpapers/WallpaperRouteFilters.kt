package com.freevibe.ui.screens.wallpapers

import com.freevibe.data.model.ContentSource

/** Wallpapers route arguments, with blank values read as absent so equal routes compare equal. */
internal data class WallpaperRouteFilters(
    val query: String?,
    val color: String?,
    val similarId: String?,
    val similarSource: String?,
    val similarFullUrl: String?,
) {
    val resolvedSimilarSource: ContentSource?
        get() = similarSource?.let { name -> runCatching { ContentSource.valueOf(name) }.getOrNull() }

    companion object {
        fun of(
            query: String?,
            color: String?,
            similarId: String?,
            similarSource: String?,
            similarFullUrl: String?,
        ) = WallpaperRouteFilters(
            query = query?.ifBlank { null },
            color = color?.ifBlank { null },
            similarId = similarId?.ifBlank { null },
            similarSource = similarSource?.ifBlank { null },
            similarFullUrl = similarFullUrl?.ifBlank { null },
        )
    }
}
