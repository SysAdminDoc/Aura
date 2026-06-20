package com.freevibe.ui.screens.sounds

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.COMMUNITY_UPLOAD_LICENSES
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.Sound
import com.freevibe.data.model.SoundAction
import com.freevibe.data.model.SoundActionDecision
import com.freevibe.data.model.SoundLicenseCapabilities
import com.freevibe.data.model.soundLicenseCapabilities
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.matchesHiddenIds
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraScreenHeader
import com.freevibe.ui.components.CompactSearchField
import com.freevibe.ui.components.CommunityGuidelinesDialog
import com.freevibe.ui.components.CountBadge
import com.freevibe.ui.components.CommunityPolicyNotice
import com.freevibe.ui.components.SearchHistoryDropdown
import com.freevibe.ui.components.ShimmerSoundList
import com.freevibe.ui.components.AuraStatusAction
import com.freevibe.ui.components.AuraStatusBanner
import com.freevibe.ui.policy.CommunityUploadPolicyKind
import com.freevibe.ui.policy.communityUploadPolicyCopy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsScreen(
    onSoundClick: (Sound) -> Unit,
    onCreateRingtone: (Uri) -> Unit = {},
    initialQuery: String? = null,
    viewModel: SoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val previewReadyIds by viewModel.previewReadyIds.collectAsStateWithLifecycle()
    val topHits by viewModel.topHits.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val youtubeProviderEnabled by viewModel.youtubeProviderEnabled.collectAsStateWithLifecycle()
    val communityProviderEnabled by viewModel.communityProviderEnabled.collectAsStateWithLifecycle()
    val communityGuidelinesAccepted by viewModel.communityGuidelinesAccepted.collectAsStateWithLifecycle()
    val hiddenIds by viewModel.hiddenIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val communityVoteIds = remember(state.sounds, state.selectedTab, communityProviderEnabled) {
        if (communityProviderEnabled && state.selectedTab == SoundTab.COMMUNITY) {
            state.sounds.map { it.stableKey() }.distinct()
        } else {
            emptyList()
        }
    }
    val communityVoteFlow = remember(communityVoteIds) {
        if (communityVoteIds.isEmpty()) flowOf(emptyMap<String, Int>()) else viewModel.voteRepo.getVoteCounts(communityVoteIds)
    }
    val voteCounts by communityVoteFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val displaySounds = remember(state.sounds, state.selectedTab, hiddenIds, voteCounts, communityProviderEnabled) {
        if (communityProviderEnabled && state.selectedTab == SoundTab.COMMUNITY) {
            state.sounds
                .filter { !matchesHiddenIds(hiddenIds, it.stableKey(), it.id) }
                .sortedByDescending { voteCounts[it.stableKey()] ?: 0 }
        } else {
            state.sounds
        }
    }
    val displayTopHits = remember(topHits, displaySounds, voteCounts, state.selectedTab, state.query, state.qualityFilter, communityProviderEnabled) {
        when {
            state.selectedTab == SoundTab.RINGTONES && state.query.isBlank() -> {
                rankSounds(topHits, SoundTab.RINGTONES, state.qualityFilter).take(5)
            }
            communityProviderEnabled && state.selectedTab == SoundTab.COMMUNITY && state.query.isBlank() -> {
                displaySounds
                    .filter { (voteCounts[it.stableKey()] ?: 0) > 0 }
                    .take(5)
            }
            else -> emptyList()
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    LaunchedEffect(state.query) { searchQuery = state.query }
    LaunchedEffect(youtubeProviderEnabled, communityProviderEnabled, state.selectedTab) {
        if (!youtubeProviderEnabled && state.selectedTab == SoundTab.YOUTUBE) {
            viewModel.selectTab(SoundTab.RINGTONES)
        }
        if (!communityProviderEnabled && state.selectedTab == SoundTab.COMMUNITY) {
            viewModel.selectTab(SoundTab.RINGTONES)
        }
    }
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && state.query != initialQuery) {
            viewModel.search(initialQuery)
        }
    }
    var showSearchHistory by remember { mutableStateOf(false) }
    var showCommunityGuidelines by remember { mutableStateOf(false) }
    var quickApplySound by remember { mutableStateOf<Sound?>(null) }
    var quickApplyActionInFlight by remember { mutableStateOf(false) }
    var quickApplyObservedApplying by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }
    var showRecordPermissionPrompt by remember { mutableStateOf(false) }
    var showRecordPermissionRecovery by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var nonBlockingWarning by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var writeSettingsRefresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                writeSettingsRefresh += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val canWriteSettings = remember(writeSettingsRefresh, state.isApplying) { viewModel.canWriteSettings() }
    val canOpenWriteSettings = remember(writeSettingsRefresh) { viewModel.canOpenWriteSettings() }
    val writeSettingsUnavailable = stringResource(R.string.write_settings_unavailable)
    fun openWriteSettings() {
        if (!canOpenWriteSettings) {
            scope.launch { snackbarHostState.showSnackbar(writeSettingsUnavailable) }
            return
        }
        runCatching { context.startActivity(viewModel.requestWriteSettings()) }
            .onFailure { scope.launch { snackbarHostState.showSnackbar(writeSettingsUnavailable) } }
    }
    val isYouTubeTab = state.selectedTab == SoundTab.YOUTUBE
    val soundFilterCount = remember(state.qualityFilter) {
        if (state.qualityFilter != SoundQualityFilter.BEST) 1 else 0
    }

    // Upload state
    var showUploadDialog by remember { mutableStateOf(false) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var awaitingUploadResult by remember { mutableStateOf(false) }
    val uploadAudioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (communityProviderEnabled && communityGuidelinesAccepted && uri != null) {
            selectedAudioUri = uri
            showUploadDialog = true
        } else if (communityProviderEnabled && uri != null) {
            selectedAudioUri = uri
            showCommunityGuidelines = true
        }
    }
    val createAudioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onCreateRingtone) }
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startCommunityRecording()
        } else {
            viewModel.reportRecordingPermissionDenied()
            showRecordPermissionRecovery = true
        }
    }
    val startRecording: () -> Unit = {
        if (!communityProviderEnabled) {
            viewModel.startCommunityRecording()
        } else if (!communityGuidelinesAccepted) {
            showCommunityGuidelines = true
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startCommunityRecording()
        } else {
            showRecordPermissionPrompt = true
        }
    }

    if (showRecordPermissionPrompt) {
        AlertDialog(
            onDismissRequest = { showRecordPermissionPrompt = false },
            title = { Text(stringResource(R.string.permission_microphone_title)) },
            text = { Text(stringResource(R.string.permission_microphone_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRecordPermissionPrompt = false
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) {
                    Text(stringResource(R.string.permission_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordPermissionPrompt = false }) {
                    Text(stringResource(R.string.permission_not_now))
                }
            },
        )
    }

    if (showRecordPermissionRecovery) {
        PermissionRecoveryDialog(
            title = stringResource(R.string.permission_microphone_title),
            body = stringResource(R.string.permission_microphone_denied_body),
            onOpenSettings = {
                showRecordPermissionRecovery = false
                openAuraAppSettings(context)
            },
            onDismiss = { showRecordPermissionRecovery = false },
        )
    }

    LaunchedEffect(state.recordedUploadUri) {
        state.recordedUploadUri?.let { uri ->
            if (communityGuidelinesAccepted) {
                selectedAudioUri = uri
                showUploadDialog = true
                viewModel.consumeRecordedUpload()
            } else {
                selectedAudioUri = uri
                viewModel.consumeRecordedUpload()
                showCommunityGuidelines = true
            }
        }
    }

    if (state.isRecordingUpload) {
        RecordingDialog(
            startedAtMs = state.recordingStartedAtMs,
            onStop = viewModel::stopCommunityRecording,
            onDiscard = viewModel::discardCommunityRecording,
        )
    }

    // Upload dialog
    val uploadUri = selectedAudioUri
    if (communityProviderEnabled && communityGuidelinesAccepted && showUploadDialog && uploadUri != null) {
        UploadDialog(
            isUploading = state.isUploading,
            uploadProgress = state.uploadProgress,
            onUpload = { name, category, tags, rights ->
                awaitingUploadResult = true
                viewModel.uploadSound(uploadUri, name, category, tags, rights)
            },
            onDismiss = {
                if (!state.isUploading) {
                    showUploadDialog = false; selectedAudioUri = null
                    awaitingUploadResult = false
                }
            },
        )
        // Auto-dismiss when upload completes
        LaunchedEffect(state.isUploading, state.applySuccess, state.error) {
            if (awaitingUploadResult && !state.isUploading) {
                awaitingUploadResult = false
            }
            if (!state.isUploading && showUploadDialog && state.applySuccess == "Upload complete") {
                showUploadDialog = false
                selectedAudioUri = null
            }
        }
    }

    // Quick Apply bottom sheet
    val currentQuickApplySound = quickApplySound
    if (currentQuickApplySound != null) {
        QuickApplySheet(
            sound = currentQuickApplySound,
            canApply = canWriteSettings,
            canOpenPermissionSettings = canOpenWriteSettings,
            isApplying = state.isApplying,
            onApply = { sound, type ->
                quickApplyActionInFlight = true
                quickApplyObservedApplying = false
                viewModel.applySound(sound, type, confirmed = true)
            },
            onDownload = { viewModel.downloadSound(it, confirmed = true); quickApplySound = null },
            onGrantPermission = ::openWriteSettings,
            onDismiss = {
                if (!state.isApplying) {
                    quickApplySound = null
                    quickApplyActionInFlight = false
                    quickApplyObservedApplying = false
                }
            },
        )
        LaunchedEffect(quickApplyActionInFlight, state.isApplying, state.applySuccess, state.error) {
            if (quickApplyActionInFlight && state.isApplying) {
                quickApplyObservedApplying = true
            }
            if (
                quickApplyActionInFlight &&
                quickApplyObservedApplying &&
                !state.isApplying &&
                (state.applySuccess != null || state.error != null)
            ) {
                quickApplySound = null
                quickApplyActionInFlight = false
                quickApplyObservedApplying = false
            }
        }
    }

    // Snackbar for success/error feedback
    LaunchedEffect(state.applySuccess) {
        state.applySuccess?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() }
    }
    LaunchedEffect(state.error) {
        if (displaySounds.isNotEmpty() || displayTopHits.isNotEmpty()) {
            state.error?.let {
                nonBlockingWarning = it
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (communityProviderEnabled && state.selectedTab == SoundTab.COMMUNITY) {
                    SmallFloatingActionButton(
                        onClick = startRecording,
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(Icons.Default.Mic, "Record community sound", modifier = Modifier.size(20.dp))
                    }
                }
                if (communityProviderEnabled) {
                    SmallFloatingActionButton(
                        onClick = { uploadAudioPickerLauncher.launch("audio/*") },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Icon(Icons.Default.Upload, "Upload community sound", modifier = Modifier.size(20.dp))
                    }
                }
                SmallFloatingActionButton(
                    onClick = { createAudioPickerLauncher.launch("audio/*") },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(Icons.Default.ContentCut, "Create from music", modifier = Modifier.size(20.dp))
                }
            }
        },
    ) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            AuraScreenHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                label = soundHeaderLabel(state.selectedTab),
                icon = soundTabIcon(state.selectedTab),
                title = soundHeaderTitle(state.selectedTab, state.query),
                subtitle = soundHeaderSubtitle(
                    tab = state.selectedTab,
                    query = state.query,
                    resultCount = displaySounds.size + displayTopHits.size,
                ),
                tint = if (isYouTubeTab) Color(0xFFFF6A5B) else MaterialTheme.colorScheme.secondary,
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; showSearchHistory = it.isEmpty() },
                            placeholder = when {
                                isYouTubeTab -> "Search YouTube or paste URL..."
                                youtubeProviderEnabled -> "Search YouTube sounds"
                                else -> "Search sounds"
                            },
                            leadingIcon = if (isYouTubeTab) Icons.Default.SmartDisplay else Icons.Default.Search,
                            leadingTint = if (isYouTubeTab) Color(0xFFFF6A5B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            onClear = {
                                searchQuery = ""
                                showSearchHistory = false
                                focusManager.clearFocus()
                                when (state.selectedTab) {
                                    SoundTab.SEARCH -> viewModel.clearSearchMode()
                                    SoundTab.YOUTUBE -> viewModel.clearYouTubeSearch()
                                    else -> Unit
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    if (isYouTubeTab && isYouTubeUrl(searchQuery)) {
                                        viewModel.importYouTubeUrl(searchQuery)
                                    } else if (isYouTubeTab) {
                                        viewModel.searchYouTube(searchQuery)
                                    } else {
                                        viewModel.search(searchQuery)
                                    }
                                }
                                showSearchHistory = false
                                focusManager.clearFocus()
                            }),
                        )
                        SoundFilterButton(
                            filterCount = soundFilterCount,
                            onClick = { showFiltersSheet = true },
                        )
                    }
                    SearchHistoryDropdown(
                        recentQueries = recentSearches,
                        isVisible = showSearchHistory && searchQuery.isEmpty(),
                        onQueryClick = {
                            searchQuery = it
                            if (isYouTubeTab) viewModel.searchYouTube(it) else viewModel.search(it)
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

                Spacer(Modifier.height(6.dp))
                SoundModeBar(
                    selectedTab = state.selectedTab,
                    youtubeProviderEnabled = youtubeProviderEnabled,
                    communityProviderEnabled = communityProviderEnabled,
                    onSelectTab = { tab ->
                        if (tab == SoundTab.COMMUNITY && communityProviderEnabled && !communityGuidelinesAccepted) {
                            showCommunityGuidelines = true
                        } else {
                            viewModel.selectTab(tab)
                        }
                    },
                )
            }

            nonBlockingWarning?.let { warning ->
                AuraStatusBanner(
                    icon = Icons.Default.GraphicEq,
                    title = "Showing last good sound results",
                    message = "$warning. Playback and saved results remain available while Aura refreshes.",
                    tone = MaterialTheme.colorScheme.tertiary,
                    primaryAction = AuraStatusAction(
                        label = "Refresh",
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.refresh() },
                    ),
                    secondaryAction = AuraStatusAction(
                        label = "Dismiss",
                        icon = Icons.Default.Close,
                        onClick = { nonBlockingWarning = null },
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            if (state.degradedSources.isNotEmpty() && nonBlockingWarning == null) {
                AuraStatusBanner(
                    icon = Icons.Default.GraphicEq,
                    title = "Some sound sources are temporarily unavailable",
                    message = soundSourceHealthSummary(state.degradedSources),
                    tone = MaterialTheme.colorScheme.tertiary,
                    primaryAction = AuraStatusAction(
                        label = "Refresh",
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.refresh() },
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.error != null && displaySounds.isEmpty() && displayTopHits.isEmpty() && !state.isLoading && !state.isRefreshing) {
                    AuraStateCard(
                        icon = Icons.Default.CloudOff,
                        title = "Sounds could not refresh",
                        description = state.error ?: "Aura could not refresh sound sources. Retry, or switch tabs to keep browsing saved picks.",
                        tone = MaterialTheme.colorScheme.error,
                        primaryAction = AuraStateAction(
                            label = "Retry",
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.refresh() },
                        ),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                } else {
                    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = { viewModel.refresh() }) {
                        SoundsList(
                            sounds = displaySounds,
                            selectedTab = state.selectedTab,
                            query = state.query,
                            isLoading = state.isLoading,
                            isRefreshing = state.isRefreshing,
                            playingId = state.playingId,
                            resolvingId = state.resolvingId,
                            isLoadingMore = state.isLoadingMore,
                            hasMore = state.hasMore,
                            previewReadyIds = previewReadyIds,
                            filterKey = state.filterKey,
                            onSoundClick = { viewModel.selectSound(it); onSoundClick(it) },
                            onLongPress = { quickApplySound = it },
                            onPlayClick = { viewModel.togglePlayback(it) },
                            onLoadMore = { viewModel.loadMore() },
                            playbackProgress = playbackProgress,
                            topHits = displayTopHits,
                            voteCounts = voteCounts,
                            collections = if (state.query.isBlank()) {
                                val base = soundCollectionsFor(state.selectedTab)
                                val seasonal = viewModel.seasonalTheme
                                if (seasonal != null && base.isNotEmpty()) {
                                    val seasonalSpec = SoundCollectionSpec(
                                        title = seasonal.title,
                                        subtitle = seasonal.subtitle,
                                        query = seasonal.soundQuery,
                                        tone = SoundCollectionTone.SEASONAL,
                                    )
                                    listOf(seasonalSpec) + base
                                } else {
                                    base
                                }
                            } else emptyList(),
                            onCollectionClick = { collection -> viewModel.search(collection.query) },
                            onUploadClick = if (communityProviderEnabled) ({
                                if (communityGuidelinesAccepted) {
                                    uploadAudioPickerLauncher.launch("audio/*")
                                } else {
                                    showCommunityGuidelines = true
                                }
                            }) else null,
                            onRecordClick = if (communityProviderEnabled) startRecording else null,
                            onUpvote = if (communityProviderEnabled) ({ sound ->
                                if (communityGuidelinesAccepted) viewModel.upvote(sound.stableKey()) else showCommunityGuidelines = true
                            }) else null,
                            onDownvote = if (communityProviderEnabled) ({ sound ->
                                if (communityGuidelinesAccepted) viewModel.downvote(sound.stableKey()) else showCommunityGuidelines = true
                            }) else null,
                        )
                    }
                }
            }
        }
    }

    if (showFiltersSheet) {
        ModalBottomSheet(onDismissRequest = { showFiltersSheet = false }) {
            SoundFiltersSheet(
                qualityFilter = state.qualityFilter,
                onSelectQuality = { filter ->
                    viewModel.setQualityFilter(filter)
                    showFiltersSheet = false
                },
                onReset = if (soundFilterCount > 0) {
                    {
                        viewModel.setQualityFilter(SoundQualityFilter.BEST)
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
                if (selectedAudioUri != null) showUploadDialog = true
                showCommunityGuidelines = false
            },
            onDismiss = { showCommunityGuidelines = false },
        )
    }
}

private fun isYouTubeUrl(text: String): Boolean {
    val t = text.trim()
    return t.contains("youtube.com/") || t.contains("youtu.be/") || t.contains("youtube.com/shorts/")
}

internal val coreSoundTabs: List<SoundTab> = listOf(
    SoundTab.RINGTONES,
    SoundTab.NOTIFICATIONS,
    SoundTab.ALARMS,
)

internal fun secondarySoundTabs(
    selectedTab: SoundTab,
    youtubeProviderEnabled: Boolean = true,
    communityProviderEnabled: Boolean = true,
): List<SoundTab> = buildList {
    if (youtubeProviderEnabled || selectedTab == SoundTab.YOUTUBE) add(SoundTab.YOUTUBE)
    if (communityProviderEnabled || selectedTab == SoundTab.COMMUNITY) add(SoundTab.COMMUNITY)
    if (selectedTab == SoundTab.SEARCH) add(SoundTab.SEARCH)
}

private fun soundTabLabel(tab: SoundTab): String = when (tab) {
    SoundTab.RINGTONES -> "Ringtones"
    SoundTab.NOTIFICATIONS -> "Notifications"
    SoundTab.ALARMS -> "Alarms"
    SoundTab.YOUTUBE -> "YouTube"
    SoundTab.COMMUNITY -> "Community"
    SoundTab.SEARCH -> "Search"
}

private fun soundTabIcon(tab: SoundTab): androidx.compose.ui.graphics.vector.ImageVector = when (tab) {
    SoundTab.RINGTONES -> Icons.Default.Call
    SoundTab.NOTIFICATIONS -> Icons.Default.Notifications
    SoundTab.ALARMS -> Icons.Default.Alarm
    SoundTab.YOUTUBE -> Icons.Default.SmartDisplay
    SoundTab.COMMUNITY -> Icons.Default.Groups
    SoundTab.SEARCH -> Icons.Default.Search
}

private fun soundHeaderLabel(tab: SoundTab): String = when (tab) {
    SoundTab.RINGTONES -> "Ringtone studio"
    SoundTab.NOTIFICATIONS -> "Notification sounds"
    SoundTab.ALARMS -> "Alarm tones"
    SoundTab.YOUTUBE -> "YouTube import"
    SoundTab.COMMUNITY -> "Community library"
    SoundTab.SEARCH -> "Sound search"
}

private fun soundHeaderTitle(tab: SoundTab, query: String): String = when {
    tab == SoundTab.SEARCH && query.isNotBlank() -> "Results for \"$query\""
    tab == SoundTab.YOUTUBE && query.isNotBlank() -> "YouTube results"
    tab == SoundTab.YOUTUBE -> "Find sound from YouTube"
    tab == SoundTab.COMMUNITY -> "Browse community sounds"
    tab == SoundTab.NOTIFICATIONS -> "Short alerts that stay clear"
    tab == SoundTab.ALARMS -> "Alarm tones with presence"
    else -> "Ringtone-ready sounds"
}

private fun soundHeaderSubtitle(
    tab: SoundTab,
    query: String,
    resultCount: Int,
): String = when {
    tab == SoundTab.YOUTUBE && query.isBlank() ->
        "Paste a video URL or search for short clips Aura can preview, trim, and apply."
    tab == SoundTab.COMMUNITY && resultCount == 0 ->
        "Upload, record, or browse user-shared clips once community content is available."
    tab == SoundTab.SEARCH && query.isNotBlank() ->
        "$resultCount result${if (resultCount == 1) "" else "s"} matched your search."
    resultCount > 0 ->
        "$resultCount curated result${if (resultCount == 1) "" else "s"} ready to preview or apply."
    else ->
        "Preview, trim, download, or long-press a result for quick apply."
}

@Composable
private fun SoundFilterButton(
    filterCount: Int,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box {
            Icon(Icons.Default.Tune, contentDescription = "Refine sounds", modifier = Modifier.size(18.dp))
            CountBadge(
                count = filterCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-8).dp),
            )
        }
    }
}

