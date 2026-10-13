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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.R
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
import com.freevibe.ui.theme.AuraShapes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@Immutable
data class CommunityReportsUiState(
    val isLoading: Boolean = true,
    val loadError: ReportsLoadError? = null,
    val lastUpdatedAt: Long? = null,
    val actionInFlightReportId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

/** Why the report queue failed to load; the screen maps each kind to its own copy. */
enum class ReportsLoadErrorKind { OFFLINE, DENIED, OTHER }

@Immutable
data class ReportsLoadError(val kind: ReportsLoadErrorKind, val detail: String? = null)

/**
 * Offline and permission failures need different next steps, so they are told
 * apart here. RTDB reports both as a DatabaseException with a message.
 */
internal fun classifyReportsLoadError(error: Throwable): ReportsLoadError {
    val text = error.message.orEmpty()
    val kind = when {
        error is java.io.IOException || OFFLINE_MARKERS.any { text.contains(it, ignoreCase = true) } ->
            ReportsLoadErrorKind.OFFLINE
        DENIED_MARKERS.any { text.contains(it, ignoreCase = true) } -> ReportsLoadErrorKind.DENIED
        else -> ReportsLoadErrorKind.OTHER
    }
    return ReportsLoadError(kind = kind, detail = text.takeIf { it.isNotBlank() })
}

private val OFFLINE_MARKERS = listOf("offline", "network", "disconnect", "unavailable")
private val DENIED_MARKERS = listOf("permission", "denied", "unauthorized")

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
    private val _state = MutableStateFlow(CommunityReportsUiState())
    val state = _state.asStateFlow()
    private val refreshTrigger = MutableStateFlow(0)
    private var loadedStatus: CommunityReportResolutionStatus? = null
    val reports = if (isAdmin) {
        combine(_selectedStatus, refreshTrigger) { status, _ -> status }
            .flatMapLatest { status ->
                _state.update { it.copy(isLoading = true, loadError = null) }
                flow {
                    // Rows from another status tab would be mislabeled, so a switch
                    // clears them. A refresh of the same tab keeps them until new rows land.
                    if (status != loadedStatus) emit(emptyList())
                    emitAll(
                        reportRepo.reports(status = status).onEach {
                            loadedStatus = status
                            _state.update { s ->
                                s.copy(isLoading = false, loadError = null, lastUpdatedAt = System.currentTimeMillis())
                            }
                        },
                    )
                }.catch { e ->
                    // No emission here: the last good list stays on screen under the error.
                    _state.update { s -> s.copy(isLoading = false, loadError = classifyReportsLoadError(e)) }
                }
            }
    } else {
        flowOf(emptyList())
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() {
        refreshTrigger.update { it + 1 }
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

@Composable
private fun reportsLoadErrorText(error: ReportsLoadError?): String = reportsLoadErrorMessage(
    error = error,
    offline = stringResource(R.string.reports_load_error_offline),
    denied = stringResource(R.string.reports_load_error_denied),
    generic = stringResource(R.string.reports_load_error_generic),
)

/**
 * The localized message leads; the raw detail stays visible because this admin-only screen
 * has no other place to show why a load failed.
 */
internal fun reportsLoadErrorMessage(
    error: ReportsLoadError?,
    offline: String,
    denied: String,
    generic: String,
): String = when (error?.kind) {
    ReportsLoadErrorKind.OFFLINE -> offline
    ReportsLoadErrorKind.DENIED -> denied
    else -> generic + error?.detail?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
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
                title = { Text(stringResource(R.string.reports_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reports_refresh))
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
                    title = stringResource(R.string.reports_admin_required_title),
                    description = stringResource(R.string.reports_admin_required_body),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                state.isLoading && reports.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
                state.loadError != null && reports.isEmpty() -> Column(
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
                            title = stringResource(R.string.reports_load_error_title),
                            description = reportsLoadErrorText(state.loadError),
                            primaryAction = AuraStateAction(stringResource(R.string.common_retry), Icons.Default.Refresh, viewModel::refresh),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                    }
                }
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
                            title = stringResource(R.string.reports_empty_title, selectedStatus.reviewLabel.lowercase(Locale.ROOT)),
                            description = stringResource(
                                if (selectedStatus == CommunityReportResolutionStatus.OPEN) {
                                    R.string.reports_empty_open_body
                                } else {
                                    R.string.reports_empty_closed_body
                                }
                            ),
                            primaryAction = AuraStateAction(stringResource(R.string.common_refresh), Icons.Default.Refresh, viewModel::refresh),
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
                    if (state.loadError != null) {
                        item {
                            val updatedAt = state.lastUpdatedAt?.let {
                                java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it))
                            }
                            FilterChip(
                                selected = false,
                                onClick = viewModel::refresh,
                                label = {
                                    Text(
                                        if (updatedAt != null) {
                                            stringResource(R.string.reports_stale_banner, updatedAt)
                                        } else {
                                            stringResource(R.string.reports_stale_banner_no_time)
                                        },
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                            )
                        }
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
            title = { Text(stringResource(R.string.reports_delete_title)) },
            text = { Text(stringResource(R.string.reports_delete_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteUpload()
                    },
                    enabled = !busy,
                    shape = AuraShapes.Medium,
                ) {
                    Text(stringResource(R.string.reports_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !busy) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (showBlockConfirm && onBlockUploader != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) showBlockConfirm = false },
            title = { Text(stringResource(R.string.reports_block_title)) },
            text = { Text(stringResource(R.string.reports_block_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirm = false
                        onBlockUploader()
                    },
                    enabled = !busy,
                    shape = AuraShapes.Medium,
                ) {
                    Text(stringResource(R.string.reports_block_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }, enabled = !busy) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    Card(
        shape = AuraShapes.Medium,
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
                        stringResource(R.string.reports_content_source_summary, report.contentType, report.contentSource),
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
            ReportFact(stringResource(R.string.reports_fact_license), report.license)
            ReportFact(stringResource(R.string.reports_fact_uploader), report.uploaderName)
            ReportFact(stringResource(R.string.reports_fact_uploader_uid), report.uploaderUid.take(12))
            ReportFact(stringResource(R.string.reports_fact_source), report.sourceUrl)
            ReportFact(stringResource(R.string.reports_fact_reporter), report.reporterUid.take(12))
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onHide, enabled = !busy, shape = AuraShapes.Medium) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.reports_action_hide))
                }
                OutlinedButton(onClick = onDismiss, enabled = !busy, shape = AuraShapes.Medium) {
                    Text(stringResource(R.string.reports_action_dismiss))
                }
                TextButton(onClick = onRestore, enabled = !busy) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.reports_action_restore))
                }
            }
            if (onDeleteUpload != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AuraShapes.Medium,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.reports_action_delete))
                }
            }
            if (onBlockUploader != null) {
                OutlinedButton(
                    onClick = { showBlockConfirm = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AuraShapes.Medium,
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.reports_action_block))
                }
            }
        }
    }
}

@Composable
private fun ReportFact(label: String, value: String) {
    if (value.isBlank()) return
    Text(
        stringResource(R.string.reports_fact_label_value, label, value),
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
