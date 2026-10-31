package com.freevibe.ui.screens.sounds

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.freevibe.data.model.ContentSource

/**
 * Source-badge colours, one pair per provider.
 *
 * A single brand colour cannot satisfy WCAG 2.2 normal-text contrast on both a
 * white surface and Aura's near-black AMOLED surface — YouTube red reaches only
 * ~4.0:1 on white — so each source carries a light-surface tone and a
 * dark-surface tone, both verified at 4.5:1 or better by
 * `SoundSourceToneContrastTest`.
 */
internal data class SoundSourceTone(
    /** Used on light surfaces. */
    val onLight: Color,
    /** Used on dark/AMOLED surfaces. */
    val onDark: Color,
)

internal fun soundSourceTone(source: ContentSource): SoundSourceTone = when (source) {
    ContentSource.BUNDLED -> SoundSourceTone(Color(0xFF8A6100), Color(0xFFFFC94D))
    ContentSource.YOUTUBE -> SoundSourceTone(Color(0xFFC62828), Color(0xFFFF6B6B))
    ContentSource.TIKTOK -> SoundSourceTone(Color(0xFF00707A), Color(0xFF25F4EE))
    ContentSource.FREESOUND -> SoundSourceTone(Color(0xFF00697F), Color(0xFF6FD3E8))
    ContentSource.JAMENDO -> SoundSourceTone(Color(0xFF5E35B1), Color(0xFFB39DDB))
    ContentSource.WIKIMEDIA -> SoundSourceTone(Color(0xFF00639A), Color(0xFF6EC1F5))
    ContentSource.AUDIUS -> SoundSourceTone(Color(0xFF00695C), Color(0xFF4DD9C4))
    ContentSource.CCMIXTER -> SoundSourceTone(Color(0xFF8E24AA), Color(0xFFDDA0E8))
    ContentSource.SOUNDCLOUD -> SoundSourceTone(Color(0xFFBF3B00), Color(0xFFFF8A50))
    ContentSource.COMMUNITY -> SoundSourceTone(Color(0xFF2E7D32), Color(0xFF7BD182))
    else -> SoundSourceTone(Color(0xFF455A64), Color(0xFFA6BCC7))
}

/**
 * The tone to draw against the current theme's surface. Chosen from the actual
 * surface luminance rather than a theme flag, so a dynamic-colour surface still
 * picks the readable variant.
 */
@Composable
@ReadOnlyComposable
internal fun SoundSourceTone.colorForSurface(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) onDark else onLight
