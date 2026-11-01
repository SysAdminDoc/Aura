package com.freevibe.service

import android.content.Context
import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared singleton that holds the currently selected wallpaper/sound for detail screens.
 * Required because each NavBackStackEntry gets its own ViewModel instance via hiltViewModel(),
 * so list-screen and detail-screen ViewModels cannot share state directly.
 *
 * The selected items and a bounded wallpaper pager window persist across process
 * death so a warm relaunch can return to the same swipe context without retaining
 * an unbounded discovery feed.
 */
@Singleton
class SelectedContentHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val wallpaperAdapter by lazy { moshi.adapter(Wallpaper::class.java) }
    private val soundAdapter by lazy { moshi.adapter(Sound::class.java) }
    private val wallpaperListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, Wallpaper::class.java)
        moshi.adapter<List<Wallpaper>>(type)
    }
    // One writer at a time, so two quick selections can't land on disk in the wrong order.
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _selectedWallpaper = MutableStateFlow<Wallpaper?>(loadWallpaper())
    val selectedWallpaper: StateFlow<Wallpaper?> = _selectedWallpaper.asStateFlow()

    /** Wallpaper list from the source screen, for pager in detail screen */
    private val _wallpaperList = MutableStateFlow(loadWallpaperList())
    val wallpaperList: StateFlow<List<Wallpaper>> = _wallpaperList.asStateFlow()
    private val _wallpaperListAnchorKey = MutableStateFlow(
        prefs.getString(KEY_WALLPAPER_LIST_ANCHOR, null)
            ?.takeIf { _wallpaperList.value.isNotEmpty() },
    )
    val wallpaperListAnchorKey: StateFlow<String?> = _wallpaperListAnchorKey.asStateFlow()

    private val _selectedSound = MutableStateFlow<Sound?>(loadSound())
    val selectedSound: StateFlow<Sound?> = _selectedSound.asStateFlow()

    @Synchronized
    fun selectWallpaper(wallpaper: Wallpaper, wallpapers: List<Wallpaper>) {
        _selectedWallpaper.value = wallpaper
        persistWallpaper(wallpaper)
        if (wallpapers.isNotEmpty()) {
            val anchorKey = wallpaper.stableKey()
            val compactList = compactPagerWindow(wallpapers, anchorKey)
            _wallpaperList.value = compactList
            _wallpaperListAnchorKey.value = anchorKey
            persistWallpaperList(compactList, anchorKey)
        } else {
            _wallpaperList.value = emptyList()
            _wallpaperListAnchorKey.value = null
            persistWallpaperList(emptyList(), null)
        }
    }

    @Synchronized
    fun selectWallpaper(wallpaper: Wallpaper) {
        _selectedWallpaper.value = wallpaper
        persistWallpaper(wallpaper)
        _wallpaperList.value = emptyList()
        _wallpaperListAnchorKey.value = null
        persistWallpaperList(emptyList(), null)
    }

    @Synchronized
    fun updateSelectedWallpaper(wallpaper: Wallpaper) {
        _selectedWallpaper.value = wallpaper
        persistWallpaper(wallpaper)
    }

    @Synchronized
    fun selectSound(sound: Sound) {
        _selectedSound.value = sound
        persistSound(sound)
    }

    private fun loadWallpaper(): Wallpaper? = runCatching {
        prefs.getString(KEY_WALLPAPER, null)?.let { wallpaperAdapter.fromJson(it) }
    }.getOrNull()

    private fun loadSound(): Sound? = runCatching {
        prefs.getString(KEY_SOUND, null)?.let { soundAdapter.fromJson(it) }
    }.getOrNull()

    private fun loadWallpaperList(): List<Wallpaper> = runCatching {
        prefs.getString(KEY_WALLPAPER_LIST, null)
            ?.let { wallpaperListAdapter.fromJson(it) }
            .orEmpty()
            .take(MAX_PERSISTED_WALLPAPERS)
    }.getOrDefault(emptyList())

    private fun persistWallpaper(w: Wallpaper) {
        persistScope.launch {
            runCatching {
                prefs.edit().putString(KEY_WALLPAPER, wallpaperAdapter.toJson(w)).apply()
            }
        }
    }

    private fun persistSound(s: Sound) {
        persistScope.launch {
            runCatching {
                prefs.edit().putString(KEY_SOUND, soundAdapter.toJson(s)).apply()
            }
        }
    }

    private fun persistWallpaperList(wallpapers: List<Wallpaper>, anchorKey: String?) {
        persistScope.launch {
            runCatching {
                prefs.edit().apply {
                    if (wallpapers.isEmpty() || anchorKey == null) {
                        remove(KEY_WALLPAPER_LIST)
                        remove(KEY_WALLPAPER_LIST_ANCHOR)
                    } else {
                        putString(KEY_WALLPAPER_LIST, wallpaperListAdapter.toJson(wallpapers))
                        putString(KEY_WALLPAPER_LIST_ANCHOR, anchorKey)
                    }
                }.apply()
            }
        }
    }

    private fun compactPagerWindow(wallpapers: List<Wallpaper>, anchorKey: String): List<Wallpaper> {
        if (wallpapers.size <= MAX_PERSISTED_WALLPAPERS) return wallpapers
        val anchorIndex = wallpapers.indexOfFirst { it.stableKey() == anchorKey }.coerceAtLeast(0)
        val start = (anchorIndex - MAX_PERSISTED_WALLPAPERS / 2)
            .coerceIn(0, wallpapers.size - MAX_PERSISTED_WALLPAPERS)
        return wallpapers.subList(start, start + MAX_PERSISTED_WALLPAPERS)
    }

    private companion object {
        const val PREFS_NAME = "freevibe_selected_content"
        const val KEY_WALLPAPER = "selected_wallpaper_json"
        const val KEY_SOUND = "selected_sound_json"
        const val KEY_WALLPAPER_LIST = "selected_wallpaper_list_json"
        const val KEY_WALLPAPER_LIST_ANCHOR = "selected_wallpaper_list_anchor"
        const val MAX_PERSISTED_WALLPAPERS = 60
    }
}
