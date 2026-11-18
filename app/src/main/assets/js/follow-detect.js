// Following prices (follow/FollowPage.kt, PriceChecker.kt): run on demand, never at page start. Defines
// window.__miFollow with:
//  - data(): the page's structured data (JSON-LD blocks and meta tags), which mi reads the product's price from, and
//    whether the page itself says it's in stock (stock());
//  - candidates(): the likeliest prices shown on the page (currency-looking text, large, near the title or a buy button,
//    marked as a price), with what makes each likely or not, to choose from;
//  - was(): the crossed-out (old) price shown beside the current one, if any;
//  - around(rule): a sale around a chosen price (a crossed-out price, a percentage off, an amount saved);
//  - SHOPS: where shops mi knows (Amazon) keep their price, tried before anything else;
//  - read(rule): the structured data plus the text at a picked spot (rule: selector, its classes, the text it had), found
//    again by its path, else by its classes, else by the text near where it was;
//  - pick(): the next tap on the page chooses the price: it's outlined and reported to window.miFollow (not followed
//    as a link or button press); stopPicking(): cancelled, taps work as usual again.
// Prices are parsed in Kotlin (FollowRules.parsePrice); this only finds the text.
(() => {
  if (window.__miFollow) return;
  const PRICE = /(?:[$€£¥₹₩₽]|R\$|zł|kr|CHF|USD|EUR|GBP)\s?\d[\d.,\s]*|\d[\d.,\s]*\s?(?:[$€£¥₹]|zł|kr|Kč|Ft|CHF|USD|EUR|GBP)/;
  const visible = (el) => { const r = el.getBoundingClientRect(); const s = getComputedStyle(el); return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; };
  const ownText = (el) => (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
  // text kept for screen readers only (clipped to a pixel, or off the screen): shops put the clean price there
  // ("$199.99") behind one drawn in pieces (a big "$199" and a raised "99")
  const hidden = (el) => {
    if (/offscreen|sr-only|visually-hidden|screen-reader|a11y/i.test(typeof el.className === 'string' ? el.className : '')) return true;
    const r = el.getBoundingClientRect();
    return (r.width <= 1 && r.height <= 1) || r.right < 0;
  };

  /**
   * Shops whose pages mi knows: where their price, and their crossed-out "was" price, are. Checked first, as page layouts
   * there change between visits (and A/B tests) faster than a remembered spot can follow.
   */
  const SHOPS = [
    // the price boxes themselves (their screen-reader copy, inside or beside them, can be empty): Amazon keeps several,
    // some in collapsed sections, so the one on screen is taken (first)
    { host: /(^|\.)amazon\./, price: ['#corePriceDisplay_mobile_feature_div .priceToPay', '#corePriceDisplay_desktop_feature_div .priceToPay',
        '#corePrice_feature_div .priceToPay', '#corePrice_mobile_feature_div .priceToPay', '.apex-pricetopay-value', '.priceToPay',
        '#tp_price_block_total_price_ww .a-price', '#corePrice_desktop .a-price:not(.a-text-price)', '#priceblock_dealprice', '#priceblock_ourprice'],
      was: ['.basisPrice .a-price', '.a-text-price[data-a-strike="true"]'],
      image: ['#main-image', '#landingImage', '#imgTagWrapperId img', '[data-a-image-name="mainImage"]', '[data-a-image-name="landingImage"]'] },
    // Walmart: the price box (a screen-reader copy "$699.00" beside "$", "699", "00" drawn in pieces)
    { host: /(^|\.)walmart\./, price: ['[data-testid="price-wrap"] [itemprop="price"]', '[data-testid="price-wrap"]', '[itemprop="price"]'] },
  ];
  const shop = () => SHOPS.find((s) => s.host.test(location.hostname));
  // on screen: it, or the box just around it (a screen-reader copy is a pixel, its price beside it), has a size; a copy
  // in a collapsed section (Amazon keeps the price twice, once out of sight) has none
  const onScreen = (el) => { for (let e = el, i = 0; e && i < 3; e = e.parentElement, i++) { const r = e.getBoundingClientRect(); if (r.width > 1 && r.height > 1) return true; } return false; };
  // the first element any of the selectors finds that shows a price, one on screen before one out of sight
  const first = (sels) => {
    let unseen = null;
    for (const q of sels || []) for (const el of document.querySelectorAll(q)) {
      if (!/\d/.test(el.textContent)) continue;
      if (onScreen(el)) return el;
      unseen = unseen || el;
    }
    return unseen;
  };

  // the buy button (Add to Cart, Buy Now…), where a page says whether the product can be bought
  const buyButton = () => [...document.querySelectorAll('button, input[type=submit], a')].find((b) => /add to (cart|bag|basket|trolley)|buy now|pre-?order/i.test(ownText(b) || b.value || ''));
  const SOLD_OUT = /out of stock|sold out|currently unavailable|temporarily unavailable|no longer available|notify me when (it's |it is )?(back|available)/i;

  /**
   * In stock (true), out of stock (false) or can't tell (null), from the page itself: around the buy button (disabled,
   * or "Sold out" beside it, is out of stock; there and enabled, in stock); with no buy button, "Out of stock" in the
   * main content. Only near the button counts when there is one, as related products elsewhere say "Sold out" too.
   */
  function stock() {
    const buy = buyButton();
    if (buy) {
      if (buy.disabled || buy.getAttribute('aria-disabled') === 'true' || SOLD_OUT.test(ownText(buy))) return false;
      let area = buy; for (let i = 0; i < 3 && area.parentElement; i++) area = area.parentElement;
      return SOLD_OUT.test(ownText(area).slice(0, 2000)) ? false : true;
    }
    return SOLD_OUT.test(ownText(document.querySelector('main') || document.body).slice(0, 20000)) ? false : null;
  }

  function data() {
    const ld = [...document.querySelectorAll('script[type="application/ld+json"]')].map((s) => s.textContent);
    const meta = {};
    document.querySelectorAll('meta[content]').forEach((m) => {
      const k = (m.getAttribute('property') || m.getAttribute('name') || m.getAttribute('itemprop') || '').toLowerCase();
      if (k) meta[k] = m.getAttribute('content');
    });
    // microdata: <span itemprop="price" content="129.99">
    const ip = document.querySelector('[itemprop="price"]');
    if (ip && !meta['product:price:amount']) meta['product:price:amount'] = ip.getAttribute('content') || ownText(ip);
    const ic = document.querySelector('[itemprop="priceCurrency"]');
    if (ic && !meta['product:price:currency']) meta['product:price:currency'] = ic.getAttribute('content') || ownText(ic);
    return { ld, meta, title: document.title, stock: stock(), blocked: blocked(), image: shopImage(), bigImage: bigImage() };
  }

  // the product's picture where a shop mi knows keeps it (Amazon's phone pages have no share picture, so mi would show
  // just the site's icon)
  function shopImage() {
    const s = shop();
    for (const q of (s && s.image) || []) {
      const img = document.querySelector(q);
      const src = img && (img.getAttribute('data-a-hires') || img.getAttribute('data-old-hires') || img.currentSrc || img.src);
      if (src && /^https?:/.test(src)) return src;
    }
    return null;
  }
  // the largest picture near the top of the page: the product's, on a page with no share picture
  function bigImage() {
    let best = null, area = 0;
    document.querySelectorAll('img').forEach((img) => {
      const r = img.getBoundingClientRect();
      if (r.top + scrollY > 1500 || r.width < 120 || r.height < 120 || !/^https?:/.test(img.currentSrc || img.src)) return;
      if (r.width * r.height > area) { area = r.width * r.height; best = img.currentSrc || img.src; }
    });
    return best;
  }

  // a page standing in for the shop's: a captcha, or "continue shopping" before the real page
  function blocked() {
    if (document.querySelector('form[action*="validateCaptcha"], #captchacharacters, iframe[src*="recaptcha"], iframe[src*="hcaptcha"], #challenge-form, #px-captcha, #cf-challenge-running')) return true;
    const t = ownText(document.body || document.documentElement).slice(0, 1500);
    return t.length < 1500 && /continue shopping|are you a (human|robot)|verify you are human|unusual traffic|robot check/i.test(t);
  }

  // a path back to this element: from the nearest ancestor with a unique id, by tag and position among its kind
  function pathOf(el) {
    const parts = [];
    for (let e = el; e && e.nodeType === 1 && e !== document.body; e = e.parentElement) {
      if (e.id && document.querySelectorAll('#' + CSS.escape(e.id)).length === 1) { parts.unshift('#' + CSS.escape(e.id)); break; }
      const same = [...(e.parentElement ? e.parentElement.children : [])].filter((c) => c.tagName === e.tagName);
      parts.unshift(e.tagName.toLowerCase() + (same.length > 1 ? `:nth-of-type(${same.indexOf(e) + 1})` : ''));
    }
    return parts.join(' > ');
  }
  // its classes, minus ones that come and go (states, generated ids, mi's own outline)
  const classesOf = (el) => [...el.classList].filter((c) => !/\d{3,}|active|hover|selected|visible|^mi-follow/.test(c)).slice(0, 4).join('.');
  // a price split into parts ("$129" and a raised "99") reads as one: "$129 99" is $129.99
  const joined = (t) => /[.,]\d/.test(t) ? t : t.replace(/(\d)\s(\d{2})(?!\d)/, '$1.$2');
  /**
   * The price an element shows: the screen-reader copy inside it (or beside it, within its price) when there is one,
   * else its own text, with a price drawn in parts joined up.
   */
  function priceText(el) {
    for (let e = el, i = 0; e && i < 3 && ownText(e).length <= 80; e = e.parentElement, i++) {
      const copy = (hidden(e) ? [e] : [...e.querySelectorAll('*')]).find((c) => hidden(c) && PRICE.test(ownText(c)) && ownText(c).length <= 30);
      if (copy) return ownText(copy).match(PRICE)[0].trim();
    }
    return joined(ownText(el));
  }
  // the words labelling a price ("List Price:", "With Prime", "Typical price"): the text around it, up to where another
  // price begins (so the next price's label isn't taken for this one's)
  const prices = (t) => (t.match(new RegExp(PRICE.source, 'g')) || []).map((p) => p.replace(/[\s.,]/g, ''));
  function labelOf(el) {
    const mine = prices(priceText(el))[0];
    let area = el;
    while (hidden(area) && area.parentElement) area = area.parentElement; // from what's shown, not its screen-reader copy
    for (let i = 0; i < 4 && area.parentElement; i++) {
      const t = ownText(area.parentElement);
      if (t.length > 120 || prices(t).some((p) => p !== mine)) break;
      area = area.parentElement;
    }
    return ownText(area).replace(new RegExp(PRICE.source, 'g'), ' ').replace(/\s+/g, ' ').trim().replace(/^(.+?) \1$/, '$1').slice(0, 40); // a screen-reader copy doubles it
  }
  const describe = (el) => ({ selector: pathOf(el), classes: classesOf(el), text: priceText(el).slice(0, 60), label: labelOf(el) });
  // not a price to follow: savings, per-unit or per-month amounts, shipping, ranges
  const NOT_THE_PRICE = /save|off\b|\/\s?(mo|month|yr|year|ea|each|oz|lb|kg|g|ml|l)\b|per\s|shipping|delivery|–|—| - /i;

  // the smallest elements whose text is a price ("$129.99" itself, not its whole card). Only text with a digit in it is
  // looked at, climbing at most three levels to take in a price split into parts ("$", "129", "99"): reading every
  // element's text would lay out a big shop page many times over
  function priceElements() {
    const found = new Set();
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let n = walker.nextNode(); n; n = walker.nextNode()) {
      if (!/\d/.test(n.nodeValue)) continue;
      let el = n.parentElement;
      if (!el || /^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE)$/.test(el.tagName) || hidden(el)) continue; // the shown price stands for its hidden copy
      for (let i = 0; i < 3 && el && el !== document.body; i++, el = el.parentElement) {
        const t = ownText(el);
        if (t.length > 40) break;
        if (PRICE.test(t)) { if (!NOT_THE_PRICE.test(t) && visible(el)) found.add(el); break; }
      }
    }
    // an element holding another that was found isn't the price itself
    return [...found].filter((el) => ![...found].some((o) => o !== el && el.contains(o)));
  }

  // crossed out, by its own style or a wrapper's (text-decoration isn't inherited, so a few levels up count too)
  function crossedOut(el) {
    if (el.closest('del, s, strike, [data-a-strike="true"]')) return true;
    for (let e = el, i = 0; e && i < 4; e = e.parentElement, i++) if (/line-through/.test(getComputedStyle(e).textDecorationLine)) return true;
    return false;
  }
  // words that mark a starting price ("From $999": it depends on the size or model chosen)
  const STARTING = /\bfrom\b|starting at|starts at|as low as/i;
  // words that mark an old price
  const OLD_WORDS = /\bwas\b|\blist\b|\bregular\b|\breg\.|\brrp\b|\bmsrp\b|typical|original|compare at/;

  /**
   * The likeliest prices, best first (up to six, one per amount shown), each with what makes it likely or not:
   * crossed out (an old price), large, next to the buy button, marked as a price or sale, and a "was"/"sale" word near it.
   */
  function candidates() {
    const h1 = document.querySelector('h1');
    const buy = buyButton();
    const dist = (a, b) => { if (!b) return Infinity; const r1 = a.getBoundingClientRect(), r2 = b.getBoundingClientRect(); return Math.hypot(r1.x - r2.x, r1.y - r2.y); };
    const marks = (el) => [el, el.parentElement, el.parentElement && el.parentElement.parentElement].filter(Boolean)
      .map((e) => `${e.className && e.className.baseVal === undefined ? e.className : ''} ${e.id || ''} ${e.getAttribute('itemprop') || ''} ${e.getAttribute('data-testid') || ''}`).join(' ').toLowerCase();
    const best = new Map();
    priceElements().forEach((el) => {
      const style = getComputedStyle(el);
      const size = parseFloat(style.fontSize) || 0;
      const struck = crossedOut(el);
      const m = marks(el);
      const label = labelOf(el);
      const around = label.toLowerCase();
      const old = /was|old|list|regular|compare|msrp|rrp|strike|before|basis/.test(m) || OLD_WORDS.test(around);
      const sale = /sale|now|current|final|deal|offer/.test(m) || /\bsale\b|\bnow\b|\bdeal\b/.test(around);
      const marked = /price/.test(m);
      const aside = !!el.closest('header, footer, nav, aside, [class*=related], [class*=recommend], [class*=carousel]');
      const nearBuy = dist(el, buy) < 400;
      const score = size * 2 - Math.min(dist(el, h1), 2000) / 40 - Math.min(dist(el, buy), 2000) / 60 - (struck || old ? 40 : 0)
        + (marked ? 12 : 0) + (sale ? 10 : 0) - (aside ? 30 : 0) - el.getBoundingClientRect().top / 200;
      const from = STARTING.test(label) || STARTING.test(ownText(el));
      const c = { score, struck: struck || old, big: size >= 20, nearBuy, sale, aside, from, ...describe(el), label };
      const key = c.text.replace(/\s/g, '') + (c.struck ? ' was' : ''); // the same amount crossed out elsewhere is its own
      if (!best.has(key) || best.get(key).score < score) best.set(key, c);
    });
    // a shop mi knows: its price first, its "was" price marked
    const s = shop();
    const known = first(s && s.price);
    if (known) best.set('shop', { score: 1e6, struck: false, big: true, nearBuy: false, sale: false, aside: false, shop: true, ...describe(known) });
    const was = first(s && s.was);
    if (was) best.set('shopWas', { score: -1e6, struck: true, big: false, nearBuy: false, sale: false, aside: false, ...describe(was) });
    // the same price twice (the shop's, and as found on the page): once
    if (known) { const k = priceText(known).replace(/\s/g, ''); best.delete(k); }
    if (was) best.delete(priceText(was).replace(/\s/g, '') + ' was');
    return [...best.values()].sort((a, b) => b.score - a.score).slice(0, 6);
  }

  // the crossed-out ("was") price shown beside the current one: the likeliest old price on the page, or null
  function was() {
    const known = first((shop() || {}).was);
    if (known) return priceText(known);
    const c = candidates().find((c) => c.struck && !c.aside); return c ? c.text : null;
  }

  /**
   * The element showing a price (rule: selector, classes, text): by its path; else (the page moved things around) the
   * same kind of element (its classes) showing a price; else where a shop mi knows keeps its price; else (a price with
   * no spot, from the product data) the price on the page that reads the same as rule.text.
   */
  function find(rule) {
    if (!rule) return null;
    // whole amounts read the same with or without cents ("$1,299" is 1299.00)
    const amount = (t) => (t || '').replace(/\D/g, '').replace(/00$/, '');
    const same = (e) => !rule.text || amount(priceText(e)) === amount(rule.text);
    const found = [];
    try { const el = rule.selector && document.querySelector(rule.selector); if (el && PRICE.test(priceText(el))) found.push(el); } catch (e) {}
    if (rule.selector && rule.classes) found.push(...[...document.querySelectorAll('.' + rule.classes.split('.').map(CSS.escape).join('.'))].filter((e) => PRICE.test(priceText(e)) && same(e)));
    if (rule.selector) { const known = first((shop() || {}).price); if (known && same(known)) found.push(known); }
    // the words beside it ("Price:", "Sale", "Your price"), which outlast a page's rearranging
    const words = (t) => (t || '').toLowerCase().replace(/[^a-z ]/g, ' ').replace(/\s+/g, ' ').trim();
    if (rule.label && /[a-z]{3}/i.test(rule.label)) found.push(...priceElements().filter((e) => !crossedOut(e) && same(e) && words(labelOf(e)) === words(rule.label)));
    if (rule.text) found.push(...priceElements().filter((e) => same(e) && !crossedOut(e)));
    // one on screen first: the same price may also sit in a section out of sight
    return found.find(onScreen) || found[0] || null;
  }

  const PERCENT_OFF = /(?:-\s?(\d{1,2})\s?%|(\d{1,2})\s?%\s?off|save\s(\d{1,2})\s?%|(\d{1,2})\s?(?:%|percent)\s?savings)/i;
  const SAVE = new RegExp('(?:save|you save|savings)\\s*:?\\s*(' + PRICE.source + ')', 'i');

  /**
   * A sale around the price the rule finds: in the area holding it (up to 600 characters of text, so not the next
   * product), a crossed-out or old-labelled price ([was]), a percentage off ("-33%", "33% off", "Save 33%") and an amount
   * saved ("Save $100"). mi works out the old price from whichever it finds (FollowRules.saleFrom).
   */
  function around(rule) {
    const el = find(rule);
    if (!el) return null;
    const mine = prices(priceText(el))[0];
    let area = el;
    for (let i = 0; i < 8 && area.parentElement && ownText(area.parentElement).length <= 600; i++) area = area.parentElement;
    let was = null;
    for (const e of area.querySelectorAll('*')) {
      const t = ownText(e);
      if (t.length > 40 || !PRICE.test(t) || e.contains(el) || el.contains(e)) continue;
      const p = prices(priceText(e))[0];
      if (!p || p === mine || e.children.length > 3) continue;
      if (crossedOut(e) || OLD_WORDS.test(labelOf(e).toLowerCase())) { was = priceText(e); break; }
    }
    const pct = ownText(area).match(PERCENT_OFF);
    const save = ownText(area).match(SAVE);
    return { was, percent: pct ? +(pct[1] || pct[2] || pct[3] || pct[4]) : null, save: save ? save[1] : null };
  }

  function read(rule) {
    const out = data();
    if (rule && rule.selector) {
      let el = find(rule);
      // nothing where it was, by any of its marks: the page's likeliest price (Kotlin holds a big jump for you to confirm)
      if (!el) { const best = candidates().find((c) => !c.struck && !c.aside); try { el = best ? document.querySelector(best.selector) : null; } catch (e) {} out.guessed = !!el; if (el) out.repoint = describe(el); }
      out.spot = el ? priceText(el).slice(0, 60) : null;
      if (el) out.near = around({ selector: pathOf(el) });
    }
    return out;
  }

  let picking = false;
  let stopPicking = () => {};
  function pick() {
    if (picking) return;
    picking = true;
    const style = document.createElement('style');
    style.textContent = '.mi-follow-picked{outline:3px solid #34a853 !important;outline-offset:2px;border-radius:4px}';
    document.documentElement.appendChild(style);
    // the page's own handlers don't see the tap (no menus opening, carousels moving); not cancelled, or no click follows
    const hide = (e) => e.stopImmediatePropagation();
    const early = ['pointerdown', 'mousedown', 'touchstart', 'pointerup', 'mouseup', 'touchend'];
    stopPicking = () => {
      document.removeEventListener('click', onTap, true);
      early.forEach((t) => document.removeEventListener(t, hide, true));
      picking = false;
    };
    const onTap = (e) => {
      e.preventDefault(); e.stopImmediatePropagation(); // not followed as a link or button press
      stopPicking();
      // the tapped element, or the price inside it (a tap often lands on the card around a price)
      // a tap on part of a price ("199", or the raised "99") climbs to the whole of it
      let el = e.target;
      for (let up = el, i = 0; up && i < 4 && ownText(up).length <= 60; up = up.parentElement, i++) if (PRICE.test(priceText(up))) { el = up; break; }
      if (!PRICE.test(priceText(el)) || ownText(el).length > 60) el = [...el.querySelectorAll('*')].find((c) => !hidden(c) && PRICE.test(ownText(c)) && ownText(c).length <= 40) || el;
      document.querySelectorAll('.mi-follow-picked').forEach((p) => p.classList.remove('mi-follow-picked'));
      el.classList.add('mi-follow-picked');
      if (window.miFollow) window.miFollow.postMessage(JSON.stringify(describe(el)));
    };
    document.addEventListener('click', onTap, true);
    early.forEach((t) => document.addEventListener(t, hide, true));
  }

  window.__miFollow = { data, candidates, was, around, read, pick, stopPicking: () => stopPicking() };
})();
