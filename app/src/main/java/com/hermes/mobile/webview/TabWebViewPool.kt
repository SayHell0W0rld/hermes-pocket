package com.hermes.mobile.webview

import com.hermes.mobile.data.HermesTab

interface PooledWebView {
    var tabId: String?
    val currentUrl: String?
    fun load(url: String)
    fun stopLoading()
    fun pause()
    fun resume()
    fun destroy()
}

class TabWebViewPool(
    maxHotCount: Int,
    private val factory: (HermesTab) -> PooledWebView
) {
    private var maxHotCount = maxHotCount.coerceIn(1, 5)
    private var active: PooledWebView? = null
    private val paused = linkedMapOf<String, PooledWebView>()

    fun acquire(tab: HermesTab): PooledWebView {
        active?.let { current ->
            if (current.tabId == tab.id) return current
            pauseCurrent()
        }

        paused.remove(tab.id)?.let { restored ->
            restored.resume()
            active = restored
            trimIfNeeded()
            return restored
        }

        evictForNewEntry()
        val created = factory(tab).apply {
            tabId = tab.id
            load(tab.lastUrl)
            resume()
        }
        active = created
        return created
    }

    fun pauseCurrent(): PooledWebView? {
        val current = active ?: return null
        current.stopLoading()
        current.pause()
        val id = current.tabId ?: return null
        paused[id] = current
        active = null
        return current
    }

    fun applyMaxHotCount(value: Int) {
        maxHotCount = value.coerceIn(1, 3)
        trimIfNeeded()
    }

    fun destroyAll() {
        pauseCurrent()?.destroy()
        paused.values.toList().forEach { it.destroy() }
        paused.clear()
        active = null
    }

    private fun evictForNewEntry() {
        val total = paused.size + if (active == null) 0 else 1
        if (total < maxHotCount) return
        val evicted = paused.remove(paused.keys.first()) ?: return
        evicted.destroy()
    }

    private fun trimIfNeeded() {
        val total = paused.size + if (active == null) 0 else 1
        var excess = total - maxHotCount
        while (excess > 0) {
            val evicted = paused.remove(paused.keys.first()) ?: break
            evicted.destroy()
            excess--
        }
    }
}
