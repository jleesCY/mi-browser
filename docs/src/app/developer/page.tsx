"use client";

import { motion } from "framer-motion";
import { Navbar } from "@/components/navbar";
import { Footer } from "@/components/footer";
import { Terminal, Cpu, Layout, FolderTree, Info } from "lucide-react";
import { useHighlight } from "@/hooks/useHighlight";
import { getAssetPath } from "@/site";


const PROJECT_STRUCTURE = `
/
├── app/
│   └── src/main/
│       ├── java/com/jlees/mibrowser/
│       │   ├── MainActivity.kt      # Edge-to-edge host, intents, permissions
│       │   ├── BrowserViewModel.kt  # Holds the feature stores, coordinates across them
│       │   ├── tabs/                # Tab list & rules, tab switcher, previews
│       │   ├── library/             # History & bookmarks: stores, rules, screens
│       │   ├── search/              # Engines, ! shortcuts, suggestions
│       │   ├── adblock/             # Filter engine, content blocking, scriptlets, link cleaning
│       │   ├── extensions/          # Extension engine on WebView: APIs, pages, store, Tested in mi; quirks/
│       │   ├── incognito/           # WebView profiles, screen & keyboard protection
│       │   ├── media/               # Picture-in-picture, background playback
│       │   ├── answers/             # Inline answers: calculator, units, time
│       │   ├── follow/              # Following prices: store, rules, checks, sheets, widget
│       │   ├── downloads/           # mi's downloader, blob downloads, Downloads page
│       │   ├── home/ qr/ launcher/ update/   # update/: release checks, What's New
│       │   ├── data/ logic/         # Shared models, JSON storage, URL parsing
│       │   ├── web/                 # WebViewHost + one class per WebView job
│       │   └── ui/                  # App shell: Pill, sheets, dialogs, widgets; settings/ (one file per section)
│       ├── assets/                  # Filter lists, page scripts (js/, ext-*.js for extensions)
│       └── res/                     # Fonts, Material Symbols, launcher icons
├── docs/                    # This website (Next.js; colours from scripts/theme.mjs)
└── notes/                   # Plans: extensions, following, ...
`.trim();

