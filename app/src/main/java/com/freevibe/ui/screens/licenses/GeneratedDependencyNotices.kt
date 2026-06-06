package com.freevibe.ui.screens.licenses

import android.content.res.Resources
import com.freevibe.R
import java.util.Locale

data class GeneratedDependencyNotice(
    val name: String,
    val licenseLabel: String,
    val licenseText: String,
    val reviewLabel: String? = null,
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
                        reviewLabel = reviewLabelFor(entry.name),
                    )
                }
            }
            .toList()

    fun filter(
        notices: List<GeneratedDependencyNotice>,
        query: String,
        reviewOnly: Boolean = false,
    ): List<GeneratedDependencyNotice> {
        val terms = query
            .lowercase(Locale.ROOT)
            .splitToSequence(' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return notices.filter { notice ->
            (!reviewOnly || notice.reviewLabel != null) &&
                terms.all { term -> term in notice.searchText }
        }
    }

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

    private fun reviewLabelFor(name: String): String? {
        val normalized = name.lowercase(Locale.ROOT)
        return reviewRules.firstOrNull { rule ->
            rule.matchers.any { matcher -> matcher in normalized }
        }?.label
    }

    private val GeneratedDependencyNotice.searchText: String
        get() = listOfNotNull(name, licenseLabel, reviewLabel)
            .joinToString(separator = " ")
            .lowercase(Locale.ROOT)
}

private data class MetadataEntry(
    val offset: Int,
    val length: Int,
    val name: String,
)

private data class ReviewRule(
    val label: String,
    val matchers: List<String>,
)

private val reviewRules = listOf(
    ReviewRule(
        label = "Review: Firebase",
        matchers = listOf("firebase"),
    ),
    ReviewRule(
        label = "Review: Play services",
        matchers = listOf("play services", "play-services", "com.google.android.gms"),
    ),
    ReviewRule(
        label = "Review: ML Kit",
        matchers = listOf("ml kit", "mlkit", "subject segmentation"),
    ),
    ReviewRule(
        label = "Review: NewPipeExtractor",
        matchers = listOf("newpipe"),
    ),
    ReviewRule(
        label = "Review: youtubedl-android",
        matchers = listOf("youtubedl"),
    ),
    ReviewRule(
        label = "Review: ProfileInstaller",
        matchers = listOf("profileinstaller", "profile installer"),
    ),
    ReviewRule(
        label = "Review: ZXing",
        matchers = listOf("zxing"),
    ),
)
