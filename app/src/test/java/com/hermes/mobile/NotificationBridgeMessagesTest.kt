package com.hermes.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationBridgeMessagesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun loadBridge(): String {
        return context.assets.open("notification_bridge.js").bufferedReader().use { it.readText() }
    }

    @Test
    fun `bridge uses contract types instead of notification helper method names`() {
        val source = loadBridge()
        assertThat(source).doesNotContain("callNative('notifyError'")
        assertThat(source).doesNotContain("callNative('notifyTaskComplete'")
        assertThat(source).contains("callNative('error'")
        assertThat(source).contains("callNative('task_complete'")
    }

    @Test
    fun `bridge removes floating tab UI and legacy storage key`() {
        val source = loadBridge()
        assertThat(source).doesNotContain("hermes-session-tabbar")
        assertThat(source).doesNotContain("hermes-session-tabs")
    }
}
