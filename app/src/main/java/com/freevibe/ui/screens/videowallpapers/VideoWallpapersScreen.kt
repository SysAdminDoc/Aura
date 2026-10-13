package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImageContent
import com.freevibe.R
import com.freevibe.data.model.VideoProviderPolicyLinks
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.WALLPAPER_PRESENTATION_FILL
import com.freevibe.data.model.WALLPAPER_PRESENTATION_FIT
import com.freevibe.data.model.ROTATION_MEDIA_VIDEO
import com.freevibe.data.model.RotationExclusionIndex
import com.freevibe.data.model.VideoWallpaperAction
import com.freevibe.data.model.canUseVideoAction
import com.freevibe.data.model.requiresVideoActionConfirmation
import com.freevibe.data.model.videoActionMessage
import com.freevibe.data.model.videoWallpaperLicenseCapabilities
import com.freevibe.data.model.rotationIdentity
import com.freevibe.data.repository.matchesHiddenIds
import com.freevibe.service.MAX_VIDEO_WALLPAPER_BYTES
import com.freevibe.service.VIDEO_WALLPAPER_SCALE_MODE_FIT
import com.freevibe.service.VIDEO_WALLPAPER_SCALE_MODE_ZOOM
import com.freevibe.service.VideoWallpaperSelectionResult
import com.freevibe.service.VideoWallpaperService
import com.freevibe.service.copyStreamCapped
import com.freevibe.service.normalizeVideoWallpaperScaleMode
import com.freevibe.service.persistVideoWallpaperSelection
import com.freevibe.service.videoWallpaperMimeTypes
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.LoadMoreIndicator
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStatusAction
import com.freevibe.ui.components.AuraStatusBanner
import com.freevibe.ui.components.CommunityReportDialog
import com.freevibe.ui.components.CompactSearchField
import com.freevibe.ui.components.CountBadge
import com.freevibe.ui.components.FitCanvasControls
import com.freevibe.ui.components.FitCanvasMedia
import com.freevibe.ui.components.ShimmerBox
import com.freevibe.ui.LiveWallpaperLaunchMode
import com.freevibe.ui.launchLiveWallpaperPicker
import com.freevibe.ui.rotation.RotationExclusionsViewModel
import com.freevibe.ui.util.openExternalUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import com.freevibe.data.repository.HiddenItemKind
import com.freevibe.data.repository.HiddenItemLabel
import com.freevibe.data.repository.localHidesFor

@androidx.compose.runtime.Immutable
data class VideoWallpaperItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val source: String,
    val duration: Long = 0,
    val uploaderName: String = "",
    val videoId: String = "",
    val popularity: Long = 0, // Views (YouTube), upvotes (Reddit), or 0 (Pexels)
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoRotationDegrees: Int = 0,
    val videoMimeType: String = "",
    val videoCodec: String = "",
    val contentSource: com.freevibe.data.model.ContentSource = com.freevibe.data.model.ContentSource.LOCAL,
    val license: String = "",
    val sourcePageUrl: String = "",
) {
    val isPortrait: Boolean get() = videoHeight > videoWidth
    val isLandscape: Boolean get() = videoWidth > videoHeight
    val hasDimensions: Boolean get() = videoWidth > 0 && videoHeight > 0
}

private const val MIN_INITIAL_VIDEO_RESULTS = 24

enum class OrientationFilter { ALL, PORTRAIT, LANDSCAPE }

@androidx.compose.runtime.Immutable
data class VideoWallpapersState(
    val items: List<VideoWallpaperItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val isApplying: String? = null,
    /** Videos whose Download is still running. */
    val downloadingIds: Set<String> = emptySet(),
    val error: String? = null,
    val searchQuery: String = "",
    val pexelsPage: Int = 1,
    val pixabayPage: Int = 1,
    val ytQueryIndex: Int = 0,
    val redditSubIndex: Int = 0,
    val redditAfters: Map<String, String?> = emptyMap(),
    val hasMore: Boolean = true,
    val emptyLoadCount: Int = 0,
    val orientation: OrientationFilter = OrientationFilter.PORTRAIT,
    val focusFilter: VideoFocusFilter = VideoFocusFilter.BEST,
    val degradedSources: List<String> = emptyList(),
)

internal fun persistSelectedVideoWallpaper(
    context: Context,
    file: File,
    scaleMode: String = VIDEO_WALLPAPER_SCALE_MODE_ZOOM,
    canvasStyle: FitCanvasStyle = FitCanvasStyle(),
) {
    persistVideoWallpaperSelection(
        context = context,
        file = file,
        scaleMode = scaleMode,
        canvasStyle = canvasStyle,
        canvasBackgroundFile = File(context.filesDir, com.freevibe.service.VIDEO_WALLPAPER_CANVAS_BACKGROUND_FILE),
        canvasBaked = file.name == "live_wallpaper.fit.mp4",
    )
}

internal suspend fun exportVideoToGallery(context: Context, file: File): Uri? = withContext(Dispatchers.IO) {
    var insertedUri: Uri? = null
    try {
        if (file.length() > MAX_VIDEO_WALLPAPER_BYTES) {
            throw java.io.IOException("Video exceeds size limit")
        }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, "Aura_Wallpaper_${System.currentTimeMillis()}.mp4")
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/Aura")
            put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        insertedUri = uri
        uri?.let { destUri ->
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                file.inputStream().use { input ->
                    copyStreamCapped(input, out, MAX_VIDEO_WALLPAPER_BYTES)
                }
            }
            context.contentResolver.update(
                destUri,
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                },
                null,
                null,
            )
        }
        uri
    } catch (e: Exception) {
        // Don't leave an orphaned 0-byte row in the user's gallery.
        insertedUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Failed to export video fallback", e)
        null
    }
}

