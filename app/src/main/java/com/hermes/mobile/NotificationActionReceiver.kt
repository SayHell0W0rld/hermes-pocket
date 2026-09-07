package com.hermes.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Delivers approval notification actions through MainActivity, never through a
 * background HTTP stack. The WebView's WebUI api() wrapper keeps credentials,
 * CSRF and stale-response handling in one place.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_APPROVAL) return
        val choice = intent.getStringExtra(NotificationHelper.EXTRA_APPROVAL_CHOICE) ?: return
        val sessionId = intent.getStringExtra(NotificationHelper.EXTRA_SESSION_ID) ?: return
        val approvalId = intent.getStringExtra(NotificationHelper.EXTRA_APPROVAL_ID) ?: return

        val delivery = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(NotificationHelper.EXTRA_SESSION_ID, sessionId)
            putExtra(NotificationHelper.EXTRA_APPROVAL_ID, approvalId)
            putExtra(NotificationHelper.EXTRA_APPROVAL_CHOICE, choice)
            putExtra(NotificationHelper.EXTRA_DELIVERY_FALLBACK, intent.getBooleanExtra(
                NotificationHelper.EXTRA_DELIVERY_FALLBACK, false
            ))
        }

        try {
            context.startActivity(delivery)
        } catch (_: Exception) {
            Toast.makeText(context, android.R.string.ok, Toast.LENGTH_SHORT).show()
        }
    }
}
