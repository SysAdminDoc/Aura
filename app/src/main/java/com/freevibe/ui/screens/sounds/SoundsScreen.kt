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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import com.freevibe.data.model.shouldShowCommunityContent
import com.freevibe.data.repository.matchesHiddenIds
import com.freevibe.data.repository.YouTubeExtractionMode
import com.freevibe.data.repository.displayName
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.LoadMoreIndicator
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.CompactSearchField
import com.freevibe.ui.components.CommunityGuidelinesDialog
import com.freevibe.ui.components.CountBadge
import com.freevibe.ui.components.CommunityPolicyNotice
import com.freevibe.ui.components.SearchHistoryDropdown
import com.freevibe.ui.components.ShimmerSoundList
import com.freevibe.ui.components.AuraStatusAction
import com.freevibe.ui.components.AuraStatusBanner
import com.freevibe.ui.navigation.LocalAuraNavigationLayout
import com.freevibe.ui.navigation.isExpanded
import com.freevibe.ui.policy.CommunityUploadPolicyKind
import com.freevibe.ui.policy.communityUploadPolicyCopy
import com.freevibe.ui.theme.AuraShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.sin
import com.freevibe.data.repository.hiddenItemLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsScreen(
    onSoundClick: (Sound) -> Unit,
    onCreateRingtone: (Uri) -> Unit = {},
    initialQuery: String? = null,
    isExpandedLayout: Boolean = LocalAuraNavigationLayout.current.isExpanded,
    viewModel: SoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val previewReadyIds by viewModel.previewReadyIds.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val youtubeProviderEnabled by viewModel.youtubeProviderEnabled.collectAsStateWithLifecycle()
    val communityProviderEnabled by viewModel.communityProviderEnabled.collectAsStateWithLifecycle()
    val communityGuidelinesAccepted by viewModel.communityGuidelinesAccepted.collectAsStateWithLifecycle()
    val hiddenIds by viewModel.hiddenIds.collectAsStateWithLifecycle(initialValue = emptySet())
    var hideAiGeneratedCommunity by rememberSaveable { mutableStateOf(false) }
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
    val displaySounds = remember(
        state.sounds,
        state.selectedTab,
        hiddenIds,
        voteCounts,
        communityProviderEnabled,
        hideAiGeneratedCommunity,
    ) {
        if (communityProviderEnabled && state.selectedTab == SoundTab.COMMUNITY) {
            state.sounds
                .filter { !matchesHiddenIds(hiddenIds, it.stableKey(), it.id) }
                .filter { shouldShowCommunityContent(it.isAiGenerated, hideAiGeneratedCommunity) }
                .sortedByDescending { voteCounts[it.stableKey()] ?: 0 }
        } else {
            state.sounds
        }
    }
    val contentSounds = displaySounds
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
    var searchExpanded by remember { mutableStateOf(false) }
    var showQuickActionsMenu by remember { mutableStateOf(false) }
    var showCommunityGuidelines by remember { mutableStateOf(false) }
    var quickApplySound by remember { mutableStateOf<Sound?>(null) }
    var quickApplyActionInFlight by remember { mutableStateOf(false) }
    var quickApplyObservedApplying by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }
    var showRecordPermissionPrompt by remember { mutableStateOf(false) }
    var showRecordPermissionRecovery by remember { mutableStateOf(false) }
    var pendingPersonalRecording by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Reading a string off LocalContext is not a composition read. LocalResources is.
    val resources = LocalResources.current
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
            if (pendingPersonalRecording) {
                pendingPersonalRecording = false
                viewModel.startPersonalRecording()
            } else {
                viewModel.startCommunityRecording()
            }
        } else {
            pendingPersonalRecording = false
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

    LaunchedEffect(state.personalRecordingUri) {
        state.personalRecordingUri?.let { uri ->
            viewModel.consumePersonalRecording()
            onCreateRingtone(uri)
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
            onUpload = { name, category, tags, rights, isAiGenerated ->
                awaitingUploadResult = true
                viewModel.uploadSound(uploadUri, name, category, tags, rights, isAiGenerated)
            },
            onDismiss = {
                if (!state.isUploading) {
                    showUploadDialog = false; selectedAudioUri = null
                    awaitingUploadResult = false
                }
            },
        )
        LaunchedEffect(state.isUploading, state.uploadComplete, state.error) {
            if (awaitingUploadResult && !state.isUploading) {
                awaitingUploadResult = false
            }
            if (showUploadDialog && state.uploadComplete) {
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
        if (displaySounds.isNotEmpty()) {
            state.error?.let {
                nonBlockingWarning = it
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }
    LaunchedEffect(state.isRefreshing, state.error) {
        if (!state.isRefreshing && state.error == null && nonBlockingWarning != null) {
            nonBlockingWarning = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
        Column(
            modifier = if (isExpandedLayout) {
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 1180.dp)
                    .align(Alignment.TopCenter)
            } else {
                Modifier.fillMaxSize()
            },
        ) {
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
                        text = stringResource(R.string.nav_sounds),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = { searchExpanded = !searchExpanded }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.sounds_search_generic))
                    }
                    IconButton(onClick = { showFiltersSheet = true }) {
                        Box {
                            Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.sounds_filter_cd))
                            CountBadge(
                                count = soundFilterCount,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-7).dp),
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showQuickActionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.sounds_mode_more))
                        }
                        DropdownMenu(
                            expanded = showQuickActionsMenu,
                            onDismissRequest = { showQuickActionsMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sounds_fab_create_from_music)) },
                                leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                onClick = {
                                    showQuickActionsMenu = false
                                    createAudioPickerLauncher.launch("audio/*")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (state.isRecordingPersonal) R.string.sounds_fab_stop_recording else R.string.sounds_fab_record_ringtone)) },
                                leadingIcon = { Icon(if (state.isRecordingPersonal) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null) },
                                onClick = {
                                    showQuickActionsMenu = false
                                    if (state.isRecordingPersonal) {
                                        viewModel.stopPersonalRecording()
                                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.startPersonalRecording()
                                    } else {
                                        pendingPersonalRecording = true
                                        showRecordPermissionPrompt = true
                                    }
                                },
                            )
                            if (communityProviderEnabled) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sounds_fab_upload_community)) },
                                    leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                    onClick = {
                                        showQuickActionsMenu = false
                                        uploadAudioPickerLauncher.launch("audio/*")
                                    },
                                )
                            }
                            if (communityProviderEnabled && state.selectedTab == SoundTab.COMMUNITY) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sounds_fab_record_community)) },
                                    leadingIcon = { Icon(Icons.Default.FiberManualRecord, contentDescription = null) },
                                    onClick = {
                                        showQuickActionsMenu = false
                                        startRecording()
                                    },
                                )
                            }
                        }
                    }
                }

                if (searchExpanded || state.selectedTab in setOf(SoundTab.SEARCH, SoundTab.YOUTUBE) || searchQuery.isNotBlank()) {
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; showSearchHistory = it.isEmpty() },
                            placeholder = stringResource(when {
                                isYouTubeTab -> R.string.sounds_search_youtube_or_paste
                                youtubeProviderEnabled -> R.string.sounds_search_youtube
                                else -> R.string.sounds_search_generic
                            }),
                            leadingIcon = if (isYouTubeTab) Icons.Default.SmartDisplay else Icons.Default.Search,
                            leadingTint = if (isYouTubeTab) soundSourceTone(ContentSource.YOUTUBE).colorForSurface() else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Spacer(Modifier.height(4.dp))
                }

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
                if (state.selectedTab == SoundTab.COMMUNITY && communityProviderEnabled) {
                    FilterChip(
                        selected = hideAiGeneratedCommunity,
                        onClick = { hideAiGeneratedCommunity = !hideAiGeneratedCommunity },
                        label = { Text(stringResource(R.string.community_hide_ai_filter)) },
                        leadingIcon = if (hideAiGeneratedCommunity) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = AuraShapes.Medium,
                    )
                }
            }

            if (
                state.selectedTab == SoundTab.YOUTUBE &&
                youtubeProviderEnabled &&
                state.youtubeExtractionStatus.mode != YouTubeExtractionMode.HEALTHY
            ) {
                val status = state.youtubeExtractionStatus
                val usingBackup = status.mode == YouTubeExtractionMode.BACKUP_ACTIVE
                AuraStatusBanner(
                    icon = Icons.Default.WarningAmber,
                    title = stringResource(
                        if (usingBackup) {
                            R.string.sounds_youtube_backup_title
                        } else {
                            R.string.sounds_youtube_unavailable_title
                        },
                    ),
                    message = if (usingBackup) {
                        stringResource(
                            R.string.sounds_youtube_backup_message,
                            status.failedEngine?.displayName().orEmpty(),
                            status.activeEngine?.displayName().orEmpty(),
                        )
                    } else {
                        stringResource(R.string.sounds_youtube_unavailable_message)
                    },
                    tone = if (usingBackup) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    primaryAction = AuraStatusAction(
                        label = stringResource(R.string.common_refresh),
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.refresh() },
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            nonBlockingWarning?.let { warning ->
                AuraStatusBanner(
                    icon = Icons.Default.GraphicEq,
                    title = stringResource(R.string.sounds_banner_stale_title),
                    message = stringResource(R.string.sounds_banner_stale_message, warning),
                    tone = MaterialTheme.colorScheme.tertiary,
                    primaryAction = AuraStatusAction(
                        label = stringResource(R.string.common_refresh),
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.refresh() },
                    ),
                    secondaryAction = AuraStatusAction(
                        label = stringResource(R.string.sounds_banner_dismiss),
                        icon = Icons.Default.Close,
                        onClick = { nonBlockingWarning = null },
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.error != null && displaySounds.isEmpty() && !state.isLoading && !state.isRefreshing) {
                    AuraStateCard(
                        icon = Icons.Default.CloudOff,
                        title = stringResource(R.string.sounds_error_source_title, soundTabLabel(state.selectedTab)),
                        description = state.error ?: stringResource(R.string.sounds_error_description),
                        tone = MaterialTheme.colorScheme.error,
                        primaryAction = AuraStateAction(
                            label = stringResource(R.string.sounds_error_retry),
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.refresh() },
                        ),
                        secondaryAction = AuraStateAction(
                            label = stringResource(R.string.editor_sound_browse),
                            icon = Icons.Default.FolderOpen,
                            onClick = { createAudioPickerLauncher.launch("audio/*") },
                        ),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                } else {
                    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = { viewModel.refresh() }) {
                        SoundsList(
                            sounds = contentSounds,
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
                            onVisibleSoundsChanged = viewModel::onVisibleSoundsChanged,
                            playbackProgress = playbackProgress,
                            isExpandedLayout = isExpandedLayout,
                            voteCounts = voteCounts,
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
                                if (communityGuidelinesAccepted) {
                                    val hiddenId = sound.stableKey()
                                    viewModel.downvote(hiddenId, sound.hiddenItemLabel())
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = resources.getString(R.string.community_item_hidden),
                                            actionLabel = resources.getString(R.string.common_undo),
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) viewModel.undoDownvote(hiddenId)
                                    }
                                } else showCommunityGuidelines = true
                            }) else null,
                        )
                    }
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

