package com.hermes.mobile

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackgroundPollWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("hermes_config", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "")?.trim().orEmpty()
        if (serverUrl.isEmpty()) return@withContext Result.success()

        refreshCronMappings(serverUrl)

        val pending = fetchJson("$serverUrl/api/approval/pending")
        if (pending == null) {
            return@withContext if (fetchJson("$serverUrl/api/approval/pending") == null) {
                Result.retry()
            } else {
                Result.success()
            }
        }

        val array = pending.optJSONArray("pending")
            ?: JSONArray(pending.toString()).takeIf { it.length() > 0 }
        if (array != null && array.length() > 0) {
            val first = array.optJSONObject(0)
            val sessionId = first?.optString("session_id").orEmpty()
            val approvalId = first?.optString("approval_id").orEmpty()
            val description = first?.optString("description")
                ?.takeIf { it.isNotBlank() }
                ?: applicationContext.getString(R.string.notification_approval_body)
            NotificationHelper(applicationContext).notifyApproval("", description, sessionId, approvalId)
        }
        Result.success()
    }

    private suspend fun refreshCronMappings(serverUrl: String) {
        val payload = fetchJson("$serverUrl/api/crons/recent?since=0") ?: return
        val completions = payload.optJSONArray("completions") ?: return
        val helper = NotificationHelper(applicationContext)
        for (index in 0 until completions.length()) {
            val completion = completions.optJSONObject(index) ?: continue
            val sessionId = completion.optString("session_id")
            val jobId = completion.optString("job_id")
            if (sessionId.isNotBlank() && jobId.isNotBlank()) {
                helper.rememberCronMapping(sessionId, jobId)
            }
        }
    }

    private suspend fun fetchJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.connect()
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
