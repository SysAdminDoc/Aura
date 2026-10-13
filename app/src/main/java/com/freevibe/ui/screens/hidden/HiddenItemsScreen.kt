package com.freevibe.ui.screens.hidden

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.freevibe.R
import com.freevibe.data.repository.HiddenItem
import com.freevibe.data.repository.HiddenItemKind
import com.freevibe.data.repository.VoteRepository
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStateCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HiddenItemsViewModel @Inject constructor(
    private val voteRepo: VoteRepository,
) : ViewModel() {
    val items: StateFlow<List<HiddenItem>> =
        voteRepo.localHiddenItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(items: List<HiddenItem>) = voteRepo.unhideLocally(items.map(HiddenItem::id))

    fun hideAgain(items: List<HiddenItem>) = voteRepo.hideLocally(items)
}

/** Items of [kind]; unlabeled hides have no kind, so they only show under All (null). */
internal fun List<HiddenItem>.inKind(kind: HiddenItemKind?): List<HiddenItem> =
    if (kind == null) this else filter { it.label?.kind == kind }

@Composable
fun HiddenItemsScreen(
    initialKind: HiddenItemKind?,
    onBack: () -> Unit,
    viewModel: HiddenItemsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(initialKind) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    HiddenItemsContent(
        items = items,
        filter = filter,
        onFilterChange = { filter = it },
        onRestore = { restored ->
            viewModel.restore(restored)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = resources.getQuantityString(R.plurals.hidden_items_restored, restored.size, restored.size),
                    actionLabel = resources.getString(R.string.common_undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.hideAgain(restored)
            }
        },
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HiddenItemsContent(
    items: List<HiddenItem>,
    filter: HiddenItemKind?,
    onFilterChange: (HiddenItemKind?) -> Unit,
    onRestore: (List<HiddenItem>) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val visible = remember(items, filter) { items.inKind(filter) }
    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_hidden_items)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (visible.isNotEmpty()) {
                        TextButton(onClick = { onRestore(visible) }) {
                            Text(stringResource(R.string.hidden_items_restore_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    null to R.string.hidden_items_filter_all,
                    HiddenItemKind.VIDEO to R.string.hidden_items_filter_videos,
                    HiddenItemKind.WALLPAPER to R.string.hidden_items_filter_wallpapers,
                    HiddenItemKind.SOUND to R.string.hidden_items_filter_sounds,
                ).forEach { (kind, labelRes) ->
                    FilterChip(
                        selected = filter == kind,
                        onClick = { onFilterChange(kind) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AuraStateCard(
                        icon = Icons.Default.VisibilityOff,
                        title = stringResource(R.string.hidden_items_empty_title),
                        description = stringResource(R.string.hidden_items_empty_body),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.id }) { item ->
                        HiddenItemRow(item = item, onRestore = { onRestore(listOf(item)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenItemRow(item: HiddenItem, onRestore: () -> Unit) {
    val label = item.label
    val title = when {
        label == null -> stringResource(R.string.hidden_items_unlabeled)
        label.title.isNotBlank() -> label.title
        else -> stringResource(kindName(label.kind))
    }
    val detail = label?.let { listOf(stringResource(kindName(it.kind)), it.sourceName).filter(String::isNotBlank).joinToString(" · ") }
    val restoreDescription = stringResource(R.string.hidden_items_restore_cd, title)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (!label?.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = label?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(kindIcon(label?.kind), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!detail.isNullOrBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(
                onClick = onRestore,
                modifier = Modifier.semantics { contentDescription = restoreDescription },
            ) {
                Text(stringResource(R.string.hidden_items_restore))
            }
        }
    }
}

private fun kindName(kind: HiddenItemKind): Int = when (kind) {
    HiddenItemKind.VIDEO -> R.string.hidden_items_kind_video
    HiddenItemKind.WALLPAPER -> R.string.hidden_items_kind_wallpaper
    HiddenItemKind.SOUND -> R.string.hidden_items_kind_sound
}

private fun kindIcon(kind: HiddenItemKind?): ImageVector = when (kind) {
    HiddenItemKind.VIDEO -> Icons.Default.Videocam
    HiddenItemKind.WALLPAPER -> Icons.Default.Wallpaper
    HiddenItemKind.SOUND -> Icons.Default.MusicNote
    null -> Icons.Default.VisibilityOff
}
