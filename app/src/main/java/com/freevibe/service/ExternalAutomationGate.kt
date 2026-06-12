package com.freevibe.service

import android.content.Context
import android.content.Intent

data class ExternalAutomationDecision(
    val accepted: Boolean,
    val reason: String,
    val nextAllowedAtMs: Long = 0L,
)

data class ExternalAutomationDiagnostics(
    val enabled: Boolean = false,
    val lastAction: String = "",
    val lastCallerPackage: String = "",
    val lastAcceptedAtMs: Long = 0L,
    val lastRejectedAtMs: Long = 0L,
    val lastRejectedReason: String = "",
    val minIntervalMs: Long = ExternalAutomationGate.MIN_INTERVAL_MS,
)

object ExternalAutomationGate {
    const val EXTRA_CALLER_PACKAGE = "com.freevibe.extra.CALLER_PACKAGE"
    const val MIN_INTERVAL_MS = 30_000L

    private const val PREFS_NAME = "freevibe_external_automation"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_ACTION = "last_action"
    private const val KEY_LAST_CALLER_PACKAGE = "last_caller_package"
    private const val KEY_LAST_ACCEPTED_AT_MS = "last_accepted_at_ms"
    private const val KEY_LAST_REJECTED_AT_MS = "last_rejected_at_ms"
    private const val KEY_LAST_REJECTED_REASON = "last_rejected_reason"

    fun evaluate(
        context: Context,
        intent: Intent,
        nowMs: Long = System.currentTimeMillis(),
    ): ExternalAutomationDecision {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val action = intent.action.orEmpty()
        val callerPackage = sanitizeCallerPackage(intent.getStringExtra(EXTRA_CALLER_PACKAGE))
        val decision = decide(
            action = action,
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            lastAcceptedAtMs = prefs.getLong(KEY_LAST_ACCEPTED_AT_MS, 0L),
            nowMs = nowMs,
        )

        prefs.edit()
            .putString(KEY_LAST_ACTION, action)
            .putString(KEY_LAST_CALLER_PACKAGE, callerPackage)
            .apply {
                if (decision.accepted) {
                    putLong(KEY_LAST_ACCEPTED_AT_MS, nowMs)
                    putString(KEY_LAST_REJECTED_REASON, "")
                } else {
                    putLong(KEY_LAST_REJECTED_AT_MS, nowMs)
                    putString(KEY_LAST_REJECTED_REASON, decision.reason)
                }
            }
            .apply()
        return decision
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun readDiagnostics(context: Context): ExternalAutomationDiagnostics {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ExternalAutomationDiagnostics(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            lastAction = prefs.getString(KEY_LAST_ACTION, "").orEmpty(),
            lastCallerPackage = prefs.getString(KEY_LAST_CALLER_PACKAGE, "").orEmpty(),
            lastAcceptedAtMs = prefs.getLong(KEY_LAST_ACCEPTED_AT_MS, 0L),
            lastRejectedAtMs = prefs.getLong(KEY_LAST_REJECTED_AT_MS, 0L),
            lastRejectedReason = prefs.getString(KEY_LAST_REJECTED_REASON, "").orEmpty(),
        )
    }

    fun decide(
        action: String?,
        enabled: Boolean,
        lastAcceptedAtMs: Long,
        nowMs: Long,
        minIntervalMs: Long = MIN_INTERVAL_MS,
    ): ExternalAutomationDecision {
        if (!isSupportedAction(action)) {
            return ExternalAutomationDecision(accepted = false, reason = "unsupported_action")
        }
        if (!enabled) {
            return ExternalAutomationDecision(accepted = false, reason = "disabled")
        }
        val nextAllowedAtMs = lastAcceptedAtMs + minIntervalMs
        if (lastAcceptedAtMs > 0L && nowMs < nextAllowedAtMs) {
            return ExternalAutomationDecision(
                accepted = false,
                reason = "rate_limited",
                nextAllowedAtMs = nextAllowedAtMs,
            )
        }
        return ExternalAutomationDecision(accepted = true, reason = "accepted")
    }

    fun isSupportedAction(action: String?): Boolean =
        action == TaskerActionReceiver.ACTION_ROTATE_NOW ||
            action == TaskerActionReceiver.ACTION_SHUFFLE_NOW

    fun sanitizeCallerPackage(raw: String?): String {
        val trimmed = raw.orEmpty().trim()
        if (trimmed.isEmpty() || trimmed.length > 96) return ""
        return if (trimmed.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }) {
            trimmed
        } else {
            ""
        }
    }
}
