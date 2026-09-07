package com.hermes.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import com.hermes.mobile.data.SettingsStore

class SettingsActivity : Activity() {

    companion object {
        private const val PREF_NAME = "hermes_config"
        private const val PREF_KEY_HTTP_WARNING_ACKNOWLEDGED = "http_warning_acknowledged"
        private const val EXAMPLE_URL = "https://my-agent.example.com/webui"
    }

    private lateinit var serverUrlInput: EditText
    private lateinit var protocolHttps: RadioButton
    private lateinit var protocolHttp: RadioButton
    private lateinit var statusText: TextView
    private lateinit var testButton: Button
    private lateinit var saveButton: Button
    private lateinit var httpsEducationCard: LinearLayout
    private lateinit var httpsEducationTitle: TextView
    private lateinit var httpsEducationSummary: TextView
    private lateinit var httpsEducationDetails: LinearLayout
    private lateinit var httpsEducationFooter: TextView
    private lateinit var hotWebViewOne: RadioButton
    private lateinit var hotWebViewTwo: RadioButton
    private lateinit var hotWebViewThree: RadioButton
    private lateinit var tailscaleGuardToggle: CheckBox
    private lateinit var fontZoom100: RadioButton
    private lateinit var fontZoom110: RadioButton
    private lateinit var fontZoom130: RadioButton
    private lateinit var fontZoom150: RadioButton
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settingsStore by lazy {
        SettingsStore(getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        serverUrlInput = findViewById(R.id.serverUrlInput)
        protocolHttps = findViewById(R.id.protocolHttps)
        protocolHttp = findViewById(R.id.protocolHttp)
        statusText = findViewById(R.id.statusText)
        testButton = findViewById(R.id.testButton)
        saveButton = findViewById(R.id.saveButton)
        httpsEducationCard = findViewById(R.id.httpsEducationCard)
        httpsEducationTitle = findViewById(R.id.httpsEducationTitle)
        httpsEducationSummary = findViewById(R.id.httpsEducationSummary)
        httpsEducationDetails = findViewById(R.id.httpsEducationDetails)
        httpsEducationFooter = findViewById(R.id.httpsEducationFooter)
        hotWebViewOne = findViewById(R.id.hotWebViewOne)
        hotWebViewTwo = findViewById(R.id.hotWebViewTwo)
        hotWebViewThree = findViewById(R.id.hotWebViewThree)
        tailscaleGuardToggle = findViewById(R.id.tailscaleGuardToggle)
        fontZoom100 = findViewById(R.id.fontZoom100)
        fontZoom110 = findViewById(R.id.fontZoom110)
        fontZoom130 = findViewById(R.id.fontZoom130)
        fontZoom150 = findViewById(R.id.fontZoom150)

        val savedUrl = settingsStore.serverUrl()
        updateProtocolSelection(savedUrl.ifEmpty { EXAMPLE_URL })

        httpsEducationCard.setOnClickListener {
            val isHttps = isHttpsSelected()
            httpsEducationDetails.visibility =
                if (httpsEducationDetails.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (isHttps) {
                httpsEducationSummary.visibility =
                    if (httpsEducationDetails.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        protocolHttps.setOnCheckedChangeListener { _, _ -> updateHttpsEducation(buildServerUrl()) }
        protocolHttp.setOnCheckedChangeListener { _, _ -> updateHttpsEducation(buildServerUrl()) }
        hotWebViewOne.setOnCheckedChangeListener { _, checked -> if (checked) settingsStore.setHotWebViewCount(1) }
        hotWebViewTwo.setOnCheckedChangeListener { _, checked -> if (checked) settingsStore.setHotWebViewCount(2) }
        hotWebViewThree.setOnCheckedChangeListener { _, checked -> if (checked) settingsStore.setHotWebViewCount(3) }
        tailscaleGuardToggle.setOnCheckedChangeListener { _, checked ->
            settingsStore.setTailscaleGuardEnabled(checked)
        }
        fontZoom100.setOnCheckedChangeListener { _, checked -> if (checked) settingsStore.setFontZoomPercent(100) }
        fontZoom110.setOnCheckedChangeListener { _, checked -> if (checked) settingsStore.setFontZoomPercent(110) }
        fontZoom130.setOnCheckedChangeListener { _, checked -> if (checked) settingsStore.setFontZoomPercent(130) }
        fontZoom150.setOnCheckedChangeListener { _, checked -> if (checked) settingsStore.setFontZoomPercent(150) }
        testButton.setOnClickListener { testConnection() }
        saveButton.setOnClickListener { saveAndFinish() }

        when (settingsStore.hotWebViewCount()) {
            1 -> hotWebViewOne.isChecked = true
            3 -> hotWebViewThree.isChecked = true
            else -> hotWebViewTwo.isChecked = true
        }
        tailscaleGuardToggle.isChecked = settingsStore.tailscaleGuardEnabled()
        when (settingsStore.fontZoomPercent()) {
            110 -> fontZoom110.isChecked = true
            130 -> fontZoom130.isChecked = true
            150 -> fontZoom150.isChecked = true
            else -> fontZoom100.isChecked = true
        }
    }

    private fun isHttpsSelected(): Boolean = protocolHttps.isChecked

    private fun updateProtocolSelection(url: String) {
        val secure = url.startsWith("https://", ignoreCase = true)
        protocolHttps.isChecked = secure
        protocolHttp.isChecked = !secure
        val host = url.trim()
            .removePrefix("https://").removePrefix("HTTPS://")
            .removePrefix("http://").removePrefix("HTTP://")
        serverUrlInput.setText(host)
        serverUrlInput.setSelection(serverUrlInput.text.length)
        updateHttpsEducation(buildServerUrl())
    }

    private fun buildServerUrl(): String {
        val host = serverUrlInput.text.toString().trim()
        if (host.isEmpty()) return ""
        val scheme = if (isHttpsSelected()) "https" else "http"
        return "$scheme://$host"
    }


    private fun isHttps(url: String): Boolean = url.startsWith("https://")

    private fun updateHttpsEducation(url: String) {
        if (url.isEmpty()) {
            httpsEducationCard.visibility = View.GONE
            return
        }
        httpsEducationCard.visibility = View.VISIBLE
        if (isHttpsSelected()) {
            httpsEducationCard.background = getDrawable(R.drawable.bg_https_success)
            httpsEducationTitle.visibility = View.GONE
            httpsEducationSummary.visibility = View.VISIBLE
            httpsEducationSummary.setTextColor(getColor(R.color.hermes_success))
            httpsEducationDetails.visibility = View.GONE
            httpsEducationFooter.visibility = View.GONE
        } else {
            httpsEducationCard.background = getDrawable(R.drawable.bg_https_warning)
            httpsEducationTitle.visibility = View.VISIBLE
            httpsEducationSummary.visibility = View.GONE
            httpsEducationDetails.visibility = View.VISIBLE
            httpsEducationFooter.visibility = View.VISIBLE
            httpsEducationFooter.setTextColor(getColor(R.color.hermes_warning))
        }
    }

    private fun saveAndFinish() {
        val url = buildServerUrl()
        if (url.isEmpty()) {
            statusText.text = getString(R.string.enter_server_url)
            statusText.visibility = View.VISIBLE
            return
        }
        updateHttpsEducation(url)
        if (!isHttpsSelected() &&
            !getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_KEY_HTTP_WARNING_ACKNOWLEDGED, false)
        ) {
            showHttpWarning(url)
            return
        }
        persistUrl(url)
    }

    private fun showHttpWarning(url: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_http_warning, null)
        val doNotAskAgain = dialogView.findViewById<CheckBox>(R.id.httpWarningDoNotAsk)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.http_warning_title))
            .setMessage(getString(R.string.http_warning_message))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.http_warning_save_anyway)) { _, _ ->
                if (doNotAskAgain.isChecked) {
                    getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_KEY_HTTP_WARNING_ACKNOWLEDGED, true)
                        .apply()
                }
                persistUrl(url)
            }
            .setNegativeButton(getString(R.string.http_warning_use_recommended)) { _, _ ->
                protocolHttps.isChecked = true
                protocolHttp.isChecked = false
                updateHttpsEducation(buildServerUrl())
            }
            .show()
    }

    private fun persistUrl(url: String) {
        settingsStore.setServerUrl(url)
        setResult(RESULT_OK)
        finish()
    }

    @SuppressLint("SetTextI18n")
    private fun testConnection() {
        val url = buildServerUrl()
        if (url.isEmpty()) {
            statusText.text = getString(R.string.enter_server_url)
            statusText.visibility = View.VISIBLE
            return
        }
        updateHttpsEducation(url)
        testButton.isEnabled = false
        statusText.text = getString(R.string.testing_connection)
        statusText.visibility = View.VISIBLE
        Thread {
            var reachable = false
            var detail = ""
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.instanceFollowRedirects = false
                conn.requestMethod = "GET"
                val code = conn.responseCode
                detail = "HTTP $code"
                reachable = code > 0
                conn.disconnect()
            } catch (e: Exception) {
                detail = e.message ?: getString(R.string.unknown_error)
            }
            mainHandler.post {
                testButton.isEnabled = true
                val msg = if (reachable) {
                    statusText.setTextColor(getColor(R.color.hermes_text))
                    getString(R.string.server_reachable, detail)
                } else {
                    statusText.setTextColor(getColor(R.color.hermes_error))
                    getString(R.string.server_unreachable, detail)
                }
                statusText.text = msg
                statusText.visibility = View.VISIBLE
            }
        }.start()
    }
}
