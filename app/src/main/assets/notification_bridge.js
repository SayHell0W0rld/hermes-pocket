(function () {
  if (window.__hermesNotificationBridgeInstalled) return;
  window.__hermesNotificationBridgeInstalled = true;

  var DEDUPE_TTL_MS = 30000;
  var SSE_COOLDOWN_MS = 35000;
  var TOAST_TTL_MS = 5000;
  var recent = new Map();
  var lastSseNotifyAt = 0;
  // stream_key -> { hidden: bool, ts: ms } — mirrors WebUI's
  // _shouldForceCompletionNotification: a stream that was ever backgrounded
  // still notifies even if the throttled `done` event lands after the user
  // returns and document.hidden is already false.
  var hiddenStreams = new Map();
  window.__hermesCronMap = window.__hermesCronMap || {};

  function now() { return Date.now(); }

  function isRecent(key, ttl) {
    var t = recent.get(key);
    if (t && (now() - t) < ttl) return true;
    recent.set(key, now());
    if (recent.size > 200) {
      var cutoff = now() - 60000;
      recent.forEach(function (v, k) { if (v < cutoff) recent.delete(k); });
    }
    return false;
  }

  function nativeBridge() {
    try { return window.hermesNative || null; } catch (e) { return null; }
  }

  function callNative(type, title, body, sid, extra, failed) {
    try {
      var b = nativeBridge();
      if (!b || typeof b.postMessage !== 'function') return false;
      var payload = { type: type, title: String(title || ''), body: String(body || '') };
      if (sid) payload.session_id = String(sid);
      if (type === 'approval') payload.approval_id = String(extra || '');
      if (type === 'task_complete') payload.cron_job_id = String(extra || '');
      if (type === 'task_complete') payload.failed = failed ? '1' : '0';
      b.postMessage(JSON.stringify(payload));
      return true;
    } catch (e) { return false; }
  }

  function send(title, body, isError) {
    if (!body) return;
    if (isError) callNative('error', title, body, '');
    else callNative('task_complete', title, body, '');
  }

  // PWA notifications are suppressed inside the shell through the WebUI's own
  // existing _notificationsEnabled switch, so native notifications are the
  // single notification path (no PWA + Android doubles).
  function suppressPwaNotifications() {
    try { window._notificationsEnabled = false; } catch (e) {}
  }

  function parseData(e) {
    try { return JSON.parse(e.data || '{}'); } catch (err) { return {}; }
  }

  function eventKey(prefix, sid, evtId, fallback) {
    return prefix + '|' + sid + '|' + (evtId || fallback || '');
  }

  function streamKeyOf(source, sid) {
    try {
      var u = new URL(source.url || '', location.href);
      var streamId = String(u.searchParams.get('stream_id') || '');
      if (streamId) return streamId;
    } catch (e) {}
    return sid ? 'sess:' + sid : '';
  }

  function markStreamHidden(key) {
    if (!key) return;
    if (!hiddenStreams.has(key)) {
      hiddenStreams.set(key, { hidden: !!document.hidden, ts: now() });
    } else {
      var entry = hiddenStreams.get(key);
      if (document.hidden) entry.hidden = true;
      entry.ts = now();
    }
    if (hiddenStreams.size > 100) {
      var cutoff = now() - 600000;
      hiddenStreams.forEach(function (v, k) { if (v.ts < cutoff) hiddenStreams.delete(k); });
    }
  }

  function wasStreamHidden(key) {
    if (!key) return false;
    var entry = hiddenStreams.get(key);
    return !!(entry && entry.hidden);
  }

  function dropStreamHidden(key) {
    if (key) hiddenStreams.delete(key);
  }

  try {
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        hiddenStreams.forEach(function (entry) { entry.hidden = true; });
      }
    });
  } catch (e) {}

  function forwardSseEvent(e, type, source) {
    try {
      var d = parseData(e);
      var sid = String(d.session_id || '');
      var evtId = String(d.event_id || d.request_id || '');

      if (type === 'approval') {
        var aKey = eventKey('approval', sid, evtId, String(d.description || d.message || d.pending_count || ''));
        if (isRecent(aKey, DEDUPE_TTL_MS)) return;
        var aBody = String(d.description || d.message || 'Tool approval needed').slice(0, 140);
        callNative('approval', '', aBody, sid, String(d.approval_id || ''));
        lastSseNotifyAt = now();
        return;
      }

      if (type === 'clarify') {
        var cKey = eventKey('clarify', sid, evtId, String(d.question || d.message || ''));
        if (isRecent(cKey, DEDUPE_TTL_MS)) return;
        var cBody = String(d.question || d.message || 'Clarification needed').slice(0, 140);
        callNative('session', '', cBody, sid);
        lastSseNotifyAt = now();
        return;
      }

      if (type === 'done') {
        var sKey = streamKeyOf(source, sid);
        if (sKey) markStreamHidden(sKey);
        var wasHidden = !!document.hidden || wasStreamHidden(sKey);
        if (sKey) dropStreamHidden(sKey);
        // Only notify when the user is not watching the page — same rule the
        // WebUI applies (no notification for foreground-visible completions).
        if (!wasHidden) return;
        var dKey = eventKey('done', sid, evtId, sKey || String(d.summary || d.preview || ''));
        if (isRecent(dKey, DEDUPE_TTL_MS)) return;
        var dB = String(d.preview || d.summary || 'Response complete').slice(0, 140);
        callNative('session', '', dB, sid);
        lastSseNotifyAt = now();
        return;
      }

      if (type === 'bg_task_complete') {
        if (!evtId) return; // server contract: every completion carries event_id
        var bKey = eventKey('task', sid, evtId, '');
        if (isRecent(bKey, DEDUPE_TTL_MS)) return;
        var tid = String(d.task_id || '').slice(0, 8);
        var bBody = 'Task ' + (tid || '?') + ' done';
        if (d.summary) bBody += ': ' + String(d.summary).slice(0, 100);
        var jobId = '';
        try {
          jobId = String((window.__hermesCronMap && window.__hermesCronMap[String(sid || '')]) || '');
        } catch (eMap) {}
        callNative('task_complete', '', bBody, sid, jobId, String(d.status || d.last_status || '').toLowerCase() === 'error' || String(d.status || d.last_status || '').toLowerCase() === 'failed');
        lastSseNotifyAt = now();
        return;
      }

      if (type === 'error') {
        // Transport errors carry no payload; only surface real errors.
        if (!e.data) return;
        var eKey = eventKey('error', sid, evtId, String(d.message || d.error || d.summary || ''));
        if (isRecent(eKey, DEDUPE_TTL_MS)) return;
        var eBody = String(d.message || d.error || 'Session error').slice(0, 140);
        callNative('error', '', eBody, sid);
        lastSseNotifyAt = now();
      }
    } catch (e2) {}
  }

  var CAPTURED_TYPES = ['approval', 'clarify', 'done', 'bg_task_complete', 'error'];
  try {
    var origAdd = EventSource.prototype.addEventListener;
    EventSource.prototype.addEventListener = function (type, listener, options) {
      if (CAPTURED_TYPES.indexOf(type) !== -1) {
        var wrapped = function (e) {
          try {
            // Flip the WebUI switch before its own listener runs so the
            // matching PWA notification is suppressed (no double notify).
            suppressPwaNotifications();
            forwardSseEvent(e, type, this);
          } catch (err) {}
          if (typeof listener === 'function') return listener.apply(this, arguments);
          return undefined;
        };
        return origAdd.call(this, type, wrapped, options);
      }
      return origAdd.apply(this, arguments);
    };
  } catch (e3) {}

  // ── Toast capture (kept from the original single-channel bridge) ──
  function looksRelevant(text) {
    return /^Cron\b/i.test(text) || /^Task\b/i.test(text) ||
           /cron|task|后台|任务|background/i.test(text);
  }

  function forwardToast(text, type) {
    try {
      var s = String(text || '').trim();
      if (!s) return;
      var isError = type === 'error';
      if (!looksRelevant(s)) return;
      // Cooldown: if an SSE notification just fired, suppress the echoing
      // toast from cron polling (every 30s).
      if (!isError && (now() - lastSseNotifyAt) < SSE_COOLDOWN_MS) return;
      var key = 'toast:' + s;
      if (isRecent(key, TOAST_TTL_MS)) return;
      send('', s, isError);
    } catch (e) {}
  }

  function cancelNotifications(kind, sid) {
    try {
      if (kind === 'approval') callNative('cancel_approval', '', '', sid);
      if (kind === 'session') callNative('cancel_session', '', '', sid);
    } catch (e) {}
  }

  function readSessionIdFromBody(body) {
    try {
      if (!body) return '';
      var raw = typeof body === 'string' ? body : String(body);
      return String(JSON.parse(raw).session_id || '');
    } catch (e) { return ''; }
  }

  try {
    var origFetch = window.fetch;
    if (typeof origFetch === 'function' && !window.__hermesApprovalFetchHooked) {
      window.__hermesApprovalFetchHooked = true;
      window.fetch = function (input, init) {
        var result = origFetch.apply(this, arguments);
        try {
          var url = '';
          if (typeof input === 'string') url = input;
          else if (input && typeof input.url === 'string') url = input.url;
          var method = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
          if (method === 'GET' && url.indexOf('/api/crons/recent') !== -1) {
            result.then(function (response) {
              if (!response || !response.ok) return;
              return response.clone().json();
            }).then(function (payload) {
              var completions = payload && payload.completions;
              if (!Array.isArray(completions)) return;
              completions.forEach(function (completion) {
                var sid = String(completion && completion.session_id || '');
                var jobId = String(completion && completion.job_id || '');
                if (!sid || !jobId) return;
                window.__hermesCronMap[sid] = jobId;
                callNative('remember_cron', '', '', sid, jobId);
              });
            }).catch(function () {});
          }
          if (method === 'POST' && url.indexOf('/api/approval/respond') !== -1) {
            var sid = readSessionIdFromBody(init && init.body);
            result.then(function (response) {
              if (response && response.ok) cancelNotifications('approval', sid);
            }).catch(function () {});
          }
        } catch (e) {}
        return result;
      };
    }
  } catch (e) {}

  window.__hermesRespondApprovalAction = function (sid, approvalId, choice) {
    try {
      sid = String(sid || '');
      approvalId = String(approvalId || '');
      choice = String(choice || '');
      if (!sid || !approvalId || (choice !== 'approve' && choice !== 'deny')) return Promise.resolve(false);
      return fetch('/api/approval/respond', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ session_id: sid, approval_id: approvalId, choice: choice })
      }).then(function (response) {
        if (!response.ok) return false;
        cancelNotifications('approval', sid);
        return fetch('/api/approval/pending?session_id=' + encodeURIComponent(sid), { credentials: 'include' })
          .then(function (pendingResponse) { return pendingResponse.ok ? pendingResponse.json() : null; })
          .then(function (payload) {
            if (payload && payload.pending && typeof window.showApprovalForSession === 'function') {
              showApprovalForSession(sid, payload.pending, payload.pending_count || 1);
            } else if (typeof window.hideApprovalCard === 'function') {
              hideApprovalCard(true);
            }
            return true;
          });
      }).catch(function () { return false; });
    } catch (e) {
      return Promise.resolve(false);
    }
  };

  window.__hermesRestoreAttentionCard = function (sid) {
    try {
      sid = String(sid || '');
      if (!sid) return Promise.resolve(false);
      var approval = fetch('/api/approval/pending?session_id=' + encodeURIComponent(sid), { credentials: 'include' })
        .then(function (response) { return response.ok ? response.json() : null; })
        .then(function (payload) {
          if (payload && payload.pending && typeof window.showApprovalForSession === 'function') {
            showApprovalForSession(sid, payload.pending, payload.pending_count || 1);
            return true;
          }
          return false;
        }).catch(function () { return false; });
      var clarify = fetch('/api/clarify/pending?session_id=' + encodeURIComponent(sid), { credentials: 'include' })
        .then(function (response) { return response.ok ? response.json() : null; })
        .then(function (payload) {
          if (payload && payload.pending && typeof window.showClarifyForSession === 'function') {
            showClarifyForSession(sid, payload.pending);
            return true;
          }
          return false;
        }).catch(function () { return false; });
      return Promise.all([approval, clarify]).then(function (results) {
        return results.indexOf(true) !== -1;
      });
    } catch (e) {
      return Promise.resolve(false);
    }
  };

  try {
    var origToast = window.showToast;
    if (typeof origToast === 'function') {
      window.showToast = function (msg, ms, type) {
        forwardToast(msg, type);
        return origToast.apply(this, arguments);
      };
    }
  } catch (e4) {}

  try {
    var toastEl = document.getElementById('toast');
    if (toastEl && 'MutationObserver' in window) {
      var mo = new MutationObserver(function () {
        try {
          if (!toastEl.classList.contains('show')) return;
          var text = toastEl.dataset.toastMessage || toastEl.textContent || '';
          var isError = toastEl.classList.contains('error');
          forwardToast(text, isError ? 'error' : '');
        } catch (e) {}
      });
      mo.observe(toastEl, { attributes: true, attributeFilter: ['class'], childList: true });
    }
  } catch (e5) {}

  // Native tab state reporter. The shell owns the tab bar, so the page never
  // renders tab controls and the shell only receives changed state.
  function callTabState(sid, title) {
    try {
      var b = nativeBridge();
      if (!b || typeof b.postMessage !== 'function') return;
      b.postMessage(JSON.stringify({
        type: 'tab_state',
        session_id: String(sid || ''),
        title: String(title || '')
      }));
    } catch (e) {}
  }

  function activeSessionId() {
    try {
      var match = String(location.pathname || '').match(/(?:^|\/)session\/([^\/?#]+)/);
      return match ? decodeURIComponent(match[1]) : '';
    } catch (e) { return ''; }
  }

  var lastTabSid = '';
  var lastTabTitle = '';
  setInterval(function () {
    var sid = activeSessionId();
    var title = '';
    try {
      var state = (typeof S !== 'undefined' && S && S.session) || (window.S && window.S.session);
      title = String((state && (state.title || state.name)) || '');
    } catch (e) {}
    if (sid === lastTabSid && title === lastTabTitle) return;
    lastTabSid = sid;
    lastTabTitle = title;
    callTabState(sid, title.slice(0, 28));
  }, 500);
  // ── PWA suppression keeper ──
  suppressPwaNotifications();
  // Boot/settings responses may re-enable _notificationsEnabled later; keep
  // the existing WebUI switch off while the shell bridge is alive.
  try {
    setInterval(function () {
      if (nativeBridge()) suppressPwaNotifications();
    }, 1000);
  } catch (e6) {}
})();
