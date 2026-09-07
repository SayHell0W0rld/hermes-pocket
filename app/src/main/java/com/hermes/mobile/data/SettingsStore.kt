package com.hermes.mobile.data

import android.content.SharedPreferences

class SettingsStore(private val prefs: SharedPreferences) {
    fun serverUrl(): String = prefs.getString(PREF_KEY_SERVER_URL, "")?.trim().orEmpty()

    fun setServerUrl(value: String) {
        prefs.edit().putString(PREF_KEY_SERVER_URL, value.trim()).apply()
    }

    fun hotWebViewCount(): Int = prefs.getInt(PREF_KEY_HOT_WEBVIEW_COUNT, DEFAULT_HOT_WEBVIEW_COUNT).coerceIn(1, 5)

    fun setHotWebViewCount(value: Int) {
        prefs.edit().putInt(PREF_KEY_HOT_WEBVIEW_COUNT, value.coerceIn(1, 5)).apply()
    }

    fun tailscaleGuardEnabled(): Boolean = prefs.getBoolean(PREF_KEY_TAILSCALE_GUARD_ENABLED, true)

    fun setTailscaleGuardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_TAILSCALE_GUARD_ENABLED, enabled).apply()
    }

    fun fontZoomPercent(): Int = prefs.getInt(PREF_KEY_FONT_ZOOM_PERCENT, DEFAULT_FONT_ZOOM_PERCENT)

    fun setFontZoomPercent(value: Int) {
        prefs.edit().putInt(PREF_KEY_FONT_ZOOM_PERCENT, value.coerceIn(100, 150)).apply()
    }

    companion object {
        const val PREF_NAME = "hermes_config"
        const val PREF_KEY_SERVER_URL = "server_url"
        const val PREF_KEY_HOT_WEBVIEW_COUNT = "hot_webview_count"
        const val PREF_KEY_TAILSCALE_GUARD_ENABLED = "tailscale_guard_enabled"
        const val PREF_KEY_FONT_ZOOM_PERCENT = "font_zoom_percent"
        const val DEFAULT_HOT_WEBVIEW_COUNT = 2
        const val DEFAULT_FONT_ZOOM_PERCENT = 100
    }
}
