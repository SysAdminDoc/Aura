package com.freevibe.ui.screens.sounds

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.toSound
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.service.BundledContentProvider
import com.freevibe.service.ContactDndGuidance
import com.freevibe.service.ContactInfo
import com.freevibe.service.ContactRingtoneBackupManager
import com.freevibe.service.ContactRingtoneNotKeptException
import com.freevibe.service.ContactRingtoneService
import com.freevibe.service.contactEditorUri
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundUrlResolver
import com.freevibe.ui.theme.AuraShapes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import com.freevibe.data.model.SoundAction
import com.freevibe.data.model.SoundActionDecision
import com.freevibe.data.model.soundLicenseCapabilities
import com.freevibe.util.tryStartActivity

data class ContactPickerState(
    val selectedContact: ContactInfo? = null,
    /** The picker's URI, the only way to read the contact back without READ_CONTACTS. */
    val selectedContactUri: Uri? = null,
    val dndGuidance: ContactDndGuidance? = null,
    val pendingVipOnly: Boolean = false,
    val isLoading: Boolean = false,
    val hasWritePermission: Boolean = false,
    val selectedSound: Sound? = null,
    val isApplying: Boolean = false,
    val applyingContactId: Long? = null,
    val success: String? = null,
    val error: String? = null,
)

private data class PendingContactAction(
    val contactId: Long,
    val message: String,
    val vipOnly: Boolean,
)

private data class PendingWriteAction(
    val contactId: Long,
    val vipOnly: Boolean,
)

