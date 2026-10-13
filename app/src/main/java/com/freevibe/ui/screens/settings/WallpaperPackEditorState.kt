package com.freevibe.ui.screens.settings

import com.freevibe.data.model.WallpaperTarget
import com.freevibe.service.Daypart
import com.freevibe.service.DaypartSlot
import com.freevibe.service.WallpaperPack
import com.freevibe.service.parsePack
import com.freevibe.service.serializePack

/** The editor keeps one pack. Its id stays fixed so theme-pack exports of it stay stable. */
internal const val WALLPAPER_PACK_ID = "main"

internal fun editablePack(raw: String): WallpaperPack =
    parsePack(raw) ?: WallpaperPack(id = WALLPAPER_PACK_ID, name = "")

internal fun WallpaperPack.slotFor(daypart: Daypart): DaypartSlot? =
    slots.firstOrNull { it.daypart == daypart }

/** Sets or replaces the wallpaper for [daypart], keeping any target already chosen for it. */
internal fun WallpaperPack.withSlot(daypart: Daypart, locator: String, label: String): WallpaperPack {
    val existing = slotFor(daypart)
    val slot = DaypartSlot(
        daypart = daypart,
        wallpaperUri = locator,
        label = label,
        target = existing?.target.orEmpty(),
    )
    return copy(slots = (slots.filter { it.daypart != daypart } + slot).sortedBy { it.daypart.ordinal })
}

internal fun WallpaperPack.withSlotTarget(daypart: Daypart, target: String): WallpaperPack =
    copy(slots = slots.map { if (it.daypart == daypart) it.copy(target = target) else it })

internal fun WallpaperPack.withoutSlot(daypart: Daypart): WallpaperPack =
    copy(slots = slots.filter { it.daypart != daypart })

/** The target a slot will use, as shown in the editor. */
internal fun WallpaperPack.effectiveTarget(slot: DaypartSlot): String =
    slot.target.ifBlank { target.ifBlank { WallpaperTarget.BOTH.name } }

internal fun WallpaperPack.toEditorJson(): String = serializePack(this)
