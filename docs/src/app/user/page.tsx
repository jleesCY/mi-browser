"use client";

import { motion } from "framer-motion";
import { Navbar } from "@/components/navbar";
import { Footer } from "@/components/footer";
import { 
  Navigation, 
  MousePointer2, 
  Layers, 
  Layout,
  Settings2, 
  Search, 
  Bookmark, 
  History, 
  QrCode, 
  Zap,
  ShieldCheck,
  Palette,
  Maximize,
  Lock,
  RefreshCw,
  Trash2,
  Monitor,
  BookOpen,
  Share2,
  ChevronRight,
  FolderHeart,
  Clock,
  GripHorizontal,
  Pencil,
  Home
} from "lucide-react";
import { useHighlight } from "@/hooks/useHighlight";

const getAssetPath = (path: string) => {
  const isProd = process.env.NODE_ENV === 'production';
  const repoName = process.env.NEXT_PUBLIC_REPO_NAME || "mi-browser";
  const basePath = isProd ? `/${repoName}` : "";
  return `${basePath}${path}`;
};

export default function UserDocs() {
  useHighlight();
  const fadeInUp = {
    initial: { opacity: 0, y: 20 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true },
    transition: { duration: 0.6 }
  };

  return (
    <main className="min-h-screen bg-[#050505] text-[#ededed] transition-colors duration-300 overflow-x-hidden">
      <Navbar />

      <div className="pt-24 sm:pt-32 pb-20 px-6">
        <div className="max-w-4xl mx-auto">
          <motion.div {...fadeInUp} className="mb-16 text-center sm:text-left">
            <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tighter mb-6">
              The Complete <span className="text-blue-600 italic">User Guide</span>
            </h1>
            <p className="text-lg sm:text-xl text-white/60 leading-relaxed font-medium">
              A comprehensive deep-dive into every feature, setting, and interaction within mi. Browser.
            </p>
          </motion.div>

          <section className="space-y-24 sm:space-y-32">
            
            {/* 1. Pill Interactions - IMAGE RIGHT */}
            <motion.div id="the-pill" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-blue-500/10 text-blue-600">
                  <Navigation size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Pill Interactions</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-base sm:text-lg text-white/60 leading-relaxed font-medium">
                    mi. Browser is designed for one-handed use. Everything centers around &quot;The Pill&quot; at the bottom of your screen.
                  </p>
                  <div className="space-y-6 inline-block text-left w-full">
                    <div className="space-y-4">
                      <GuideItem icon={<MousePointer2 size={18}/>} title="Tap the Pill" desc="Focuses the address bar. Type a URL or a search query to navigate instantly." />
                      <GuideItem icon={<ChevronRight size={18} className="-rotate-90"/>} title="Swipe Up" desc="Reveals the Dashboard. This is your hub for History, Bookmarks, and System Settings." />
                      <GuideItem icon={<ChevronRight size={18} className="rotate-90"/>} title="Swipe Down" desc="Hides the Pill entirely for an immersive full-screen experience. Tap the floating recall button to bring it back." />
                      <div id="search-engine-only">
                        <GuideItem icon={<Search size={18}/>} title="Search Engine Only" desc="Tap the search engine icon inside the address bar to force search mode. This bypasses URL parsing and sends your query directly to your search engine." />
                      </div>
                    </div>
                    
                    <div id="gestures" className="pt-4 space-y-4 border-t border-white/5">
                      <h4 className="text-xs font-bold uppercase tracking-[0.2em] text-blue-500 mb-4">Navigation Gestures</h4>
                      <GuideItem icon={<ChevronRight size={18}/>} title="Swipe Right" desc="Go back to the previous page in your history." />
                      <GuideItem icon={<ChevronRight size={18} className="rotate-180"/>} title="Swipe Left" desc="Go forward to the next page if you've navigated back." />
                    </div>

                    <div className="p-4 rounded-xl border border-white/5 bg-white/5 text-[10px] sm:text-xs text-white/50">
                      <p><strong>Pro Tip:</strong> These gestures work directly on the Pill area, allowing for effortless one-handed browsing.</p>
                    </div>
                  </div>
                </div>
                <DeviceMockup src="/images/homepage.jpg" color="blue" />
              </div>
            </motion.div>

            {/* 2. The Dashboard - IMAGE LEFT */}
            <motion.div id="dashboard" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-orange-500/10 text-orange-600">
                  <Layout size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">The Dashboard</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                   <DeviceMockup src="/images/dashboard.jpg" color="orange" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-white/60 leading-relaxed font-medium">
                    Swipe up on the Pill from any page to reveal your navigation hub. The Dashboard gives you instant access to your most important data.
                  </p>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-left">
                    <GuideItem icon={<Layers size={18}/>} title="Tabs" desc="Switch between your open browsing sessions." />
                    <GuideItem icon={<Bookmark size={18}/>} title="Bookmarks" desc="Access your saved favorite sites and folders." />
                    <GuideItem icon={<History size={18}/>} title="History" desc="View and search your recently visited pages." />
                    <GuideItem icon={<Settings2 size={18}/>} title="Settings" desc="Configure every detail of your browsing experience." />
                  </div>
                  <div className="p-4 rounded-xl border border-white/5 bg-white/5 text-[10px] sm:text-xs text-white/50">
                    <p><strong>Secondary Menu:</strong> Tap the &quot;Menu&quot; button on the far right of the Dashboard to access Power Tools like Reader Mode, QR Tools, and Desktop Site toggles.</p>
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 3. Expanded Search Drawer - IMAGE RIGHT */}
            <motion.div id="recent-history" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-blue-500/10 text-blue-600">
                  <Maximize size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Expanded Search</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-base sm:text-lg text-white/60 leading-relaxed font-medium">
                    Access your history and favorite sites without leaving the search experience. When the search pill is focused, an expanded interaction area appears.
                  </p>
                  <div className="space-y-4 inline-block text-left w-full">
                    <GuideItem icon={<Clock size={18}/>} title="Recent History" desc="Drag the handle above the Pill upwards to reveal a quick-access list of your most recent visits." />
                    <GuideItem icon={<FolderHeart size={18}/>} title="Favorites Bar" desc="A persistent row of your top 5 pinned sites sits at the bottom of the drawer for instant navigation." />
                    <GuideItem icon={<Maximize size={18}/>} title="Adding Favorites" desc="Tap the '+' icon in the Favorites Bar to instantly pin the current page to your quick-access list." />
                    <GuideItem icon={<GripHorizontal size={18}/>} title="Rearrange Favorites" desc="Touch and drag any favorite icon to reorder them instantly." />
                    <GuideItem icon={<Trash2 size={18}/>} title="Drag to Delete" desc="Drag a favorite icon upwards out of the bar area to remove it." />
                  </div>
                  <div className="p-4 rounded-xl border border-white/5 bg-white/5 text-[10px] sm:text-xs text-white/50">
                    <p><strong>Configurable:</strong> Use settings to automatically expand history or keep the Favorites Bar visible every time you focus the Pill.</p>
                  </div>
                </div>
                <DeviceMockup src="/images/recent_history.jpg" color="blue" />
              </div>
            </motion.div>

            {/* 4. Visual Tab Management - IMAGE LEFT */}
            <motion.div id="tabs" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-purple-500/10 text-purple-600">
                  <Layers size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Tab Management</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                   <DeviceMockup src="/images/tab_grid.jpg" color="purple" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-white/60 leading-relaxed font-medium">
                    Manage multiple sessions with ease. mi. uses high-fidelity snapshots so you never lose your place.
                  </p>
                  <div className="space-y-4 inline-block text-left">
                    <GuideItem icon={<Layers size={18}/>} title="Grid/Card View" desc="Switch between dense rows or large visual cards in Settings." />
                    <GuideItem icon={<MousePointer2 size={18}/>} title="Reordering" desc="In the tab view, long-press a tab and drag it to a new position to reorder your workspace." />
                    <GuideItem icon={<Trash2 size={18}/>} title="Closing Tabs" desc="Swipe left on a tab row or tap the 'X' on a card to close it. Use 'Clear All' to reset completely." />
                    <div id="tab-editing">
                      <GuideItem icon={<Pencil size={18}/>} title="Tab Editing" desc="Tap the pencil icon on any tab to change its display name for better organization." />
                    </div>
                    <GuideItem icon={<Zap size={18}/>} title="Visual Snapshots" desc="Real-time previews of your pages help you identify the right tab in seconds." />
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 5. Bookmark Management - IMAGE RIGHT */}
            <motion.div id="bookmarks" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-pink-500/10 text-pink-600">
                  <FolderHeart size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Bookmark Management</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-base sm:text-lg text-white/60 leading-relaxed font-medium">
                    Keep your favorite corners of the web organized. mi. Browser provides a flexible bookmarking system with folder support.
                  </p>
                  <div className="space-y-4 inline-block text-left w-full">
                    <GuideItem icon={<MousePointer2 size={18}/>} title="Quick Save" desc="Open the Dashboard Menu and select 'Bookmark' to instantly save the current page to your library." />
                    <div id="bookmark-swiping">
                      <GuideItem icon={<RefreshCw size={18}/>} title="Swipe to Edit" desc="Swipe left on any bookmark or folder to reveal Edit and Delete actions. You can change names, URLs, or move items between folders." />
                    </div>
                    <GuideItem icon={<FolderHeart size={18}/>} title="Nested Folders" desc="Create and manage folders to group related bookmarks together for easier access." />
                    <GuideItem icon={<Search size={18}/>} title="Instant Search" desc="Quickly find any saved bookmark by typing in the search bar at the top of the Bookmarks view." />
                  </div>
                </div>
                <DeviceMockup src="/images/bookmarks.jpg" color="pink" />
              </div>
            </motion.div>

            {/* 6. History Management - IMAGE LEFT */}
            <motion.div id="history" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-green-500/10 text-green-600">
                  <Clock size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">History Management</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                   <DeviceMockup src="/images/history.jpg" color="green" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-white/60 leading-relaxed font-medium">
                    Never lose a page you&apos;ve visited. mi. keeps a local-only record of your browsing activity that stays entirely on your device.
                  </p>
                  <div className="space-y-4 inline-block text-left">
                    <GuideItem icon={<Search size={18}/>} title="Search History" desc="Filter through your past visits using the search bar within the History view." />
                    <GuideItem icon={<Trash2 size={18}/>} title="Partial Deletion" desc="Swipe left on any history item to remove it individually without affecting the rest of your data." />
                    <GuideItem icon={<RefreshCw size={18}/>} title="Clear Ranges" desc="Wipe your history for the last hour, day, week, or all time via the trash icon in History." />
                    <GuideItem icon={<ShieldCheck size={18}/>} title="Local-Only" desc="Your history is never uploaded to any cloud. It is stored securely on your local file system." />
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 7. Detailed Settings Reference - IMAGE RIGHT */}
            <motion.div id="customization" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-orange-500/10 text-orange-600">
                  <Settings2 size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Settings</h2>
              </div>
              
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-lg text-white/60 leading-relaxed font-medium">
                    Every aspect of mi. Browser can be customized. Here is a breakdown of every available setting:
                  </p>
                  <div className="p-4 rounded-xl border border-white/5 bg-white/5 text-[10px] sm:text-xs text-white/50">
                    <p><strong>Pro Tip:</strong> Most settings apply in real-time. You can change your accent color or UI spacing and see the results instantly without restarting the app.</p>
                  </div>
                </div>
                <DeviceMockup src="/images/settings.jpg" color="orange" />
              </div>

              <div className="grid gap-12">
                {/* Colors */}
                <SettingsSection title="Colors" icon={<Palette size={20} />}>
                  <SettingItem title="Theme" desc="Choose between Light, Dark, or Adaptive. Adaptive mode generates a custom theme based on your Accent Color." />
                  <SettingItem title="Accent Color" desc="Pick from 18 hand-selected colors that define the app's UI elements, buttons, and highlights." />
                </SettingsSection>

                {/* Interface */}
                <SettingsSection title="Interface" icon={<Maximize size={20} />}>
                  <SettingItem title="Font Size" desc="Adjust the global font scale from 80% to 120% to suit your readability needs." />
                  <SettingItem title="Corners" desc="Choose between Square (0px), Semi-Round (10px), or Round (22px) for all UI components and cards." />
                  <SettingItem title="Spacing" desc="Select Compact, Normal, or Airy to adjust the padding and density of the interface." />
                  <SettingItem title="Status Bar" desc="Toggles the visibility of your device's system status bar (clock, battery, etc.) for a more immersive look." />
                  <SettingItem title="Expand Menus" desc="When enabled, menus (History, Tabs, Settings) will open to full-screen height immediately." />
                </SettingsSection>

                {/* Pill */}
                <SettingsSection title="Pill" icon={<Navigation size={20} />}>
                  <SettingItem title="Size" desc="Choose between Thin (60px), Normal (70px), or Tall (80px) for optimal thumb reach." />
                  <SettingItem title="Loading Bar" desc="Choose the progress bar style: Standard (Left-to-Right), Center Out, or Hidden." />
                  <SettingItem title="Pin Favorites" desc="Keeps the Favorites Bar visible at the bottom of the search drawer whenever the Pill is focused." />
                  <SettingItem title="Expand Searches" desc="Toggles whether your most recent searches are automatically expanded when opening the Pill." />
                  <SettingItem title="Reorder Icons" desc="Customise the Dashboard menu bar by dragging icons into your preferred order for quicker access to Tabs, Bookmarks, History, or Settings." />
                </SettingsSection>

                {/* Tabs */}
                <SettingsSection title="Tabs" icon={<Layers size={20} />}>
                  <SettingItem title="Style" desc="Toggle between Rows (dense list) or Cards (visual grid)." />
                  <SettingItem title="Site Logo" desc="Toggles the visibility of website icons (favicons) in the tab switcher. When disabled, a letter avatar is shown." />
                  <SettingItem title="Preview Content" desc="Enables real-time snapshots of your pages when using Card view." />
                  <SettingItem title="Background Refresh" desc="When enabled, all tabs render on app startup instead of when opened. This allows for instant switching but uses more battery." />
                </SettingsSection>

                {/* History */}
                <SettingsSection title="History" icon={<History size={20} />}>
                  <SettingItem title="Load Count" desc="The number of history items rendered at a time (10, 25, 50, or 100). Rendering more items may affect performance." />
                  <SettingItem title="Group By" desc="Organize your history by Time (Today, Yesterday) or by Site (hostname)." />
                  <SettingItem title="Clear History" desc="Wipe your browsing data for the last 24 hours, 7 days, 4 weeks, or all time." />
                </SettingsSection>

                {/* Bookmarks */}
                <SettingsSection title="Bookmarks" icon={<Bookmark size={20} />}>
                  <SettingItem title="Site Logo" desc="Toggles the visibility of website icons (favicons) in the bookmarks list. When disabled, a letter avatar is shown." />
                </SettingsSection>

                {/* Browsing */}
                <SettingsSection title="Browsing" icon={<Lock size={20} />}>
                  <SettingItem title="Search Engine" desc="Support for Google, Bing, DuckDuckGo, Brave, Ecosia, and Yahoo." />
                  <SettingItem title="On Startup" desc="Choose to start with a New Tab or Continue Session (starts from your last tab)." />
                  <SettingItem title="Enable JavaScript" desc="Optionally disable JavaScript for maximum security or to bypass certain site restrictions." />
                  <SettingItem title="Enable Cookies" desc="Allow websites to store cookies on your device. Disabling this may break login functionality on many sites." />
                  <SettingItem title="HTTPS Only" desc="Forces the browser to only connect to websites via secure encrypted connections." />
                </SettingsSection>
              </div>
            </motion.div>

            {/* 8. Power Tools - IMAGE LEFT */}
            <motion.div id="power-tools" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-600">
                  <QrCode size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Power Tools</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                   <DeviceMockup src="/images/power_tools.jpg" color="cyan" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-white/60 leading-relaxed font-medium">
                    Tap the Menu button on the Dashboard to access advanced utility tools for any page.
                  </p>
                  <div className="grid grid-cols-1 gap-4 inline-block text-left w-full">
                    <div id="home-button">
                      <GuideItem icon={<Home size={18}/>} title="Home Button" desc="Instantly return to the minimalist home screen and reset your current session." />
                    </div>
                    <div id="quick-bookmark">
                      <GuideItem icon={<Bookmark size={18}/>} title="Quick Bookmark" desc="Save the current page to your bookmarks with one tap from the Power Tools menu." />
                    </div>
                    <div id="find-in-page">
                      <GuideItem icon={<Search size={18}/>} title="Find in Page" desc="Search for specific text within the current webpage. Highlighting and navigation between matches included." />
                    </div>
                    <div id="qr-toolbox">
                      <GuideItem icon={<QrCode size={18}/>} title="QR Toolbox" desc="Scan physical codes, upload images from your gallery to scan, or generate a code for the current URL." />
                    </div>
                    <GuideItem icon={<BookOpen size={18}/>} title="Reader Mode" desc="Removes ads and clutter, leaving only the text and essential images for a clean reading experience." />
                    <GuideItem icon={<Monitor size={18}/>} title="Desktop Mode" desc="Requests the desktop version of the current site for full functionality." />
                    <GuideItem icon={<Share2 size={18}/>} title="Share &amp; Print" desc="Native integration with your device&apos;s share sheet and wireless printing capabilities." />
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 9. Privacy Commitment - IMAGE RIGHT */}
            <motion.div id="privacy" {...fadeInUp} className="bg-blue-600 rounded-[2rem] p-12 text-white relative overflow-hidden">
               <div className="absolute top-0 right-0 p-12 opacity-10">
                 <ShieldCheck size={200} />
               </div>
               <div className="relative z-10 max-w-2xl">
                 <h2 className="text-3xl font-extrabold mb-6 tracking-tight">Privacy by Design</h2>
                 <p className="text-xl text-blue-50 font-medium leading-relaxed mb-8">
                   mi. Browser is not just minimal in design, but minimal in data collection. Your browsing remains yours.
                 </p>
                 <div className="grid sm:grid-cols-2 gap-8">
                   <div className="space-y-2">
                     <div className="flex items-center gap-2 font-bold">
                       <Lock size={18} /> No Tracking
                     </div>
                     <p className="text-sm text-blue-100/80">We do not track your history, cookies, or search patterns. No data is sent to our servers.</p>
                   </div>
                   <div className="space-y-2">
                     <div className="flex items-center gap-2 font-bold">
                       <RefreshCw size={18} /> Local Storage
                     </div>
                     <p className="text-sm text-blue-100/80">All bookmarks and history are stored locally on your device using industry-standard encryption.</p>
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
      <div className="shrink-0 w-10 h-10 rounded-xl bg-white/5 border border-white/5 flex items-center justify-center text-white/40">
        {icon}
      </div>
      <div>
        <h4 className="font-bold text-lg leading-tight mb-1">{title}</h4>
        <p className="text-white/50 font-medium leading-relaxed">{desc}</p>
      </div>
    </div>
  );
}

