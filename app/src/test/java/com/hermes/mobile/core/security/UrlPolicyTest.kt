package com.hermes.mobile.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlPolicyTest {
    @Test
    fun `default ports are normalized`() {
        assertThat(UrlPolicy.originRule("https://example.com/webui")).isEqualTo("https://example.com")
        assertThat(UrlPolicy.originRule("https://example.com:443")).isEqualTo("https://example.com")
        assertThat(UrlPolicy.originRule("http://example.com:80/x")).isEqualTo("http://example.com")
    }

    @Test
    fun `explicit ports are retained`() {
        assertThat(UrlPolicy.originRule("https://example.com:8443/webui")).isEqualTo("https://example.com:8443")
    }

    @Test
    fun `same origin compares scheme host and port`() {
        val serverUrl = "https://example.com:8443/webui"
        assertThat(UrlPolicy.isSameOrigin("https://example.com:8443/session/abc", serverUrl)).isTrue()
        assertThat(UrlPolicy.isSameOrigin("http://example.com:8443/session/abc", serverUrl)).isFalse()
        assertThat(UrlPolicy.isSameOrigin("https://example.com/session/abc", serverUrl)).isFalse()
        assertThat(UrlPolicy.isSameOrigin("https://evil.com:8443", serverUrl)).isFalse()
    }

    @Test
    fun `invalid urls are rejected`() {
        assertThat(UrlPolicy.originRule("")).isEmpty()
        assertThat(UrlPolicy.originRule("notaurl")).isEmpty()
        assertThat(UrlPolicy.originRule("ftp://example.com")).isEmpty()
        assertThat(UrlPolicy.isSameOrigin("https://example.com", "")).isFalse()
    }
}
