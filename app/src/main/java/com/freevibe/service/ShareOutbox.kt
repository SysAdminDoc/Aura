package com.freevibe.service

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal object ShareOutbox {
    private const val SHARE_OUT_DIR = "share_out"
    private const val DEFAULT_MAX_AGE_MS = 24L * 60L * 60L * 1000L

    fun directory(context: Context, vararg childSegments: String): File =
        directory(context.cacheDir, *childSegments)

    fun pruneStaleFiles(context: Context, nowMs: Long = System.currentTimeMillis()) {
        pruneStaleFiles(directory(context), nowMs)
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

    internal fun directory(cacheDir: File, vararg childSegments: String): File {
        var dir = File(cacheDir, SHARE_OUT_DIR)
        childSegments.forEach { segment ->
            require(segment.isNotBlank() && '/' !in segment && '\\' !in segment) {
                "Share outbox path segments must be simple names"
            }
            dir = File(dir, segment)
        }
        return dir.apply { mkdirs() }
    }

    internal fun pruneStaleFiles(
        root: File,
        nowMs: Long,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    ) {
        if (!root.exists()) return
        root.walkBottomUp().forEach { file ->
            if (file == root) return@forEach
            if (file.isFile && nowMs - file.lastModified() > maxAgeMs) {
                file.delete()
            } else if (file.isDirectory && file.listFiles()?.isEmpty() == true) {
                file.delete()
            }
        }
    }
}
