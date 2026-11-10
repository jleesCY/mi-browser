// Keeps the page's content clear of the camera cutout while mi's status bar is hidden: the page still fills the
// screen (it shows through the strip as it scrolls), but it starts below the strip. __DP__ is the strip's height in
// dp, or 0 to take the space away again. CSS px are dp divided by the page's zoom (a zoomed-out desktop page needs more).
((dp) => {
  const id = 'mi-top-inset';
  let style = document.getElementById(id);
  if (!dp) { if (style) style.remove(); return; }
  const px = dp / ((window.visualViewport && window.visualViewport.scale) || 1);
  if (!style) {
    style = document.createElement('style');
    style.id = id;
    (document.head || document.documentElement).appendChild(style);
  }
  style.textContent = `html{padding-top:${px}px !important}`;
})(__DP__);
