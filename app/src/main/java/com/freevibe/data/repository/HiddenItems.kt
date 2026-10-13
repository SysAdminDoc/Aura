package com.freevibe.data.repository

import com.freevibe.data.legal.providerDisplayName
import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper
import org.json.JSONException
import org.json.JSONObject

/** The feed a hide came from, so the hidden list can filter by it. */
enum class HiddenItemKind { VIDEO, WALLPAPER, SOUND }

/** What the hidden list shows for an item. It's saved at hide time, since a content ID alone says nothing. */
data class HiddenItemLabel(
    val kind: HiddenItemKind,
    val title: String,
    val thumbnailUrl: String = "",
    val sourceName: String = "",
)

/** One local hide. [label] is null for items hidden before labels were saved. */
data class HiddenItem(
    val id: String,
    val label: HiddenItemLabel?,
    val hiddenAtMs: Long,
)

internal data class HiddenItemRecord(val label: HiddenItemLabel, val hiddenAtMs: Long)

private const val MAX_HIDDEN_TITLE_CHARS = 200

fun Wallpaper.hiddenItemLabel() = HiddenItemLabel(
    kind = HiddenItemKind.WALLPAPER,
    title = category,
    thumbnailUrl = thumbnailUrl,
    sourceName = source.providerDisplayName(),
)

fun Sound.hiddenItemLabel() = HiddenItemLabel(
    kind = HiddenItemKind.SOUND,
    title = name,
    sourceName = source.providerDisplayName(),
)

/** Labels live in one JSON object keyed by content ID. An unreadable entry is dropped, never fatal. */
internal fun decodeHiddenItemRecords(json: String?): Map<String, HiddenItemRecord> {
    if (json.isNullOrBlank()) return emptyMap()
    val root = try {
        JSONObject(json)
    } catch (_: JSONException) {
        return emptyMap()
    }
    return buildMap {
        root.keys().forEach { id ->
            val entry = root.optJSONObject(id) ?: return@forEach
            val kind = HiddenItemKind.entries.firstOrNull { it.name == entry.optString("kind") } ?: return@forEach
            val label = HiddenItemLabel(
                kind = kind,
                title = entry.optString("title"),
                thumbnailUrl = entry.optString("thumbnailUrl"),
                sourceName = entry.optString("sourceName"),
            )
            put(id, HiddenItemRecord(label, entry.optLong("hiddenAtMs")))
        }
    }
}

internal fun encodeHiddenItemRecords(records: Map<String, HiddenItemRecord>): String = JSONObject().apply {
    records.forEach { (id, record) ->
        put(
            id,
            JSONObject()
                .put("kind", record.label.kind.name)
                .put("title", record.label.title.take(MAX_HIDDEN_TITLE_CHARS))
                .put("thumbnailUrl", record.label.thumbnailUrl)
                .put("sourceName", record.label.sourceName)
                .put("hiddenAtMs", record.hiddenAtMs),
        )
    }
}.toString()

/** Newest first; hides from before labels were saved have no time and go last. */
internal fun hiddenItemsOf(ids: Set<String>, records: Map<String, HiddenItemRecord>): List<HiddenItem> =
    ids.map { id -> records[id].let { HiddenItem(id, it?.label, it?.hiddenAtMs ?: 0L) } }
        .sortedWith(compareByDescending<HiddenItem> { it.hiddenAtMs }.thenBy { it.id })

/** The stored local hides behind [contentIds], raw or sanitized, which is what a restore has to remove. */
internal fun localHidesFor(contentIds: Collection<String>, localHiddenIds: Set<String>): Set<String> =
    contentIds.flatMap { listOf(it, sanitizeVoteKey(it)) }.toSet().intersect(localHiddenIds)