@Composable
private fun soundTabLabel(tab: SoundTab): String = stringResource(
    when (tab) {
        SoundTab.RINGTONES -> R.string.sounds_tab_ringtones
        SoundTab.NOTIFICATIONS -> R.string.sounds_tab_notifications
        SoundTab.ALARMS -> R.string.sounds_tab_alarms
        SoundTab.YOUTUBE -> R.string.sounds_tab_youtube
        SoundTab.COMMUNITY -> R.string.sounds_tab_community
        SoundTab.SEARCH -> R.string.sounds_tab_search
    },
)

private fun soundTabIcon(tab: SoundTab): androidx.compose.ui.graphics.vector.ImageVector = when (tab) {
    SoundTab.RINGTONES -> Icons.Default.Call
    SoundTab.NOTIFICATIONS -> Icons.Default.Notifications
    SoundTab.ALARMS -> Icons.Default.Alarm
    SoundTab.YOUTUBE -> Icons.Default.SmartDisplay
    SoundTab.COMMUNITY -> Icons.Default.Groups
    SoundTab.SEARCH -> Icons.Default.Search
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
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        coreSoundTabs.forEach { tab ->
            val selected = selectedTab == tab
            Surface(
                onClick = { onSelectTab(tab) },
                color = Color.Transparent,
                shape = AuraShapes.None,
                modifier = Modifier.semantics { this.selected = selected; role = androidx.compose.ui.semantics.Role.Tab },
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .widthIn(min = 92.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        soundTabLabel(tab),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }

        if (secondaryTabs.isNotEmpty()) {
            Box {
                Surface(
                    onClick = { showMoreMenu = true },
                    color = Color.Transparent,
                    shape = AuraShapes.None,
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (secondarySelected) soundTabLabel(selectedTab) else stringResource(R.string.sounds_mode_more),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (secondarySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
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
    isExpandedLayout: Boolean = false,
    voteCounts: Map<String, Int> = emptyMap(),
    onUploadClick: (() -> Unit)? = null,
    onRecordClick: (() -> Unit)? = null,
    onUpvote: ((Sound) -> Unit)? = null,
    onDownvote: ((Sound) -> Unit)? = null,
    onVisibleSoundsChanged: (List<Sound>) -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(filterKey) { listState.scrollToItem(0) }

    // Report the on-screen rows (plus lookahead) so previews resolve for what is
    // seen, not for every result. Sound rows are the first items in the list.
    val currentSounds by rememberUpdatedState(sounds)
    val currentOnVisible by rememberUpdatedState(onVisibleSoundsChanged)
    LaunchedEffect(listState) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            previewResolveWindow(
                sounds = currentSounds,
                firstVisibleIndex = visible.firstOrNull()?.index,
                lastVisibleIndex = visible.lastOrNull()?.index,
            ).map { it.stableKey() to it }
        }
            .distinctUntilChanged { old, new -> old.map { it.first } == new.map { it.first } }
            .collect { window -> if (window.isNotEmpty()) currentOnVisible(window.map { it.second }) }
    }

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
        contentPadding = PaddingValues(
            horizontal = if (isExpandedLayout) 24.dp else 14.dp,
            vertical = if (isExpandedLayout) 10.dp else 6.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(sounds, key = { it.stableKey() }, contentType = { "sound_card" }) { sound ->
            SoundCard(
                sound = sound,
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
        if ((isLoading || isRefreshing) && sounds.isEmpty()) {
            item(key = "loading", contentType = "loading") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.sounds_loading_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ShimmerSoundList(Modifier.fillMaxWidth())
                }
            }
        }

        // Empty state
        if (!isLoading && !isRefreshing && sounds.isEmpty()) {
            item(key = "empty") {
                val (icon, title, supportingText) = soundsEmptyState(selectedTab, query)
                AuraStateCard(
                    icon = icon,
                    title = title,
                    description = supportingText ?: stringResource(R.string.sounds_empty_fallback),
                    primaryAction = if (selectedTab == SoundTab.COMMUNITY && onUploadClick != null) {
                        AuraStateAction(
                            label = stringResource(R.string.sounds_empty_upload),
                            icon = Icons.Default.Upload,
                            onClick = onUploadClick,
                        )
                    } else null,
                    secondaryAction = if (selectedTab == SoundTab.COMMUNITY && onRecordClick != null) {
                        AuraStateAction(
                            label = stringResource(R.string.sounds_empty_record),
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
                LoadMoreIndicator()
            }
        }

    }
}

@Composable
private fun soundsEmptyState(
    selectedTab: SoundTab,
    query: String,
): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String?> = when {
    selectedTab == SoundTab.YOUTUBE -> Triple(
        Icons.Default.SmartDisplay,
        stringResource(R.string.sounds_empty_youtube_title),
        stringResource(R.string.sounds_empty_youtube_body),
    )
    selectedTab == SoundTab.COMMUNITY -> Triple(
        Icons.Default.UploadFile,
        stringResource(R.string.sounds_empty_community_title),
        stringResource(R.string.sounds_empty_community_body),
    )
    selectedTab == SoundTab.SEARCH && query.isNotBlank() -> Triple(
        Icons.Default.MusicOff,
        stringResource(R.string.sounds_empty_search_title, query),
        stringResource(R.string.sounds_empty_search_body),
    )
    else -> Triple(
        Icons.Default.MusicOff,
        stringResource(R.string.sounds_empty_title),
        stringResource(R.string.sounds_empty_fallback),
    )
}

// -- Sound Card --

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundCard(
    sound: Sound,
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
        !(sound.source == ContentSource.BUNDLED && sound.uploaderName in setOf("Aura", "Aura Picks"))
    val sourceTone = soundSourceTone(sound.source)
    val sourceLabel = sourceTone.label
    val sourceColor = sourceTone.colorForSurface()
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
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f) else Color.Transparent,
        shape = AuraShapes.None,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onClickLabel = openSoundDetailsLabel,
                onLongClick = onLongPress,
                onLongClickLabel = quickActionsLabel,
            )
            .semantics {
                contentDescription = openSoundDetailsLabel
                stateDescription = soundStateDescription
                customActions = cardActions
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Play button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics {
                            contentDescription = playButtonDescription
                            stateDescription = soundStateDescription
                            onClick(label = playButtonDescription, action = null)
                        }
                        .background(if (isPlaying) MaterialTheme.colorScheme.primary else Color.Transparent),
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
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            sourceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = sourceColor,
                            fontWeight = if (sound.source == ContentSource.BUNDLED) FontWeight.Bold else FontWeight.Medium,
                        )
                        if (sound.isAiGenerated == true) {
                            Text(
                                stringResource(R.string.community_ai_generated_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            formatSoundFeedDuration(sound.duration),
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
                }

                // Chevron
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.sounds_card_details_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }

            // Resolving indicator
            if (isResolving) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (sound.source == ContentSource.YOUTUBE) R.string.sounds_card_loading_youtube else R.string.sounds_card_preparing_audio),
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
                            shape = AuraShapes.Medium,
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
                                if ((voteCount ?: 0) > 0) stringResource(R.string.sounds_card_upvote_count, voteCount ?: 0) else stringResource(R.string.sounds_card_upvote),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    onDownvote?.let {
                        TextButton(
                            onClick = it,
                            shape = AuraShapes.Medium,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .semantics { onClick(label = hideSoundLabel, action = null) },
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.sounds_card_hide), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp, start = 54.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            )
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

@Composable
private fun soundFilterLabel(filter: SoundQualityFilter): String = stringResource(
    when (filter) {
        SoundQualityFilter.BEST -> R.string.sounds_quality_best
        SoundQualityFilter.CLEAN -> R.string.sounds_quality_clean
        SoundQualityFilter.SHORT -> R.string.sounds_quality_short
        SoundQualityFilter.CALM -> R.string.sounds_quality_calm
        SoundQualityFilter.PUNCHY -> R.string.sounds_quality_punchy
    },
)

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
        Text(stringResource(R.string.sounds_filter_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.sounds_filter_quality_bias),
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
                Text(stringResource(R.string.sounds_filter_reset))
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
                    Text(stringResource(R.string.sounds_quick_apply_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.common_cancel))
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
                if (sound.uploaderName.isNotEmpty() && sound.uploaderName != "Unknown") {
                    stringResource(R.string.sounds_duration_by_uploader, formatSoundFeedDuration(sound.duration), sound.uploaderName)
                } else {
                    formatSoundFeedDuration(sound.duration)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (!canApply) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    shape = AuraShapes.Medium,
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
                    shape = AuraShapes.Medium,
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

            val setRingtoneLabel = stringResource(R.string.sounds_quick_apply_set_ringtone)
            val setNotificationLabel = stringResource(R.string.sounds_quick_apply_set_notification)
            val setAlarmLabel = stringResource(R.string.sounds_quick_apply_set_alarm)
            val applySoundTitle = stringResource(R.string.sounds_quick_apply_title)
            val downloadLabel = stringResource(R.string.sounds_quick_apply_download)
            val saveSoundTitle = stringResource(R.string.sounds_quick_apply_save_title)

            QuickApplyRow(setRingtoneLabel, Icons.Default.Call, canUseApply && !isApplying) {
                runSoundAction(SoundAction.APPLY, applySoundTitle) { onApply(sound, ContentType.RINGTONE) }
            }
            QuickApplyRow(setNotificationLabel, Icons.Default.Notifications, canUseApply && !isApplying) {
                runSoundAction(SoundAction.APPLY, applySoundTitle) { onApply(sound, ContentType.NOTIFICATION) }
            }
            QuickApplyRow(setAlarmLabel, Icons.Default.Alarm, canUseApply && !isApplying) {
                runSoundAction(SoundAction.APPLY, applySoundTitle) { onApply(sound, ContentType.ALARM) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            QuickApplyRow(downloadLabel, Icons.Default.Download, canUseDownload && !isApplying) {
                runSoundAction(SoundAction.DOWNLOAD, saveSoundTitle) { onDownload(sound) }
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
        shape = AuraShapes.Medium,
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
            Button(onClick = onStop, shape = AuraShapes.Medium, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stopLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard, shape = AuraShapes.Medium, modifier = Modifier.heightIn(min = 48.dp)) {
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
    onUpload: (name: String, category: String, tags: List<String>, rights: CommunityUploadRights, isAiGenerated: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ringtone") }
    var selectedLicense by remember { mutableStateOf(COMMUNITY_UPLOAD_LICENSES.first()) }
    var sourceUrl by remember { mutableStateOf("") }
    var rightsAttested by remember { mutableStateOf(false) }
    var isAiGenerated by remember { mutableStateOf(false) }
    var tagsText by remember { mutableStateOf("") }
    val policyCopy = remember { communityUploadPolicyCopy(CommunityUploadPolicyKind.SOUND) }
    val ringtoneLabel = stringResource(R.string.sounds_upload_category_ringtone)
    val notificationLabel = stringResource(R.string.sounds_upload_category_notification)
    val alarmLabel = stringResource(R.string.sounds_upload_category_alarm)
    val categories = listOf("ringtone" to ringtoneLabel, "notification" to notificationLabel, "alarm" to alarmLabel)
    val parsedTags = remember(tagsText) {
        tagsText.split(',', '#')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text(stringResource(R.string.sounds_upload_title)) },
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
                    label = { Text(stringResource(R.string.sounds_upload_name_label)) },
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
                Text(stringResource(R.string.sounds_upload_category_label), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label) },
                            shape = AuraShapes.Medium,
                        )
                    }
                }
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(stringResource(R.string.sounds_upload_tags_label)) },
                    placeholder = { Text(stringResource(R.string.sounds_upload_tags_placeholder)) },
                    supportingText = { Text(stringResource(R.string.sounds_upload_tags_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.sounds_upload_license_label), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    COMMUNITY_UPLOAD_LICENSES.forEach { license ->
                        FilterChip(
                            selected = selectedLicense == license,
                            onClick = { selectedLicense = license },
                            label = { Text(license) },
                            shape = AuraShapes.Medium,
                        )
                    }
                }
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    label = { Text(stringResource(R.string.sounds_upload_source_label)) },
                    placeholder = { Text(stringResource(R.string.sounds_upload_source_placeholder)) },
                    supportingText = { Text(stringResource(R.string.sounds_upload_source_hint)) },
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
                        stringResource(R.string.sounds_upload_progress, (uploadProgress * 100).toInt()),
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
                shape = AuraShapes.Medium,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.sounds_upload_button)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading,
                shape = AuraShapes.Medium,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.common_cancel)) }
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
