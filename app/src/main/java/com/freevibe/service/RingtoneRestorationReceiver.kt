package com.freevibe.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import com.freevibe.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RingtoneRestorationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val prefs = PreferencesManager(context)
                restoreIfNeeded(context, prefs, RingtoneManager.TYPE_RINGTONE, prefs.lastAppliedRingtoneUri.first())
                restoreIfNeeded(context, prefs, RingtoneManager.TYPE_NOTIFICATION, prefs.lastAppliedNotificationUri.first())
                restoreIfNeeded(context, prefs, RingtoneManager.TYPE_ALARM, prefs.lastAppliedAlarmUri.first())
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun restoreIfNeeded(
        context: Context,
        prefs: PreferencesManager,
        type: Int,
        lastAppliedUri: String,
    ) {
        if (lastAppliedUri.isBlank()) return
        val expected = Uri.parse(lastAppliedUri)
        val current = RingtoneManager.getActualDefaultRingtoneUri(context, type)
        if (current != expected) {
            try {
                context.contentResolver.openInputStream(expected)?.close()
                    ?: return
                RingtoneManager.setActualDefaultRingtoneUri(context, type, expected)
            } catch (_: Exception) {
            }
        }
    }
}
