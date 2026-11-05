// Reader mode: run once the page has loaded. Restyles the page for reading (narrow column, larger text) and hides
// navigation, headers, footers, sidebars and ad boxes.
(function() {
  var style = document.createElement('style');
  style.type = 'text/css';
  style.appendChild(document.createTextNode('body { max-width: 800px; margin: 0 auto; padding: 20px; font-size: 18px; line-height: 1.6; color: #333; background: #fff; } img { max-width: 100%; height: auto; } nav, header, footer, .ad, .advertisement, .sidebar { display: none !important; }'));
  document.head.appendChild(style);
})();
