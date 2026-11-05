// The page's largest declared icon (touch icons count as 180 px when unsized), as an absolute URL, or null. */
(() => {
  let best = null, size = 0;
  for (const e of document.querySelectorAll('link[rel~="apple-touch-icon"],link[rel~="apple-touch-icon-precomposed"],link[rel~="icon"]')) {
    const s = parseInt(((e.sizes && e.sizes.value) || '').split(/x/i)[0]) || (/apple/.test(e.rel) ? 180 : 16);
    if (s > size && e.href) { size = s; best = e.href; }
  }
  return size >= 48 ? best : null;
})()
