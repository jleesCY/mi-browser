// Extensions' native addresses in a page. Extensions are told their addresses the way their browser gives them
// (moz-extension://<id>/…, as Firefox gives them), as many check for those. WebView can't load that scheme, so wherever
// a page loads one (an image, a script, a style sheet, a frame, a fetch, a worker, a window), it's switched to the
// address mi really serves the extension at
// (https://<id>.ext.mi.invalid/…). Elements made from HTML text are fixed as they're added. This file is an
// expression: mi embeds it as `installAddresses = <this>()` in its extension scripts; it changes only addresses of the
// extensions given to it, nothing of the page's own.
(function () {
  return function installAddresses(ids) {
    if (!ids.length) return (u) => u;
    const re = new RegExp('^moz-extension://(' + ids.map((i) => i.replace(/[^a-z0-9-]/g, '')).join('|') + ')/', 'i');
    const fix = (u) => (typeof u === 'string' && re.test(u) ? u.replace(re, (_, id) => 'https://' + id.toLowerCase() + '.ext.mi.invalid/') : u);
    const fixAny = (u) => (u instanceof URL ? fix(u.href) : fix(u));

    // properties that load something
    const props = [[HTMLImageElement, 'src'], [HTMLScriptElement, 'src'], [HTMLLinkElement, 'href'], [HTMLIFrameElement, 'src'],
      [HTMLSourceElement, 'src'], [HTMLMediaElement, 'src'], [HTMLEmbedElement, 'src'], [HTMLObjectElement, 'data'],
      [HTMLAnchorElement, 'href'], [HTMLImageElement, 'srcset'], [HTMLSourceElement, 'srcset']];
    for (const [C, k] of props) {
      const d = C && Object.getOwnPropertyDescriptor(C.prototype, k);
      if (!d || !d.set) continue;
      Object.defineProperty(C.prototype, k, { configurable: true, enumerable: d.enumerable, get: d.get, set(v) { d.set.call(this, k === 'srcset' ? String(v).split(',').map((s) => fix(s.trim())).join(', ') : fixAny(v)); } });
    }
    const loads = new Set(['src', 'href', 'data', 'srcset', 'poster', 'action']);
    const setAttribute = Element.prototype.setAttribute;
    Element.prototype.setAttribute = function (name, value) {
      return setAttribute.call(this, name, loads.has(String(name).toLowerCase()) ? fix(String(value)) : value);
    };
    // made from HTML text (innerHTML, a template): fixed as it's added, which loads it again from the right place
    const sel = ['src', 'href', 'srcset', 'data', 'poster'].map((a) => '[' + a + '*="-extension://"]').join(',');
    const fixTree = (n) => {
      if (n.nodeType !== 1) return;
      for (const el of [...(n.matches(sel) ? [n] : []), ...n.querySelectorAll(sel)])
        for (const a of ['src', 'href', 'srcset', 'data', 'poster']) { const v = el.getAttribute && el.getAttribute(a); if (v && re.test(v)) setAttribute.call(el, a, fix(v)); }
    };
    new MutationObserver((ms) => { for (const m of ms) m.addedNodes.forEach(fixTree); })
      .observe(document, { childList: true, subtree: true });

    // requests, workers and windows
    const nativeFetch = window.fetch;
    window.fetch = function (input, init) { return nativeFetch.call(this, input instanceof Request ? input : fixAny(input), init); };
    const open = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (m, u, ...rest) { return open.call(this, m, fixAny(u), ...rest); };
    for (const name of ['Worker', 'SharedWorker']) {
      const W = window[name];
      if (W) window[name] = new Proxy(W, { construct: (T, [u, ...rest]) => new T(fixAny(u), ...rest) });
    }
    const winOpen = window.open;
    window.open = function (u, ...rest) { return winOpen.call(this, fixAny(u), ...rest); };
    for (const k of ['assign', 'replace']) { const f = Location.prototype[k]; try { Location.prototype[k] = function (u) { return f.call(this, fixAny(u)); }; } catch (_) {} }
    return fix;
  };
})
