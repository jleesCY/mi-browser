"use client";

import { Footer } from "@/components/footer";
import { Navbar } from "@/components/navbar";
import { useHighlight } from "@/hooks/useHighlight";
import { motion } from "framer-motion";
import {
  Calculator,
  CirclePlay,
  Hand,
  Bookmark,
  BookOpen,
  ChevronRight,
  Clock,
  FolderHeart,
  GripHorizontal,
  History,
  Home,
  Layers,
  Layout,
  Lock,
  Maximize,
  Monitor,
  MousePointer2,
  Navigation,
  Palette,
  Pencil,
  QrCode,
  RefreshCw,
  Search,
  Settings2,
  Share2,
  ShieldCheck,
  Trash2,
  Zap,
  Pin,
  Tag,
  Puzzle,
  Star,
  EyeOff,
  Group,
  Boxes,
} from "lucide-react";
import { DeviceMockup } from "@/components/device-mockup";


export default function UserDocs() {
  useHighlight();
  const fadeInUp = {
    initial: { opacity: 0, y: 20 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true },
    transition: { duration: 0.6 }
  };

  return (
    <main className="min-h-screen bg-surface-container-low text-on-surface transition-colors duration-300 overflow-x-hidden">
      <Navbar />

      <div className="pt-24 sm:pt-32 pb-20 px-6">
        <div className="max-w-4xl mx-auto">
          <motion.div {...fadeInUp} className="mb-16 text-center sm:text-left">
            <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tighter mb-6">
              The Complete <span className="text-primary italic">User Guide</span>
            </h1>
            <p className="text-lg sm:text-xl text-on-surface-variant leading-relaxed font-medium">
              A comprehensive deep-dive into every feature, setting, and interaction within mi. Browser.
            </p>
          </motion.div>

          <section className="space-y-24 sm:space-y-32">

            {/* 1. The Pill - IMAGE RIGHT */}
            <motion.div id="the-pill" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-primary-container text-on-primary-container">
                  <Navigation size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">The Pill</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Everything starts from the Pill at the bottom of the screen, within reach of your thumb.
                  </p>
                  <div id="gestures" className="space-y-4 inline-block text-left w-full">
                    <GuideItem icon={<MousePointer2 size={18} />} title="Tap" desc="Type an address or a search." />
                    <GuideItem icon={<ChevronRight size={18} className="-rotate-90" />} title="Swipe Up" desc="Open the tab switcher." />
                    <GuideItem icon={<ChevronRight size={18} />} title="Swipe Sideways" desc="Right to go back, left to go forward." />
                    <div id="recall-button">
                      <GuideItem icon={<ChevronRight size={18} className="rotate-90" />} title="Swipe Down" desc="Hide the Pill. Tap the small button left behind to bring it back, or drag it to either side." />
                    </div>
                    <GuideItem icon={<Search size={18} />} title="Paste and Go" desc="Just copied a link? Tap the Pill to open it." />
                    <GuideItem icon={<MousePointer2 size={18} />} title="Hold" desc="Copy the page's link." />
                  </div>
                </div>
                <DeviceMockup src="/images/browsing.jpg" color="blue" />
              </div>
            </motion.div>

            {/* 1b. More ways to browse */}
            <motion.div id="more-ways" {...fadeInUp} className="space-y-8">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-secondary-container text-on-secondary-container">
                  <Maximize size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">More Ways to Browse</h2>
              </div>
              <div className="grid sm:grid-cols-2 gap-6 text-left">
                <div id="incognito">
                  <GuideItem icon={<Lock size={18} />} title="Incognito" desc="Tap the mask in the Pill on a new tab. Nothing is saved, and everything is erased when you close it." />
                </div>
                <div id="home-screen-apps">
                  <GuideItem icon={<Maximize size={18} />} title="Sites as Apps" desc="Tab menu › Home screen puts a site on your home screen as its own app, without the Pill." />
                </div>
                <div id="immersive">
                  <GuideItem icon={<Maximize size={18} />} title="Immersive" desc="Tab menu › Immersive hides the Pill on one tab. A notification brings it back." />
                </div>
                <div id="search-widget">
                  <GuideItem icon={<Search size={18} />} title="Search Widget" desc="Add mi's widget to your home screen to start a search, an incognito tab or a QR scan." />
                </div>
                <div id="big-screens">
                  <GuideItem icon={<Monitor size={18} />} title="Landscape and Tablets" desc="mi turns with your phone, fits wide screens and works in split screen." />
                </div>
              </div>
            </motion.div>

            {/* 2. The Tab Switcher - IMAGE LEFT */}
            <motion.div id="dashboard" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-secondary-container text-on-secondary-container">
                  <Layout size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">The Tab Switcher</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                  <DeviceMockup src="/images/tab_switcher.jpg" color="orange" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Swipe up on the Pill from any page to open the Tab Switcher. Your current tab sits on the far right; swipe right to go back through the tabs you used before. The card's title and icon appear as soon as you start swiping. The bar at the bottom has a new tab button (+) and the ⋮ tab menu, with Bookmarks on the left and Settings on the right. Swipe that Pill right to open the Library, left to open Settings, or up for History and Recently Closed (Settings › Gestures lets you choose which page each swipe opens). Hold it to search your open tabs.
                  </p>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-left">
                    <GuideItem icon={<Layers size={18} />} title="Tabs" desc="Swipe between cards and tap one to zoom back into it. Swipe a card up to close it (with undo). Pinned tabs show a pin and can't be swiped away." />
                    <GuideItem icon={<Bookmark size={18} />} title="Library" desc="The left button (or swiping the Pill right) opens Bookmarks, Downloads, Following and Extensions on top of the switcher; swiping the Pill up (or its clock button) opens History and Recently Closed. Each searches from a Pill at the bottom; swipe that Pill back the way the page came to return to the switcher." />
                    <GuideItem icon={<History size={18} />} title="New Tab" desc="The + button opens a fresh tab and zooms into it." />
                    <GuideItem icon={<Settings2 size={18} />} title="Settings" desc="The right button (or swiping the Pill left) opens Settings on top of the switcher. Search from the Pill at the bottom; swipe it right to go back up a level, and from the top level back to the switcher." />
                  </div>
                  <div className="p-4 rounded-xl border border-outline-variant bg-surface-container-high text-[10px] sm:text-xs text-on-surface-variant">
                    <p><strong>Tab Menu:</strong> Tap ⋮ for the centered tab: Favorite, Bookmark, Pin and Share along the top, then three pages, switched like the Library's: Tab (Desktop site, Reader mode, Find in page, Block ads here, Group and Profile tiles), Extensions (each extension's button, and Add Extension) and Tools (Scan QR, QR code, Print, Follow price, Add shortcut, Home screen, Immersive). It opens on the page you used last. Copy link is beside the tab's name. &quot;Close all tabs&quot; keeps pinned tabs.</p>
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 3. Search & Favorites - IMAGE RIGHT */}
            <motion.div id="recent-history" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-primary-container text-on-primary-container">
                  <Maximize size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Search &amp; Favorites</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Tap the Pill and it grows upward with what you need, the best match closest to your thumb. Your favorites wait on every new tab.
                  </p>
                  <div className="space-y-4 inline-block text-left w-full">
                    <GuideItem icon={<Calculator size={18} />} title="Inline Answers" desc="Math, unit conversions, number bases, the time in a city, coin flips and dice are answered right above the field as you type, worked out on your phone. Tap an answer to copy it." />
                    <GuideItem icon={<Clock size={18} />} title="Recent Searches" desc="Your past searches appear as soon as you tap the Pill. Tap one to put it back in the field; tap its ✕ to forget it." />
                    <GuideItem icon={<Search size={18} />} title="Search Suggestions" desc="Suggestions from your search engine appear as you type, with the part you typed in bold. Tap ↖ to fill one in and keep typing." />
                    <div id="search-engine-only"><GuideItem icon={<Zap size={18} />} title="Shortcuts" desc="Type ! for your search shortcuts, like !w for Wikipedia. !se always searches, even for something that looks like an address." /></div>
                    <GuideItem icon={<FolderHeart size={18} />} title="Favorites" desc="Your favorites sit at the bottom of every new tab, four across and two down, swiped sideways for more. Add one with the star in the tab menu, or hold a link and choose Add to Favorites." />
                    <GuideItem icon={<GripHorizontal size={18} />} title="Rearrange Favorites" desc="Touch and hold a favorite, then drag it to its new place." />
                    <GuideItem icon={<Trash2 size={18} />} title="Edit or Remove a Favorite" desc="Touch and hold a favorite and let go without moving it to edit its name and address; Delete is in there too. Favorites also live in a Favorites folder in your bookmarks; deleting that folder deletes them all (mi asks first)." />
                  </div>
                  <div className="p-4 rounded-xl border border-outline-variant bg-surface-container-high text-[10px] sm:text-xs text-on-surface-variant">
                    <p><strong>Configurable:</strong> Settings › Pill › While Typing switches each kind of suggestion on or off, and Settings › Home Page › Favorites shows or hides the favorites.</p>
                  </div>
                </div>
                <DeviceMockup src="/images/recent_history.jpg" color="blue" />
              </div>
            </motion.div>

            {/* 4. Visual Tab Management - IMAGE LEFT */}
            <motion.div id="tabs" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-tertiary-container text-on-tertiary-container">
                  <Layers size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Tab Management</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                  <DeviceMockup src="/images/tab_zoom.jpg" color="purple" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Moving between tabs feels like switching apps: the live page zooms out into its card under your finger, and zooms back in when you pick a tab.
                  </p>
                  <div className="space-y-4 inline-block text-left">
                    <GuideItem icon={<Trash2 size={18} />} title="Closing Tabs" desc="Swipe a card up. It resists until you pass the threshold (you'll feel a tick), then follows your finger; let go to close. Tap Undo if you didn't mean to. 'Close all tabs' is in the ⋮ menu." />
                    <div id="tab-editing">
                      <GuideItem icon={<Pencil size={18} />} title="Tab Editing" desc="Long-press a card to rename the tab." />
                    </div>
                    <GuideItem icon={<Zap size={18} />} title="Live Previews" desc="Every card shows a snapshot of its page, updated whenever you leave the tab." />
                    <div id="tab-groups">
                      <GuideItem icon={<Group size={18} />} title="Tab Groups" desc="A tab you open from a page's link joins that page's group (the two start one, named for its site). Once there's a group, chips above the cards show All or one group's tabs; + makes a group of the centered tab, and holding a chip renames or ungroups it. Put any tab in a group, or take it out, from the Group tile in the tab menu. Incognito tabs aren't grouped." />
                    </div>
                    <div id="profiles">
                      <GuideItem icon={<Boxes size={18} />} title="Profiles" desc="Keep Work and play apart: each profile, shown by an icon in its own colour and named for it (Work, Home, Games…), has its own tabs, tab groups, history and recent searches. Turn them on in Settings › Tabs › Profiles. You start with one, Default. Make another there (+), or with the Profile tile in the tab menu, where New Profile moves that tab into it and you can move a tab to any profile. The Tab Switcher shows the profile you're in beside its tab count: tap it for every profile and New Profile, then tap one to switch (only its tabs and groups show), its pencil (or a profile in Settings › Tabs › Profiles) to change its icon and colour, delete it, or Keep Sign-ins Separate, which gives it its own cookies, sign-ins and site data. New tabs and links open in the profile you're in. History shows the profile you're in, with chips to add the others. Bookmarks are shared." />
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 5. Bookmark Management - IMAGE RIGHT */}
            <motion.div id="bookmarks" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-tertiary-container text-on-tertiary-container">
                  <FolderHeart size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Bookmark Management</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Keep your favorite corners of the web organized. mi. Browser provides a flexible bookmarking system with folder support.
                  </p>
                  <div className="space-y-4 inline-block text-left w-full">
                    <GuideItem icon={<MousePointer2 size={18} />} title="Quick Save" desc="Open the Tab Switcher's ⋮ menu and tap Bookmark to save the centered tab to a folder of your choice." />
                    <div id="bookmark-swiping">
                      <GuideItem icon={<RefreshCw size={18} />} title="Swipe to Edit or Delete" desc="Swipe right on a bookmark or folder to edit it (name, address, folder), or left to delete it; mi asks before deleting, and says what's inside a folder. To move a bookmark, open Location and browse into the folder you want, or make a new one there." />
                    </div>
                    <GuideItem icon={<FolderHeart size={18} />} title="Nested Folders" desc="Create and manage folders to group related bookmarks together for easier access." />
                    <GuideItem icon={<RefreshCw size={18} />} title="Import &amp; Export" desc="Settings → Library → Import Bookmarks brings bookmarks from Chrome, Firefox, Edge or Safari: choose their exported bookmarks file, or paste its contents. Export Bookmarks saves yours to a file any browser can import." />
                    <GuideItem icon={<Search size={18} />} title="Instant Search" desc="Quickly find any saved bookmark by typing in the search Pill at the bottom of the Bookmarks view." />
                  </div>
                </div>
                <DeviceMockup src="/images/bookmarks.jpg" color="pink" />
              </div>
            </motion.div>

            {/* 6. History Management - IMAGE LEFT */}
            <motion.div id="history" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-secondary-container text-on-secondary-container">
                  <Clock size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">History Management</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                  <DeviceMockup src="/images/history.jpg" color="green" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Never lose a page you&apos;ve visited. mi. keeps a local-only record of your browsing activity that stays entirely on your device.
                  </p>
                  <div className="space-y-4 inline-block text-left">
                    <GuideItem icon={<Search size={18} />} title="Search History" desc="Filter through your past visits using the search bar within the History view." />
                    <GuideItem icon={<Trash2 size={18} />} title="Partial Deletion" desc="Swipe left on any history item to remove it individually without affecting the rest of your data." />
                    <GuideItem icon={<RefreshCw size={18} />} title="Clear Ranges" desc="Wipe your history for the last hour, day, week, four weeks, or all time via the ⋮ menu in History." />
                    <div id="recently-closed">
                      <GuideItem icon={<Layers size={18} />} title="Recently Closed" desc="Tabs you closed this session are listed in History's Recently Closed section (swipe up on the tab switcher's Pill, or tap its clock button). They show as cards with the page as it was; tap one to open it again where it was, or clear them all from the ⋮ menu. They're kept until mi closes (or longer: see Keep Recently Closed), and never from incognito." />
                    </div>
                    <GuideItem icon={<ShieldCheck size={18} />} title="Local-Only" desc="Your history is never uploaded to any cloud. It is stored only in the app's private storage on your device." />
                    <div id="downloads">
                      <GuideItem icon={<Layers size={18} />} title="Downloads" desc="Beside Bookmarks in the Library, grouped by day. Downloads in progress show their progress, speed and time left, with pause and cancel (they keep going when you leave mi, with a notification). Large files come over several connections at once, which is often quicker. A download whose connection drops tries again by itself when you're back online, and one that was running when mi closed carries on when you open it; finished ones open with a tap and can be shared. Swipe one left to delete it from your phone (or cancel it, if it's still downloading); mi asks first. ⋮ › Delete All Downloads deletes every file mi downloaded, and nothing else. Files you delete elsewhere leave the list." />
                    </div>
                    <div id="following">
                      <GuideItem icon={<Tag size={18} />} title="Following Prices" desc="On a product page, open the tab menu and tap Follow Price. mi finds the price (the shop's product data, or the likeliest price shown, with any others it found under Other Prices Found) and any sale beside it, so a crossed-out price, '-33%' or 'Save $100' shows as 'Was … · N% off'. Not right? Tap Pick It Myself, then tap the price on the page. You can rename the product before following. Library › Following lists everything you follow with its price, sale and stock (out of stock in red); products with a change you haven't seen are pinned at the top with a dot, and you can hold and drag the rest into any order. Tap one for its price graph (press and drag along it to read any day), its lowest and highest prices, Check Now, its alerts (drops or any change, the smallest change worth one, stock changes, right away, daily or never), how often it's checked, Use My Login for member prices, and Pause Checking. Checks run in the background on your phone, with no account or server; a check that keeps failing tells you why." />
                    </div>
                    <div id="following-widget">
                      <GuideItem icon={<Tag size={18} />} title="Following Widget" desc="Add mi's Following widget to your home screen to see every product you follow, scrolling, with its picture, price and how it moved; those that changed since you last looked come first with a dot. Tap one to open it in mi, or the refresh button to check every price now (it shows 'Checking 3 of 12' as it goes)." />
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 7. Detailed Settings Reference - IMAGE RIGHT */}
            <motion.div id="customization" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-secondary-container text-on-secondary-container">
                  <Settings2 size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Settings</h2>
              </div>

              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-lg text-on-surface-variant leading-relaxed font-medium">
                    Every aspect of mi. Browser can be customized. Settings opens on a list of sections; tap one to open its page, or search to find any setting from any section. Here is a breakdown of every available setting:
                  </p>
                  <div className="p-4 rounded-xl border border-outline-variant bg-surface-container-high text-[10px] sm:text-xs text-on-surface-variant">
                    <p><strong>Pro Tip:</strong> Most settings apply in real-time. You can change your accent color or UI spacing and see the results instantly without restarting the app.</p>
                  </div>
                </div>
                <DeviceMockup src="/images/settings.jpg" color="orange" />
              </div>

              <div className="grid gap-12">
                <SettingsSection title="Search" icon={<Search size={20} />}>
                  <SettingItem title="Search Engine" desc="Support for Google, Bing, DuckDuckGo, Brave, Ecosia, and Yahoo. Pick Custom… to use any other search site: enter a name and an address with %s where your search goes (for example https://www.startpage.com/do/search?q=%s)." />
                  <SettingItem title="Your Shortcuts and Built-in Shortcuts" id="setting-search-shortcuts" desc="Start a search with a shortcut to search one site: !w Wikipedia, !yt YouTube, !r Reddit, !a Amazon, !m Maps, !i Images, and !se your search engine. Only one shortcut, at the very start. It turns into a small pill in the Pill once you type a space after it. Your Shortcuts lists yours and lets you add more (a short name plus the site&apos;s search address, with %s where the search goes); yours take priority. To add one, search a site as usual, then tap ⋮ › Add shortcut: mi finds the site's search for you." />
                </SettingsSection>

                <SettingsSection title="Web Pages" icon={<Monitor size={20} />}>
                  <SettingItem title="Text Size" desc="Text size on websites, from 50% to 200%, separate from the app's own Font Size." />
                  <SettingItem title="Darken" desc="In dark theme, darkens websites that don't have a dark mode of their own, including bright ones that ask not to be darkened (their pictures keep their colours)." />
                  <SettingItem title="Desktop Mode" desc="Request desktop versions of websites. Each tab can still switch from the tab menu." />
                  <SettingItem title="Reader Mode" desc="Simplify pages for reading. Each tab can still switch from the tab menu." />
                </SettingsSection>

                <SettingsSection title="Media" icon={<CirclePlay size={20} />}>
                  <SettingItem title="Block Video Autoplay" desc="Videos wait for a tap instead of playing on their own." />
                  <SettingItem title="Picture-in-Picture" desc="A fullscreen video keeps playing in a small floating window when you leave mi, shaped to the video. On by default." />
                  <SettingItem title="Background Playback" desc="Audio and video keep playing when you leave mi or lock your phone, with play/pause, a seek bar and back/forward 10 seconds in a notification and on the lock screen. Playback pauses when headphones disconnect, a call comes in or another app plays, and stays paused until you press play." />
                </SettingsSection>

                <SettingsSection title="Look & Feel" icon={<Palette size={20} />}>
                  <SettingItem title="Theme" desc="Choose Light, Dark, or System (follows your device's dark mode)." />
                  <SettingItem title="Pure Black" desc="In dark mode, use true black backgrounds. Saves battery on OLED screens; the tab switcher is black too, while cards and the Pill stay a shade lighter." />
                  <SettingItem title="Accent Color" desc="Adaptive (your wallpaper colors, Android 12+), Gray, Blue (the default), Green, Red, Orange, Yellow, or Purple. The full Material 3 color scheme is generated from your choice." />
                  <SettingItem title="App Icon" desc="How mi looks on your home screen: the globe on a dark or a light background, each shown as a preview. Your launcher may take a moment to show the change." />
                  <SettingItem title="Font Size" desc="Adjust the global font scale from 80% to 120% to suit your readability needs." />
                  <SettingItem title="Font" desc="Standard (Google Sans Flex), Rounded (its rounded stroke ends) or System (your phone's own font)." />
                  <SettingItem title="Corners" desc="Choose Square (0dp), Soft (12dp), or Round (28dp) for sheets, cards, and the Pill." />
                  <SettingItem title="List Spacing" desc="Select Compact, Normal, or Airy to adjust the padding and density of the interface." />
                  <SettingItem title="Status Bar" desc="Toggles the visibility of your device's system status bar (clock, battery, etc.) for a more immersive look. With it hidden, pages (sticky headers included) still start below the camera cutout and scroll behind it." />
                  <SettingItem title="Rotate Screen" desc="mi turns sideways with your phone when Android's auto-rotate is on. Off, it stays in portrait (full-screen video still turns)." />
                  <SettingItem title="List Style" desc="Table (rows edge to edge, the default) or Cards (each row its own card) for Downloads, Following and History." />
                  <SettingItem title="Pan Overflowed Text" desc="One-line text too long to fit slides slowly to show the rest instead of ending in '…'. On by default for Settings and the tab menu; each other place has its own switch. Web addresses never pan." />
                  <SettingItem title="Gesture Haptics" desc="A light tap when a gesture is about to act: a swipe on a list row, the Pill opening the tab switcher or going back and forward, pull to refresh, closing a tab card, and long presses. On by default." />
                </SettingsSection>

                <SettingsSection title="Pill" icon={<Navigation size={20} />}>
                  <SettingItem title="Auto Hide" desc="On (the default), scrolling down a page tucks the Pill into the recall button and scrolling up brings it back. Off, it stays put; you can still swipe it down." />
                  <SettingItem title="Loading Bar" desc="Choose the progress bar style: Standard (Left-to-Right), Center Out, or Hidden." />
                  <SettingItem title="Quick Actions" desc="While typing: actions like Paste and Go, offered above the Pill when you tap it. On by default." />
                  <SettingItem title="Inline Answers" desc="While typing: math, unit conversions, number bases, the time and date (here or in a city), coin flips and dice, worked out on your phone and shown right above the field. Tap an answer to copy it. On by default." />
                  <SettingItem title="Shortcuts" desc="While typing: start with ! and your search shortcuts appear, narrowing as you type; tap one to put it in the Pill. On by default." />
                  <SettingItem title="Recent Searches" desc="While typing: your past searches, above the Pill when you tap it. Never in incognito tabs. On by default." />
                  <SettingItem title="Search Suggestions" desc="While typing: suggestions from your search engine. Turn off to keep keystrokes from being sent before you search. On by default." />
                  <SettingItem title="Suggestions in Incognito" desc="Off by default: in an incognito tab, nothing you type is sent to your search engine until you search." />
                </SettingsSection>

                <SettingsSection title="Home Page" icon={<Home size={20} />}>
                  <SettingItem title="Logo" desc="Choose how the mi. logo (a globe with mi.'s dot) shows on a blank tab: None, Static, or Fidget (drag it around)." />
                  <SettingItem title="Clock" desc="Select the clock format on the home page: None, 12h, or 24h." />
                  <SettingItem title="Date" desc="Choose the date placement on the home page: None, Above Clock, or Below Clock." />
                  <SettingItem title="Favorites" desc="Your favorites at the bottom of the home page, four across and two down, swiped sideways for more. Hold one and drag to rearrange it, or hold and let go to edit it. On by default." />
                </SettingsSection>

                <SettingsSection title="Ad Blocking" icon={<ShieldCheck size={20} />}>
                  <SettingItem title="Block Ads" desc="Blocks ads and ad networks with EasyList and the StevenBlack malware and ad domain list: matching requests are dropped and leftover ad spaces are hidden, and small page scripts from uBlock Origin's lists defuse ads served with a site's own content (as on YouTube), anti-adblock walls and pop-unders. On by default." />
                  <SettingItem title="Block Trackers" desc="Blocks analytics and tracking scripts that follow you across sites (EasyPrivacy). On by default." />
                  <SettingItem title="Hide Cookie Banners" desc="Hides cookie-consent pop-ups with the EasyList Cookie List. Turn it off if a site won't let you continue without answering its banner." />
                  <SettingItem title="Remove Tracking from Links" desc="Strips tracking tags such as utm_source, fbclid and gclid from links you open, copy or share. On by default." />
                  <SettingItem title="Allowed Sites" desc="Sites where ad, tracker and cookie-banner blocking is off. Add one from the tab menu on that site (switch off Block ads here); remove it here." />
                  <SettingItem title="Update Filter Lists" desc="The lists update themselves weekly on Wi-Fi; tap to update them now. Shows when they were last updated." />
                </SettingsSection>

                <SettingsSection title="Privacy & Security" icon={<Lock size={20} />}>
                  <SettingItem title="Clear Browsing Data" desc="Deletes cookies, site storage, cached files, saved site icons and tab snapshots, plus this session's sign-ins, permissions and certificate exceptions. You will be signed out of websites." />
                  <SettingItem title="Clear on Exit" desc="Clears all browsing data when you leave mi: history and recent searches, open and recently closed tabs (pinned tabs stay), and cookies, site data and cache. Android rarely closes apps itself, so mi also clears at its next start if it was swiped away, and Exit and Clear in the tab menu closes it on demand." />
                  <SettingItem title="HTTPS Only" desc="On by default. Always use secure connections, local addresses included. If a site can't connect securely, mi warns you every time you go to it, with a way to continue over http for that visit (its links stay on http while you stay on the site in that tab)." />
                  <SettingItem title="Warn Every Time" desc="With HTTPS Only on: ask before every visit to an insecure site (on by default). Turn it off and continuing to a site lets it use http for the rest of the session." />
                  <SettingItem title="Allow Cookies" desc="Allow websites to store cookies on your device. Disabling this may break login functionality on many sites." />
                  <SettingItem title="Block Third-Party Cookies" desc="Stops sites embedded in a page from setting cookies that follow you across the web. Off by default, as in Chrome, because payment and sign-in windows (such as PayPal) rely on them." />
                  <SettingItem title="JavaScript" desc="Optionally disable JavaScript for maximum security or to bypass certain site restrictions." />
                  <SettingItem title="Pop-ups" desc="Pop-up windows open when you tap something (sign-in and payment windows stay linked to the page that opened them). Pop-ups without a tap are blocked; choose Always allow when asked to allow a site, and review or reset the list here." />
                  <SettingItem title="Not Secure Marker" desc="Pages without an encrypted connection (any plain http address) and sites you allowed past a certificate warning show a red Not secure marker in the search pill. Tap it for details, and don't enter passwords or payment details there. Sign-in prompts on such sites warn that the password would be sent unencrypted." />
                </SettingsSection>

                <SettingsSection title="Tabs" icon={<Layers size={20} />}>
                  <SettingItem title="On Startup" desc="Choose to start with a New Tab or Continue Session (restores your last browsing session)." />
                  <SettingItem title="Rest Unused Tabs" desc="On by default. Tabs you haven't opened in 10 minutes free their memory, saving battery, and reload where they were when you open them. The tab you're on, one playing media and a pop-up with its opener always stay." />
                  <SettingItem title="Group Tabs from Links" desc="On by default. A tab you open from a page's link joins that page's tab group, or the two start one named for its site." />
                  <SettingItem title="Profiles" desc="Off by default. On: profiles, each with its own tabs and history, switched from the Tab Switcher. Your profiles are listed under it: tap one to edit it, or + for a new one. Off, every tab and all history show together. Keeping a profile's sign-ins separate needs a recent Android System WebView." />
                  <SettingItem title="Site Icons" id="setting-site-icons-tabs" desc="Shows each website's icon on its tab card in the tab switcher. When off, a globe is shown." />
                </SettingsSection>

                <SettingsSection title="Gestures" icon={<Hand size={20} />}>
                  <SettingItem title="Long Press to Copy Link" desc="Hold the Pill to copy the page's link, without tracking. On by default." />
                  <SettingItem title="Swipe Left, Swipe Right, Swipe Up" desc="Which page each swipe on the tab switcher's Pill opens: Library, Settings or History. The buttons and the side each page slides in from follow your choice." />
                  <SettingItem title="Long Press to Search" desc="Hold the tab switcher's Pill to search your open tabs. On by default." />
                  <SettingItem title="Pull to Refresh" desc="Pull down at the very top of a page to reload it. It never triggers while you're scrolled down, even if you scroll back to the top in the same pull." />
                </SettingsSection>

                <SettingsSection title="Library" icon={<Bookmark size={20} />}>
                  <SettingItem title="Site Icons" id="setting-site-icons-bookmarks" desc="Shows each website's icon in your bookmarks. When off, a letter avatar is shown." />
                  <SettingItem title="Import Bookmarks" desc="Bring bookmarks from Chrome, Firefox, Edge, Safari or a mi export: choose the browser's exported bookmarks file (.html), or paste its contents. Folders are kept, pages you already have are skipped, and another browser's bookmarks go into a dated Imported folder. Chrome's file can also come from Google Takeout." />
                  <SettingItem title="Export Bookmarks" desc="Save all your bookmarks to a standard bookmarks file that mi and any other browser can import." />
                  <SettingItem title="Download Manager" desc="mi (its own downloader: pause, resume, notifications and the Downloads list) or Android's download manager (saves to Downloads; not listed in mi). Incognito tabs always use mi." />
                  <SettingItem title="Location" desc="Where mi saves downloads: the Downloads folder, or any folder you choose." />
                  <SettingItem title="Ask Every Time" desc="Choose the folder and file name for each download in Android's save dialog." />
                  <SettingItem title="Confirm Downloads" desc="Ask before a download starts. On by default." />
                  <SettingItem title="Background Checks" desc="Check followed prices while mi is closed, about every hour (each product at its own interval, only with a network and the battery not low). On by default." />
                  <SettingItem title="Wi-Fi Only" desc="Background checks wait for Wi-Fi, so they don't use mobile data. Check Now still works anywhere." />
                  <SettingItem title="Notifications" id="setting-notifications-following" desc="Turns every Following notification on or off (turning it on asks for Android's permission if mi doesn't have it). Each product still chooses its own: right away, in a daily summary, or never." />
                </SettingsSection>

                <SettingsSection title="History" icon={<History size={20} />}>
                  <SettingItem title="Group History By" desc="Organize your history by Time (Today, Yesterday) or by Site (hostname)." />
                  <SettingItem title="Clear History" desc="Wipe your browsing history for the last hour, 24 hours, 7 days, 4 weeks, or all time." />
                  <SettingItem title="Keep Recently Closed" desc="How long tabs you close stay in History's Recently Closed: Session (the default, until mi closes), 1 Day or 1 Week. Longer than a session, they're saved on your device so they outlast a restart. Incognito tabs are never kept." />
                  <SettingItem title="Clear Recently Closed Tabs" desc="Forget the tabs you've closed." />
                </SettingsSection>

                <SettingsSection title="Backup & Reset" icon={<RefreshCw size={20} />}>
                  <SettingItem title="Back Up" desc="Save all your settings, bookmarks and followed prices to a file, for safekeeping or a new phone. History, downloads and other browsing data are left out." />
                  <SettingItem title="Restore" desc="Bring back everything from a backup file: its settings replace yours (this phone keeps its own download folder), and bookmarks and followed prices you don't already have are added." />
                  <SettingItem title="Reset All Settings" desc="Revert every setting to factory defaults without affecting your bookmarks, history, or tabs." />
                  <SettingItem title="Wipe All Data" desc="Permanently delete all app data including bookmarks, history, tabs, followed prices, downloads and settings, then restart mi." />
                </SettingsSection>

                <SettingsSection title="About" icon={<BookOpen size={20} />}>
                  <SettingItem title="Check for Updates" desc="Look for a new release now. mi also checks on its own, at most twice a day. After an update, What's New shows that version's release notes once." />
                  <SettingItem title="Default Browser" desc="Make mi. the app that opens links on your phone." />
                  <SettingItem title="Gesture Walkthrough" desc="A hands-on tour of every gesture, shown once on first launch or after updating: the Pill, your tabs, the Library, Settings and History. Open it here to go through it again." />
                  <SettingItem title="Report a Bug" desc="Opens a new bug report on mi's GitHub page, with your mi version filled in." />
                  <SettingItem title="Suggest a Feature" desc="Opens a new feature request on mi's GitHub page, with your mi version filled in." />
                  <SettingItem title="Request Extension Support" desc="Ask for an extension to work in mi: opens a request on GitHub with your mi and Android System WebView versions filled in, labelled so it's easy to find." />
                  <SettingItem title="More mi. Apps" desc="The other mi. apps (Notes, Budget, Edits, Sports and Weather), tucked under one row. Tap one to open it if you have it, or to see its website." />
                  <SettingItem title="Source Code" desc="mi is open source: its code is on GitHub." />
                  <SettingItem title="Privacy Policy" desc="mi collects no personal data. The full policy is on this site." />
                  <SettingItem title="Credits" desc="The open-source projects mi is built with, and their licenses." />
                  <SettingItem title="Filter List Credits" desc="The filter lists mi blocks with (EasyList, EasyPrivacy, the EasyList Cookie List, StevenBlack hosts) and their licenses." />
                </SettingsSection>
              </div>
            </motion.div>

            {/* 8. Power Tools - IMAGE LEFT */}
            <motion.div id="power-tools" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-primary-container text-on-primary-container">
                  <QrCode size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Power Tools</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                  <DeviceMockup src="/images/power_tools.jpg" color="cyan" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Tap ⋮ in the Tab Switcher for the centered tab's actions, modes and tools.
                  </p>
                  <div className="grid grid-cols-1 gap-4 inline-block text-left w-full">
                    <div id="pin-tabs">
                      <GuideItem icon={<Pin size={18} />} title="Favorite, Bookmark &amp; Pin" desc="The row at the top: add the page to your favorites (on the home page), save it to a bookmark folder, or pin the tab so it can't be swiped away or closed by Close all tabs." />
                    </div>
                    <div id="quick-bookmark">
                      <GuideItem icon={<Bookmark size={18} />} title="Share &amp; Copy" desc="Share sends the page with your device's share sheet; the copy icon next to the tab's name copies its link." />
                    <div id="site-settings">
                      <GuideItem icon={<ShieldCheck size={18} />} title="Site Settings" desc="Tap the site at the top of the tab menu: its connection, what it may use this session (camera, microphone, location), pop-ups, ad blocking (with how many ads and trackers were blocked on the page; the Block ads here tile shows it too), and Clear Site Data for just that site." />
                    </div>
                    </div>
                    <div id="find-in-page">
                      <GuideItem icon={<Search size={18} />} title="Find in Page" desc="The Pill turns into a find bar: type to highlight every match, and step through them with the arrows while the count (2/14) follows along. Back or ✕ turns it back into the Pill." />
                    </div>
                    <div id="qr-toolbox">
                      <GuideItem icon={<QrCode size={18} />} title="QR Toolbox" desc="Scan physical codes, upload images from your gallery to scan, or generate a code for the current URL." />
                    </div>
                    <GuideItem icon={<BookOpen size={18} />} title="Reader Mode" desc="Removes ads and clutter, leaving only the text and essential images for a clean reading experience." />
                    <GuideItem icon={<Monitor size={18} />} title="Desktop Mode" desc="Requests the desktop version of the current site for full functionality." />
                    <GuideItem icon={<Share2 size={18} />} title="Share &amp; Print" desc="Native integration with your device&apos;s share sheet and Android&apos;s print service (print or save as PDF)." />
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 9. Extensions */}
            <motion.div id="extensions" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-tertiary-container text-on-tertiary-container">
                  <Puzzle size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Extensions</h2>
              </div>
              <div className="space-y-6">
                <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium text-center sm:text-left">
                  mi runs extensions made for Firefox on Android: dark modes, ad blockers, price trackers, YouTube helpers and more. Find them in the Library under Extensions.
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 text-left">
                  <GuideItem icon={<Puzzle size={18} />} title="Get Extensions" desc="Library › Extensions › Get Extensions: browse by category or search Firefox Add-ons from the Pill. Only extensions made for phones are offered; tap one, check what it can do, and Add. On Firefox Add-ons' own site, an 'Add to mi.' bar offers the page's extension too." />
                  <div id="tested-in-mi">
                    <GuideItem icon={<Star size={18} />} title="Tested in mi" desc="Extensions checked by hand in mi, at the top of Get Extensions: Dark Reader, uBlock Origin, uBlock Origin Lite, Keepa, SponsorBlock, Privacy Badger, ClearURLs and LocalCDN. If your phone's Android System WebView is too old for one, mi says so and how to update it. Anything testing found one can't do in mi is listed under Known Limitations, above any other warnings, before you add it and on its page: uBlock Origin starts filtering a little while after mi opens (mi's own ad blocking covers that time), and on a heavy page lets through a request it hasn't answered within 1.5 seconds; uBlock Origin Lite, just added, hides page elements once it has finished setting up; SponsorBlock needs mi to turn off one of YouTube's script protections (Trusted Types) on YouTube; LocalCDN can't use its Firefox-only HTML filter, and right after mi opens a page's first libraries can load from their CDN before it's ready." />
                  </div>
                  <GuideItem icon={<ShieldCheck size={18} />} title="How Well It Works" desc="Before you add an extension, mi reads its code and rates it: Works, Might Have Problems (with what may not work) or Not Recommended. Extensions made only for desktop, and password managers, can't be added: mi says why." />
                  <GuideItem icon={<Layers size={18} />} title="In the Tab Menu" desc="An extension with a button gets a tile under Extensions in the tab menu, like the tab's own tiles: its name, and under it its badge (a count of blocked ads, for example). Tap it for its popup; hold and drag to rearrange; hold and let go for its page in the Library." />
                  <GuideItem icon={<Settings2 size={18} />} title="Its Page" desc="Tap an extension in the Library for its page: turn it on or off, Show in Tab Menu, Allow in Incognito (off by default), the sites it's turned off on, its permissions, Options, Clear Data and Remove." />
                  <GuideItem icon={<Trash2 size={18} />} title="Remove" desc="Remove from its page, or swipe it away in the Library. Undo puts it back with its settings." />
                  <GuideItem icon={<EyeOff size={18} />} title="With mi's Ad Blocking" desc="mi's own ad blocking keeps running beside an ad-blocking extension, so both block. If a site breaks, turn one of them off for that site." />
                  <GuideItem icon={<Search size={18} />} title="Missing One?" desc="Settings › About › Request Extension Support asks for an extension to be made to work in mi." />
                </div>
              </div>
            </motion.div>

            {/* 10. Privacy Commitment - IMAGE RIGHT */}
            <motion.div id="privacy" {...fadeInUp} className="bg-primary-container rounded-[2rem] p-12 text-on-primary-container relative overflow-hidden">
              <div className="absolute top-0 right-0 p-12 opacity-10">
                <ShieldCheck size={200} />
              </div>
              <div className="relative z-10 max-w-2xl">
                <h2 className="text-3xl font-extrabold mb-6 tracking-tight">Privacy by Design</h2>
                <p className="text-xl text-on-primary-container font-medium leading-relaxed mb-8">
                  mi. Browser is not just minimal in design, but minimal in data collection. Your browsing remains yours.
                </p>
                <div className="grid sm:grid-cols-2 gap-8">
                  <div className="space-y-2">
                    <div className="flex items-center gap-2 font-bold">
                      <Lock size={18} /> No Tracking
                    </div>
                    <p className="text-sm text-on-primary-container/80">We do not track your history, cookies, or search patterns. No data is sent to our servers.</p>
                  </div>
                  <div className="space-y-2">
                    <div className="flex items-center gap-2 font-bold">
                      <RefreshCw size={18} /> Local Storage
                    </div>
                    <p className="text-sm text-on-primary-container/80">Bookmarks, history, and tabs are stored only in the app&apos;s private storage on your device. Sites you allow past certificate warnings, and camera, microphone or location permissions you grant, last only until you close the app.</p>
                  </div>
                </div>
              </div>
            </motion.div>
          </section>
        </div>
      </div>

      <Footer />
    </main>
  );
}

