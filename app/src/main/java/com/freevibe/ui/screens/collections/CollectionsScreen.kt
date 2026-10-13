package com.freevibe.ui.screens.collections

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.WallpaperCollectionItemEntity
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.service.CollectionExporter
import com.freevibe.service.CollectionImportResult
import com.freevibe.service.PhotoPickerCustomization
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.DeletedCollectionSnapshot
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.EmbeddedImagePickerSheet
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareCollectionEvent {
    data class Ready(val intent: Intent, val collectionName: String) : ShareCollectionEvent
    data class Message(val message: String) : ShareCollectionEvent
    data class Failure(val message: String) : ShareCollectionEvent
}

data class CollectionQrState(
    val collectionName: String,
    val shareLink: String,
    val itemCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collectionRepo: CollectionRepository,
    private val selectedContent: SelectedContentHolder,
    private val collectionExporter: CollectionExporter,
) : ViewModel() {

    private val _shareEvent = MutableStateFlow<ShareCollectionEvent?>(null)
    val shareEvent: StateFlow<ShareCollectionEvent?> = _shareEvent.asStateFlow()
    fun consumeShareEvent() { _shareEvent.value = null }

    private val _qrState = MutableStateFlow<CollectionQrState?>(null)
    val qrState: StateFlow<CollectionQrState?> = _qrState.asStateFlow()
    fun dismissQr() { _qrState.value = null }

    fun shareCollection(collection: WallpaperCollectionEntity) {
        viewModelScope.launch {
            collectionExporter.prepareShareBundle(collection.collectionId, collection.name)
                .onSuccess { bundle ->
                    _shareEvent.value = ShareCollectionEvent.Ready(
                        collectionExporter.buildShareIntent(bundle),
                        bundle.collectionName,
                    )
                }
                .onFailure { e ->
                    Log.w(TAG, "Collection share bundle preparation failed", e)
                    _shareEvent.value = ShareCollectionEvent.Failure(
                        context.getString(R.string.collections_share_prepare_failed)
                    )
                }
        }
    }

    fun showQr(collection: WallpaperCollectionEntity) {
        viewModelScope.launch {
            collectionExporter.publishShareLink(collection.collectionId, collection.name)
                .onSuccess { link ->
                    _qrState.value = CollectionQrState(
                        collectionName = link.collectionName,
                        shareLink = link.link,
                        itemCount = link.itemCount,
                    )
                }
                .onFailure { e ->
                    Log.w(TAG, "Collection share link creation failed", e)
                    _shareEvent.value = ShareCollectionEvent.Failure(
                        context.getString(R.string.collections_share_link_failed),
                    )
                }
        }
    }

    fun importCollectionLink(input: String) {
        viewModelScope.launch {
            collectionExporter.importFromTokenOrLink(input).handleImportResult()
        }
    }

    fun importCollectionFile(uri: Uri) {
        viewModelScope.launch {
            collectionExporter.importFromUri(uri).handleImportResult()
        }
    }

    fun importCollectionQr(uri: Uri) {
        viewModelScope.launch {
            collectionExporter.importFromQrImage(uri).handleImportResult()
        }
    }

    /** Drawn off the main thread; null when the link can't be encoded, so the dialog can say so. */
    suspend fun buildQrBitmap(link: String): ImageBitmap? = try {
        collectionExporter.buildQrBitmap(link).asImageBitmap()
    } catch (e: Exception) {
        e.rethrowIfCancelled()
        null
    }

    private fun Result<CollectionImportResult>.handleImportResult() {
        onSuccess { result ->
            _selectedCollectionId.value = result.collectionId
            _shareEvent.value = ShareCollectionEvent.Message(
                context.getString(
                    R.string.collections_import_summary,
                    result.collectionName,
                    result.itemCount,
                    result.skippedCount,
                    result.failedCount,
                )
            )
        }.onFailure { e ->
            Log.w(TAG, "Collection import failed", e)
            _shareEvent.value = ShareCollectionEvent.Failure(
                context.getString(R.string.collections_import_failed),
            )
        }
    }

    val collections = collectionRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    val selectedCollectionId = _selectedCollectionId.asStateFlow()

    val selectedItems: StateFlow<List<WallpaperCollectionItemEntity>> = _selectedCollectionId
        .flatMapLatest { id ->
            if (id != null) collectionRepo.getItems(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCollection(id: Long) { _selectedCollectionId.value = id }
    fun clearSelection() { _selectedCollectionId.value = null }

    fun createCollection(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return
        viewModelScope.launch {
            _selectedCollectionId.value = collectionRepo.create(normalizedName)
        }
    }

    fun selectWallpaper(item: WallpaperCollectionItemEntity, items: List<WallpaperCollectionItemEntity>) {
        val wallpapers = items.map { it.toWallpaper() }
        selectedContent.selectWallpaper(
            item.toWallpaper(),
            wallpapers,
        )
    }

    /**
     * Deletes a collection and hands back the snapshot needed to undo it.
     *
     * Removing one item already offered Undo while deleting the whole collection
     * was immediate and total; [onDeleted] lets the caller offer the same escape.
     */
    fun deleteCollection(id: Long, onDeleted: (DeletedCollectionSnapshot?) -> Unit = {}) {
        viewModelScope.launch {
            val snapshot = collectionRepo.deleteWithSnapshot(id)
            _selectedCollectionId.value = null
            onDeleted(snapshot)
        }
    }

    fun restoreCollection(snapshot: DeletedCollectionSnapshot) {
        viewModelScope.launch { collectionRepo.restore(snapshot) }
    }

    fun removeItem(collectionId: Long, item: WallpaperCollectionItemEntity) {
        viewModelScope.launch { collectionRepo.removeWallpaper(collectionId, item.toWallpaper()) }
    }

    fun addItem(collectionId: Long, item: WallpaperCollectionItemEntity) {
        viewModelScope.launch { collectionRepo.addWallpaper(collectionId, item.toWallpaper()) }
    }

    fun renameCollection(id: Long, name: String) {
        viewModelScope.launch { collectionRepo.rename(id, name) }
    }

    fun getItemCount(collectionId: Long): Flow<Int> = collectionRepo.getItemCount(collectionId)
    fun getCoverThumbnails(collectionId: Long): Flow<List<String>> = collectionRepo.getCoverThumbnails(collectionId)

    private companion object {
        const val TAG = "CollectionsViewModel"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onWallpaperClick: (Wallpaper) -> Unit,
    initialImportToken: String? = null,
    initialImportUri: String? = null,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val selectedCollection = collections.find { it.collectionId == selectedCollectionId }
    val qrState by viewModel.qrState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showImportSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Observe prepared share/import events and keep system intents out of recomposition.
    val context = androidx.compose.ui.platform.LocalContext.current
    // Resource lookups go through LocalResources, not the context: reading a
    // string off LocalContext is not a composition read, so these labels would
    // survive a language change unchanged until something else recomposed them.
    val resources = androidx.compose.ui.platform.LocalResources.current
    val clipboard = LocalClipboardManager.current
    val shareEvent by viewModel.shareEvent.collectAsStateWithLifecycle()
    var showEmbeddedQrPicker by remember { mutableStateOf(false) }
    val jsonImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importCollectionFile)
    }
    fun handleQrImageUri(uri: Uri?) {
        uri?.let(viewModel::importCollectionQr)
    }
    // QR code import via Photo Picker (no READ_MEDIA_IMAGES; scoped-storage compliant).
    // Supported Android 14+ extension devices use the embedded picker; fallback remains
    // ActivityResultContracts.PickVisualMedia with Aura's 9:16 portrait grid customization.
    val qrImportLauncher = rememberLauncherForActivityResult(
        com.freevibe.service.AuraPickVisualMedia()
    ) { uri ->
        handleQrImageUri(uri)
    }
    val qrImportPickerRequest = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }
    fun launchQrImportPicker() {
        if (PhotoPickerCustomization.isEmbeddedImagePickerAvailable(context)) {
            showEmbeddedQrPicker = true
        } else {
            qrImportLauncher.launch(qrImportPickerRequest)
        }
    }

    LaunchedEffect(initialImportToken, initialImportUri) {
        initialImportToken?.let(viewModel::importCollectionLink)
        initialImportUri?.let { viewModel.importCollectionFile(Uri.parse(it)) }
    }

    LaunchedEffect(shareEvent) {
        val event = shareEvent
        when (event) {
            is ShareCollectionEvent.Ready -> {
                val intent = android.content.Intent.createChooser(
                    event.intent,
                    resources.getString(R.string.collections_share_chooser, event.collectionName),
                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                try { context.startActivity(intent) } catch (_: Exception) {
                    scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.collections_no_share_target)) }
                }
                viewModel.consumeShareEvent()
            }
            is ShareCollectionEvent.Message -> {
                snackbarHostState.showSnackbar(event.message)
                viewModel.consumeShareEvent()
            }
            is ShareCollectionEvent.Failure -> {
                snackbarHostState.showSnackbar(event.message)
                viewModel.consumeShareEvent()
            }
            null -> Unit
        }
    }

    if (showImportSheet) {
        ImportCollectionSheet(
            onDismiss = { showImportSheet = false },
            onImportLink = { link ->
                showImportSheet = false
                viewModel.importCollectionLink(link)
            },
            onOpenFile = {
                showImportSheet = false
                jsonImportLauncher.launch(arrayOf("application/json", "text/*"))
            },
            onOpenQrImage = {
                showImportSheet = false
                launchQrImportPicker()
            },
        )
    }
    if (showCreateDialog) {
        CreateCollectionDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                viewModel.createCollection(name)
            },
        )
    }
    if (showRenameDialog && selectedCollection != null) {
        RenameCollectionDialog(
            currentName = selectedCollection.name,
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                showRenameDialog = false
                viewModel.renameCollection(selectedCollection.collectionId, newName)
            },
        )
    }
    if (showEmbeddedQrPicker) {
        EmbeddedImagePickerSheet(
            title = stringResource(R.string.photo_picker_qr_title),
            body = stringResource(R.string.photo_picker_qr_body),
            fallbackLabel = stringResource(R.string.photo_picker_fallback_open),
            onDismiss = { showEmbeddedQrPicker = false },
            onFallback = {
                showEmbeddedQrPicker = false
                qrImportLauncher.launch(qrImportPickerRequest)
            },
            onImagePicked = { uri ->
                showEmbeddedQrPicker = false
                handleQrImageUri(uri)
            },
        )
    }

    qrState?.let { state ->
        val qrImage by produceState<CollectionQrImage>(CollectionQrImage.Loading, state.shareLink) {
            value = viewModel.buildQrBitmap(state.shareLink)?.let(CollectionQrImage::Ready) ?: CollectionQrImage.Failed
        }
        CollectionQrDialog(
            state = state,
            qrImage = qrImage,
            onCopyLink = {
                clipboard.setText(AnnotatedString(state.shareLink))
                scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.collections_link_copied)) }
            },
            onDismiss = viewModel::dismissQr,
        )
    }

    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(selectedCollection?.name ?: stringResource(R.string.collections_title))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedCollectionId != null) viewModel.clearSelection() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (selectedCollection != null) {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.collections_more))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.collections_share_link_and_file)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.shareCollection(selectedCollection)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.collections_show_qr)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.showQr(selectedCollection)
                                    },
                                    leadingIcon = { Icon(Icons.Default.QrCode2, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.collections_rename)) },
                                    onClick = {
                                        showMenu = false
                                        showRenameDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.collections_delete)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.deleteCollection(selectedCollection.collectionId) { snapshot ->
                                            if (snapshot == null) return@deleteCollection
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = resources.getString(
                                                        R.string.collections_deleted,
                                                        snapshot.collection.name,
                                                    ),
                                                    actionLabel = resources.getString(R.string.common_undo),
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.restoreCollection(snapshot)
                                                }
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, stringResource(R.string.collections_create))
                        }
                        IconButton(onClick = { showImportSheet = true }) {
                            Icon(Icons.Default.FileDownload, stringResource(R.string.collections_import))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (selectedCollectionId != null) {
            BackHandler { viewModel.clearSelection() }
            // Collection detail: grid of wallpapers
            if (selectedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    AuraStateCard(
                        icon = Icons.Default.Folder,
                        title = stringResource(R.string.collections_empty_title),
                        description = stringResource(R.string.collections_empty_body),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    items(selectedItems.size, key = { selectedItems[it].stableKey() }) { index ->
                        val item = selectedItems[index]
                        val itemLabel = "${item.source} ${stringResource(R.string.collections_item_fallback_label)}"
                        val removeLabel = stringResource(R.string.collections_item_remove)
                        @OptIn(ExperimentalFoundationApi::class)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .semantics(mergeDescendants = true) {
                                    contentDescription = itemLabel
                                    customActions = listOf(
                                        CustomAccessibilityAction(removeLabel) {
                                            val cid = selectedCollectionId ?: return@CustomAccessibilityAction false
                                            viewModel.removeItem(cid, item)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = resources.getString(R.string.collections_removed),
                                                    actionLabel = resources.getString(R.string.common_undo),
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.addItem(cid, item)
                                                }
                                            }
                                            true
                                        },
                                    )
                                }
                                .combinedClickable(
                                    onClick = {
                                        val wallpaper = item.toWallpaper()
                                        viewModel.selectWallpaper(item, selectedItems)
                                        onWallpaperClick(wallpaper)
                                    },
                                    onLongClick = {
                                        val cid = selectedCollectionId ?: return@combinedClickable
                                        viewModel.removeItem(cid, item)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.collections_removed),
                                                actionLabel = resources.getString(R.string.common_undo),
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.addItem(cid, item)
                                            }
                                        }
                                    },
                                ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = itemLabel,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.67f),
                            )
                        }
                    }
                }
            }
        } else {
            // Collection list
            if (collections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    AuraStateCard(
                        icon = Icons.Default.CreateNewFolder,
                        title = stringResource(R.string.collections_list_empty_title),
                        description = stringResource(R.string.collections_list_empty_body),
                        primaryAction = AuraStateAction(
                            label = stringResource(R.string.collections_create),
                            icon = Icons.Default.CreateNewFolder,
                            onClick = { showCreateDialog = true },
                        ),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    items(collections, key = { it.collectionId }) { collection ->
                        CollectionCard(
                            collection = collection,
                            viewModel = viewModel,
                            onClick = { viewModel.selectCollection(collection.collectionId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val normalizedName = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
        title = { Text(stringResource(R.string.collections_create_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.detail_collection_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(normalizedName) },
                enabled = normalizedName.isNotEmpty(),
            ) {
                Text(stringResource(R.string.collections_create_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun RenameCollectionDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    val normalizedName = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        title = { Text(stringResource(R.string.collections_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.detail_collection_name_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(normalizedName) },
                enabled = normalizedName.isNotEmpty() && normalizedName != currentName.trim(),
            ) {
                Text(stringResource(R.string.collections_rename_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportCollectionSheet(
    onDismiss: () -> Unit,
    onImportLink: (String) -> Unit,
    onOpenFile: () -> Unit,
    onOpenQrImage: () -> Unit,
) {
    var link by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.collections_import_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.collections_import_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.collections_import_link_label)) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )
            Button(
                onClick = { onImportLink(link) },
                enabled = link.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.collections_import_link_button))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenFile,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.collections_import_json))
                }
                OutlinedButton(
                    onClick = onOpenQrImage,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.collections_import_qr))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private sealed interface CollectionQrImage {
    data object Loading : CollectionQrImage
    data class Ready(val bitmap: ImageBitmap) : CollectionQrImage
    data object Failed : CollectionQrImage
}

@Composable
private fun CollectionQrDialog(
    state: CollectionQrState,
    qrImage: CollectionQrImage,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
        title = { Text(stringResource(R.string.collections_qr_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    contentColor = androidx.compose.ui.graphics.Color.Black,
                    tonalElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (qrImage) {
                            CollectionQrImage.Loading -> CircularProgressIndicator()
                            is CollectionQrImage.Ready -> Image(
                                bitmap = qrImage.bitmap,
                                contentDescription = stringResource(R.string.collections_qr_cd, state.collectionName),
                                modifier = Modifier.fillMaxSize(),
                            )
                            CollectionQrImage.Failed -> Text(
                                stringResource(R.string.collections_qr_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.collections_qr_info, state.collectionName, state.itemCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    state.shareLink,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCopyLink, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.collections_qr_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.common_done))
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

private fun WallpaperCollectionItemEntity.toWallpaper() = Wallpaper(
    id = wallpaperId,
    source = try { ContentSource.valueOf(source) } catch (_: Exception) { ContentSource.WALLHAVEN },
    thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl,
    width = width,
    height = height,
)

@Composable
private fun CollectionCard(
    collection: WallpaperCollectionEntity,
    viewModel: CollectionsViewModel,
    onClick: () -> Unit,
) {
    val countFlow = remember(collection.collectionId) { viewModel.getItemCount(collection.collectionId) }
    val coversFlow = remember(collection.collectionId) { viewModel.getCoverThumbnails(collection.collectionId) }
    val count by countFlow.collectAsStateWithLifecycle(initialValue = 0)
    val covers by coversFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cover preview (2x2 grid of thumbnails)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (covers.isNotEmpty()) {
                    val gridSize = if (covers.size >= 4) 2 else 1
                    // covers is a List<String> collected out of the flow above, so
                    // this is List.take. Lint resolves it through the `by` delegate
                    // and lands on Flow.take, which would indeed be wrong here.
                    @Suppress("FlowOperatorInvokedInComposition")
                    val displayCovers = covers.take(gridSize * gridSize)
                    Column {
                        for (row in 0 until gridSize) {
                            Row(modifier = Modifier.weight(1f)) {
                                for (col in 0 until gridSize) {
                                    val idx = row * gridSize + col
                                    if (idx < displayCovers.size) {
                                        AsyncImage(
                                            model = displayCovers[idx],
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    collection.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.collections_wallpaper_count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
