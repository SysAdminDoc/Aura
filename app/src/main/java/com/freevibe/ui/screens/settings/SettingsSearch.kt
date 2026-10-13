package com.freevibe.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.ui.theme.AuraShapes
import kotlinx.coroutines.delay
import java.util.Locale

/** One searchable settings section: stable key plus localized header resources. */
internal data class SettingsSearchSection(
    val key: String,
    val titleRes: Int,
    val descriptionRes: Int,
)

/** A rendered setting row registered by the shared SettingsItem/SettingsToggle primitives. */
internal data class SettingsSearchRow(
    val key: String,
    val sectionKey: String,
    val sectionTitle: String,
    val title: String,
    val subtitle: String,
    val aliases: Set<String> = emptySet(),
)

internal class SettingsSearchRegistry {
    val rows = mutableStateMapOf<String, SettingsSearchRow>()
    var focusedRowKey by mutableStateOf<String?>(null)

    fun keyFor(sectionKey: String, title: String): String =
        "$sectionKey:${title.trim().lowercase(Locale.ROOT)}"

    fun register(row: SettingsSearchRow) {
        rows[row.key] = row
    }

    fun focusRow(key: String) {
        focusedRowKey = key
    }

    fun clearFocus(key: String) {
        if (focusedRowKey == key) focusedRowKey = null
    }
}

internal val LocalSettingsSearchRegistry = staticCompositionLocalOf<SettingsSearchRegistry?> { null }
internal val LocalSettingsSearchQuery = compositionLocalOf { "" }
internal val LocalSettingsSearchSectionKey = compositionLocalOf { "unscoped" }
internal val LocalSettingsSearchSectionTitle = compositionLocalOf { "" }
internal val LocalSettingsSearchSectionMatches = compositionLocalOf { true }

internal data class SettingsSearchRowBinding(
    val key: String,
    val modifier: Modifier,
)

/** Registers a row, filters it when a non-header query is active, and scrolls to selections. */
@Composable
internal fun rememberSettingsSearchRow(
    title: String,
    subtitle: String,
    aliases: Set<String> = emptySet(),
): SettingsSearchRowBinding? {
    val registry = LocalSettingsSearchRegistry.current
    val query = LocalSettingsSearchQuery.current
    val sectionKey = LocalSettingsSearchSectionKey.current
    val sectionTitle = LocalSettingsSearchSectionTitle.current
    val sectionMatches = LocalSettingsSearchSectionMatches.current
    val rowKey = registry?.keyFor(sectionKey, title)
        ?: "$sectionKey:${title.trim()}"
    val requester = remember(rowKey) { BringIntoViewRequester() }
    val row = remember(rowKey, sectionTitle, title, subtitle, aliases) {
        SettingsSearchRow(
            key = rowKey,
            sectionKey = sectionKey,
            sectionTitle = sectionTitle,
            title = title,
            subtitle = subtitle,
            aliases = aliases,
        )
    }

    if (registry != null) {
        SideEffect { registry.register(row) }
        LaunchedEffect(registry.focusedRowKey, rowKey) {
            if (registry.focusedRowKey == rowKey) {
                withFrameNanos { }
                requester.bringIntoView()
                delay(1200)
                registry.clearFocus(rowKey)
            }
        }
    }

    val rowMatches = settingsSearchRowMatchesQuery(query, row)
    if (query.trim().isNotEmpty() && !sectionMatches && !rowMatches) return null

    val highlight = registry?.focusedRowKey == rowKey
    return SettingsSearchRowBinding(
        key = rowKey,
        modifier = Modifier
            .bringIntoViewRequester(requester)
            .then(
                if (highlight) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        AuraShapes.Medium,
                    )
                } else {
                    Modifier
                },
            ),
    )
}

internal object SettingsSectionKeys {
    const val WALLPAPERS = "wallpapers"
    const val SCHEDULER = "scheduler"
    const val BACKUP = "backup"
    const val SMART = "smart"
    const val SOUNDS = "sounds"
    const val VIDEO = "video"
    const val SERVICES = "services"
    const val STORAGE = "storage"
    const val DIAGNOSTICS = "diagnostics"
    const val PERMISSIONS = "permissions"
    const val ABOUT = "about"
}