function SettingsSection({ title, icon, children }: { title: string, icon: React.ReactNode, children: React.ReactNode }) {
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 pb-2 border-b border-white/5">
        <div className="text-blue-400">{icon}</div>
        <h3 className="font-bold text-xl tracking-tight uppercase text-xs tracking-[0.2em] opacity-50">{title}</h3>
      </div>
      <div className="grid sm:grid-cols-2 gap-6">
        {children}
      </div>
    </div>
  );
}

function SettingItem({ title, desc }: { title: string, desc: string }) {
  return (
    <div className="p-6 rounded-2xl bg-white/5 border border-white/5 shadow-sm hover:border-blue-500/30 transition-colors">
      <h4 className="font-bold text-sm mb-2">{title}</h4>
      <p className="text-xs text-white/50 leading-relaxed font-medium">{desc}</p>
    </div>
  );
}

function DeviceMockup({ src, color }: { src: string, color?: string }) {
  const glowStyles: any = {
    blue: "bg-blue-500/20 shadow-blue-500/40",
    purple: "bg-purple-500/20 shadow-purple-500/40",
    pink: "bg-pink-500/20 shadow-pink-500/40",
    orange: "bg-orange-500/20 shadow-orange-500/40",
    cyan: "bg-cyan-500/20 shadow-cyan-500/40",
    green: "bg-green-500/20 shadow-green-500/40",
  };

  const glowClass = (color && glowStyles[color]) ? glowStyles[color].split(' ')[0] : 'bg-blue-500/10';

  return (
    <div className="relative group flex justify-center">
      <div className={`absolute -inset-10 ${glowClass} rounded-full blur-[80px] group-hover:opacity-100 transition-opacity opacity-50`} />
      <div className={`relative w-[220px] sm:w-[280px] bg-black rounded-[2rem] p-2 shadow-2xl border-[4px] border-[#1a1a1a] overflow-hidden transition-transform duration-500 group-hover:scale-105 group-hover:-rotate-1`}>
        <img 
          src={getAssetPath(src)} 
          alt="mi. App Screenshot" 
          className="w-full h-auto block rounded-[1.4rem]"
        />
      </div>
    </div>
  );
}