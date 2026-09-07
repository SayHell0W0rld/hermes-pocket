package com.hermes.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import org.json.JSONObject
import com.hermes.mobile.core.security.UrlPolicy
import com.hermes.mobile.data.SettingsStore
import com.hermes.mobile.data.HermesTab
import com.hermes.mobile.data.SharedPreferencesTabStore
import com.hermes.mobile.data.TabRepository
import com.hermes.mobile.domain.TailscaleEndpointDetector
import com.hermes.mobile.domain.TabRoute
import com.hermes.mobile.domain.VpnGuard
import com.hermes.mobile.ui.TabBarView
import com.hermes.mobile.webview.HermesWebViewConfigurator
import com.hermes.mobile.webview.PooledWebView
import com.hermes.mobile.webview.TabWebViewPool



class MainActivity : Activity() {

    companion object {
        private const val PREF_NAME = "hermes_config"

        // Language/theme sync preferences (mirrored by OnboardingActivity).
        private const val PREF_KEY_LANG_PROMPTED = "lang_sync_prompted"
        private const val PREF_KEY_LANG_CHOICE = "lang_sync_choice"
        private const val PREF_KEY_THEME_PROMPTED = "theme_sync_prompted"
        private const val PREF_KEY_THEME_CHOICE = "theme_sync_choice"
        private const val PREF_KEY_TEXT_CHOICE = "text_zoom"
        private const val PREF_KEY_NOTIF_PERMISSION_PROMPTED = "notif_permission_prompted"
        private const val CHOICE_FOLLOW = "follow"
        private const val CHOICE_KEEP = "keep"

        private const val FILE_CHOOSER_REQUEST = 1001
        private const val SETTINGS_REQUEST = 1002
        private const val ONBOARDING_REQUEST = 1003
        private const val NOTIFICATION_PERMISSION_REQUEST = 2001
        private const val TRIPLE_TAP_WINDOW_MS = 600L
        private const val EXTRA_NEW_SESSION = "com.hermes.mobile.extra.NEW_SESSION"
        private const val VPN_POLL_INTERVAL_MS = 1000L
        private const val VPN_FALLBACK_DELAY_MS = 10000L
        private const val VPN_GUARD_TIMEOUT_MS = 60000L

        private const val SYSTEM_THEME_PATCH = """
            try {
              if (localStorage.getItem('hermes-theme') === 'system') {
                var hermesMq = window.matchMedia('(prefers-color-scheme:dark)');
                var hermesApply = function () {
                  var html = document.documentElement;
                  if (html.classList.contains('dark') !== hermesMq.matches) {
                    html.classList.toggle('dark', hermesMq.matches);
                  }
                };
                hermesApply();
                setTimeout(hermesApply, 0);
                setTimeout(hermesApply, 50);
                if (hermesMq.addEventListener) {
                  hermesMq.addEventListener('change', hermesApply);
                } else if (hermesMq.addListener) {
                  hermesMq.addListener(hermesApply);
                }
              }
            } catch (_) {}
        """
    }

