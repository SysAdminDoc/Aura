package com.freevibe.ui.screens.wallpapers

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Resources
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.freevibe.ui.theme.AuraShapes
import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.model.COMMUNITY_REPORT_REASONS
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.GENERATED_CONTENT_REPORT_REASONS
import com.freevibe.data.model.RotationExclusionIndex
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperAction
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.isSourceUnavailable
import com.freevibe.data.model.stableKey
import com.freevibe.data.model.rotationIdentity
import com.freevibe.data.model.wallpaperLicenseCapabilities
import com.freevibe.service.ParallaxWallpaperService
import com.freevibe.ui.LiveWallpaperBlock
import com.freevibe.ui.LiveWallpaperLaunchMode
import com.freevibe.ui.LiveWallpaperLaunchResult
import com.freevibe.ui.LiveWallpaperSource
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.CommunityReportDialog
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.HighlightPill
import com.freevibe.ui.components.SourceBadge
import com.freevibe.ui.policy.CommunityUploadPolicyKind
import com.freevibe.ui.rotation.RotationExclusionsViewModel
import com.freevibe.ui.policy.COMMUNITY_REPORT_TAKEDOWN_COPY
import com.freevibe.ui.policy.communityBlockConfirmationCopy
import com.freevibe.ui.policy.communityOwnerDeleteConfirmationCopy
import com.freevibe.ui.launchLiveWallpaperPicker
import com.freevibe.ui.liveWallpaperBlock
import com.freevibe.ui.liveWallpaperBlockedMessage
import com.freevibe.ui.util.openExternalUrl
import com.freevibe.util.tryStartActivity
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.freevibe.data.repository.hiddenItemLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WallpaperDetailScreen(
    wallpaperId: String,
    fallbackWallpaper: com.freevibe.data.model.Wallpaper? = null,
    onBack: () -> Unit,
    onEdit: (com.freevibe.data.model.Wallpaper) -> Unit = {},
    onCrop: (com.freevibe.data.model.Wallpaper) -> Unit = {},
    onPreview: (com.freevibe.data.model.Wallpaper) -> Unit = {},
    onSearchTag: (String) -> Unit = {},
    onSearchColor: (String) -> Unit = {},
    onFindSimilar: (com.freevibe.data.model.Wallpaper) -> Unit = {},
    viewModel: WallpapersViewModel = hiltViewModel(),
    rotationExclusionsViewModel: RotationExclusionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedList by viewModel.sharedWallpaperList.collectAsStateWithLifecycle()
    val sharedListAnchorKey by viewModel.sharedWallpaperListAnchorKey.collectAsStateWithLifecycle()
    val hiddenIds by viewModel.hiddenIds.collectAsStateWithLifecycle()
    val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()
    val rotationExclusions by rotationExclusionsViewModel.exclusions.collectAsStateWithLifecycle()
    val rotationExclusionIndex = remember(rotationExclusions) { RotationExclusionIndex(rotationExclusions) }
    val targetSource = fallbackWallpaper?.source
    val targetFullUrl = fallbackWallpaper?.fullUrl
    val detailIdentityKey = remember(wallpaperId, targetSource, targetFullUrl) {
        listOf(
            wallpaperId,
            targetSource?.name.orEmpty(),
            targetFullUrl.orEmpty(),
        ).joinToString("|")
    }
    var restoreResolved by remember(detailIdentityKey) { mutableStateOf(false) }
    var resolvedWallpaper by remember(detailIdentityKey) {
        mutableStateOf(fallbackWallpaper)
    }

    LaunchedEffect(detailIdentityKey) {
        resolvedWallpaper = viewModel.resolveWallpaper(
            id = wallpaperId,
            source = targetSource,
            fullUrl = targetFullUrl,
        ) ?: fallbackWallpaper
        restoreResolved = true
    }

    val initialWp = resolvedWallpaper
    if (initialWp == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!restoreResolved) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(24.dp),
                ) {
                    HighlightPill(
                        label = stringResource(R.string.detail_loading_pill),
                        icon = Icons.Default.AutoAwesome,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.detail_loading_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.detail_loading_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            } else {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(24.dp),
                ) {
                    Surface(
                        shape = AuraShapes.Medium,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    ) {
                        Icon(
                            Icons.Default.BrokenImage,
                            null,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(28.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.detail_unavailable_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.detail_unavailable_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onBack, shape = AuraShapes.Medium) { Text(stringResource(R.string.detail_unavailable_action)) }
                }
            }
        }
        return
    }

    val pagerSeed = remember(initialWp, sharedList, sharedListAnchorKey, hiddenIds) {
        computeWallpaperPagerItems(
            currentWallpaper = initialWp,
            sharedWallpapers = sharedList,
            hiddenIds = hiddenIds,
            sharedListAnchorKey = sharedListAnchorKey,
        )
    }
    var wallpapers by remember(detailIdentityKey) { mutableStateOf(pagerSeed.wallpapers) }

    LaunchedEffect(pagerSeed.wallpapers, state.wallpapers, hiddenIds) {
        val retained = wallpapers.filterNot { isWallpaperHidden(it, hiddenIds) }
        val knownKeys = retained.mapTo(hashSetOf()) { it.stableKey() }
        val additions = (pagerSeed.wallpapers + state.wallpapers)
            .filterNot { isWallpaperHidden(it, hiddenIds) || it.stableKey() in knownKeys }
            .distinctBy { it.stableKey() }
        wallpapers = retained + additions
    }

    if (wallpapers.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // Track which wallpaper the pager is currently showing
    val initialPage = wallpapers.indexOfFirst { it.stableKey() == initialWp.stableKey() }
        .takeIf { it >= 0 }
        ?: pagerSeed.initialPage.coerceIn(0, wallpapers.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage) { wallpapers.size }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(detailIdentityKey) {
        if (wallpapers.isNotEmpty()) {
            pagerState.scrollToPage(initialPage.coerceIn(0, wallpapers.lastIndex))
        }
    }

    // Clamp page when list shrinks (e.g. downvote hides item near end)
    LaunchedEffect(wallpapers.size) {
        if (pagerState.currentPage >= wallpapers.size && wallpapers.isNotEmpty()) {
            pagerState.scrollToPage(wallpapers.size - 1)
        }
    }

    val currentWp = wallpapers.getOrNull(pagerState.currentPage) ?: wallpapers.firstOrNull() ?: return

    // The pager composes one page each side (beyondViewportPageCount), and each page's image request is
    // display-sized and cancelled when the page leaves composition. That's the whole lookahead: the
    // original-size prefetches this used to enqueue fetched the same URLs again and outlived fast swipes.
    LaunchedEffect(pagerState.settledPage) {
        wallpapers.getOrNull(pagerState.settledPage)?.let {
            viewModel.selectWallpaperOnly(it)
        }
        if (pagerState.settledPage >= wallpapers.size - 3) viewModel.loadMore()
    }
    val settledUrl = wallpapers.getOrNull(pagerState.settledPage)?.fullUrl
    LaunchedEffect(settledUrl) {
        settledUrl?.let(viewModel::extractColors)
    }

    // Use pager's current wallpaper for UI (not the reactive wp which causes reorder)
    val wp = currentWp
    val rotationIdentity = remember(wp) { wp.rotationIdentity() }
    val rotationExclusion = remember(rotationIdentity, rotationExclusions) {
        rotationExclusionIndex.find(rotationIdentity)
    }
    val sourceUnavailable = wp.isSourceUnavailable()
    val hints = remember(wp) { wp.qualityHints() }
    val actionCapabilities = remember(wp) { wp.wallpaperLicenseCapabilities() }
    val canApply = actionCapabilities.canUse(WallpaperAction.APPLY)
    val canDownload = actionCapabilities.canUse(WallpaperAction.DOWNLOAD)
    val canShare = actionCapabilities.canUse(WallpaperAction.SHARE)
    val canEdit = actionCapabilities.canUse(WallpaperAction.EDIT)

    val isFavorite by viewModel.isFavorite(wp).collectAsStateWithLifecycle(initialValue = false)
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val communityProviderEnabled by viewModel.communityProviderEnabled.collectAsStateWithLifecycle()
    val voteCountFlow = remember(wp.stableKey(), communityProviderEnabled) {
        if (communityProviderEnabled) viewModel.getVoteCount(wp.stableKey()) else flowOf(0)
    }
    val voteCount by voteCountFlow.collectAsStateWithLifecycle(initialValue = 0)

    var showApplyOptions by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    var showReportDialog by remember(wp.stableKey()) { mutableStateOf(false) }
    var showBlockCreatorDialog by remember(wp.stableKey()) { mutableStateOf(false) }
    var showDeleteUploadDialog by remember(wp.stableKey()) { mutableStateOf(false) }
    var showDetailsPanel by remember { mutableStateOf(false) }
    val detailsScrollState = rememberScrollState()
    val isGeneratedWallpaper = wp.source == ContentSource.AI_GENERATED
    val canReportWallpaper = wp.source != ContentSource.LOCAL
    var canDeleteUpload by remember(wp.stableKey(), communityProviderEnabled) { mutableStateOf(false) }
    LaunchedEffect(wp.stableKey(), communityProviderEnabled) {
        canDeleteUpload = if (communityProviderEnabled) viewModel.canDeleteCommunityWallpaper(wp) else false
    }
    val canBlockCreator = viewModel.canBlockCommunityWallpaper(wp) && !canDeleteUpload
    LaunchedEffect(wp.stableKey()) {
        showDetailsPanel = false
    }
    LaunchedEffect(showDetailsPanel) {
        if (showDetailsPanel) detailsScrollState.scrollTo(0)
    }

    val parallaxDirectMessage = stringResource(R.string.settings_feedback_parallax_direct)
    val parallaxChooserMessage = stringResource(R.string.settings_feedback_parallax_chooser)
    val staticApplyLabel = stringResource(R.string.live_wallpaper_static_action)
    val shareWallpaperTitle = stringResource(R.string.detail_share_wallpaper_title)
    val shareFailedMessage = stringResource(R.string.common_share_failed)
    val snackbarHostState = remember { SnackbarHostState() }
    val parallaxComponent = remember(context) { ComponentName(context, ParallaxWallpaperService::class.java) }
    // A picture that can't be a live wallpaper can still be an ordinary one, so the notice offers that.
    suspend fun offerStaticApply(block: LiveWallpaperBlock, wallpaper: Wallpaper) {
        val result = snackbarHostState.showSnackbar(
            message = liveWallpaperBlockedMessage(context, block, LiveWallpaperSource.IMAGE),
            actionLabel = staticApplyLabel,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.applyWallpaper(wallpaper, WallpaperTarget.BOTH)
    }
    LaunchedEffect(state.pendingLiveWallpaperLaunch) {
        if (state.pendingLiveWallpaperLaunch) {
            val launch = launchLiveWallpaperPicker(
                context = context,
                serviceComponent = parallaxComponent,
                tag = "ParallaxWallpaper",
            )
            when (launch) {
                is LiveWallpaperLaunchResult.Opened -> snackbarHostState.showSnackbar(
                    if (launch.mode == LiveWallpaperLaunchMode.DIRECT) parallaxDirectMessage else parallaxChooserMessage,
                )
                is LiveWallpaperLaunchResult.Blocked -> offerStaticApply(launch.reason, wp)
            }
            // Cleared last: the flag is this effect's key, so clearing it earlier would cancel the notice.
            viewModel.clearPendingLaunch()
        }
    }
    LaunchedEffect(state.applySuccess) {
        state.applySuccess?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccess()
        }
    }
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val compactOverlayBottomPadding = maxOf(8.dp, navigationBottomPadding / 4)
    val expandedOverlayBottomPadding = navigationBottomPadding + 14.dp

    Scaffold(snackbarHost = { AuraSnackbarHost(snackbarHostState) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Wallpaper pager
            if (wallpapers.size > 1) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { page -> wallpapers[page].stableKey() },
                ) { page ->
                    val pageUrl = wallpapers.getOrNull(page)?.fullUrl ?: wp.fullUrl
                    WallpaperImage(
                        url = pageUrl,
                        onLoaded = { viewModel.onWallpaperImageLoaded(pageUrl) },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val pageOffset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                                val scale = 1f + (pageOffset.absoluteValue * 0.15f).coerceAtMost(0.15f)
                                scaleX = scale; scaleY = scale
                                translationY = pageOffset * size.height * 0.06f
                                alpha = 1f - (pageOffset.absoluteValue * 0.3f).coerceAtMost(0.3f)
                            },
                    )
                }
            } else {
                WallpaperImage(
                    url = wp.fullUrl,
                    onLoaded = { viewModel.onWallpaperImageLoaded(wp.fullUrl) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 0.18f),
                                0.18f to Color.Transparent,
                                0.68f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.46f),
                            ),
                        ),
                    ),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailTopIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        onClick = onBack,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (wallpapers.size > 1) {
                            DetailOverlayPill(
                                label = stringResource(R.string.detail_pager_position, pagerState.currentPage + 1, wallpapers.size),
                            )
                        }
                    }
                }

                if (!showDetailsPanel) Spacer(Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (showDetailsPanel) Modifier.weight(1f) else Modifier)
                        .then(if (showDetailsPanel) Modifier.verticalScroll(detailsScrollState) else Modifier)
                        .padding(horizontal = 14.dp)
                        .padding(
                            top = 14.dp,
                            bottom = if (showDetailsPanel) expandedOverlayBottomPadding else compactOverlayBottomPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showDetailsPanel) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AuraShapes.Medium,
                        contentPadding = PaddingValues(18.dp),
                        highlightHeight = 84.dp,
                        shadowElevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SourceBadge(wp.source.name)
                                if (sourceUnavailable) {
                                    HighlightPill(
                                        label = stringResource(R.string.detail_source_unavailable),
                                        icon = Icons.Default.Warning,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                                if (voteCount > 0) {
                                    HighlightPill(
                                        label = stringResource(R.string.detail_like_count, formatCompactCount(voteCount)),
                                        icon = Icons.Default.ThumbUp,
                                        tint = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                            TextButton(
                                onClick = { showDetailsPanel = false },
                                shape = AuraShapes.Medium,
                            ) {
                                Text(stringResource(R.string.detail_show_image))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = wallpaperDetailTitle(resources, wp),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = wallpaperDetailSubtitle(resources, wp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DetailInfoChip(resources.wallpaperResolutionLabel(hints))
                            DetailInfoChip(stringResource(hints.orientation.labelRes))
                            if (wp.isAiGenerated == true) DetailInfoChip(stringResource(R.string.community_ai_generated_badge))
                            if (wp.license.isNotBlank()) DetailInfoChip(wp.license)
                            if (hints.isAmoled) DetailInfoChip(stringResource(R.string.detail_chip_amoled_friendly))
                            if (hints.isIconSafe) DetailInfoChip(stringResource(R.string.detail_chip_icon_safe))
                            if (wp.views > 0) DetailInfoChip(stringResource(R.string.detail_view_count, formatCompactCount(wp.views)))
                            if (wp.favorites > 0) DetailInfoChip(stringResource(R.string.detail_save_count, formatCompactCount(wp.favorites)))
                            formatFileTypeLabel(wp.fileType)?.let { DetailInfoChip(it) }
                            formatFileSizeLabel(wp.fileSize)?.let { DetailInfoChip(it) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { showApplyOptions = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                enabled = canApply && !state.isApplying,
                                shape = AuraShapes.Medium,
                            ) {
                                if (state.isApplying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Default.Wallpaper, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.detail_set_wallpaper))
                                }
                            }
                            FilledTonalButton(
                                onClick = { onPreview(wp) },
                                modifier = Modifier
                                    .weight(0.72f)
                                    .heightIn(min = 48.dp),
                                shape = AuraShapes.Medium,
                            ) {
                                Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.detail_preview))
                            }
                        }

                        if (wp.colors.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            DetailSectionTitle(stringResource(R.string.detail_palette_title))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                wp.colors.take(5).forEach { hex ->
                                    val colorContentDescription = stringResource(R.string.detail_search_color, hex)
                                    val colorInt = runCatching {
                                        android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
                                    }.getOrDefault(0)
                                    Surface(
                                        onClick = { onSearchColor(hex.removePrefix("#")) },
                                        color = Color(colorInt),
                                        shape = AuraShapes.Compact,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .semantics { contentDescription = colorContentDescription },
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                        content = {},
                                    )
                                }
                            }
                        }

                        // Material You extracted theme colors
                        val palette = colorPalette
                        if (palette != null) {
                            Spacer(Modifier.height(16.dp))
                            DetailSectionTitle(stringResource(R.string.detail_theme_colors_title))
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                maxItemsInEachRow = 4,
                            ) {
                                listOf(
                                    stringResource(R.string.detail_color_dominant) to palette.dominantColor,
                                    stringResource(R.string.detail_color_vibrant) to palette.vibrantColor,
                                    stringResource(R.string.detail_color_muted) to palette.mutedColor,
                                    stringResource(R.string.detail_color_accent) to palette.bestAccentColor,
                                    stringResource(R.string.detail_color_light) to palette.vibrantLight,
                                ).forEach { (label, colorInt) ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Surface(
                                            color = Color(colorInt),
                                            shape = AuraShapes.Medium,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                            modifier = Modifier.size(64.dp),
                                        ) {}
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            String.format("#%06X", colorInt and 0xFFFFFF),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else if (wp.colors.isNotEmpty()) {
                            // Show shimmer while colors extract
                            Spacer(Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                                        AuraShapes.Medium,
                                    ),
                            )
                        }

                        if (wp.tags.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            DetailSectionTitle(stringResource(R.string.detail_related_looks_title))
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                wp.tags.take(8).forEach { tag ->
                                    SuggestionChip(
                                        onClick = { onSearchTag(tag) },
                                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        border = null,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DetailActionPill(
                                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                label = if (isFavorite) stringResource(R.string.detail_saved) else stringResource(R.string.common_save),
                                tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                onClick = { viewModel.toggleFavorite(wp) },
                            )
                            if (canDownload) {
                                DetailActionPill(
                                    icon = Icons.Default.Download,
                                    label = stringResource(R.string.detail_download),
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = { viewModel.downloadWallpaper(wp) },
                                )
                            }
                            if (!sourceUnavailable) {
                                DetailActionPill(
                                    icon = Icons.Default.ImageSearch,
                                    label = stringResource(R.string.detail_similar),
                                    tint = MaterialTheme.colorScheme.secondary,
                                    onClick = { onFindSimilar(wp) },
                                )
                            }
                            if (canShare) {
                                DetailActionPill(
                                    icon = Icons.Default.Share,
                                    label = stringResource(R.string.common_share),
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        val shareUrl = if (sourceUnavailable) wp.fullUrl else wp.sourcePageUrl.ifEmpty { wp.fullUrl }
                                        if (shareUrl.isBlank()) return@DetailActionPill
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                                        }
                                        if (!context.tryStartActivity(Intent.createChooser(intent, shareWallpaperTitle), "wallpaper share")) {
                                            scope.launch { snackbarHostState.showSnackbar(shareFailedMessage) }
                                        }
                                    },
                                )
                            }
                            if (wp.sourcePageUrl.isNotBlank() && !sourceUnavailable) {
                                DetailActionPill(
                                    icon = Icons.Default.Link,
                                    label = stringResource(R.string.detail_source),
                                    tint = MaterialTheme.colorScheme.secondary,
                                    onClick = { openExternalUrl(context, wp.sourcePageUrl) },
                                )
                            }
                            if (communityProviderEnabled) {
                                DetailActionPill(
                                    icon = Icons.Default.ThumbUp,
                                    label = stringResource(R.string.detail_like),
                                    tint = MaterialTheme.colorScheme.secondary,
                                    onClick = { viewModel.upvote(wp.stableKey()) },
                                )
                                DetailActionPill(
                                    icon = Icons.Default.ThumbDown,
                                    label = stringResource(R.string.detail_hide),
                                    tint = MaterialTheme.colorScheme.error,
                                    onClick = { viewModel.downvote(wp.stableKey(), wp.hiddenItemLabel()) },
                                )
                            }
                            if (canReportWallpaper) {
                                DetailActionPill(
                                    icon = Icons.Default.Report,
                                    label = stringResource(R.string.detail_report),
                                    tint = MaterialTheme.colorScheme.error,
                                    onClick = { showReportDialog = true },
                                )
                            }
                            if (canBlockCreator) {
                                DetailActionPill(
                                    icon = Icons.Default.Block,
                                    label = stringResource(R.string.common_block),
                                    tint = MaterialTheme.colorScheme.error,
                                    onClick = { showBlockCreatorDialog = true },
                                )
                            }
                            DetailActionPill(
                                icon = Icons.Default.MoreHoriz,
                                label = stringResource(R.string.detail_more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { showMoreMenu = true },
                            )
                        }
                    }
                    } else {
                        CompactWallpaperOverlayCard(
                            isFavorite = isFavorite,
                            isApplying = state.isApplying,
                            canApply = canApply,
                            onApplyClick = { showApplyOptions = true },
                            onShowDetails = { showDetailsPanel = true },
                            onToggleFavorite = { viewModel.toggleFavorite(wp) },
                        )
                    }
                }
            }

            // Apply options sheet
            if (showApplyOptions) {
                ApplyOptionsSheet(
                    onDismiss = { showApplyOptions = false },
                    onApply = { target ->
                        showApplyOptions = false
                        viewModel.applyWallpaper(wp, target)
                    },
                    onSplitCrop = {
                        showApplyOptions = false
                        viewModel.applySplitCrop(wp)
                    },
                    onParallax = {
                        showApplyOptions = false
                        // Checked before the download so nobody waits on a wallpaper the phone can't show.
                        val block = liveWallpaperBlock(context, parallaxComponent)
                        if (block == null) {
                            viewModel.applyParallax(wp)
                        } else {
                            scope.launch { offerStaticApply(block, wp) }
                        }
                    },
                    allowTransforms = canEdit,
                )
            }

            // More menu sheet
            if (showMoreMenu) {
                MoreActionsSheet(
                    onDismiss = { showMoreMenu = false },
                    onEdit = if (canEdit) ({ showMoreMenu = false; onEdit(wp) }) else null,
                    onCrop = if (canEdit) ({ showMoreMenu = false; onCrop(wp) }) else null,
                    onPreview = { showMoreMenu = false; onPreview(wp) },
                    onCollection = { showMoreMenu = false; showCollectionPicker = true },
                    rotationExcluded = rotationExclusion != null,
                    onRotationExclusion = {
                        showMoreMenu = false
                        scope.launch {
                            if (rotationExclusion != null) {
                                rotationExclusionsViewModel.restoreNow(rotationExclusion.stableId)
                                snackbarHostState.showSnackbar(
                                    resources.getString(R.string.rotation_restored_message, wallpaperDetailTitle(resources, wp)),
                                )
                            } else {
                                val exclusion = rotationExclusionsViewModel.exclude(rotationIdentity)
                                val result = snackbarHostState.showSnackbar(
                                    message = resources.getString(R.string.rotation_excluded_message, wallpaperDetailTitle(resources, wp)),
                                    actionLabel = resources.getString(R.string.common_undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    rotationExclusionsViewModel.restoreNow(exclusion.stableId)
                                }
                            }
                        }
                    },
                    onFindSimilar = {
                        showMoreMenu = false
                        onFindSimilar(wp)
                    },
                    onReport = if (canReportWallpaper) ({
                        showMoreMenu = false
                        showReportDialog = true
                    }) else null,
                    onBlockCreator = if (canBlockCreator) ({
                        showMoreMenu = false
                        showBlockCreatorDialog = true
                    }) else null,
                    onDeleteUpload = if (canDeleteUpload) ({
                        showMoreMenu = false
                        showDeleteUploadDialog = true
                    }) else null,
                    uploaderName = wp.uploaderName,
                    license = wp.license,
                )
            }

            if (showReportDialog) {
                CommunityReportDialog(
                    title = if (isGeneratedWallpaper) {
                        stringResource(R.string.detail_report_generated_wallpaper)
                    } else {
                        stringResource(R.string.detail_report_wallpaper)
                    },
                    onDismiss = { showReportDialog = false },
                    onSubmit = { reason, note -> viewModel.reportWallpaper(wp, reason, note) },
                    reasons = if (isGeneratedWallpaper) GENERATED_CONTENT_REPORT_REASONS else COMMUNITY_REPORT_REASONS,
                    body = if (isGeneratedWallpaper) {
                        stringResource(R.string.detail_report_generated_body)
                    } else {
                        COMMUNITY_REPORT_TAKEDOWN_COPY
                    },
                )
            }

            if (showBlockCreatorDialog) {
                AlertDialog(
                    onDismissRequest = { showBlockCreatorDialog = false },
                    title = { Text(stringResource(R.string.detail_block_title)) },
                    text = { Text(communityBlockConfirmationCopy(CommunityUploadPolicyKind.WALLPAPER)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showBlockCreatorDialog = false
                                viewModel.blockCommunityWallpaper(wp, onBlocked = onBack)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.reports_block_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBlockCreatorDialog = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            }

            if (showDeleteUploadDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteUploadDialog = false },
                    title = { Text(stringResource(R.string.detail_delete_title)) },
                    text = { Text(communityOwnerDeleteConfirmationCopy(CommunityUploadPolicyKind.WALLPAPER)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteUploadDialog = false
                                viewModel.deleteCommunityWallpaper(wp)
                                onBack()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.reports_delete_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteUploadDialog = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            }

            // Collection picker
            if (showCollectionPicker) {
                CollectionPickerSheet(
                    collections = collections,
                    onDismiss = { showCollectionPicker = false },
                    onSelectCollection = { id ->
                        showCollectionPicker = false
                        viewModel.addToCollection(id, wp)
                    },
                    onCreateNew = { name ->
                        showCollectionPicker = false
                        viewModel.createCollection(name, wp)
                    },
                )
            }
        }
    }
}

@Composable
private fun WallpaperImage(url: String, onLoaded: () -> Unit, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = stringResource(R.string.detail_image_cd),
        contentScale = ContentScale.Crop,
        modifier = modifier,
        onState = { state -> if (state is AsyncImagePainter.State.Success) onLoaded() },
    ) {
        when (painter.state.collectAsState().value) {
            is AsyncImagePainter.State.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(40.dp), color = Color.White, strokeWidth = 3.dp)
                }
            }
            is AsyncImagePainter.State.Error -> {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.BrokenImage, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> SubcomposeAsyncImageContent()
        }
    }
}

