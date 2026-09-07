package com.hermes.mobile.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(SettingsStore.PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store = SettingsStore(context.getSharedPreferences(SettingsStore.PREF_NAME, Context.MODE_PRIVATE))
    }

    @Test
    fun `hot webview count defaults to two and clamps values`() {
        assertThat(store.hotWebViewCount()).isEqualTo(2)
        store.setHotWebViewCount(0)
        assertThat(store.hotWebViewCount()).isEqualTo(1)
        store.setHotWebViewCount(4)
        assertThat(store.hotWebViewCount()).isEqualTo(4)
        store.setHotWebViewCount(6)
        assertThat(store.hotWebViewCount()).isEqualTo(5)
    }

    @Test
    fun `tailscale guard defaults to enabled and persists`() {
        assertThat(store.tailscaleGuardEnabled()).isTrue()
        store.setTailscaleGuardEnabled(false)
        assertThat(store.tailscaleGuardEnabled()).isFalse()
    }

    @Test
    fun `font zoom defaults to 100 and is clamped`() {
        assertThat(store.fontZoomPercent()).isEqualTo(100)
        store.setFontZoomPercent(130)
        assertThat(store.fontZoomPercent()).isEqualTo(130)
        store.setFontZoomPercent(180)
        assertThat(store.fontZoomPercent()).isEqualTo(150)
    }

    @Test
    fun `server url persists trimmed`() {
        store.setServerUrl(" https://example.com ")
        assertThat(store.serverUrl()).isEqualTo("https://example.com")
    }
}