/** Search index in on-screen order; each entry maps a rendered section to its header copy. */
internal val SETTINGS_SEARCH_SECTIONS: List<SettingsSearchSection> = listOf(
    SettingsSearchSection(SettingsSectionKeys.WALLPAPERS, R.string.settings_wallpapers_section_title, R.string.settings_wallpapers_section_description),
    SettingsSearchSection(SettingsSectionKeys.SCHEDULER, R.string.settings_scheduler_section_title, R.string.settings_scheduler_section_description),
    SettingsSearchSection(SettingsSectionKeys.BACKUP, R.string.settings_backup_section_title, R.string.settings_backup_section_description),
    SettingsSearchSection(SettingsSectionKeys.SMART, R.string.settings_smart_section_title, R.string.settings_smart_section_description),
    SettingsSearchSection(SettingsSectionKeys.SOUNDS, R.string.settings_sounds_section_title, R.string.settings_sounds_section_description),
    SettingsSearchSection(SettingsSectionKeys.VIDEO, R.string.settings_video_section_title, R.string.settings_video_section_description),
    SettingsSearchSection(SettingsSectionKeys.SERVICES, R.string.settings_services_section_title, R.string.settings_services_section_description),
    SettingsSearchSection(SettingsSectionKeys.STORAGE, R.string.settings_storage_section_title, R.string.settings_storage_section_description),
    SettingsSearchSection(SettingsSectionKeys.DIAGNOSTICS, R.string.settings_diagnostics_section_title, R.string.settings_diagnostics_section_description),
    SettingsSearchSection(SettingsSectionKeys.PERMISSIONS, R.string.settings_permissions_section_title, R.string.settings_permissions_section_description),
    SettingsSearchSection(SettingsSectionKeys.ABOUT, R.string.settings_about_section_title, R.string.settings_about_section_description),
)

internal fun settingsSectionMatchesQuery(query: String, haystack: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return haystack.contains(trimmed, ignoreCase = true)
}

internal fun settingsSearchRowMatchesQuery(query: String, row: SettingsSearchRow): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return sequenceOf(row.title, row.subtitle, row.sectionTitle)
        .plus(row.aliases.asSequence())
        .any { it.contains(trimmed, ignoreCase = true) }
}

/** Keys of matching sections, including sections reached through a matching row anchor. */
internal fun visibleSettingsSectionKeys(
    context: Context,
    query: String,
    rows: Collection<SettingsSearchRow> = emptyList(),
): Set<String> {
    val visible = SETTINGS_SEARCH_SECTIONS.filter { section ->
        settingsSectionMatchesQuery(
            query,
            context.getString(section.titleRes) + " " + context.getString(section.descriptionRes),
        )
    }.mapTo(mutableSetOf()) { it.key }
    rows.filter { settingsSearchRowMatchesQuery(query, it) }
        .mapTo(visible) { it.sectionKey }
    return visible
}

/** Search field, matching row anchors, and the no-matches message. */
@Composable
internal fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SettingsSearchRow>,
    resultsEmpty: Boolean,
    onRowSelected: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        label = { Text(stringResource(R.string.settings_search_hint)) },
        singleLine = true,
    )
    if (query.trim().isNotEmpty() && results.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_search_matches_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            results.take(8).forEach { row ->
                Surface(
                    onClick = { onRowSelected(row.key) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    shape = AuraShapes.Medium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.sectionTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (resultsEmpty) {
        Text(
            text = stringResource(R.string.settings_search_no_results, query.trim()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        )
    }
}

@Composable
internal fun SettingsSearchScope(
    context: Context,
    registry: SettingsSearchRegistry,
    query: String,
    onQueryChange: (String) -> Unit,
    content: @Composable (Set<String>) -> Unit,
) {
    val searchRows = registry.rows.values.toList()
    val matchingSearchRows = searchRows.filter { settingsSearchRowMatchesQuery(query, it) }
    val visibleSectionKeys = visibleSettingsSectionKeys(context, query, searchRows)
    SettingsSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        results = matchingSearchRows,
        resultsEmpty = query.trim().isNotEmpty() && matchingSearchRows.isEmpty(),
        onRowSelected = registry::focusRow,
    )
    CompositionLocalProvider(
        LocalSettingsSearchRegistry provides registry,
        LocalSettingsSearchQuery provides query,
    ) {
        content(visibleSectionKeys)
    }
}
