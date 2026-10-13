package com.freevibe.ui.qa

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.freevibe.R
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.LocalMediaStatus
import com.freevibe.data.model.ROTATION_MEDIA_VIDEO
import com.freevibe.data.model.ROTATION_MEDIA_WALLPAPER
import com.freevibe.data.model.RotationExclusionEntity
import com.freevibe.data.model.WALLPAPER_PRESENTATION_FIT
import com.freevibe.ui.components.AuraStatusAction
import com.freevibe.ui.components.AuraStatusBanner
import com.freevibe.ui.components.CompactSearchField
import com.freevibe.ui.components.FitCanvasControls
import com.freevibe.ui.components.FitCanvasMedia
import com.freevibe.ui.components.ShimmerWallpaperGrid
import com.freevibe.ui.preview.PREVIEW_SOUNDS
import com.freevibe.ui.preview.PREVIEW_WALLPAPERS
import com.freevibe.ui.screens.editor.WallpaperEditorPreview
import com.freevibe.ui.screens.downloads.DownloadHistoryCard
import com.freevibe.ui.screens.settings.SettingsMetric
import com.freevibe.ui.screens.settings.RotationExclusionsManagerDialog
import com.freevibe.ui.screens.settings.SettingsItem
import com.freevibe.ui.screens.settings.SettingsSection
import com.freevibe.ui.screens.settings.SettingsToggle
import com.freevibe.ui.screens.settings.SoundShufflePoolEditorPanel
import com.freevibe.service.SoundShuffleHistoryEntry
import com.freevibe.service.SoundShuffleHistoryStatus
import com.freevibe.service.SoundShuffleReason
import com.freevibe.service.SoundShufflePool
import com.freevibe.service.SoundShufflePoolItem
import com.freevibe.service.SoundShuffleSourceKind
import com.freevibe.service.SoundShuffleTarget
import com.freevibe.ui.screens.sounds.ApplyButton
import com.freevibe.ui.screens.sounds.DetailWaveform
import com.freevibe.ui.screens.videowallpapers.VideoCard
import com.freevibe.ui.screens.videowallpapers.VideoWallpaperItem
import com.freevibe.ui.screens.wallpapers.WallpaperGrid
import com.freevibe.ui.screens.wallpapers.WallpaperStateAction
import com.freevibe.ui.screens.wallpapers.WallpaperStateCard

/**
 * Release-build route scenarios used by screenshot and accessibility checks.
 *
 * Each branch is made from the same production renderers that the live screens
 * call. The scenario data is deterministic, but the UI is not a debug-only
 * drawing of the route.
 */
enum class ProductionRouteScenario(
    val screenshotName: String,
    @StringRes val assertionResource: Int,
) {
    WallpapersGridSuccess("wallpapers_grid_success", R.string.nav_wallpapers),
    WallpapersOfflineEmpty("wallpapers_offline_empty", R.string.wallpapers_empty_default_title),
    SoundDetailReady("sound_detail_ready", R.string.nav_sounds),
    SettingsProviderDisabled("settings_provider_disabled", R.string.nav_settings),
    SettingsCredentialRecovery("settings_credential_recovery", R.string.settings_services_provider_key_reentry_title),
    VideoWallpapersError("video_wallpapers_error", R.string.nav_videos),
    WallpaperEditorLoading("wallpaper_editor_loading", R.string.editor_wallpaper_title),
    FitCanvasPreview("fit_canvas_preview", R.string.fit_canvas_controls_title),
    RotationExclusionsManager("rotation_exclusions_manager", R.string.settings_rotation_exclusions_title),
    DownloadsMediaCopies("downloads_media_copies", R.string.downloads_optimized_copy),
    DownloadsMissingMedia("downloads_missing_media", R.string.media_relink_action),
    SoundShufflePools("sound_shuffle_pools", R.string.sound_shuffle_pool_name),
}

