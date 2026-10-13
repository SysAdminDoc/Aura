package com.google.firebase.storage

import android.net.Uri
import com.google.android.gms.tasks.Task

class FirebaseStorage private constructor() {
    val reference: StorageReference = StorageReference()

    companion object {
        fun getInstance(): FirebaseStorage = FirebaseStorage()
    }
}

class StorageReference {
    fun child(path: String): StorageReference = this
    fun putBytes(bytes: ByteArray, metadata: StorageMetadata): UploadTask = UploadTask()
    fun putFile(uri: Uri): UploadTask = UploadTask()
    fun putFile(uri: Uri, metadata: StorageMetadata): UploadTask = UploadTask()
    fun delete(): Task<Void?> = Task.failure()
    val downloadUrl: Task<Uri> get() = Task.failure()
}

class UploadTask : Task<Void?>(failure = IllegalStateException("Firebase Storage is unavailable in the FOSS build.")) {
    fun addOnProgressListener(listener: (TaskSnapshot) -> Unit): UploadTask = this
}

class TaskSnapshot {
    val totalByteCount: Long = 0
    val bytesTransferred: Long = 0
}

class StorageMetadata private constructor() {
    class Builder {
        fun setContentType(contentType: String): Builder = this
        fun build(): StorageMetadata = StorageMetadata()
    }
}

open class StorageException(
    val errorCode: Int,
    message: String = "Firebase Storage is unavailable in the FOSS build.",
) : Exception(message) {
    companion object {
        const val ERROR_OBJECT_NOT_FOUND: Int = -13010
    }
}
