// Incognito: navigator.credentials.create({publicKey}) is refused, so no passkey is saved from an incognito tab.
// ponytail: a page could reach an unpatched create() through a fresh about:blank iframe; Android still shows its own
// "Create passkey?" sheet then, so nothing is saved without the user confirming.
(() => {
  const proto = window.CredentialsContainer && CredentialsContainer.prototype;
  if (!proto || !proto.create || proto.__miNoCreate) return;
  const create = proto.create;
  Object.defineProperty(proto, '__miNoCreate', { value: true });
  Object.defineProperty(proto, 'create', { configurable: false, writable: false, value: function (options) {
    if (options && options.publicKey) {
      try { window.miBridge && window.miBridge.postMessage(JSON.stringify({ type: 'passkeyBlocked' })); } catch (e) {}
      return Promise.reject(new DOMException("Passkeys can't be created in incognito", 'NotAllowedError'));
    }
    return create.apply(this, arguments);
  } });
})();
