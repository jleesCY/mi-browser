// Keeps the page's content clear of the camera cutout while mi's status bar is hidden: the page still fills the
// screen (it shows through the strip as it scrolls), but it starts below the strip. __DP__ is the strip's height in
// dp, or 0 to take the space away again. CSS px are dp divided by the page's zoom (a zoomed-out desktop page needs more).
//
// Two parts: padding at the top of the page moves its normal content down; headers pinned to the top (position fixed
// or sticky, top 0) ignore that padding, so each one found in the strip is moved down by the same amount. They're
// looked for where they'd be (a few points in the strip) whenever the page scrolls (once a frame at most; sticky
// headers pin on scroll) and shortly after load (headers added late), never by walking the whole page; with no strip
// (the status bar shown) nothing listens at all. Run again with a new height (the setting changed) it updates both;
// with 0 it puts everything back.
((dp) => {
  const s = window.__miTopInset || (window.__miTopInset = { px: 0, moved: new Set(), queued: false });
  s.px = dp ? dp / ((window.visualViewport && window.visualViewport.scale) || 1) : 0;

  const id = 'mi-top-inset';
  let style = document.getElementById(id);
  if (!s.px) { if (style) style.remove(); }
  else {
    if (!style) {
      style = document.createElement('style');
      style.id = id;
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent = `html{padding-top:${s.px}px !important}`;
  }

  // a header moved down keeps its own top (inline style) to put back
  const restore = (el) => {
    el.style.setProperty('top', el.dataset.miTop || '', el.dataset.miTopPriority || '');
    delete el.dataset.miTop; delete el.dataset.miTopPriority; delete el.dataset.miBy;
    s.moved.delete(el);
  };
  const pinnedAncestor = (el) => {
    for (; el && el !== document.body && el !== document.documentElement; el = el.parentElement) {
      const pos = getComputedStyle(el).position;
      if (pos === 'fixed' || pos === 'sticky') return el;
    }
    return null;
  };
  const check = () => {
    s.queued = false;
    s.moved.forEach((el) => { if (!el.isConnected || String(s.px) !== el.dataset.miBy) restore(el); });
    if (!s.px || !document.body) return;
    const y = Math.max(1, s.px / 2);
    for (const x of [innerWidth * 0.1, innerWidth * 0.5, innerWidth * 0.9]) {
      for (const hit of document.elementsFromPoint(x, y)) {
        const el = pinnedAncestor(hit);
        if (!el || s.moved.has(el)) continue;
        const r = el.getBoundingClientRect();
        if (r.top >= s.px || r.height > innerHeight / 2) continue; // not in the strip, or a full-screen layer (not a header)
        el.dataset.miTop = el.style.getPropertyValue('top');
        el.dataset.miTopPriority = el.style.getPropertyPriority('top');
        el.dataset.miBy = String(s.px);
        el.style.setProperty('top', `${(parseFloat(getComputedStyle(el).top) || 0) + s.px}px`, 'important');
        s.moved.add(el);
      }
    }
  };
  const queue = () => { if (!s.queued) { s.queued = true; requestAnimationFrame(check); } };

  // nothing to look for until there's a strip (status bar hidden): no scroll listening on pages that don't need it
  if (s.px && !s.listening) {
    s.listening = true;
    addEventListener('scroll', queue, { passive: true, capture: true });
    addEventListener('resize', queue, { passive: true });
    [300, 1500, 4000].forEach((ms) => setTimeout(queue, ms));
  }
  queue();
})(__DP__);
