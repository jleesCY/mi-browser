// Saving files a page makes itself (blob: and data: addresses, as file converters, editors and exports make): mi's
// downloader can't fetch those from outside the page, so the page reads the file and hands it over to mi in pieces
// (the miBlob channel; downloads/BlobDownloads). It keeps each file the page makes into an address, so the file can
// still be read after the page throws the address away (pages often do, right after clicking the link), and the name
// a download link gives it. mi asks for a file with a one-time token (only after a real download starts), so a page
// can't push files into Downloads by itself. Runs at document start.
(() => {
  if (window.__miBlob) return;
  const CHUNK = 512 * 1024;
  const blobs = new Map(); // address -> the file it was made from
  const names = new Map(); // address -> the name its download link gives it
  const create = URL.createObjectURL, revoke = URL.revokeObjectURL;
  URL.createObjectURL = function (o) {
    const u = create.call(URL, o);
    if (o instanceof Blob) blobs.set(u, o);
    return u;
  };
  URL.revokeObjectURL = function (u) {
    const b = blobs.get(u);
    if (b) setTimeout(() => { if (blobs.get(u) === b) { blobs.delete(u); names.delete(u); } }, 60000); // long enough to be asked for it
    return revoke.call(URL, u);
  };
  const note = (a) => {
    if (a && a.href && /^(blob|data):/.test(a.href) && a.hasAttribute('download')) names.set(a.href, a.getAttribute('download'));
  };
  document.addEventListener('click', (e) => note(e.target && e.target.closest && e.target.closest('a[href]')), true);
  const click = HTMLAnchorElement.prototype.click; // a link made in code and clicked without joining the page
  HTMLAnchorElement.prototype.click = function () { note(this); return click.apply(this, arguments); };

  const post = (m) => { try { miBlob.postMessage(JSON.stringify(m)); } catch (_) {} };
  const bytes = (buf) => {
    let s = '';
    for (let i = 0; i < buf.length; i += 0x8000) s += String.fromCharCode.apply(null, buf.subarray(i, i + 0x8000));
    return btoa(s);
  };
  window.__miBlob = {
    // mi asks for [href] with [token]: the file goes over in pieces, then "end" (or "fail")
    save: async (href, token) => {
      const name = names.get(href) || '';
      try {
        const blob = blobs.get(href) || await (await fetch(href)).blob();
        post({ t: 'start', token, name, type: blob.type || '', size: blob.size });
        for (let o = 0; o < blob.size; o += CHUNK) post({ t: 'chunk', token, data: bytes(new Uint8Array(await blob.slice(o, o + CHUNK).arrayBuffer())) });
        post({ t: 'end', token });
      } catch (_) { post({ t: 'fail', token, name }); }
    },
  };
})();
