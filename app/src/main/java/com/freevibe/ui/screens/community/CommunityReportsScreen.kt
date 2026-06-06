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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.CommunityReportRecord
import com.freevibe.data.model.CommunityReportResolutionStatus
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class CommunityReportsUiState(
    val actionInFlightReportId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class CommunityReportsViewModel @Inject constructor(
    private val reportRepo: CommunityReportRepository,
    private val voteRepo: VoteRepository,
) : ViewModel() {
    val isAdmin: Boolean get() = voteRepo.isAdmin
    val reports = if (isAdmin) {
        reportRepo.reports()
    } else {
        flowOf(emptyList())
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(CommunityReportsUiState())
    val state = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(message = "Report queue refreshed", error = null) }
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
                reports.isEmpty() -> AuraStateCard(
                    icon = Icons.Default.Report,
                    title = "No open reports",
                    description = "New reports will appear here after users submit them from content detail screens.",
                    primaryAction = AuraStateAction("Refresh", Icons.Default.Refresh, viewModel::refresh),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                        )
                    }
                }
            }
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
) {
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
