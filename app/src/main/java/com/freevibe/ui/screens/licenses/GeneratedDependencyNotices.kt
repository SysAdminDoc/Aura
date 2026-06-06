package com.freevibe.ui.screens.licenses

import android.content.res.Resources
import com.freevibe.R
import java.util.Locale

data class GeneratedDependencyNotice(
    val name: String,
    val licenseLabel: String,
    val licenseText: String,
)

internal object GoogleOssNoticeReader {
    fun load(resources: Resources): List<GeneratedDependencyNotice> =
        runCatching {
            val metadataText = resources.openRawResource(R.raw.third_party_license_metadata)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val licenseBytes = resources.openRawResource(R.raw.third_party_licenses)
                .use { it.readBytes() }
            parse(metadataText = metadataText, licenseBytes = licenseBytes)
        }.getOrDefault(emptyList())

    fun parse(metadataText: String, licenseBytes: ByteArray): List<GeneratedDependencyNotice> =
        metadataText
            .lineSequence()
            .mapNotNull { parseMetadataLine(it) }
            .mapNotNull { entry ->
                val end = entry.offset + entry.length
                if (entry.offset < 0 || entry.length <= 0 || end > licenseBytes.size) {
                    null
                } else {
                    val licenseText = licenseBytes
                        .copyOfRange(entry.offset, end)
                        .toString(Charsets.UTF_8)
                        .trim()
                    GeneratedDependencyNotice(
                        name = entry.name,
                        licenseLabel = summarizeLicense(licenseText),
                        licenseText = licenseText,
                    )
                }
            }
            .toList()

    private fun parseMetadataLine(line: String): MetadataEntry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        val firstSpace = trimmed.indexOf(' ')
        if (firstSpace <= 0) return null

        val range = trimmed.substring(0, firstSpace)
        val colon = range.indexOf(':')
        if (colon <= 0 || colon == range.lastIndex) return null

        val offset = range.substring(0, colon).toIntOrNull() ?: return null
        val length = range.substring(colon + 1).toIntOrNull() ?: return null
        val name = trimmed.substring(firstSpace + 1).trim()
        if (name.isEmpty()) return null

        return MetadataEntry(offset = offset, length = length, name = name)
    }

    private fun summarizeLicense(licenseText: String): String {
        val lower = licenseText.lowercase(Locale.ROOT)
        val firstLine = licenseText.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()

        return when {
            "apache.org/licenses/license-2.0" in lower ||
                ("apache license" in lower && "version 2.0" in lower) -> "Apache 2.0"
            "api.github.com/licenses/gpl-3.0" in lower ||
                "gnu general public license" in lower -> "GPL-3.0"
            "opensource.org/licenses/bsd-3-clause" in lower ||
                "redistribution and use in source and binary forms" in lower -> "BSD-3-Clause"
            "mit license" in lower -> "MIT"
            firstLine.length in 1..80 -> firstLine
                .removePrefix("https://")
                .removePrefix("http://")
            else -> "Generated notice"
        }
    }
}

private data class MetadataEntry(
    val offset: Int,
    val length: Int,
    val name: String,
)
