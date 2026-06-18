package com.freevibe.ui.screens.fixtures

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.freevibe.ui.components.AuraScreenHeader
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.AuraStatusBanner
import com.freevibe.ui.components.CompactSearchField
import com.freevibe.ui.components.ShimmerSoundList
import com.freevibe.ui.components.ShimmerWallpaperGrid
import com.freevibe.ui.theme.FreeVibeTheme

enum class AuraRouteFixture(
    val screenshotName: String,
) {
    WallpapersGridSuccess("wallpapers_grid_success"),
    WallpapersOfflineEmpty("wallpapers_offline_empty"),
    SoundDetailReady("sound_detail_ready"),
    SettingsProviderDisabled("settings_provider_disabled"),
    VideoWallpapersError("video_wallpapers_error"),
    WallpaperEditorLoading("wallpaper_editor_loading"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuraRouteStateFixture(
    fixture: AuraRouteFixture,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (fixture) {
            AuraRouteFixture.WallpapersGridSuccess -> WallpapersGridFixture()
            AuraRouteFixture.WallpapersOfflineEmpty -> WallpapersOfflineFixture()
            AuraRouteFixture.SoundDetailReady -> SoundDetailFixture()
            AuraRouteFixture.SettingsProviderDisabled -> SettingsFixture()
            AuraRouteFixture.VideoWallpapersError -> VideoWallpapersFixture()
            AuraRouteFixture.WallpaperEditorLoading -> WallpaperEditorFixture()
        }
    }
}

@Composable
private fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun WallpapersGridFixture() {
    ScreenColumn {
        AuraScreenHeader(
            label = "Wallpapers",
            icon = Icons.Default.Wallpaper,
            title = "Fresh AMOLED picks",
            subtitle = "Local saves stay visible while provider results refresh.",
            tint = MaterialTheme.colorScheme.primary,
        ) {
            CompactSearchField(
                value = "neon forest",
                onValueChange = {},
                placeholder = "Search wallpapers",
                leadingIcon = Icons.Default.Search,
            )
        }
        AuraStatusBanner(
            icon = Icons.Default.CheckCircle,
            title = "Local cache ready",
            message = "18 downloaded wallpapers and 4 favorites are available offline.",
            tone = MaterialTheme.colorScheme.secondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FixtureWallpaperCard(
                        title = if (row == 0) "Aurora glass" else if (row == 1) "Copper dusk" else "Mist ridge",
                        source = if (row == 2) "Local" else "Wallhaven",
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    FixtureWallpaperCard(
                        title = if (row == 0) "Rain window" else if (row == 1) "Orbit linen" else "Noir bloom",
                        source = if (row == 1) "Pexels" else "Aura Picks",
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.88f),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpapersOfflineFixture() {
    ScreenColumn {
        AuraScreenHeader(
            label = "Wallpapers",
            icon = Icons.Default.Wallpaper,
            title = "Offline wallpaper search",
            subtitle = "Network providers pause without hiding local content.",
            tint = MaterialTheme.colorScheme.tertiary,
        ) {
            CompactSearchField(
                value = "",
                onValueChange = {},
                placeholder = "Search local saves",
                leadingIcon = Icons.Default.Search,
            )
        }
        AuraStatusBanner(
            icon = Icons.Default.CloudOff,
            title = "Provider results unavailable",
            message = "Wallhaven, Pexels, and Pixabay are disabled until the connection returns.",
            tone = MaterialTheme.colorScheme.tertiary,
        )
        AuraStateCard(
            icon = Icons.Default.Folder,
            title = "No local matches",
            description = "Downloads, favorites, and imported files will appear here when they match the search.",
            tone = MaterialTheme.colorScheme.secondary,
            primaryAction = AuraStateAction("Open downloads", Icons.Default.Download, {}),
            secondaryAction = AuraStateAction("Clear search", Icons.Default.Search, {}),
        )
        ShimmerWallpaperGrid(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FixtureWallpaperCard(
    title: String,
    source: String,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .aspectRatio(0.72f)
            .semantics { contentDescription = "$title from $source" },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors)),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.46f))
                    .padding(10.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = source,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SoundDetailFixture() {
    ScreenColumn {
        AuraScreenHeader(
            label = "Sound",
            icon = Icons.Default.LibraryMusic,
            title = "Midnight Pulse",
            subtitle = "Freesound - CC BY 4.0 - 28 seconds",
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            WaveformFixture()
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { 0.42f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Preview")
            }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Set tone")
            }
        }
        FixtureInfoGrid(
            items = listOf(
                "Format" to "MP3, 192 kbps",
                "Duration" to "00:28",
                "Source" to "Freesound",
                "License" to "Attribution required",
            ),
        )
        AuraStatusBanner(
            icon = Icons.Default.Info,
            title = "Playback cached",
            message = "Preview continues while browsing other sound tabs.",
            tone = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun WaveformFixture() {
    val bars = listOf(0.24f, 0.5f, 0.78f, 0.42f, 0.9f, 0.62f, 0.36f, 0.7f, 0.88f, 0.52f, 0.34f, 0.66f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        val gap = size.width / (bars.size * 2f)
        bars.forEachIndexed { index, heightRatio ->
            val x = gap + index * gap * 2f
            val barHeight = size.height * heightRatio
            drawLine(
                color = if (index < 5) Color(0xFF8EDCE6) else Color(0xFFE7BE63),
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SettingsFixture() {
    ScreenColumn {
        AuraScreenHeader(
            label = "Settings",
            icon = Icons.Default.Settings,
            title = "Local-first controls",
            subtitle = "Provider keys, automation, diagnostics, and privacy are grouped by task.",
            tint = MaterialTheme.colorScheme.primary,
        ) {
            AuraStatusBanner(
                icon = Icons.Default.Error,
                title = "Provider disabled",
                message = "Pixabay search is off until an API key is saved.",
                tone = MaterialTheme.colorScheme.tertiary,
            )
        }
        SettingsRowFixture(
            icon = Icons.Default.CloudOff,
            title = "Online providers",
            body = "Wallhaven on, Pexels off, Pixabay needs key",
            checked = false,
        )
        SettingsRowFixture(
            icon = Icons.Default.BatteryChargingFull,
            title = "Battery-aware rotation",
            body = "Pause live effects below 15 percent battery",
            checked = true,
        )
        SettingsRowFixture(
            icon = Icons.Default.DarkMode,
            title = "AMOLED defaults",
            body = "Prefer dark previews and high-contrast controls",
            checked = true,
        )
        FixtureInfoGrid(
            items = listOf(
                "Crash diagnostics" to "Local only",
                "Backups" to "Manual export",
                "Source metrics" to "On device",
                "Identity" to "Anonymous",
            ),
        )
    }
}

@Composable
private fun SettingsRowFixture(
    icon: ImageVector,
    title: String,
    body: String,
    checked: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun VideoWallpapersFixture() {
    ScreenColumn {
        AuraScreenHeader(
            label = "Videos",
            icon = Icons.Default.Movie,
            title = "Video wallpaper metadata",
            subtitle = "Stream dimensions are probed before apply decisions.",
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            CompactSearchField(
                value = "city loops",
                onValueChange = {},
                placeholder = "Search video wallpapers",
                leadingIcon = Icons.Default.Search,
            )
        }
        AuraStatusBanner(
            icon = Icons.Default.BrokenImage,
            title = "One source failed",
            message = "YouTube metadata timed out; local clips and Pexels results remain available.",
            tone = MaterialTheme.colorScheme.tertiary,
        )
        FixtureVideoCard()
        FixtureInfoGrid(
            items = listOf(
                "Resolution" to "1080 x 1920",
                "Rotation" to "90 degrees",
                "Codec" to "H.264",
                "Duration" to "00:14",
            ),
        )
    }
}

@Composable
private fun FixtureVideoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.78f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.42f)) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(14.dp).size(36.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Portrait rain loop", style = MaterialTheme.typography.titleMedium)
                Text("Ready for crop preview - source: Pexels", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallpaperEditorFixture() {
    ScreenColumn {
        AuraScreenHeader(
            label = "Editor",
            icon = Icons.Default.Palette,
            title = "Wallpaper editor recovery",
            subtitle = "Image loading, filter controls, and empty states remain deterministic.",
            tint = MaterialTheme.colorScheme.primary,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .background(Color.Black, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading full-resolution source", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolChipFixture(Icons.Default.Tune, "Brightness", true)
            ToolChipFixture(Icons.Default.ColorLens, "Saturation", false)
            ToolChipFixture(Icons.Default.FilterVintage, "Grain", false)
            ToolChipFixture(Icons.Default.AutoAwesome, "Amoled", false)
        }
        EditorSliderFixture("Brightness", 0.62f)
        EditorSliderFixture("Contrast", 0.48f)
        EditorSliderFixture("Blur", 0.22f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Preview")
            }
            Button(onClick = {}, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply")
            }
        }
    }
}

@Composable
private fun ToolChipFixture(
    icon: ImageVector,
    label: String,
    selected: Boolean,
) {
    FilterChip(
        selected = selected,
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun EditorSliderFixture(
    label: String,
    value: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = {})
    }
}

@Composable
private fun FixtureInfoGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (label, value) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(3.dp))
                            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Preview(name = "Wallpapers dark", widthDp = 411, heightDp = 891, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun WallpapersDarkPreview() {
    FreeVibeTheme(darkTheme = true) {
        AuraRouteStateFixture(AuraRouteFixture.WallpapersGridSuccess)
    }
}

@Preview(name = "Settings light", widthDp = 411, heightDp = 891)
@Composable
private fun SettingsLightPreview() {
    FreeVibeTheme(darkTheme = false) {
        AuraRouteStateFixture(AuraRouteFixture.SettingsProviderDisabled)
    }
}
