package com.hermes.mobile.webview

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object HermesWebViewConfigurator {
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(view: WebView, textZoom: Int) {
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.forceDark = WebSettings.FORCE_DARK_OFF
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportZoom(false)
        settings.textZoom = textZoom.coerceIn(MIN_FONT_ZOOM_PERCENT, MAX_FONT_ZOOM_PERCENT)

        val defaultUA = settings.userAgentString
        settings.userAgentString = "$defaultUA HermesApp/1.0"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
    }

    const val MIN_FONT_ZOOM_PERCENT = 100
    const val MAX_FONT_ZOOM_PERCENT = 150
}