@Composable
private fun CompactWallpaperOverlayCard(
    isFavorite: Boolean,
    isApplying: Boolean,
    canApply: Boolean,
    onApplyClick: () -> Unit,
    onShowDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailTopIconButton(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) stringResource(R.string.detail_saved) else stringResource(R.string.common_save),
                onClick = onToggleFavorite,
            )
            DetailTopIconButton(
                icon = Icons.Default.MoreHoriz,
                contentDescription = stringResource(R.string.detail_more_wallpaper_actions_cd),
                onClick = onShowDetails,
            )
        }
        Button(
            onClick = onApplyClick,
            modifier = Modifier
                .widthIn(min = 104.dp)
                .height(44.dp),
            enabled = canApply && !isApplying,
            shape = AuraShapes.Medium,
        ) {
            if (isApplying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Default.Wallpaper, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.detail_set))
            }
        }
    }
}

@Composable
private fun DetailTopIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(AuraShapes.Medium)
            .background(Color.Black.copy(alpha = 0.28f)),
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DetailOverlayPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.34f),
        shape = AuraShapes.Medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun DetailInfoChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = AuraShapes.Medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailSectionTitle(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DetailActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = label
        },
        shape = AuraShapes.Medium,
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyOptionsSheet(
    onDismiss: () -> Unit,
    onApply: (WallpaperTarget) -> Unit,
    onSplitCrop: () -> Unit,
    onParallax: () -> Unit = {},
    allowTransforms: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.detail_set_wallpaper_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.detail_apply_options_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SheetOption(Icons.Default.Home, stringResource(R.string.detail_home_screen), stringResource(R.string.detail_home_screen_body)) { onApply(WallpaperTarget.HOME) }
            SheetOption(Icons.Default.Lock, stringResource(R.string.detail_lock_screen), stringResource(R.string.detail_lock_screen_body)) { onApply(WallpaperTarget.LOCK) }
            SheetOption(Icons.Default.Smartphone, stringResource(R.string.detail_home_lock), stringResource(R.string.detail_home_lock_body)) { onApply(WallpaperTarget.BOTH) }
            if (allowTransforms) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                SheetOption(Icons.Default.Splitscreen, stringResource(R.string.detail_split_crop), stringResource(R.string.detail_split_crop_body)) { onSplitCrop() }
                SheetOption(Icons.Default.Layers, stringResource(R.string.detail_parallax_depth), stringResource(R.string.detail_parallax_depth_body)) { onParallax() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreActionsSheet(
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)?,
    onCrop: (() -> Unit)?,
    onPreview: () -> Unit,
    onCollection: () -> Unit,
    rotationExcluded: Boolean,
    onRotationExclusion: () -> Unit,
    onFindSimilar: (() -> Unit)?,
    onReport: (() -> Unit)?,
    onBlockCreator: (() -> Unit)?,
    onDeleteUpload: (() -> Unit)?,
    uploaderName: String = "",
    license: String = "",
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.detail_more_actions), style = MaterialTheme.typography.titleLarge)
            if (uploaderName.isNotEmpty()) {
                Text(
                    stringResource(R.string.detail_uploaded_by, uploaderName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (license.isNotBlank()) {
                Text(
                    license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            SheetOption(Icons.Default.Visibility, stringResource(R.string.detail_preview_mock_title), stringResource(R.string.detail_preview_mock_body)) { onPreview() }
            if (onEdit != null) {
                SheetOption(Icons.Default.Edit, stringResource(R.string.detail_edit), stringResource(R.string.detail_edit_body)) { onEdit() }
            }
            if (onCrop != null) {
                SheetOption(Icons.Default.Crop, stringResource(R.string.detail_crop_position), stringResource(R.string.detail_crop_position_body)) { onCrop() }
            }
            SheetOption(Icons.Default.CreateNewFolder, stringResource(R.string.detail_save_to_collection), stringResource(R.string.detail_save_to_collection_body)) { onCollection() }
            SheetOption(
                if (rotationExcluded) Icons.Default.Restore else Icons.Default.PlaylistRemove,
                stringResource(if (rotationExcluded) R.string.rotation_restore_action else R.string.rotation_exclude_action),
                stringResource(R.string.settings_rotation_exclusions_manage_subtitle),
            ) { onRotationExclusion() }
            if (onFindSimilar != null) {
                SheetOption(Icons.Default.ColorLens, stringResource(R.string.detail_find_similar_wallpapers), stringResource(R.string.detail_find_similar_wallpapers_body)) { onFindSimilar() }
            }
            if (onReport != null) {
                SheetOption(Icons.Default.Report, stringResource(R.string.detail_report_content), stringResource(R.string.detail_report_content_body)) { onReport() }
            }
            if (onBlockCreator != null) {
                SheetOption(
                    Icons.Default.Block,
                    stringResource(R.string.detail_block_creator),
                    stringResource(R.string.detail_block_creator_body),
                    tint = MaterialTheme.colorScheme.error,
                ) { onBlockCreator() }
            }
            if (onDeleteUpload != null) {
                SheetOption(
                    Icons.Default.Delete,
                    stringResource(R.string.detail_delete_upload),
                    stringResource(R.string.detail_delete_upload_body),
                    tint = MaterialTheme.colorScheme.error,
                ) { onDeleteUpload() }
            }
        }
    }
}

@Composable
private fun SheetOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = AuraShapes.Medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = AuraShapes.Medium,
                color = tint.copy(alpha = 0.12f),
            ) {
                Icon(
                    icon,
                    null,
                    tint = tint,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionPickerSheet(
    collections: List<WallpaperCollectionEntity>,
    onDismiss: () -> Unit,
    onSelectCollection: (Long) -> Unit,
    onCreateNew: (String) -> Unit,
) {
    var showCreateField by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.detail_save_to_collection), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.detail_save_to_collection_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            collections.forEach { collection ->
                SheetOption(Icons.Default.Folder, collection.name, stringResource(R.string.detail_add_to_collection_body)) {
                    onSelectCollection(collection.collectionId)
                }
            }
            if (showCreateField) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.detail_collection_name_placeholder)) },
                        singleLine = true,
                        shape = AuraShapes.Medium,
                    )
                    FilledTonalButton(
                        onClick = { if (newName.isNotBlank()) onCreateNew(newName.trim()) },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = AuraShapes.Medium,
                    ) { Text(stringResource(R.string.detail_create)) }
                }
            } else {
                SheetOption(Icons.Default.Add, stringResource(R.string.detail_new_collection), stringResource(R.string.detail_new_collection_body)) {
                    showCreateField = true
                }
            }
        }
    }
}

