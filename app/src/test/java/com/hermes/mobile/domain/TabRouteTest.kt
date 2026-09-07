package com.hermes.mobile.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabRouteTest {
    @Test
    fun `extracts session ids`() {
        assertThat(TabRoute.sessionIdFrom("https://example.com/session/abc?x=1#hash")).isEqualTo("abc")
        assertThat(TabRoute.sessionIdFrom("/webui/session/encoded%20id")).isEqualTo("encoded id")
    }

    @Test
    fun `rejects non session urls and blank ids`() {
        assertThat(TabRoute.sessionIdFrom("https://example.com/")).isNull()
        assertThat(TabRoute.sessionIdFrom("https://example.com/session/")).isNull()
        assertThat(TabRoute.sessionIdFrom("")).isNull()
    }
}
