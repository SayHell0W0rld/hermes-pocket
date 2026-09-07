package com.hermes.mobile.domain

object TabRoute {
    private val SESSION_PATTERN = Regex("""/session/([^/?#]+)""")

    fun sessionIdFrom(url: String): String? {
        if (url.isBlank()) return null
        return runCatching {
            SESSION_PATTERN.find(url)?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() }
                ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
        }.getOrNull()
    }
}
