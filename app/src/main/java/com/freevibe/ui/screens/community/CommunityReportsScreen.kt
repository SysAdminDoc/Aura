package com.freevibe.ui.screens.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityReportReason
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.CommunityReportRecord
import com.freevibe.data.model.CommunityReportResolutionStatus
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@Immutable
data class CommunityReportsUiState(
    val actionInFlightReportId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityReportsViewModel @Inject constructor(
    private val reportRepo: CommunityReportRepository,
    private val voteRepo: VoteRepository,
    private val blockRepo: CommunityBlockRepository,
) : ViewModel() {
    val isAdmin: Boolean get() = voteRepo.isAdmin
    private val _selectedStatus = MutableStateFlow(CommunityReportResolutionStatus.OPEN)
    val selectedStatus = _selectedStatus.asStateFlow()
    val reports = if (isAdmin) {
        _selectedStatus.flatMapLatest { status ->
            reportRepo.reports(status = status)
        }
    } else {
        flowOf(emptyList())
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(CommunityReportsUiState())
    val state = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(message = "Report queue refreshed", error = null) }
    }

    fun selectStatus(status: CommunityReportResolutionStatus) {
        _selectedStatus.value = status
        _state.update { it.copy(message = "${status.reviewLabel} reports", error = null) }
    }

    fun hide(report: CommunityReportRecord) {
        resolve(report, CommunityReportResolutionStatus.HIDDEN, "Hidden from report queue") {
            voteRepo.moderateHide(report.contentId)
        }
    }

    fun dismiss(report: CommunityReportRecord) {
        resolve(report, CommunityReportResolutionStatus.DISMISSED, "Dismissed from report queue")
    }

    fun restore(report: CommunityReportRecord) {
        resolve(report, CommunityReportResolutionStatus.RESTORED, "Restored from report queue") {
            voteRepo.moderateUnhide(report.contentId)
        }
    }

    fun deleteUpload(report: CommunityReportRecord) {
        viewModelScope.launch {
            _state.update { it.copy(actionInFlightReportId = report.id, error = null, message = null) }
            runCatching {
                voteRepo.moderateHide(report.contentId)
                reportRepo.deleteReportedCommunityUpload(report.id, "Deleted after rights review").getOrThrow()
            }.onSuccess {
                _state.update {
                    it.copy(
                        actionInFlightReportId = null,
                        message = "Upload deleted",
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update {
                    it.copy(
                        actionInFlightReportId = null,
                        error = error.message ?: "Upload delete failed",
                    )
                }
            }
        }
    }

    fun blockReportedUploader(report: CommunityReportRecord) {
        val uploaderUid = report.uploaderUid
        if (!report.canBlockReportedUploader()) {
            _state.update { it.copy(error = "This report does not expose a blockable community uploader", message = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(actionInFlightReportId = report.id, error = null, message = null) }
            blockRepo.blockUser(uploaderUid, CommunityBlockReason.OTHER)
                .onSuccess {
                    _state.update {
                        it.copy(
                            actionInFlightReportId = null,
                            message = "Creator blocked",
                        )
                    }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.update {
                        it.copy(
                            actionInFlightReportId = null,
                            error = error.message ?: "Creator block failed",
                        )
                    }
                }
        }
    }

    private fun resolve(
        report: CommunityReportRecord,
        status: CommunityReportResolutionStatus,
        note: String,
        beforeResolve: suspend () -> Unit = {},
    ) {
        viewModelScope.launch {
            _state.update { it.copy(actionInFlightReportId = report.id, error = null, message = null) }
            runCatching {
                beforeResolve()
                reportRepo.resolveReport(report.id, status, note).getOrThrow()
            }.onSuccess {
                _state.update {
                    it.copy(
                        actionInFlightReportId = null,
                        message = when (status) {
                            CommunityReportResolutionStatus.HIDDEN -> "Report hidden"
                            CommunityReportResolutionStatus.DISMISSED -> "Report dismissed"
                            CommunityReportResolutionStatus.RESTORED -> "Report restored"
                            CommunityReportResolutionStatus.OPEN -> "Report updated"
                        },
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update {
                    it.copy(
                        actionInFlightReportId = null,
                        error = error.message ?: "Report action failed",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityReportsScreen(
    onBack: () -> Unit,
    viewModel: CommunityReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh reports")
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
                !viewModel.isAdmin -> AuraStateCard(
                    icon = Icons.Default.VerifiedUser,
                    title = "Admin access required",
                    description = "Report details are only available to accounts with the admin custom claim.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                reports.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ReportStatusChips(
                        selectedStatus = selectedStatus,
                        onSelectStatus = viewModel::selectStatus,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        AuraStateCard(
                            icon = Icons.Default.Report,
                            title = "No ${selectedStatus.reviewLabel.lowercase(Locale.ROOT)} reports",
                            description = if (selectedStatus == CommunityReportResolutionStatus.OPEN) {
                                "New reports will appear here after users submit them from content detail screens."
                            } else {
                                "Closed reports will appear here after admins take action."
                            },
                            primaryAction = AuraStateAction("Refresh", Icons.Default.Refresh, viewModel::refresh),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ReportStatusChips(
                            selectedStatus = selectedStatus,
                            onSelectStatus = viewModel::selectStatus,
                        )
                    }
                    if (state.error != null || state.message != null) {
                        item {
                            FilterChip(
                                selected = state.error == null,
                                onClick = viewModel::refresh,
                                label = { Text(state.error ?: state.message.orEmpty()) },
                                leadingIcon = {
                                    Icon(
                                        if (state.error == null) Icons.Default.CheckCircle else Icons.Default.Report,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                    items(reports, key = { it.id }) { report ->
                        ReportCard(
                            report = report,
                            busy = state.actionInFlightReportId == report.id,
                            onHide = { viewModel.hide(report) },
                            onDismiss = { viewModel.dismiss(report) },
                            onRestore = { viewModel.restore(report) },
                            onDeleteUpload = if (report.canDeleteCommunityUpload()) {
                                { viewModel.deleteUpload(report) }
                            } else {
                                null
                            },
                            onBlockUploader = if (report.canBlockReportedUploader()) {
                                { viewModel.blockReportedUploader(report) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportStatusChips(
    selectedStatus: CommunityReportResolutionStatus,
    onSelectStatus: (CommunityReportResolutionStatus) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(CommunityReportReviewFilters, key = { it.storageValue }) { status ->
            FilterChip(
                selected = selectedStatus == status,
                onClick = { onSelectStatus(status) },
                label = { Text(status.reviewLabel) },
            )
        }
    }
}

@Composable
private fun ReportCard(
    report: CommunityReportRecord,
    busy: Boolean,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onDeleteUpload: (() -> Unit)?,
    onBlockUploader: (() -> Unit)?,
) {
    var showDeleteConfirm by remember(report.id) { mutableStateOf(false) }
    var showBlockConfirm by remember(report.id) { mutableStateOf(false) }
    if (showDeleteConfirm && onDeleteUpload != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) showDeleteConfirm = false },
            title = { Text("Delete upload?") },
            text = { Text("This removes the community upload file and catalog row after recording a private takedown receipt.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteUpload()
                    },
                    enabled = !busy,
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !busy) {
                    Text("Cancel")
                }
            },
        )
    }
    if (showBlockConfirm && onBlockUploader != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) showBlockConfirm = false },
            title = { Text("Block creator?") },
            text = { Text("This hides future community uploads from this creator in your personal community feeds.") },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirm = false
                        onBlockUploader()
                    },
                    enabled = !busy,
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }, enabled = !busy) {
                    Text("Cancel")
                }
            },
        )
    }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(report.reason.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${report.contentType} - ${report.contentSource}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text(report.contentId, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (report.note.isNotBlank()) {
                Text(report.note, style = MaterialTheme.typography.bodyMedium)
            }
            ReportFact("License", report.license)
            ReportFact("Uploader", report.uploaderName)
            ReportFact("Uploader UID", report.uploaderUid.take(12))
            ReportFact("Source", report.sourceUrl)
            ReportFact("Reporter", report.reporterUid.take(12))
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onHide, enabled = !busy) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Hide")
                }
                OutlinedButton(onClick = onDismiss, enabled = !busy) {
                    Text("Dismiss")
                }
                TextButton(onClick = onRestore, enabled = !busy) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Restore")
                }
            }
            if (onDeleteUpload != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Delete upload")
                }
            }
            if (onBlockUploader != null) {
                OutlinedButton(
                    onClick = { showBlockConfirm = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Block creator")
                }
            }
        }
    }
}

@Composable
private fun ReportFact(label: String, value: String) {
    if (value.isBlank()) return
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun CommunityReportRecord.canDeleteCommunityUpload(): Boolean =
    reason == CommunityReportReason.RIGHTS &&
        contentSource.equals("COMMUNITY", ignoreCase = true) &&
        contentType.uppercase(Locale.ROOT) in setOf("SOUND", "WALLPAPER")

private fun CommunityReportRecord.canBlockReportedUploader(): Boolean =
    contentSource.equals("COMMUNITY", ignoreCase = true) &&
        uploaderUid.isNotBlank()

private val CommunityReportReviewFilters = listOf(
    CommunityReportResolutionStatus.OPEN,
    CommunityReportResolutionStatus.HIDDEN,
    CommunityReportResolutionStatus.DISMISSED,
    CommunityReportResolutionStatus.RESTORED,
)

private val CommunityReportResolutionStatus.reviewLabel: String
    get() = when (this) {
        CommunityReportResolutionStatus.OPEN -> "Open"
        CommunityReportResolutionStatus.HIDDEN -> "Hidden"
        CommunityReportResolutionStatus.DISMISSED -> "Dismissed"
        CommunityReportResolutionStatus.RESTORED -> "Restored"
    }
