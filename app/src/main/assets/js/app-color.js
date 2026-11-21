// The page's own colour, for the status bar of a page open as an app (launcher/AppMode): its <meta name="theme-color">
// (the one whose media query matches, e.g. a dark one in dark mode), else the background showing at the very top of the
// page (its header), else the page's own background. A CSS colour string, or null.
(() => {
  for (const m of document.querySelectorAll('meta[name="theme-color"]')) {
    const media = m.getAttribute('media');
    if (m.content && (!media || matchMedia(media).matches)) return m.content.trim();
  }
  const solid = (el) => {
    for (; el; el = el.parentElement) {
      const bg = getComputedStyle(el).backgroundColor;
      if (bg && !/^(transparent|rgba\([^)]*,\s*0\))$/.test(bg)) return bg;
    }
    return null;
  };
  return solid(document.elementFromPoint(innerWidth / 2, 2)) || solid(document.body) || solid(document.documentElement);
})()
