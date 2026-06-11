package com.freevibe.ui.screens.sounds

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.toSound
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.service.BundledContentProvider
import com.freevibe.service.ContactInfo
import com.freevibe.service.ContactRingtoneService
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundUrlResolver
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

data class ContactPickerState(
    val selectedContact: ContactInfo? = null,
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
)

@HiltViewModel
class ContactPickerViewModel @Inject constructor(
    private val contactService: ContactRingtoneService,
    private val soundApplier: SoundApplier,
    private val favoritesRepo: FavoritesRepository,
    private val bundledContent: BundledContentProvider,
    private val soundUrlResolver: SoundUrlResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactPickerState())
    val state = _state.asStateFlow()

    fun setWritePermissionGranted(granted: Boolean) {
        _state.update { it.copy(hasWritePermission = granted) }
    }

    fun loadSelectedContact(contactUri: Uri) {
        _state.update { it.copy(isLoading = true, error = null, selectedContact = null) }
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

    fun assignToContact(contactId: Long, confirmed: Boolean = false) {
        val sound = _state.value.selectedSound ?: run {
            _state.update { it.copy(error = "No sound selected. Return to Sounds and choose a valid item.") }
            return
        }
        soundActionGateMessage(sound, confirmed)?.let { message ->
            _state.update { it.copy(error = message) }
            return
        }
        _state.update { it.copy(isApplying = true, applyingContactId = contactId, error = null, success = null) }
        viewModelScope.launch {
            val dlUrl = soundUrlResolver.resolve(sound)
            if (dlUrl.isNullOrBlank()) {
                _state.update { it.copy(isApplying = false, applyingContactId = null, error = "This sound does not have a downloadable ringtone file.") }
                return@launch
            }
            soundApplier.downloadOnly(dlUrl, sound.name, ContentType.RINGTONE)
                .onSuccess { uri ->
                    contactService.setContactRingtone(contactId, uri)
                        .onSuccess {
                            _state.update { it.copy(isApplying = false, applyingContactId = null, success = "Ringtone set for contact") }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(isApplying = false, applyingContactId = null, error = e.message) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(isApplying = false, applyingContactId = null, error = "Download failed: ${e.message}") }
                }
        }
    }

    fun clearMessages() = _state.update { it.copy(success = null, error = null) }

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
    var pendingWriteContactId by remember(soundIdentityKey) { mutableStateOf<Long?>(null) }

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
        val pendingContactId = pendingWriteContactId
        pendingWriteContactId = null
        if (granted && pendingContactId != null) {
            viewModel.assignToContact(pendingContactId, confirmed = true)
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
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar("Error: $it"); viewModel.clearMessages() }
    }

    fun launchSystemContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        try {
            contactPickerLauncher.launch(intent)
        } catch (_: Exception) {
            scope.launch { snackbarHostState.showSnackbar("No contact picker is available on this device.") }
        }
    }

    fun requestWriteOrAssign(contactId: Long) {
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasWrite) {
            viewModel.setWritePermissionGranted(true)
            viewModel.assignToContact(contactId, confirmed = true)
        } else {
            pendingWriteContactId = contactId
            writePermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    fun assignWithPolicy(contactId: Long) {
        val sound = state.selectedSound ?: return
        val capability = sound.soundLicenseCapabilities().capability(SoundAction.APPLY)
        when (capability.decision) {
            SoundActionDecision.ALLOWED -> requestWriteOrAssign(contactId)
            SoundActionDecision.CONFIRMATION_REQUIRED -> {
                pendingContactAction = PendingContactAction(contactId, capability.reason)
            }
            SoundActionDecision.DISABLED -> Unit
        }
    }

    pendingContactAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingContactAction = null },
            title = { Text("Apply sound") },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingContactAction = null
                        requestWriteOrAssign(pending.contactId)
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingContactAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Assign to Contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(Modifier.height(12.dp))
                            Text("Opening contact picker...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    return@Scaffold
                }
                false -> {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        AuraStateCard(
                            icon = Icons.Default.MusicOff,
                            title = "Sound unavailable",
                            description = "The selected sound could not be restored. Return to Sounds and choose another item.",
                            tone = MaterialTheme.colorScheme.tertiary,
                            primaryAction = AuraStateAction("Back to sounds", Icons.AutoMirrored.Filled.ArrowBack, onBack),
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
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
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
                                "Assign as this contact's ringtone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Reading selected contact...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (state.selectedContact == null) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    AuraStateCard(
                        icon = Icons.Default.Contacts,
                        title = "Pick a contact",
                        description = "Use Android's contact picker so Aura only receives the person you choose. Contact updates are requested later if you apply the ringtone.",
                        tone = MaterialTheme.colorScheme.primary,
                        primaryAction = AuraStateAction("Pick contact", Icons.Default.PersonSearch, ::launchSystemContactPicker),
                        secondaryAction = AuraStateAction("Back", Icons.AutoMirrored.Filled.ArrowBack, onBack),
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
                        .fillMaxWidth()
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
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        onApply = { assignWithPolicy(contact.id) },
                    )
                }
            }
        }
    }
}

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
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
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
                    shape = RoundedCornerShape(12.dp),
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
                        if (contact.currentRingtoneUri != null) "Custom ringtone set" else "Selected from Android contact picker",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!writePermissionGranted) {
                Text(
                    text = if (permissionPermanentlyDenied) {
                        "Contact update permission is off. Open Android settings to allow Aura to write the selected ringtone to this contact."
                    } else {
                        "Aura will ask for contact update permission only when you apply this ringtone."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onChangeContact, enabled = !isApplying) {
                    Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Change")
                }
                if (permissionPermanentlyDenied && !writePermissionGranted) {
                    Button(onClick = onOpenSettings, enabled = !isApplying) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Settings")
                    }
                } else {
                    Button(onClick = onApply, enabled = enabled) {
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
                        Text(if (isApplying) "Applying" else "Apply")
                    }
                }
            }
        }
    }
}
