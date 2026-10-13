package com.freevibe.ui.screens.settings

import android.net.Uri
import android.util.Log
import com.freevibe.service.SoundShufflePoolManager
import com.freevibe.service.SoundShuffleTarget
import com.freevibe.util.rethrowIfCancelled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Named sound shuffle pools: the store the dialog renders and every pool mutation. */
internal class SettingsSoundShuffleDelegate(
    private val manager: SoundShufflePoolManager,
    private val scope: CoroutineScope,
) {
    val store get() = manager.store
    val downloads get() = manager.downloads
    val originals get() = manager.originals

    init {
        // Opening Settings converts the pre-pool shuffle toggles into a pool so they show up here.
        scope.launch {
            runCatching {
                manager.migrateLegacyIfNeeded()
                manager.restoreSchedules()
            }.onFailure { error ->
                error.rethrowIfCancelled()
                Log.w(TAG, "Sound pool migration failed", error)
            }
        }
    }

    fun create(name: String, onResult: (Result<String>) -> Unit) =
        scope.launch { onResult(manager.createPool(name)) }

    fun rename(poolId: String, name: String, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.renamePool(poolId, name)) }

    fun delete(poolId: String, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.deletePool(poolId)) }

    fun setEnabled(poolId: String, enabled: Boolean, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.setEnabled(poolId, enabled)) }

    fun setTargets(poolId: String, targets: Collection<SoundShuffleTarget>, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.setTargets(poolId, targets)) }

    fun setInterval(poolId: String, hours: Long, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.setInterval(poolId, hours)) }

    fun addDownload(poolId: String, downloadId: String, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.addDownload(poolId, downloadId)) }

    fun addOriginal(poolId: String, originalId: String, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.addOriginal(poolId, originalId)) }

    fun addLocal(poolId: String, uri: Uri, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.addLocal(poolId, uri)) }

    fun relink(poolId: String, itemId: String, uri: Uri, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.relinkItem(poolId, itemId, uri)) }

    fun remove(poolId: String, itemId: String, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.removeItem(poolId, itemId)) }

    fun runNow(poolId: String) = manager.runNow(poolId)

    fun undo(historyId: String, onResult: (Result<Unit>) -> Unit) =
        scope.launch { onResult(manager.undo(historyId)) }

    private companion object {
        const val TAG = "SettingsSoundShuffle"
    }
}
