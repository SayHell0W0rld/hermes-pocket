package com.hermes.mobile.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesTabStore(private val prefs: SharedPreferences) : TabStore {
    override fun loadTabs(): List<HermesTab> {
        val raw = prefs.getString(PREF_KEY_TABS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                HermesTab(
                    sessionId = item.optString("sessionId"),
                    title = item.optString("title"),
                    lastUrl = item.optString("lastUrl"),
                    scrollPosition = item.optInt("scrollPosition", 0),
                    createdAt = item.optLong("createdAt", 0L),
                    lastActiveAt = item.optLong("lastActiveAt", 0L),
                    isValid = item.optBoolean("isValid", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun saveTabs(tabs: List<HermesTab>) {
        val array = JSONArray()
        tabs.forEach { tab ->
            array.put(
                JSONObject().apply {
                    put("sessionId", tab.sessionId)
                    put("title", tab.title)
                    put("lastUrl", tab.lastUrl)
                    put("scrollPosition", tab.scrollPosition)
                    put("createdAt", tab.createdAt)
                    put("lastActiveAt", tab.lastActiveAt)
                    put("isValid", tab.isValid)
                }
            )
        }
        prefs.edit().putString(PREF_KEY_TABS, array.toString()).apply()
    }

    override fun loadActiveTabId(): String? {
        val value = prefs.getString(PREF_KEY_ACTIVE_TAB_ID, null) ?: return null
        return value.ifBlank { null }
    }

    override fun saveActiveTabId(id: String?) {
        prefs.edit().putString(PREF_KEY_ACTIVE_TAB_ID, id).apply()
    }

    companion object {
        const val PREF_KEY_TABS = "tabs"
        const val PREF_KEY_ACTIVE_TAB_ID = "active_tab_id"
    }
}
