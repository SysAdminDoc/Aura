package com.freevibe.data.repository

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

internal suspend fun FirebaseStorage.deleteCommunityStoragePathIfPresent(storagePath: String) {
    try {
        reference.child(storagePath).delete().await()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        if (e is StorageException && e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) return
        throw e
    }
}
