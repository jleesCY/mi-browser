// Runs installed extensions' content scripts in a page (extensions/ExtensionTabs builds the document-start script
// around this body, with makeApi, EXTS and CODE in scope). It runs before any of the page's own scripts, so it takes
// the miExt channel first and keeps it, and each extension's calls carry a secret token mi gave it: a page can't make
// extension calls of its own. It checks each script's patterns against this page, runs it at its run_at time (start,
// end or idle), in every frame or only the top one, and gives it chrome.* and browser.* as its own variables (never
// globals the page could see).
// an extension's own page (opened in a tab) is not a web page: content scripts don't run there, and its own start
// script (ext-page.js) takes the channel
if (/\.ext\.mi\.invalid$/.test(location.hostname)) return;
const chan = window.miExt;
if (!chan) return;
try { delete window.miExt; } catch (_) {}
const top = window === window.top;
// the extensions' native addresses (moz-extension://…) load from mi's, in this page too (ext-address.js)
const fixAddr = installAddresses(EXTS.map((e) => e.id));
// import() in a content script (code-split extensions load their parts this way): its address is switched the same way
// (ExtensionRules.isolatedWorld turns their `import(` into this); if it fails, the extension's own fallback runs
try { Object.defineProperty(window, '__miImport', { configurable: true, value: (u) => import(fixAddr(u instanceof URL ? u.href : String(u))) }); } catch (_) {}
const post = (o) => { try { chan.postMessage(JSON.stringify(o)); } catch (_) {} };
const pending = {}, handlers = {}, tokens = {};
let seq = 0;
chan.addEventListener('message', (e) => {
  let m; try { m = JSON.parse(e.data); } catch (_) { return; }
  if (m.t === 'res') { const p = pending[m.c]; if (p) { delete pending[m.c]; m.ok ? p.res(m.v) : p.rej(new Error(m.e)); } }
  else if (m.t === 'ev' && handlers[m.x]) handlers[m.x](m.n, m.d, (v, ok) => post({ t: 'reply', x: m.x, k: tokens[m.x], r: m.r, v, ok }));
});

