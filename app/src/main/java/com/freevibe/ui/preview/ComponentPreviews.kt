package com.freevibe.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.screens.settings.SettingsItem
import com.freevibe.ui.screens.settings.SettingsMetric
import com.freevibe.ui.screens.settings.SettingsSection
import com.freevibe.ui.screens.settings.SettingsToggle
import com.freevibe.ui.theme.FreeVibeTheme

@Preview(name = "AuraStateCard — Empty", showBackground = true)
@Composable
private fun AuraStateCardEmptyPreview() {
    FreeVibeTheme(darkTheme = true) {
        AuraStateCard(
            icon = Icons.Default.Favorite,
            title = "No favorite wallpapers yet",
            description = "Browse wallpapers and tap the heart to save them here.",
            primaryAction = AuraStateAction(label = "Browse wallpapers", icon = Icons.Default.Favorite, onClick = {}),
        )
    }
}

@Preview(name = "AuraStateCard — Error", showBackground = true)
@Composable
private fun AuraStateCardErrorPreview() {
    FreeVibeTheme(darkTheme = true) {
        AuraStateCard(
            icon = Icons.Default.WifiOff,
            title = "Couldn't load wallpapers",
            description = "Check your internet connection and try again.",
            tone = MaterialTheme.colorScheme.error,
            primaryAction = AuraStateAction(label = "Retry", icon = Icons.Default.ErrorOutline, onClick = {}),
        )
    }
}

@Preview(name = "AuraStateCard — Light", showBackground = true)
@Composable
private fun AuraStateCardLightPreview() {
    FreeVibeTheme(darkTheme = false) {
        AuraStateCard(
            icon = Icons.Default.ErrorOutline,
            title = "Source unavailable",
            description = "This provider is temporarily offline.",
        )
    }
}

@Preview(name = "Settings Section — Dark", showBackground = true)
@Composable
private fun SettingsSectionPreview() {
    FreeVibeTheme(darkTheme = true) {
        SettingsSection(
            title = "Wallpaper Scheduler",
            description = "Automate rotation across sources, collections, and screen targets.",
        ) {
            SettingsToggle(
                icon = Icons.Default.Schedule,
                title = "Auto-rotate wallpapers",
                subtitle = "Every 30 minutes",
                checked = true,
                onCheckedChange = {},
            )
            SettingsItem(
                icon = Icons.Default.FolderOpen,
                title = "Local rotation folder",
                subtitle = "Pictures/Wallpapers",
                onClick = {},
            )
            SettingsItem(
                icon = Icons.Default.BatteryAlert,
                title = "Samsung battery optimization",
                subtitle = "Open Settings > Battery > Background usage limits and remove Aura.",
                onClick = {},
            )
        }
    }
}

@Preview(name = "Settings Section — Light", showBackground = true)
@Composable
private fun SettingsSectionLightPreview() {
    FreeVibeTheme(darkTheme = false) {
        SettingsSection(
            title = "Library Backup",
            description = "Keep favorites recoverable without creating an account.",
        ) {
            SettingsToggle(
                icon = Icons.Default.FolderOpen,
                title = "Scheduled favorites backup",
                subtitle = "Every 7 days · Keep 5",
                checked = true,
                onCheckedChange = {},
            )
        }
    }
}

@Preview(name = "Settings Metrics — Dark", showBackground = true)
@Composable
private fun SettingsMetricPreview() {
    FreeVibeTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            SettingsMetric(
                label = "Favorites",
                value = "142",
                icon = Icons.Default.Favorite,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