internal fun wallpaperDetailTitle(resources: Resources, wallpaper: Wallpaper): String =
    when {
        wallpaper.category.isNotBlank() -> wallpaper.category.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        wallpaper.tags.isNotEmpty() -> wallpaper.tags.first().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        else -> resources.getString(R.string.wallpaper_card_source, resources.wallpaperSourceName(wallpaper.source))
    }

internal fun wallpaperDetailSubtitle(resources: Resources, wallpaper: Wallpaper): String {
    val sourceLabel = resources.wallpaperSourceName(wallpaper.source)
    return when {
        wallpaper.isSourceUnavailable() && wallpaper.sourceAvailabilityReason.isNotBlank() ->
            resources.getString(
                R.string.wallpaper_detail_subtitle_unavailable_reason,
                sourceLabel,
                wallpaper.sourceAvailabilityReason,
            )
        wallpaper.isSourceUnavailable() ->
            resources.getString(R.string.wallpaper_detail_subtitle_unavailable, sourceLabel)
        wallpaper.uploaderName.isNotBlank() ->
            resources.getString(R.string.wallpaper_detail_subtitle_by, wallpaper.uploaderName, sourceLabel)
        wallpaper.sourcePageUrl.isNotBlank() ->
            resources.getString(R.string.wallpaper_detail_subtitle_source_page, sourceLabel)
        else ->
            resources.getString(R.string.wallpaper_detail_subtitle_source, sourceLabel)
    }
}

