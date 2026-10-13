package com.freevibe.ui.components

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.service.DownloadProgress
import com.freevibe.ui.theme.AuraShapes
import com.freevibe.ui.theme.GLASS_CARD_ALPHA
import com.freevibe.ui.theme.sourceBadgeTone

private val AuraCardShape = AuraShapes.Medium
private val AuraControlShape = AuraShapes.Medium
private val AuraIconTileShape = AuraShapes.Medium
private val AuraMinimumTouchTarget = 48.dp

/**
 * Pagination footer shown while the next page loads. Unlike a bare spinner it carries a
 * visible "Loading more…" label and a polite live region, so TalkBack announces progress and
 * sighted users can tell a fetch in progress from a hung list.
 */
@Composable
fun LoadMoreIndicator(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.load_more_indicator)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = label
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(20.dp)
                .clearAndSetSemantics {},
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Feedback Chrome ────────────────────────────────────────────────

@Composable
fun AuraSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = AuraCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Download Progress Overlay ─────────────────────────────────────

@Composable
fun DownloadProgressBar(
    downloads: Map<String, DownloadProgress>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (downloads.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        downloads.forEach { (id, download) ->
            DownloadItem(download = download, onDismiss = { onDismiss(id) })
        }
    }
}

@Composable
private fun DownloadItem(
    download: DownloadProgress,
    onDismiss: () -> Unit,
) {
    val dismissDownloadLabel = stringResource(R.string.a11y_dismiss_download, download.fileName)
    val downloadStateDescription = when {
        download.isComplete -> stringResource(R.string.a11y_download_complete)
        download.error != null -> stringResource(R.string.a11y_download_failed)
        else -> stringResource(R.string.a11y_download_percent, (download.progress * 100).toInt())
    }
    Surface(
        modifier = Modifier.semantics {
            contentDescription = download.fileName
            stateDescription = downloadStateDescription
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = AuraCardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = when {
                        download.isComplete -> Icons.Default.CheckCircle
                        download.error != null -> Icons.Default.Error
                        else -> Icons.Default.Download
                    },
                    contentDescription = null,
                    tint = when {
                        download.isComplete -> MaterialTheme.colorScheme.secondary
                        download.error != null -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        download.fileName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (download.error != null) {
                        Text(
                            download.error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (!download.isComplete) {
                        val pct = (download.progress * 100).toInt()
                        val sizeText = if (download.totalBytes > 0) {
                            "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)} ($pct%)"
                        } else {
                            "${formatBytes(download.downloadedBytes)} ($pct%)"
                        }
                        Text(
                            sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (download.isComplete || download.error != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(AuraMinimumTouchTarget)
                            .semantics { onClick(label = dismissDownloadLabel, action = null) },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (!download.isComplete && download.error == null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(AuraShapes.Hairline)
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(download.progress, 0f..1f)
                        },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

// ── Shimmer Loading Effect ────────────────────────────────────────

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = AuraCardShape,
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
    )

    val animationsEnabled = rememberSystemAnimationsEnabled()
    val brush = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmer_translate",
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 200f, translateAnim - 200f),
            end = Offset(translateAnim, translateAnim),
        )
    } else {
        SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
    )
}

@Composable
fun ShimmerWallpaperGrid(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (it % 2 == 0) 220.dp else 180.dp),
                    shape = AuraCardShape,
                )
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (it % 2 == 0) 180.dp else 220.dp),
                    shape = AuraCardShape,
                )
            }
        }
    }
}

@Composable
fun ShimmerSoundList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(8) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerBox(
                    modifier = Modifier.size(44.dp),
                    shape = AuraIconTileShape,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShimmerBox(modifier = Modifier.width(180.dp).height(14.dp))
                    ShimmerBox(modifier = Modifier.width(100.dp).height(10.dp))
                }
            }
        }
    }
}

