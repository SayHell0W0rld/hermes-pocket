package com.hermes.mobile.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabRepositoryTest {
    private fun tab(id: String, activeAt: Long = 0L, url: String = "https://example.com/$id") =
        HermesTab(
            sessionId = if (id == "home") "" else id,
            title = "Tab $id",
            lastUrl = url,
            createdAt = 0L,
            lastActiveAt = activeAt
        )

    @Test
    fun `upserts by id and moves existing tab to front`() {
        val repository = TabRepository(InMemoryTabStore())
        repository.upsert(tab("a", activeAt = 1L))
        repository.upsert(tab("b", activeAt = 2L))
        assertThat(repository.tabs().map { it.id }).containsExactly("b", "a").inOrder()

        repository.upsert(tab("a", activeAt = 2L))
        assertThat(repository.tabs().map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(repository.tabs().first().lastActiveAt).isGreaterThan(2L)
    }

    @Test
    fun `sixth tab evicts oldest inactive tab`() {
        val repository = TabRepository(InMemoryTabStore())
        repeat(4) { index -> repository.upsert(tab(index.toString(), activeAt = index.toLong())) }
        repository.activate("3")
        repository.upsert(tab("4"))
        repository.upsert(tab("5"))

        assertThat(repository.tabs().map { it.id }).containsExactly("5", "4", "3", "2", "1").inOrder()
        assertThat(repository.tabs().none { it.id == "0" }).isTrue()
    }

    @Test
    fun `activate persists active tab`() {
        val store = InMemoryTabStore()
        val repository = TabRepository(store)
        repository.upsert(tab("a"))
        repository.upsert(tab("b"))
        repository.activate("a")

        assertThat(store.activeId).isEqualTo("a")
        assertThat(repository.activeTab()?.id).isEqualTo("a")
    }

    @Test
    fun `remove clears active tab when needed`() {
        val store = InMemoryTabStore()
        val repository = TabRepository(store)
        repository.upsert(tab("a"))
        repository.upsert(tab("b"))
        repository.activate("a")
        repository.remove("a")

        assertThat(repository.tabs().map { it.id }).containsExactly("b")
        assertThat(store.activeId).isEqualTo("b")
    }
}
