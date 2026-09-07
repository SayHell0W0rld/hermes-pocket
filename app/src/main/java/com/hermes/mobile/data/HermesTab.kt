package com.hermes.mobile.data

import java.net.URI

data class HermesTab(
    val sessionId: String,
    val title: String,
    val lastUrl: String,
    val scrollPosition: Int = 0,
    val createdAt: Long,
    val lastActiveAt: Long,
    val isValid: Boolean = true
) {
    val id: String
        get() = sessionId.ifBlank { "home:${normalizedBase(lastUrl)}" }

    private fun normalizedBase(url: String): String {
        return try {
            val uri = URI(url)
            URI(uri.scheme, uri.authority, uri.path, null, null).toString()
        } catch (_: Exception) {
            url
        }
    }
}
