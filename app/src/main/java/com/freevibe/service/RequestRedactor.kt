package com.freevibe.service

import java.net.URI
import java.util.Locale

internal object RequestRedactor {
    private const val REDACTED = "<redacted>"
    private val authorizationHeaderRegex = Regex("""(?i)\bauthorization\s*[:=]\s*Bearer\s+[A-Za-z0-9._~+/=-]+""")
    private val bearerRegex = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    private val assignmentSecretRegex = Regex(
        """(?i)\b((?:[a-z0-9]+[._-])*(?:api[._-]?key|apikey|access[._-]?token|token|password|secret|client[._-]?id|authorization|key))\b\s*[:=]\s*["']?[^"',\s)&]+""",
    )
    private val querySecretRegex = Regex(
        """(?i)([?&](?:api[._-]?key|apikey|key|access[._-]?token|token|client[._-]?id|password|secret)=)[^&#\s)'">]+""",
    )

    fun redact(raw: String): String {
        var result = raw
        result = authorizationHeaderRegex.replace(result, "authorization=$REDACTED")
        result = bearerRegex.replace(result, "Bearer $REDACTED")
        result = assignmentSecretRegex.replace(result) { match ->
            "${match.groupValues[1]}=$REDACTED"
        }
        result = querySecretRegex.replace(result) { match ->
            "${match.groupValues[1]}$REDACTED"
        }
        return result
    }

    fun redactUrl(url: String): String = redact(url)

    fun formatRequest(method: String, url: String, statusCode: Int? = null): String {
        val parts = mutableListOf<String>()
        method.trim()
            .takeIf { it.isNotBlank() }
            ?.let { parts += it.uppercase(Locale.ROOT) }
        parts += redactedUrlForDisplay(url)
        statusCode?.let { parts += "status=$it" }
        return parts.joinToString(" ")
    }

    private fun redactedUrlForDisplay(url: String): String {
        val trimmed = url.trim()
        val parsed = runCatching { URI(trimmed) }.getOrNull()
        val host = parsed?.host?.takeIf { it.isNotBlank() } ?: return redact(trimmed)
        val path = parsed.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        val query = parsed.rawQuery
            ?.takeIf { it.isNotBlank() }
            ?.let { redact("?$it").removePrefix("?") }
        return buildString {
            append(host)
            append(path)
            if (!query.isNullOrBlank()) {
                append("?")
                append(query)
            }
        }
    }
}
