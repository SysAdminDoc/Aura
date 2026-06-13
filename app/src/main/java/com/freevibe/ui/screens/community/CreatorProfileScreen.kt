package com.freevibe.ui.screens.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.freevibe.R
import java.util.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CreatorProfileUpdateInput
import com.freevibe.data.model.isCommunityUserBlocked
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CreatorProfileDashboard
import com.freevibe.data.repository.CreatorProfileRepository
import com.freevibe.data.repository.CreatorPublicProfile
import com.freevibe.data.repository.CreatorStats
import com.freevibe.data.repository.CreatorUploadRef
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.GlassCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class CreatorProfileUiState(
    val isLoading: Boolean = true,
    val dashboard: CreatorProfileDashboard? = null,
    val error: String? = null,
    val message: String? = null,
    val actionInFlightCreatorId: String? = null,
    val isProfileSaving: Boolean = false,
)

@HiltViewModel
class CreatorProfileViewModel @Inject constructor(
    private val repository: CreatorProfileRepository,
    private val communityBlockRepo: CommunityBlockRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CreatorProfileUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching { repository.getDashboard() }
                .onSuccess { dashboard ->
                    _state.update {
                        it.copy(isLoading = false, dashboard = dashboard, error = null, message = null)
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update {
                        it.copy(isLoading = false, error = e.message ?: "Creator profile could not load", message = null)
                    }
                }
        }
    }

    fun follow(creator: CreatorStats) {
        updateFollow(creator, follow = true)
    }

    fun unfollow(creator: CreatorStats) {
        updateFollow(creator, follow = false)
    }

    fun blockCreator(creator: CreatorStats) {
        val dashboard = _state.value.dashboard
        if (dashboard != null && creator.matchesCreator(dashboard.currentCreator.creatorId)) {
            _state.update { it.copy(error = "You cannot block your own creator profile", message = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(actionInFlightCreatorId = creator.creatorId, error = null, message = null) }
            communityBlockRepo.blockUser(creator.creatorId, CommunityBlockReason.OTHER)
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            actionInFlightCreatorId = null,
                            dashboard = state.dashboard?.withoutCreator(creator.creatorId),
                            message = "Creator blocked",
                        )
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update {
                        it.copy(
                            actionInFlightCreatorId = null,
                            error = e.message ?: "Creator block failed",
                            message = null,
                        )
                    }
                }
        }
    }

    fun updateProfile(displayName: String, bio: String, websiteUrl: String, avatarUrl: String) {
        viewModelScope.launch {
            _state.update { it.copy(isProfileSaving = true, error = null, message = null) }
            repository.updateCreatorProfile(
                CreatorProfileUpdateInput(
                    displayName = displayName,
                    bio = bio,
                    websiteUrl = websiteUrl,
                    avatarUrl = avatarUrl,
                ),
            )
                .onSuccess { profile ->
                    _state.update { state ->
                        state.copy(
                            isProfileSaving = false,
                            dashboard = state.dashboard?.withCurrentProfile(profile),
                            error = null,
                            message = "Profile saved",
                        )
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update {
                        it.copy(
                            isProfileSaving = false,
                            error = e.message ?: "Profile save failed",
                            message = null,
                        )
                    }
                }
        }
    }

    private fun updateFollow(creator: CreatorStats, follow: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(actionInFlightCreatorId = creator.creatorId, error = null, message = null) }
            val result = if (follow) {
                repository.followCreator(creator.creatorId, creator.label)
            } else {
                repository.unfollowCreator(creator.creatorId)
            }
            result
                .onSuccess {
                    _state.update { it.copy(actionInFlightCreatorId = null) }
                    refresh()
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update {
                    it.copy(
                        actionInFlightCreatorId = null,
                        error = e.message ?: "Follow action failed",
                        message = null,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorProfileScreen(
    onBack: () -> Unit,
    viewModel: CreatorProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dashboard = state.dashboard

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && dashboard == null -> {
                    AuraStateCard(
                        icon = Icons.Default.Person,
                        title = "Loading creator profile",
                        description = "Aura is gathering your uploads, votes, follows, and community leaderboard.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                state.error != null && dashboard == null -> {
                    AuraStateCard(
                        icon = Icons.Default.Groups,
                        title = "Creator profile unavailable",
                        description = state.error ?: "Try again in a moment.",
                        primaryAction = AuraStateAction("Retry", Icons.Default.Refresh, viewModel::refresh),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                dashboard != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            CreatorSummaryCard(
                                dashboard = dashboard,
                                isSaving = state.isProfileSaving,
                                onUpdateProfile = viewModel::updateProfile,
                            )
                        }
                        val statusMessage = state.error ?: state.message
                        if (statusMessage != null) {
                            item {
                                AssistChip(
                                    onClick = viewModel::refresh,
                                    label = { Text(statusMessage) },
                                    leadingIcon = {
                                        Icon(
                                            if (state.error == null) Icons.Default.CheckCircle else Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                )
                            }
                        }
                        item {
                            SectionHeader("Top creators", Icons.Default.Leaderboard)
                        }
                        if (dashboard.topCreators.isEmpty()) {
                            item {
                                EmptyCreatorSection("No creator uploads yet")
                            }
                        } else {
                            items(dashboard.topCreators, key = { "top_${it.creatorId}" }) { creator ->
                                CreatorRow(
                                    creator = creator,
                                    isCurrentUser = creator.creatorId == dashboard.currentCreator.creatorId,
                                    actionInFlight = state.actionInFlightCreatorId == creator.creatorId,
                                    onFollow = { viewModel.follow(creator) },
                                    onUnfollow = { viewModel.unfollow(creator) },
                                    onBlock = if (creator.matchesCreator(dashboard.currentCreator.creatorId)) {
                                        null
                                    } else {
                                        { viewModel.blockCreator(creator) }
                                    },
                                )
                            }
                        }
                        item {
                            SectionHeader("Following", Icons.Default.Favorite)
                        }
                        if (dashboard.followedCreators.isEmpty()) {
                            item {
                                EmptyCreatorSection("Follow creators from the leaderboard to track their new uploads.")
                            }
                        } else {
                            items(dashboard.followedCreators, key = { "followed_${it.creatorId}" }) { creator ->
                                CreatorRow(
                                    creator = creator,
                                    isCurrentUser = false,
                                    actionInFlight = state.actionInFlightCreatorId == creator.creatorId,
                                    onFollow = { viewModel.follow(creator) },
                                    onUnfollow = { viewModel.unfollow(creator) },
                                    onBlock = { viewModel.blockCreator(creator) },
                                )
                            }
                        }
                        item {
                            SectionHeader("New from follows", Icons.Default.Upload)
                        }
                        if (dashboard.followedUploads.isEmpty()) {
                            item {
                                EmptyCreatorSection("Followed creator uploads will appear here.")
                            }
                        } else {
                            items(dashboard.followedUploads, key = { it.stableKey }) { upload ->
                                UploadRow(upload)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorSummaryCard(
    dashboard: CreatorProfileDashboard,
    isSaving: Boolean,
    onUpdateProfile: (String, String, String, String) -> Unit,
) {
    val creator = dashboard.currentCreator
    var showEditProfile by remember { mutableStateOf(false) }
    if (showEditProfile) {
        CreatorProfileEditDialog(
            profile = dashboard.currentProfile,
            fallbackDisplayName = creator.label,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) showEditProfile = false },
            onSave = { displayName, bio, websiteUrl, avatarUrl ->
                onUpdateProfile(displayName, bio, websiteUrl, avatarUrl)
                showEditProfile = false
            },
        )
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        highlightHeight = 72.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(creator.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dashboard.authLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (dashboard.currentProfile.bio.isNotBlank()) {
                    Text(
                        dashboard.currentProfile.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (dashboard.currentProfile.websiteUrl.isNotBlank()) {
                    Text(
                        dashboard.currentProfile.websiteUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { showEditProfile = true }, enabled = !isSaving) {
                Icon(Icons.Default.Edit, contentDescription = "Edit creator profile")
            }
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CreatorMetric("Uploads", creator.uploadCount.toString(), Modifier.weight(1f))
            CreatorMetric("Votes", creator.totalVotes.toString(), Modifier.weight(1f))
            CreatorMetric("Saved", creator.favoritesCount.toString(), Modifier.weight(1f))
        }
        if (!dashboard.googleSignInAvailable) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Google sign-in needs a Firebase OAuth client before it can be enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreatorProfileEditDialog(
    profile: CreatorPublicProfile,
    fallbackDisplayName: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var displayName by remember(profile, fallbackDisplayName) {
        mutableStateOf(profile.displayName.ifBlank { fallbackDisplayName })
    }
    var bio by remember(profile) { mutableStateOf(profile.bio) }
    var websiteUrl by remember(profile) { mutableStateOf(profile.websiteUrl) }
    var avatarUrl by remember(profile) { mutableStateOf(profile.avatarUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.profile_edit_name_label)) },
                    singleLine = true,
                    enabled = !isSaving,
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(stringResource(R.string.profile_edit_bio_label)) },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSaving,
                )
                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = { websiteUrl = it },
                    label = { Text(stringResource(R.string.profile_edit_website_label)) },
                    singleLine = true,
                    enabled = !isSaving,
                )
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text(stringResource(R.string.profile_edit_avatar_label)) },
                    singleLine = true,
                    enabled = !isSaving,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(displayName, bio, websiteUrl, avatarUrl) },
                enabled = !isSaving && displayName.trim().length >= 2,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.common_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving, shape = RoundedCornerShape(8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun CreatorMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CreatorRow(
    creator: CreatorStats,
    isCurrentUser: Boolean,
    actionInFlight: Boolean,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onBlock: (() -> Unit)?,
) {
    var showBlockConfirm by remember(creator.creatorId) { mutableStateOf(false) }
    if (showBlockConfirm && onBlock != null) {
        AlertDialog(
            onDismissRequest = { if (!actionInFlight) showBlockConfirm = false },
            title = { Text(stringResource(R.string.detail_block_title)) },
            text = { Text(stringResource(R.string.detail_block_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirm = false
                        onBlock()
                    },
                    enabled = !actionInFlight,
                ) {
                    Text(stringResource(R.string.reports_block_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }, enabled = !actionInFlight) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(creator.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${creator.uploadCount} uploads - ${creator.totalVotes} votes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isCurrentUser) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = if (creator.isFollowed) onUnfollow else onFollow,
                        enabled = !actionInFlight,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        if (actionInFlight) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (creator.isFollowed) "Following" else "Follow")
                        }
                    }
                    if (onBlock != null) {
                        IconButton(
                            onClick = { showBlockConfirm = true },
                            enabled = !actionInFlight,
                        ) {
                            Icon(Icons.Default.Block, contentDescription = "Block creator")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadRow(upload: CreatorUploadRef) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Text(
                    upload.contentType.take(1).uppercase(Locale.ROOT),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(upload.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${upload.creatorLabel} - ${upload.votes} votes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyCreatorSection(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun CreatorProfileDashboard.withoutCreator(creatorId: String): CreatorProfileDashboard =
    copy(
        topCreators = topCreators.filterNot { it.matchesCreator(creatorId) },
        followedCreators = followedCreators.filterNot { it.matchesCreator(creatorId) },
        followedUploads = followedUploads.filterNot { it.matchesCreator(creatorId) },
    )

private fun CreatorProfileDashboard.withCurrentProfile(profile: CreatorPublicProfile): CreatorProfileDashboard =
    copy(
        currentProfile = profile,
        currentCreator = currentCreator.copy(label = profile.displayName.ifBlank { currentCreator.label }),
    )

private fun CreatorStats.matchesCreator(creatorId: String): Boolean =
    isCommunityUserBlocked(
        uploaderUid = this.creatorId,
        uploaderId = this.creatorId,
        blockedUserIds = setOf(creatorId),
    )

private fun CreatorUploadRef.matchesCreator(creatorId: String): Boolean =
    isCommunityUserBlocked(
        uploaderUid = this.creatorId,
        uploaderId = this.creatorId,
        blockedUserIds = setOf(creatorId),
    )
