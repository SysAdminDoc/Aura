package com.freevibe.ui.screens.wallpapers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freevibe.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.freevibe.R
import com.freevibe.data.model.COMMUNITY_UPLOAD_LICENSES
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.stableKey
import com.freevibe.data.model.shouldShowCommunityContent
import com.freevibe.data.repository.matchesHiddenIds
import com.freevibe.service.PhotoPickerCustomization
import com.freevibe.ui.components.CompactSearchField
import com.freevibe.ui.components.LoadMoreIndicator
import com.freevibe.ui.components.CommunityGuidelinesDialog
import com.freevibe.ui.components.CommunityPolicyNotice
import com.freevibe.ui.components.CountBadge
import com.freevibe.ui.components.DownloadProgressBar
import com.freevibe.ui.components.EmbeddedImagePickerSheet
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.SearchHistoryDropdown
import com.freevibe.ui.components.ShimmerBox
import com.freevibe.ui.components.ShimmerWallpaperGrid
import com.freevibe.ui.components.SourceBadge
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStatusAction
import com.freevibe.ui.components.AuraStatusBanner
import com.freevibe.ui.components.BrowseRail
import com.freevibe.ui.components.BrowseRailItem
import com.freevibe.ui.navigation.LocalAuraNavigationLayout
import com.freevibe.ui.navigation.isExpanded
import com.freevibe.ui.policy.CommunityUploadPolicyKind
import com.freevibe.ui.policy.communityUploadPolicyCopy