@HiltViewModel
class ContactPickerViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val contactService: ContactRingtoneService,
    private val soundApplier: SoundApplier,
    private val favoritesRepo: FavoritesRepository,
    private val bundledContent: BundledContentProvider,
    private val soundUrlResolver: SoundUrlResolver,
    private val contactRingtoneBackup: ContactRingtoneBackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactPickerState())
    val state = _state.asStateFlow()

    fun setWritePermissionGranted(granted: Boolean) {
        _state.update { it.copy(hasWritePermission = granted) }
    }

    fun loadSelectedContact(contactUri: Uri) {
        _state.update { it.copy(isLoading = true, error = null, selectedContact = null, selectedContactUri = contactUri) }
        viewModelScope.launch {
            try {
                val contact = contactService.getContact(contactUri)
                _state.update {
                    it.copy(
                        selectedContact = contact,
                        isLoading = false,
                        error = if (contact == null) "Aura could not read the selected contact." else null,
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    suspend fun ensureSelectedSound(soundId: String, fallbackSound: Sound?): Boolean {
        val resolved = resolveSound(soundId)
        val sound = when {
            fallbackSound == null -> resolved
            resolved == null -> fallbackSound
            matchesFallbackIdentity(resolved, fallbackSound) -> resolved
            else -> fallbackSound
        } ?: run {
            _state.update { it.copy(selectedSound = null) }
            return false
        }
        _state.update { it.copy(selectedSound = sound, error = null) }
        return true
    }

    fun assignToContact(
        contactId: Long,
        confirmed: Boolean = false,
        ignoreDnd: Boolean = false,
    ) {
        assignContact(contactId, confirmed, vipOnly = false, ignoreDnd = ignoreDnd)
    }

    fun assignVipOnlyRinging(
        contactId: Long,
        confirmed: Boolean = false,
        ignoreDnd: Boolean = false,
    ) {
        assignContact(contactId, confirmed, vipOnly = true, ignoreDnd = ignoreDnd)
    }

    private fun assignContact(
        contactId: Long,
        confirmed: Boolean,
        vipOnly: Boolean,
        ignoreDnd: Boolean,
    ) {
        val sound = _state.value.selectedSound ?: run {
            _state.update { it.copy(error = "No sound selected. Return to Sounds and choose a valid item.") }
            return
        }
        soundActionGateMessage(sound, confirmed)?.let { message ->
            _state.update { it.copy(error = message) }
            return
        }
        _state.update {
            it.copy(
                isApplying = true,
                applyingContactId = contactId,
                dndGuidance = null,
                pendingVipOnly = false,
                error = null,
                success = null,
            )
        }
        viewModelScope.launch {
            val contact = _state.value.selectedContact
            if (!ignoreDnd && contact != null) {
                val guidance = contactService.getDndGuidance(contact)
                if (guidance != ContactDndGuidance.NONE) {
                    _state.update {
                        it.copy(
                            isApplying = false,
                            applyingContactId = null,
                            dndGuidance = guidance,
                            pendingVipOnly = vipOnly,
                        )
                    }
                    return@launch
                }
            }
            val dlUrl = soundUrlResolver.resolve(sound)
            if (dlUrl.isNullOrBlank()) {
                _state.update { it.copy(isApplying = false, applyingContactId = null, error = "This sound does not have a downloadable ringtone file.") }
                return@launch
            }
            soundApplier.downloadOnly(dlUrl, sound.name, ContentType.RINGTONE)
                .onSuccess { uri ->
                    val readBackUri = _state.value.selectedContactUri.takeIf { contact?.id == contactId }
                    contactService.setContactRingtone(contactId, uri, readBackUri)
                        .onSuccess {
                            // Noted so a library backup can name this contact and sound later.
                            if (contact != null && contact.id == contactId) {
                                contactRingtoneBackup.record(contact, sound, uri.toString())
                            }
                            if (!vipOnly) {
                                _state.update {
                                    it.copy(
                                        isApplying = false,
                                        applyingContactId = null,
                                        success = "Ringtone set for contact",
                                    )
                                }
                            } else {
                                soundApplier.setDefaultRingtoneSilent()
                                    .onSuccess {
                                        _state.update {
                                            it.copy(
                                                isApplying = false,
                                                applyingContactId = null,
                                                success = context.getString(R.string.contact_picker_vip_success),
                                            )
                                        }
                                    }
                                    .onFailure { e ->
                                        _state.update {
                                            it.copy(
                                                isApplying = false,
                                                applyingContactId = null,
                                                error = context.getString(
                                                    R.string.contact_picker_vip_default_failed,
                                                    e.message ?: e.javaClass.simpleName,
                                                ),
                                            )
                                        }
                                    }
                                }
                        }
                        .onFailure { e ->
                            val message = if (e is ContactRingtoneNotKeptException) {
                                context.getString(R.string.contact_picker_ringtone_not_kept)
                            } else {
                                e.message
                            }
                            _state.update { it.copy(isApplying = false, applyingContactId = null, error = message) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(isApplying = false, applyingContactId = null, error = "Download failed: ${e.message}") }
                }
        }
    }

    fun clearMessages() = _state.update { it.copy(success = null, error = null) }

    fun clearDndGuidance() = _state.update { it.copy(dndGuidance = null, pendingVipOnly = false) }

    private suspend fun resolveSound(soundId: String): Sound? {
        favoritesRepo.getLatestByIdAndType(soundId, "SOUND")
            ?.takeIf { it.type == "SOUND" }
            ?.toSound()
            ?.let { return it }

        return listOf(
            bundledContent.getRingtones(),
            bundledContent.getNotifications(),
            bundledContent.getAlarms(),
        ).flatten().firstOrNull { it.id == soundId }
    }

    private fun matchesFallbackIdentity(sound: Sound, fallbackSound: Sound): Boolean {
        if (sound.id != fallbackSound.id) return false
        if (sound.source != fallbackSound.source) return false
        if (fallbackSound.previewUrl.isNotBlank() && sound.previewUrl != fallbackSound.previewUrl) return false
        if (fallbackSound.downloadUrl.isNotBlank() && sound.downloadUrl != fallbackSound.downloadUrl) return false
        return true
    }

    private fun soundActionGateMessage(sound: Sound, confirmed: Boolean): String? {
        val capability = sound.soundLicenseCapabilities().capability(SoundAction.APPLY)
        return when (capability.decision) {
            SoundActionDecision.ALLOWED -> null
            SoundActionDecision.CONFIRMATION_REQUIRED -> capability.reason.takeUnless { confirmed }
            SoundActionDecision.DISABLED -> capability.reason
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerScreen(
    soundId: String,
    fallbackSound: Sound? = null,
    onBack: () -> Unit,
    viewModel: ContactPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val errorMessage = state.error?.let { stringResource(R.string.common_error_format, it) }
    val noContactPickerMessage = stringResource(R.string.contact_picker_unavailable_message)
    val dndSettingsUnavailableMessage = stringResource(R.string.contact_picker_dnd_settings_unavailable)
    val dndContactUnavailableMessage = stringResource(R.string.contact_picker_dnd_contact_unavailable)
    val appSettingsUnavailableMessage = stringResource(R.string.contact_picker_app_settings_unavailable)
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    val soundIdentityKey = remember(soundId, fallbackSound?.source, fallbackSound?.previewUrl, fallbackSound?.downloadUrl) {
        listOf(
            soundId,
            fallbackSound?.source?.name.orEmpty(),
            fallbackSound?.previewUrl.orEmpty(),
            fallbackSound?.downloadUrl.orEmpty(),
        ).joinToString("|")
    }
    var soundResolved by remember(soundIdentityKey) { mutableStateOf<Boolean?>(null) }
    var pendingContactAction by remember(soundIdentityKey) { mutableStateOf<PendingContactAction?>(null) }
    var pendingWriteAction by remember(soundIdentityKey) { mutableStateOf<PendingWriteAction?>(null) }

    LaunchedEffect(soundIdentityKey) {
        soundResolved = viewModel.ensureSelectedSound(soundId, fallbackSound)
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let(viewModel::loadSelectedContact)
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setWritePermissionGranted(granted)
        val pending = pendingWriteAction
        pendingWriteAction = null
        if (granted && pending != null) {
            if (pending.vipOnly) {
                viewModel.assignVipOnlyRinging(pending.contactId, confirmed = true)
            } else {
                viewModel.assignToContact(pending.contactId, confirmed = true)
            }
        } else if (!granted) {
            val activity = context as? Activity
            if (activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CONTACTS)
            ) {
                permissionPermanentlyDenied = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasWrite = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.setWritePermissionGranted(hasWrite)
    }

    LaunchedEffect(state.success) {
        state.success?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }

    fun launchSystemContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        try {
            contactPickerLauncher.launch(intent)
        } catch (_: Exception) {
            scope.launch { snackbarHostState.showSnackbar(noContactPickerMessage) }
        }
    }

    fun requestWriteOrAssign(contactId: Long, vipOnly: Boolean = false) {
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasWrite) {
            viewModel.setWritePermissionGranted(true)
            if (vipOnly) {
                viewModel.assignVipOnlyRinging(contactId, confirmed = true)
            } else {
                viewModel.assignToContact(contactId, confirmed = true)
            }
        } else {
            pendingWriteAction = PendingWriteAction(contactId, vipOnly)
            writePermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    fun assignWithPolicy(contactId: Long, vipOnly: Boolean = false) {
        val sound = state.selectedSound ?: return
        val capability = sound.soundLicenseCapabilities().capability(SoundAction.APPLY)
        when (capability.decision) {
            SoundActionDecision.ALLOWED -> requestWriteOrAssign(contactId, vipOnly)
            SoundActionDecision.CONFIRMATION_REQUIRED -> {
                pendingContactAction = PendingContactAction(contactId, capability.reason, vipOnly)
            }
            SoundActionDecision.DISABLED -> Unit
        }
    }

    fun openDndSettings() {
        val action = if (state.dndGuidance == ContactDndGuidance.POLICY_ACCESS_REQUIRED) {
            Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        } else {
            Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS
        }
        if (context.tryStartActivity(Intent(action), "DND settings")) {
            viewModel.clearDndGuidance()
        } else {
            scope.launch { snackbarHostState.showSnackbar(dndSettingsUnavailableMessage) }
        }
    }

    fun openContactForPriority() {
        val contact = state.selectedContact ?: return
        if (context.tryStartActivity(Intent(Intent.ACTION_EDIT, contactEditorUri(contact)), "contact editor")) {
            viewModel.clearDndGuidance()
        } else {
            scope.launch { snackbarHostState.showSnackbar(dndContactUnavailableMessage) }
        }
    }

    pendingContactAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingContactAction = null },
            title = { Text(stringResource(R.string.contact_picker_apply_sound_title)) },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingContactAction = null
                        requestWriteOrAssign(pending.contactId, pending.vipOnly)
                    },
                ) {
                    Text(stringResource(R.string.common_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingContactAction = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    val dndGuidance = state.dndGuidance
    val dndContact = state.selectedContact
    if (dndGuidance != null && dndContact != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearDndGuidance,
            title = { Text(stringResource(R.string.contact_picker_dnd_title)) },
            text = {
                Text(
                    when (dndGuidance) {
                        ContactDndGuidance.POLICY_ACCESS_REQUIRED -> stringResource(R.string.contact_picker_dnd_policy_access_body)
                        ContactDndGuidance.CALLS_BLOCKED -> stringResource(R.string.contact_picker_dnd_calls_blocked_body)
                        ContactDndGuidance.PRIORITY_CALLS_DISABLED -> stringResource(R.string.contact_picker_dnd_priority_calls_disabled_body)
                        ContactDndGuidance.CONTACT_MUST_BE_STARRED -> stringResource(R.string.contact_picker_dnd_contact_not_starred_body)
                        ContactDndGuidance.NONE -> ""
                    },
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (dndGuidance == ContactDndGuidance.POLICY_ACCESS_REQUIRED) {
                        TextButton(onClick = ::openDndSettings) {
                            Text(stringResource(R.string.contact_picker_dnd_grant_access))
                        }
                    } else if (dndGuidance == ContactDndGuidance.CALLS_BLOCKED ||
                        dndGuidance == ContactDndGuidance.PRIORITY_CALLS_DISABLED
                    ) {
                        TextButton(onClick = ::openDndSettings) {
                            Text(stringResource(R.string.contact_picker_dnd_open_settings))
                        }
                    }
                    if (!dndContact.isStarred) {
                        TextButton(onClick = ::openContactForPriority) {
                            Text(stringResource(R.string.contact_picker_dnd_mark_priority))
                        }
                    }
                    TextButton(
                        onClick = {
                            val vipOnly = state.pendingVipOnly
                            viewModel.clearDndGuidance()
                            if (vipOnly) {
                                viewModel.assignVipOnlyRinging(dndContact.id, confirmed = true, ignoreDnd = true)
                            } else {
                                viewModel.assignToContact(dndContact.id, confirmed = true, ignoreDnd = true)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.contact_picker_dnd_assign_anyway))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearDndGuidance) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contact_picker_assign_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (soundResolved) {
                null -> {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.contact_picker_opening), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    return@Scaffold
                }
                false -> {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        AuraStateCard(
                            icon = Icons.Default.MusicOff,
                            title = stringResource(R.string.contact_picker_sound_unavailable_title),
                            description = stringResource(R.string.contact_picker_sound_unavailable_body),
                            tone = MaterialTheme.colorScheme.tertiary,
                            primaryAction = AuraStateAction(stringResource(R.string.contact_picker_back_to_sounds), Icons.AutoMirrored.Filled.ArrowBack, onBack),
                        )
                    }
                    return@Scaffold
                }
                true -> Unit
            }

            state.selectedSound?.let { sound ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = AuraShapes.Medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = AuraShapes.Medium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp).size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sound.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.contact_picker_ready_to_apply),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.isLoading) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.contact_picker_reading_contact), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (state.selectedContact == null) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    AuraStateCard(
                        icon = Icons.Default.Contacts,
                        title = stringResource(R.string.contact_picker_pick_title),
                        description = stringResource(R.string.contact_picker_pick_body),
                        tone = MaterialTheme.colorScheme.primary,
                        primaryAction = AuraStateAction(stringResource(R.string.contact_picker_pick_action), Icons.Default.PersonSearch, ::launchSystemContactPicker),
                        secondaryAction = AuraStateAction(stringResource(R.string.common_back), Icons.AutoMirrored.Filled.ArrowBack, onBack),
                    )
                }
            } else {
                val contact = state.selectedContact ?: return@Scaffold
                val canApplySelectedSound = state.selectedSound
                    ?.soundLicenseCapabilities()
                    ?.canUse(SoundAction.APPLY)
                    ?: false
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ContactAssignmentCard(
                        contact = contact,
                        enabled = !state.isApplying && canApplySelectedSound,
                        isApplying = state.applyingContactId == contact.id,
                        writePermissionGranted = state.hasWritePermission,
                        permissionPermanentlyDenied = permissionPermanentlyDenied,
                        onChangeContact = ::launchSystemContactPicker,
                        onOpenSettings = {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            if (!context.tryStartActivity(intent, "app settings")) {
                                scope.launch { snackbarHostState.showSnackbar(appSettingsUnavailableMessage) }
                            }
                        },
                        onApply = { assignWithPolicy(contact.id) },
                        onVipOnlyRinging = { assignWithPolicy(contact.id, vipOnly = true) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactAssignmentCard(
    contact: ContactInfo,
    enabled: Boolean,
    isApplying: Boolean,
    writePermissionGranted: Boolean,
    permissionPermanentlyDenied: Boolean,
    onChangeContact: () -> Unit,
    onOpenSettings: () -> Unit,
    onApply: () -> Unit,
    onVipOnlyRinging: () -> Unit,
) {
    Surface(
        shape = AuraShapes.Medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = AuraShapes.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            contact.name.take(1).uppercase(Locale.ROOT).ifBlank { "?" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (contact.currentRingtoneUri != null) {
                            stringResource(R.string.contact_picker_custom_ringtone_set)
                        } else {
                            stringResource(R.string.contact_picker_selected_from_picker)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!writePermissionGranted) {
                Text(
                    text = if (permissionPermanentlyDenied) {
                        stringResource(R.string.contact_picker_permission_off)
                    } else {
                        stringResource(R.string.contact_picker_permission_prompt)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onChangeContact,
                    enabled = !isApplying,
                    shape = AuraShapes.Medium,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_change))
                }
                OutlinedButton(
                    onClick = onVipOnlyRinging,
                    enabled = enabled && !isApplying,
                    shape = AuraShapes.Medium,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.contact_picker_vip_preset))
                }
                if (permissionPermanentlyDenied && !writePermissionGranted) {
                    Button(
                        onClick = onOpenSettings,
                        enabled = !isApplying,
                        shape = AuraShapes.Medium,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.common_settings))
                    }
                } else {
                    Button(
                        onClick = onApply,
                        enabled = enabled,
                        shape = AuraShapes.Medium,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        if (isApplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isApplying) {
                                stringResource(R.string.common_applying)
                            } else {
                                stringResource(R.string.common_apply)
                            }
                        )
                    }
                }
            }
        }
    }
}
