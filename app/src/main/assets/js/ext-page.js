// Starts an extension's own page (its background, popup or options) in mi: extensions/ExtensionPages builds the
// document-start script around this body, with makeApi, CFG and TOKEN in scope. The page gets chrome.* and browser.*
// with everything mi supports. Its requests to other sites (which the extension's host permissions allow) go through mi
// natively, as extensions' requests aren't held to the sites' cross-origin rules in a real browser either. A Manifest V3
// service worker runs in this page too, so it also gets what a worker has (importScripts, skipWaiting, clients).
const chan = window.miExt;
if (!chan) return;
try { delete window.miExt; } catch (_) {}
const post = (o) => { try { chan.postMessage(JSON.stringify(o)); } catch (_) {} };
const pending = {};
let seq = 0, handler = null;
chan.addEventListener('message', (e) => {
  let m; try { m = JSON.parse(e.data); } catch (_) { return; }
  if (m.t === 'res') { const p = pending[m.c]; if (p) { delete pending[m.c]; m.ok ? p.res(m.v) : p.rej(new Error(m.e)); } }
  else if (m.t === 'ev' && handler) handler(m.n, m.d, (v, ok) => post({ t: 'reply', x: CFG.id, k: TOKEN, r: m.r, v, ok }));
});
const call = (name, args) => new Promise((res, rej) => { const c = ++seq; pending[c] = { res, rej }; post({ t: 'call', x: CFG.id, k: TOKEN, c, n: name, a: args, u: location.href, top: true }); });
const channel = { call, on: (fn) => { handler = fn; } };
const api = makeApi(CFG, channel);
for (const k of ['chrome', 'browser']) { try { Object.defineProperty(window, k, { value: api, writable: true, configurable: true }); } catch (_) { window[k] = api; } }

// ---- requests to other sites, through mi
const own = location.origin;
const crossSite = (u) => { try { const x = new URL(u, location.href); return /^https?:$/.test(x.protocol) && x.origin !== own; } catch (_) { return false; } };
const { b64, unb64 } = channel.base64; // ext-api.js's
const bodyOf = async (body) => {
  if (body == null) return null;
  if (typeof body === 'string') return { text: body };
  if (body instanceof URLSearchParams) return { text: body.toString(), type: 'application/x-www-form-urlencoded;charset=UTF-8' };
  if (body instanceof Blob) return { b64: b64(await body.arrayBuffer()), type: body.type };
  if (body instanceof ArrayBuffer || ArrayBuffer.isView(body)) return { b64: b64(body.buffer ? body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength) : body) };
  if (body instanceof FormData) { const r = new Response(body); return { b64: b64(await r.arrayBuffer()), type: r.headers.get('content-type') }; }
  return { text: String(body) };
};
const headersOf = (h) => { const o = {}; new Headers(h || {}).forEach((v, k) => { o[k] = v; }); return o; };
const nativeFetch = window.fetch.bind(window);
window.fetch = async function (input, init) {
  const req = input instanceof Request ? input : null;
  const href = req ? req.url : String(input);
  if (!crossSite(href)) return nativeFetch(input, init);
  init = init || {};
  const method = (init.method || (req && req.method) || 'GET').toUpperCase();
  const headers = Object.assign(req ? headersOf(req.headers) : {}, headersOf(init.headers));
  const body = await bodyOf(init.body != null ? init.body : req && method !== 'GET' && method !== 'HEAD' ? await req.clone().arrayBuffer() : null);
  if (init.signal && init.signal.aborted) throw new DOMException('The operation was aborted.', 'AbortError');
  const r = await call('net.fetch', [{ url: new URL(href, location.href).href, method, headers, body, credentials: init.credentials || 'include', redirect: init.redirect || 'follow' }]);
  const noBody = r.status === 204 || r.status === 205 || r.status === 304 || method === 'HEAD';
  const res = new Response(noBody ? null : unb64(r.body), { status: r.status || 200, statusText: r.statusText || '', headers: r.headers });
  Object.defineProperty(res, 'url', { value: r.url });
  Object.defineProperty(res, 'redirected', { value: r.url !== href });
  return res;
};

