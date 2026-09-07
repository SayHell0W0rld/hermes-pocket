package com.hermes.mobile.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HermesTabTest {
    @Test
    fun homeTabIdUsesNormalizedUrlSoNewChatsRemainSeparateTabs() {
        val base = HermesTab(
            sessionId = "",
            title = "Home",
            lastUrl = "https://example.com/",
            createdAt = 1L,
            lastActiveAt = 1L
        )
        val newChat = base.copy(lastUrl = "https://example.com/?action=new-chat")

        val hash = newChat.copy(lastUrl = "https://example.com/#draft")
        assertThat(base.id).isEqualTo(newChat.id)
        assertThat(base.id).isEqualTo(hash.id)
        assertThat(base.id).startsWith("home:")
    }
}
