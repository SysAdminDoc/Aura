package com.freevibe.ui.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URI
import java.util.Locale

internal fun isSupportedExternalUrl(rawUrl: String): Boolean =
    supportedExternalUrlOrNull(rawUrl) != null

fun openExternalUrl(
    context: Context,
    rawUrl: String,
    failureMessage: String = "Could not open link",
): Boolean {
    val url = supportedExternalUrlOrNull(rawUrl) ?: return showExternalLinkFailure(context, failureMessage)
    val uri = Uri.parse(url)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return runCatching {
        context.startActivity(intent)
    }.fold(
        onSuccess = { true },
        onFailure = { showExternalLinkFailure(context, failureMessage) },
    )
}

private fun supportedExternalUrlOrNull(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    val scheme = runCatching { URI(trimmed).scheme?.lowercase(Locale.ROOT) }.getOrNull()
    return when (scheme) {
        "http", "https", "mailto" -> trimmed
        else -> null
    }
}

private fun showExternalLinkFailure(context: Context, message: String): Boolean {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    return false
}