export default function DeveloperDocs() {
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
              Developer <span className="text-primary italic">Guide</span>
            </h1>
            <p className="text-lg sm:text-xl text-on-surface-variant leading-relaxed font-medium">
              Technical architecture and development setup for the mi. Browser open-source project.
            </p>
          </motion.div>

                    <section className="space-y-20">
                      {/* UI Reference */}
                      <motion.div id="ui-reference" {...fadeInUp} className="space-y-8">
                        <div className="flex items-center justify-center sm:justify-start gap-3 mb-6">
                          <div className="p-2 rounded-xl bg-primary-container text-on-primary-container">
                            <Layout size={24} />
                          </div>
                          <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">UI Reference</h2>
                        </div>
                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
                          {[
                            { name: "Homepage", src: "/images/homepage.jpg" },
                            { name: "Focused Pill", src: "/images/focused_pill.jpg" },
                            { name: "Expanded Search", src: "/images/recent_history.jpg" },
                            { name: "Browsing", src: "/images/browsing.jpg" },
                            { name: "Tab Switcher", src: "/images/tab_switcher.jpg" },
                            { name: "Tab Zoom", src: "/images/tab_zoom.jpg" },
                            { name: "Bookmarks", src: "/images/bookmarks.jpg" },
                            { name: "History", src: "/images/history.jpg" },
                            { name: "Tab Menu", src: "/images/power_tools.jpg" },
                            { name: "Incognito", src: "/images/incognito.jpg" },
                            { name: "Settings", src: "/images/settings.jpg" },
                          ].map((img) => (
                            <div key={img.name} className="space-y-2 group">
                              <div className="bg-black rounded-2xl overflow-hidden border border-outline-variant shadow-lg group-hover:scale-[1.02] transition-transform">
                                <img src={getAssetPath(img.src)} alt={img.name} className="w-full h-auto block" />
                              </div>
                              <p className="text-center text-[10px] font-bold text-on-surface-variant/80 uppercase tracking-widest">{img.name}</p>
                            </div>
                          ))}
                        </div>
                      </motion.div>
          
                      {/* Tech Stack */}
                      <motion.div id="tech-stack" {...fadeInUp} className="space-y-8">
                        <div className="flex items-center gap-3 mb-6">
                          <div className="p-2 rounded-xl bg-tertiary-container text-on-tertiary-container">
                            <Cpu size={24} />
                          </div>
                          <h2 className="text-3xl font-bold tracking-tight">Tech Stack</h2>
                        </div>
                        <div className="grid sm:grid-cols-2 gap-6">
                          <TechCard 
                            title="Core Framework" 
                            items={["Kotlin", "Jetpack Compose", "Single Activity + ViewModel"]} 
                            color="blue"
                          />
                          <TechCard 
                            title="Design" 
                            items={["Material 3 Expressive", "MaterialKolor (seed color schemes)", "Google Sans Flex", "Material Symbols Rounded"]} 
                            color="purple"
                          />
                          <TechCard 
                            title="Engine & Storage" 
                            items={["Android System WebView", "mi's own WebExtensions engine (Firefox Add-ons for Android)", "kotlinx.serialization (JSON)", "EasyList, EasyPrivacy, uBlock Origin scriptlets, StevenBlack hosts"]} 
                            color="pink"
                          />
                          <TechCard 
                            title="System Integration" 
                            items={["CameraX + ZXing (QR)", "DownloadManager", "PrintManager", "App Shortcuts"]} 
                            color="cyan"
                          />
                        </div>
                      </motion.div>
          
                      {/* Project Structure */}
                      <motion.div id="project-structure" {...fadeInUp} className="space-y-8">
                        <div className="flex items-center gap-3 mb-6">
                          <div className="p-2 rounded-xl bg-secondary-container text-on-secondary-container">
                            <FolderTree size={24} />
                          </div>
                          <h2 className="text-3xl font-bold tracking-tight">Project Structure</h2>
                        </div>
                        <div className="bg-surface-container-high rounded-3xl p-8 border border-outline-variant font-mono text-sm leading-relaxed overflow-x-auto">
                          <pre>{PROJECT_STRUCTURE}</pre>
                        </div>
                      </motion.div>
          
                      {/* Development Setup */}
                      <motion.div id="setup" {...fadeInUp} className="space-y-8">              <div className="flex items-center gap-3 mb-6">
                <div className="p-2 rounded-xl bg-secondary-container text-on-secondary-container">
                  <Terminal size={24} />
                </div>
                <h2 className="text-3xl font-bold tracking-tight">Development Setup</h2>
              </div>
              
              <div className="space-y-12">
                <div className="space-y-4">
                  <h3 className="text-xl font-bold">Prerequisites</h3>
                  <ul className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {["Android Studio (latest stable)", "JDK 17+", "Android SDK 37", "A device or emulator (Android 8.0+)"].map(item => (
                      <li key={item} className="flex items-center gap-3 text-on-surface-variant font-medium">
                        <div className="w-1.5 h-1.5 rounded-full bg-secondary" />
                        {item}
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold">Installation</h3>
                  <CodeBlock 
                    command={`git clone https://github.com/mi-app-hub/mi-browser.git\ncd mi-browser`}
                  />
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold text-on-surface">Running the App</h3>
                  <CodeBlock 
                    command={`./gradlew installDebug        # build + install on the connected device\n./gradlew testDebugUnitTest    # run the logic tests`}
                  />
                  <div className="flex gap-4 text-sm font-medium text-on-surface-variant bg-surface-container-high p-4 rounded-2xl border border-outline-variant">
                    <Info size={18} className="shrink-0" />
                    <p>Or open the project folder in Android Studio and press Run. There is no JavaScript toolchain anymore: the app is a single Gradle module.</p>
                  </div>
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold text-on-surface">Core Concepts</h3>
                  <div className="grid sm:grid-cols-2 gap-4">
                    <div className="p-6 rounded-2xl bg-surface-container-high border border-outline-variant">
                      <h4 className="font-bold text-sm mb-2">State Management</h4>
                      <p className="text-xs text-on-surface-variant leading-relaxed">Each feature keeps its state in its own store (TabsStore, HistoryStore, BookmarksStore), which saves itself as JSON in app-private storage; BrowserViewModel holds the stores and coordinates between them. Decisions are pure, unit-tested functions. WebViews live outside Compose in WebViewHost, one per tab.</p>
                    </div>
                    <div className="p-6 rounded-2xl bg-surface-container-high border border-outline-variant sm:col-span-2">
                      <h4 className="font-bold text-sm mb-2">Extensions</h4>
                      <p className="text-xs text-on-surface-variant leading-relaxed">mi implements the WebExtensions APIs itself on the system WebView (extensions/): backgrounds, popups and options run in WebViews of their own, content scripts in each tab through a private message channel, and webRequest and declarativeNetRequest in mi&apos;s request handling. Only extensions whose Firefox Add-ons listing names Android are offered or added. Code for one particular extension lives in extensions/quirks/, never in the engine. The hand-tested list is VerifiedExtensions.kt.</p>
                    </div>
                    <div className="p-6 rounded-2xl bg-surface-container-high border border-outline-variant">
                      <h4 className="font-bold text-sm mb-2">Gesture Logic</h4>
                      <p className="text-xs text-on-surface-variant leading-relaxed">The Pill, sheets, and switcher share one pan detector that tracks the finger in screen coordinates and always settles on release, animated with Material 3 Expressive springs.</p>
                    </div>
                  </div>
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold text-on-surface">Building</h3>
                  <p className="text-sm text-on-surface-variant">Build a minified release APK with Gradle:</p>
                  <CodeBlock 
                    command={`./gradlew assembleRelease\n# → app/build/outputs/apk/release/`}
                  />
                  <div className="p-4 rounded-xl border border-primary/20 bg-primary/5 text-xs text-primary/80">
                    <p><strong>Signing:</strong> create a <code className="bg-surface-container-highest px-1 rounded">keystore.properties</code> file in the project root (it is git-ignored) with <code className="bg-surface-container-highest px-1 rounded">storeFile</code>, <code className="bg-surface-container-highest px-1 rounded">storePassword</code>, <code className="bg-surface-container-highest px-1 rounded">keyAlias</code> and <code className="bg-surface-container-highest px-1 rounded">keyPassword</code>. Without it, the release APK is built unsigned.</p>
                  </div>
                  <p className="text-sm text-on-surface-variant"><strong>Versioning:</strong> MAJOR.MINOR.PATCH: major for ground-up changes (1.0 was the native rewrite; 2.0 the mi. family rebrand and extensions), minor for significant new features, patch for fixes and smaller improvements. The 0.x releases were built with React Native; their source is on the <a className="underline" href="https://github.com/mi-app-hub/mi-browser/tree/legacy">legacy</a> branch.</p>
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

function TechCard({ title, items, color }: { title: string, items: string[], color: string }) {
  const colors: any = {
    blue: "bg-primary",
    purple: "bg-tertiary",
    pink: "bg-tertiary",
    cyan: "bg-secondary",
  };

  return (
    <div className="p-8 rounded-[2rem] bg-surface-container-high border border-outline-variant shadow-sm">
      <h4 className="font-bold text-lg mb-4">{title}</h4>
      <ul className="space-y-2">
        {items.map(item => (
          <li key={item} className="flex items-center gap-2 text-on-surface-variant font-medium">
            <div className={`w-1.5 h-1.5 rounded-full ${colors[color]}`} />
            {item}
          </li>
        ))}
      </ul>
    </div>
  );
}

function CodeBlock({ command }: { command: string }) {
  return (
    <div className="relative group">
      <div className="absolute -inset-0.5 bg-gradient-to-r from-primary to-tertiary rounded-2xl blur opacity-20 group-hover:opacity-40 transition duration-1000"></div>
      <div className="relative bg-inverse-surface rounded-2xl p-6 font-mono text-sm text-inverse-on-surface overflow-x-auto border border-outline-variant">
        <pre>{command}</pre>
      </div>
    </div>
  );
}
