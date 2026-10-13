package com.freevibe.ui.screens.reddit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.RedditFeedMediaType
import com.freevibe.data.model.RedditFeedPreset
import com.freevibe.data.model.RedditFeedPresetStore
import com.freevibe.data.model.activate
import com.freevibe.data.model.bindRotation
import com.freevibe.data.model.delete
import com.freevibe.data.model.duplicate
import com.freevibe.data.model.move
import com.freevibe.data.model.upsert
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Reads and edits the named Reddit feeds that the Wallpapers and Video tabs and rotation load. */
@HiltViewModel
class RedditFeedPresetsViewModel @Inject constructor(
    private val prefs: PreferencesManager,
) : ViewModel() {

    /** Null until the first read, so a screen never flashes the wrong feed name. */
    val store: StateFlow<RedditFeedPresetStore?> =
        prefs.redditFeedPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val showAdultContent: StateFlow<Boolean> =
        prefs.showNsfwContent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val redditEnabled: StateFlow<Boolean> =
        prefs.redditProviderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Makes [id] the feed its tab loads, then runs [onSaved] so the tab can reload from it. */
    fun activate(id: String, onSaved: () -> Unit = {}) = edit(onSaved) { it.activate(id) }

    fun save(preset: RedditFeedPreset) = edit { it.upsert(preset) }

    fun duplicate(id: String, copyName: (String) -> String) = edit { it.duplicate(id, newRedditFeedPresetId(), copyName) }

    fun move(id: String, offset: Int) = edit { it.move(id, offset) }

    fun delete(id: String) = edit { it.delete(id) }

    /** Blank [id] lets rotation follow the feed active on the Reddit tab. */
    fun bindRotation(id: String) = edit { it.bindRotation(id) }

    private fun edit(onSaved: () -> Unit = {}, transform: (RedditFeedPresetStore) -> RedditFeedPresetStore) {
        viewModelScope.launch {
            prefs.updateRedditFeedPresets(transform)
            onSaved()
        }
    }
}

internal fun newRedditFeedPresetId(): String = "p_" + UUID.randomUUID().toString().replace("-", "").take(16)

/** A blank preset of [mediaType] for the editor to fill in. */
internal fun newRedditFeedPreset(mediaType: RedditFeedMediaType) = RedditFeedPreset(
    id = newRedditFeedPresetId(),
    name = "",
    mediaType = mediaType,
    subreddits = emptyList(),
    sort = if (mediaType == RedditFeedMediaType.VIDEO) "new" else "top",
)
