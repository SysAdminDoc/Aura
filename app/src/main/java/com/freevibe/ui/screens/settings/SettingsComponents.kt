package com.freevibe.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.ui.theme.AuraShapes

/**
 * Shared UI primitives for Settings sections.
 *
 * Extracted from the monolithic SettingsScreen.kt to support feature-owned
 * section composables in the same package.
 */

@Composable
internal fun SettingsSection(
    sectionKey: String = "unscoped",
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val query = LocalSettingsSearchQuery.current
    val sectionMatches = settingsSectionMatchesQuery(query, "$title $description")
    CompositionLocalProvider(
        LocalSettingsSearchSectionKey provides sectionKey,
        LocalSettingsSearchSectionTitle provides title,
        LocalSettingsSearchSectionMatches provides sectionMatches,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp), content = content)
        }
    }
}

@Composable
internal fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    searchAliases: Set<String> = emptySet(),
    subtitleMaxLines: Int = 2,
) {
    val searchRow = rememberSettingsSearchRow(title, subtitle, searchAliases)
    if (searchRow == null) return
    val itemDescription = stringResource(R.string.a11y_title_subtitle, title, subtitle)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .then(searchRow.modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = itemDescription
                onClick(label = title, action = null)
            },
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        shape = AuraShapes.None,
        shadowElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    modifier = Modifier.size(20.dp),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 40.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            )
        }
    }
}

@Composable
internal fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    searchAliases: Set<String> = emptySet(),
) {
    val searchRow = rememberSettingsSearchRow(title, subtitle, searchAliases)
    if (searchRow == null) return
    val toggleStateDescription = stringResource(if (checked) R.string.a11y_on else R.string.a11y_off)
    val toggleActionLabel = stringResource(
        if (checked) R.string.a11y_turn_off else R.string.a11y_turn_on,
        title,
    )
    val toggleDescription = stringResource(R.string.a11y_title_subtitle, title, subtitle)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .then(searchRow.modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = toggleDescription
                stateDescription = toggleStateDescription
                onClick(label = toggleActionLabel, action = null)
            },
        onClick = { onCheckedChange(!checked) },
        color = Color.Transparent,
        shape = AuraShapes.None,
        shadowElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 40.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            )
        }
    }
}

@Composable
internal fun SettingsValueSlider(
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    searchAliases: Set<String> = emptySet(),
) {
    val searchRow = rememberSettingsSearchRow(title, subtitle, searchAliases)
    if (searchRow == null) return
    val description = stringResource(R.string.a11y_title_subtitle, title, "$subtitle. $valueLabel")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .then(searchRow.modifier)
            .semantics(mergeDescendants = false) {
                contentDescription = description
            },
        color = Color.Transparent,
        shape = AuraShapes.None,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        valueLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    steps = steps,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun SettingsRadioOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    searchAliases: Set<String> = emptySet(),
) {
    val searchRow = rememberSettingsSearchRow(label, "", searchAliases)
    if (searchRow == null) return
    Row(
        modifier = modifier
            .then(searchRow.modifier)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
internal fun SettingsMetric(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val metricDescription = stringResource(R.string.a11y_label_value, label, value)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = metricDescription
        },
        color = tint.copy(alpha = 0.12f),
        shape = AuraShapes.Medium,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = AuraShapes.Medium,
                color = tint.copy(alpha = 0.14f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(16.dp),
                )
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Locale-independent data scope of a disclosed permission. */
internal enum class PermissionScope { LOCAL, REMOTE }

@Composable
internal fun PermissionTransparencyRow(
    icon: ImageVector,
    permission: String,
    scope: PermissionScope,
    description: String,
    granted: Boolean? = null,
) {
    val isLocal = scope == PermissionScope.LOCAL
    val scopeLabel = stringResource(
        if (isLocal) R.string.settings_perm_scope_local else R.string.settings_perm_scope_remote,
    )
    val grantedLabel = when (granted) {
        null -> null
        true -> stringResource(R.string.settings_perm_granted)
        false -> stringResource(R.string.settings_perm_not_granted)
    }
    val rowDescription = if (grantedLabel != null) {
        stringResource(R.string.settings_perm_a11y_row_granted, permission, scopeLabel, grantedLabel, description)
    } else {
        stringResource(R.string.settings_perm_a11y_row, permission, scopeLabel, description)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            },
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.74f),
        shape = AuraShapes.Medium,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        ),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = AuraShapes.Medium,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
            ) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(permission, style = MaterialTheme.typography.titleMedium)
                    Surface(
                        shape = AuraShapes.Small,
                        color = if (isLocal) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        },
                    ) {
                        Text(
                            text = scopeLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLocal) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    if (granted != null && grantedLabel != null) {
                        Surface(
                            shape = AuraShapes.Small,
                            color = if (granted) {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                            },
                        ) {
                            Text(
                                text = grantedLabel,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (granted) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Format a rotation interval in minutes to a localized, readable label. */
@Composable
internal fun formatInterval(minutes: Long): String = when {
    minutes < 60L -> {
        val count = minutes.toInt()
        pluralStringResource(R.plurals.settings_interval_minutes, count, count)
    }
    minutes < 1440L -> {
        val hours = (minutes / 60L).toInt()
        pluralStringResource(R.plurals.settings_interval_hours, hours, hours)
    }
    else -> {
        val days = (minutes / 1440L).toInt()
        pluralStringResource(R.plurals.settings_interval_days, days, days)
    }
}
