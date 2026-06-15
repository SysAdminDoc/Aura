package com.freevibe.ui.screens.aigenerate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.freevibe.data.model.GENERATED_CONTENT_REPORT_REASONS
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.AiStyle
import com.freevibe.ui.components.CommunityReportDialog
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.HighlightPill
import com.freevibe.ui.components.ShimmerBox

@Composable
fun GeneratedWallpaperDisclosureDialog(
    accepted: Boolean,
    onAccept: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_disclosure_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.ai_disclosure_prompt_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.ai_disclosure_pricing_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.ai_disclosure_storage_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        shape = RoundedCornerShape(8.dp),
        confirmButton = {
            TextButton(onClick = {
                if (!accepted) onAccept()
                onDismiss()
            }) {
                Text(if (accepted) stringResource(R.string.ai_disclosure_done) else stringResource(R.string.ai_disclosure_accept))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accepted) {
                    TextButton(onClick = {
                        onReset()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.common_reset))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWallpaperScreen(
    onBack: () -> Unit,
    viewModel: AiWallpaperViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apiKey by viewModel.stabilityAiKey.collectAsStateWithLifecycle()
    val generatedContentProviderEnabled by viewModel.generatedContentProviderEnabled.collectAsStateWithLifecycle()
    val generatedContentDisclosureAccepted by viewModel.generatedContentDisclosureAccepted.collectAsStateWithLifecycle()

    // NX-13: intercept back while a generation is in flight so the user can
    // cancel a request that's burning their Stability AI credit. Without this
    // the in-flight Job kept running after the screen disappeared.
    androidx.activity.compose.BackHandler(enabled = state.isGenerating) {
        viewModel.cancelGeneration()
        onBack()
    }

    var localApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var showApiKeyField by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showTargetMenu by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }
    var generateAfterDisclosure by remember { mutableStateOf(false) }
    var showGeneratedReportDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.result?.id) {
        showGeneratedReportDialog = false
    }

    LaunchedEffect(apiKey, generatedContentProviderEnabled) {
        if (!generatedContentProviderEnabled) {
            showApiKeyField = false
            showDisclosureDialog = false
            generateAfterDisclosure = false
            showGeneratedReportDialog = false
        } else if (apiKey.isBlank()) {
            showApiKeyField = true
        }
    }
    LaunchedEffect(state.applySuccess) {
        state.applySuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Header card ──────────────────────────────────────────────
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                highlightHeight = 76.dp,
                shadowElevation = 2.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        HighlightPill(
                            label = "Stability AI",
                            icon = Icons.Default.AutoAwesome,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.ai_header_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.ai_header_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { showApiKeyField = !showApiKeyField },
                        modifier = Modifier.size(48.dp),
                        enabled = generatedContentProviderEnabled,
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = stringResource(R.string.ai_api_key_settings),
                            modifier = Modifier.size(18.dp),
                            tint = if (!generatedContentProviderEnabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else if (apiKey.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }

            // ── API key field ────────────────────────────────────────────
            AnimatedVisibility(
                visible = generatedContentProviderEnabled && showApiKeyField,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.ai_api_key_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = localApiKey,
                        onValueChange = { localApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ai_api_key_placeholder)) },
                        visualTransformation = if (apiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    if (apiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = stringResource(if (apiKeyVisible) R.string.ai_api_key_hide else R.string.ai_api_key_show),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.ai_api_key_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.saveApiKey(localApiKey)
                                showApiKeyField = false
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(stringResource(R.string.ai_api_key_save), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // ── Prompt input ─────────────────────────────────────────────
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    stringResource(R.string.ai_prompt_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = { viewModel.setPrompt(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    placeholder = {
                        Text(stringResource(R.string.ai_prompt_placeholder))
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Default,
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    maxLines = 8,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ai_prompt_counter, state.prompt.length),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }

            // ── Style picker ─────────────────────────────────────────────
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    stringResource(R.string.ai_style_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AiStyle.entries.forEach { style ->
                        FilterChip(
                            selected = state.selectedStyle == style,
                            onClick = { viewModel.setStyle(style) },
                            label = {
                                Text(style.label, style = MaterialTheme.typography.labelSmall)
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            }

            // ── Generate button ──────────────────────────────────────────
            Button(
                onClick = {
                    if (generatedContentProviderEnabled) {
                        val requestKey = localApiKey.ifBlank { apiKey }
                        if (
                            state.prompt.isNotBlank() &&
                            requestKey.isNotBlank() &&
                            !generatedContentDisclosureAccepted
                        ) {
                            generateAfterDisclosure = true
                            showDisclosureDialog = true
                        } else {
                            viewModel.generate(requestKey)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(52.dp),
                enabled = generatedContentProviderEnabled && !state.isGenerating && !state.isApplying,
            ) {
                if (!generatedContentProviderEnabled) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_generate_disabled))
                } else if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.ai_generating))
                } else {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_generate_button))
                }
            }

            Text(
                stringResource(R.string.ai_credit_note, state.sessionGenerationCount),
                modifier = Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (showDisclosureDialog) {
                GeneratedWallpaperDisclosureDialog(
                    accepted = generatedContentDisclosureAccepted,
                    onAccept = {
                        if (generateAfterDisclosure) {
                            viewModel.acceptDisclosureAndGenerate(localApiKey.ifBlank { apiKey })
                        } else {
                            viewModel.acceptGeneratedContentDisclosure()
                        }
                        generateAfterDisclosure = false
                    },
                    onReset = {
                        viewModel.resetGeneratedContentDisclosure()
                        generateAfterDisclosure = false
                    },
                    onDismiss = {
                        showDisclosureDialog = false
                        generateAfterDisclosure = false
                    },
                )
            }

            state.pendingDuplicateConfirmation?.let { duplicate ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissDuplicateGeneration() },
                    title = { Text(stringResource(R.string.ai_duplicate_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.ai_duplicate_body, duplicate.styleLabel),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(R.string.ai_duplicate_credit_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                duplicate.promptPreview,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.confirmDuplicateGeneration(localApiKey.ifBlank { apiKey })
                            },
                        ) {
                            Text(stringResource(R.string.ai_duplicate_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissDuplicateGeneration() }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            }

            // ── Generating shimmer placeholder ───────────────────────────
            if (state.isGenerating) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                )
            }

            // ── Result image + actions ───────────────────────────────────
            AnimatedVisibility(
                visible = state.result != null && !state.isGenerating,
                enter = fadeIn() + expandVertically(),
            ) {
                val wallpaper = state.result
                if (wallpaper != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SubcomposeAsyncImage(
                            model = wallpaper.thumbnailUrl,
                            contentDescription = stringResource(R.string.ai_result_cd),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        ) {
                            when (painter.state) {
                                is AsyncImagePainter.State.Loading -> {
                                    ShimmerBox(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                }
                                is AsyncImagePainter.State.Error -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.BrokenImage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                else -> SubcomposeAsyncImageContent()
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.saveToFavorites() },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !state.isSaved && !state.isApplying,
                            ) {
                                Icon(
                                    if (state.isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(if (state.isSaved) R.string.ai_saved else R.string.ai_save))
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { showTargetMenu = true },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !state.isApplying,
                                ) {
                                    if (state.isApplying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Wallpaper,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.ai_set_wallpaper))
                                    }
                                }
                                DropdownMenu(
                                    expanded = showTargetMenu,
                                    onDismissRequest = { showTargetMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ai_target_home)) },
                                        onClick = {
                                            showTargetMenu = false
                                            viewModel.applyWallpaper(WallpaperTarget.HOME)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ai_target_lock)) },
                                        onClick = {
                                            showTargetMenu = false
                                            viewModel.applyWallpaper(WallpaperTarget.LOCK)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ai_target_both)) },
                                        onClick = {
                                            showTargetMenu = false
                                            viewModel.applyWallpaper(WallpaperTarget.BOTH)
                                        },
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showGeneratedReportDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isApplying,
                        ) {
                            Icon(
                                Icons.Default.Report,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_report_button))
                        }

                        if (showGeneratedReportDialog) {
                            CommunityReportDialog(
                                title = stringResource(R.string.ai_report_title),
                                onDismiss = { showGeneratedReportDialog = false },
                                onSubmit = { reason, note ->
                                    viewModel.reportGeneratedWallpaper(wallpaper, reason, note)
                                },
                                reasons = GENERATED_CONTENT_REPORT_REASONS,
                                body = stringResource(R.string.ai_report_body),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