// #9: Wallhaven supported colors
private val WALLHAVEN_COLORS = listOf(
    "660000" to Color(0xFF660000), "990000" to Color(0xFF990000),
    "cc0000" to Color(0xFFCC0000), "cc3333" to Color(0xFFCC3333),
    "ea4c88" to Color(0xFFEA4C88), "993399" to Color(0xFF993399),
    "663399" to Color(0xFF663399), "333399" to Color(0xFF333399),
    "0066cc" to Color(0xFF0066CC), "0099cc" to Color(0xFF0099CC),
    "66cccc" to Color(0xFF66CCCC), "77cc33" to Color(0xFF77CC33),
    "669900" to Color(0xFF669900), "336600" to Color(0xFF336600),
    "666600" to Color(0xFF666600), "999900" to Color(0xFF999900),
    "cccc33" to Color(0xFFCCCC33), "ffff00" to Color(0xFFFFFF00),
    "ffcc33" to Color(0xFFFFCC33), "ff6600" to Color(0xFFFF6600),
    "cc6633" to Color(0xFFCC6633), "996633" to Color(0xFF996633),
    "663300" to Color(0xFF663300), "000000" to Color(0xFF000000),
    "999999" to Color(0xFF999999), "cccccc" to Color(0xFFCCCCCC),
    "ffffff" to Color(0xFFFFFFFF), "424153" to Color(0xFF424153),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpapersScreen(
    initialQuery: String? = null,
    initialColor: String? = null,
    initialSimilarId: String? = null,
    initialSimilarSource: String? = null,
    initialSimilarFullUrl: String? = null,
    isExpandedLayout: Boolean = LocalAuraNavigationLayout.current.isExpanded,
    onWallpaperClick: (Wallpaper) -> Unit = {},
    onGenerateClick: () -> Unit = {},
    onCategoriesClick: () -> Unit = {},
    onCollectionsClick: () -> Unit = {},
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val favoriteIdentities by viewModel.favoriteIdentities.collectAsStateWithLifecycle()
    val dailyPick by viewModel.dailyPick.collectAsStateWithLifecycle()
    val topVoted by viewModel.topVoted.collectAsStateWithLifecycle()
    val hiddenIds by viewModel.hiddenIds.collectAsStateWithLifecycle()
    val wallhavenProviderEnabled by viewModel.wallhavenProviderEnabled.collectAsStateWithLifecycle()
    val redditProviderEnabled by viewModel.redditProviderEnabled.collectAsStateWithLifecycle()
    val pexelsProviderEnabled by viewModel.pexelsProviderEnabled.collectAsStateWithLifecycle()
    val pixabayProviderEnabled by viewModel.pixabayProviderEnabled.collectAsStateWithLifecycle()
    val communityProviderEnabled by viewModel.communityProviderEnabled.collectAsStateWithLifecycle()
    val communityGuidelinesAccepted by viewModel.communityGuidelinesAccepted.collectAsStateWithLifecycle()
    val generatedContentProviderEnabled by viewModel.generatedContentProviderEnabled
        .collectAsStateWithLifecycle()
    val showGeneratedContentEntry = !BuildConfig.FOSS_BUILD && generatedContentProviderEnabled
    var hideAiGeneratedCommunity by rememberSaveable { mutableStateOf(false) }
    val contentFingerprints by viewModel.contentFingerprintMap.collectAsStateWithLifecycle()
    val feedWallpapers = remember(state.wallpapers, state.selectedTab, hideAiGeneratedCommunity, contentFingerprints) {
        val shown = if (state.selectedTab == WallpaperTab.COMMUNITY && hideAiGeneratedCommunity) {
            state.wallpapers.filter { shouldShowCommunityContent(it.isAiGenerated, hideAiGenerated = true) }
        } else {
            state.wallpapers
        }
        coalesceContentDuplicates(shown, contentFingerprints)
    }
    val visibleSections = remember(
        feedWallpapers,
        hiddenIds,
        topVoted,
        dailyPick,
        state.selectedTab,
        communityProviderEnabled,
        communityGuidelinesAccepted,
    ) {
        computeVisibleWallpaperSections(
            wallpapers = feedWallpapers,
            hiddenIds = hiddenIds,
            topVoted = if (communityProviderEnabled && communityGuidelinesAccepted) topVoted else emptyList(),
            dailyPick = dailyPick,
            isDiscoverTab = state.selectedTab == WallpaperTab.DISCOVER,
        )
    }

    // Vote counts for visible wallpapers — use derivedStateOf to avoid recomputing on referential inequality
    val wallpaperIds = remember(feedWallpapers, communityProviderEnabled, communityGuidelinesAccepted) {
        if (communityProviderEnabled && communityGuidelinesAccepted) feedWallpapers.map { it.stableKey() } else emptyList()
    }
    val voteCountsFlow = remember(wallpaperIds) {
        if (wallpaperIds.isNotEmpty()) {
            viewModel.voteRepo.getVoteCounts(wallpaperIds)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyMap())
        }
    }
    val voteCounts by voteCountsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    var searchQuery by remember { mutableStateOf(state.query) }
    LaunchedEffect(state.query) { searchQuery = state.query }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val hiddenMessage = stringResource(R.string.community_item_hidden)
    val undoLabel = stringResource(R.string.common_undo)
    var nonBlockingWarning by remember { mutableStateOf<String?>(null) }
    var nonBlockingWarningSource by remember { mutableStateOf<String?>(null) }
    var showSearchHistory by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(initialQuery?.isNotBlank() == true) }
    var showSourceMenu by remember { mutableStateOf(false) }
    var showQuickActionsMenu by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }
    var showCommunityGuidelines by remember { mutableStateOf(false) }
    var showWallpaperUploadDialog by remember { mutableStateOf(false) }
    var showEmbeddedWallpaperPicker by remember { mutableStateOf(false) }
    var selectedWallpaperUploadUri by remember { mutableStateOf<Uri?>(null) }
    var awaitingWallpaperUploadResult by remember { mutableStateOf(false) }
    val hasQuickActionsMenu = showGeneratedContentEntry ||
        wallhavenProviderEnabled ||
        (communityProviderEnabled && state.selectedTab == WallpaperTab.COMMUNITY)
    LaunchedEffect(wallhavenProviderEnabled, redditProviderEnabled, pexelsProviderEnabled, pixabayProviderEnabled, communityProviderEnabled, state.selectedTab) {
        val disabledTab = when (state.selectedTab) {
            WallpaperTab.NEWEST -> !wallhavenProviderEnabled && !pixabayProviderEnabled
            WallpaperTab.WALLHAVEN -> !wallhavenProviderEnabled
            WallpaperTab.REDDIT -> !redditProviderEnabled
            WallpaperTab.PEXELS -> !pexelsProviderEnabled
            WallpaperTab.PIXABAY -> !pixabayProviderEnabled
            WallpaperTab.COMMUNITY -> !communityProviderEnabled
            else -> false
        }
        if (disabledTab) {
            viewModel.selectTab(WallpaperTab.DISCOVER)
        }
    }
    fun handleWallpaperUploadUri(uri: Uri?) {
        if (communityProviderEnabled && communityGuidelinesAccepted && uri != null) {
            selectedWallpaperUploadUri = uri
            showWallpaperUploadDialog = true
        } else if (communityProviderEnabled && uri != null) {
            selectedWallpaperUploadUri = uri
            showCommunityGuidelines = true
        }
    }

    // Photo Picker (Android 13+, backported via Google Play services on older versions). No
    // READ_MEDIA_IMAGES permission needed; scoped-storage compliant; better UX than GetContent.
    // Supported Android 14+ extension devices use the embedded picker; fallback remains
    // ActivityResultContracts.PickVisualMedia with Aura's 9:16 portrait grid customization.
    val wallpaperUploadLauncher = rememberLauncherForActivityResult(
        com.freevibe.service.AuraPickVisualMedia()
    ) { uri ->
        handleWallpaperUploadUri(uri)
    }
    val wallpaperUploadPickerRequest = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }
    fun launchWallpaperUploadPicker() {
        if (PhotoPickerCustomization.isEmbeddedImagePickerAvailable(context)) {
            showEmbeddedWallpaperPicker = true
        } else {
            wallpaperUploadLauncher.launch(wallpaperUploadPickerRequest)
        }
    }

    // NX-10: EyeDropper API (Android 17+). The EyeDropper app launches a system
    // pixel-picker overlay; on confirm, it returns an Int colour via
    // Intent.EXTRA_COLOR. We strip alpha and feed the lower 24 bits to the
    // wallpaper-by-colour search. Raw action / extra strings keep this
    // compatible with compileSdk 35 — no API 37 class refs at compile time.
    val eyeDropperLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            // Try the documented Int extra first, then the colour-int alias some
            // OEM EyeDropper builds emit. Falls through silently when neither
            // is present (e.g. user cancelled before confirming).
            val color = data.getIntExtra("android.intent.extra.COLOR", Int.MIN_VALUE)
            if (color != Int.MIN_VALUE) {
                viewModel.searchByPickedColor(color)
            }
        }
    }
    val eyeDropperAvailable = remember(context) {
        if (android.os.Build.VERSION.SDK_INT < 37) {
            false
        } else {
            // EyeDropper is delivered as a system app on Android 17 devices.
            // Probe with PackageManager so the FAB doesn't surface on un-updated
            // GSI builds where the system app hasn't been installed yet.
            val probe = android.content.Intent("android.intent.action.OPEN_EYE_DROPPER")
            probe.resolveActivity(context.packageManager) != null
        }
    }
    val wallpaperFilterCount = remember(state.selectedTab, state.selectedColor, state.discoverFilter, state.topRange) {
        buildList {
            if (state.selectedTab == WallpaperTab.DISCOVER && state.discoverFilter != WallpaperDiscoverFilter.FOR_YOU) {
                add("discover")
            }
            if (state.selectedColor != null) add("color")
            if (state.selectedTab == WallpaperTab.WALLHAVEN && state.topRange != "1M") add("range")
        }.size
    }
    val adaptiveGridColumns = remember(gridColumns, isExpandedLayout) {
        if (isExpandedLayout) gridColumns.coerceAtLeast(3) else gridColumns
    }

    LaunchedEffect(initialQuery, initialColor, initialSimilarId, initialSimilarSource, initialSimilarFullUrl) {
        viewModel.handleRouteFilters(
            query = initialQuery,
            color = initialColor,
            similarId = initialSimilarId,
            similarSource = initialSimilarSource,
            similarFullUrl = initialSimilarFullUrl,
        )
    }
    LaunchedEffect(state.applySuccess) {
        state.applySuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }
    LaunchedEffect(state.error, state.errorSource, state.wallpapers.isNotEmpty()) {
        if (state.wallpapers.isNotEmpty()) {
            state.error?.let {
                nonBlockingWarning = it
                nonBlockingWarningSource = state.errorSource
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }
    LaunchedEffect(state.isRefreshing, state.error) {
        if (!state.isRefreshing && state.error == null && nonBlockingWarning != null) {
            nonBlockingWarning = null
            nonBlockingWarningSource = null
        }
    }
    if (communityProviderEnabled && communityGuidelinesAccepted && showWallpaperUploadDialog && selectedWallpaperUploadUri != null) {
        WallpaperUploadDialog(
            isUploading = state.isUploadingWallpaper,
            uploadProgress = state.wallpaperUploadProgress,
            onUpload = { name, category, tags, rights, isAiGenerated ->
                awaitingWallpaperUploadResult = true
                selectedWallpaperUploadUri?.let { uri ->
                    viewModel.uploadCommunityWallpaper(
                        localUri = uri,
                        name = name,
                        category = category,
                        tags = tags,
                        rights = rights,
                        isAiGenerated = isAiGenerated,
                    )
                }
            },
            onDismiss = {
                if (!state.isUploadingWallpaper) {
                    showWallpaperUploadDialog = false
                    selectedWallpaperUploadUri = null
                    awaitingWallpaperUploadResult = false
                }
            },
        )
        LaunchedEffect(state.isUploadingWallpaper, state.wallpaperUploadComplete, state.error) {
            if (awaitingWallpaperUploadResult && !state.isUploadingWallpaper) {
                awaitingWallpaperUploadResult = false
            }
            if (state.wallpaperUploadComplete) {
                showWallpaperUploadDialog = false
                selectedWallpaperUploadUri = null
            }
        }
    }
    if (showEmbeddedWallpaperPicker) {
        EmbeddedImagePickerSheet(
            title = stringResource(R.string.photo_picker_wallpaper_title),
            body = stringResource(R.string.photo_picker_wallpaper_body),
            fallbackLabel = stringResource(R.string.photo_picker_fallback_open),
            onDismiss = { showEmbeddedWallpaperPicker = false },
            onFallback = {
                showEmbeddedWallpaperPicker = false
                wallpaperUploadLauncher.launch(wallpaperUploadPickerRequest)
            },
            onImagePicked = { uri ->
                showEmbeddedWallpaperPicker = false
                handleWallpaperUploadUri(uri)
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val visibleTabs = remember(state.selectedTab, pexelsProviderEnabled, pixabayProviderEnabled, communityProviderEnabled) {
                WallpaperTab.entries.filter {
                    it != WallpaperTab.SEARCH || state.selectedTab == WallpaperTab.SEARCH
                }.filter {
                    // Newest is a rail tab, not a source; keep it out of the source dropdown.
                    it != WallpaperTab.NEWEST
                }.filter {
                    it != WallpaperTab.COLOR || state.selectedTab == WallpaperTab.COLOR
                }.filter {
                    it != WallpaperTab.WALLHAVEN || wallhavenProviderEnabled || state.selectedTab == WallpaperTab.WALLHAVEN
                }.filter {
                    it != WallpaperTab.PEXELS || pexelsProviderEnabled || state.selectedTab == WallpaperTab.PEXELS
                }.filter {
                    it != WallpaperTab.PIXABAY || pixabayProviderEnabled || state.selectedTab == WallpaperTab.PIXABAY
                }.filter {
                    it != WallpaperTab.COMMUNITY || communityProviderEnabled || state.selectedTab == WallpaperTab.COMMUNITY
                }
            }
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
                        text = wallpaperHeaderTitle(
                            tab = state.selectedTab,
                            query = state.query,
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { searchExpanded = !searchExpanded }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.wallpapers_search_placeholder),
                        )
                    }
                    Box {
                        IconButton(onClick = { showSourceMenu = true }) {
                            Icon(
                                wallpaperTabIcon(state.selectedTab),
                                contentDescription = wallpaperTabLabel(state.selectedTab),
                            )
                        }
                        DropdownMenu(
                            expanded = showSourceMenu,
                            onDismissRequest = { showSourceMenu = false },
                        ) {
                            visibleTabs.forEach { tab ->
                                DropdownMenuItem(
                                    text = { Text(wallpaperTabLabel(tab)) },
                                    onClick = {
                                        showSourceMenu = false
                                        if (tab == WallpaperTab.COMMUNITY && communityProviderEnabled && !communityGuidelinesAccepted) {
                                            showCommunityGuidelines = true
                                        } else {
                                            viewModel.selectTab(tab)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(wallpaperTabIcon(tab), contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        if (state.selectedTab == tab) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showFiltersSheet = true }) {
                        Box {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.wallpapers_filter_cd),
                                tint = if (wallpaperFilterCount > 0 || state.selectedColor != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            CountBadge(
                                count = wallpaperFilterCount,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-8).dp),
                            )
                        }
                    }
                    if (hasQuickActionsMenu) {
                        Box {
                            IconButton(onClick = { showQuickActionsMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.a11y_show_quick_actions))
                            }
                            DropdownMenu(
                                expanded = showQuickActionsMenu,
                                onDismissRequest = { showQuickActionsMenu = false },
                            ) {
                                if (communityProviderEnabled && state.selectedTab == WallpaperTab.COMMUNITY) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.wallpapers_fab_upload)) },
                                        leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                        onClick = {
                                            showQuickActionsMenu = false
                                            if (communityGuidelinesAccepted) launchWallpaperUploadPicker() else showCommunityGuidelines = true
                                        },
                                    )
                                }
                                if (showGeneratedContentEntry) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.feed_generate)) },
                                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                        onClick = { showQuickActionsMenu = false; onGenerateClick() },
                                    )
                                }
                                if (wallhavenProviderEnabled && state.selectedTab == WallpaperTab.DISCOVER) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.wallpapers_fab_theme_match)) },
                                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                        onClick = { showQuickActionsMenu = false; viewModel.matchMyTheme() },
                                    )
                                    if (eyeDropperAvailable) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.wallpapers_fab_pick_colour)) },
                                            leadingIcon = { Icon(Icons.Default.Colorize, contentDescription = null) },
                                            onClick = {
                                                showQuickActionsMenu = false
                                                val intent = android.content.Intent("android.intent.action.OPEN_EYE_DROPPER")
                                                runCatching { eyeDropperLauncher.launch(intent) }
                                            },
                                        )
                                    }
                                }
                                if (wallhavenProviderEnabled) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.wallpapers_fab_surprise_me)) },
                                        leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                                        onClick = { showQuickActionsMenu = false; viewModel.loadRandom() },
                                    )
                                }
                            }
                        }
                    }
                }

                if (searchExpanded || state.selectedTab == WallpaperTab.SEARCH || searchQuery.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        CompactSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; showSearchHistory = it.isEmpty() },
                            placeholder = stringResource(R.string.wallpapers_search_placeholder),
                            modifier = Modifier
                                .fillMaxWidth(),
                            onClear = {
                                showSearchHistory = false
                                focusManager.clearFocus()
                                if (state.selectedTab == WallpaperTab.SEARCH || state.selectedTab == WallpaperTab.COLOR) {
                                    searchQuery = ""
                                    viewModel.clearActiveFilter()
                                } else {
                                    searchQuery = ""
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    viewModel.search(searchQuery)
                                    showSearchHistory = false
                                    focusManager.clearFocus()
                                },
                            ),
                        )
                        SearchHistoryDropdown(
                            recentQueries = recentSearches,
                            isVisible = showSearchHistory && searchQuery.isEmpty(),
                            onQueryClick = { query ->
                                searchQuery = query
                                viewModel.search(query)
                                showSearchHistory = false
                                focusManager.clearFocus()
                            },
                            onDeleteQuery = { viewModel.removeSearch(it) },
                            onClearAll = { viewModel.clearSearchHistory() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 42.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                // Newest/Categories/Collections only apply to the non-Reddit catalog, so in the
                // default Reddit-only mode the rail collapses to Popular. Enabling any other source
                // in Settings restores them (state.extendedBrowseSourcesEnabled flips reactively).
                val popularLabel = stringResource(R.string.browse_rail_popular)
                val newestLabel = stringResource(R.string.browse_rail_newest)
                val categoriesLabel = stringResource(R.string.browse_rail_categories)
                val collectionsLabel = stringResource(R.string.browse_rail_collections)
                BrowseRail(
                    items = buildList {
                        add(
                            BrowseRailItem(
                                label = popularLabel,
                                icon = Icons.Default.Explore,
                                selected = state.selectedTab == WallpaperTab.DISCOVER &&
                                    state.discoverFilter == WallpaperDiscoverFilter.FOR_YOU &&
                                    state.query.isBlank(),
                                onClick = {
                                    searchQuery = ""
                                    viewModel.selectTab(WallpaperTab.DISCOVER)
                                    viewModel.setDiscoverFilter(WallpaperDiscoverFilter.FOR_YOU)
                                },
                            ),
                        )
                        if (state.extendedBrowseSourcesEnabled) {
                            add(
                                BrowseRailItem(
                                    label = newestLabel,
                                    icon = Icons.Default.Schedule,
                                    selected = state.selectedTab == WallpaperTab.NEWEST,
                                    onClick = {
                                        searchQuery = ""
                                        viewModel.selectTab(WallpaperTab.NEWEST)
                                    },
                                ),
                            )
                            add(
                                BrowseRailItem(
                                    label = categoriesLabel,
                                    icon = Icons.Default.Category,
                                    onClick = onCategoriesClick,
                                ),
                            )
                            add(
                                BrowseRailItem(
                                    label = collectionsLabel,
                                    icon = Icons.Default.Folder,
                                    onClick = onCollectionsClick,
                                ),
                            )
                        }
                    },
                )
                if (state.selectedTab == WallpaperTab.COMMUNITY && communityProviderEnabled) {
                    FilterChip(
                        selected = hideAiGeneratedCommunity,
                        onClick = { hideAiGeneratedCommunity = !hideAiGeneratedCommunity },
                        label = { Text(stringResource(R.string.community_hide_ai_filter)) },
                        leadingIcon = if (hideAiGeneratedCommunity) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = RoundedCornerShape(8.dp),
                    )
                }
                if (
                    (state.selectedTab == WallpaperTab.DISCOVER && state.discoverFilter != WallpaperDiscoverFilter.FOR_YOU) ||
                    state.selectedColor != null ||
                    (state.selectedTab == WallpaperTab.WALLHAVEN && state.topRange != "1M")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    if (state.selectedTab == WallpaperTab.DISCOVER && state.discoverFilter != WallpaperDiscoverFilter.FOR_YOU) {
                        AssistChip(
                            onClick = { viewModel.setDiscoverFilter(WallpaperDiscoverFilter.FOR_YOU) },
                            label = { Text(wallpaperFilterLabel(state.discoverFilter), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(12.dp)) },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                    state.selectedColor?.let { selectedColor ->
                        AssistChip(
                            onClick = { viewModel.clearActiveFilter() },
                            label = { Text(stringResource(R.string.wallpapers_filter_tone_chip), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(parseHexColor(selectedColor)),
                                )
                            },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                    if (state.selectedTab == WallpaperTab.WALLHAVEN && state.topRange != "1M") {
                        AssistChip(
                            onClick = { viewModel.setTopRange("1M") },
                            label = { Text(wallpaperTopRangeLabel(state.topRange), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(12.dp)) },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                    }
                }
            }

            // Download progress
            DownloadProgressBar(
                downloads = downloads,
                onDismiss = { viewModel.dismissDownload(it) },
            )

            nonBlockingWarning?.let { warning ->
                AuraStatusBanner(
                    icon = Icons.Default.CloudOff,
                    title = nonBlockingWarningSource?.let { source ->
                        stringResource(R.string.wallpapers_banner_stale_source_title, source.lowercase(java.util.Locale.ROOT))
                    } ?: stringResource(R.string.wallpapers_banner_stale_title),
                    message = stringResource(R.string.wallpapers_banner_stale_message, warning),
                    tone = MaterialTheme.colorScheme.tertiary,
                    primaryAction = AuraStatusAction(
                        label = stringResource(R.string.common_refresh),
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.refresh() },
                    ),
                    secondaryAction = AuraStatusAction(
                        label = stringResource(R.string.wallpapers_banner_dismiss),
                        icon = Icons.Default.Close,
                        onClick = {
                            nonBlockingWarning = null
                            nonBlockingWarningSource = null
                        },
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // Content with pull-to-refresh (#4)
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.wallpapers.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WallpaperStateCard(
                                icon = Icons.Default.AutoAwesome,
                                title = stringResource(R.string.wallpapers_loading_title),
                                description = stringResource(R.string.wallpapers_loading_description),
                            )
                            ShimmerWallpaperGrid(Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                    state.error != null && state.wallpapers.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            WallpaperStateCard(
                                icon = Icons.Default.CloudOff,
                                title = state.errorSource?.let {
                                    stringResource(R.string.wallpapers_error_source_title, it.lowercase(java.util.Locale.ROOT))
                                } ?: stringResource(R.string.wallpapers_error_title),
                                description = state.error ?: stringResource(R.string.wallpapers_error_description_fallback),
                                primaryAction = WallpaperStateAction(
                                    label = stringResource(R.string.wallpapers_error_retry),
                                    icon = Icons.Default.Refresh,
                                    onClick = { viewModel.refresh() },
                                ),
                                secondaryAction = if (state.selectedTab != WallpaperTab.DISCOVER) {
                                    WallpaperStateAction(
                                        label = stringResource(R.string.wallpapers_error_back_to_discover),
                                        icon = Icons.Default.Explore,
                                        onClick = { viewModel.selectTab(WallpaperTab.DISCOVER) },
                                    )
                                } else null,
                            )
                        }
                    }
                    !visibleSections.hasRenderableContent && !state.isRefreshing -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            WallpaperStateCard(
                                modifier = Modifier.padding(24.dp),
                                icon = Icons.Default.ImageNotSupported,
                                title = when {
                                    state.selectedTab == WallpaperTab.SEARCH -> stringResource(R.string.wallpapers_empty_search_title, state.query)
                                    state.selectedTab == WallpaperTab.COLOR -> stringResource(R.string.wallpapers_empty_color_title)
                                    state.selectedTab == WallpaperTab.COMMUNITY -> stringResource(R.string.wallpapers_empty_community_title)
                                    state.selectedTab == WallpaperTab.PIXABAY -> stringResource(R.string.wallpapers_empty_pixabay_title)
                                    else -> stringResource(R.string.wallpapers_empty_default_title)
                                },
                                description = when {
                                    state.selectedTab == WallpaperTab.SEARCH ->
                                        stringResource(R.string.wallpapers_empty_search_description)
                                    state.selectedTab == WallpaperTab.COLOR ->
                                        stringResource(R.string.wallpapers_empty_color_description)
                                    state.selectedTab == WallpaperTab.COMMUNITY ->
                                        stringResource(R.string.wallpapers_empty_community_description)
                                    state.selectedTab == WallpaperTab.PIXABAY ->
                                        stringResource(R.string.wallpapers_empty_pixabay_description)
                                    else ->
                                        stringResource(R.string.wallpapers_empty_default_description)
                                },
                                primaryAction = if (state.selectedTab == WallpaperTab.COMMUNITY && !communityProviderEnabled) {
                                    null
                                } else WallpaperStateAction(
                                    label = if (state.selectedTab == WallpaperTab.COMMUNITY) {
                                        stringResource(R.string.wallpapers_empty_upload_action)
                                    } else if (state.selectedColor != null || state.selectedTab != WallpaperTab.DISCOVER) {
                                        stringResource(R.string.wallpapers_empty_back_to_discover_action)
                                    } else {
                                        stringResource(R.string.common_refresh)
                                    },
                                    icon = if (state.selectedTab == WallpaperTab.COMMUNITY) {
                                        Icons.Default.Upload
                                    } else if (state.selectedColor != null || state.selectedTab != WallpaperTab.DISCOVER) {
                                        Icons.Default.Explore
                                    } else {
                                        Icons.Default.Refresh
                                    },
                                    onClick = {
                                        if (state.selectedTab == WallpaperTab.COMMUNITY) {
                                            if (communityGuidelinesAccepted) {
                                                launchWallpaperUploadPicker()
                                            } else {
                                                showCommunityGuidelines = true
                                            }
                                        } else if (state.selectedColor != null || state.selectedTab != WallpaperTab.DISCOVER) {
                                            viewModel.selectTab(WallpaperTab.DISCOVER)
                                        } else {
                                            viewModel.refresh()
                                        }
                                    },
                                ),
                            )
                        }
                    }
                    else -> {
                        // #4: Pull-to-refresh wrapper
                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                        ) {
                            WallpaperGrid(
                                // Featured cards are folded into the same content stream. The
                                // section builder keeps every Reddit item ahead of Bing and the
                                // other providers, so the home viewport starts with the source
                                // people came here for instead of a decorative daily-pick slot.
                                wallpapers = visibleSections.pagerWallpapers,
                                isLoadingMore = state.isLoadingMore,
                                columns = adaptiveGridColumns,
                                onWallpaperClick = { wp ->
                                    viewModel.selectWallpaper(wp, visibleSections.pagerWallpapers)
                                    onWallpaperClick(wp)
                                },
                                onLongPress = { wp ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleFavorite(wp)
                                },
                                favoriteIdentities = favoriteIdentities,
                                hiddenIds = hiddenIds,
                                onUpvote = if (communityProviderEnabled) ({ id ->
                                    if (communityGuidelinesAccepted) viewModel.upvote(id) else showCommunityGuidelines = true
                                }) else null,
                                onDownvote = { wallpaper ->
                                    val hiddenKey = wallpaper.stableKey()
                                    viewModel.skipWallpaper(wallpaper)
                                    if (communityProviderEnabled) {
                                        if (communityGuidelinesAccepted) {
                                            viewModel.downvote(hiddenKey)
                                        } else {
                                            showCommunityGuidelines = true
                                        }
                                    }
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = hiddenMessage,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undoSkipWallpaper(wallpaper)
                                            viewModel.undoDownvote(hiddenKey)
                                        }
                                    }
                                },
                                voteCounts = voteCounts,
                                onLoadMore = { viewModel.loadMore() },
                                isExpandedLayout = isExpandedLayout,
                            )
                        }
                    }
                }
            }
        }

        AuraSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        )

    }

    if (showFiltersSheet) {
        ModalBottomSheet(onDismissRequest = { showFiltersSheet = false }) {
            WallpaperFiltersSheet(
                selectedTab = state.selectedTab,
                discoverFilter = state.discoverFilter,
                selectedColor = state.selectedColor,
                topRange = state.topRange,
                onSelectDiscoverFilter = { filter ->
                    viewModel.setDiscoverFilter(filter)
                    showFiltersSheet = false
                },
                onSelectColor = { color ->
                    viewModel.searchByColor(color)
                    showFiltersSheet = false
                },
                onClearColor = if (state.selectedColor != null) {
                    {
                        viewModel.clearActiveFilter()
                        showFiltersSheet = false
                    }
                } else null,
                onSelectTopRange = { range ->
                    viewModel.setTopRange(range)
                    showFiltersSheet = false
                },
                onResetFilters = if (wallpaperFilterCount > 0) {
                    {
                        if (state.selectedColor != null) {
                            viewModel.clearActiveFilter()
                        }
                        if (state.selectedTab == WallpaperTab.DISCOVER && state.discoverFilter != WallpaperDiscoverFilter.FOR_YOU) {
                            viewModel.setDiscoverFilter(WallpaperDiscoverFilter.FOR_YOU)
                        }
                        if (state.selectedTab == WallpaperTab.WALLHAVEN && state.topRange != "1M") {
                            viewModel.setTopRange("1M")
                        }
                        showFiltersSheet = false
                    }
                } else null,
            )
        }
    }
    if (showCommunityGuidelines) {
        CommunityGuidelinesDialog(
            onAccept = {
                viewModel.acceptCommunityGuidelines()
                if (selectedWallpaperUploadUri != null) showWallpaperUploadDialog = true
                showCommunityGuidelines = false
            },
            onDismiss = { showCommunityGuidelines = false },
        )
    }
}

