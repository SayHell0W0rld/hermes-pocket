package com.hermes.mobile

import android.Manifest
import android.app.Notification
import android.app.Notification.Action
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_APPROVAL = "hermes_approval"
        const val CHANNEL_SESSION = "hermes_session"
        const val CHANNEL_TASKS = "hermes_tasks"
        const val CHANNEL_ERRORS = "hermes_errors"

        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_CRON_JOB_ID = "cron_job_id"
        const val EXTRA_APPROVAL_ID = "approval_id"
        const val EXTRA_APPROVAL_CHOICE = "approval_choice"
        const val EXTRA_DELIVERY_FALLBACK = "delivery_fallback"
        const val ACTION_APPROVAL = "com.hermes.mobile.APPROVAL_ACTION"
        const val CHOICE_APPROVE = "approve"
        const val CHOICE_DENY = "deny"
        private const val PREF_NAME = "hermes_config"

        private const val MAX_CRON_MAPPINGS = 128
        private const val MAX_APPROVAL_IDS = 64
        private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    private data class ChannelSpec(
        val id: String,
        val nameRes: Int,
        val descRes: Int,
        val importance: Int
    )

    init {
        ensureChannels()
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            ChannelSpec(
                CHANNEL_APPROVAL,
                R.string.notification_channel_approval_name,
                R.string.notification_channel_approval_desc,
                NotificationManager.IMPORTANCE_HIGH
            ),
            ChannelSpec(
                CHANNEL_SESSION,
                R.string.notification_channel_session_name,
                R.string.notification_channel_session_desc,
                NotificationManager.IMPORTANCE_DEFAULT
            ),
            ChannelSpec(
                CHANNEL_TASKS,
                R.string.notification_channel_tasks_name,
                R.string.notification_channel_tasks_desc,
                NotificationManager.IMPORTANCE_DEFAULT
            ),
            ChannelSpec(
                CHANNEL_ERRORS,
                R.string.notification_channel_errors_name,
                R.string.notification_channel_errors_desc,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        channels.forEach { spec ->
            nm.createNotificationChannel(
                NotificationChannel(spec.id, context.getString(spec.nameRes), spec.importance).apply {
                    description = context.getString(spec.descRes)
                }
            )
        }
    }

    fun notifyApproval(title: String, body: String, sessionId: String?, approvalId: String? = null) {
        val t = title.ifBlank { context.getString(R.string.notification_approval_title) }
        val b = body.ifBlank { context.getString(R.string.notification_approval_body) }
        show(CHANNEL_APPROVAL, t, b, sessionId, approvalId = approvalId)
    }

    fun notifySession(title: String, body: String, sessionId: String?) {
        val t = title.ifBlank { context.getString(R.string.notification_session_title) }
        val b = body.ifBlank { context.getString(R.string.notification_session_body) }
        show(CHANNEL_SESSION, t, b, sessionId)
    }

    fun notifyTaskComplete(
        title: String,
        body: String,
        sessionId: String?,
        cronJobId: String? = null,
        failed: Boolean = false
    ) {
        val fallback = if (failed) R.string.notification_cron_failed_title else R.string.notification_task_complete_title
        val t = title.ifBlank { context.getString(fallback) }
        val b = body.ifBlank { context.getString(R.string.notification_task_complete_body) }
        show(CHANNEL_TASKS, t, b, sessionId, cronJobId = cronJobId)
    }

    fun notifyError(title: String, body: String, sessionId: String?) {
        val t = title.ifBlank { context.getString(R.string.notification_task_error_title) }
        val b = body.ifBlank { context.getString(R.string.notification_task_error_body) }
        show(CHANNEL_ERRORS, t, b, sessionId)
    }

    fun rememberCronMapping(sessionId: String, cronJobId: String) {
        if (sessionId.isBlank() || cronJobId.isBlank()) return
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            putString("cron_job_${sessionId.hashCode()}", cronJobId)
            putString("cron_session_${cronJobId.hashCode()}", sessionId)
            apply()
        }
        trimCronMappings()
    }

    fun cronJobIdForSession(sessionId: String?): String? {
        if (sessionId.isNullOrBlank()) return null
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("cron_job_${sessionId.hashCode()}", null)
            ?.takeIf { it.isNotBlank() }
    }

    fun clearCronMappings() {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.all.keys.filter {
            it.startsWith("cron_job_") || it.startsWith("cron_session_")
        }.forEach(prefs.edit()::remove)
        prefs.edit().apply()
    }

    private fun trimCronMappings() {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val entries = prefs.all.keys.filter {
            it.startsWith("cron_job_") || it.startsWith("cron_session_")
        }
        val limit = MAX_CRON_MAPPINGS * 2
        if (entries.size > limit) {
            entries.take(entries.size - limit).forEach(prefs.edit()::remove)
            prefs.edit().apply()
        }
    }

    private fun rememberApprovalId(sessionId: String, approvalId: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString("approval_id_${sessionId.hashCode()}", approvalId)
            .apply()
        trimApprovalIds()
    }

    fun approvalIdForSession(sessionId: String?): String? {
        if (sessionId.isNullOrBlank()) return null
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("approval_id_${sessionId.hashCode()}", null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun trimApprovalIds() {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val entries = prefs.all.keys.filter { it.startsWith("approval_id_") }
        if (entries.size > MAX_APPROVAL_IDS) {
            entries.take(entries.size - MAX_APPROVAL_IDS).forEach(prefs.edit()::remove)
            prefs.edit().apply()
        }
    }

    fun cancelApproval(sessionId: String?) {
            notificationManager().cancel(notificationId(CHANNEL_APPROVAL, sessionId))
    }

    fun cancelAllForSession(sessionId: String?) {
        val nm = notificationManager()
        listOf(CHANNEL_APPROVAL, CHANNEL_SESSION, CHANNEL_TASKS, CHANNEL_ERRORS)
            .forEach { nm.cancel(notificationId(it, sessionId)) }
    }

    fun cancelAll() {
        val nm = notificationManager()
        listOf(CHANNEL_APPROVAL, CHANNEL_SESSION, CHANNEL_TASKS, CHANNEL_ERRORS)
            .forEach { nm.cancel(notificationId(it, null)) }
    }

    private fun notificationManager(): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun notificationId(channelId: String, sessionId: String?): Int {
        val seed = channelId.hashCode() and 0x3F
        val sessionPart = sessionId?.takeIf { it.isNotBlank() }?.hashCode() ?: 0
        return 100000 + (seed shl 20) + (sessionPart and 0xFFFFF)
    }

    private fun show(
        channelId: String,
        title: String,
        body: String,
        sessionId: String?,
        cronJobId: String? = null,
        approvalId: String? = null
    ) {
            if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val id = notificationId(channelId, sessionId)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            if (!sessionId.isNullOrBlank()) putExtra(EXTRA_SESSION_ID, sessionId)
            if (!cronJobId.isNullOrBlank()) putExtra(EXTRA_CRON_JOB_ID, cronJobId)
            if (!approvalId.isNullOrBlank()) putExtra(EXTRA_APPROVAL_ID, approvalId)
        }
        val approvalActionIntent: Intent? = if (channelId == CHANNEL_APPROVAL && !sessionId.isNullOrBlank()) {
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_APPROVAL
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_APPROVAL_ID, approvalId)
                putExtra(EXTRA_DELIVERY_FALLBACK, approvalId.isNullOrBlank())
            }
        } else null
        val approvePending: PendingIntent? = approvalActionIntent?.let { base ->
            val intent = Intent(base)
            intent.putExtra(EXTRA_APPROVAL_CHOICE, CHOICE_APPROVE)
            PendingIntent.getBroadcast(context, id + 1, intent, PENDING_FLAGS)
        }
        val denyPending: PendingIntent? = approvalActionIntent?.let { base ->
            val intent = Intent(base)
            intent.putExtra(EXTRA_APPROVAL_CHOICE, CHOICE_DENY)
            PendingIntent.getBroadcast(context, id + 2, intent, PENDING_FLAGS)
        }
            val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .apply {
                if (approvePending != null && denyPending != null) {
                    addAction(
                        Action.Builder(
                            R.drawable.ic_stat_notification,
                            context.getString(R.string.notification_action_approve),
                            approvePending
                        ).build()
                    )
                    addAction(
                        Action.Builder(
                            R.drawable.ic_stat_notification,
                            context.getString(R.string.notification_action_deny),
                            denyPending
                        ).build()
                    )
                }
            }
            .build()
            try {
            notificationManager().notify(id, notification)
        } catch (_: SecurityException) {}
    }
}
