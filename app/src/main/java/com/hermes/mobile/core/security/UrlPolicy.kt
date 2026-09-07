package com.hermes.mobile.core.security

import java.net.URI

object UrlPolicy {
    fun originRule(serverUrl: String): String {
        if (serverUrl.isBlank()) return ""
        val uri = runCatching { URI(serverUrl.trim()) }.getOrNull() ?: return ""
        val scheme = uri.scheme?.lowercase() ?: return ""
        val host = uri.host?.lowercase() ?: return ""
        if (scheme != "http" && scheme != "https") return ""
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port == -1) defaultPort else uri.port
        return if (port == defaultPort) "$scheme://$host" else "$scheme://$host:$port"
    }

    fun isSameOrigin(url: String, serverUrl: String): Boolean {
        val expected = originRule(serverUrl)
        if (expected.isEmpty()) return false
        return originRule(url) == expected
    }
}