// #9: Color picker row (scrollable — 28 swatches won't fit in a single screen)
@Composable
private fun ColorPickerRow(
    selectedColor: String?,
    onColorSelected: (String) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val selectedDescription = stringResource(R.string.a11y_selected)
    val notSelectedDescription = stringResource(R.string.a11y_not_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onClear != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClickLabel = clearWallpaperColorFilterLabel()) { onClear() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = clearWallpaperColorFilterLabel(),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        WALLHAVEN_COLORS.forEach { (hex, color) ->
            val colorLabel = wallpaperColorFilterActionLabel(hex)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .then(
                        if (selectedColor == hex) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .clickable(onClickLabel = colorLabel) { onColorSelected(hex) }
                    .semantics {
                        contentDescription = colorLabel
                        stateDescription = if (selectedColor == hex) selectedDescription else notSelectedDescription
                    },
            )
        }
    }
}

@Composable
internal fun clearWallpaperColorFilterLabel(): String = stringResource(R.string.wallpapers_clear_color_filter)

@Composable
internal fun wallpaperColorFilterActionLabel(hex: String): String =
    stringResource(R.string.wallpapers_color_filter_action, hex)

@Composable
private fun WallpaperFiltersSheet(
    selectedTab: WallpaperTab,
    discoverFilter: WallpaperDiscoverFilter,
    selectedColor: String?,
    topRange: String,
    onSelectDiscoverFilter: (WallpaperDiscoverFilter) -> Unit,
    onSelectColor: (String) -> Unit,
    onClearColor: (() -> Unit)?,
    onSelectTopRange: (String) -> Unit,
    onResetFilters: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.feed_refine_title), style = MaterialTheme.typography.titleMedium)
        if (selectedTab == WallpaperTab.DISCOVER) {
            Text(
                stringResource(R.string.wallpapers_filter_discover_mix),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WallpaperDiscoverFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = discoverFilter == filter,
                        onClick = { onSelectDiscoverFilter(filter) },
                        label = { Text(wallpaperFilterLabel(filter)) },
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = if (discoverFilter == filter) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }
        }

        Text(
            stringResource(R.string.wallpapers_filter_color_tone),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorPickerRow(
            selectedColor = selectedColor,
            onColorSelected = onSelectColor,
            onClear = onClearColor,
        )

        if (selectedTab == WallpaperTab.WALLHAVEN) {
            Text(
                stringResource(R.string.wallpapers_filter_wallhaven_time_range),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "1d" to stringResource(R.string.wallpapers_filter_range_today),
                    "1w" to stringResource(R.string.wallpapers_filter_range_week),
                    "1M" to stringResource(R.string.wallpapers_filter_range_month),
                    "6M" to stringResource(R.string.wallpapers_filter_range_6months),
                    "1y" to stringResource(R.string.wallpapers_filter_range_year),
                ).forEach { (range, label) ->
                    FilterChip(
                        selected = topRange == range,
                        onClick = { onSelectTopRange(range) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = if (topRange == range) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }
        }

        onResetFilters?.let {
            TextButton(onClick = it) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.feed_reset_filters))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WallpaperGrid(
    wallpapers: List<Wallpaper>,
    isLoadingMore: Boolean,
    columns: Int = 2,
    onWallpaperClick: (Wallpaper) -> Unit,
    onLongPress: ((Wallpaper) -> Unit)? = null,
    favoriteIdentities: Set<FavoriteIdentity> = emptySet(),
    hiddenIds: Set<String> = emptySet(),
    onUpvote: ((String) -> Unit)? = null,
    onDownvote: ((Wallpaper) -> Unit)? = null,
    voteCounts: Map<String, Int> = emptyMap(),
    onLoadMore: () -> Unit,
    isExpandedLayout: Boolean = false,
) {
    val gridState = rememberLazyStaggeredGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 6
        }
    }
    val laidOutItemCount by remember {
        derivedStateOf { gridState.layoutInfo.totalItemsCount }
    }

    val visibleWallpapers by remember(wallpapers, hiddenIds) {
        derivedStateOf {
            wallpapers
                .filter { !isWallpaperHidden(it, hiddenIds) }
        }
    }
    val inventoryStartKey = visibleWallpapers.firstOrNull()?.stableKey()
    var lastRequestedItemCount by remember(inventoryStartKey) { mutableIntStateOf(-1) }

    // Wait until Compose has laid out the newly appended inventory before deciding
    // that the viewport is still near the end. Without the layout-count guard, the
    // old layout can remain "near end" for a frame and recursively consume dozens of
    // provider pages. The item-count latch also prevents an empty/throttled response
    // from becoming a retry loop; scrolling away and back explicitly re-arms it.
    LaunchedEffect(shouldLoadMore, visibleWallpapers.size, laidOutItemCount, isLoadingMore) {
        if (!shouldLoadMore) {
            lastRequestedItemCount = -1
        } else if (shouldRequestNextWallpaperPage(
                isNearEnd = shouldLoadMore,
                isLoadingMore = isLoadingMore,
                laidOutItemCount = laidOutItemCount,
                visibleItemCount = visibleWallpapers.size,
                lastRequestedItemCount = lastRequestedItemCount,
            )
        ) {
            lastRequestedItemCount = visibleWallpapers.size
            onLoadMore()
        }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns.coerceIn(1, 4)),
        state = gridState,
        contentPadding = PaddingValues(
            start = if (isExpandedLayout) 18.dp else 8.dp,
            top = if (isExpandedLayout) 6.dp else 0.dp,
            end = if (isExpandedLayout) 18.dp else 8.dp,
            bottom = if (isExpandedLayout) 36.dp else 96.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        items(visibleWallpapers, key = { it.stableKey() }, contentType = { "wallpaper_card" }) { wallpaper ->
            val isFav = wallpaper.favoriteIdentity() in favoriteIdentities
            WallpaperCard(
                wallpaper = wallpaper,
                isFavorite = isFav,
                voteCount = voteCounts[wallpaper.stableKey()] ?: 0,
                onClick = { onWallpaperClick(wallpaper) },
                onFavoriteClick = onLongPress?.let { { it(wallpaper) } },
                onLongPress = onLongPress?.let { { it(wallpaper) } },
                onUpvote = onUpvote?.let { { it(wallpaper.stableKey()) } },
                onDownvote = onDownvote?.let { { it(wallpaper) } },
            )
        }

        if (isLoadingMore) {
            item(span = StaggeredGridItemSpan.FullLine, key = "loading_more") {
                LoadMoreIndicator()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun WallpaperCard(
    wallpaper: Wallpaper,
    isFavorite: Boolean = false,
    voteCount: Int = 0,
    onClick: () -> Unit,
    onFavoriteClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onUpvote: (() -> Unit)? = null,
    onDownvote: (() -> Unit)? = null,
) {
    val aspectRatio = if (wallpaper.width > 0 && wallpaper.height > 0) {
        wallpaper.width.toFloat() / wallpaper.height.toFloat()
    } else 0.67f
    val hints = wallpaper.qualityHints()
    val openWallpaperLabel = stringResource(R.string.wallpapers_card_open_details)
    val applyWallpaperLabel = stringResource(R.string.wallpapers_card_show_actions)
    val favoriteWallpaperLabel = if (isFavorite) stringResource(R.string.wallpapers_card_remove_favorite) else stringResource(R.string.wallpapers_card_add_favorite)
    val cardActions = buildList {
        add(CustomAccessibilityAction(openWallpaperLabel) { onClick(); true })
        onFavoriteClick?.let { toggleFavorite ->
            add(CustomAccessibilityAction(favoriteWallpaperLabel) { toggleFavorite(); true })
        }
        onUpvote?.let { upvote ->
            add(CustomAccessibilityAction(stringResource(R.string.wallpapers_card_upvote)) { upvote(); true })
        }
        onDownvote?.let { downvote ->
            add(CustomAccessibilityAction(stringResource(R.string.wallpapers_card_hide)) { downvote(); true })
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onClickLabel = openWallpaperLabel,
                onLongClick = onLongPress,
                onLongClickLabel = if (onLongPress != null) favoriteWallpaperLabel else null,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = wallpaper.cardAccessibilitySummary(
                    hints = hints,
                    isFavorite = isFavorite,
                    voteCount = voteCount,
                )
                customActions = cardActions
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box {
            SubcomposeAsyncImage(
                model = wallpaper.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio.coerceIn(0.5f, 1.0f)),
            ) {
                when (painter.state.collectAsState().value) {
                    is AsyncImagePainter.State.Loading -> {
                        ShimmerBox(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(0.dp),
                        )
                    }
                    is AsyncImagePainter.State.Error -> {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.BrokenImage, contentDescription = stringResource(R.string.wallpapers_card_failed_to_load_cd), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }

            // Favorite heart overlay (top-right)
            if (onFavoriteClick != null) {
                val heartScale by animateFloatAsState(
                    targetValue = if (isFavorite) 1.2f else 1f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
                    label = "heart",
                )
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp)),
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) stringResource(R.string.wallpapers_card_remove_favorite_short) else stringResource(R.string.wallpapers_card_add_favorite_short),
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else Color.White.copy(alpha = 0.86f),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { scaleX = heartScale; scaleY = heartScale },
                    )
                }
            }

            if (wallpaper.isAiGenerated == true) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(
                        stringResource(R.string.community_ai_generated_badge),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

internal data class WallpaperStateAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
)

@Composable
internal fun WallpaperStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    primaryAction: WallpaperStateAction? = null,
    secondaryAction: WallpaperStateAction? = null,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
        highlightHeight = 76.dp,
        shadowElevation = 2.dp,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (primaryAction != null || secondaryAction != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                primaryAction?.let { action ->
                    Button(
                        onClick = action.onClick,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(action.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(action.label)
                    }
                }
                secondaryAction?.let { action ->
                    OutlinedButton(
                        onClick = action.onClick,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(action.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(action.label)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallpaperUploadDialog(
    isUploading: Boolean,
    uploadProgress: Float,
    onUpload: (name: String, category: String, tags: List<String>, rights: CommunityUploadRights, isAiGenerated: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("other") }
    var selectedLicense by remember { mutableStateOf(COMMUNITY_UPLOAD_LICENSES.first()) }
    var sourceUrl by remember { mutableStateOf("") }
    var rightsAttested by remember { mutableStateOf(false) }
    var isAiGenerated by remember { mutableStateOf(false) }
    var tagsText by remember { mutableStateOf("") }
    val policyCopy = remember { communityUploadPolicyCopy(CommunityUploadPolicyKind.WALLPAPER) }
    val categories = listOf(
        "other" to stringResource(R.string.wallpapers_upload_category_general),
        "amoled" to stringResource(R.string.wallpapers_upload_category_amoled),
        "minimal" to stringResource(R.string.wallpapers_upload_category_minimal),
        "nature" to stringResource(R.string.wallpapers_upload_category_nature),
        "abstract" to stringResource(R.string.wallpapers_upload_category_abstract),
        "city" to stringResource(R.string.wallpapers_upload_category_city),
        "space" to stringResource(R.string.wallpapers_upload_category_space),
    )
    val parsedTags = remember(tagsText) {
        tagsText.split(',', '#')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text(stringResource(R.string.feed_upload_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.feed_upload_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = isAiGenerated,
                        onCheckedChange = { isAiGenerated = it },
                        enabled = !isUploading,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.community_ai_upload_declaration),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.community_ai_upload_declaration_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(stringResource(R.string.feed_upload_category_label), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(stringResource(R.string.feed_upload_tags_label)) },
                    placeholder = { Text(stringResource(R.string.wallpapers_upload_tags_placeholder)) },
                    supportingText = { Text(stringResource(R.string.feed_upload_tags_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.feed_upload_license_label), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    COMMUNITY_UPLOAD_LICENSES.forEach { license ->
                        FilterChip(
                            selected = selectedLicense == license,
                            onClick = { selectedLicense = license },
                            label = { Text(license) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    label = { Text(stringResource(R.string.feed_upload_source_label)) },
                    placeholder = { Text(stringResource(R.string.wallpapers_upload_source_placeholder)) },
                    supportingText = { Text(stringResource(R.string.feed_upload_source_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CommunityPolicyNotice(
                    title = policyCopy.publicTitle,
                    body = "${policyCopy.publicBody} ${policyCopy.takedownBody}",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = rightsAttested,
                        onCheckedChange = { rightsAttested = it },
                        enabled = !isUploading,
                    )
                    Text(
                        policyCopy.attestation,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isUploading) {
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.wallpapers_upload_progress_percent, (uploadProgress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onUpload(
                        name.trim(),
                        selectedCategory,
                        parsedTags,
                        CommunityUploadRights(
                            license = selectedLicense,
                            rightsAttested = rightsAttested,
                            sourceUrl = sourceUrl.trim(),
                        ),
                        isAiGenerated,
                    )
                },
                enabled = !isUploading && name.isNotBlank() && rightsAttested,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.feed_upload_button)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun FloatingActionTray(
    modifier: Modifier = Modifier,
    showUpload: Boolean,
    showThemeMatch: Boolean,
    showEyeDropper: Boolean,
    showSurprise: Boolean,
    onUpload: () -> Unit,
    onThemeMatch: () -> Unit,
    onEyeDropper: () -> Unit,
    onSurpriseMe: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (showUpload) {
            FloatingActionRow(
                icon = Icons.Default.Upload,
                label = stringResource(R.string.wallpapers_fab_upload),
                tint = MaterialTheme.colorScheme.secondary,
                onClick = onUpload,
            )
        }
        if (showThemeMatch) {
            FloatingActionRow(
                icon = Icons.Default.Palette,
                label = stringResource(R.string.wallpapers_fab_theme_match),
                tint = MaterialTheme.colorScheme.tertiary,
                onClick = onThemeMatch,
            )
        }
        if (showEyeDropper) {
            // NX-10: Android 17+ EyeDropper API entry point.
            FloatingActionRow(
                icon = Icons.Default.Colorize,
                label = stringResource(R.string.wallpapers_fab_pick_colour),
                tint = MaterialTheme.colorScheme.tertiary,
                onClick = onEyeDropper,
            )
        }
        if (showSurprise) {
            FloatingActionRow(
                icon = Icons.Default.Shuffle,
                label = stringResource(R.string.wallpapers_fab_surprise_me),
                tint = MaterialTheme.colorScheme.primary,
                onClick = onSurpriseMe,
            )
        }
    }
}

@Composable
private fun FloatingActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.16f)),
        shadowElevation = 6.dp,
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

@Composable
private fun wallpaperHeaderEyebrow(
    tab: WallpaperTab,
    discoverFilter: WallpaperDiscoverFilter,
): String = when (tab) {
    WallpaperTab.DISCOVER -> when (discoverFilter) {
        WallpaperDiscoverFilter.FOR_YOU -> stringResource(R.string.wallpapers_header_curated)
        else -> stringResource(R.string.wallpapers_header_discover_mix, wallpaperFilterLabel(discoverFilter))
    }
    WallpaperTab.SEARCH -> stringResource(R.string.wallpapers_header_cross_source)
    WallpaperTab.COLOR -> stringResource(R.string.wallpapers_header_color_browsing)
    WallpaperTab.COMMUNITY -> stringResource(R.string.wallpapers_header_community_uploads)
    else -> stringResource(R.string.wallpapers_header_source, wallpaperTabLabel(tab))
}

@Composable
private fun wallpaperHeaderTitle(
    tab: WallpaperTab,
    query: String,
): String = when (tab) {
    WallpaperTab.DISCOVER -> stringResource(R.string.nav_wallpapers)
    WallpaperTab.SEARCH -> if (query.isNotBlank()) stringResource(R.string.wallpapers_header_search_results_query, query) else stringResource(R.string.wallpapers_header_search_results)
    WallpaperTab.COLOR -> stringResource(R.string.wallpapers_header_browse_by_tone)
    WallpaperTab.COMMUNITY -> stringResource(R.string.wallpapers_header_community_title)
    else -> wallpaperTabLabel(tab)
}

@Composable
private fun wallpaperHeaderSubtitle(
    tab: WallpaperTab,
    discoverFilter: WallpaperDiscoverFilter,
    query: String,
    selectedColor: String?,
    wallpaperCount: Int,
): String = when (tab) {
    WallpaperTab.DISCOVER -> when {
        wallpaperCount > 0 && discoverFilter == WallpaperDiscoverFilter.FOR_YOU ->
            stringResource(R.string.wallpapers_subtitle_discover_count, wallpaperCount)
        discoverFilter != WallpaperDiscoverFilter.FOR_YOU ->
            stringResource(R.string.wallpapers_subtitle_discover_filter, wallpaperFilterLabel(discoverFilter).lowercase(java.util.Locale.ROOT))
        else ->
            stringResource(R.string.wallpapers_subtitle_discover_default)
    }
    WallpaperTab.SEARCH -> if (query.isNotBlank()) {
        stringResource(R.string.wallpapers_subtitle_search_active)
    } else {
        stringResource(R.string.wallpapers_subtitle_search_default)
    }
    WallpaperTab.COLOR -> if (selectedColor != null) {
        stringResource(R.string.wallpapers_subtitle_color_active)
    } else {
        stringResource(R.string.wallpapers_subtitle_color_default)
    }
    WallpaperTab.NEWEST -> stringResource(R.string.wallpapers_subtitle_newest)
    WallpaperTab.WALLHAVEN -> stringResource(R.string.wallpapers_subtitle_wallhaven)
    WallpaperTab.PEXELS -> stringResource(R.string.wallpapers_subtitle_pexels)
    WallpaperTab.PIXABAY -> stringResource(R.string.wallpapers_subtitle_pixabay)
    WallpaperTab.REDDIT -> stringResource(R.string.wallpapers_subtitle_reddit)
    WallpaperTab.COMMUNITY -> stringResource(R.string.wallpapers_subtitle_community)
}

private fun wallpaperTabIcon(tab: WallpaperTab): androidx.compose.ui.graphics.vector.ImageVector = when (tab) {
    WallpaperTab.DISCOVER -> Icons.Default.Explore
    WallpaperTab.NEWEST -> Icons.Default.Schedule
    WallpaperTab.PEXELS -> Icons.Default.PhotoLibrary
    WallpaperTab.PIXABAY -> Icons.Default.Collections
    WallpaperTab.REDDIT -> Icons.Default.Public
    WallpaperTab.WALLHAVEN -> Icons.Default.ImageSearch
    WallpaperTab.COMMUNITY -> Icons.Default.Groups
    WallpaperTab.COLOR -> Icons.Default.Palette
    WallpaperTab.SEARCH -> Icons.Default.Search
}

@Composable
private fun wallpaperTabLabel(tab: WallpaperTab): String =
    when (tab) {
        WallpaperTab.DISCOVER -> stringResource(R.string.wallpapers_tab_discover)
        WallpaperTab.NEWEST -> stringResource(R.string.browse_rail_newest)
        WallpaperTab.PEXELS -> stringResource(R.string.wallpapers_tab_pexels)
        WallpaperTab.PIXABAY -> stringResource(R.string.wallpapers_tab_pixabay)
        WallpaperTab.REDDIT -> stringResource(R.string.wallpapers_tab_reddit)
        WallpaperTab.WALLHAVEN -> stringResource(R.string.wallpapers_tab_wallhaven)
        WallpaperTab.COMMUNITY -> stringResource(R.string.wallpapers_tab_community)
        WallpaperTab.COLOR -> stringResource(R.string.wallpapers_tab_color)
        WallpaperTab.SEARCH -> stringResource(R.string.wallpapers_tab_search)
    }

@Composable
private fun wallpaperFilterLabel(filter: WallpaperDiscoverFilter): String = when (filter) {
    WallpaperDiscoverFilter.FOR_YOU -> stringResource(R.string.wallpapers_discover_for_you)
    WallpaperDiscoverFilter.AMOLED -> stringResource(R.string.wallpapers_discover_amoled)
    WallpaperDiscoverFilter.HIGH_RES -> stringResource(R.string.wallpapers_discover_high_res)
    WallpaperDiscoverFilter.PORTRAIT -> stringResource(R.string.wallpapers_discover_portrait)
    WallpaperDiscoverFilter.ICON_SAFE -> stringResource(R.string.wallpapers_discover_icon_safe)
}

@Composable
private fun wallpaperTopRangeLabel(range: String): String = when (range) {
    "1d" -> stringResource(R.string.wallpapers_filter_range_today)
    "1w" -> stringResource(R.string.wallpapers_filter_range_week)
    "1M" -> stringResource(R.string.wallpapers_filter_range_month)
    "6M" -> stringResource(R.string.wallpapers_filter_range_6months)
    "1y" -> stringResource(R.string.wallpapers_filter_range_year)
    else -> range
}

private fun parseHexColor(hex: String): Color {
    val normalized = hex.removePrefix("#")
    return runCatching {
        Color(android.graphics.Color.parseColor("#$normalized"))
    }.getOrElse { Color(0xFF7C8CFF) }
}

internal data class VisibleWallpaperSections(
    val dailyPick: Wallpaper?,
    val topVoted: List<Pair<Wallpaper, Int>>,
    val feedWallpapers: List<Wallpaper>,
    val pagerWallpapers: List<Wallpaper>,
    val hasRenderableContent: Boolean,
)

internal fun shouldRequestNextWallpaperPage(
    isNearEnd: Boolean,
    isLoadingMore: Boolean,
    laidOutItemCount: Int,
    visibleItemCount: Int,
    lastRequestedItemCount: Int,
): Boolean =
    isNearEnd &&
        !isLoadingMore &&
        laidOutItemCount >= visibleItemCount &&
        lastRequestedItemCount != visibleItemCount

internal data class WallpaperPagerItems(
    val wallpapers: List<Wallpaper>,
    val initialPage: Int,
)

internal fun computeVisibleWallpaperSections(
    wallpapers: List<Wallpaper>,
    hiddenIds: Set<String>,
    topVoted: List<Pair<Wallpaper, Int>>,
    dailyPick: Wallpaper?,
    isDiscoverTab: Boolean,
): VisibleWallpaperSections {
    val visibleDailyPick = dailyPick?.takeIf { !isWallpaperHidden(it, hiddenIds) && isDiscoverTab }
    val dailyKey = visibleDailyPick?.stableKey()
    val visibleTopVoted = if (isDiscoverTab) {
        topVoted
            .filter { (wallpaper, _) -> !isWallpaperHidden(wallpaper, hiddenIds) }
            .distinctBy { (wallpaper, _) -> wallpaper.stableKey() }
            .filterNot { (wallpaper, _) -> wallpaper.stableKey() == dailyKey }
    } else {
        emptyList()
    }
    val featuredKeys = buildSet {
        dailyKey?.let(::add)
        addAll(visibleTopVoted.map { (wallpaper, _) -> wallpaper.stableKey() })
    }
    val feedWallpapers = wallpapers
        .filter { !isWallpaperHidden(it, hiddenIds) }
        .distinctBy { it.stableKey() }
        .filterNot { it.stableKey() in featuredKeys }
    val featuredWallpapers = buildList {
        visibleDailyPick?.let(::add)
        addAll(visibleTopVoted.map { (wallpaper, _) -> wallpaper })
    }.distinctBy { it.stableKey() }
    val pagerWallpapers = if (isDiscoverTab) {
        // Preserve ranking inside each group while making the source hierarchy
        // unambiguous: every Reddit card precedes every non-Reddit feature/provider.
        buildList {
            addAll(feedWallpapers.filter { it.source == ContentSource.REDDIT })
            addAll(featuredWallpapers.filter { it.source == ContentSource.REDDIT })
            addAll(featuredWallpapers.filterNot { it.source == ContentSource.REDDIT })
            addAll(feedWallpapers.filterNot { it.source == ContentSource.REDDIT })
        }.distinctBy { it.stableKey() }
    } else {
        feedWallpapers
    }

    return VisibleWallpaperSections(
        dailyPick = visibleDailyPick,
        topVoted = visibleTopVoted,
        feedWallpapers = feedWallpapers,
        pagerWallpapers = pagerWallpapers,
        hasRenderableContent = pagerWallpapers.isNotEmpty(),
    )
}

internal fun computeWallpaperPagerItems(
    currentWallpaper: Wallpaper,
    sharedWallpapers: List<Wallpaper>,
    hiddenIds: Set<String>,
    sharedListAnchorKey: String? = null,
): WallpaperPagerItems {
    val currentKey = currentWallpaper.stableKey()
    val sharedContainsCurrent = sharedWallpapers.any { it.stableKey() == currentKey }
    val includeSharedList = sharedContainsCurrent &&
        (sharedListAnchorKey == null || sharedListAnchorKey == currentKey)
    val wallpapers = (if (includeSharedList) sharedWallpapers else listOf(currentWallpaper))
        .distinctBy { it.stableKey() }
        .filter { !isWallpaperHidden(it, hiddenIds) }
    val initialPage = wallpapers.indexOfFirst { it.stableKey() == currentKey }
        .takeIf { it >= 0 }
        ?: 0
    return WallpaperPagerItems(wallpapers = wallpapers, initialPage = initialPage)
}

internal fun isWallpaperHidden(
    wallpaper: Wallpaper,
    hiddenIds: Set<String>,
): Boolean = matchesHiddenIds(hiddenIds, wallpaper.stableKey(), wallpaper.id)
