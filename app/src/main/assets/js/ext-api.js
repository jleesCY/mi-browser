// The WebExtensions API (chrome.* and browser.*) for extensions running in mi: one object answering both styles, Chrome's
// callbacks and Firefox's promises. Every call goes to mi (extensions/ExtensionApi) over a channel the caller provides;
// events come back the same way. Content scripts get the part Chrome gives them (runtime, storage, i18n, extension);
// an extension's own pages (background, popup, options) get everything mi supports. This file is an expression: mi
// embeds it as `makeApi = <this>()`, never as a global a page could reach.
(function () {
  return function makeApi(cfg, channel) {
    const call = (name, args) => channel.call(name, args);
    const privileged = cfg.kind !== 'content';
    let lastError; // chrome.runtime.lastError, set only while a callback runs

    // ---- helpers
    // a function that takes a trailing callback (Chrome) or returns a promise (Firefox)
    const api = (fn) => function (...args) {
      const cb = typeof args[args.length - 1] === 'function' ? args.pop() : null;
      let p;
      try { p = Promise.resolve(fn.apply(null, args)); } catch (e) { p = Promise.reject(e); }
      if (!cb) return p;
      p.then((v) => { try { cb(v); } catch (e) { console.error(e); } },
        (e) => { lastError = { message: String((e && e.message) || e) }; try { cb(); } finally { lastError = undefined; } });
    };
    const remote = (name) => api((...args) => call(name, args));
    const listeners = {};
    const event = (name) => {
      const set = (listeners[name] = listeners[name] || []);
      return {
        addListener(fn) { if (typeof fn === 'function' && !set.includes(fn)) set.push(fn); },
        removeListener(fn) { const i = set.indexOf(fn); if (i >= 0) set.splice(i, 1); },
        hasListener(fn) { return set.includes(fn); },
        hasListeners() { return set.length > 0; },
      };
    };
    const fire = (name, ...args) => { for (const fn of (listeners[name] || []).slice()) { try { fn(...args); } catch (e) { console.error(e); } } };
    // a namespace mi doesn't support: calls resolve to nothing, events never fire, so extensions keep running
    const stub = (ns, events = []) => new Proxy({}, {
      get(target, k) {
        if (k in target) return target[k];
        if (typeof k !== 'string') return undefined;
        if (/^on[A-Z]/.test(k)) return (target[k] = event(ns + '.' + k));
        return (target[k] = api(() => Promise.reject(new Error(ns + '.' + k + ' is not supported in mi.'))));
      },
    });
    const url = (path) => (/^[a-z-]+:/.test(path) ? path : cfg.baseUrl + String(path || '').replace(/^\//, ''));

    // ---- i18n (the extension's _locales, read by mi)
    const message = (name, subs) => {
      if (name === '@@extension_id') return cfg.id;
      if (name === '@@ui_locale') return cfg.uiLanguage.replace('-', '_');
      if (name === '@@bidi_dir') return 'ltr';
      let m = cfg.messages[String(name).toLowerCase()];
      if (m == null) return '';
      const s = subs == null ? [] : Array.isArray(subs) ? subs : [subs];
      return m.replace(/\$(\d)/g, (_, i) => (s[i - 1] != null ? String(s[i - 1]) : '')).replace(/\$\$/g, '$');
    };
    const i18n = {
      getMessage: message,
      getUILanguage: () => cfg.uiLanguage,
      getAcceptLanguages: api(() => [cfg.uiLanguage, cfg.uiLanguage.split('-')[0]]),
      detectLanguage: api(() => ({ isReliable: false, languages: [] })),
    };

    // ---- storage
    const area = (name) => {
      const a = {
        get: api(async (keys) => {
          const all = await call('storage.get', [name]);
          if (keys == null) return all;
          if (typeof keys === 'string') return keys in all ? { [keys]: all[keys] } : {};
          if (Array.isArray(keys)) { const o = {}; for (const k of keys) if (k in all) o[k] = all[k]; return o; }
          const o = {}; for (const k of Object.keys(keys)) o[k] = k in all ? all[k] : keys[k]; return o;
        }),
        set: api((items) => call('storage.set', [name, JSON.parse(JSON.stringify(items || {}))]).then(() => undefined)),
        remove: api((keys) => call('storage.remove', [name, typeof keys === 'string' ? [keys] : keys || []]).then(() => undefined)),
        clear: api(() => call('storage.clear', [name]).then(() => undefined)),
        getBytesInUse: api(() => call('storage.bytes', [name])),
        getKeys: api(async () => Object.keys(await call('storage.get', [name]))),
        setAccessLevel: api(() => undefined),
        onChanged: event('storage.' + name + '.onChanged'),
        QUOTA_BYTES: name === 'sync' ? 102400 : 10485760,
      };
      if (name === 'sync') Object.assign(a, { QUOTA_BYTES_PER_ITEM: 8192, MAX_ITEMS: 512, MAX_WRITE_OPERATIONS_PER_HOUR: 1800, MAX_WRITE_OPERATIONS_PER_MINUTE: 120 });
      return a;
    };
    const storage = { local: area('local'), sync: area('sync'), session: area('session'), managed: { get: api(() => ({})), onChanged: event('storage.managed.onChanged') }, onChanged: event('storage.onChanged') };

    // ---- runtime messaging and ports
    const ports = {};
    const makePort = (portId, name, sender) => {
      const port = {
        name, sender,
        onMessage: event('port.' + portId + '.message'),
        onDisconnect: event('port.' + portId + '.disconnect'),
        postMessage(msg) { call('port.post', [portId, msg]); },
        disconnect() { delete ports[portId]; call('port.disconnect', [portId]); },
      };
      ports[portId] = port;
      return port;
    };
    const sendArgs = (args) => {
      // sendMessage(message) or sendMessage(extensionId, message, options)
      if (args.length >= 2 && (typeof args[0] === 'string' || args[0] == null) && !(args.length === 2 && typeof args[1] === 'object' && args[1] && 'includeTlsChannelId' in args[1])) return [args[0], args[1]];
      return [null, args[0]];
    };
    const runtime = {
      id: cfg.id,
      get lastError() { return lastError; },
      getURL: url,
      getManifest: () => JSON.parse(JSON.stringify(cfg.manifest)),
      sendMessage: api((...args) => { const [ext, msg] = sendArgs(args); return call('runtime.sendMessage', [ext, msg]); }),
      connect: (a, b) => {
        const info = typeof a === 'object' && a ? a : b || {};
        const portId = 'p' + Math.random().toString(36).slice(2);
        const port = makePort(portId, info.name || '', undefined);
        call('port.connect', [portId, info.name || '', null]);
        return port;
      },
      onMessage: event('runtime.onMessage'),
      onMessageExternal: event('runtime.onMessageExternal'),
      onConnect: event('runtime.onConnect'),
      onConnectExternal: event('runtime.onConnectExternal'),
      onInstalled: event('runtime.onInstalled'),
      onStartup: event('runtime.onStartup'),
      onSuspend: event('runtime.onSuspend'),
      onUpdateAvailable: event('runtime.onUpdateAvailable'),
      getPlatformInfo: api(() => ({ os: 'android', arch: 'arm64', nacl_arch: 'arm' })),
      getBrowserInfo: api(() => ({ name: 'mi', vendor: 'mi.', version: cfg.appVersion, buildID: cfg.appVersion })),
      PlatformOs: { ANDROID: 'android', LINUX: 'linux', MAC: 'mac', WIN: 'win' },
      OnInstalledReason: { INSTALL: 'install', UPDATE: 'update', BROWSER_UPDATE: 'browser_update' },
    };
    if (privileged) Object.assign(runtime, {
      openOptionsPage: remote('runtime.openOptionsPage'),
      setUninstallURL: api(() => undefined),
      reload: () => { call('runtime.reload', []); },
      getContexts: api(() => []),
      getBackgroundPage: api(() => (cfg.kind === 'background' ? window : null)),
      requestUpdateCheck: api(() => ({ status: 'no_update' })),
    });

    const extension = {
      getURL: url,
      inIncognitoContext: !!cfg.incognito,
      isAllowedIncognitoAccess: api(() => !!cfg.incognitoAllowed),
      isAllowedFileSchemeAccess: api(() => false),
      getBackgroundPage: () => (cfg.kind === 'background' ? window : null),
      getViews: () => [window],
      sendMessage: runtime.sendMessage,
      onMessage: runtime.onMessage,
    };

    const out = { runtime, storage, i18n, extension };

    // ---- WebSocket, as the extension's code sees it: mi makes the connection (extensions/ExtensionSockets), so it says it
    // comes from the extension, as a browser's would (sites like Grammarly's only take those). Pages get it as their
    // WebSocket; content scripts as theirs, never the page's.
    const sockets = {};
    let socketSeq = 0;
    const toB64 = (buf) => { let s = ''; const a = new Uint8Array(buf); for (let i = 0; i < a.length; i += 0x8000) s += String.fromCharCode.apply(null, a.subarray(i, i + 0x8000)); return btoa(s); };
    const fromB64 = (s) => { const bin = atob(s || ''); const a = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) a[i] = bin.charCodeAt(i); return a; };
    class MiWebSocket extends EventTarget {
      constructor(address, protocols) {
        super();
        let u;
        try { u = new URL(String(address), location.href); } catch (_) { throw new DOMException("Failed to construct 'WebSocket': The URL '" + address + "' is invalid.", 'SyntaxError'); }
        if (u.protocol === 'https:') u.protocol = 'wss:'; else if (u.protocol === 'http:') u.protocol = 'ws:';
        if (u.protocol !== 'ws:' && u.protocol !== 'wss:') throw new DOMException("Failed to construct 'WebSocket': The URL's scheme must be either 'ws' or 'wss'.", 'SyntaxError');
        this._id = 's' + (++socketSeq) + Math.random().toString(36).slice(2, 8);
        this._queue = Promise.resolve();
        Object.assign(this, { url: u.href, readyState: 0, protocol: '', extensions: '', bufferedAmount: 0, binaryType: 'blob', onopen: null, onmessage: null, onerror: null, onclose: null });
        sockets[this._id] = this;
        call('ws.open', [this._id, u.href, protocols == null ? [] : [].concat(protocols).map(String)]).catch(() => {
          this._event({ type: 'error' }); this._event({ type: 'close', code: 1006, reason: '', clean: false });
        });
      }
      send(data) {
        if (this.readyState === 0) throw new DOMException("Failed to execute 'send' on 'WebSocket': Still in CONNECTING state.", 'InvalidStateError');
        if (this.readyState !== 1) return;
        // in order, also when a Blob has to be read first
        this._queue = this._queue.then(async () => {
          if (typeof data === 'string') return call('ws.send', [this._id, data, null]);
          const buf = data instanceof Blob ? await data.arrayBuffer() : ArrayBuffer.isView(data) ? data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength) : data;
          return call('ws.send', [this._id, null, toB64(buf)]);
        }).catch(() => {});
      }
      close(code, reason) {
        if (this.readyState >= 2) return;
        this.readyState = 2;
        this._queue.then(() => call('ws.close', [this._id, code == null ? 1000 : code, reason == null ? '' : String(reason)])).catch(() => {});
      }
      _fire(ev) {
        const h = this['on' + ev.type];
        if (typeof h === 'function') { try { h.call(this, ev); } catch (e) { console.error(e); } }
        this.dispatchEvent(ev);
      }
      _event(d) {
        if (d.type === 'open') { this.readyState = 1; this.protocol = d.protocol || ''; this._fire(new Event('open')); }
        else if (d.type === 'message') {
          const data = d.text != null ? d.text : this.binaryType === 'arraybuffer' ? fromB64(d.b64).buffer : new Blob([fromB64(d.b64)]);
          this._fire(new MessageEvent('message', { data, origin: new URL(this.url).origin }));
        } else if (d.type === 'error') this._fire(new Event('error'));
        else if (d.type === 'close') {
          if (this.readyState === 3) return;
          this.readyState = 3; delete sockets[this._id];
          this._fire(new CloseEvent('close', { code: d.code, reason: d.reason || '', wasClean: !!d.clean }));
        }
      }
    }
    Object.entries({ CONNECTING: 0, OPEN: 1, CLOSING: 2, CLOSED: 3 }).forEach(([k, v]) => { MiWebSocket[k] = v; MiWebSocket.prototype[k] = v; });
    Object.defineProperty(MiWebSocket, 'name', { value: 'WebSocket' });
    channel.WebSocket = MiWebSocket;
    channel.base64 = { b64: toB64, unb64: fromB64 }; // for ext-page.js's requests

    if (privileged) {
      // ---- tabs and scripting
      const tabs = {
        TAB_ID_NONE: -1,
        query: remote('tabs.query'),
        get: remote('tabs.get'),
        getCurrent: api(() => call('tabs.getCurrent', []).then((t) => t || undefined)),
        create: remote('tabs.create'),
        update: api((a, b) => (typeof a === 'number' ? call('tabs.update', [a, b || {}]) : call('tabs.update', [null, a || {}]))),
        remove: api((ids) => call('tabs.remove', [Array.isArray(ids) ? ids : [ids]]).then(() => undefined)),
        reload: api((a) => call('tabs.reload', [typeof a === 'number' ? a : null]).then(() => undefined)),
        sendMessage: api((tabId, msg, opts) => call('tabs.sendMessage', [tabId, msg, opts || {}])),
        executeScript: api((a, b) => { const [tabId, d] = typeof a === 'number' ? [a, b] : [null, a]; return call('tabs.executeScript', [tabId, d || {}]); }),
        insertCSS: api((a, b) => { const [tabId, d] = typeof a === 'number' ? [a, b] : [null, a]; return call('tabs.insertCSS', [tabId, d || {}]).then(() => undefined); }),
        removeCSS: api((a, b) => { const [tabId, d] = typeof a === 'number' ? [a, b] : [null, a]; return call('tabs.removeCSS', [tabId, d || {}]).then(() => undefined); }),
        connect: (tabId, info) => {
          const portId = 'p' + Math.random().toString(36).slice(2);
          const port = makePort(portId, (info && info.name) || '', undefined);
          call('port.connect', [portId, (info && info.name) || '', tabId]);
          return port;
        },
        onUpdated: event('tabs.onUpdated'), onActivated: event('tabs.onActivated'), onCreated: event('tabs.onCreated'),
        onRemoved: event('tabs.onRemoved'), onReplaced: event('tabs.onReplaced'), onHighlighted: event('tabs.onHighlighted'),
        onMoved: event('tabs.onMoved'), onAttached: event('tabs.onAttached'), onDetached: event('tabs.onDetached'), onZoomChange: event('tabs.onZoomChange'),
      };
      // the extras: a tab id first (or the current tab), as Chrome takes them
      const tabArg = (name) => api((a, b) => call('tabsx.' + name, typeof a === 'number' || a == null ? [a == null ? null : a, b || {}] : [null, a]));
      for (const k of ['captureVisibleTab', 'goBack', 'goForward', 'duplicate', 'detectLanguage', 'getZoom', 'discard']) tabs[k] = tabArg(k);
      tabs.move = api((ids, o) => call('tabsx.move', [Array.isArray(ids) ? ids[0] : ids, o || {}]));
      tabs.highlight = remote('tabsx.highlight');
      tabs.setZoom = api(() => undefined); tabs.getZoomSettings = api(() => ({ mode: 'automatic', scope: 'per-origin', defaultZoomFactor: 1 })); tabs.setZoomSettings = api(() => undefined);
      for (const k of ['group', 'ungroup']) tabs[k] = api(() => Promise.reject(new Error('tabs.' + k + ' is not supported in mi.')));
      const fnSource = (d) => (d.func ? '(' + d.func.toString() + ').apply(null, ' + JSON.stringify(d.args || []) + ')' : null);
      const scripting = {
        executeScript: api((d) => call('scripting.executeScript', [Object.assign({}, d, { func: undefined, code: fnSource(d) })])),
        insertCSS: api((d) => call('scripting.insertCSS', [d]).then(() => undefined)),
        removeCSS: api((d) => call('scripting.removeCSS', [d]).then(() => undefined)),
        registerContentScripts: api((s) => call('scripting.registerContentScripts', [s || []]).then(() => undefined)),
        updateContentScripts: api((s) => call('scripting.updateContentScripts', [s || []]).then(() => undefined)),
        getRegisteredContentScripts: api((f) => call('scripting.getRegisteredContentScripts', [f || {}])),
        unregisterContentScripts: api((f) => call('scripting.unregisterContentScripts', [f || {}]).then(() => undefined)),
        ExecutionWorld: { ISOLATED: 'ISOLATED', MAIN: 'MAIN' },
      };

      // ---- the toolbar button (action / browserAction / pageAction), shown in mi's tab menu
      const action = {
        setBadgeText: remote('action.setBadgeText'), getBadgeText: remote('action.getBadgeText'),
        setBadgeBackgroundColor: remote('action.setBadgeBackgroundColor'), getBadgeBackgroundColor: api(() => [0, 0, 0, 0]),
        setBadgeTextColor: api(() => undefined), getBadgeTextColor: api(() => [255, 255, 255, 255]),
        setTitle: remote('action.setTitle'), getTitle: remote('action.getTitle'),
        setIcon: api((d) => call('action.setIcon', [{ path: d && d.path, tabId: d && d.tabId }]).then(() => undefined)),
        setPopup: remote('action.setPopup'), getPopup: remote('action.getPopup'),
        enable: remote('action.enable'), disable: remote('action.disable'), isEnabled: api(() => true),
        openPopup: remote('action.openPopup'), getUserSettings: api(() => ({ isOnToolbar: true })),
        show: remote('action.enable'), hide: remote('action.disable'),
        onClicked: event('action.onClicked'),
      };

      // Firefox's contentScripts.register: scripts (files or code) added while running, until the extension stops
      out.contentScripts = {
        register: api(async (o) => {
          const id = await call('contentScripts.register', [o || {}]);
          return { unregister: api(() => call('contentScripts.unregister', [id]).then(() => undefined)) };
        }),
      };
      // chrome.privacy: each setting an object with get, set, clear and onChange
      const setting = (key) => ({
        get: api((d) => call('privacy.get', [key, d || {}])),
        set: api((d) => call('privacy.set', [key, d || {}]).then(() => undefined)),
        clear: api(() => call('privacy.clear', [key]).then(() => undefined)),
        onChange: event('privacy.' + key),
      });
      const group = (prefix, keys) => Object.fromEntries(keys.map((k) => [k, setting(prefix + '.' + k)]));
      out.tabs = tabs;
      out.scripting = scripting;
      out.action = action; out.browserAction = action; out.pageAction = action;
      out.alarms = {
        create: api((a, b) => { const [name, info] = typeof a === 'string' ? [a, b] : ['', a]; return call('alarms.create', [name, info || {}]).then(() => undefined); }),
        get: api((name) => call('alarms.get', [name || ''])), getAll: remote('alarms.getAll'),
        clear: api((name) => call('alarms.clear', [name || ''])), clearAll: remote('alarms.clearAll'),
        onAlarm: event('alarms.onAlarm'),
      };
      out.notifications = {
        create: api((a, b) => { const [id, o] = typeof a === 'string' ? [a, b] : ['', a]; return call('notifications.create', [id, o || {}]); }),
        update: api(() => true), clear: remote('notifications.clear'), getAll: remote('notifications.getAll'),
        getPermissionLevel: api(() => 'granted'),
        onClicked: event('notifications.onClicked'), onClosed: event('notifications.onClosed'),
        onButtonClicked: event('notifications.onButtonClicked'), onShowSettings: event('notifications.onShowSettings'),
      };
      out.permissions = {
        contains: remote('permissions.contains'), getAll: remote('permissions.getAll'),
        request: remote('permissions.request'), remove: remote('permissions.remove'),
        onAdded: event('permissions.onAdded'), onRemoved: event('permissions.onRemoved'),
      };
      const theWindow = () => ({ id: 1, focused: true, top: 0, left: 0, width: screen.width, height: screen.height, incognito: !!cfg.incognito, type: 'normal', state: 'maximized', alwaysOnTop: false });
      out.windows = {
        WINDOW_ID_CURRENT: -2, WINDOW_ID_NONE: -1,
        getCurrent: api(async (o) => Object.assign(theWindow(), o && o.populate ? { tabs: await call('tabs.query', [{}]) } : {})),
        getLastFocused: api(async (o) => Object.assign(theWindow(), o && o.populate ? { tabs: await call('tabs.query', [{}]) } : {})),
        get: api(async (id, o) => Object.assign(theWindow(), o && o.populate ? { tabs: await call('tabs.query', [{}]) } : {})),
        getAll: api(async (o) => [Object.assign(theWindow(), o && o.populate ? { tabs: await call('tabs.query', [{}]) } : {})]),
        create: api(async (o) => { const t = await call('tabs.create', [{ url: Array.isArray(o && o.url) ? o.url[0] : o && o.url }]); return Object.assign(theWindow(), { tabs: [t] }); }),
        update: api(() => theWindow()), remove: api(() => undefined),
        onCreated: event('windows.onCreated'), onRemoved: event('windows.onRemoved'), onFocusChanged: event('windows.onFocusChanged'), onBoundsChanged: event('windows.onBoundsChanged'),
      };
      // As in a browser, a namespace is there only for an extension whose permissions include it. One mi doesn't have is
      // a stand-in so the extension doesn't crash, and only for permissions it needs (one it only asks for later stays
      // missing, so its own "is this here?" checks work); such extensions are rated Maybe or worse before installing.
      const perms = new Set(cfg.permissions || []);
      const has = (...names) => names.some((n) => perms.has(n));
      // also one it may ask for later (mi checks it was allowed on each call)
      const optional = new Set((cfg.manifest && cfg.manifest.optional_permissions) || []);
      const may = (...names) => names.some((n) => perms.has(n) || optional.has(n));
      // ---- items in mi's long-press and selected-text menus (extensions/ExtensionMenus); ids go to mi as text, and come
      // back as the extension gave them (Chrome makes number ids for items without one)
      const menuClicks = {}, menuIds = {};
      let menuSeq = 0;
      const menuProps = (p) => {
        const o = Object.assign({}, p);
        delete o.onclick; delete o.icons;
        if (o.parentId != null) o.parentId = String(o.parentId);
        if (o.contexts != null && !Array.isArray(o.contexts)) o.contexts = [o.contexts];
        return o;
      };
      const withError = (p, cb) => p.then(() => { if (cb) cb(); }, (e) => { if (cb) { lastError = { message: String((e && e.message) || e) }; try { cb(); } finally { lastError = undefined; } } else console.warn(e); });
      if (may('contextMenus', 'menus')) {
        const menus = {
          create(p, cb) {
            const id = p && p.id != null ? p.id : ++menuSeq;
            menuIds[String(id)] = id;
            if (p && typeof p.onclick === 'function') menuClicks[String(id)] = p.onclick;
            withError(call('menus.create', [Object.assign(menuProps(p), { id: String(id) })]), cb);
            return id;
          },
          update: api((id, p) => { if (p && typeof p.onclick === 'function') menuClicks[String(id)] = p.onclick; return call('menus.update', [String(id), menuProps(p)]).then(() => undefined); }),
          remove: api((id) => { delete menuClicks[String(id)]; return call('menus.remove', [String(id)]).then(() => undefined); }),
          removeAll: api(() => call('menus.removeAll', []).then(() => undefined)),
          refresh: api(() => undefined), overrideContext: () => {}, getTargetElement: () => null,
          onClicked: event('menus.onClicked'), onShown: event('menus.onShown'), onHidden: event('menus.onHidden'),
          ACTION_MENU_TOP_LEVEL_LIMIT: 6,
          ContextType: Object.fromEntries(['all', 'page', 'frame', 'selection', 'link', 'editable', 'image', 'video', 'audio', 'launcher', 'browser_action', 'page_action', 'action', 'tab', 'bookmark', 'tools_menu', 'password'].map((k) => [k.toUpperCase(), k])),
          ItemType: { NORMAL: 'normal', CHECKBOX: 'checkbox', RADIO: 'radio', SEPARATOR: 'separator' },
        };
        out.contextMenus = menus; out.menus = menus;
      }
      channel.menuClicked = (info, tab) => {
        const key = String(info.menuItemId);
        info.menuItemId = key in menuIds ? menuIds[key] : info.menuItemId;
        if (info.parentMenuItemId != null && String(info.parentMenuItemId) in menuIds) info.parentMenuItemId = menuIds[String(info.parentMenuItemId)];
        if (menuClicks[key]) { try { menuClicks[key](info, tab); } catch (e) { console.error(e); } }
        fire('menus.onClicked', info, tab);
      };
      // ---- web requests (extensions/ExtensionRequests): listeners register with mi, which asks them about each matching request
      const wrFns = {};
      let wrSeq = 0;
      const wrEvent = (name) => {
        const mine = []; // [fn, id]
        return {
          addListener(fn, filter, extra) {
            if (typeof fn !== 'function') return;
            const id = 'w' + (++wrSeq) + Math.random().toString(36).slice(2, 6);
            wrFns[id] = fn; mine.push([fn, id]);
            call('webRequest.addListener', [name, id, filter || { urls: ['<all_urls>'] }, extra || []]);
          },
          removeListener(fn) { for (let i = mine.length - 1; i >= 0; i--) if (mine[i][0] === fn) { delete wrFns[mine[i][1]]; call('webRequest.removeListener', [mine[i][1]]); mine.splice(i, 1); } },
          hasListener(fn) { return mine.some((m) => m[0] === fn); },
          hasListeners() { return mine.length > 0; },
        };
      };
      if (has('webRequest')) out.webRequest = {
        onBeforeRequest: wrEvent('onBeforeRequest'), onBeforeSendHeaders: wrEvent('onBeforeSendHeaders'), onSendHeaders: wrEvent('onSendHeaders'),
        onHeadersReceived: wrEvent('onHeadersReceived'), onAuthRequired: wrEvent('onAuthRequired'), onResponseStarted: wrEvent('onResponseStarted'),
        onBeforeRedirect: wrEvent('onBeforeRedirect'), onCompleted: wrEvent('onCompleted'), onErrorOccurred: wrEvent('onErrorOccurred'),
        handlerBehaviorChanged: api(() => undefined), MAX_HANDLER_BEHAVIOR_CHANGED_CALLS_PER_10_MINUTES: 20,
        ResourceType: { MAIN_FRAME: 'main_frame', SUB_FRAME: 'sub_frame', STYLESHEET: 'stylesheet', SCRIPT: 'script', IMAGE: 'image', FONT: 'font', OBJECT: 'object', XMLHTTPREQUEST: 'xmlhttprequest', PING: 'ping', CSP_REPORT: 'csp_report', MEDIA: 'media', WEBSOCKET: 'websocket', OTHER: 'other' },
        OnBeforeRequestOptions: { BLOCKING: 'blocking', REQUEST_BODY: 'requestBody', EXTRA_HEADERS: 'extraHeaders' },
        OnBeforeSendHeadersOptions: { REQUEST_HEADERS: 'requestHeaders', BLOCKING: 'blocking', EXTRA_HEADERS: 'extraHeaders' },
        OnSendHeadersOptions: { REQUEST_HEADERS: 'requestHeaders', EXTRA_HEADERS: 'extraHeaders' },
        OnHeadersReceivedOptions: { BLOCKING: 'blocking', RESPONSE_HEADERS: 'responseHeaders', EXTRA_HEADERS: 'extraHeaders' },
        OnResponseStartedOptions: { RESPONSE_HEADERS: 'responseHeaders', EXTRA_HEADERS: 'extraHeaders' },
        OnCompletedOptions: { RESPONSE_HEADERS: 'responseHeaders', EXTRA_HEADERS: 'extraHeaders' },
      };
      // asked about a request: the listener's answer (it may be a promise, as in Firefox) goes back to mi
      channel.webRequest = (id, details, reply) => {
        const fn = wrFns[id];
        if (!fn) return reply(undefined, null);
        let r;
        try { r = fn(details); } catch (e) { console.error(e); return reply(undefined, null); }
        if (r && typeof r.then === 'function') r.then((v) => reply(v == null ? null : v, true), () => reply(undefined, null));
        else reply(r == null ? null : r, true);
      };
      if (has('declarativeNetRequest', 'declarativeNetRequestWithHostAccess', 'declarativeNetRequestFeedback')) out.declarativeNetRequest = {
        updateDynamicRules: api((o) => call('dnr.updateDynamicRules', [o || {}]).then(() => undefined)),
        getDynamicRules: api((f) => call('dnr.getDynamicRules', []).then((r) => (f && f.ruleIds ? r.filter((x) => f.ruleIds.includes(x.id)) : r))),
        updateSessionRules: api((o) => call('dnr.updateSessionRules', [o || {}]).then(() => undefined)),
        getSessionRules: api((f) => call('dnr.getSessionRules', []).then((r) => (f && f.ruleIds ? r.filter((x) => f.ruleIds.includes(x.id)) : r))),
        updateEnabledRulesets: api((o) => call('dnr.updateEnabledRulesets', [o || {}]).then(() => undefined)),
        getEnabledRulesets: remote('dnr.getEnabledRulesets'),
        updateStaticRules: api(() => undefined), getDisabledRuleIds: api(() => []),
        getAvailableStaticRuleCount: api(() => 30000), isRegexSupported: api(() => ({ isSupported: true })),
        setExtensionActionOptions: api(() => undefined), getMatchedRules: api(() => ({ rulesMatchedInfo: [] })),
        testMatchOutcome: api(() => ({ matchedRules: [] })),
        onRuleMatchedDebug: event('declarativeNetRequest.onRuleMatchedDebug'),
        MAX_NUMBER_OF_DYNAMIC_RULES: 30000, MAX_NUMBER_OF_SESSION_RULES: 5000, MAX_NUMBER_OF_UNSAFE_DYNAMIC_RULES: 5000, MAX_NUMBER_OF_UNSAFE_SESSION_RULES: 5000,
        MAX_NUMBER_OF_REGEX_RULES: 1000, MAX_NUMBER_OF_STATIC_RULESETS: 100, MAX_NUMBER_OF_ENABLED_STATIC_RULESETS: 50, GUARANTEED_MINIMUM_STATIC_RULES: 30000,
        DYNAMIC_RULESET_ID: '_dynamic', SESSION_RULESET_ID: '_session',
        RuleActionType: { BLOCK: 'block', REDIRECT: 'redirect', ALLOW: 'allow', UPGRADE_SCHEME: 'upgradeScheme', MODIFY_HEADERS: 'modifyHeaders', ALLOW_ALL_REQUESTS: 'allowAllRequests' },
        ResourceType: { MAIN_FRAME: 'main_frame', SUB_FRAME: 'sub_frame', STYLESHEET: 'stylesheet', SCRIPT: 'script', IMAGE: 'image', FONT: 'font', OBJECT: 'object', XMLHTTPREQUEST: 'xmlhttprequest', PING: 'ping', CSP_REPORT: 'csp_report', MEDIA: 'media', WEBSOCKET: 'websocket', OTHER: 'other' },
        HeaderOperation: { APPEND: 'append', SET: 'set', REMOVE: 'remove' },
        DomainType: { FIRST_PARTY: 'firstParty', THIRD_PARTY: 'thirdParty' },
        RequestMethod: { GET: 'get', POST: 'post', PUT: 'put', DELETE: 'delete', HEAD: 'head', OPTIONS: 'options', PATCH: 'patch', CONNECT: 'connect', OTHER: 'other' },
      };
      // ---- page loads (extensions/ExtensionNavigation), each listener with Chrome's URL filters
      const navListeners = {};
      const urlFilterOk = (f, href) => {
        let u; try { u = new URL(href); } catch (_) { return false; }
        const host = u.hostname, path = u.pathname, query = u.search.replace(/^\?/, '');
        const t = (v, fn) => v === undefined || fn(v);
        return t(f.hostContains, (v) => host.includes(v)) && t(f.hostEquals, (v) => host === v) && t(f.hostPrefix, (v) => host.startsWith(v)) && t(f.hostSuffix, (v) => host.endsWith(v)) &&
          t(f.pathContains, (v) => path.includes(v)) && t(f.pathEquals, (v) => path === v) && t(f.pathPrefix, (v) => path.startsWith(v)) && t(f.pathSuffix, (v) => path.endsWith(v)) &&
          t(f.queryContains, (v) => query.includes(v)) && t(f.queryEquals, (v) => query === v) && t(f.queryPrefix, (v) => query.startsWith(v)) && t(f.querySuffix, (v) => query.endsWith(v)) &&
          t(f.urlContains, (v) => href.includes(v)) && t(f.urlEquals, (v) => href === v) && t(f.urlPrefix, (v) => href.startsWith(v)) && t(f.urlSuffix, (v) => href.endsWith(v)) &&
          t(f.urlMatches, (v) => new RegExp(v).test(href)) && t(f.originAndPathMatches, (v) => new RegExp(v).test(u.origin + u.pathname)) &&
          t(f.schemes, (v) => v.includes(u.protocol.replace(':', ''))) &&
          t(f.ports, (v) => v.some((p) => (Array.isArray(p) ? (Number(u.port) >= p[0] && Number(u.port) <= p[1]) : Number(u.port || (u.protocol === 'https:' ? 443 : 80)) === p)));
      };
      const navEvent = (name) => {
        const list = (navListeners[name] = []);
        return {
          addListener(fn, filter) { if (typeof fn === 'function') list.push([fn, filter && filter.url ? filter.url : null]); },
          removeListener(fn) { for (let i = list.length - 1; i >= 0; i--) if (list[i][0] === fn) list.splice(i, 1); },
          hasListener(fn) { return list.some((l) => l[0] === fn); },
          hasListeners() { return list.length > 0; },
        };
      };
      channel.navigation = (name, d) => {
        for (const [fn, filters] of (navListeners[name] || []).slice()) {
          if (filters && !filters.some((f) => urlFilterOk(f, d.url || ''))) continue;
          try { fn(d); } catch (e) { console.error(e); }
        }
      };
      if (has('webNavigation')) out.webNavigation = Object.assign(Object.fromEntries(['onBeforeNavigate', 'onCommitted', 'onDOMContentLoaded', 'onCompleted', 'onErrorOccurred',
        'onCreatedNavigationTarget', 'onReferenceFragmentUpdated', 'onTabReplaced', 'onHistoryStateUpdated'].map((n) => [n, navEvent(n)])), {
        getFrame: remote('webNavigation.getFrame'), getAllFrames: remote('webNavigation.getAllFrames'),
        TransitionType: { LINK: 'link', TYPED: 'typed', AUTO_BOOKMARK: 'auto_bookmark', AUTO_SUBFRAME: 'auto_subframe', MANUAL_SUBFRAME: 'manual_subframe', GENERATED: 'generated', START_PAGE: 'start_page', FORM_SUBMIT: 'form_submit', RELOAD: 'reload', KEYWORD: 'keyword', KEYWORD_GENERATED: 'keyword_generated' },
      });
      if (has('privacy')) out.privacy = {
        network: group('network', ['networkPredictionEnabled', 'webRTCIPHandlingPolicy', 'peerConnectionEnabled', 'httpsOnlyMode', 'globalPrivacyControl']),
        services: group('services', ['alternateErrorPagesEnabled', 'autofillAddressEnabled', 'autofillCreditCardEnabled', 'autofillEnabled', 'passwordSavingEnabled',
          'safeBrowsingEnabled', 'safeBrowsingExtendedReportingEnabled', 'searchSuggestEnabled', 'spellingServiceEnabled', 'translationServiceEnabled']),
        websites: group('websites', ['adMeasurementEnabled', 'doNotTrackEnabled', 'fledgeEnabled', 'hyperlinkAuditingEnabled', 'protectedContentEnabled', 'referrersEnabled',
          'relatedWebsiteSetsEnabled', 'thirdPartyCookiesAllowed', 'topicsEnabled', 'cookieConfig', 'firstPartyIsolate', 'resistFingerprinting', 'trackingProtectionMode']),
      };
      // namespaces mi has for real (extensions/ExtensionPlatform): there for an extension that needs them or may ask for them
      // (mi checks it was allowed on each call)
      if (may('cookies')) out.cookies = {
        get: remote('cookies.get'), getAll: api((d) => call('cookies.getAll', [d || {}])), set: remote('cookies.set'), remove: remote('cookies.remove'),
        getAllCookieStores: api(() => call('cookies.getAllCookieStores', [{}])), getPartitionKey: api(() => ({})),
        onChanged: event('cookies.onChanged'),
        OnChangedCause: { EVICTED: 'evicted', EXPIRED: 'expired', EXPLICIT: 'explicit', EXPIRED_OVERWRITE: 'expired_overwrite', OVERWRITE: 'overwrite' },
        SameSiteStatus: { NO_RESTRICTION: 'no_restriction', LAX: 'lax', STRICT: 'strict', UNSPECIFIED: 'unspecified' },
      };
      if (may('identity')) out.identity = {
        getRedirectURL: (path) => cfg.identityRedirect + String(path || '').replace(/^\//, ''),
        launchWebAuthFlow: remote('identity.launchWebAuthFlow'),
        getAuthToken: remote('identity.getAuthToken'), getProfileUserInfo: remote('identity.getProfileUserInfo'), getAccounts: remote('identity.getAccounts'),
        removeCachedAuthToken: remote('identity.removeCachedAuthToken'), clearAllCachedAuthTokens: remote('identity.clearAllCachedAuthTokens'),
        onSignInChanged: event('identity.onSignInChanged'),
      };
      if (may('sidePanel') || (cfg.manifest && cfg.manifest.side_panel)) out.sidePanel = {
        setOptions: remote('sidePanel.setOptions'), getOptions: remote('sidePanel.getOptions'),
        setPanelBehavior: remote('sidePanel.setPanelBehavior'), getPanelBehavior: remote('sidePanel.getPanelBehavior'),
        open: remote('sidePanel.open'), close: remote('sidePanel.close'), onOpened: event('sidePanel.onOpened'), onClosed: event('sidePanel.onClosed'),
      };
      if (cfg.manifest && cfg.manifest.sidebar_action) {
        let sidebarTitle = cfg.manifest.sidebar_action.default_title || cfg.name;
        out.sidebarAction = {
          setPanel: api((d) => call('sidePanel.setOptions', [{ path: d && d.panel }]).then(() => undefined)),
          getPanel: api(async () => cfg.baseUrl + String(((await call('sidePanel.getOptions', [{}])) || {}).path || '').replace(/^\//, '')),
          open: remote('sidePanel.open'), close: remote('sidePanel.close'), toggle: remote('sidePanel.toggle'), isOpen: remote('sidePanel.isOpen'),
          setTitle: api((d) => { sidebarTitle = (d && d.title) || sidebarTitle; }), getTitle: api(() => sidebarTitle), setIcon: api(() => undefined),
        };
      }
      if (may('idle')) out.idle = {
        queryState: api(() => call('idle.queryState', [])), setDetectionInterval: () => {}, getAutoLockDelay: api(() => 0),
        onStateChanged: event('idle.onStateChanged'), IdleState: { ACTIVE: 'active', IDLE: 'idle', LOCKED: 'locked' },
      };
      if (may('dns')) out.dns = { resolve: api((host) => call('dns.resolve', [host])) };
      // itself only: other extensions aren't shown to an extension
      out.management = {
        getSelf: remote('management.getSelf'), getAll: remote('management.getAll'), get: remote('management.get'),
        uninstallSelf: remote('management.uninstallSelf'), setEnabled: remote('management.setEnabled'),
        getPermissionWarningsById: remote('management.getPermissionWarningsById'), getPermissionWarningsByManifest: remote('management.getPermissionWarningsByManifest'),
        onInstalled: event('management.onInstalled'), onUninstalled: event('management.onUninstalled'), onEnabled: event('management.onEnabled'), onDisabled: event('management.onDisabled'),
      };
      if (may('browsingData')) out.browsingData = Object.assign(
        { remove: api((o, what) => call('browsingData.remove', [o || {}, what || {}]).then(() => undefined)), settings: remote('browsingData.settings') },
        Object.fromEntries(['Cookies', 'Cache', 'History', 'LocalStorage', 'IndexedDB', 'ServiceWorkers', 'Downloads', 'CacheStorage', 'FormData', 'Passwords', 'PluginData', 'WebSQL', 'FileSystems', 'AppCache']
          .map((k) => ['remove' + k, api((o) => call('browsingData.remove' + k, [o || {}]).then(() => undefined))])),
      );
      if (may('downloads')) out.downloads = {
        download: api((o) => call('downloads.download', [o || {}])), search: api((q) => call('downloads.search', [q || {}])),
        pause: remote('downloads.pause'), resume: remote('downloads.resume'), cancel: remote('downloads.cancel'),
        erase: api((q) => call('downloads.erase', [q || {}])), open: remote('downloads.open'), show: remote('downloads.show'),
        showDefaultFolder: remote('downloads.showDefaultFolder'), getFileIcon: remote('downloads.getFileIcon'),
        removeFile: api(() => Promise.reject(new Error('downloads.removeFile is not supported in mi.'))), setShelfEnabled: () => {}, setUiOptions: api(() => undefined),
        acceptDanger: api(() => undefined),
        onCreated: event('downloads.onCreated'), onChanged: event('downloads.onChanged'), onErased: event('downloads.onErased'), onDeterminingFilename: event('downloads.onDeterminingFilename'),
      };
      if (may('offscreen')) out.offscreen = {
        createDocument: api((o) => call('offscreen.createDocument', [Object.assign({}, o, { url: url(o && o.url) })]).then(() => undefined)),
        closeDocument: api(() => call('offscreen.closeDocument', []).then(() => undefined)), hasDocument: remote('offscreen.hasDocument'),
        Reason: Object.fromEntries(['TESTING', 'AUDIO_PLAYBACK', 'IFRAME_SCRIPTING', 'DOM_SCRAPING', 'BLOBS', 'DOM_PARSER', 'USER_MEDIA', 'DISPLAY_MEDIA', 'WEB_RTC', 'CLIPBOARD', 'LOCAL_STORAGE', 'WORKERS', 'BATTERY_STATUS', 'MATCH_MEDIA', 'GEOLOCATION'].map((k) => [k, k])),
      };
      // the rest: not in mi; a stand-in for an extension that needs it, so it doesn't crash
      for (const ns of ['history', 'bookmarks', 'topSites', 'sessions', 'omnibox', 'tabGroups', 'search',
        'contentSettings', 'fontSettings', 'tts', 'proxy', 'debugger', 'userScripts', 'power', 'system'])
        if (has(ns)) out[ns] = stub(ns);
      // always there in a browser (no permission needed): keyboard shortcuts (none on a phone)
      out.commands = { getAll: api(() => Object.entries((cfg.manifest && cfg.manifest.commands) || {}).map(([name, c]) => ({ name, description: (c && c.description) || '', shortcut: '' }))), onCommand: event('commands.onCommand') };
    }

    // ---- events mi sends this context
    channel.on((name, data, reply) => {
      if (name === 'webRequest.event') { if (channel.webRequest) channel.webRequest(data.id, data.details, reply); else reply(undefined, null); return; }
      if (name === 'runtime.onMessage' || name === 'runtime.onMessageExternal') {
        // listeners answer by sendResponse, by returning true and answering later, or by returning a promise (Firefox)
        let answered = false, waiting = false;
        const respond = (v) => { if (!answered) { answered = true; reply(v === undefined ? null : v, true); } };
        for (const fn of (listeners[name] || []).slice()) {
          let r;
          try { r = fn(data.message, data.sender, respond); } catch (e) { console.error(e); }
          if (r === true) waiting = true;
          else if (r && typeof r.then === 'function') { waiting = true; r.then(respond, (e) => { if (!answered) { answered = true; reply(String((e && e.message) || e), false); } }); }
        }
        if (!waiting && !answered) reply(undefined, null); // no answer from this context
        return;
      }
      if (name === 'runtime.onConnect') { fire('runtime.onConnect', makePort(data.portId, data.name, data.sender)); return; }
      if (name === 'port.message') { if (ports[data.portId]) fire('port.' + data.portId + '.message', data.message, ports[data.portId]); return; }
      if (name === 'port.disconnect') { const p = ports[data.portId]; if (p) { delete ports[data.portId]; fire('port.' + data.portId + '.disconnect', p); } return; }
      if (name.startsWith('webNavigation.')) { if (channel.navigation) channel.navigation(name.slice(14), Array.isArray(data) ? data[0] : data); return; }
      if (name === 'ws.event') { const s = sockets[data.id]; if (s) s._event(data); return; }
      if (name === 'menus.onClicked') { if (channel.menuClicked) channel.menuClicked(data[0], data[1]); return; }
      if (name === 'privacy.changed') { fire('privacy.' + data.key, { value: data.value, levelOfControl: 'controlled_by_this_extension' }); return; }
      if (name === 'storage.changed') { fire('storage.' + data.area + '.onChanged', data.changes); fire('storage.onChanged', data.changes, data.area); return; }
      fire(name, ...(Array.isArray(data) ? data : [data]));
    });

    return out;
  };
})
