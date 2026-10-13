package com.freevibe.service

/** A new profile starts as a night window, the case most people make one for. */
internal const val SOUND_PROFILE_DEFAULT_START_HOUR = 22
internal const val SOUND_PROFILE_DEFAULT_END_HOUR = 7

/** True when the profile names at least one sound. One that names none changes nothing. */
internal val SoundProfile.changesASound: Boolean
    get() = ringtoneUri.isNotBlank() || notificationUri.isNotBlank() || alarmUri.isNotBlank()

/** The 15 minute worker has something to do only with the switch on and a profile that can change a sound. */
internal fun soundProfilesNeedWorker(enabled: Boolean, profiles: List<SoundProfile>): Boolean =
    enabled && profiles.any { it.enabled && it.changesASound }

internal val SoundProfile.isAllDay: Boolean get() = startHour <= 0 && endHour >= 24

/** True when the window runs past midnight into the next day. */
internal val SoundProfile.isOvernight: Boolean get() = startHour > endHour

internal fun SoundProfile.soundFor(target: SoundShuffleTarget): String = when (target) {
    SoundShuffleTarget.RINGTONE -> ringtoneUri
    SoundShuffleTarget.NOTIFICATION -> notificationUri
    SoundShuffleTarget.ALARM -> alarmUri
}

/** A blank [uri] means the profile leaves that sound alone. */
internal fun SoundProfile.withSound(target: SoundShuffleTarget, uri: String): SoundProfile = when (target) {
    SoundShuffleTarget.RINGTONE -> copy(ringtoneUri = uri)
    SoundShuffleTarget.NOTIFICATION -> copy(notificationUri = uri)
    SoundShuffleTarget.ALARM -> copy(alarmUri = uri)
}

/**
 * Puts hours from an import or an older build into the range the editor steps through: a start
 * of 0 to 23 and an end of 1 to 24, where 24 is midnight at the end of the day. A window with the
 * same start and end never matched an hour, so it opens as all day for the person to narrow.
 */
internal fun SoundProfile.withEditableHours(): SoundProfile {
    val start = Math.floorMod(startHour, 24)
    val end = if (endHour in 1..24) endHour else Math.floorMod(endHour, 24).let { if (it == 0) 24 else it }
    return if (start == end) copy(startHour = 0, endHour = 24) else copy(startHour = start, endHour = end)
}

/** Moves the start an hour forward or back, skipping the one hour that would leave an empty window. */
internal fun SoundProfile.withStartStepped(delta: Int): SoundProfile {
    var next = Math.floorMod(startHour + delta, 24)
    if (next == endHour) next = Math.floorMod(next + delta, 24)
    return copy(startHour = next)
}

/** Moves the end an hour forward or back within 1 to 24, skipping the empty window the same way. */
internal fun SoundProfile.withEndStepped(delta: Int): SoundProfile {
    var next = Math.floorMod(endHour - 1 + delta, 24) + 1
    if (next == startHour) next = Math.floorMod(next - 1 + delta, 24) + 1
    return copy(endHour = next)
}

/**
 * Other enabled profiles that cover an hour [profile] also covers. Where two overlap, the one
 * higher in the list is the one the worker applies, so the editor says which ones are in the way.
 */
internal fun overlappingSoundProfiles(profiles: List<SoundProfile>, profile: SoundProfile): List<SoundProfile> {
    val hours = (0..23).filter(profile::coversHour)
    return profiles.filter { other ->
        other.id != profile.id && other.enabled && hours.any(other::coversHour)
    }
}

/** Replaces the profile with the same id, or adds it at the end when it's new. */
internal fun List<SoundProfile>.saving(profile: SoundProfile): List<SoundProfile> =
    if (any { it.id == profile.id }) map { if (it.id == profile.id) profile else it } else this + profile

/** Moves a profile up (-1) or down (+1) the list. Out of range leaves the list as it was. */
internal fun List<SoundProfile>.moving(id: String, delta: Int): List<SoundProfile> {
    val from = indexOfFirst { it.id == id }
    val to = from + delta
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
