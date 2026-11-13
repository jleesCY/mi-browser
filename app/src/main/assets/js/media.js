// Every frame, at document start: tells mi when audible audio or video starts and stops (window.miMedia, see
// media/BackgroundMedia.kt), for the media notification, background playback and the picture-in-picture window's
// shape; and plays or pauses it when mi asks (notification and lock-screen buttons). One player per frame is followed:
// a muted clip never takes over from one you're listening to. While mi keeps this frame's media playing in the
// background ('keep'), the page is told it's still on screen and full size (sites like YouTube pause themselves when
// hidden or in a small window), and a video the browser engine paused as the page went to the background (no script
// asked, within moments of it) starts again: audio and video both keep playing, as Chrome's own media controls allow.
// Other pauses with no script behind them are Android's (a call, another app playing): they stand, and the engine's own
// resume afterwards is undone; playback starts again only when you press play (in the page or the notification).
(() => {
  const port = window.miMedia;
  if (!port || window.__miMedia) return;
  window.__miMedia = true;
  const token = Math.random().toString(36).slice(2); // tells this frame's reports apart from other frames'
  let current = null;
  let keep = false;
  const proto = Document.prototype;
  const reallyHidden = Object.getOwnPropertyDescriptor(proto, 'hidden').get.bind(document);
  let askedToPause = 0; // when a script last paused media (the page, or mi's notification buttons)
  let askedToPlay = 0;
  let interrupted = false; // Android paused it; don't let the engine resume it by itself
  const pause = HTMLMediaElement.prototype.pause;
  HTMLMediaElement.prototype.pause = function () { askedToPause = Date.now(); return pause.call(this); };
  const play = HTMLMediaElement.prototype.play;
  HTMLMediaElement.prototype.play = function () { askedToPlay = Date.now(); return play.apply(this, arguments); };
  [['hidden', false], ['webkitHidden', false], ['visibilityState', 'visible'], ['webkitVisibilityState', 'visible']].forEach(([name, shown]) => {
    const real = Object.getOwnPropertyDescriptor(proto, name);
    if (real && real.get) Object.defineProperty(document, name, { configurable: true, get() { return keep ? shown : real.get.call(this); } });
  });
  // the window's outer size: YouTube reads a window much smaller than the screen as picture-in-picture or split
  // screen, and pauses there. Pages lay out by the inner size, which stays real.
  [['outerWidth', 'width'], ['outerHeight', 'height']].forEach(([name, side]) => {
    const real = Object.getOwnPropertyDescriptor(window, name) || Object.getOwnPropertyDescriptor(Window.prototype, name);
    if (real && real.get) Object.defineProperty(window, name, { configurable: true, get() { return keep ? screen[side] : real.get.call(window); } });
  });
  // in picture-in-picture the fullscreen video fills the small window (sites size it for the screen they believe in)
  const fill = document.createElement('style');
  fill.textContent = 'video.mi-pip{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;' +
    'object-fit:contain!important;transform:none!important;z-index:2147483647!important;background:#000!important}';
  const fitPip = () => {
    const small = keep && !!document.fullscreenElement && innerWidth * innerHeight < screen.width * screen.height / 2;
    if (small && !fill.isConnected) (document.head || document.documentElement).appendChild(fill);
    document.querySelectorAll('video.mi-pip').forEach((v) => v !== current && v.classList.remove('mi-pip'));
    if (current) current.classList.toggle('mi-pip', small);
  };
  window.addEventListener('resize', fitPip, true);
  let hiddenAt = 0; // when the page really went to the background
  window.addEventListener('visibilitychange', () => { if (reallyHidden()) hiddenAt = Date.now(); }, true); // before block
  const block = (e) => { if (keep) e.stopImmediatePropagation(); };
  ['visibilitychange', 'webkitvisibilitychange'].forEach((t) => window.addEventListener(t, block, true)); // before the page's own
  const silent = (el) => el.muted || el.volume === 0;
  const report = (el) => {
    const md = navigator.mediaSession && navigator.mediaSession.metadata;
    port.postMessage(JSON.stringify({
      token,
      playing: !el.paused && !el.ended,
      muted: silent(el),
      duration: isFinite(el.duration) ? el.duration : -1,
      width: el.videoWidth || 0,
      height: el.videoHeight || 0,
      title: (md && md.title) || document.title || '',
      artist: (md && md.artist) || location.hostname,
      position: el.currentTime || 0,
      rate: el.playbackRate || 1,
    }));
  };
  const on = (e) => {
    const el = e.target;
    if (!(el instanceof HTMLMediaElement)) return;
    if (e.type === 'playing' && el !== current && (!current || current.paused || !silent(el))) current = el;
    // single-page sites (YouTube) swap the title shortly after the next video starts
    if (e.type === 'playing') setTimeout(() => el === current && report(el), 3000);
    if (el !== current) return;
    // a live stream's duration keeps moving as it loads: nothing the notification shows changes
    if (e.type === 'durationchange' && !isFinite(el.duration)) return;
    if (e.type === 'pause' && !el.ended && Date.now() - askedToPause > 1000) {
      if (keep && reallyHidden() && Date.now() - hiddenAt < 3000) { el.play().catch(() => report(el)); return; }
      interrupted = true;
    }
    if (e.type === 'play') {
      const tapped = navigator.userActivation && navigator.userActivation.isActive; // the page's own play button
      if (interrupted && Date.now() - askedToPlay > 1000 && !tapped) { el.pause(); return; }
      interrupted = false;
    }
    report(el);
  };
  // media events don't bubble: listen while they travel down (capture)
  // 'play' too (the attempt, before 'playing'): a tab mi suspended while paused must wake for media to actually start,
  // e.g. when Android resumes it after a call
  ['play', 'playing', 'pause', 'ended', 'volumechange', 'seeked', 'ratechange', 'durationchange'].forEach((t) => document.addEventListener(t, on, true));
  port.onmessage = (e) => {
    if (!current && e.data !== 'release') return;
    if (e.data === 'keep' || e.data === 'release') keep = e.data === 'keep';
    else if (e.data === 'play') current.play().catch(() => {});
    else if (e.data === 'pause') current.pause();
    else if (typeof e.data === 'string' && e.data.startsWith('skip:')) current.currentTime = Math.max(0, current.currentTime + Number(e.data.slice(5)));
    else if (typeof e.data === 'string' && e.data.startsWith('seek:')) current.currentTime = Number(e.data.slice(5)); // the notification's seek bar
  };
})();
