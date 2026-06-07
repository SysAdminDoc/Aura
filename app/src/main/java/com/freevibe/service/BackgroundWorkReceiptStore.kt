package com.freevibe.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class BackgroundWorkReceipt(
    val lastSuccessUtc: String? = null,
    val lastFailureUtc: String? = null,
    val lastErrorClass: String? = null,
    val lastResult: String? = null,
    val lastDeferralReason: String? = null,
)

@Singleton
class BackgroundWorkReceiptStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(uniqueWorkName: String): BackgroundWorkReceipt {
        val prefix = keyPrefix(uniqueWorkName)
        return BackgroundWorkReceipt(
            lastSuccessUtc = prefs.getString("${prefix}last_success_utc", null),
            lastFailureUtc = prefs.getString("${prefix}last_failure_utc", null),
            lastErrorClass = prefs.getString("${prefix}last_error_class", null),
            lastResult = prefs.getString("${prefix}last_result", null),
            lastDeferralReason = prefs.getString("${prefix}last_deferral_reason", null),
        )
    }

    fun recordSuccess(uniqueWorkName: String) {
        val prefix = keyPrefix(uniqueWorkName)
        prefs.edit()
            .putString("${prefix}last_success_utc", utcNow())
            .putString("${prefix}last_result", "success")
            .remove("${prefix}last_error_class")
            .remove("${prefix}last_deferral_reason")
            .apply()
    }

    fun recordRetry(
        uniqueWorkName: String,
        errorClass: String? = null,
        deferralReason: String,
    ) {
        val prefix = keyPrefix(uniqueWorkName)
        prefs.edit()
            .putString("${prefix}last_failure_utc", utcNow())
            .putString("${prefix}last_result", "retry")
            .putString("${prefix}last_deferral_reason", deferralReason)
            .apply {
                if (errorClass.isNullOrBlank()) remove("${prefix}last_error_class")
                else putString("${prefix}last_error_class", errorClass)
            }
            .apply()
    }

    fun recordFailure(
        uniqueWorkName: String,
        errorClass: String,
        deferralReason: String,
    ) {
        val prefix = keyPrefix(uniqueWorkName)
        prefs.edit()
            .putString("${prefix}last_failure_utc", utcNow())
            .putString("${prefix}last_result", "failure")
            .putString("${prefix}last_error_class", errorClass)
            .putString("${prefix}last_deferral_reason", deferralReason)
            .apply()
    }

    fun recordWorkerResult(
        uniqueWorkName: String,
        resultClassName: String,
        retryReason: String,
    ) {
        when (resultClassName.lowercase(Locale.ROOT)) {
            "success" -> recordSuccess(uniqueWorkName)
            "retry" -> recordRetry(
                uniqueWorkName = uniqueWorkName,
                deferralReason = retryReason,
            )
            "failure" -> recordFailure(
                uniqueWorkName = uniqueWorkName,
                errorClass = "WorkerFailure",
                deferralReason = retryReason,
            )
            else -> recordFailure(
                uniqueWorkName = uniqueWorkName,
                errorClass = resultClassName.ifBlank { "UnknownResult" },
                deferralReason = "worker returned an unknown result",
            )
        }
    }

    private fun keyPrefix(uniqueWorkName: String): String =
        uniqueWorkName.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "unknown" } + "."

    private fun utcNow(): String = checkNotNull(UTC_FORMAT.get()).format(Date())

    private companion object {
        const val PREFS_NAME = "background_work_receipts"
        val UTC_FORMAT: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}
