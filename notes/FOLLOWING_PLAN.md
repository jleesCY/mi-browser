# Following plan (price tracking, then blog/site feeds)

Status: decided in an interview on 2026-11-17. Phase 1 (prices without AI) built on 2026-11-17, not yet released.

A hub in the Library for things the user follows on the web. It tracks when they change and shows the updates in mi,
with notifications, so the user doesn't have to visit the site. Prices come first; feeds come next.

## Decisions

| Topic | Decision |
|---|---|
| First release | **Prices only.** Feeds come in the next release. Watching any part of a page waits for the feeds release too. |
| Where | **A third Library tab, Following** (Bookmarks · Downloads · Following), with Updates and Prices views (Feeds added later). |
| Which shops | **Any shop.** Checks must survive shops that block automated requests (Amazon): see "Checking". |
| Checking | **A quick fetch first, a hidden page render when needed.** mi remembers for each site which one works. |
| How often | **Per follow; new follows check every 6 hours** (hourly / every 6 h / daily). |
| Logins | **Anonymous by default**, with a per-follow "Use my login" switch (member or region prices). |
| Following a product | **mi detects the price and asks you to confirm** ("Is $129.99 the price?", with "Pick it myself"). |
| Alerts | **Any drop, below a target, a drop of at least X%, back in stock**; set for each product. |
| Notifications | **Chosen per follow** (instant, digest or off). |
| History | **Every price change, kept forever.** Only changes are stored, not every check, so it stays tiny. Gives a chart and the lowest price ever. |
| When a check fails | **Try the fallbacks** (and AI once, if it's on). **Then notify** the user to re-pick the price, also when the result looks suspicious (for example a 90% "drop"). |
| AI | **Used sparingly.** It finds the price when following, repairs a broken follow, and later summarises posts. **Never on routine checks.** Tiers: no AI (always on), the phone's built-in AI, a downloadable model, cloud with the user's own key. **On-device first**, cloud only as a fallback. |
| Feeds (later) | A post opens in **a new tab as the normal page**. AI summaries of new posts are allowed. |

## Checking a price

Each check:
1. **Quick fetch:** a plain HTTP request, asking the site whether anything changed (`If-None-Match` / `If-Modified-Since`). Read the price with the follow's saved extraction rule:
   - the product data in the page (schema.org `Product`/`Offer` JSON-LD, then the `product:price:amount` header tag, then microdata);
   - or the remembered spot (a CSS path plus the text around it) applied to the fetched HTML.
2. **Hidden render** when step 1 finds no price, gets a bot or captcha page, or the site is marked "needs render": load the page in an off-screen WebView. The user's cookies are only used when "Use my login" is on. Run the same extraction in the page, then destroy the WebView.
   - Run at most one render at a time, with a time limit (about 30 s).
   - Remember for each site that it needs a render, so later checks skip step 1.
3. **Compare** with the last price (and stock state) and record a change. Fire whichever alerts match.
4. **If nothing is found:** run the fallbacks, then AI (if on), then mark the follow **Needs attention** and send the failure notification. A price that changes suspiciously (more than 70% at once, or a different currency) also asks for confirmation before any alert goes out.

**Politeness:**
- Space out checks to the same site.
- Back off on 429/403 responses.
- Run a batch only when the battery isn't low; a Wi-Fi-only option comes later if wanted.
- Scheduled with Android's **JobScheduler** (no WorkManager: it would add about 1 MB, including a database library).

## Price parsing (pure, tested)
- Number formats from different countries (`1.299,00 €`, `$1,299.00`, `1 299,00 zł`, `¥1,299`). The currency is always stored with the price.
- Stock: `availability` from the product data (InStock, OutOfStock, PreOrder…), or "out of stock" / "sold out" text near the remembered spot.
- The product variant is part of the follow (keep the URL's query, which usually carries size and colour).
- Test against saved pages from real shops in `app/src/test/resources/`.

## Following a product (the sheet)
**Follow price…** in the tab menu. A chip near the Pill appears when the page script finds product data.
1. Detect: product data, then header tags, then a scored guess (currency-shaped text, large type, near the title or the Add to Cart button), then AI if it's on.
2. Show "**$129.99** · In stock: is this the price?" with **Yes** / **Pick it myself** (tap the price on the page; mi saves its CSS path and nearby text).
3. Set alerts (any drop is on by default; target, percent and back in stock are optional), the check interval and notifications.
4. Following from incognito isn't allowed: offer to open the page in a normal tab.

## The Following tab
- **Updates:** a timeline: "Price dropped to $89 (was $109, lowest ever)", "Back in stock", "Needs attention: pick the price again". Unread items are bold, and the Library button gets a dot.
- **Prices:** each product with its image (from the product data or OpenGraph), title, current price, change since following, lowest price, a small chart, alert summary, last checked time and a Needs attention badge.
- Tapping a product opens a detail sheet: the full chart, alerts, the interval, "Use my login", Check now, Open page, Re-pick price, Unfollow.

## AI tiers (after the core works without AI)
1. **No AI:** always on; covers most shops (product data plus your own picking).
2. **The phone's built-in AI:** Gemini Nano through Android AICore / ML Kit GenAI, offered only where the phone has it. 0 MB added to mi.
3. **Downloadable model:** an optional on-device model (about 0.5–1.5 GB) plus its runtime library in the APK. Runs only while charging.
4. **Cloud:** off by default; the user's own API key with a provider they choose. Only the page's extracted text is sent, never cookies, and never from incognito.

**AI's job:** given the page's visible text and element structure, return the price element and the stock state as a **saved extraction rule** (CSS path plus nearby text). Routine checks then use the rule with no AI. Order: on-device, then cloud.

Setting: **Settings › Following › Page Understanding: Off · On this phone · Cloud (your key)**.

## Notifications
- Channels: **Price changes**, **Needs attention**, and later **New posts**, so Android can mute each.
- Per follow: instant, daily digest or off. Needs attention always notifies; the user asked for this.
- Tapping one opens the Following tab on that item.

## How it fits the code (CLAUDE.md)
- A `follow/` package:
  - `FollowStore`: follows and their price history (JSON, changes only);
  - `FollowRules`: price parsing, stock, matching alerts, the suspicious-change check, the extraction rule format;
  - `PriceChecker`: the fetch and render pipeline;
  - the `JobService`;
  - the Following tab, the follow sheet and the product sheet;
  - the Settings section;
  - `assets/js/follow-detect.js` (detection and applying a rule in the page).
- Library: add the tab to the Library's switch between Bookmarks and Downloads.
- Backup: include follows. Privacy: no follows from incognito.
- Tests: the rules against saved shop pages; Robolectric for the fetch path against a local HttpServer.

## Phases
1. **Prices without AI:**
   - detection with confirm or pick;
   - quick fetch and hidden render;
   - JobScheduler checks every 6 h by default;
   - alerts (drop, target, percent, back in stock);
   - history and chart;
   - Needs attention with its notification;
   - the Following tab (Updates and Prices).
2. **AI for finding and repairing:** the phone's built-in AI, then cloud with the user's key, then the downloadable model.
3. **Feeds:**
   - feed detection and known feed addresses;
   - posts as updates (they open in a new tab as the normal page);
   - per-follow notifications or a digest;
   - AI summaries;
   - OPML import and export.
4. **Watch any part of a page** for changes (restocks, job posts, schedules), reusing picking and rendering.

## Answered (phase 1, tested on the emulator)
- A hidden page load runs fine from the background job with mi fully closed (the job starts a process with no screen).
  Two things were needed: resume the page timers mi pauses in the background, and save the store explicitly when the
  run ends (with no screen there are no frames to set off its automatic saving).
- How often real shops show a captcha even to the hidden page load: still unknown; it needs testing on real shops on a
  phone. If they do, the follow goes to Needs attention, and the user can open the page in mi and pick the price.
