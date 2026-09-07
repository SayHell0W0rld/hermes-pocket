package com.hermes.mobile.webview

import com.hermes.mobile.data.HermesTab
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabWebViewPoolTest {
    private class FakeWebView(
        override val currentUrl: String = "https://example.com"
    ) : PooledWebView {
        var loaded = mutableListOf<String>()
        var stopped = 0
        var paused = 0
        var resumed = 0
        var destroyed = 0
        override var tabId: String? = null

        override fun load(url: String) {
            loaded.add(url)
        }

        override fun stopLoading() {
            stopped++
        }

        override fun pause() {
            paused++
        }

        override fun resume() {
            resumed++
        }

        override fun destroy() {
            destroyed++
        }
    }

    private fun tab(id: String) = HermesTab(
        sessionId = id,
        title = id,
        lastUrl = "https://example.com/session/$id",
        createdAt = 0L,
        lastActiveAt = 0L
    )

    @Test
    fun `restores paused webview without creating another`() {
        val created = mutableListOf<FakeWebView>()
        val pool = TabWebViewPool(2) { tab ->
            FakeWebView(tab.lastUrl).also(created::add)
        }

        val first = pool.acquire(tab("a"))
        pool.pauseCurrent()
        val restored = pool.acquire(tab("a"))

        assertThat(restored).isSameInstanceAs(first)
        assertThat(created).hasSize(1)
        assertThat(created.first().destroyed).isEqualTo(0)
    }

    @Test
    fun `evicts least recently used before creating third webview`() {
        val created = mutableListOf<FakeWebView>()
        val pool = TabWebViewPool(2) { tab ->
            FakeWebView(tab.lastUrl).also(created::add)
        }

        val first = pool.acquire(tab("a"))
        pool.pauseCurrent()
        pool.acquire(tab("b"))
        pool.pauseCurrent()
        val third = pool.acquire(tab("c"))

        assertThat(created).hasSize(3)
        assertThat(created.first().destroyed).isEqualTo(1)
        assertThat(created.first().stopped).isEqualTo(1)
        assertThat(created[1].destroyed).isEqualTo(0)
        assertThat(third.tabId).isEqualTo("c")
    }

    @Test
    fun `lowering max destroys excess paused webviews`() {
        val created = mutableListOf<FakeWebView>()
        val pool = TabWebViewPool(3) { tab ->
            FakeWebView(tab.lastUrl).also(created::add)
        }
        val a = pool.acquire(tab("a"))
        pool.pauseCurrent()
        pool.acquire(tab("b"))
        pool.pauseCurrent()
        pool.acquire(tab("c"))
        pool.pauseCurrent()
        pool.applyMaxHotCount(1)

        assertThat(created.first().destroyed).isEqualTo(1)
    }
}
