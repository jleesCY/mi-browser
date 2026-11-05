// Document-start script for every frame: asks mi (window.miAdBlock) what to hide and adds one stylesheet.
//  - Simple generic selectors (".ad-box", "#promo": ~96% of them) get a rule only once an element on the page uses
//    that class or id: found by one scan, then a MutationObserver for added nodes and class/id changes. The page's
//    stylesheet stays small, so style updates stay cheap (the same idea as uBlock Origin's generic filtering).
//  - Complex generic selectors go in up front, 20 to a rule; if some group fails to parse, its bad selectors are
//    found once (querySelector throws on them), left out, and reported so mi drops them for good.
(() => {
  if (window.__miCos) return; window.__miCos = 1;
  const ch = window.miAdBlock; if (!ch) return;
  ch.onmessage = (e) => {
    let r; try { r = JSON.parse(e.data); } catch (_) { return; }
    const x = new Set(r.x || []), simple = new Set(), complex = [], groups = [], SIMPLE = /^[.#][\w-]+$/;
    for (const list of [r.g, r.k]) if (list) for (const q of list) if (!x.has(q)) { if (SIMPLE.test(q)) simple.add(q); else complex.push(q); }
    for (let i = 0; i < complex.length; i += 20) groups.push(complex.slice(i, i + 20));
    const css = (gs) => (r.c || '') + gs.map((p) => p.join(',') + '{display:none!important}').join('\n');
    const s = document.createElement('style');
    s.textContent = css(groups);
    (document.head || document.documentElement).appendChild(s);
    if (groups.length && s.sheet && s.sheet.cssRules.length < (r.n || 0) + groups.length) {
      const bad = [];
      for (const p of groups) for (const q of p) { try { document.querySelector(q); } catch (_) { bad.push(q); } }
      if (bad.length) {
        const b = new Set(bad);
        s.textContent = css(groups.map((p) => p.filter((q) => !b.has(q))).filter((p) => p.length));
        ch.postMessage('bad:' + JSON.stringify(bad));
      }
    }
    if (!simple.size || !s.sheet) return;
    const sheet = s.sheet, done = new Set();
    const hide = (q) => { if (simple.has(q) && !done.has(q)) { done.add(q); try { sheet.insertRule(q + '{display:none!important}', sheet.cssRules.length); } catch (_) {} } };
    const check = (el) => { if (el.id) hide('#' + el.id); const cl = el.classList; if (cl) for (let i = 0; i < cl.length; i++) hide('.' + cl[i]); };
    const scan = (root) => { if (root.nodeType !== 1) return; check(root); const all = root.querySelectorAll('[id],[class]'); for (let i = 0; i < all.length; i++) check(all[i]); };
    scan(document.documentElement);
    new MutationObserver((ms) => {
      for (const m of ms) { if (m.type === 'attributes') { if (m.target.nodeType === 1) check(m.target); } else for (const n of m.addedNodes) scan(n); }
    }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id'] });
  };
  ch.postMessage('');
})();