internal suspend fun launchOrExportVideoWallpaper(
    context: Context,
    file: File,
    isCropped: Boolean = false,
    scaleMode: String = VIDEO_WALLPAPER_SCALE_MODE_ZOOM,
    canvasStyle: FitCanvasStyle = FitCanvasStyle(),
    selectionAlreadyPersisted: Boolean = false,
) {
    if (!selectionAlreadyPersisted) {
        persistSelectedVideoWallpaper(context, file, scaleMode, canvasStyle)
    }
    when (
        withContext(Dispatchers.Main) {
            launchLiveWallpaperPicker(
                context = context,
                serviceComponent = android.content.ComponentName(context, VideoWallpaperService::class.java),
                tag = "VideoWallpaper",
            )
        }
    ) {
        LiveWallpaperLaunchMode.DIRECT -> {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.settings_feedback_video_direct), Toast.LENGTH_LONG).show()
            }
        }
        LiveWallpaperLaunchMode.CHOOSER -> {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.settings_feedback_video_chooser), Toast.LENGTH_LONG).show()
            }
        }
        null -> {
            val savedUri = exportVideoToGallery(context, file)
            withContext(Dispatchers.Main) {
                val message = if (savedUri != null) {
                    context.getString(
                        if (isCropped) R.string.video_wp_cropped_saved_manual else R.string.video_wp_saved_manual,
                    )
                } else {
                    context.getString(
                        if (isCropped) R.string.video_wp_cropped_ready_manual else R.string.settings_feedback_video_manual,
                    )
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

// ── UI ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoWallpapersScreen(
    initialQuery: String? = null,
    onManageHiddenClick: () -> Unit = {},
    viewModel: VideoWallpapersViewModel = hiltViewModel(),
    rotationExclusionsViewModel: RotationExclusionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gallerySelectionResult by viewModel.gallerySelectionResult.collectAsStateWithLifecycle()
    val resolvedIds by viewModel.resolvedIds.collectAsStateWithLifecycle()
    val savedVideoPresentation by viewModel.videoWallpaperPresentation.collectAsStateWithLifecycle()
    val savedVideoCanvasMode by viewModel.videoFitCanvasMode.collectAsStateWithLifecycle()
    val savedVideoCanvasColor by viewModel.videoFitCanvasColor.collectAsStateWithLifecycle()
    val hiddenIds by viewModel.voteRepo.hiddenIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val localHiddenIds by viewModel.voteRepo.localHiddenIds.collectAsStateWithLifecycle()
    val rotationExclusions by rotationExclusionsViewModel.exclusions.collectAsStateWithLifecycle()
    val rotationExclusionIndex = remember(rotationExclusions) { RotationExclusionIndex(rotationExclusions) }
    val itemIds = remember(state.items) { state.items.map { it.id } }
    val voteCounts by remember(itemIds) {
        if (itemIds.isNotEmpty()) viewModel.voteRepo.getVoteCounts(itemIds)
        else kotlinx.coroutines.flow.flowOf(emptyMap())
    }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val context = LocalContext.current
    // Reading a string off LocalContext is not a composition read. LocalResources is.
    val resources = LocalResources.current
    var confirmItem by remember { mutableStateOf<VideoWallpaperItem?>(null) }
    var downloadConfirmItem by remember { mutableStateOf<VideoWallpaperItem?>(null) }
    var reportItem by remember { mutableStateOf<VideoWallpaperItem?>(null) }
    val requestDownload: (VideoWallpaperItem) -> Unit = { item ->
        // Provider terms come first when the license policy asks for them, as they do for Apply.
        if (item.itemActionAvailability().downloadNeedsConfirmation) downloadConfirmItem = item else viewModel.downloadVideo(item)
    }
    val shareSource: (VideoWallpaperItem) -> Unit = { item ->
        if (!context.shareVideoSource(item)) {
            Toast.makeText(context, resources.getString(R.string.common_share_failed), Toast.LENGTH_SHORT).show()
        }
    }
    var cropItem by remember { mutableStateOf<Pair<VideoWallpaperItem, String>?>(null) }
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val visibleItems = remember(state.items, hiddenIds) {
        state.items.filterNot { isVideoWallpaperHidden(it, hiddenIds) }
    }
    var immersiveVideoIndex by rememberSaveable { mutableIntStateOf(-1) }
    var immersiveVideoItems by remember { mutableStateOf<List<VideoWallpaperItem>>(emptyList()) }
    val previewMediaSourceFactory = remember(viewModel) { viewModel.previewMediaSourceFactory() }
    var showOrientationMenu by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }
    val videoFilterCount = remember(state.focusFilter) {
        if (state.focusFilter != VideoFocusFilter.BEST) 1 else 0
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.prepareGalleryVideoWallpaper(it) }
    }

    // Video crop editor
    cropItem?.let { (item, streamUrl) ->
        VideoCropScreen(
            videoUrl = streamUrl,
            videoTitle = item.title,
            onBack = { cropItem = null },
            onCropped = { croppedFile ->
                cropItem = null
                scope.launch {
                    launchOrExportVideoWallpaper(appContext, croppedFile, isCropped = true)
                }
            },
        )
        return
    }

    var searchQuery by rememberSaveable(state.searchQuery) { mutableStateOf(state.searchQuery) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    val videoNewestQuery = stringResource(R.string.browse_rail_video_newest_query)
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(initialQuery) {
        val routeQuery = initialQuery?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (routeQuery != state.searchQuery) {
            searchQuery = routeQuery
            viewModel.search(routeQuery)
        }
    }
    LaunchedEffect(state.error, state.items.isNotEmpty()) {
        if (state.items.isNotEmpty()) {
            state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
        }
    }
    LaunchedEffect(visibleItems.size, state.hasMore, state.isLoading, state.isLoadingMore, state.isRefreshing) {
        if (
            visibleItems.isNotEmpty() &&
            visibleItems.size < MIN_INITIAL_VIDEO_RESULTS &&
            state.hasMore &&
            !state.isLoading &&
            !state.isLoadingMore &&
            !state.isRefreshing
        ) {
            viewModel.loadMore()
        }
    }
    LaunchedEffect(gallerySelectionResult) {
        when (val result = gallerySelectionResult) {
            VideoWallpaperSelectionResult.Ready -> {
                when (
                    launchLiveWallpaperPicker(
                        context = context,
                        serviceComponent = android.content.ComponentName(context, VideoWallpaperService::class.java),
                        tag = "VideoWallpaperGallery",
                    )
                ) {
                    LiveWallpaperLaunchMode.DIRECT -> {
                        Toast.makeText(context, resources.getString(R.string.video_wp_toast_direct), Toast.LENGTH_LONG).show()
                    }
                    LiveWallpaperLaunchMode.CHOOSER -> {
                        Toast.makeText(context, resources.getString(R.string.video_wp_toast_chooser), Toast.LENGTH_LONG).show()
                    }
                    null -> {
                        Toast.makeText(context, resources.getString(R.string.video_wp_toast_fallback), Toast.LENGTH_LONG).show()
                    }
                }
                viewModel.clearGallerySelectionResult()
            }
            is VideoWallpaperSelectionResult.Failure -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                viewModel.clearGallerySelectionResult()
            }
            else -> Unit
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
    Column(Modifier.fillMaxSize().padding(scaffoldPadding)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nav_videos),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                IconButton(onClick = { searchExpanded = !searchExpanded }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.video_wp_search_placeholder))
                }
                Box {
                    IconButton(
                        onClick = { showOrientationMenu = true },
                    ) {
                        Icon(
                            when (state.orientation) {
                                OrientationFilter.PORTRAIT -> Icons.Default.CropPortrait
                                OrientationFilter.LANDSCAPE -> Icons.Default.CropLandscape
                                OrientationFilter.ALL -> Icons.Default.CropFree
                            },
                            contentDescription = orientationLabel(state.orientation),
                        )
                    }
                    DropdownMenu(
                        expanded = showOrientationMenu,
                        onDismissRequest = { showOrientationMenu = false },
                    ) {
                        OrientationFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(orientationLabel(filter)) },
                                onClick = {
                                    showOrientationMenu = false
                                    viewModel.setOrientation(filter)
                                },
                                leadingIcon = {
                                    if (state.orientation == filter) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { showFiltersSheet = true }) {
                    Box {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.video_wp_filters_label))
                        CountBadge(
                            count = videoFilterCount,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 9.dp, y = (-7).dp),
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showQuickMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.sounds_mode_more))
                    }
                    DropdownMenu(
                        expanded = showQuickMenu,
                        onDismissRequest = { showQuickMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_rail_popular)) },
                            leadingIcon = { Icon(Icons.Default.Explore, contentDescription = null) },
                            onClick = {
                                showQuickMenu = false
                                searchQuery = ""
                                viewModel.search("")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_rail_newest)) },
                            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                            onClick = {
                                showQuickMenu = false
                                searchQuery = videoNewestQuery
                                viewModel.search(videoNewestQuery)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_rail_local)) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            onClick = {
                                showQuickMenu = false
                                galleryLauncher.launch(videoWallpaperMimeTypes())
                            },
                        )
                    }
                }
            }

            if (searchExpanded || state.searchQuery.isNotBlank()) {
                CompactSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.video_wp_search_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                    onClear = {
                        searchQuery = ""
                        viewModel.search("")
                        focusManager.clearFocus()
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.search(searchQuery)
                        focusManager.clearFocus()
                    }),
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        if (state.degradedSources.isNotEmpty()) {
            AuraStatusBanner(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.video_wp_degraded_title),
                message = videoSourceHealthSummary(resources, state.degradedSources),
                tone = MaterialTheme.colorScheme.tertiary,
                primaryAction = AuraStatusAction(
                    label = stringResource(R.string.video_wp_degraded_refresh),
                    icon = Icons.Default.Refresh,
                    onClick = { viewModel.refresh() },
                ),
                secondaryAction = AuraStatusAction(
                    label = stringResource(R.string.video_wp_degraded_gallery),
                    icon = Icons.Default.FolderOpen,
                    onClick = { galleryLauncher.launch(videoWallpaperMimeTypes()) },
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                (state.isLoading || state.isRefreshing) && state.items.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.video_wp_loading_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        repeat(3) {
                            ShimmerBox(
                                modifier = Modifier.fillMaxWidth().height(228.dp),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }
                state.error != null && state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AuraStateCard(
                            icon = Icons.Default.ErrorOutline,
                            title = stringResource(R.string.video_wp_error_title),
                            description = state.error ?: stringResource(R.string.video_wp_error_description),
                            tone = MaterialTheme.colorScheme.error,
                            primaryAction = AuraStateAction(stringResource(R.string.video_wp_error_retry), Icons.Default.Refresh) { viewModel.refresh() },
                            secondaryAction = AuraStateAction(stringResource(R.string.video_wp_error_gallery), Icons.Default.FolderOpen) { galleryLauncher.launch(videoWallpaperMimeTypes()) },
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                state.items.isEmpty() -> {
                    val (icon, title, detail) = videoEmptyState(
                        resources = resources,
                        query = state.searchQuery,
                        orientation = state.orientation,
                    )
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AuraStateCard(
                            icon = icon,
                            title = title,
                            description = detail,
                            primaryAction = AuraStateAction(stringResource(R.string.video_wp_empty_retry), Icons.Default.Refresh) { viewModel.refresh() },
                            secondaryAction = AuraStateAction(stringResource(R.string.video_wp_error_gallery), Icons.Default.FolderOpen) { galleryLauncher.launch(videoWallpaperMimeTypes()) },
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                    ) {
                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()

                        // Infinite scroll
                        val shouldLoadMore by remember {
                            androidx.compose.runtime.derivedStateOf {
                                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible >= listState.layoutInfo.totalItemsCount - 3
                            }
                        }
                        LaunchedEffect(shouldLoadMore, visibleItems.size) {
                            if (shouldLoadMore && state.hasMore && !state.isLoadingMore) viewModel.loadMore()
                        }

                        val activePreviewId by remember(visibleItems, listState) {
                            androidx.compose.runtime.derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val viewportCenter =
                                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                                layoutInfo.visibleItemsInfo
                                    .mapNotNull { info ->
                                        visibleItems.getOrNull(info.index)?.id?.let { id ->
                                            id to abs((info.offset + (info.size / 2)) - viewportCenter)
                                        }
                                    }
                                    .minByOrNull { it.second }
                                    ?.first
                            }
                        }

                        if (visibleItems.isEmpty()) {
                            // Hides outlive a refresh and a restart, so this state says so and offers the way back.
                            // Moderator removals aren't the user's to undo, so with none of their own it only refreshes.
                            val restorable = remember(state.items, localHiddenIds) {
                                localHidesFor(state.items.map { it.id }, localHiddenIds)
                            }
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                AuraStateCard(
                                    icon = Icons.Default.VisibilityOff,
                                    title = stringResource(R.string.video_wp_all_hidden_title),
                                    description = stringResource(
                                        if (restorable.isEmpty()) R.string.video_wp_all_hidden_moderated_body else R.string.video_wp_all_hidden_body,
                                    ),
                                    primaryAction = if (restorable.isEmpty()) {
                                        AuraStateAction(stringResource(R.string.video_wp_empty_refresh), Icons.Default.Refresh) { viewModel.refresh() }
                                    } else {
                                        AuraStateAction(stringResource(R.string.video_wp_restore_hidden), Icons.Default.Visibility) { viewModel.restoreHidden(restorable) }
                                    },
                                    secondaryAction = if (restorable.isEmpty()) null else {
                                        AuraStateAction(stringResource(R.string.video_wp_manage_hidden), Icons.Default.VisibilityOff, onManageHiddenClick)
                                    },
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(visibleItems, key = { it.id }) { item ->
                                    val isResolved = item.id in resolvedIds
                                    val resolvedUrl = if (isResolved) viewModel.getStreamUrl(item.id) else null
                                    val rotationIdentity = item.rotationExclusionIdentity()
                                    val rotationExclusion = rotationExclusionIndex.find(rotationIdentity)
                                    VideoCard(
                                        item = item,
                                        streamUrl = resolvedUrl,
                                        mediaSourceFactory = previewMediaSourceFactory,
                                        shouldPreview = immersiveVideoIndex < 0 && item.id == activePreviewId,
                                        isApplying = state.isApplying == item.id,
                                        isDownloading = item.id in state.downloadingIds,
                                        onDownload = { requestDownload(item) },
                                        onShare = { shareSource(item) },
                                        onReport = { reportItem = item },
                                        voteCount = voteCounts[item.id] ?: 0,
                                        onApply = { confirmItem = item },
                                        onOpen = {
                                            immersiveVideoItems = visibleItems.toList()
                                            immersiveVideoIndex = immersiveVideoItems.indexOfFirst { it.id == item.id }
                                        },
                                        onUpvote = { viewModel.upvote(item.id) },
                                        onDownvote = {
                                            val hiddenId = item.id
                                            viewModel.downvote(
                                                hiddenId,
                                                HiddenItemLabel(HiddenItemKind.VIDEO, item.title, item.thumbnailUrl, item.source),
                                            )
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = resources.getString(R.string.community_item_hidden),
                                                    actionLabel = resources.getString(R.string.common_undo),
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) viewModel.undoDownvote(hiddenId)
                                            }
                                        },
                                        rotationExcluded = rotationExclusion != null,
                                        onToggleRotationExclusion = {
                                            scope.launch {
                                                if (rotationExclusion != null) {
                                                    rotationExclusionsViewModel.restoreNow(rotationExclusion.stableId)
                                                    snackbarHostState.showSnackbar(
                                                        resources.getString(R.string.rotation_restored_message, item.title),
                                                    )
                                                } else {
                                                    val exclusion = rotationExclusionsViewModel.exclude(rotationIdentity)
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = resources.getString(R.string.rotation_excluded_message, item.title),
                                                        actionLabel = resources.getString(R.string.common_undo),
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        rotationExclusionsViewModel.restoreNow(exclusion.stableId)
                                                    }
                                                }
                                            }
                                        },
                                    )
                                }
                                if (state.isLoadingMore) {
                                    item {
                                        LoadMoreIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(visibleItems, immersiveVideoIndex) {
        if (immersiveVideoIndex >= 0 && immersiveVideoItems.isNotEmpty()) {
            val knownIds = immersiveVideoItems.mapTo(hashSetOf()) { it.id }
            val additions = visibleItems.filterNot { it.id in knownIds }
            if (additions.isNotEmpty()) immersiveVideoItems = immersiveVideoItems + additions
        }
    }

    if (immersiveVideoIndex >= 0 && immersiveVideoItems.isNotEmpty()) {
        VideoImmersivePager(
            items = immersiveVideoItems,
            initialPage = immersiveVideoIndex.coerceIn(0, immersiveVideoItems.lastIndex),
            resolvedIds = resolvedIds,
            mediaSourceFactory = previewMediaSourceFactory,
            streamUrlFor = viewModel::getStreamUrl,
            onResolve = viewModel::ensureStreamResolved,
            onLoadMore = viewModel::loadMore,
            isLoadingMore = state.isLoadingMore,
            downloadingIds = state.downloadingIds,
            onDownload = requestDownload,
            onShare = shareSource,
            onReport = { reportItem = it },
            onApply = {
                immersiveVideoIndex = -1
                immersiveVideoItems = emptyList()
                confirmItem = it
            },
            onDismiss = {
                immersiveVideoIndex = -1
                immersiveVideoItems = emptyList()
            },
        )
    }

    // Applying overlay
    if (state.isApplying != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                // Consume touches: without this the scrim lets taps through to the
                // list beneath, allowing a second concurrent apply.
                .pointerInput(Unit) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.video_downloading), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.video_downloading_hint), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    // Confirmation dialog with crop option
    confirmItem?.let { item ->
        val streamUrl = viewModel.getStreamUrl(item.id)
        val needsCrop = item.hasDimensions && item.isLandscape
        val capabilities = remember(item) { item.videoWallpaperLicenseCapabilities() }
        val canApplyVideo = remember(item) { item.canUseVideoAction(VideoWallpaperAction.APPLY) }
        var selectedScaleMode by remember(item.id, savedVideoPresentation) {
            mutableStateOf(
                if (savedVideoPresentation == WALLPAPER_PRESENTATION_FIT) {
                    VIDEO_WALLPAPER_SCALE_MODE_FIT
                } else {
                    VIDEO_WALLPAPER_SCALE_MODE_ZOOM
                },
            )
        }
        var selectedCanvasStyle by remember(item.id, savedVideoCanvasMode, savedVideoCanvasColor) {
            mutableStateOf(
                FitCanvasStyle(
                    FitCanvasMode.fromPreference(savedVideoCanvasMode),
                    savedVideoCanvasColor,
                ).normalized(),
            )
        }
        AlertDialog(
            onDismissRequest = { confirmItem = null },
            title = { Text(stringResource(R.string.video_wallpaper_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        localizedVideoSummary(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(
                            R.string.video_wp_badge_summary,
                            localizedLoopBadge(item),
                            localizedBatteryBadge(item),
                            localizedFitBadge(item, state.orientation),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    FitCanvasMedia(
                        model = item.thumbnailUrl,
                        presentation = if (selectedScaleMode == VIDEO_WALLPAPER_SCALE_MODE_FIT) {
                            WALLPAPER_PRESENTATION_FIT
                        } else {
                            WALLPAPER_PRESENTATION_FILL
                        },
                        style = selectedCanvasStyle,
                        dominantColor = null,
                        contentDescription = stringResource(R.string.a11y_video_preview_ready),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.height(12.dp))
                    VideoProvenanceBlock(
                        item = item,
                        normalizedLicense = capabilities.normalizedLicense,
                        policyLinks = capabilities.providerPolicyLinks,
                        onOpenUrl = { url -> openExternalUrl(context, url) },
                    )
                    if (item.requiresVideoActionConfirmation(VideoWallpaperAction.APPLY)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.videoActionMessage(VideoWallpaperAction.APPLY),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.video_wp_confirm_presentation),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    FitCanvasControls(
                        presentation = if (selectedScaleMode == VIDEO_WALLPAPER_SCALE_MODE_FIT) {
                            WALLPAPER_PRESENTATION_FIT
                        } else {
                            WALLPAPER_PRESENTATION_FILL
                        },
                        style = selectedCanvasStyle,
                        onPresentationChange = { presentation ->
                            selectedScaleMode = if (presentation == WALLPAPER_PRESENTATION_FIT) {
                                VIDEO_WALLPAPER_SCALE_MODE_FIT
                            } else {
                                VIDEO_WALLPAPER_SCALE_MODE_ZOOM
                            }
                            viewModel.setVideoFitCanvasPreferences(presentation, selectedCanvasStyle)
                        },
                        onStyleChange = { style ->
                            selectedCanvasStyle = style
                            viewModel.setVideoFitCanvasPreferences(
                                if (selectedScaleMode == VIDEO_WALLPAPER_SCALE_MODE_FIT) {
                                    WALLPAPER_PRESENTATION_FIT
                                } else {
                                    WALLPAPER_PRESENTATION_FILL
                                },
                                style,
                            )
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    if (needsCrop) {
                        Text(
                            if (selectedScaleMode == VIDEO_WALLPAPER_SCALE_MODE_FIT) {
                                stringResource(R.string.video_wp_confirm_fit_landscape_letterbox)
                            } else {
                                stringResource(R.string.video_wp_confirm_fill_landscape_crop)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedScaleMode == VIDEO_WALLPAPER_SCALE_MODE_FIT) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    } else {
                        Text(
                            stringResource(R.string.video_wp_confirm_fit_portrait),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (streamUrl != null && canApplyVideo) {
                        if (needsCrop) {
                            OutlinedButton(onClick = { viewModel.applyVideoWallpaper(item, selectedScaleMode, selectedCanvasStyle); confirmItem = null }) { Text(stringResource(R.string.video_apply)) }
                            Button(onClick = {
                                confirmItem = null
                                cropItem = item to streamUrl
                            }) {
                                Icon(Icons.Default.Crop, stringResource(R.string.video_crop), Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.video_crop))
                            }
                        } else {
                            OutlinedButton(onClick = {
                                confirmItem = null
                                cropItem = item to streamUrl
                            }) {
                                Icon(Icons.Default.Crop, stringResource(R.string.video_crop), Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.video_crop))
                            }
                            Button(onClick = { viewModel.applyVideoWallpaper(item, selectedScaleMode, selectedCanvasStyle); confirmItem = null }) { Text(stringResource(R.string.video_apply)) }
                        }
                    } else if (canApplyVideo) {
                        Button(onClick = { viewModel.applyVideoWallpaper(item, selectedScaleMode, selectedCanvasStyle); confirmItem = null }) { Text(stringResource(R.string.video_apply)) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmItem = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // Provider terms before a download, with the same source and rights links Apply shows.
    downloadConfirmItem?.let { item ->
        val capabilities = remember(item) { item.videoWallpaperLicenseCapabilities() }
        AlertDialog(
            onDismissRequest = { downloadConfirmItem = null },
            title = { Text(stringResource(R.string.video_wp_download_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    VideoProvenanceBlock(
                        item = item,
                        normalizedLicense = capabilities.normalizedLicense,
                        policyLinks = capabilities.providerPolicyLinks,
                        onOpenUrl = { url -> openExternalUrl(context, url) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.videoActionMessage(VideoWallpaperAction.DOWNLOAD),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    downloadConfirmItem = null
                    viewModel.downloadVideo(item)
                }) { Text(stringResource(R.string.video_wp_download)) }
            },
            dismissButton = { TextButton(onClick = { downloadConfirmItem = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    reportItem?.let { item ->
        CommunityReportDialog(
            title = stringResource(R.string.video_wp_report),
            onDismiss = { reportItem = null },
            onSubmit = { reason, note -> viewModel.reportVideo(item, reason, note) },
        )
    }
    } // end Scaffold

    if (showFiltersSheet) {
        ModalBottomSheet(onDismissRequest = { showFiltersSheet = false }) {
            VideoFiltersSheet(
                focusFilter = state.focusFilter,
                onSelectFocus = { filter ->
                    viewModel.setFocusFilter(filter)
                    showFiltersSheet = false
                },
                onQuickSearch = { query ->
                    viewModel.search(query)
                    showFiltersSheet = false
                },
                onReset = if (videoFilterCount > 0) {
                    {
                        viewModel.setFocusFilter(VideoFocusFilter.BEST)
                        showFiltersSheet = false
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun localizedVideoSummary(item: VideoWallpaperItem): String {
    val format = if (item.hasDimensions) {
        stringResource(
            R.string.video_wp_technical_dimensions,
            item.videoWidth,
            item.videoHeight,
            stringResource(
                if (item.isPortrait) R.string.video_wp_badge_portrait else R.string.video_wp_badge_landscape,
            ),
        )
    } else {
        stringResource(R.string.video_wp_technical_adaptive_stream)
    }
    val duration = item.duration.takeIf { it > 0 }
        ?.let { stringResource(R.string.video_wp_duration_seconds, it) }
    return listOfNotNull(format, duration).joinToString(" · ")
}

@Composable
private fun localizedLoopBadge(item: VideoWallpaperItem): String = stringResource(
    if (item.isLoopFriendly()) R.string.video_wp_badge_loop_safe else R.string.video_wp_badge_dynamic,
)

@Composable
private fun localizedBatteryBadge(item: VideoWallpaperItem): String = stringResource(
    when (item.batteryTier()) {
        BatteryTier.LOW -> R.string.video_wp_badge_low_battery
        BatteryTier.MEDIUM -> R.string.video_wp_badge_balanced
        BatteryTier.HIGH -> R.string.video_wp_badge_high_motion
    },
)

@Composable
private fun localizedFitBadge(item: VideoWallpaperItem, orientation: OrientationFilter): String = stringResource(
    when {
        !item.hasDimensions -> R.string.video_wp_badge_flexible
        orientation == OrientationFilter.PORTRAIT && item.isPortrait -> R.string.video_wp_badge_phone_fit
        orientation == OrientationFilter.LANDSCAPE && item.isLandscape -> R.string.video_wp_badge_wide_fit
        orientation == OrientationFilter.ALL && item.isPortrait -> R.string.video_wp_badge_phone_fit
        else -> R.string.video_wp_badge_needs_crop
    },
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoImmersivePager(
    items: List<VideoWallpaperItem>,
    initialPage: Int,
    resolvedIds: Set<String>,
    mediaSourceFactory: MediaSource.Factory,
    streamUrlFor: (String) -> String?,
    onResolve: (VideoWallpaperItem) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    downloadingIds: Set<String>,
    onDownload: (VideoWallpaperItem) -> Unit,
    onShare: (VideoWallpaperItem) -> Unit,
    onReport: (VideoWallpaperItem) -> Unit,
    onApply: (VideoWallpaperItem) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val pagerState = rememberPagerState(initialPage = initialPage) { items.size }
        LaunchedEffect(pagerState.settledPage, items.size) {
            items.getOrNull(pagerState.settledPage)?.let(onResolve)
            items.getOrNull(pagerState.settledPage + 1)?.let(onResolve)
            if (pagerState.settledPage >= items.lastIndex - 2) onLoadMore()
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { page -> items[page].id },
            ) { page ->
                val item = items[page]
                ImmersiveVideoPage(
                    item = item,
                    streamUrl = if (item.id in resolvedIds) streamUrlFor(item.id) else null,
                    isActive = page == pagerState.currentPage,
                    mediaSourceFactory = mediaSourceFactory,
                    isDownloading = item.id in downloadingIds,
                    onDownload = { onDownload(item) },
                    onShare = { onShare(item) },
                    onReport = { onReport(item) },
                    onApply = { onApply(item) },
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(10.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(50)),
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White)
            }

            if (isLoadingMore) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(18.dp)
                        .size(24.dp)
                        .align(Alignment.BottomCenter),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

// Media3 1.11 reclassified experimentalSetDynamicSchedulingEnabled from UnstableApi
// to ExperimentalApi, so opting into UnstableApi alone no longer covers it.
@androidx.annotation.OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.media3.common.util.ExperimentalApi::class,
)
@Composable
private fun ImmersiveVideoPage(
    item: VideoWallpaperItem,
    streamUrl: String?,
    isActive: Boolean,
    mediaSourceFactory: MediaSource.Factory,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onApply: () -> Unit,
) {
    val context = LocalContext.current
    // Reading a string off LocalContext is not a composition read. LocalResources is.
    val resources = LocalResources.current
    val immersivePreviewDescription = stringResource(R.string.a11y_immersive_video_preview, item.title)
    val isAnimatedStream = streamUrl?.isAnimatedImageStream() == true
    var isBuffering by remember(item.id, streamUrl) { mutableStateOf(streamUrl != null && !isAnimatedStream) }
    var playbackFailed by remember(item.id, streamUrl) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = immersivePreviewDescription },
    ) {
        if (streamUrl != null && isActive) {
            if (isAnimatedStream) {
                coil3.compose.SubcomposeAsyncImage(
                    model = streamUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (painter.state.collectAsState().value) {
                        is coil3.compose.AsyncImagePainter.State.Loading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { VideoPreviewLoadingIndicator() }
                        is coil3.compose.AsyncImagePainter.State.Error -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { VideoPreviewUnavailableIndicator() }
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            } else {
                val player = remember(item.id, streamUrl) {
                    ExoPlayer.Builder(context)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .experimentalSetDynamicSchedulingEnabled(true)
                        .build()
                        .apply {
                            setMediaItem(
                                MediaItem.Builder()
                                    .setUri(streamUrl)
                                    .setCustomCacheKey(item.id)
                                    .build(),
                            )
                            repeatMode = Player.REPEAT_MODE_ALL
                            setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                            volume = 0f
                            prepare()
                            play()
                        }
                }
                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            isBuffering = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            isBuffering = false
                            playbackFailed = true
                        }
                    }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                        player.release()
                    }
                }
                AndroidView(
                    factory = { context ->
                        androidx.media3.ui.PlayerView(context).apply {
                            this.player = player
                            useController = false
                            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            // Let the codec crop into fixed view bounds. PlayerView zoom
                            // resizes its SurfaceView after format discovery, which can
                            // exhaust Qualcomm BufferQueues on large aspect-ratio changes.
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_ALWAYS)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            coil3.compose.AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isActive && streamUrl == null) {
            VideoPreviewLoadingIndicator(Modifier.align(Alignment.Center))
        } else if (isActive && playbackFailed) {
            VideoPreviewUnavailableIndicator(Modifier.align(Alignment.Center))
        } else if (isActive && isBuffering) {
            VideoPreviewLoadingIndicator(Modifier.align(Alignment.Center))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)))),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // The creator stays next to the provider so credit is visible before any action.
                    listOfNotNull(
                        item.source,
                        item.uploaderName.takeIf(String::isNotBlank),
                        item.duration.takeIf { it > 0 }?.let { "${it}s" },
                    ).joinToString(" · "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Button(
                    onClick = onApply,
                    enabled = streamUrl != null && item.canUseVideoAction(VideoWallpaperAction.APPLY),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.video_apply))
                }
            }
            ImmersiveVideoActions(
                actions = remember(item) { item.itemActionAvailability() },
                isDownloading = isDownloading,
                onDownload = onDownload,
                onShare = onShare,
                onReport = onReport,
            )
        }
    }
}

@Composable
private fun VideoPreviewLoadingIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.video_wp_preparing_preview),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun VideoPreviewUnavailableIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.White)
            Text(
                text = stringResource(R.string.video_wp_preview_unavailable),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@androidx.annotation.OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.media3.common.util.ExperimentalApi::class,
)
@Composable
internal fun VideoCard(
    item: VideoWallpaperItem,
    streamUrl: String?,
    mediaSourceFactory: MediaSource.Factory,
    shouldPreview: Boolean,
    isApplying: Boolean,
    voteCount: Int = 0,
    onApply: () -> Unit,
    onOpen: () -> Unit,
    onUpvote: () -> Unit = {},
    onDownvote: () -> Unit = {},
    isDownloading: Boolean = false,
    onDownload: () -> Unit = {},
    onShare: () -> Unit = {},
    onReport: () -> Unit = {},
    rotationExcluded: Boolean = false,
    onToggleRotationExclusion: () -> Unit = {},
) {
    val context = LocalContext.current
    // Reading a string off LocalContext is not a composition read. LocalResources is.
    val resources = LocalResources.current
    val feedPreviewHeight = 260.dp
    var showItemActions by remember { mutableStateOf(false) }
    val showUploader = item.uploaderName.isNotBlank() &&
        !item.title.contains(item.uploaderName, ignoreCase = true)
    val videoStateDescription = when {
        isApplying -> stringResource(R.string.a11y_video_applying)
        shouldPreview && streamUrl != null -> stringResource(R.string.a11y_video_preview_playing)
        streamUrl == null -> stringResource(R.string.a11y_video_preview_loading)
        else -> stringResource(R.string.a11y_video_preview_ready)
    }
    val upvoteVideoLabel = stringResource(R.string.a11y_upvote_video_wallpaper)
    val hideVideoLabel = stringResource(R.string.a11y_hide_video_wallpaper)
    val openVideoPreviewLabel = stringResource(R.string.a11y_open_video_preview, item.title)
    val applyVideoLabel = stringResource(R.string.a11y_apply_video_wallpaper)
    val quickActionsLabel = stringResource(R.string.a11y_show_quick_actions)
    val applyingLabel = stringResource(R.string.a11y_applying)
    val readyLabel = stringResource(R.string.a11y_ready)
    val voteStateDescription = stringResource(R.string.a11y_vote_count, voteCount)
    val canApplyVideo = remember(item) { item.canUseVideoAction(VideoWallpaperAction.APPLY) }
    val itemActions = remember(item) { item.itemActionAvailability() }
    val unavailableLabel = stringResource(R.string.video_wp_unavailable)
    val applyBlockedMessage = remember(item, unavailableLabel) { item.videoActionMessage(VideoWallpaperAction.APPLY).ifBlank { unavailableLabel } }

    Card(
        modifier = Modifier.semantics {
            contentDescription = item.title
            stateDescription = videoStateDescription
        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            // ExoPlayer video or loading placeholder
            if (streamUrl != null && shouldPreview && streamUrl.isAnimatedImageStream()) {
                coil3.compose.AsyncImage(
                    model = streamUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(feedPreviewHeight)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                )
            } else if (streamUrl != null && shouldPreview) {
                val exoPlayer = remember(streamUrl) {
                    ExoPlayer.Builder(context)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .experimentalSetDynamicSchedulingEnabled(true)
                        .build().apply {
                        setMediaItem(
                            MediaItem.Builder()
                                .setUri(streamUrl)
                                .setCustomCacheKey(item.id)
                                .build(),
                        )
                        repeatMode = Player.REPEAT_MODE_ALL
                        setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        volume = 0f
                        prepare()
                        play()
                    }
                }

                DisposableEffect(exoPlayer) {
                    onDispose { exoPlayer.release() }
                }

                AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(feedPreviewHeight)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                )
            } else {
                // Static thumbnail for non-focused cards; show spinner only while unresolved
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(feedPreviewHeight)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    coil3.compose.AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (streamUrl == null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                            }
                        }
                    } else {
                        Surface(
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClickLabel = openVideoPreviewLabel, onClick = onOpen)
                    .semantics { contentDescription = openVideoPreviewLabel },
            )

            if (item.duration > 0) {
                Text(
                    text = stringResource(R.string.video_wp_duration_seconds, item.duration),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.46f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }

        // Identity and primary controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showUploader) {
                    Text(
                        item.uploaderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { showItemActions = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = quickActionsLabel)
                }
                DropdownMenu(
                    expanded = showItemActions,
                    onDismissRequest = { showItemActions = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(if (voteCount > 0) "$upvoteVideoLabel ($voteCount)" else upvoteVideoLabel)
                        },
                        leadingIcon = { Icon(Icons.Default.ThumbUp, contentDescription = null) },
                        onClick = {
                            showItemActions = false
                            onUpvote()
                        },
                        modifier = Modifier.semantics { stateDescription = voteStateDescription },
                    )
                    if (itemActions.canDownload) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (isDownloading) R.string.video_wp_downloading else R.string.video_wp_download)) },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            enabled = !isDownloading,
                            onClick = {
                                showItemActions = false
                                onDownload()
                            },
                        )
                    }
                    if (itemActions.canShare) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.video_wp_share_source)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showItemActions = false
                                onShare()
                            },
                        )
                    }
                    if (itemActions.canReport) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.video_wp_report)) },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                            onClick = {
                                showItemActions = false
                                onReport()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(hideVideoLabel) },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                        onClick = {
                            showItemActions = false
                            onDownvote()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (rotationExcluded) R.string.rotation_restore_action else R.string.rotation_exclude_action,
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (rotationExcluded) Icons.Default.Restore else Icons.Default.PlaylistRemove,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showItemActions = false
                            onToggleRotationExclusion()
                        },
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onApply,
                enabled = !isApplying && canApplyVideo,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        stateDescription = when {
                            isApplying -> applyingLabel
                            !canApplyVideo -> applyBlockedMessage
                            else -> readyLabel
                        }
                        onClick(label = applyVideoLabel, action = null)
                    },
            ) {
                if (isApplying) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Wallpaper, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.video_apply))
                }
            }
        }
    }
}

internal fun VideoWallpaperItem.rotationExclusionIdentity() = rotationIdentity(
    mediaType = ROTATION_MEDIA_VIDEO,
    source = contentSource.name,
    contentId = videoId.ifBlank { id },
    title = title,
    thumbnailUrl = thumbnailUrl,
)

private fun String.isAnimatedImageStream(): Boolean =
    substringBefore('?').endsWith(".gif", ignoreCase = true)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun VideoProvenanceBlock(
    item: VideoWallpaperItem,
    normalizedLicense: String,
    policyLinks: VideoProviderPolicyLinks,
    onOpenUrl: (String) -> Unit,
) {
    Text(
        stringResource(R.string.video_wp_provenance_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        listOf(item.source, normalizedLicense)
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (item.uploaderName.isNotBlank()) {
        Text(
            item.uploaderName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(8.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VideoPolicyLinkButton(stringResource(R.string.video_wp_provenance_source), Icons.Default.Link, item.sourcePageUrl, onOpenUrl)
        VideoPolicyLinkButton(stringResource(R.string.video_wp_provenance_terms), Icons.Default.Policy, policyLinks.termsUrl, onOpenUrl)
        VideoPolicyLinkButton(stringResource(R.string.video_wp_provenance_report), Icons.Default.Report, policyLinks.reportUrl, onOpenUrl)
        VideoPolicyLinkButton(stringResource(R.string.video_wp_provenance_rights), Icons.Default.Info, policyLinks.takedownUrl, onOpenUrl)
    }
}

@Composable
private fun VideoPolicyLinkButton(
    label: String,
    icon: ImageVector,
    url: String,
    onOpenUrl: (String) -> Unit,
) {
    if (url.isBlank()) return
    OutlinedButton(
        onClick = { onOpenUrl(url) },
        modifier = Modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun videoEmptyState(
    resources: Resources,
    query: String,
    orientation: OrientationFilter,
): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String> {
    val orientationName = resources.getString(orientation.labelRes())
    return when {
        query.isNotBlank() -> Triple(
            Icons.Default.SearchOff,
            resources.getString(R.string.video_wp_empty_search_title, query),
            resources.getString(R.string.video_wp_empty_search_body, orientationName),
        )
        else -> Triple(
            Icons.Default.VideoLibrary,
            resources.getString(R.string.video_wp_empty_title),
            resources.getString(R.string.video_wp_empty_body, orientationName),
        )
    }
}

@StringRes
private fun OrientationFilter.labelRes(): Int = when (this) {
    OrientationFilter.ALL -> R.string.video_wp_orientation_all
    OrientationFilter.PORTRAIT -> R.string.video_wp_badge_portrait
    OrientationFilter.LANDSCAPE -> R.string.video_wp_badge_landscape
}

@Composable
private fun orientationLabel(filter: OrientationFilter): String = stringResource(filter.labelRes())

@Composable
private fun videoFocusLabel(filter: VideoFocusFilter): String = stringResource(
    when (filter) {
        VideoFocusFilter.BEST -> R.string.video_wp_focus_best
        VideoFocusFilter.LOOP_SAFE -> R.string.video_wp_focus_loop_safe
        VideoFocusFilter.LOW_BATTERY -> R.string.video_wp_focus_low_battery
        VideoFocusFilter.PHONE_FIT -> R.string.video_wp_badge_phone_fit
    },
)

@Composable
private fun VideoFiltersSheet(
    focusFilter: VideoFocusFilter,
    onSelectFocus: (VideoFocusFilter) -> Unit,
    onQuickSearch: (String) -> Unit,
    onReset: (() -> Unit)?,
) {
    val categories = listOf(
        stringResource(R.string.video_wp_category_nature) to "nature calm loop",
        stringResource(R.string.video_wp_category_abstract) to "abstract particles loop",
        stringResource(R.string.video_wp_category_space) to "space galaxy stars loop",
        stringResource(R.string.video_wp_category_neon) to "neon lights glow loop",
        stringResource(R.string.video_wp_category_ocean) to "ocean waves water loop",
        stringResource(R.string.video_wp_category_fire) to "fire flames embers loop",
        stringResource(R.string.video_wp_category_cinemagraph) to "cinemagraph subtle motion",
        stringResource(R.string.video_wp_category_scifi) to "sci-fi futuristic loop",
        stringResource(R.string.video_wp_category_rain) to "rain drops window loop",
        stringResource(R.string.video_wp_category_clouds) to "clouds sky timelapse loop",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.video_refine_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.video_wp_filter_focus),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VideoFocusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = focusFilter == filter,
                    onClick = { onSelectFocus(filter) },
                    label = { Text(videoFocusLabel(filter)) },
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = if (focusFilter == filter) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }

        Text(
            stringResource(R.string.video_wp_filter_quick_searches),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { (label, query) ->
                AssistChip(
                    onClick = { onQuickSearch(query) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }

        onReset?.let {
            TextButton(onClick = it) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.video_reset_filters))
            }
        }
    }
}

private fun videoSourceHealthSummary(resources: Resources, degradedSources: List<String>): String =
    resources.getString(R.string.video_wp_degraded_message, degradedSources.sorted().joinToString(", "))

internal fun isVideoWallpaperHidden(
    item: VideoWallpaperItem,
    hiddenIds: Set<String>,
): Boolean = matchesHiddenIds(hiddenIds, item.id)
