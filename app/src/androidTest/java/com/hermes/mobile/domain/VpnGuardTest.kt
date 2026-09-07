package com.hermes.mobile.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnGuardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun vpnTransportQueryDoesNotCrashWithoutVpn() {
        assertThat(VpnGuard.isVpnTransportActive(context)).isFalse()
    }

    @Test
    fun tailscaleDetectorIsolatesGuardedHosts() {
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("https://agent.example.ts.net")).isTrue()
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("https://example.com")).isFalse()
    }
}
