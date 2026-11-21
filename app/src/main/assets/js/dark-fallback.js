// Darken (Settings › Web Pages) for a page WebView's own darkening leaves alone: one that opts out of it with the
// "only light" colour scheme yet is bright. Its colours are inverted (hues kept), and pictures, videos and frames are
// inverted back so they show as they are. Once per page; runs after it loads.
(() => {
  if (document.getElementById('mi-dark-fallback')) return;
  const root = document.documentElement;
  const meta = document.querySelector('meta[name="color-scheme"]');
  const optedOut = /only/.test(getComputedStyle(root).colorScheme || '') || (meta && /only/.test(meta.content || ''));
  if (!optedOut) return;
  // the page's background (see-through counts as the default white)
  const bg = [document.body, root].map((e) => e && getComputedStyle(e).backgroundColor)
    .find((c) => c && !/^(transparent|rgba\([^)]*,\s*0\))$/.test(c)) || 'rgb(255, 255, 255)';
  const [r, g, b] = (bg.match(/[\d.]+/g) || [255, 255, 255]).map(Number);
  if ((0.2126 * r + 0.7152 * g + 0.0722 * b) / 255 < 0.6) return; // already dark enough
  const style = document.createElement('style');
  style.id = 'mi-dark-fallback';
  style.textContent = 'html{filter:invert(1) hue-rotate(180deg);background:#fff}' +
    'img,video,picture,canvas,iframe,embed,object,[style*="background-image"]{filter:invert(1) hue-rotate(180deg)}';
  (document.head || root).appendChild(style);
})()
