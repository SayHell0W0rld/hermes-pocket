package com.hermes.mobile.data

interface TabStore {
    fun loadTabs(): List<HermesTab>
    fun saveTabs(tabs: List<HermesTab>)
    fun loadActiveTabId(): String?
    fun saveActiveTabId(id: String?)
}

class InMemoryTabStore : TabStore {
    var tabs: List<HermesTab> = emptyList()
    var activeId: String? = null

    override fun loadTabs(): List<HermesTab> = tabs
    override fun saveTabs(tabs: List<HermesTab>) {
        this.tabs = tabs
    }
    override fun loadActiveTabId(): String? = activeId
    override fun saveActiveTabId(id: String?) {
        activeId = id
    }
}

class TabRepository(private val store: TabStore) {
    private var activeId: String? = store.loadActiveTabId()

    fun tabs(): List<HermesTab> = store.loadTabs()

    fun activeTab(): HermesTab? {
        val active = activeId ?: return null
        return store.loadTabs().firstOrNull { it.id == active }
    }

    fun upsert(tab: HermesTab): List<HermesTab> {
        val current = store.loadTabs()
        val updated = HermesTab(
            sessionId = tab.sessionId,
            title = tab.title,
            lastUrl = tab.lastUrl,
            scrollPosition = tab.scrollPosition,
            createdAt = current.firstOrNull { it.id == tab.id }?.createdAt ?: tab.createdAt,
            lastActiveAt = tab.lastActiveAt,
            isValid = tab.isValid
        )
        val next = (listOf(updated) + current.filterNot { it.id == tab.id }).take(MAX_TABS)
        store.saveTabs(next)
        activate(updated.id)
        return next
    }

    fun activate(id: String?): List<HermesTab> {
        activeId = id
        store.saveActiveTabId(id)
        if (id == null) return store.loadTabs()
        val now = System.currentTimeMillis()
        val next = store.loadTabs().map { if (it.id == id) it.copy(lastActiveAt = now) else it }
        store.saveTabs(next)
        return next
    }

    fun remove(id: String): List<HermesTab> {
        val next = store.loadTabs().filterNot { it.id == id }
        store.saveTabs(next)
        if (activeId == id) {
            activeId = next.firstOrNull()?.id
            store.saveActiveTabId(activeId)
        }
        return next
    }

    companion object {
        const val MAX_TABS = 5
    }
}
