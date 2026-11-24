// Scriptlets: uBlock Origin-style scripts that run in a page before its own, for what blocking requests and hiding
// elements can't reach (a site's ads in its own data, as YouTube's; anti-adblock walls; pop-unders). Document-start
// script for every frame: it asks mi which run on this site (window.miScriptlets.get, answered at once by
// adblock/ScriptletRules from the filter lists) and runs them. Each is written to behave as uBlock's of the same name,
// for the arguments lists use; one mi doesn't have is skipped.
(() => {
  const bridge = window.miScriptlets;
  if (!bridge || window.__miScriptlets) return;
  let list;
  try { list = JSON.parse(bridge.get(location.hostname || '')); } catch (_) { return; }
  if (!Array.isArray(list) || !list.length) return;
  Object.defineProperty(window, '__miScriptlets', { value: true });

  // ---- helpers (taken before the page can change them)
  const { defineProperty, getOwnPropertyDescriptor, keys } = Object;
  const { apply } = Reflect;
  const parse = JSON.parse, stringify = JSON.stringify;
  const magic = String.fromCharCode(Date.now() % 26 + 97) + Math.floor(Math.random() * 982451653 + 982451653).toString(36);
  // "/re/flags" is a regular expression; other text matches as itself; "" matches anything
  const toRe = (p, flags = '') => {
    if (p === undefined || p === null || p === '') return /^/;
    const m = /^\/(.+)\/([gimsuy]*)$/.exec(p);
    if (m) { try { return new RegExp(m[1], m[2] || flags); } catch (_) { return /^\b$/; } }
    return new RegExp(String(p).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), flags);
  };
  // a needle that may start with "!" (doesn't match)
  const tester = (p) => { const not = typeof p === 'string' && p.startsWith('!'); const re = toRe(not ? p.slice(1) : p); return (s) => re.test(String(s)) !== not; };
  // set-constant's values
  const constant = (raw) => {
    switch (raw) {
      case 'undefined': return { v: undefined }; case 'false': return { v: false }; case 'true': return { v: true };
      case 'null': return { v: null }; case "''": case 'emptyStr': case '': return { v: '' };
      case 'noopFunc': return { v: function () {} }; case 'trueFunc': return { v: function () { return true; } };
      case 'falseFunc': return { v: function () { return false; } }; case 'throwFunc': return { v: function () { throw new Error(magic); } };
      case 'emptyArr': return { v: [] }; case 'emptyObj': return { v: {} }; case '$remove$': return { remove: true };
      case 'yes': case 'no': case 'on': case 'off': return { v: raw };
    }
    if (/^-?\d+$/.test(raw) && Math.abs(Number(raw)) <= 0x7fff) return { v: Number(raw) };
    try { return { v: parse(raw) }; } catch (_) { return null; }
  };
  // reaches "a.b.c" on window, waiting for a and a.b to be set, then hands c's owner to [leaf]
  const trapChain = (owner, chain, leaf) => {
    const dot = chain.indexOf('.');
    if (dot === -1) { leaf(owner, chain); return; }
    const prop = chain.slice(0, dot), rest = chain.slice(dot + 1);
    const isObj = (x) => x !== null && (typeof x === 'object' || typeof x === 'function');
    let v = owner[prop];
    if (isObj(v)) { trapChain(v, rest, leaf); return; }
    const d = getOwnPropertyDescriptor(owner, prop);
    if (d && d.configurable === false) return;
    // another rule may wait on the same name ("x.a" and "x.b"): its trap stays, under this one
    try {
      defineProperty(owner, prop, {
        configurable: true, enumerable: true,
        get() { return d && d.get ? d.get.call(this) : v; },
        set(a) { if (d && d.set) d.set.call(this, a); else v = a; if (isObj(a)) trapChain(a, rest, leaf); },
      });
    } catch (_) {}
  };
  const whenReady = (fn) => { if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn, { once: true }); else fn(); };
  const observe = (fn) => { whenReady(fn); new MutationObserver(fn).observe(document, { childList: true, subtree: true }); };
  // the page's errors from aborted scripts aren't shown
  window.addEventListener('error', (e) => { if (String(e.message).includes(magic)) e.preventDefault(); }, true);
  // a request's details, for "propsToMatch" ("url:/re/ method:POST", or a bare needle for the address)
  const matchProps = (props, details) => {
    if (!props) return true;
    return props.split(/\s+/).filter(Boolean).every((tok) => {
      const m = /^(\w+):(.*)$/.exec(tok);
      const [k, v] = m && m[1] in details ? [m[1], m[2]] : ['url', tok];
      return tester(v)(details[k] === undefined ? '' : details[k]);
    });
  };
  const fetchDetails = (args) => {
    const [input, init] = args; const req = input instanceof Request ? input : null;
    return { url: req ? req.url : String(input), method: ((init && init.method) || (req && req.method) || 'GET').toUpperCase(), body: (init && typeof init.body === 'string') ? init.body : '' };
  };
  // "a.b.[].c": [] (or *) is every key; [-] removes the array items that have what follows
  const segs = (path) => path.split('.').filter(Boolean);
  const has = (o, s) => {
    if (!s.length) return true;
    if (o === null || typeof o !== 'object') return false;
    const [k, ...rest] = s;
    if (k === '[]' || k === '*' || k === '[-]') return keys(o).some((x) => has(o[x], rest));
    return k in o && has(o[k], rest);
  };
  const prune = (o, s) => {
    if (o === null || typeof o !== 'object' || !s.length) return;
    const [k, ...rest] = s;
    if (k === '[-]') { if (Array.isArray(o)) for (let i = o.length - 1; i >= 0; i--) if (has(o[i], rest)) o.splice(i, 1); return; }
    if (k === '[]' || k === '*') { for (const x of keys(o)) { if (rest.length) prune(o[x], rest); else delete o[x]; } return; }
    if (!rest.length) { delete o[k]; return; }
    prune(o[k], rest);
  };
  const pruner = (rawPrune, rawNeedle) => {
    const p = String(rawPrune || '').split(/ +/).filter(Boolean).map(segs);
    const n = String(rawNeedle || '').split(/ +/).filter(Boolean).map(segs);
    return (o) => { if (o && typeof o === 'object' && n.every((s) => has(o, s))) p.forEach((s) => prune(o, s)); return o; };
  };
  // options after the positional arguments: "key, value" pairs
  const options = (rest) => { const o = {}; for (let i = 0; i + 1 < rest.length; i += 2) o[rest[i]] = rest[i + 1]; return o; };

  // ---- fetch and XHR responses, changed for the scriptlets that ask
  const fetchHooks = []; // [matches(details), change(Response) → Promise<Response>]
  let fetchHooked = false;
  const hookFetch = (matches, change) => {
    fetchHooks.push([matches, change]);
    if (fetchHooked) return; fetchHooked = true;
    window.fetch = new Proxy(window.fetch, {
      apply(target, thisArg, args) {
        const d = fetchDetails(args);
        const mine = fetchHooks.filter(([m]) => m(d));
        const p = apply(target, thisArg, args);
        return mine.length ? p.then((r) => mine.reduce((acc, [, c]) => acc.then((x) => c(x).catch(() => x)), Promise.resolve(r))) : p;
      },
    });
  };
  const remake = (r, text) => { const n = new Response(text, { status: r.status, statusText: r.statusText, headers: r.headers }); defineProperty(n, 'url', { value: r.url }); defineProperty(n, 'redirected', { value: r.redirected }); return n; };
  const xhrHooks = []; // [matches(details), change(text) → text]
  let xhrHooked = false;
  const hookXhr = (matches, change) => {
    xhrHooks.push([matches, change]);
    if (xhrHooked) return; xhrHooked = true;
    const P = XMLHttpRequest.prototype, info = new WeakMap(), done = new WeakMap();
    const open = P.open;
    P.open = function (method, url) { info.set(this, { method: String(method).toUpperCase(), url: String(url) }); return apply(open, this, arguments); };
    const text = (xhr, raw) => {
      const d = info.get(xhr); if (!d || xhr.readyState !== 4) return raw;
      if (done.has(xhr)) return done.get(xhr);
      const out = xhrHooks.filter(([m]) => m(d)).reduce((t, [, c]) => { try { return c(t); } catch (_) { return t; } }, raw);
      done.set(xhr, out); return out;
    };
    for (const k of ['responseText', 'response']) {
      const desc = getOwnPropertyDescriptor(P, k);
      if (!desc || !desc.get) continue;
      defineProperty(P, k, {
        configurable: true, enumerable: desc.enumerable,
        get() {
          const raw = desc.get.call(this);
          if (k === 'response' && this.responseType === 'json') { const t = text(this, raw === null ? 'null' : stringify(raw)); try { return parse(t); } catch (_) { return raw; } }
          if (k === 'response' && this.responseType !== '' && this.responseType !== 'text') return raw;
          return typeof raw === 'string' ? text(this, raw) : raw;
        },
      });
    }
  };

  // ---- the scriptlets
  const S = {
    'set-constant'(chain, raw) {
      const c = constant(raw); if (!c || !chain) return;
      trapChain(window, chain, (owner, prop) => {
        const d = getOwnPropertyDescriptor(owner, prop);
        if (d && d.configurable === false) return;
        if (c.remove) { delete owner[prop]; return; }
        try { defineProperty(owner, prop, { configurable: false, enumerable: true, get() { return c.v; }, set() {} }); } catch (_) {}
      });
    },
    'abort-on-property-read'(chain) {
      trapChain(window, chain, (owner, prop) => { try { defineProperty(owner, prop, { configurable: true, get() { throw new ReferenceError(magic); }, set() {} }); } catch (_) {} });
    },
    'abort-on-property-write'(chain) {
      trapChain(window, chain, (owner, prop) => {
        let v = owner[prop];
        try { defineProperty(owner, prop, { configurable: true, get() { return v; }, set() { throw new ReferenceError(magic); } }); } catch (_) {}
      });
    },
    'abort-current-script'(chain, needle) {
      const t = tester(needle || '');
      const check = () => {
        const s = document.currentScript;
        if (!(s instanceof HTMLScriptElement)) return;
        if (t(s.src ? s.src : s.textContent)) throw new ReferenceError(magic);
      };
      trapChain(window, chain, (owner, prop) => {
        const d = getOwnPropertyDescriptor(owner, prop);
        if (d && d.configurable === false) return;
        let v = owner[prop];
        try {
          defineProperty(owner, prop, {
            configurable: true,
            get() { check(); return d && d.get ? d.get.call(this) : v; },
            set(a) { check(); if (d && d.set) d.set.call(this, a); else v = a; },
          });
        } catch (_) {}
      });
    },
    'abort-on-stack-trace'(chain, needle) {
      const t = tester(needle || '');
      const check = () => { if (t(new Error().stack || '')) throw new ReferenceError(magic); };
      trapChain(window, chain, (owner, prop) => {
        let v = owner[prop];
        try { defineProperty(owner, prop, { configurable: true, get() { check(); return v; }, set(a) { check(); v = a; } }); } catch (_) {}
      });
    },
    'no-setTimeout-if'(needle, delay) { defuseTimer('setTimeout', needle, delay); },
    'no-setInterval-if'(needle, delay) { defuseTimer('setInterval', needle, delay); },
    'adjust-setTimeout'(needle, delay, boost) { boostTimer('setTimeout', needle, delay, boost); },
    'adjust-setInterval'(needle, delay, boost) { boostTimer('setInterval', needle, delay, boost); },
    'addEventListener-defuser'(type, needle) {
      const ty = tester(type || ''), t = tester(needle || '');
      EventTarget.prototype.addEventListener = new Proxy(EventTarget.prototype.addEventListener, {
        apply(target, thisArg, args) {
          let h = ''; try { h = String(args[1]); } catch (_) {}
          if (ty(args[0]) && t(h)) return;
          return apply(target, thisArg, args);
        },
      });
    },
    'no-window-open-if'(needle) {
      const t = tester(needle || '');
      const decoy = new Proxy(function () {}, { get: (o, k) => (k === 'closed' ? false : k === 'document' ? document.implementation.createHTMLDocument('') : function () {}), apply: () => {} });
      window.open = new Proxy(window.open, { apply(target, thisArg, args) { return t(args[0] === undefined ? '' : args[0]) ? decoy : apply(target, thisArg, args); } });
    },
    'remove-node-text'(selector, needle) { nodeText(selector, needle, () => ''); },
    'replace-node-text'(selector, pattern, replacement, ...rest) {
      const o = options(rest), cond = o.condition ? toRe(o.condition) : null;
      let left = o.sedCount ? parseInt(o.sedCount, 10) : Infinity;
      const re = toRe(pattern);
      nodeText(selector, cond ? cond : re, (text) => {
        if (left <= 0) return text;
        left--;
        return text.replace(re, replacement === undefined ? '' : replacement);
      });
    },
    'remove-attr'(attrs, selector, behavior) {
      const names = String(attrs || '').split('|').filter(Boolean);
      if (!names.length) return;
      const sel = selector || names.map((a) => `[${CSS.escape(a)}]`).join(',');
      const run = () => { try { document.querySelectorAll(sel).forEach((el) => names.forEach((a) => el.removeAttribute(a))); } catch (_) {} };
      if (behavior && /stay|complete/.test(behavior)) observe(run); else whenReady(run);
    },
    'remove-class'(classes, selector, behavior) {
      const names = String(classes || '').split('|').filter(Boolean);
      if (!names.length) return;
      const sel = selector || names.map((c) => '.' + CSS.escape(c)).join(',');
      const run = () => { try { document.querySelectorAll(sel).forEach((el) => el.classList.remove(...names)); } catch (_) {} };
      if (behavior && /stay|complete/.test(behavior)) observe(run); else whenReady(run);
    },
    'prevent-fetch'(props, body) {
      if (props === undefined) return;
      const text = body === 'emptyObj' ? '{}' : body === 'emptyArr' ? '[]' : body === 'emptyStr' || body === undefined || body === '' ? '' : body;
      window.fetch = new Proxy(window.fetch, {
        apply(target, thisArg, args) {
          const d = fetchDetails(args);
          if (!matchProps(props, d)) return apply(target, thisArg, args);
          const r = new Response(text, { status: 200, statusText: 'OK' }); defineProperty(r, 'url', { value: d.url });
          return Promise.resolve(r);
        },
      });
    },
    'prevent-xhr'(props, directive) {
      if (props === undefined) return;
      const P = XMLHttpRequest.prototype, blocked = new WeakSet(), open = P.open, send = P.send;
      P.open = function (method, url) { if (matchProps(props, { method: String(method).toUpperCase(), url: String(url) })) blocked.add(this); return apply(open, this, arguments); };
      P.send = function () {
        if (!blocked.has(this)) return apply(send, this, arguments);
        const body = directive && directive !== 'true' ? directive : '';
        for (const [k, v] of [['readyState', 4], ['status', 200], ['statusText', 'OK'], ['responseText', body], ['response', body]]) defineProperty(this, k, { value: v, configurable: true });
        setTimeout(() => { for (const ev of ['readystatechange', 'load', 'loadend']) this.dispatchEvent(new Event(ev)); }, 1);
      };
    },
    'noeval-if'(needle) {
      const t = tester(needle || '');
      window.eval = new Proxy(window.eval, { apply(target, thisArg, args) { return t(args[0]) ? undefined : apply(target, thisArg, args); } });
    },
    nowebrtc() {
      const Fake = function () { this.close = this.createDataChannel = this.createOffer = this.setLocalDescription = this.addEventListener = function () {}; };
      for (const k of ['RTCPeerConnection', 'webkitRTCPeerConnection']) if (window[k]) window[k] = Fake;
    },
    'json-prune'(rawPrune, rawNeedle) {
      const pr = pruner(rawPrune, rawNeedle);
      JSON.parse = new Proxy(JSON.parse, { apply(target, thisArg, args) { return pr(apply(target, thisArg, args)); } });
      Response.prototype.json = new Proxy(Response.prototype.json, { apply(target, thisArg, args) { return apply(target, thisArg, args).then(pr); } });
    },
    'json-prune-fetch-response'(rawPrune, rawNeedle, ...rest) {
      const pr = pruner(rawPrune, rawNeedle), props = options(rest).propsToMatch;
      hookFetch((d) => matchProps(props, d), (r) => r.clone().text().then((t) => remake(r, stringify(pr(parse(t))))));
    },
    'json-prune-xhr-response'(rawPrune, rawNeedle, ...rest) {
      const pr = pruner(rawPrune, rawNeedle), props = options(rest).propsToMatch;
      hookXhr((d) => matchProps(props, d), (t) => { try { return stringify(pr(parse(t))); } catch (_) { return t; } });
    },
    'replace-fetch-response'(pattern, replacement, props) {
      if (!pattern) return;
      const re = toRe(pattern);
      hookFetch((d) => matchProps(props, d), (r) => r.clone().text().then((t) => remake(r, t.replace(re, replacement || ''))));
    },
    'replace-xhr-response'(pattern, replacement, props) {
      if (!pattern) return;
      const re = toRe(pattern);
      hookXhr((d) => matchProps(props, d), (t) => t.replace(re, replacement || ''));
    },
    'set-cookie'(name, value, path) {
      if (!name) return;
      document.cookie = `${encodeURIComponent(name)}=${encodeURIComponent(value || '')}; path=${path === 'none' ? '' : path || '/'}`;
    },
    'set-local-storage-item'(key, value) { storage(localStorage, key, value); },
    'set-session-storage-item'(key, value) { storage(sessionStorage, key, value); },
    'cookie-remover'(needle) {
      const t = tester(needle || '');
      const run = () => {
        for (const c of document.cookie.split(';')) {
          const name = c.split('=')[0].trim();
          if (!name || !t(name)) continue;
          const host = location.hostname.split('.');
          for (let i = 0; i < host.length - 1; i++) {
            const domain = host.slice(i).join('.');
            for (const d of ['', `; domain=${domain}`, `; domain=.${domain}`]) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/${d}`;
          }
        }
      };
      run(); window.addEventListener('beforeunload', run);
    },
    'bab-defuser'() { defuser(['blockAdBlock', 'BlockAdBlock']); },
    'fuckadblock-defuser'() { defuser(['fuckAdBlock', 'FuckAdBlock', 'sniffAdBlock', 'SniffAdBlock']); },
    'popads-dummy'() { for (const k of ['PopAds', 'popns']) { try { defineProperty(window, k, { value: {}, configurable: false }); } catch (_) {} } },
    'href-sanitizer'(selector, source) {
      if (!selector) return;
      const run = () => {
        document.querySelectorAll(selector).forEach((a) => {
          let v = '';
          if (!source || source === 'text') v = a.textContent.trim();
          else if (source.startsWith('?')) { try { v = new URL(a.href).searchParams.get(source.slice(1)) || ''; } catch (_) {} }
          else if (/^\[.+\]$/.test(source)) v = a.getAttribute(source.slice(1, -1)) || '';
          if (/^https?:\/\//.test(v) && a.href !== v) a.setAttribute('href', v);
        });
      };
      observe(run);
    },
    'set-attr'(selector, attr, value) {
      if (!selector || !attr) return;
      const run = () => {
        document.querySelectorAll(selector).forEach((el) => {
          const v = /^\[.+\]$/.test(value || '') ? el.getAttribute(value.slice(1, -1)) : value || '';
          if (v !== null && el.getAttribute(attr) !== v) el.setAttribute(attr, v);
        });
      };
      observe(run);
    },
    'refresh-defuser'() { whenReady(() => document.querySelectorAll('meta[http-equiv="refresh" i]').forEach((m) => m.remove())); },
    'disable-newtab-links'() {
      document.addEventListener('click', (e) => { const a = e.target && e.target.closest && e.target.closest('a[target="_blank"]'); if (a) { e.preventDefault(); location.href = a.href; } }, true);
    },
  };

  function defuseTimer(name, needle, delay) {
    const t = tester(needle || '');
    const not = typeof delay === 'string' && delay.startsWith('!'), want = delay ? parseInt(not ? delay.slice(1) : delay, 10) : NaN;
    window[name] = new Proxy(window[name], {
      apply(target, thisArg, args) {
        const fn = args[0]; let s = ''; try { s = String(fn); } catch (_) {}
        const delayOk = isNaN(want) || ((Number(args[1]) || 0) === want) !== not;
        if (t(s) && delayOk) return 0;
        return apply(target, thisArg, args);
      },
    });
  }
  function boostTimer(name, needle, delay, boost) {
    const t = tester(needle || ''), want = delay === '*' ? NaN : parseInt(delay || '1000', 10);
    const b = Math.min(50, Math.max(0.001, parseFloat(boost || '0.05') || 0.05));
    window[name] = new Proxy(window[name], {
      apply(target, thisArg, args) {
        let s = ''; try { s = String(args[0]); } catch (_) {}
        if (t(s) && (isNaN(want) || Number(args[1]) === want)) args = [args[0], (Number(args[1]) || 0) * b, ...args.slice(2)];
        return apply(target, thisArg, args);
      },
    });
  }
  // text in nodes matching [selector] ("#text" for text nodes) whose text matches [needle], changed by [change]
  function nodeText(selector, needle, change) {
    const re = needle instanceof RegExp ? needle : toRe(needle);
    const sel = selector || '#text';
    const handle = (n) => {
      if (n.nodeType === 3) { if (sel === '#text' && re.test(n.nodeValue)) n.nodeValue = change(n.nodeValue); return; }
      if (n.nodeType !== 1 || sel === '#text') return;
      try { if (n.matches(sel) && re.test(n.textContent)) n.textContent = change(n.textContent); } catch (_) {}
    };
    const scan = (n) => { handle(n); if (n.nodeType === 1 && sel !== '#text') try { n.querySelectorAll(sel).forEach(handle); } catch (_) {} };
    new MutationObserver((ms) => { for (const m of ms) m.addedNodes.forEach(scan); }).observe(document, { childList: true, subtree: true });
    if (document.documentElement) scan(document.documentElement);
  }
  function storage(store, key, value) {
    if (!key) return;
    try { if (value === '$remove$') store.removeItem(key); else store.setItem(key, value === 'emptyObj' ? '{}' : value === 'emptyArr' ? '[]' : value === undefined ? '' : value); } catch (_) {}
  }
  function defuser(names) {
    const fake = { check() {}, emitEvent() { return this; }, clearEvent() {}, on() { return this; }, onDetected() { return this; }, onNotDetected(fn) { try { fn(); } catch (_) {} return this; }, setOption() { return this; } };
    const C = function () { return fake; };
    for (const n of names) { try { defineProperty(window, n, { value: /^[A-Z]/.test(n) ? C : fake, configurable: false }); } catch (_) {} }
  }

  for (const [name, ...args] of list) {
    const fn = S[name];
    if (fn) { try { fn(...args); } catch (_) {} }
  }
})();