@Composable
fun ProductionRouteState(
    scenario: ProductionRouteScenario,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (scenario) {
            ProductionRouteScenario.WallpapersGridSuccess -> WallpapersGridState()
            ProductionRouteScenario.WallpapersOfflineEmpty -> WallpapersOfflineState()
            ProductionRouteScenario.SoundDetailReady -> SoundDetailState()
            ProductionRouteScenario.SettingsProviderDisabled -> SettingsState()
            ProductionRouteScenario.SettingsCredentialRecovery -> SettingsCredentialRecoveryState()
            ProductionRouteScenario.VideoWallpapersError -> VideoWallpapersState()
            ProductionRouteScenario.WallpaperEditorLoading -> WallpaperEditorState()
            ProductionRouteScenario.FitCanvasPreview -> FitCanvasPreviewState()
            ProductionRouteScenario.RotationExclusionsManager -> RotationExclusionsManagerState()
            ProductionRouteScenario.DownloadsMediaCopies -> DownloadsMediaCopiesState()
            ProductionRouteScenario.DownloadsMissingMedia -> DownloadsMissingMediaState()
            ProductionRouteScenario.SoundShufflePools -> SoundShufflePoolsState()
        }
    }
}

@Composable
private fun RouteColumn(
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun WallpapersGridState() {
    Column(modifier = Modifier.fillMaxSize()) {
        RouteColumn(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nav_wallpapers), style = MaterialTheme.typography.headlineSmall)
            CompactSearchField(
                value = "",
                onValueChange = {},
                placeholder = stringResource(R.string.wallpapers_search_placeholder),
                leadingIcon = Icons.Default.Wallpaper,
            )
            AuraStatusBanner(
                icon = Icons.Default.CheckCircle,
                title = stringResource(R.string.settings_storage_section_title),
                message = stringResource(R.string.wallpapers_subtitle_search_default),
                tone = MaterialTheme.colorScheme.secondary,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            WallpaperGrid(
                wallpapers = PREVIEW_WALLPAPERS,
                isLoadingMore = false,
                columns = 2,
                onWallpaperClick = {},
                favoriteIdentities = emptySet(),
                onLoadMore = {},
            )
        }
    }
}

