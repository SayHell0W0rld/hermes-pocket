package com.hermes.mobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class OnboardingActivity : Activity() {
    companion object {
        const val PREF_NAME = "hermes_config"
        const val PREF_KEY_SERVER_URL = "server_url"
        const val PREF_KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val PREF_KEY_HTTP_WARNING_ACKNOWLEDGED = "http_warning_acknowledged"
        const val PREF_KEY_LANG_PROMPTED = "lang_sync_prompted"
        const val PREF_KEY_LANG_CHOICE = "lang_sync_choice"
        const val PREF_KEY_THEME_PROMPTED = "theme_sync_prompted"
        const val PREF_KEY_THEME_CHOICE = "theme_sync_choice"
        const val PREF_KEY_TEXT_CHOICE = "text_zoom"
        const val PREF_KEY_NOTIF_PERMISSION_PROMPTED = "notif_permission_prompted"
        const val CHOICE_FOLLOW = "follow"
        const val CHOICE_KEEP = "keep"
        private const val NOTIFICATION_PERMISSION_REQUEST = 2001
        private const val TOTAL_STEPS = 7
        private const val EXAMPLE_URL = "https://my-agent.example.com/webui"

        fun shouldShow(prefs: android.content.SharedPreferences): Boolean {
            return !prefs.getBoolean(PREF_KEY_ONBOARDING_COMPLETE, false)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentStep = 0
    private var serverTestSucceeded = false
    private var testedServerDetail = ""
    private lateinit var stepText: TextView
    private lateinit var progressBars: List<View>
    private lateinit var steps: List<View>
    private lateinit var serverInput: EditText
    private lateinit var protocolHttps: RadioButton
    private lateinit var protocolHttp: RadioButton
    private lateinit var statusText: TextView
    private lateinit var textPreview: TextView
    private lateinit var backButton: Button
    private lateinit var nextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        stepText = findViewById(R.id.onboardingStepText)
        progressBars = listOf(
            findViewById(R.id.progressOne), findViewById(R.id.progressTwo),
            findViewById(R.id.progressThree), findViewById(R.id.progressFour),
            findViewById(R.id.progressFive), findViewById(R.id.progressSix),
            findViewById(R.id.progressSeven)
        )
        steps = listOf(
            findViewById(R.id.welcomeStep), findViewById(R.id.serverStep),
            findViewById(R.id.languageStep), findViewById(R.id.themeStep),
            findViewById(R.id.textStep), findViewById(R.id.notificationStep),
            findViewById(R.id.completeStep)
        )
        serverInput = findViewById(R.id.onboardingServerInput)
        protocolHttps = findViewById(R.id.onboardingProtocolHttps)
        protocolHttp = findViewById(R.id.onboardingProtocolHttp)
        statusText = findViewById(R.id.onboardingStatusText)
        textPreview = findViewById(R.id.textPreview)
        backButton = findViewById(R.id.backButton)
        nextButton = findViewById(R.id.nextButton)

        val existingUrl = prefs.getString(PREF_KEY_SERVER_URL, "").orEmpty()
        updateProtocolSelection(existingUrl.ifEmpty { EXAMPLE_URL })
        statusText.visibility = View.GONE
        findViewById<TextView>(R.id.languageSystemValue).text =
            getString(R.string.onboarding_lang_system, systemLanguageName())
        val themeName = if (isSystemNight()) {
            getString(R.string.theme_mode_dark)
        } else {
            getString(R.string.theme_mode_light)
        }
        findViewById<TextView>(R.id.themeSystemValue).text =
            getString(R.string.onboarding_theme_system, themeName)

        protocolHttps.setOnCheckedChangeListener { _, _ -> updateHttpsEducation() }
        protocolHttp.setOnCheckedChangeListener { _, _ -> updateHttpsEducation() }
        findViewById<Button>(R.id.onboardingTestButton).setOnClickListener { testConnection() }
        findViewById<Button>(R.id.onboardingSaveButton).setOnClickListener { saveServer() }
        findViewById<Button>(R.id.languageFollowButton).setOnClickListener {
            saveChoice(prefs, PREF_KEY_LANG_PROMPTED, PREF_KEY_LANG_CHOICE, CHOICE_FOLLOW)
        }
        findViewById<Button>(R.id.languageKeepButton).setOnClickListener {
            saveChoice(prefs, PREF_KEY_LANG_PROMPTED, PREF_KEY_LANG_CHOICE, CHOICE_KEEP)
        }
        findViewById<Button>(R.id.themeFollowButton).setOnClickListener {
            saveChoice(prefs, PREF_KEY_THEME_PROMPTED, PREF_KEY_THEME_CHOICE, CHOICE_FOLLOW)
        }
        findViewById<Button>(R.id.themeKeepButton).setOnClickListener {
            saveChoice(prefs, PREF_KEY_THEME_PROMPTED, PREF_KEY_THEME_CHOICE, CHOICE_KEEP)
        }
        findViewById<Button>(R.id.textSystemButton).setOnClickListener { saveTextChoice("system") }
        findViewById<Button>(R.id.text115Button).setOnClickListener { saveTextChoice("115") }
        findViewById<Button>(R.id.text130Button).setOnClickListener { saveTextChoice("130") }
        findViewById<Button>(R.id.enableNotificationsButton).setOnClickListener { requestNotificationPermission() }
        findViewById<Button>(R.id.notificationLaterButton).setOnClickListener {
            prefs.edit().putBoolean(PREF_KEY_NOTIF_PERMISSION_PROMPTED, true).apply()
            showStep(6)
        }
        backButton.setOnClickListener { showStep(currentStep - 1) }
        nextButton.setOnClickListener {
            if (currentStep == TOTAL_STEPS - 1) {
                completeOnboarding()
                finish()
            } else if (currentStep == 1) {
                advanceFromServerStep()
            } else {
                showStep(currentStep + 1)
            }
        }
        findViewById<Button>(R.id.skipButton).setOnClickListener {
            completeOnboarding()
            finish()
        }
        showStep(0)
    }

    fun systemLanguageName(): String = Locale.getDefault().getDisplayName(Locale.getDefault())

    fun isSystemNight(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun showStep(step: Int) {
        currentStep = step.coerceIn(0, TOTAL_STEPS - 1)
        steps.forEachIndexed { index, view ->
            view.visibility = if (index == currentStep) View.VISIBLE else View.GONE
        }
        progressBars.forEachIndexed { index, bar ->
            bar.setBackgroundColor(
                getColor(if (index <= currentStep) R.color.hermes_accent else R.color.hermes_border)
            )
        }
        stepText.text = getString(R.string.onboarding_step, currentStep + 1, TOTAL_STEPS)
        backButton.visibility = if (currentStep == 0) View.GONE else View.VISIBLE
        nextButton.text = getString(
            if (currentStep == TOTAL_STEPS - 1) R.string.onboarding_finish else R.string.onboarding_next
        )
        if (currentStep == 1) {
            findViewById<Button>(R.id.skipButton).visibility = View.GONE
            statusText.visibility = if (serverTestSucceeded) View.VISIBLE else View.GONE
            if (serverTestSucceeded) statusText.text = getString(R.string.server_reachable, testedServerDetail)
        } else {
            findViewById<Button>(R.id.skipButton).visibility = View.VISIBLE
        }
        if (currentStep == 5) textPreview.textSize = previewSize()
    }

    private fun advanceFromServerStep() {
        val url = normalizeUrl(serverInput.text.toString())
        if (!serverTestSucceeded || url != testedServerDetail) {
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.onboarding_server_test_required)
            testConnection()
            return
        }
        saveServer()
    }

    private fun previewSize(): Float {
        val choice = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(PREF_KEY_TEXT_CHOICE, "system").orEmpty()
        return when (choice) {
            "115" -> 16.1f
            "130" -> 18.2f
            else -> 14f
        }
    }

    private fun saveTextChoice(choice: String) {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY_TEXT_CHOICE, choice).apply()
        textPreview.textSize = previewSize()
    }

    private fun saveChoice(
        prefs: android.content.SharedPreferences,
        promptedKey: String,
        choiceKey: String,
        choice: String
    ) {
        prefs.edit().putBoolean(promptedKey, true).putString(choiceKey, choice).apply()
        showStep(currentStep + 1)
    }

    private fun isHttpsSelected(): Boolean = protocolHttps.isChecked

    private fun updateProtocolSelection(url: String) {
        val secure = url.startsWith("https://", ignoreCase = true)
        protocolHttps.isChecked = secure
        protocolHttp.isChecked = !secure
        val host = url.trim()
            .removePrefix("https://").removePrefix("HTTPS://")
            .removePrefix("http://").removePrefix("HTTP://")
        serverInput.setText(host)
        serverInput.setSelection(serverInput.text.length)
        updateHttpsEducation()
    }

    private fun updateHttpsEducation() {
        if (isHttpsSelected()) {
            protocolHttps.setTextColor(getColor(R.color.hermes_accent))
            protocolHttp.setTextColor(getColor(R.color.hermes_muted))
        } else {
            protocolHttps.setTextColor(getColor(R.color.hermes_muted))
            protocolHttp.setTextColor(getColor(R.color.hermes_warning))
        }
    }

    private fun saveServer() {
        val url = normalizeUrl(serverInput.text.toString())
        if (url.isEmpty()) {
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.enter_server_url)
            return
        }
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!isHttpsSelected() && !prefs.getBoolean(PREF_KEY_HTTP_WARNING_ACKNOWLEDGED, false)) {
            showHttpWarning(url)
            return
        }
        prefs.edit().putString(PREF_KEY_SERVER_URL, url).apply()
        statusText.visibility = View.VISIBLE
        statusText.text = getString(R.string.onboarding_server_saved)
        showStep(2)
    }

    private fun showHttpWarning(url: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.http_warning_title))
            .setMessage(getString(R.string.http_warning_message))
            .setPositiveButton(getString(R.string.http_warning_save_anyway)) { _, _ ->
                getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_KEY_HTTP_WARNING_ACKNOWLEDGED, true).apply()
                saveServer()
            }
            .setNegativeButton(getString(R.string.http_warning_use_recommended)) { _, _ ->
                protocolHttps.isChecked = true
                protocolHttp.isChecked = false
            }
            .show()
    }

    private fun requestNotificationPermission() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
            return
        }
        prefs.edit().putBoolean(PREF_KEY_NOTIF_PERMISSION_PROMPTED, true).apply()
        showStep(6)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_KEY_NOTIF_PERMISSION_PROMPTED, true).apply()
        showStep(6)
    }

    private fun completeOnboarding() {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_KEY_ONBOARDING_COMPLETE, true).apply()
        setResult(RESULT_OK)
    }

    private fun testConnection() {
        val url = normalizeUrl(serverInput.text.toString())
        if (url.isEmpty()) {
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.enter_server_url)
            return
        }
        val testButton = findViewById<Button>(R.id.onboardingTestButton)
        testButton.isEnabled = false
        statusText.visibility = View.VISIBLE
        statusText.text = getString(R.string.testing_connection)
        Thread {
            var reachable = false
            var detail = ""
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                val code = connection.responseCode
                detail = "HTTP $code"
                reachable = code > 0
                connection.disconnect()
            } catch (e: Exception) {
                detail = e.message ?: getString(R.string.unknown_error)
            }
            mainHandler.post {
                testButton.isEnabled = true
                serverTestSucceeded = reachable
                if (reachable) testedServerDetail = normalizeUrl(serverInput.text.toString())
                statusText.text = if (reachable) {
                    getString(R.string.server_reachable, detail)
                } else {
                    getString(R.string.server_unreachable, detail)
                }
            }
        }.start()
    }

    private fun normalizeUrl(raw: String): String {
        var url = raw.trim()
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "${if (isHttpsSelected()) "https" else "http"}://$url"
        }
        return url
    }

    override fun onBackPressed() {
        if (currentStep > 0) {
            showStep(currentStep - 1)
        }
    }
}
