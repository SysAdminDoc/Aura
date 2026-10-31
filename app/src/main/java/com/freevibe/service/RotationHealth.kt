package com.freevibe.service

import com.freevibe.R
import com.freevibe.util.UiText
import com.freevibe.util.uiText

/**
 * Why a rotation is or is not going to happen.
 *
 * Auto-rotation stopping silently is the most-reported failure in this category
 * and no app in it shows the user any scheduler state, so "it just stopped" is
 * where every report ends. The point of this type is to make the difference
 * between the five reasons visible, because the user's next action is different
 * for each one and only one of them is a bug.
 */
enum class RotationHealthVerdict {
    /** Rotation is off. Nothing is wrong; nothing is scheduled either. */
    DISABLED,

    /** Scheduled, with a next fire time, and the last run did not fail. */
    HEALTHY,

    /** Scheduled, but the OS is holding it — battery optimisation, a constraint, a quota. */
    THROTTLED,

    /** Enabled, but WorkManager has no record of it. The schedule was lost. */
    NOT_SCHEDULED,

    /** Scheduled and running, but the last run ended in a failure. */
    FAILING,
}

/**
 * A single reading of everything that decides whether the next rotation happens.
 *
 * Times are pre-formatted UTC strings rather than epoch millis so the whole type
 * can be built and asserted in a JVM test without a clock or a locale.
 */
data class RotationHealthSnapshot(
    val verdict: RotationHealthVerdict = RotationHealthVerdict.DISABLED,
    val rotationEnabled: Boolean = false,
    val intervalMinutes: Long? = null,
    val sourceLabel: String? = null,
    val workState: String = WORK_INFO_STATUS_NONE,
    val lastFireUtc: String? = null,
    val lastFailureUtc: String? = null,
    val nextFireUtc: String? = null,
    val stopReason: String? = null,
    val lastErrorClass: String? = null,
    val lastResult: String? = null,
    val lastDeferralReason: String? = null,
    val bootReceiverLastUtc: String? = null,
    val ignoringBatteryOptimizations: Boolean? = null,
    val batteryGuidance: BackgroundBatteryGuidance =
        backgroundBatteryGuidanceForManufacturer(""),
    val actionHint: UiText? = null,
    val readError: String? = null,
) {
    /** True when the user has something to act on rather than something to read. */
    val needsAttention: Boolean
        get() = verdict == RotationHealthVerdict.NOT_SCHEDULED ||
            verdict == RotationHealthVerdict.FAILING ||
            verdict == RotationHealthVerdict.THROTTLED

    /** Whether a boot has been observed since Aura was installed. */
    val bootReceiverFired: Boolean get() = bootReceiverLastUtc != null
}

/**
 * The states WorkManager reports for a periodic worker that is still on the books.
 *
 * ENQUEUED is the normal resting state between runs; RUNNING is a live fire; and
 * BLOCKED means a prerequisite has not been met. CANCELLED and the terminal states
 * mean the schedule is gone even when the setting still says rotation is on, which
 * is precisely the failure this screen exists to name.
 */
private val LIVE_WORK_STATES = setOf("ENQUEUED", "RUNNING", "BLOCKED")

/**
 * A stop reason the OS chose, rather than one Aura or the user chose.
 *
 * CANCELLED_BY_APP and USER are deliberate, so they are not evidence of
 * throttling; everything else in this set is the system deciding Aura does not
 * get to run right now, which is the case a user can actually fix.
 */
private val THROTTLING_STOP_REASONS = setOf(
    "CONSTRAINT_BATTERY_NOT_LOW",
    "CONSTRAINT_CHARGING",
    "CONSTRAINT_CONNECTIVITY",
    "CONSTRAINT_DEVICE_IDLE",
    "CONSTRAINT_STORAGE_NOT_LOW",
    "QUOTA",
    "BACKGROUND_RESTRICTION",
    "APP_STANDBY",
    "DEVICE_STATE",
    "TIMEOUT",
    "PREEMPT",
)

/**
 * Decides the verdict from the raw readings.
 *
 * Kept separate from the reader so every branch is provable without WorkManager,
 * a PowerManager, or a device. The ordering matters: "rotation is off" outranks
 * everything, and a lost schedule outranks a stale failure, because rescheduling
 * is what fixes both and the user should be told the more actionable one.
 */
internal fun classifyRotationHealth(
    rotationEnabled: Boolean,
    workState: String,
    hasNextFire: Boolean,
    stopReason: String?,
    lastResult: String?,
    ignoringBatteryOptimizations: Boolean?,
): RotationHealthVerdict {
    if (!rotationEnabled) return RotationHealthVerdict.DISABLED

    val scheduled = LIVE_WORK_STATES.any { workState.contains(it) }
    if (!scheduled) return RotationHealthVerdict.NOT_SCHEDULED

    if (lastResult.equals("failure", ignoreCase = true)) return RotationHealthVerdict.FAILING

    val throttledByOs = stopReason
        ?.split(",")
        ?.map { it.substringBefore("=").trim() }
        ?.any { it in THROTTLING_STOP_REASONS } == true
    if (throttledByOs) return RotationHealthVerdict.THROTTLED

    // No next fire time on a live periodic worker means the OS is holding it
    // rather than scheduling it. Battery optimisation is the usual cause, so it
    // only counts as throttling when the app is not exempt — an exempt app with
    // no next fire is between runs, not held.
    if (!hasNextFire && ignoringBatteryOptimizations == false) {
        return RotationHealthVerdict.THROTTLED
    }

    if (lastResult.equals("retry", ignoreCase = true)) return RotationHealthVerdict.THROTTLED

    return RotationHealthVerdict.HEALTHY
}

/**
 * The one thing to do next, in the user's terms.
 *
 * Returns null for a healthy schedule rather than inventing reassurance: a row
 * that always has advice is a row nobody reads when the advice matters.
 */
internal fun rotationHealthActionHint(
    verdict: RotationHealthVerdict,
    ignoringBatteryOptimizations: Boolean?,
    batteryGuidance: BackgroundBatteryGuidance,
    lastErrorClass: String?,
    lastDeferralReason: String?,
): UiText? = when (verdict) {
    RotationHealthVerdict.DISABLED -> uiText(R.string.rotation_health_hint_disabled)

    RotationHealthVerdict.HEALTHY -> null

    RotationHealthVerdict.NOT_SCHEDULED -> uiText(R.string.rotation_health_hint_not_scheduled)

    RotationHealthVerdict.FAILING -> {
        val advice = when {
            lastDeferralReason?.contains("network", ignoreCase = true) == true ||
                lastErrorClass == "IOException" -> uiText(R.string.rotation_health_hint_check_connection)
            lastDeferralReason?.contains("permission", ignoreCase = true) == true ->
                uiText(R.string.rotation_health_hint_check_permission)
            else -> uiText(R.string.rotation_health_hint_try_run_now)
        }
        if (lastErrorClass != null) {
            uiText(R.string.rotation_health_hint_failed_with, lastErrorClass, advice)
        } else {
            uiText(R.string.rotation_health_hint_failed, advice)
        }
    }

    RotationHealthVerdict.THROTTLED -> if (ignoringBatteryOptimizations == false) {
        uiText(
            R.string.rotation_health_hint_throttled,
            batteryGuidance.manufacturer,
            batteryGuidance.summary,
        )
    } else {
        uiText(R.string.rotation_health_hint_waiting)
    }
}
