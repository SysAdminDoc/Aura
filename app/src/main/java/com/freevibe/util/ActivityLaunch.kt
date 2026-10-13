package com.freevibe.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import com.freevibe.MainActivity

private const val TAG = "ActivityLaunch"

/**
 * Starts [intent] and reports whether it opened. A missing handler, a permission refusal or a
 * background-launch block is logged in every build instead of being swallowed, and the false
 * return lets the caller tell the person on whatever surface it has.
 */
internal fun Context.tryStartActivity(intent: Intent, tag: String): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    Log.w(TAG, "No activity can open $tag")
    false
} catch (e: SecurityException) {
    Log.w(TAG, "Not allowed to open $tag")
    false
} catch (e: RuntimeException) {
    Log.w(TAG, "Couldn't open $tag: ${e.javaClass.simpleName}")
    false
}

/**
 * Aura's launcher intent. The package manager can return none (a launcher alias disabled, or a
 * query that races an update), so this falls back to an explicit MainActivity intent.
 */
internal fun appLaunchIntent(
    context: Context,
    launchIntent: Intent? = context.packageManager.getLaunchIntentForPackage(context.packageName),
): Intent =
    launchIntent ?: Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