// ── Quiet grouped surface ─────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = AuraCardShape,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    highlightHeight: Dp = 72.dp,
    shadowElevation: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = GLASS_CARD_ALPHA),
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun HighlightPill(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .clearAndSetSemantics {
            contentDescription = label
            }
            .heightIn(min = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Default.Search,
    leadingTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClear: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = AuraControlShape,
    clearContentDescription: String? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val clearLabel = clearContentDescription ?: stringResource(R.string.common_clear)

    Surface(
        modifier = modifier
            .heightIn(min = AuraMinimumTouchTarget)
            .semantics { contentDescription = placeholder },
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (isFocused) 0.92f else 0.72f),
        shape = shape,
        border = if (isFocused) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
        } else {
            null
        },
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AuraMinimumTouchTarget)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = leadingTint,
                modifier = Modifier.size(19.dp),
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (value.isNotEmpty() && onClear != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(AuraMinimumTouchTarget)
                        .semantics { onClick(label = clearLabel, action = null) },
                ) {
                    Icon(Icons.Default.Close, contentDescription = clearLabel, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun AuraScreenHeader(
    label: String,
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
    ) {
        HighlightPill(
            label = label,
            icon = icon,
            tint = tint,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

// ── Source Badge ───────────────────────────────────────────────────

@Composable
fun SourceBadge(source: String, modifier: Modifier = Modifier) {
    val label = when (source.uppercase(java.util.Locale.ROOT)) {
        "WALLHAVEN" -> "Wallhaven"
        "PICSUM" -> "Legacy"
        "BING" -> "Bing"
        "WIKIMEDIA" -> "Wikimedia"
        "INTERNET_ARCHIVE" -> "Archive.org"
        "REDDIT" -> "Reddit"
        "NASA" -> "NASA"
        "FREESOUND" -> "Freesound" // Legacy favorites only
        "JAMENDO" -> "Jamendo"
        "AUDIUS" -> "Audius"
        "CCMIXTER" -> "ccMixter"
        "YOUTUBE" -> "YouTube"
        "TIKTOK" -> "TikTok"
        "PEXELS" -> "Pexels"
        "PIXABAY" -> "Pixabay"
        "KLIPY" -> "Klipy"
        "SOUNDCLOUD" -> "SoundCloud"
        "COMMUNITY" -> "Community"
        "BUNDLED" -> "Aura Originals"
        else -> source
    }
    val colorScheme = MaterialTheme.colorScheme
    val color = remember(source, colorScheme) { sourceBadgeTone(source, colorScheme) }

    Text(
        text = label,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = label
        },
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

data class AuraStateAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

data class AuraStatusAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuraStatusBanner(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.primary,
    primaryAction: AuraStatusAction? = null,
    secondaryAction: AuraStatusAction? = null,
) {
    val bannerContentDescription = stringResource(R.string.aura_status_banner_cd, title, message)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = bannerContentDescription
                liveRegion = LiveRegionMode.Polite
            },
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (primaryAction != null || secondaryAction != null) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        primaryAction?.let { action ->
                            TextButton(
                                onClick = action.onClick,
                                shape = AuraControlShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .semantics { onClick(label = action.label, action = null) },
                            ) {
                                Icon(action.icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(action.label)
                            }
                        }
                        secondaryAction?.let { action ->
                            TextButton(
                                onClick = action.onClick,
                                shape = AuraControlShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .semantics { onClick(label = action.label, action = null) },
                            ) {
                                Icon(action.icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(action.label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuraStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.primary,
    primaryAction: AuraStateAction? = null,
    secondaryAction: AuraStateAction? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.58f),
        shape = AuraCardShape,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
            if (primaryAction != null || secondaryAction != null) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    primaryAction?.let { action ->
                        Button(
                            onClick = action.onClick,
                            shape = AuraControlShape,
                            modifier = Modifier
                                .heightIn(min = AuraMinimumTouchTarget)
                                .semantics { onClick(label = action.label, action = null) },
                        ) {
                            Icon(action.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(action.label)
                        }
                    }
                    secondaryAction?.let { action ->
                        TextButton(
                            onClick = action.onClick,
                            shape = AuraControlShape,
                            modifier = Modifier
                                .heightIn(min = AuraMinimumTouchTarget)
                                .semantics { onClick(label = action.label, action = null) },
                        ) {
                            Icon(action.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(action.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.primary,
) {
    if (count <= 0) return
    val countDescription = if (count > 99) {
        stringResource(R.string.a11y_count_badge_overflow)
    } else {
        stringResource(R.string.a11y_count_badge, count)
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = countDescription
        },
        shape = AuraShapes.Compact,
        color = tone,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 1.dp,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

// ── Utilities ─────────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String {
    val root = java.util.Locale.ROOT
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(root, "%.1f KB", bytes / 1024.0)
        else -> String.format(root, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

@Composable
private fun rememberSystemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }
}