    private lateinit var webView: WebView
    private val settingsStore by lazy {
        SettingsStore(getSharedPreferences(PREF_NAME, MODE_PRIVATE))
    }
    private lateinit var progressBar: ProgressBar
    private lateinit var splashContainer: FrameLayout
    private lateinit var errorContainer: FrameLayout
    private lateinit var errorMessageText: TextView
    private lateinit var errorTitleText: TextView
    private lateinit var retryButton: Button
    private lateinit var settingsButton: Button
    private lateinit var splashSettingsButton: ImageButton
    private lateinit var tabBarView: TabBarView
    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private var firstLoadComplete = false
    private var pendingLoadFailed = false
    private var settingsOpen = false
    private var onboardingOpen = false
    private var tapCount = 0
    private var lastTapTime = 0L
    private var webViewUserAgent = "HermesApp/1.0"
    private var pendingSessionId: String? = null
    private var pendingCronJobId: String? = null
    private var pendingApprovalAction: Triple<String, String, String>? = null
        private lateinit var notificationHelper: NotificationHelper
    private var notificationBridgeScript: String? = null
    private var activePooledWebView: PooledWebView? = null
    private var pendingVpnGuardUrl: String? = null
    private var vpnGuardStartedAt = 0L
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkWasValidated = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingNewChatShortcut = false
    private val tabRepository by lazy {
        TabRepository(SharedPreferencesTabStore(getSharedPreferences(PREF_NAME, MODE_PRIVATE)))
    }
    private val tabWebViewPool by lazy {
        TabWebViewPool(effectiveHotWebViewCount()) { tab -> createPooledWebView(tab) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Intent handling entry point:
        // LSPosed modules may forward doubao:// (or future custom schemes
        // such as hermes://) Intents to this Activity. When that integration
        // lands, resolve the incoming Intent here before loading the WebUI.
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        splashContainer = findViewById(R.id.splashContainer)
        errorContainer = findViewById(R.id.errorContainer)
        errorMessageText = findViewById(R.id.errorMessageText)
        errorTitleText = findViewById(R.id.errorTitleText)
        retryButton = findViewById(R.id.retryButton)
        settingsButton = findViewById(R.id.settingsButton)
        splashSettingsButton = findViewById(R.id.splashSettingsButton)

        notificationHelper = NotificationHelper(applicationContext)

        setupWebView()
        setupServiceWorker()

        tabBarView = findViewById(R.id.tabBarView)
        tabBarView.onTabSelected = { tab -> loadTab(tab) }
        tabBarView.onAddClicked = { openNewTabPage() }
        settingsButton.setOnClickListener { openSettings() }
        splashSettingsButton.setOnClickListener { openSettings() }
        retryButton.setOnClickListener { reloadServer() }
        setupTripleTap(findViewById(R.id.errorTitleText))

        scheduleBackgroundApprovalPolling()

        // Notification-tap deep-link: carry session_id to /session/<sid>.
        pendingSessionId = intent?.getStringExtra(NotificationHelper.EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }
        pendingCronJobId = intent?.getStringExtra(NotificationHelper.EXTRA_CRON_JOB_ID)?.takeIf { it.isNotBlank() }
        pendingApprovalAction = readApprovalAction(intent)

        handleShortcutIntent(intent)
        startMainFlow()
    }

    /**
     * Entry gate for the shell: server must be configured first, then the
     * one-time language/theme onboarding runs before the WebUI loads.
     */
    private fun startMainFlow() {
        if (onboardingOpen) return
        val url = getServerUrl()
        if (url.isEmpty()) {
            openOnboarding()
            return
        }
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (OnboardingActivity.shouldShow(prefs)) {
            openOnboarding()
        } else {
            loadServer(url)
        }
    }

    private fun scheduleBackgroundApprovalPolling() {
        val request = PeriodicWorkRequestBuilder<BackgroundPollWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "hermes-background-approval-poll",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun getServerUrl(): String {
        return settingsStore.serverUrl()
    }

    private fun effectiveHotWebViewCount(): Int {
        val configured = settingsStore.hotWebViewCount()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return configured
        return if (activityManager.isLowRamDevice || activityManager.memoryClass < 192) {
            configured.coerceAtMost(3)
        } else {
            configured
        }
    }

    private fun handleShortcutIntent(activityIntent: Intent?) {
        val explicit = activityIntent?.getBooleanExtra(EXTRA_NEW_SESSION, false) == true
        val deepLink = activityIntent?.data?.getQueryParameter("action") == "new-chat"
        if (explicit || deepLink) pendingNewChatShortcut = true
        if (explicit) activityIntent?.removeExtra(EXTRA_NEW_SESSION)
    }

    private fun loadServer(url: String) {
        if (url.isEmpty()) {
            openSettings()
            return
        }
        if (shouldWaitForVpn(url)) {
            vpnGuardStartedAt = SystemClock.elapsedRealtime()
            pendingVpnGuardUrl = buildServerUrl(url, pendingSessionId, pendingNewChatShortcut)
            VpnGuard.requestTailscaleConnect(this)
            mainHandler.postDelayed({ checkVpnGuard() }, VPN_POLL_INTERVAL_MS)
            return
        }
        pendingVpnGuardUrl = null
        val newChatRequested = pendingNewChatShortcut
        val target = buildServerUrl(url, pendingSessionId, newChatRequested)
        if (newChatRequested) pendingNewChatShortcut = false

        // A server change invalidates routes from the previous origin.
        tabRepository.tabs().filterNot { UrlPolicy.isSameOrigin(it.lastUrl, url) }.forEach {
            tabRepository.remove(it.id)
        }
        val activeTab = tabRepository.activeTab()
        val tab = activeTab?.copy(lastUrl = target, isValid = true)
            ?: HermesTab(
                sessionId = "",
                title = getString(R.string.tab_home),
                lastUrl = target,
                createdAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )
        tabRepository.upsert(tab)

        errorContainer.visibility = View.GONE
        splashContainer.visibility = View.VISIBLE
        firstLoadComplete = false
        pendingLoadFailed = false
        unregisterNetworkAutoRetry()
        loadTab(tab)
    }

    private fun shouldWaitForVpn(url: String): Boolean {
        return settingsStore.tailscaleGuardEnabled() &&
            TailscaleEndpointDetector.isTailscaleUrl(url) &&
            !VpnGuard.isVpnTransportActive(this)
    }

    private fun checkVpnGuard() {
        val url = pendingVpnGuardUrl ?: return
        val elapsed = SystemClock.elapsedRealtime() - vpnGuardStartedAt
        if (elapsed > VPN_GUARD_TIMEOUT_MS) {
            pendingVpnGuardUrl = null
            showError(
                getString(R.string.vpn_guard_timeout_title),
                getString(R.string.vpn_guard_timeout_message)
            )
            return
        }
        if (VpnGuard.isVpnTransportActive(this)) {
            Thread {
                val ready = isServerReachable(url)
                mainHandler.post {
                    if (pendingVpnGuardUrl == url) {
                        if (ready) {
                            pendingVpnGuardUrl = null
                            loadServer(url)
                        } else {
                            mainHandler.postDelayed({ checkVpnGuard() }, VPN_POLL_INTERVAL_MS)
                        }
                    }
                }
            }.start()
            return
        }
        if (elapsed > VPN_FALLBACK_DELAY_MS) VpnGuard.openTailscaleOrVpnSettings(this)
        mainHandler.postDelayed({ checkVpnGuard() }, VPN_POLL_INTERVAL_MS)
    }

    private fun isServerReachable(url: String): Boolean {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = (VPN_POLL_INTERVAL_MS - 100).toInt()
            connection.readTimeout = (VPN_POLL_INTERVAL_MS - 100).toInt()
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            val ready = connection.responseCode in 200..499
            connection.disconnect()
            ready
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun openSettings() {
        if (settingsOpen) return
        settingsOpen = true
        startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_REQUEST)
    }

    private fun openOnboarding() {
        if (settingsOpen || onboardingOpen) return
        onboardingOpen = true
        startActivityForResult(Intent(this, OnboardingActivity::class.java), ONBOARDING_REQUEST)
    }

    private fun reloadServer() {
        val url = getServerUrl()
        if (url.isEmpty()) {
            openSettings()
            return
        }
        errorContainer.visibility = View.GONE
        splashContainer.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        firstLoadComplete = false
        pendingLoadFailed = false
        unregisterNetworkAutoRetry()
        try {
            webView.clearCache(false)
        } catch (_: Exception) {
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        loadServer(url)
    }

    /**
     * Build a URL that optionally deep-links to a session or requests a new chat.
     */
    private fun buildServerUrl(baseUrl: String, sessionId: String?, appendNewChat: Boolean = false): String {
        if (sessionId.isNullOrBlank() && !appendNewChat) return baseUrl
        return try {
            val base = Uri.parse(baseUrl)
            val basePath = (base.path ?: "").trimEnd('/')
            val builder = base.buildUpon().path(basePath).clearQuery()
            if (sessionId != null) builder.appendPath("session").appendPath(Uri.encode(sessionId))
            if (appendNewChat) builder.appendQueryParameter("action", "new-chat")
            builder.build().toString()
        } catch (_: Exception) {
            val sessionPath = if (sessionId == null) "" else "session/${Uri.encode(sessionId)}"
            val base = "${baseUrl.trimEnd('/')}${if (sessionPath.isEmpty()) "" else "/$sessionPath"}"
            if (!appendNewChat) base
            else "$base${if (base.contains('?')) "&" else "?"}action=new-chat"
        }
    }

    private fun setupTripleTap(view: View) {
        view.setOnClickListener { registerTap() }
    }

    private fun registerTap() {
        val now = System.currentTimeMillis()
        tapCount = if (now - lastTapTime <= TRIPLE_TAP_WINDOW_MS) tapCount + 1 else 1
        lastTapTime = now
        if (tapCount >= 3) {
            tapCount = 0
            openSettings()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val topStripPx = (48 * resources.displayMetrics.density).toInt()
            if (ev.y <= topStripPx) {
                registerTap()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        HermesWebViewConfigurator.configure(webView, settingsStore.fontZoomPercent())
        webViewUserAgent = webView.settings.userAgentString
        installWebMessageListener()
        installWebViewClients(webView)
    }

    private fun installWebViewClients(target: WebView) {

        target.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return handleUrlOverride(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleUrlOverride(url)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                if (isServerOrigin(url)) {
                    syncScript()?.let { view.evaluateJavascript(it, null) }
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (isServerOrigin(url)) injectNotificationBridge(view)
                if (isServerOrigin(url)) {
                }
                // A retry forces LOAD_NO_CACHE; restore the default mode once
                // the navigation has settled.
                if (view.settings.cacheMode == WebSettings.LOAD_NO_CACHE) {
                    view.settings.cacheMode = WebSettings.LOAD_DEFAULT
                }
                if (!firstLoadComplete) {
                    firstLoadComplete = true
                    splashContainer.visibility = View.GONE
                }
                if (isServerOrigin(url)) {
                    handleDeepLinkInjection(view)
                    rememberTabRoute(view)
                }
                    if (pendingLoadFailed) {
                    // Navigation failed: keep the error page up (do not let
                    // this callback auto-hide it) and settle the progress UI.
                    pendingLoadFailed = false
                    progressBar.visibility = View.GONE
                } else {
                    if (errorContainer.visibility == View.VISIBLE) {
                        // A later successful page while the error was showing.
                        errorContainer.visibility = View.GONE
                    }
                    // Delay hiding progress bar to show completion briefly
                    mainHandler.postDelayed({ progressBar.visibility = View.GONE }, 200)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame && error.errorCode != -1) { // -1 = cancelled
                    pendingLoadFailed = true
                    showError(
                        getString(R.string.error_cannot_connect_title),
                        getString(R.string.error_cannot_connect_message)
                    )
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String,
                failingUrl: String
            ) {
                if (errorCode != -1 && (failingUrl == webView.url ||
                        (failingUrl.isNotEmpty() && target.url.isNullOrEmpty()))) {
                    pendingLoadFailed = true
                    showError(
                        getString(R.string.error_cannot_connect_title),
                        getString(R.string.error_cannot_connect_message)
                    )
                }
            }
        }

        target.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    mainHandler.postDelayed({ progressBar.visibility = View.GONE }, 300)
                }
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                uploadMessage = filePathCallback
                try {
                    val intent = fileChooserParams.createIntent()
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST)
                    return true
                } catch (e: android.content.ActivityNotFoundException) {
                    uploadMessage = null
                    return false
                }
            }
        }

        target.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimeType)
                request.addRequestHeader("User-Agent", userAgent)
                request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                getSystemService(Context.DOWNLOAD_SERVICE)?.let { dm ->
                    (dm as DownloadManager).enqueue(request)
                    Toast.makeText(this, getString(R.string.downloading, fileName), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.download_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createPooledWebView(tab: HermesTab): PooledWebView {
        val view = WebView(this)
        HermesWebViewConfigurator.configure(view, settingsStore.fontZoomPercent())
        installWebViewClients(view)
        installWebMessageListener(view)
        return WebViewPoolEntry(view).also { entry ->
            findViewById<ViewGroup>(R.id.webContainer).addView(entry.view, 0)
        }
    }

    private fun loadTab(tab: HermesTab) {
        if (!this::webView.isInitialized) return
        val currentTab = tabRepository.activeTab()
        if (currentTab?.id != tab.id) tabWebViewPool.pauseCurrent()

        val pooled = tabWebViewPool.acquire(tab)
        activePooledWebView = pooled
        (pooled as? WebViewPoolEntry)?.let { webView = it.view }
        tabRepository.activate(tab.id)
        if (pooled.currentUrl != tab.lastUrl) pooled.load(tab.lastUrl)
        syncActiveWebViewVisibility()
        renderTabs()
    }

    private fun openNewTabPage() {
        val serverUrl = getServerUrl()
        if (serverUrl.isEmpty()) return
        val home = HermesTab(
            sessionId = "",
            title = getString(R.string.tab_home),
            lastUrl = buildServerUrl(serverUrl, null, true),
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis()
        )
        loadTab(tabRepository.upsert(home).first { it.id == home.id })
    }

    private fun rememberTabRoute(view: WebView) {
        val url = view.url ?: return
        val sessionId = TabRoute.sessionIdFrom(url) ?: return
        val current = tabRepository.activeTab() ?: return
        val next = current.copy(sessionId = sessionId, lastUrl = url)
        tabRepository.upsert(next)
        renderTabs()
    }

    private fun syncActiveWebViewVisibility() {
        findViewById<ViewGroup>(R.id.webContainer).let { container ->
            (0 until container.childCount).map(container::getChildAt).forEach { child ->
            child.visibility = if (child === webView) View.VISIBLE else View.GONE
        }
        }
    }

    private fun renderTabs() {
        if (!this::tabBarView.isInitialized) return
        tabBarView.render(tabRepository.tabs(), tabRepository.activeTab()?.id)
    }

    private class WebViewPoolEntry(val view: WebView) : PooledWebView {
        override var tabId: String? = null
        override val currentUrl: String?
            get() = view.url

        override fun load(url: String) = view.loadUrl(url)
        override fun stopLoading() = view.stopLoading()
        override fun pause() = view.onPause()
        override fun resume() = view.onResume()
        override fun destroy() {
            view.stopLoading()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
    }

    private fun setupServiceWorker() {
        try {
            val swController = ServiceWorkerController.getInstance()
            val swSettings = swController.serviceWorkerWebSettings
            swSettings.cacheMode = WebSettings.LOAD_DEFAULT
            swSettings.allowContentAccess = true
        } catch (e: Exception) {
            // ServiceWorker not available — ignore
        }
    }


    private fun installWebMessageListener(target: WebView = webView) {
        val rule = serverOriginRule(getServerUrl())
        if (rule.isEmpty()) return
        try {
            target.removeJavascriptInterface("hermesNative")
            val listener = object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(
                    view: WebView,
                    message: WebMessageCompat,
                    sourceOrigin: Uri,
                    isMainFrame: Boolean,
                    replyProxy: JavaScriptReplyProxy
                ) {
                    handleWebMessage(target, message.data ?: "")
                }
            }
            WebViewCompat.addWebMessageListener(target, "hermesNative", setOf(rule), listener)
        } catch (_: Exception) {
        }
    }

    private fun serverOriginRule(serverUrl: String): String = UrlPolicy.originRule(serverUrl)

    private fun isServerOrigin(url: String): Boolean = UrlPolicy.isSameOrigin(url, getServerUrl())

    private fun handleWebMessage(source: WebView, raw: String) {
        val message = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return
        }
        val title = cleanWebMessageText(message.optString("title"))
        val body = cleanWebMessageText(message.optString("body"))
        val sessionId = cleanWebMessageId(message.optString("session_id"))
        if (message.optString("type") == "tab_state") {
            val current = tabRepository.activeTab() ?: return
            val updated = if (sessionId.isNotBlank()) {
                current.copy(
                    sessionId = sessionId,
                    title = title.ifBlank { current.title },
                    lastUrl = source.url ?: current.lastUrl
                )
            } else {
                current.copy(
                    title = title.ifBlank { current.title },
                    lastUrl = source.url ?: current.lastUrl
                )
            }
            if (updated.id != current.id) {
                tabRepository.remove(current.id)
            }
            tabRepository.upsert(
                if (updated.id != current.id) updated.copy(
                    createdAt = current.createdAt,
                    lastActiveAt = System.currentTimeMillis()
                ) else updated
            )
            renderTabs()
            return
        }
        when (message.optString("type")) {
            "approval" -> notificationHelper.notifyApproval(title, body, sessionId, cleanWebMessageId(message.optString("approval_id")))
            "session" -> notificationHelper.notifySession(title, body, sessionId)
            "task_complete" -> notificationHelper.notifyTaskComplete(title, body, sessionId, cleanWebMessageId(message.optString("cron_job_id")), message.optString("failed") == "1")
            "error" -> notificationHelper.notifyError(title, body, sessionId)
            "cancel_approval" -> notificationHelper.cancelApproval(sessionId)
            "cancel_session" -> notificationHelper.cancelAllForSession(sessionId)
            "remember_cron" -> notificationHelper.rememberCronMapping(sessionId, cleanWebMessageId(message.optString("cron_job_id")))
        }
    }

    private fun cleanWebMessageText(value: String): String {
        return value.replace('\u0000', ' ').trim().take(200)
    }

    private fun cleanWebMessageId(value: String): String {
        return value.trim().take(128)
    }

    private fun syncScript(): String? {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val langChoice = prefs.getString(PREF_KEY_LANG_CHOICE, CHOICE_KEEP)
        val themeChoice = prefs.getString(PREF_KEY_THEME_CHOICE, CHOICE_FOLLOW)
        val ops = StringBuilder()
        if (langChoice == CHOICE_FOLLOW) {
            val lang = JSONObject.quote(Locale.getDefault().toLanguageTag())
            ops.append("localStorage.setItem('hermes-lang',").append(lang).append(");")
        }
        if (themeChoice == CHOICE_FOLLOW) {
            ops.append("localStorage.setItem('hermes-theme','system');")
            ops.append(SYSTEM_THEME_PATCH)
        }
        return if (ops.isEmpty()) null else ops.toString()
    }

    private fun injectNotificationBridge(view: WebView) {
        try {
            val script = notificationBridgeScript ?: assets.open("notification_bridge.js")
                .bufferedReader().use { it.readText() }.also { notificationBridgeScript = it }
            view.evaluateJavascript(script, null)
        } catch (_: Exception) {
            // Asset missing ? bridge simply will not be installed.
        }
    }

    private fun syncWebViewTheme() {
        if (!this::webView.isInitialized || !isServerOrigin(webView.url.orEmpty())) return
        syncScript()?.let { script -> webView.evaluateJavascript(script, null) }
    }

    // ── URL override, error, activity lifecycle ──────────────────────────

    private fun readApprovalAction(intent: Intent?): Triple<String, String, String>? {
        val sessionId = intent?.getStringExtra(NotificationHelper.EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }
            ?: return null
        val approvalId = intent.getStringExtra(NotificationHelper.EXTRA_APPROVAL_ID).orEmpty()
        val choice = intent.getStringExtra(NotificationHelper.EXTRA_APPROVAL_CHOICE).orEmpty()
        return Triple(sessionId, approvalId, choice)
    }

    private fun handleDeepLinkInjection(view: WebView) {
        val jobId = pendingCronJobId
        if (!jobId.isNullOrBlank()) {
            pendingCronJobId = null
            val sessionId = pendingSessionId
            pendingSessionId = null
            openCronDetailInWebUi(view, jobId, sessionId.orEmpty())
            return
        }

        pendingSessionId?.takeIf { it.isNotBlank() }?.let { sessionId ->
            pendingSessionId = null
            val approvalAction = pendingApprovalAction
            pendingApprovalAction = null
            if (approvalAction == null) {
                restoreAttentionCard(view, sessionId)
            } else {
                deliverApprovalAction(view, approvalAction.first, approvalAction.second, approvalAction.third)
            }
        }
    }

    private fun openCronDetailInWebUi(view: WebView, jobId: String, fallbackSessionId: String) {
        val script = """
            (function () {
              var attempts = 0;
              var timer = null;
              function fallbackToSession() {
                if (timer) clearInterval(timer);
                var sid = HermesFallbackSessionId;
                if (!sid) return;
                try {
                  var nextUrl = location.pathname.replace(/\/session\/[^/?#]+\/?$/, '') +
                    '/session/' + encodeURIComponent(sid) + location.search + location.hash;
                  if (nextUrl === location.pathname + location.search + location.hash) return;
                  window.history.pushState({ hermesDeepLink: true }, '', nextUrl);
                  window.dispatchEvent(new PopStateEvent('popstate', { state: { hermesDeepLink: true } }));
                } catch (_) {}
              }
              function run() {
                attempts++;
                if (attempts > 12) { fallbackToSession(); clearInterval(timer); return; }
                Promise.resolve()
                  .then(function () {
                    if (typeof window.switchPanel !== 'function') throw new Error('switchPanel unavailable');
                    return window.switchPanel('tasks');
                  })
                  .then(function (opened) {
                    if (opened === false) throw new Error('tasks panel blocked');
                    if (typeof window.openCronDetail !== 'function') throw new Error('openCronDetail unavailable');
                    if (typeof window._findCronJob !== 'function' || !window._findCronJob(String(HermesCronJobId))) {
                      throw new Error('cron list not ready');
                    }
                    window.openCronDetail(String(HermesCronJobId));
                    if (timer) clearInterval(timer);
                  })
                  .catch(function () {});
              }
              run();
              timer = setInterval(run, 250);
            })();
        """.replace("HermesCronJobId", JSONObject.quote(jobId))
            .replace("HermesFallbackSessionId", JSONObject.quote(fallbackSessionId))
            .trimIndent()
        view.evaluateJavascript(script, null)
    }

    private fun navigateToSession(sessionId: String) {
        val baseUrl = getServerUrl()
        if (baseUrl.isEmpty() || !this::webView.isInitialized) return
        val target = buildServerUrl(baseUrl, sessionId)
        webView.evaluateJavascript("window.location.replace(" + JSONObject.quote(target) + ");", null)
    }

    private fun restoreAttentionCard(view: WebView, sessionId: String) {
        val script = """
            (function () {
              if (window.__hermesRestoreAttentionCard) window.__hermesRestoreAttentionCard(HermesSid);
            })();
        """.replace("HermesSid", JSONObject.quote(sessionId)).trimIndent()
        view.evaluateJavascript(script, null)
    }

    private fun deliverApprovalAction(
        view: WebView,
        sessionId: String,
        approvalId: String,
        choice: String
    ) {
        if (choice != NotificationHelper.CHOICE_APPROVE && choice != NotificationHelper.CHOICE_DENY) return
        val script = """
            (function () {
              if (!window.__hermesRespondApprovalAction) return false;
              return window.__hermesRespondApprovalAction(HermesSid, HermesApprovalId, HermesChoice);
            })();
        """.replace("HermesSid", JSONObject.quote(sessionId))
            .replace("HermesApprovalId", JSONObject.quote(approvalId))
            .replace("HermesChoice", JSONObject.quote(choice))
            .trimIndent()
        view.evaluateJavascript(script) { result ->
            if (result == "false" && intent.getBooleanExtra(
                    NotificationHelper.EXTRA_DELIVERY_FALLBACK,
                    false
                )
            ) {
                navigateToSession(sessionId)
            }
        }
    }

    private fun handleUrlOverride(url: String): Boolean {
        val scheme = Uri.parse(url).scheme ?: ""
        if (scheme.startsWith("http")) {
            return false // load in WebView
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // ignore
        }
        return true
    }

    private fun showError(title: String, message: String) {
        splashContainer.visibility = View.GONE
        progressBar.visibility = View.GONE
        errorTitleText.text = title
        errorMessageText.text = message
        errorContainer.visibility = View.VISIBLE
        registerNetworkAutoRetry()
    }

    private fun registerNetworkAutoRetry() {
        if (networkCallback != null) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkWasValidated = cm.activeNetwork
            ?.let { cm.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ?: false
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Validation is confirmed in onCapabilitiesChanged.
            }

            override fun onLost(network: Network) {
                networkWasValidated = false
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    if (!networkWasValidated) {
                        networkWasValidated = true
                        maybeAutoRetry()
                    }
                }
            }
        }
        networkCallback = cb
        try {
            cm.registerDefaultNetworkCallback(cb)
        } catch (_: Exception) {
            networkCallback = null
        }
    }

    private fun maybeAutoRetry() {
        mainHandler.post {
            if (errorContainer.visibility == View.VISIBLE) {
                unregisterNetworkAutoRetry()
                reloadServer()
            }
        }
    }

    private fun unregisterNetworkAutoRetry() {
        val cb = networkCallback ?: return
        networkCallback = null
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
            // Ignore; manual retry still works.
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (errorContainer.visibility == View.VISIBLE) {
            moveTaskToBack(true)
            return
        }
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            moveTaskToBack(true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST) {
            settingsOpen = false
            if (resultCode == RESULT_OK) {
                installWebMessageListener()
                startMainFlow()
            } else if (getServerUrl().isEmpty()) {
                showError(
                    getString(R.string.no_server_configured_title),
                    getString(R.string.no_server_configured_message)
                )
            }
            return
        }
        if (requestCode == ONBOARDING_REQUEST) {
            onboardingOpen = false
            settingsOpen = false
            installWebMessageListener()
            if (getServerUrl().isEmpty()) {
                mainHandler.post { openOnboarding() }
            } else {
                reloadServer()
            }
            return
        }
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val callback = uploadMessage
            uploadMessage = null
            if (callback != null) {
                val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                callback.onReceiveValue(results)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val sessionId = intent.getStringExtra(NotificationHelper.EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() } ?: return
        val cronJobId = intent.getStringExtra(NotificationHelper.EXTRA_CRON_JOB_ID)?.takeIf { it.isNotBlank() }
        val approvalAction = readApprovalAction(intent)
        val baseUrl = getServerUrl()
        if (baseUrl.isEmpty() || !this::webView.isInitialized || errorContainer.visibility == View.VISIBLE) {
            pendingSessionId = sessionId
            if (!cronJobId.isNullOrBlank()) pendingCronJobId = cronJobId
            if (approvalAction != null) pendingApprovalAction = approvalAction
            reloadServer()
            return
        }

        if (!cronJobId.isNullOrBlank()) {
            pendingCronJobId = cronJobId
            pendingSessionId = sessionId
            handleDeepLinkInjection(webView)
            return
        }

        if (approvalAction != null && !approvalAction.second.isNullOrBlank()) {
            pendingSessionId = sessionId
            pendingApprovalAction = approvalAction
            val target = buildServerUrl(baseUrl, sessionId)
            if (webView.url == target) {
                handleDeepLinkInjection(webView)
            } else {
                navigateToSession(sessionId)
            }
            return
        }

        pendingSessionId = sessionId
        navigateToSession(sessionId)
    }

    override fun onResume() {
        super.onResume()
        if (pendingVpnGuardUrl != null) return
        tabWebViewPool.applyMaxHotCount(effectiveHotWebViewCount())
        webView.onResume()
        syncWebViewTheme()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        syncWebViewTheme()
    }

    override fun onPause() {
        super.onPause()
        if (pendingVpnGuardUrl != null) return
        webView.onPause()
    }

    override fun onDestroy() {
        unregisterNetworkAutoRetry()
        mainHandler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        webView.freeMemory()
    }
}
