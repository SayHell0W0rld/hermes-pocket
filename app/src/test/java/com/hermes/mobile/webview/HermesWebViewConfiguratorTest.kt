package com.hermes.mobile.webview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HermesWebViewConfiguratorTest {
    @Test
    fun fontZoomRangeIsBounded() {
        assertThat(HermesWebViewConfigurator.MIN_FONT_ZOOM_PERCENT).isEqualTo(100)
        assertThat(HermesWebViewConfigurator.MAX_FONT_ZOOM_PERCENT).isEqualTo(150)
    }
}
