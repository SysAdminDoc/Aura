package com.freevibe.ui.screens.reddit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.model.RedditFeedMediaType
import com.freevibe.data.model.RedditFeedPreset
import com.freevibe.data.model.activePreset
import com.freevibe.data.model.feedSignature
import com.freevibe.data.model.presetsOf
import com.freevibe.ui.theme.AuraShapes

/**
 * Names the Reddit feed a tab is showing and switches to another. It hides itself while Reddit is
 * off. [onFeedChanged] runs once a different feed is saved, from this chip or from Settings, so the
 * tab reloads from it.
 */
@Composable
fun RedditFeedPresetChip(
    mediaType: RedditFeedMediaType,
    onFeedChanged: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RedditFeedPresetsViewModel = hiltViewModel(),
) {
    val store by viewModel.store.collectAsStateWithLifecycle()
    val redditEnabled by viewModel.redditEnabled.collectAsStateWithLifecycle()
    val current = store ?: return
    if (!redditEnabled) return
    val active = current.activePreset(mediaType)
    // The global Show NSFW setting stays out of this, so its first read doesn't count as a change.
    val signature = active.feedSignature(showAdultContent = true)
    LaunchedEffect(mediaType, signature) {
        if (viewModel.feedChanged(mediaType, signature)) onFeedChanged()
    }
    RedditFeedPresetSwitcher(
        presets = current.presetsOf(mediaType),
        active = active,
        onSelect = { preset -> viewModel.activate(preset.id) },
        modifier = modifier,
    )
}

@Composable
internal fun RedditFeedPresetSwitcher(
    presets: List<RedditFeedPreset>,
    active: RedditFeedPreset,
    onSelect: (RedditFeedPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    stringResource(R.string.reddit_preset_chip, active.name),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = { Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            shape = AuraShapes.Medium,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { preset ->
                val isActive = preset.id == active.id
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    onClick = {
                        expanded = false
                        if (!isActive) onSelect(preset)
                    },
                    leadingIcon = if (isActive) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics { selected = isActive },
                )
            }
        }
    }
}
