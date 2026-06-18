package com.freevibe.data.repository

import java.io.IOException
import kotlinx.coroutines.withTimeoutOrNull

internal const val FIREBASE_READ_TIMEOUT_MS = 8_000L

internal class FirebaseReadTimeoutException(surface: String, timeoutMs: Long) :
    IOException("$surface did not respond in ${formatFirebaseReadTimeout(timeoutMs)}")

internal suspend fun <T> awaitFirebaseRead(
    surface: String,
    timeoutMs: Long = FIREBASE_READ_TIMEOUT_MS,
    block: suspend () -> T,
): T =
    withTimeoutOrNull(timeoutMs) { block() }
        ?: throw FirebaseReadTimeoutException(surface, timeoutMs)

private fun formatFirebaseReadTimeout(timeoutMs: Long): String =
    if (timeoutMs >= 1_000L && timeoutMs % 1_000L == 0L) {
        "${timeoutMs / 1_000L} seconds"
    } else {
        "$timeoutMs ms"
    }