@Composable
private fun WallpapersOfflineState() {
    Column(modifier = Modifier.fillMaxSize()) {
        RouteColumn(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nav_wallpapers), style = MaterialTheme.typography.headlineSmall)
            AuraStatusBanner(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.search_provider_offline),
                message = stringResource(R.string.wallpapers_empty_default_description),
                tone = MaterialTheme.colorScheme.tertiary,
            )
            WallpaperStateCard(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.wallpapers_empty_default_title),
                description = stringResource(R.string.wallpapers_empty_default_description),
                primaryAction = WallpaperStateAction(
                    label = stringResource(R.string.wallpapers_empty_back_to_discover_action),
                    icon = Icons.Default.Download,
                    onClick = {},
                ),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ShimmerWallpaperGrid(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SoundDetailState() {
    val sound = PREVIEW_SOUNDS.first()
    RouteColumn {
        Text(stringResource(R.string.nav_sounds), style = MaterialTheme.typography.headlineSmall)
        Text(sound.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.sound_detail_by_creator, sound.uploaderName),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DetailWaveform(
            duration = sound.duration,
            isPlaying = true,
            progress = 0.42f,
            modifier = Modifier.fillMaxWidth().height(156.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ApplyButton(
                text = stringResource(R.string.editor_sound_apply_ringtone),
                icon = Icons.Default.PlayArrow,
                enabled = true,
                isLoading = false,
                modifier = Modifier.weight(1f),
                onClick = {},
            )
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.sound_detail_edit_sound_title))
            }
        }
        AuraStatusBanner(
            icon = Icons.Default.Info,
            title = stringResource(R.string.sound_detail_source_policy),
            message = stringResource(R.string.sound_detail_source_unavailable_body),
            tone = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsState() {
    RouteColumn {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall)
        SettingsSection(
            title = stringResource(R.string.settings_services_section_title),
            description = stringResource(R.string.settings_services_section_description),
        ) {
            SettingsToggle(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.settings_wp_reddit_title),
                subtitle = stringResource(R.string.settings_wp_reddit_off_subtitle),
                checked = false,
                onCheckedChange = {},
            )
            SettingsToggle(
                icon = Icons.Default.BatteryChargingFull,
                title = stringResource(R.string.settings_video_battery_saver_title),
                subtitle = stringResource(R.string.settings_video_battery_saver_on_subtitle),
                checked = true,
                onCheckedChange = {},
            )
        }
        SettingsSection(
            title = stringResource(R.string.settings_diagnostics_section_title),
            description = stringResource(R.string.settings_diagnostics_section_description),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsMetric(
                    label = stringResource(R.string.settings_diag_crash_title),
                    value = stringResource(R.string.settings_diagnostics_section_description),
                    icon = Icons.Default.Info,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                SettingsMetric(
                    label = stringResource(R.string.settings_diag_source_title),
                    value = stringResource(R.string.settings_diag_source_empty_subtitle),
                    icon = Icons.Default.Settings,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AuraStatusBanner(
            icon = Icons.Default.DarkMode,
            title = stringResource(R.string.settings_wp_night_variant_title),
            message = stringResource(R.string.settings_wp_night_variant_on_subtitle),
            tone = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsCredentialRecoveryState() {
    RouteColumn {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall)
        SettingsSection(
            title = stringResource(R.string.settings_services_section_title),
            description = stringResource(R.string.settings_services_section_description),
        ) {
            SettingsItem(
                icon = Icons.Default.Warning,
                title = stringResource(R.string.settings_services_provider_key_reentry_title),
                subtitle = stringResource(R.string.settings_services_provider_key_reentry_subtitle),
                onClick = {},
                subtitleMaxLines = 4,
            )
            SettingsItem(
                icon = Icons.Default.Key,
                title = stringResource(R.string.settings_services_wallhaven_key_title),
                subtitle = stringResource(R.string.settings_services_wallhaven_key_subtitle),
                onClick = {},
            )
        }
    }
}

@androidx.annotation.OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.media3.common.util.ExperimentalApi::class,
)
@Composable
private fun VideoWallpapersState() {
    val context = LocalContext.current
    val mediaSourceFactory = remember(context) { DefaultMediaSourceFactory(context) }
    RouteColumn {
        Text(stringResource(R.string.nav_videos), style = MaterialTheme.typography.headlineSmall)
        AuraStatusBanner(
            icon = Icons.Default.CloudOff,
            title = stringResource(R.string.video_wp_degraded_title),
            message = stringResource(R.string.video_wp_degraded_refresh),
            tone = MaterialTheme.colorScheme.tertiary,
            primaryAction = AuraStatusAction(
                label = stringResource(R.string.video_wp_degraded_gallery),
                icon = Icons.Default.Folder,
                onClick = {},
            ),
        )
        VideoCard(
            item = VideoWallpaperItem(
                id = "production-route-video",
                title = stringResource(R.string.video_wp_loading_title),
                thumbnailUrl = "",
                source = ContentSource.PIXABAY.name,
                duration = 14,
                videoWidth = 1080,
                videoHeight = 1920,
                contentSource = ContentSource.PIXABAY,
            ),
            streamUrl = null,
            mediaSourceFactory = mediaSourceFactory,
            shouldPreview = false,
            isApplying = false,
            onApply = {},
            onOpen = {},
        )
    }
}

@Composable
private fun WallpaperEditorState() {
    val editorBitmap = remember {
        Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    RouteColumn {
        Text(stringResource(R.string.editor_wallpaper_title), style = MaterialTheme.typography.headlineSmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(Color.Black, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            WallpaperEditorPreview(
                bitmap = editorBitmap,
                contentDescription = stringResource(R.string.editor_wallpaper_edited_cd),
                modifier = Modifier.fillMaxSize(),
                bitmapWidth = 720,
                bitmapHeight = 1280,
                overlays = emptyList(),
                selectedOverlayId = null,
                onSelectOverlay = {},
                onMoveOverlay = { _, _, _ -> },
                onResizeOverlay = { _, _ -> },
                onDeleteOverlay = {},
            )
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.editor_wallpaper_loading_image), color = Color.White)
                }
            }
        }
        Text(
            stringResource(R.string.editor_wallpaper_quality_warning_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FitCanvasPreviewState() {
    val previewBitmap = remember {
        Bitmap.createBitmap(900, 420, Bitmap.Config.ARGB_8888).apply {
            val canvas = android.graphics.Canvas(this)
            canvas.drawColor(android.graphics.Color.rgb(24, 32, 58))
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(105, 214, 186)
            }
            canvas.drawCircle(210f, 210f, 150f, paint)
            paint.color = android.graphics.Color.rgb(164, 110, 255)
            canvas.drawCircle(690f, 210f, 180f, paint)
        }
    }
    val style = FitCanvasStyle(FitCanvasMode.BLURRED_EDGE)
    RouteColumn {
        Text(stringResource(R.string.fit_canvas_controls_title), style = MaterialTheme.typography.headlineSmall)
        FitCanvasMedia(
            model = previewBitmap,
            presentation = WALLPAPER_PRESENTATION_FIT,
            style = style,
            dominantColor = android.graphics.Color.rgb(24, 32, 58),
            contentDescription = stringResource(R.string.preview_wallpaper_cd),
            modifier = Modifier
                .fillMaxWidth()
                .height(610.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black, RoundedCornerShape(8.dp)),
        )
        Text(
            stringResource(R.string.fit_canvas_fit_help),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FitCanvasControls(
            presentation = WALLPAPER_PRESENTATION_FIT,
            style = style,
            onPresentationChange = {},
            onStyleChange = {},
        )
    }
}

@Composable
private fun RotationExclusionsManagerState() {
    Box(Modifier.fillMaxSize()) {
        SettingsState()
        RotationExclusionsManagerDialog(
            exclusions = listOf(
                RotationExclusionEntity(
                    stableId = "WALLPAPER::REDDIT::aurora-lake",
                    mediaType = ROTATION_MEDIA_WALLPAPER,
                    source = "REDDIT",
                    contentId = "aurora-lake",
                    title = stringResource(R.string.wallpapers_header_curated),
                    excludedAt = 1_789_070_400_000,
                ),
                RotationExclusionEntity(
                    stableId = "VIDEO::YOUTUBE::forest-rain",
                    mediaType = ROTATION_MEDIA_VIDEO,
                    source = "YOUTUBE",
                    contentId = "forest-rain",
                    title = stringResource(R.string.nav_videos),
                    excludedAt = 1_788_984_000_000,
                ),
                RotationExclusionEntity(
                    stableId = "WALLPAPER::LOCAL_HASH::photo",
                    mediaType = ROTATION_MEDIA_WALLPAPER,
                    source = "LOCAL",
                    contentId = "photo",
                    title = stringResource(R.string.nav_library),
                    excludedAt = 1_788_897_600_000,
                ),
            ),
            onRestore = {},
            onRestoreAll = {},
            onDismiss = {},
        )
    }
}

@Composable
private fun DownloadsMediaCopiesState() {
    RouteColumn {
        Text(stringResource(R.string.downloads_title), style = MaterialTheme.typography.headlineSmall)
        DownloadHistoryCard(
            download = DownloadEntity(
                id = "video:reddit:aurora-loop",
                source = "REDDIT",
                type = "VIDEO",
                localPath = "/media_originals/aurora-loop.webm",
                name = "Aurora over the mountains",
                downloadedAt = 1_788_897_600_000,
                provenanceUrl = "https://www.reddit.com/r/EarthPorn/",
                originalSha256 = "original-sha256",
                originalMimeType = "video/webm",
                originalCodec = "AV1",
                originalWidth = 3_840,
                originalHeight = 2_160,
                originalDurationMs = 18_000,
                originalSizeBytes = 42L * 1_024L * 1_024L,
                originalHdr = true,
                optimizedPath = "/apply_copies/aurora-loop.mp4",
                optimizedSha256 = "optimized-sha256",
                optimizedMimeType = "video/mp4",
                optimizedCodec = "H264",
                optimizedWidth = 1_920,
                optimizedHeight = 1_080,
                optimizedDurationMs = 18_000,
                optimizedSizeBytes = 9L * 1_024L * 1_024L,
                optimizationReason = "Compatible H.264 MP4",
            ),
            onOpen = {},
            onDelete = {},
            onDeleteOptimized = {},
        )
        AuraStatusBanner(
            icon = Icons.Default.Info,
            title = stringResource(R.string.downloads_original),
            message = stringResource(R.string.downloads_optimized_deleted),
            tone = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun DownloadsMissingMediaState() {
    RouteColumn {
        Text(stringResource(R.string.downloads_title), style = MaterialTheme.typography.headlineSmall)
        AuraStatusBanner(
            icon = Icons.Default.Warning,
            title = stringResource(R.string.media_status_missing),
            message = stringResource(R.string.media_relink_missing_help),
            tone = MaterialTheme.colorScheme.error,
        )
        DownloadHistoryCard(
            download = DownloadEntity(
                id = "sound:youtube:morning-birds",
                source = "YOUTUBE",
                type = "SOUND",
                localPath = "content://media/external/audio/missing",
                name = "Morning birds in the forest",
                downloadedAt = 1_788_897_600_000,
                localMediaStatus = LocalMediaStatus.MISSING,
                localMediaReason = "Local file is missing",
                provenanceUrl = "https://www.youtube.com/watch?v=example",
                originalSha256 = "saved-original-sha256",
                originalMimeType = "audio/mp4",
                originalCodec = "AAC",
                originalDurationMs = 28_000,
                originalSizeBytes = 2L * 1_024L * 1_024L,
            ),
            broken = true,
            onOpen = {},
            onDelete = {},
            onRelink = {},
        )
    }
}

@Composable
private fun SoundShufflePoolsState() {
    val pool = SoundShufflePool(
        poolId = "preview-pool",
        name = "Morning mix",
        enabled = true,
        targets = SoundShuffleTarget.entries,
        intervalHours = 6,
        items = listOf(
            SoundShufflePoolItem(
                itemId = "bundled:preview_s1",
                sourceKind = SoundShuffleSourceKind.BUNDLED,
                sourceId = "preview_s1",
                name = "Morning Melody",
                locator = "rawresource:///1",
                mimeType = "audio/ogg",
                durationMs = 28_500,
            ),
            SoundShufflePoolItem(
                itemId = "local:missing",
                sourceKind = SoundShuffleSourceKind.LOCAL,
                sourceId = "missing",
                name = "Soft office ping",
                localMediaStatus = LocalMediaStatus.MISSING,
                durationMs = 1_200,
            ),
        ),
    )
    val originals = PREVIEW_SOUNDS.mapIndexed { index, sound ->
        sound.copy(
            id = "original-$index",
            source = ContentSource.BUNDLED,
            name = if (index == 0) "Crystal Chime" else "Soft Pop",
            duration = if (index == 0) 11.7 else 0.7,
            fileType = "audio/ogg",
        )
    }
    SoundShufflePoolEditorPanel(
        pool = pool,
        downloads = listOf(
            DownloadEntity(
                id = "download-1",
                source = "YOUTUBE",
                type = "SOUND",
                localPath = "content://media/audio/1",
                name = "Clean digital bell",
                originalMimeType = "audio/mp4",
                originalDurationMs = 8_400,
            ),
            DownloadEntity(
                id = "download-2",
                source = "YOUTUBE",
                type = "SOUND",
                localPath = "content://media/audio/2",
                name = "Ocean wake alarm",
                originalMimeType = "audio/mp4",
                originalDurationMs = 22_000,
            ),
        ),
        originals = originals,
        history = listOf(
            SoundShuffleHistoryEntry(
                historyId = "history-1",
                poolId = pool.poolId,
                poolName = pool.name,
                itemId = pool.items.first().itemId,
                itemName = pool.items.first().name,
                target = SoundShuffleTarget.RINGTONE,
                status = SoundShuffleHistoryStatus.APPLIED,
                appliedUri = "content://media/audio/applied",
                reason = SoundShuffleReason.APPLIED_AUTOMATICALLY.name,
            ),
        ),
        canWriteSettings = true,
        onOpenWriteSettings = {},
        onRename = {},
        onDelete = {},
        onEnabledChange = {},
        onTargetsChange = {},
        onIntervalChange = {},
        onRunNow = {},
        onAddLocal = {},
        onRelink = {},
        onRemove = {},
        onToggleDownload = { _, _ -> },
        onToggleOriginal = { _, _ -> },
        onUndo = {},
        modifier = Modifier.fillMaxSize(),
    )
}
