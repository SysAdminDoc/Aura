package com.freevibe.ui.preview

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper

val PREVIEW_WALLPAPERS = listOf(
    Wallpaper(
        id = "preview_1",
        source = ContentSource.WALLHAVEN,
        thumbnailUrl = "",
        fullUrl = "",
        width = 2160,
        height = 3840,
        category = "general",
        tags = listOf("nature", "landscape", "mountains"),
        colors = listOf("#2C3E50", "#E74C3C", "#ECF0F1"),
        uploaderName = "photographer_1",
        views = 12345,
        favorites = 678,
    ),
    Wallpaper(
        id = "preview_2",
        source = ContentSource.NASA,
        thumbnailUrl = "",
        fullUrl = "",
        width = 4096,
        height = 2160,
        category = "astronomy",
        tags = listOf("nasa", "apod", "space", "nebula"),
        uploaderName = "NASA/ESA",
    ),
    Wallpaper(
        id = "preview_3",
        source = ContentSource.PEXELS,
        thumbnailUrl = "",
        fullUrl = "",
        width = 1080,
        height = 1920,
        category = "abstract",
        tags = listOf("amoled", "dark", "minimal"),
    ),
)

val PREVIEW_SOUNDS = listOf(
    Sound(
        id = "preview_s1",
        source = ContentSource.YOUTUBE,
        name = "Morning Melody Ringtone",
        description = "A gentle wake-up ringtone with piano and birds",
        previewUrl = "",
        downloadUrl = "",
        duration = 28.5,
        tags = listOf("ringtone", "morning", "gentle"),
        uploaderName = "SoundStudio",
    ),
    Sound(
        id = "preview_s2",
        source = ContentSource.COMMUNITY,
        name = "Notification Pop",
        previewUrl = "",
        downloadUrl = "",
        duration = 2.1,
        tags = listOf("notification", "pop", "short"),
        uploaderName = "community_user",
    ),
)