@Composable
private fun SoundModeBar(
    selectedTab: SoundTab,
    youtubeProviderEnabled: Boolean,
    communityProviderEnabled: Boolean,
    onSelectTab: (SoundTab) -> Unit,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val secondaryTabs = remember(selectedTab, youtubeProviderEnabled, communityProviderEnabled) {
        secondarySoundTabs(
            selectedTab = selectedTab,
            youtubeProviderEnabled = youtubeProviderEnabled,
            communityProviderEnabled = communityProviderEnabled,
        )
    }
    val secondarySelected = selectedTab in secondaryTabs

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        coreSoundTabs.forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                label = { Text(soundTabLabel(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(8.dp),
            )
        }

        if (secondaryTabs.isNotEmpty()) {
            Box {
                FilterChip(
                    selected = secondarySelected,
                    onClick = { showMoreMenu = true },
                    label = {
                        Text(
                            if (secondarySelected) soundTabLabel(selectedTab) else "More",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(8.dp),
                )
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                ) {
                    secondaryTabs.forEach { tab ->
                        DropdownMenuItem(
                            text = { Text(soundTabLabel(tab)) },
                            onClick = {
                                showMoreMenu = false
                                onSelectTab(tab)
                            },
                            leadingIcon = {
                                Icon(
                                    if (selectedTab == tab) Icons.Default.Check else soundTabIcon(tab),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

// -- Sounds List --

@Composable
private fun SoundsList(
    sounds: List<Sound>,
    selectedTab: SoundTab,
    query: String,
    isLoading: Boolean,
    isRefreshing: Boolean,
    playingId: String?,
    resolvingId: String?,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    previewReadyIds: Set<String>,
    filterKey: Int,
    onSoundClick: (Sound) -> Unit,
    onLongPress: (Sound) -> Unit,
    onPlayClick: (Sound) -> Unit,
    onLoadMore: () -> Unit,
    playbackProgress: Float,
    topHits: List<Sound>,
    voteCounts: Map<String, Int> = emptyMap(),
    collections: List<SoundCollectionSpec>,
    onCollectionClick: (SoundCollectionSpec) -> Unit,
    onUploadClick: (() -> Unit)? = null,
    onRecordClick: (() -> Unit)? = null,
    onUpvote: ((Sound) -> Unit)? = null,
    onDownvote: ((Sound) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(filterKey) { listState.scrollToItem(0) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 5 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore) {
        if (hasMore && shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (collections.isNotEmpty()) {
            item(key = "sound_collections", contentType = "collections") {
                SoundCollectionCarousel(
                    collections = collections,
                    onCollectionClick = onCollectionClick,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }

        // Featured sound section for ringtones and ranked community uploads.
        if (topHits.isNotEmpty()) {
            item(key = "tophits_header", contentType = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    Icon(
                        if (selectedTab == SoundTab.COMMUNITY) Icons.Default.Groups else Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = if (selectedTab == SoundTab.COMMUNITY) "Community picks" else "Trending",
                        Modifier.size(20.dp),
                        tint = if (selectedTab == SoundTab.COMMUNITY) MaterialTheme.colorScheme.primary else Color(0xFFFF4444),
                    )
                    Text(
                        if (selectedTab == SoundTab.COMMUNITY) "Community Picks" else "Top 5 This Week",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            items(topHits, key = { "hit_${it.stableKey()}" }, contentType = { "sound_card" }) { sound ->
                SoundCard(
                    sound = sound,
                    tab = if (selectedTab == SoundTab.COMMUNITY) SoundTab.COMMUNITY else SoundTab.RINGTONES,
                    isPlaying = playingId == sound.stableKey(),
                    isResolving = sound.stableKey() == resolvingId,
                    isPreviewReady = sound.stableKey() in previewReadyIds,
                    playbackProgress = if (playingId == sound.stableKey()) playbackProgress else 0f,
                    voteCount = voteCounts[sound.stableKey()],
                    onClick = { onSoundClick(sound) },
                    onLongPress = { onLongPress(sound) },
                    onPlayClick = { onPlayClick(sound) },
                    onUpvote = onUpvote?.let { { it(sound) } },
                    onDownvote = onDownvote?.let { { it(sound) } },
                )
            }
            item(key = "tophits_divider", contentType = "divider") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }

        // Main list
        val topHitIds = topHits.map { it.stableKey() }.toSet()
        val filteredSounds = sounds.filter { it.stableKey() !in topHitIds }

        items(filteredSounds, key = { it.stableKey() }, contentType = { "sound_card" }) { sound ->
            SoundCard(
                sound = sound,
                tab = selectedTab,
                isPlaying = playingId == sound.stableKey(),
                isResolving = sound.stableKey() == resolvingId,
                isPreviewReady = sound.stableKey() in previewReadyIds,
                playbackProgress = if (playingId == sound.stableKey()) playbackProgress else 0f,
                voteCount = voteCounts[sound.stableKey()],
                onClick = { onSoundClick(sound) },
                onLongPress = { onLongPress(sound) },
                onPlayClick = { onPlayClick(sound) },
                onUpvote = onUpvote?.let { { it(sound) } },
                onDownvote = onDownvote?.let { { it(sound) } },
            )
        }

        // Loading spinner
        if ((isLoading || isRefreshing) && sounds.isEmpty() && topHits.isEmpty()) {
            item(key = "loading", contentType = "loading") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AuraStateCard(
                        icon = Icons.Default.GraphicEq,
                        title = "Tuning the sound feed",
                        description = "Aura is checking YouTube clip length and preview availability before showing ringtone-ready results.",
                    )
                    ShimmerSoundList(Modifier.fillMaxWidth())
                }
            }
        }

        // Empty state
        if (!isLoading && !isRefreshing && sounds.isEmpty() && topHits.isEmpty()) {
            item(key = "empty") {
                val (icon, title, supportingText) = soundsEmptyState(selectedTab, query)
                AuraStateCard(
                    icon = icon,
                    title = title,
                    description = supportingText ?: "Try another source or adjust the quality filter.",
                    primaryAction = if (selectedTab == SoundTab.COMMUNITY && onUploadClick != null) {
                        AuraStateAction(
                            label = "Upload sound",
                            icon = Icons.Default.Upload,
                            onClick = onUploadClick,
                        )
                    } else null,
                    secondaryAction = if (selectedTab == SoundTab.COMMUNITY && onRecordClick != null) {
                        AuraStateAction(
                            label = "Record",
                            icon = Icons.Default.Mic,
                            onClick = onRecordClick,
                        )
                    } else null,
                )
            }
        }

        // Load more spinner
        if (isLoadingMore) {
            item(key = "loading_more", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }

    }
}

private fun soundsEmptyState(
    selectedTab: SoundTab,
    query: String,
): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String?> = when {
    selectedTab == SoundTab.YOUTUBE && query.isBlank() -> Triple(
        Icons.Default.SmartDisplay,
        "Loading YouTube sounds",
        "Aura will load a default YouTube search here. You can also paste a video URL.",
    )
    selectedTab == SoundTab.YOUTUBE -> Triple(
        Icons.Default.SmartDisplay,
        "No YouTube audio found",
        "Try another search or paste a specific video URL.",
    )
    selectedTab == SoundTab.COMMUNITY -> Triple(
        Icons.Default.UploadFile,
        "No community sounds yet",
        "Uploads will appear here once the community feed has content.",
    )
    selectedTab == SoundTab.SEARCH && query.isNotBlank() -> Triple(
        Icons.Default.MusicOff,
        "No sounds found for \"$query\"",
        "Try fewer words or a more direct YouTube sound search.",
    )
    else -> Triple(
        Icons.Default.MusicOff,
        "No sounds found",
        "Try another sound type or switch to a different quality filter.",
    )
}

@Composable
private fun SoundCollectionCarousel(
    collections: List<SoundCollectionSpec>,
    onCollectionClick: (SoundCollectionSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "Collections",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Collections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(collections, key = { it.title }) { collection ->
                SoundCollectionCard(
                    collection = collection,
                    onClick = { onCollectionClick(collection) },
                )
            }
        }
    }
}

@Composable
private fun SoundCollectionCard(
    collection: SoundCollectionSpec,
    onClick: () -> Unit,
) {
    val accent = collectionToneColor(collection.tone)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(168.dp)
            .height(104.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.26f)),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        collectionToneIcon(collection.tone),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accent,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    collection.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    collection.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun collectionToneColor(tone: SoundCollectionTone): Color = when (tone) {
    SoundCollectionTone.MINIMAL -> MaterialTheme.colorScheme.primary
    SoundCollectionTone.CALM -> MaterialTheme.colorScheme.tertiary
    SoundCollectionTone.RETRO -> Color(0xFFFFB74D)
    SoundCollectionTone.NATURE -> Color(0xFF66BB6A)
    SoundCollectionTone.PUNCHY -> Color(0xFFFF6B6B)
    SoundCollectionTone.MELODIC -> Color(0xFF64B5F6)
    SoundCollectionTone.SEASONAL -> Color(0xFFFFCA28) // amber-gold accent
}

private fun collectionToneIcon(tone: SoundCollectionTone): androidx.compose.ui.graphics.vector.ImageVector = when (tone) {
    SoundCollectionTone.MINIMAL -> Icons.Default.RadioButtonUnchecked
    SoundCollectionTone.CALM -> Icons.Default.Spa
    SoundCollectionTone.RETRO -> Icons.Default.PhoneInTalk
    SoundCollectionTone.NATURE -> Icons.Default.WaterDrop
    SoundCollectionTone.PUNCHY -> Icons.Default.Bolt
    SoundCollectionTone.MELODIC -> Icons.Default.GraphicEq
    SoundCollectionTone.SEASONAL -> Icons.Default.Celebration
}

// -- Sound Card --

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundCard(
    sound: Sound,
    tab: SoundTab,
    isPlaying: Boolean,
    isResolving: Boolean = false,
    isPreviewReady: Boolean = false,
    playbackProgress: Float = 0f,
    voteCount: Int? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onPlayClick: () -> Unit,
    onUpvote: (() -> Unit)? = null,
    onDownvote: (() -> Unit)? = null,
) {
    val showUploader = sound.uploaderName.isNotEmpty() &&
        sound.uploaderName != "Unknown" &&
        !(sound.source == ContentSource.BUNDLED && sound.uploaderName == "Aura Picks")
    val (sourceLabel, sourceColor) = soundSourceTone(sound.source)
    val badges = remember(sound, tab) { soundBadges(sound, tab) }
    val playPreviewLabel = stringResource(R.string.a11y_play_preview)
    val pausePreviewLabel = stringResource(R.string.a11y_pause_preview)
    val quickActionsLabel = stringResource(R.string.a11y_show_quick_actions)
    val upvoteSoundLabel = stringResource(R.string.a11y_upvote_sound)
    val hideSoundLabel = stringResource(R.string.a11y_hide_sound)
    val openSoundDetailsLabel = stringResource(R.string.a11y_open_sound_details, sound.name)
    val openDetailsLabel = stringResource(R.string.a11y_open_details)
    val playButtonDescription = when {
        isResolving && sound.source == ContentSource.YOUTUBE -> stringResource(R.string.a11y_loading_youtube_audio)
        isResolving -> stringResource(R.string.a11y_preparing_audio)
        isPlaying -> pausePreviewLabel
        else -> playPreviewLabel
    }
    val soundStateDescription = when {
        isResolving -> stringResource(R.string.a11y_preview_preparing)
        isPlaying -> stringResource(R.string.a11y_preview_playing)
        isPreviewReady -> stringResource(R.string.a11y_preview_ready)
        else -> stringResource(R.string.a11y_preview_not_loaded)
    }
    val voteStateDescription = stringResource(R.string.a11y_vote_count, voteCount ?: 0)
    val cardActions = buildList {
        add(CustomAccessibilityAction(openDetailsLabel) { onClick(); true })
        add(CustomAccessibilityAction(if (isPlaying) pausePreviewLabel else playPreviewLabel) { onPlayClick(); true })
        add(CustomAccessibilityAction(quickActionsLabel) { onLongPress(); true })
        onUpvote?.let { upvote ->
            add(CustomAccessibilityAction(upvoteSoundLabel) { upvote(); true })
        }
        onDownvote?.let { downvote ->
            add(CustomAccessibilityAction(hideSoundLabel) { downvote(); true })
        }
    }
    Surface(
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
        ),
        shadowElevation = if (isPlaying) 3.dp else 1.dp,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onClickLabel = openSoundDetailsLabel,
                onLongClick = onLongPress,
                onLongClickLabel = quickActionsLabel,
            )
            .semantics {
                stateDescription = soundStateDescription
                customActions = cardActions
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Play button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .semantics {
                            contentDescription = playButtonDescription
                            stateDescription = soundStateDescription
                            onClick(label = playButtonDescription, action = null)
                        }
                        .background(
                            if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Info
                Column(Modifier.weight(1f)) {
                    Text(
                        sound.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Source badge
                        Surface(color = sourceColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                sourceLabel,
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = sourceColor,
                                fontWeight = if (sound.source == ContentSource.BUNDLED) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                        Text(
                            formatDuration(sound.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val codecBadge = remember(sound) { formatSoundCodecBadge(sound) }
                        if (codecBadge != null) {
                            Text(
                                codecBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        if (isPreviewReady) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    "Ready",
                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        if (showUploader) {
                            Text(
                                sound.uploaderName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                    if (badges.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(badges, key = { badge -> "${sound.stableKey()}_$badge" }) { badge ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        badge,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                // Chevron
                Icon(Icons.Default.ChevronRight, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }

            // Resolving indicator
            if (isResolving) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (sound.source == ContentSource.YOUTUBE) "Loading YouTube audio..." else "Preparing audio...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 56.dp),
                )
            }

            // Playback waveform
            if (isPlaying && sound.duration > 0) {
                Spacer(Modifier.height(6.dp))
                MiniWaveform(sound.duration, true, playbackProgress, Modifier.fillMaxWidth().padding(start = 60.dp))
            }

            if (sound.source == ContentSource.COMMUNITY && (onUpvote != null || onDownvote != null)) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 52.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onUpvote?.let {
                        OutlinedButton(
                            onClick = it,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .semantics {
                                    stateDescription = voteStateDescription
                                    onClick(label = upvoteSoundLabel, action = null)
                                },
                        ) {
                            Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if ((voteCount ?: 0) > 0) "+${voteCount ?: 0}" else "Upvote",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    onDownvote?.let {
                        TextButton(
                            onClick = it,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .semantics { onClick(label = hideSoundLabel, action = null) },
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Hide", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniWaveform(duration: Double, isPlaying: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val activeColor = MaterialTheme.colorScheme.primary
    val barCount = 50
    val heights = remember(duration) {
        val seed = (duration * 1000).toInt()
        List(barCount) { i -> (0.2f + 0.8f * ((sin((seed + i * 37) % 360 * 0.0174533) + 1f) / 2f).toFloat()) }
    }
    val waveformDescription = stringResource(R.string.a11y_playback_waveform)
    val waveformState = if (isPlaying) {
        stringResource(R.string.a11y_playing_percent, (progress * 100).toInt())
    } else {
        stringResource(R.string.a11y_stopped)
    }
    Canvas(
        modifier
            .height(20.dp)
            .semantics {
                contentDescription = waveformDescription
                stateDescription = waveformState
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        val barWidth = size.width / barCount
        val gap = 1.dp.toPx()
        heights.forEachIndexed { i, height ->
            val x = i * barWidth + barWidth / 2
            val barH = size.height * height
            drawLine(
                color = if (isPlaying && (i.toFloat() / barCount) < progress) activeColor else inactiveColor,
                start = Offset(x, size.height / 2 - barH / 2),
                end = Offset(x, size.height / 2 + barH / 2),
                strokeWidth = (barWidth - gap).coerceAtLeast(1f),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt()
    val m = total / 60
    val s = total % 60
    return if (m > 0) "${m}:${s.toString().padStart(2, '0')}" else "0:${s.toString().padStart(2, '0')}"
}

private fun soundFilterLabel(filter: SoundQualityFilter): String = when (filter) {
    SoundQualityFilter.BEST -> "Best"
    SoundQualityFilter.CLEAN -> "Clean"
    SoundQualityFilter.SHORT -> "Short"
    SoundQualityFilter.CALM -> "Calm"
    SoundQualityFilter.PUNCHY -> "Punchy"
}

@Composable
private fun SoundFiltersSheet(
    qualityFilter: SoundQualityFilter,
    onSelectQuality: (SoundQualityFilter) -> Unit,
    onReset: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Refine sounds", style = MaterialTheme.typography.titleMedium)
        Text(
            "Quality bias",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoundQualityFilter.entries.forEach { filter ->
                FilterChip(
                    selected = qualityFilter == filter,
                    onClick = { onSelectQuality(filter) },
                    label = { Text(soundFilterLabel(filter)) },
                    leadingIcon = if (qualityFilter == filter) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }
        onReset?.let {
            TextButton(onClick = it) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset filters")
            }
        }
    }
}

// -- Quick Apply Bottom Sheet --

private data class QuickApplyPendingAction(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit,
)

private fun quickApplyPolicyMessages(capabilities: SoundLicenseCapabilities): List<String> =
    SoundAction.entries
        .map { capabilities.capability(it) }
        .filter { it.decision == SoundActionDecision.DISABLED && it.reason.isNotBlank() }
        .map { it.reason }
        .distinct()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickApplySheet(
    sound: Sound,
    canApply: Boolean,
    canOpenPermissionSettings: Boolean,
    isApplying: Boolean,
    onApply: (Sound, ContentType) -> Unit,
    onDownload: (Sound) -> Unit,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    val licenseCapabilities = remember(sound) { sound.soundLicenseCapabilities() }
    val canUseApply = canApply && licenseCapabilities.canUse(SoundAction.APPLY)
    val canUseDownload = licenseCapabilities.canUse(SoundAction.DOWNLOAD)
    val policyMessages = remember(licenseCapabilities) { quickApplyPolicyMessages(licenseCapabilities) }
    val writeSettingsBody = stringResource(R.string.write_settings_body)
    val openSettingsLabel = stringResource(R.string.write_settings_open)
    var pendingAction by remember(sound.stableKey()) { mutableStateOf<QuickApplyPendingAction?>(null) }

    pendingAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(pending.title) },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        pending.onConfirm()
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    fun runSoundAction(action: SoundAction, title: String, onConfirm: () -> Unit) {
        val capability = licenseCapabilities.capability(action)
        when (capability.decision) {
            SoundActionDecision.ALLOWED -> onConfirm()
            SoundActionDecision.CONFIRMATION_REQUIRED -> {
                pendingAction = QuickApplyPendingAction(title, capability.reason, onConfirm)
            }
            SoundActionDecision.DISABLED -> Unit
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(sound.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatDuration(sound.duration)}${if (sound.uploaderName.isNotEmpty() && sound.uploaderName != "Unknown") " - ${sound.uploaderName}" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (!canApply) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            writeSettingsBody,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onGrantPermission, enabled = !isApplying && canOpenPermissionSettings) {
                            Text(openSettingsLabel)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (policyMessages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            policyMessages.joinToString(" "),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            QuickApplyRow("Set as Ringtone", Icons.Default.Call, canUseApply && !isApplying) {
                runSoundAction(SoundAction.APPLY, "Apply sound") { onApply(sound, ContentType.RINGTONE) }
            }
            QuickApplyRow("Set as Notification", Icons.Default.Notifications, canUseApply && !isApplying) {
                runSoundAction(SoundAction.APPLY, "Apply sound") { onApply(sound, ContentType.NOTIFICATION) }
            }
            QuickApplyRow("Set as Alarm", Icons.Default.Alarm, canUseApply && !isApplying) {
                runSoundAction(SoundAction.APPLY, "Apply sound") { onApply(sound, ContentType.ALARM) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            QuickApplyRow("Download", Icons.Default.Download, canUseDownload && !isApplying) {
                runSoundAction(SoundAction.DOWNLOAD, "Save sound") { onDownload(sound) }
            }

            if (isApplying) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun QuickApplyRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val rowStateDescription = stringResource(if (enabled) R.string.a11y_available else R.string.a11y_disabled)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.58f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.3f)
        },
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
        ),
        modifier = Modifier.semantics {
            stateDescription = rowStateDescription
            if (enabled) {
                onClick(label = label, action = null)
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

// -- Upload Dialog --

@Composable
private fun RecordingDialog(
    startedAtMs: Long,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
) {
    var elapsedMs by remember(startedAtMs) { mutableStateOf(0L) }
    val recordingTitle = stringResource(R.string.community_recording_title)
    val recordingBody = stringResource(R.string.community_recording_body)
    val stopLabel = stringResource(R.string.community_recording_stop)
    val discardLabel = stringResource(R.string.community_recording_discard)
    LaunchedEffect(startedAtMs) {
        while (true) {
            elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
            delay(250L)
        }
    }

    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Mic, contentDescription = null) },
        title = { Text(recordingTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    formatRecordingElapsed(elapsedMs),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(
                    progress = { (elapsedMs / 60_000f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    recordingBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onStop, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stopLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(discardLabel)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UploadDialog(
    isUploading: Boolean,
    uploadProgress: Float,
    onUpload: (name: String, category: String, tags: List<String>, rights: CommunityUploadRights) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ringtone") }
    var selectedLicense by remember { mutableStateOf(COMMUNITY_UPLOAD_LICENSES.first()) }
    var sourceUrl by remember { mutableStateOf("") }
    var rightsAttested by remember { mutableStateOf(false) }
    var tagsText by remember { mutableStateOf("") }
    val policyCopy = remember { communityUploadPolicyCopy(CommunityUploadPolicyKind.SOUND) }
    val categories = listOf("ringtone" to "Ringtone", "notification" to "Notification", "alarm" to "Alarm")
    val parsedTags = remember(tagsText) {
        tagsText.split(',', '#')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("Upload sound") },
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
                    label = { Text("Sound name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
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
                    label = { Text("Tags") },
                    placeholder = { Text("calm, chime, short") },
                    supportingText = { Text("Comma-separated tags help people find it.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("License", style = MaterialTheme.typography.labelMedium)
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
                    label = { Text("Source URL") },
                    placeholder = { Text("https://example.com/source") },
                    supportingText = { Text("Optional HTTPS link for credited source material.") },
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
                        "${(uploadProgress * 100).toInt()}%",
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
                    )
                },
                enabled = !isUploading && name.isNotBlank() && rightsAttested,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Upload") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun PermissionRecoveryDialog(
    title: String,
    body: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.permission_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.permission_not_now))
            }
        },
    )
}

private fun openAuraAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    runCatching { context.startActivity(intent) }
}

private fun formatRecordingElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun soundSourceHealthSummary(degradedSources: Set<String>): String {
    val labels = degradedSources.sorted().joinToString(", ")
    return "Limited source health right now: $labels"
}
