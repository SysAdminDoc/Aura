package com.freevibe.util

import android.content.Context
import android.icu.text.ListFormatter
import android.os.Build
import androidx.annotation.StringRes
import com.freevibe.R
import java.io.IOException

/**
 * Text that gets its wording where a [Context] exists. Code that builds a message without one
 * (media ingestion, repositories) returns a resource ID and its arguments, and the screen or
 * ViewModel that shows it calls [resolve].
 */
sealed interface UiText {
    fun resolve(context: Context): String

    /** Text that is already in its final form, such as a license name. */
    data class Plain(val text: String) : UiText {
        override fun resolve(context: Context): String = text
    }

    /** A string resource. An argument that is itself a [UiText] is resolved first. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        override fun resolve(context: Context): String =
            context.getString(id, *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
    }

    /** Names joined with the locale's "or", e.g. "JPEG, PNG, or WebP". */
    data class OrList(val items: List<String>) : UiText {
        override fun resolve(context: Context): String {
            if (items.size <= 1) return items.firstOrNull().orEmpty()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val locale = context.resources.configuration.locales[0]
                return ListFormatter.getInstance(locale, ListFormatter.Type.OR, ListFormatter.Width.WIDE).format(items)
            }
            val head = items.dropLast(1).reduce { joined, item -> context.getString(R.string.list_join, joined, item) }
            val last = items.last()
            return context.getString(
                if (items.size == 2) R.string.list_or_two else R.string.list_or_last,
                head,
                last,
            )
        }
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/**
 * An I/O failure whose wording is a resource. The exception text stays a short diagnostic
 * for logs; screens that show it call [displayMessage].
 */
open class UiTextException(
    val text: UiText,
    message: String = text.toString(),
    cause: Throwable? = null,
) : IOException(message, cause)

/** What to show a person for this failure: a localized [UiTextException] text, else the plain message. */
fun Throwable.displayMessage(context: Context): String? =
    (this as? UiTextException)?.text?.resolve(context) ?: message