internal fun sourceDisplayName(source: ContentSource): String = when (source) {
    ContentSource.WALLHAVEN -> "Wallhaven"
    ContentSource.PICSUM -> "Picsum"
    ContentSource.BING -> "Bing"
    ContentSource.WIKIMEDIA -> "Wikimedia"
    ContentSource.INTERNET_ARCHIVE -> "Internet Archive"
    ContentSource.REDDIT -> "Reddit"
    ContentSource.NASA -> "NASA"
    ContentSource.FREESOUND -> "Freesound"
    ContentSource.JAMENDO -> "Jamendo"
    ContentSource.AUDIUS -> "Audius"
    ContentSource.CCMIXTER -> "ccMixter"
    ContentSource.LOCAL -> "Local"
    ContentSource.YOUTUBE -> "YouTube"
    ContentSource.PEXELS -> "Pexels"
    ContentSource.PIXABAY -> "Pixabay"
    ContentSource.KLIPY -> "Klipy"
    ContentSource.SOUNDCLOUD -> "SoundCloud"
    ContentSource.COMMUNITY -> "Community"
    ContentSource.BUNDLED -> "Aura Originals"
    ContentSource.AI_GENERATED -> "AI Generated"
    ContentSource.OPEN_METEO -> "Open-Meteo"
    ContentSource.LEMMY -> "Lemmy"
    ContentSource.TIKTOK -> "TikTok"
}

internal fun formatCompactCount(value: Int): String {
    val root = java.util.Locale.ROOT
    return when {
        value >= 1_000_000 -> String.format(root, "%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format(root, "%.1fk", value / 1_000f)
        else -> value.toString()
    }
}

internal fun formatFileTypeLabel(fileType: String): String? {
    val clean = fileType.trim()
    if (clean.isBlank()) return null
    return when {
        clean.contains("jpeg", ignoreCase = true) || clean.contains("jpg", ignoreCase = true) -> "JPG"
        clean.contains("png", ignoreCase = true) -> "PNG"
        clean.contains("webp", ignoreCase = true) -> "WEBP"
        // Locale.ROOT: MIME-type suffix is ASCII; Turkish locale would corrupt the "i" in "gif".
        else -> clean.substringAfterLast('/').uppercase(java.util.Locale.ROOT)
    }
}

internal fun formatFileSizeLabel(bytes: Long): String? {
    val root = java.util.Locale.ROOT
    return when {
        bytes <= 0L -> null
        bytes >= 1024L * 1024L -> String.format(root, "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024L -> String.format(root, "%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}
