// Every page, at document start: window.print() and (where the page has none) navigator.share go to mi through
// window.miBridge, which mi answers only for the main frame and only right after a real touch (see PageBridge.kt).
(() => {
  const bridge = window.miBridge;
  if (!bridge || window.__miBridgeShim) return;
  window.__miBridgeShim = true;
  const send = (m) => bridge.postMessage(JSON.stringify(m));
  window.print = () => send({ type: 'print' });
  if (!navigator.share) {
    navigator.share = (d) => {
      if (navigator.userActivation && !navigator.userActivation.isActive)
        return Promise.reject(new DOMException('Sharing requires a user gesture', 'NotAllowedError'));
      d = d || {};
      if (d.files && d.files.length) return Promise.reject(new DOMException('Sharing files is not supported', 'NotAllowedError'));
      let url = '';
      try { url = d.url ? new URL(d.url, location.href).href : ''; } catch (e) { return Promise.reject(new TypeError('Invalid URL')); }
      send({ type: 'share', title: String(d.title || ''), text: String(d.text || ''), url });
      return Promise.resolve();
    };
    navigator.canShare = (d) => !!d && !(d.files && d.files.length) && !!(d.url || d.text || d.title);
  }
})();
