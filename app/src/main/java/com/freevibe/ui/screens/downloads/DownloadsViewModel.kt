package com.freevibe.ui.screens.downloads

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.model.SOURCE_AVAILABILITY_AVAILABLE
import com.freevibe.data.model.SOURCE_AVAILABILITY_UNAVAILABLE
import com.freevibe.service.DownloadManager
import com.freevibe.service.LocalMediaRelinkManager
import com.freevibe.service.LocalMediaRelinkOutcome
import com.freevibe.service.LocalMediaRelinkTarget
import com.freevibe.service.MediaCopyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val downloadManager: DownloadManager,
    private val mediaCopyStore: MediaCopyStore,
    private val localMediaRelinkManager: LocalMediaRelinkManager,
) : ViewModel() {
    val allDownloads = downloadDao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val wallpaperDownloads = downloadDao.getByType("WALLPAPER").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val soundDownloads = downloadDao.getByType("SOUND").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeDownloads = downloadManager.activeDownloads

    suspend fun downloadExists(id: String): Boolean = downloadDao.getById(id) != null
    fun deleteDownload(id: String) = viewModelScope.launch { downloadManager.deleteDownload(id) }
    suspend fun deleteOptimizedCopy(id: String): Boolean = mediaCopyStore.deleteOptimizedCopy(id)
    suspend fun relinkDownload(id: String, uri: Uri, acceptMismatch: Boolean = false): LocalMediaRelinkOutcome =
        localMediaRelinkManager.relink(LocalMediaRelinkTarget.Download(id), uri, acceptMismatch)

    /** Puts a staged download back, file included, from the Undo action. */
    fun restoreDownload(id: String) = viewModelScope.launch { downloadManager.restoreDownload(id) }
    fun dismissActive(id: String) = downloadManager.clearCompleted(id)
    fun retryActive(id: String) = downloadManager.startRetry(id)
    fun markSourceUnavailable(id: String, reason: String? = null) = viewModelScope.launch {
        downloadDao.updateSourceAvailability(
            id,
            SOURCE_AVAILABILITY_UNAVAILABLE,
            reason?.takeIf { it.isNotBlank() },
        )
    }
    fun clearSourceUnavailable(id: String) = viewModelScope.launch {
        downloadDao.updateSourceAvailability(id, SOURCE_AVAILABILITY_AVAILABLE, null)
    }
}