function GuideItem({ icon, title, desc }: { icon: React.ReactNode, title: string, desc: string }) {
  return (
    <div className="flex gap-4">
      <div className="shrink-0 w-10 h-10 rounded-xl bg-surface-container-high border border-outline-variant flex items-center justify-center text-on-surface-variant/80">
        {icon}
      </div>
      <div>
        <h4 className="font-bold text-lg leading-tight mb-1">{title}</h4>
        <p className="text-on-surface-variant font-medium leading-relaxed">{desc}</p>
      </div>
    </div>
  );
}

function SettingsSection({ title, icon, children }: { title: string, icon: React.ReactNode, children: React.ReactNode }) {
  const id = title.toLowerCase().replace(/\s+/g, '-');
  return (
    <div id={id} className="space-y-6 scroll-mt-28">
      <div className="flex items-center gap-2 pb-2 border-b border-outline-variant">
        <div className="text-primary">{icon}</div>
        <h3 className="font-bold text-xl tracking-tight uppercase text-xs tracking-[0.2em] opacity-50">{title}</h3>
      </div>
      <div className="grid sm:grid-cols-2 gap-6">
        {children}
      </div>
    </div>
  );
}

function SettingItem({ title, desc, id: given }: { title: string, desc: string, id?: string }) {
  // its link (#setting-…) from its name; settings sharing a name (Site Icons, for bookmarks and for tabs) pass their own
  const id = given ?? `setting-${title.toLowerCase().replace(/\s+/g, '-')}`;
  return (
    <div id={id} className="p-6 rounded-2xl bg-surface-container-high border border-outline-variant shadow-sm hover:border-primary/40 transition-colors scroll-mt-28">
      <h4 className="font-bold text-sm mb-2">{title}</h4>
      <p className="text-xs text-on-surface-variant leading-relaxed font-medium">{desc}</p>
    </div>
  );
}