// XMLHttpRequest to other sites: the same path, behind the usual interface
const NativeXHR = window.XMLHttpRequest;
window.XMLHttpRequest = function () {
  const real = new NativeXHR();
  let cross = false, method = 'GET', target = '', reqHeaders = {}, aborted = false;
  const self = this, listeners = {};
  const state = { readyState: 0, status: 0, statusText: '', responseText: '', response: null, responseURL: '', headers: {} };
  const emit = (type) => {
    const ev = new Event(type);
    if (typeof self['on' + type] === 'function') try { self['on' + type](ev); } catch (e) { console.error(e); }
    (listeners[type] || []).slice().forEach((fn) => { try { fn.call(self, ev); } catch (e) { console.error(e); } });
  };
  ['readystatechange', 'load', 'error', 'abort', 'timeout', 'loadend', 'loadstart', 'progress'].forEach((t) => real.addEventListener(t, () => { if (!cross) emit(t); }));
  const pass = (k) => Object.defineProperty(self, k, { get: () => (cross ? state[k] : real[k]) });
  ['readyState', 'status', 'statusText', 'responseText', 'response', 'responseURL'].forEach(pass);
  Object.defineProperty(self, 'responseXML', { get: () => (cross ? new DOMParser().parseFromString(state.responseText, 'text/xml') : real.responseXML) });
  for (const k of ['responseType', 'timeout', 'withCredentials']) Object.defineProperty(self, k, { get: () => real[k], set: (v) => { real[k] = v; } });
  self.upload = real.upload;
  self.addEventListener = (t, fn) => { (listeners[t] = listeners[t] || []).push(fn); };
  self.removeEventListener = (t, fn) => { const l = listeners[t] || []; const i = l.indexOf(fn); if (i >= 0) l.splice(i, 1); };
  self.open = (m, u, async) => { method = String(m).toUpperCase(); target = new URL(u, location.href).href; cross = crossSite(target); reqHeaders = {}; if (!cross) real.open(m, u, async !== false); else { state.readyState = 1; emit('readystatechange'); } };
  self.setRequestHeader = (k, v) => { if (cross) reqHeaders[k] = v; else real.setRequestHeader(k, v); };
  self.overrideMimeType = (t) => real.overrideMimeType(t);
  self.getAllResponseHeaders = () => (cross ? Object.entries(state.headers).map(([k, v]) => k + ': ' + v).join('\r\n') : real.getAllResponseHeaders());
  self.getResponseHeader = (k) => (cross ? state.headers[String(k).toLowerCase()] ?? null : real.getResponseHeader(k));
  self.abort = () => { if (cross) { aborted = true; emit('abort'); emit('loadend'); } else real.abort(); };
  self.send = (body) => {
    if (!cross) return real.send(body);
    emit('loadstart');
    bodyOf(body).then((b) => call('net.fetch', [{ url: target, method, headers: reqHeaders, body: b, credentials: real.withCredentials ? 'include' : 'include' }])).then((r) => {
      if (aborted) return;
      const bytes = unb64(r.body);
      const text = new TextDecoder().decode(bytes);
      state.status = r.status; state.statusText = r.statusText || ''; state.responseURL = r.url; state.responseText = text;
      state.headers = {}; Object.entries(r.headers || {}).forEach(([k, v]) => { state.headers[k.toLowerCase()] = v; });
      const rt = real.responseType;
      state.response = rt === 'json' ? (() => { try { return JSON.parse(text); } catch (_) { return null; } })()
        : rt === 'arraybuffer' ? bytes.buffer : rt === 'blob' ? new Blob([bytes], { type: state.headers['content-type'] || '' })
        : rt === 'document' ? new DOMParser().parseFromString(text, 'text/html') : text;
      state.readyState = 4; emit('readystatechange'); emit('load'); emit('loadend');
    }, () => { if (aborted) return; state.readyState = 4; state.status = 0; emit('readystatechange'); emit('error'); emit('loadend'); });
  };
};
Object.assign(window.XMLHttpRequest, { UNSENT: 0, OPENED: 1, HEADERS_RECEIVED: 2, LOADING: 3, DONE: 4 });

// ---- what a service worker has, for a Manifest V3 background running as this page
if (CFG.kind === 'background') {
  window.importScripts = (...urls) => urls.forEach((u) => {
    const x = new NativeXHR(); x.open('GET', new URL(u, location.href).href, false); x.send();
    (0, eval)(x.responseText + '\n//# sourceURL=' + u);
  });
  window.skipWaiting = () => Promise.resolve();
  if (!('clients' in window)) window.clients = { claim: () => Promise.resolve(), matchAll: () => Promise.resolve([]), get: () => Promise.resolve(undefined), openWindow: (u) => call('tabs.create', [{ url: u }]) };
  if (!('registration' in window)) window.registration = { scope: own + '/', active: {}, unregister: () => Promise.resolve(true), showNotification: (t, o) => call('notifications.create', ['', { type: 'basic', title: t, message: (o && o.body) || '', iconUrl: o && o.icon }]) };
  // when the scripts have run: install and activate (as a worker gets them), then mi starts sending events
  window.addEventListener('load', () => {
    for (const type of ['install', 'activate']) {
      const ev = new Event(type); ev.waitUntil = () => {}; window.dispatchEvent(ev);
    }
    post({ t: 'ready', x: CFG.id, k: TOKEN });
  });
}
post({ t: 'hello', x: CFG.id, k: TOKEN, u: location.href, top: window === window.top });
