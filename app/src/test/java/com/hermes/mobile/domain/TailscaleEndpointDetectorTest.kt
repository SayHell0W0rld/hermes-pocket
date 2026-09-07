package com.hermes.mobile.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TailscaleEndpointDetectorTest {
    @Test
    fun `detects tailscale magicdns domains`() {
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("https://my-agent.example.ts.net/webui")).isTrue()
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("https://example.ts.net.evil.com")).isFalse()
    }

    @Test
    fun `detects tailscale cgnat range`() {
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("http://100.64.0.1:8080")).isTrue()
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("http://100.127.255.254")).isTrue()
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("http://100.128.0.1")).isFalse()
    }

    @Test
    fun `detects tailscale ipv6 prefix`() {
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("http://[fd7a:115c:a1e0::1]:8080")).isTrue()
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("http://[fd00::1]:8080")).isFalse()
    }

    @Test
    fun `rejects ordinary hosts and invalid input`() {
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("https://example.com")).isFalse()
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("")).isFalse()
        assertThat(TailscaleEndpointDetector.isTailscaleUrl("notaurl")).isFalse()
    }
}