// ---- match patterns (as extensions/ExtensionRules does in Kotlin)
const esc = (s) => s.replace(/[.+^${}()|[\]\\]/g, '\\$&');
const glob = (g, text) => new RegExp('^' + esc(g).replace(/\*/g, '.*').replace(/\?/g, '.') + '$', 's').test(text);
const pattern = (p, url) => {
  if (p === '<all_urls>') return /^(https?|file):/.test(url);
  const m = /^(\*|https?|file|wss?|ftp):\/\/([^/]*)(\/.*)?$/.exec(p);
  const u = /^([a-z]+):\/\/([^/?#]*)([^#]*)/.exec(url);
  if (!m || !u) return false;
  if (m[1] === '*' ? !/^https?$/.test(u[1]) : m[1] !== u[1]) return false;
  const host = u[2].replace(/^.*@/, '').replace(/:.*$/, '').toLowerCase(), h = m[2].replace(/:.*$/, '').toLowerCase();
  if (!(h === '*' || (h.startsWith('*.') ? host === h.slice(2) || host.endsWith(h.slice(1)) : host === h))) return false;
  return glob(m[3] || '/', u[3] || '/');
};
const runsOn = (s, url) => s.matches.some((p) => pattern(p, url)) && !s.excludeMatches.some((p) => pattern(p, url)) &&
  (!s.includeGlobs.length || s.includeGlobs.some((g) => glob(g, url))) && !s.excludeGlobs.some((g) => glob(g, url));

// ---- when to run
const whenReady = (runAt, fn) => {
  if (runAt === 'document_start') return fn();
  const ready = () => (runAt === 'document_end' ? fn() : setTimeout(fn, 0));
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready, { once: true }); else ready();
};
const addStyle = (css) => {
  const s = document.createElement('style');
  s.dataset.miExtension = '1';
  s.textContent = css;
  const put = () => (document.head || document.documentElement).appendChild(s);
  if (document.documentElement) put();
  else new MutationObserver((_, o) => { if (document.documentElement) { o.disconnect(); put(); } }).observe(document, { childList: true });
  return s;
};

// An extension adding one of its own files to the page as a script (<script src=…>, to run in the page's world): sites
// with strict security rules (YouTube's Trusted Types and Content Security Policy) refuse to load it, though a browser
// exempts extensions. So it isn't loaded by the page at all: mi runs the file in the page itself (as a browser would),
// then the element gets its load event as usual. Only in the top frame (mi can run scripts there), and only for the
// extension's own files; every other script is the site's business, under its rules.
const calls = {};
const own = (v) => (typeof v === 'string' || v instanceof URL) && /^https:\/\/([a-z0-9-]+)\.ext\.mi\.invalid\//.exec(fixAddr(String(v)));
const srcProp = Object.getOwnPropertyDescriptor(HTMLScriptElement.prototype, 'src');
const runOwn = (el, url, id) => {
  el.__miSrc = url;
  Promise.resolve().then(() => { window.__miExtCurrentScript = el; return calls[id]('page.injectScript', [url]); })
    .then(() => { el.dispatchEvent(new Event('load')); }, () => { el.dispatchEvent(new Event('error')); });
};
if (top && srcProp) {
  Object.defineProperty(HTMLScriptElement.prototype, 'src', {
    configurable: true, enumerable: srcProp.enumerable,
    get() { return this.__miSrc || srcProp.get.call(this); },
    set(v) { const m = own(v); if (m && calls[m[1]]) runOwn(this, m.input, m[1]); else srcProp.set.call(this, v); },
  });
  const setAttribute = Element.prototype.setAttribute;
  Element.prototype.setAttribute = function (name, value) {
    const m = this instanceof HTMLScriptElement && String(name).toLowerCase() === 'src' && own(value);
    if (m && calls[m[1]]) return runOwn(this, m.input, m[1]);
    return setAttribute.call(this, name, value);
  };
}

// globalThis and self as content scripts see them: the page's, except chrome and browser are the extension's
const standIns = new WeakSet();
// a browser function bound to one (requestAnimationFrame.bind(self)) gets the real window instead, which it needs
// ("Illegal invocation" otherwise); nothing changes for the page, which never has one
const nativeBind = Function.prototype.bind;
const bind = function bind(t) {
  if (!standIns.has(t)) return Reflect.apply(nativeBind, this, arguments);
  const args = Array.from(arguments); args[0] = window;
  return Reflect.apply(nativeBind, this, args);
};
Object.defineProperty(bind, 'toString', { value: () => 'function bind() { [native code] }' });
Object.defineProperty(Function.prototype, 'bind', { configurable: true, writable: true, value: bind });
const globalFor = (api) => new Proxy(window, {
  get(w, k) {
    if (k === 'chrome' || k === 'browser') return api;
    const v = Reflect.get(w, k);
    return typeof v === 'function' && !/^[A-Z]/.test(String(k)) ? v.bind(w) : v;
  },
  set(w, k, v) { w[k] = v; return true; },
  has(w, k) { return k === 'chrome' || k === 'browser' || k in w; },
});
const standIn = (api) => { const g = globalFor(api); standIns.add(g); return g; };

const apis = {}, lazy = {};
Object.defineProperty(window, '__miExtRunEntry', {
  value: (id, token, nonce, fn) => {
    if (!tokens[id] || tokens[id] !== token || !lazy[nonce]) return;
    const run = lazy[nonce]; delete lazy[nonce]; run(fn);
  },
});
// mi learns of this frame first: a call made before it knows the frame would go unanswered (a document_start script
// that asks for storage at once, as uBlock Origin Lite's do, would wait forever)
post({ t: 'hello', u: location.href, top, x: EXTS.map((e) => [e.id, e.token]) });
for (const ext of EXTS) {
  tokens[ext.id] = ext.token;
  const channel = {
    call: (name, args) => new Promise((res, rej) => { const c = ++seq; pending[c] = { res, rej }; post({ t: 'call', x: ext.id, k: ext.token, c, n: name, a: args, u: location.href, top }); }),
    on: (fn) => { handlers[ext.id] = fn; },
  };
  calls[ext.id] = channel.call;
  const api = (apis[ext.id] = makeApi(Object.assign({}, ext.cfg, { kind: 'content' }), channel));
  const g = standIn(api);
  // turned off on this site in Settings (and its subdomains): none of its scripts run here
  const host = location.hostname.replace(/^www\./, '');
  if ((ext.offSites || []).some((h) => host === h || host.endsWith('.' + h))) continue;
  ext.scripts.forEach((s, i) => {
    if (!s.allFrames && !top) return;
    if (!runsOn(s, location.href)) return;
    s.css.forEach((css) => whenReady('document_start', () => addStyle(css)));
    const code = CODE[ext.id] && CODE[ext.id][i];
    // a big one mi runs in the page when it's time (extensions/ExtensionTabs.runEntry), with a nonce only this page knows
    if (code === 0) whenReady(s.runAt, () => {
      const nonce = Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
      lazy[nonce] = (fn) => { try { fn.call(window, api, api, g, g); } catch (e) { console.error('[' + ext.cfg.name + ']', e); } };
      channel.call('content.run', [i, nonce]).catch((e) => { delete lazy[nonce]; console.error('[' + ext.cfg.name + ']', e); });
    });
    else if (code) whenReady(s.runAt, () => {
      try { s.mainWorld ? code.call(window) : code.call(window, api, api, g, g); } catch (e) { console.error('[' + ext.cfg.name + ']', e); }
    });
  });
}
// the page's security policy refused one of the extensions' files (a browser exempts them): mi is told, once, and
// fetches this site's pages with them allowed from then on (extensions/ExtensionRequests)
let cspTold = false;
document.addEventListener('securitypolicyviolation', (ev) => {
  // an extension's file (at mi's address or its native one), or an inline style (what Dark Reader and others add)
  const ext = /\.ext\.mi\.invalid\/|^moz-extension:/.test(ev.blockedURI || '');
  // an inline style from extension code (mi runs it from no address); the site's own report its page or script
  const style = ev.blockedURI === 'inline' && /^style-src/.test(ev.effectiveDirective || ev.violatedDirective || '') &&
    !/^https?:/.test(ev.sourceFile || '');
  // Trusted Types refusing a plain string from extension code (YouTube's: SponsorBlock sets innerHTML)
  const types = /^require-trusted-types-for/.test(ev.effectiveDirective || ev.violatedDirective || '') &&
    (!/^https?:/.test(ev.sourceFile || '') || /\.ext\.mi\.invalid\//.test(ev.sourceFile));
  if (cspTold || ev.disposition === 'report' || !(ext || style || types)) return;
  cspTold = true; post({ t: 'csp', u: location.href, top });
}, true);
// the page's DOM is ready: for chrome.webNavigation.onDOMContentLoaded
if (top) whenReady('document_end', () => post({ t: 'nav', u: location.href, top: true }));

// scripts and styles an extension adds while running (tabs.executeScript, scripting.*): mi calls this in the page,
// with the extension's token; the result goes back over the channel (it may be a promise)
Object.defineProperty(window, '__miExtExec', {
  value: (id, token, callId, fn) => {
    if (!tokens[id] || tokens[id] !== token) return false;
    Promise.resolve().then(() => { const g = standIn(apis[id]); return fn(apis[id], apis[id], g, g); })
      .then((v) => post({ t: 'exec', x: id, k: token, c: callId, ok: true, v: v === undefined ? null : JSON.parse(JSON.stringify(v)) }),
        (e) => post({ t: 'exec', x: id, k: token, c: callId, ok: false, e: String((e && e.message) || e) }));
    return true; // taken: its answer comes over the channel, however long it runs
  },
});
const added = {};
Object.defineProperty(window, '__miExtCss', {
  value: (id, token, key, css, remove) => {
    if (!tokens[id] || tokens[id] !== token) return;
    if (remove) { (added[key] || []).forEach((s) => s.remove()); delete added[key]; return; }
    (added[key] = added[key] || []).push(addStyle(css));
  },
});
