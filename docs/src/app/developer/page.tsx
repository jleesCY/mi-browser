"use client";

import { motion } from "framer-motion";
import { Navbar } from "@/components/navbar";
import { Footer } from "@/components/footer";
import { Terminal, Cpu, Layout, FolderTree, Info } from "lucide-react";
import { useHighlight } from "@/hooks/useHighlight";

const getAssetPath = (path: string) => {
  const isProd = process.env.NODE_ENV === 'production';
  const repoName = process.env.NEXT_PUBLIC_REPO_NAME || "mi-browser";
  const basePath = isProd ? `/${repoName}` : "";
  return `${basePath}${path}`;
};

const PROJECT_STRUCTURE = `
/
├── app/
│   └── src/main/
│       ├── java/com/jlees/mibrowser/
│       │   ├── MainActivity.kt      # Edge-to-edge host, intents, permissions
│       │   ├── BrowserViewModel.kt  # All app state + actions
│       │   ├── data/                # Models, JSON storage, .midata, updates, blocklist
│       │   ├── logic/               # Pure logic: URL parsing, tab history, bookmarks (unit tested)
│       │   ├── web/                 # WebViewHost: one WebView per tab, clients, downloads
│       │   └── ui/                  # Compose UI: pill, sheets, tabs, history, bookmarks,
│       │                            #   settings, home, QR, shared M3 widgets
│       └── res/                     # Fonts, Material Symbols, launcher icons
├── docs/                    # This website (Next.js)
└── notes/                   # Port plan & project status
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
    <main className="min-h-screen bg-[#fafafa] dark:bg-[#050505] text-[#171717] dark:text-[#ededed] transition-colors duration-300 overflow-x-hidden">
      <Navbar />

      <div className="pt-24 sm:pt-32 pb-20 px-6">
        <div className="max-w-4xl mx-auto">
          <motion.div {...fadeInUp} className="mb-16 text-center sm:text-left">
            <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tighter mb-6">
              Developer <span className="text-blue-600 italic">Guide</span>
            </h1>
            <p className="text-lg sm:text-xl text-white/60 leading-relaxed font-medium">
              Technical architecture and development setup for the mi. Browser open-source project.
            </p>
          </motion.div>

                    <section className="space-y-20">
                      {/* UI Reference */}
                      <motion.div id="ui-reference" {...fadeInUp} className="space-y-8">
                        <div className="flex items-center justify-center sm:justify-start gap-3 mb-6">
                          <div className="p-2 rounded-xl bg-blue-500/10 text-blue-600">
                            <Layout size={24} />
                          </div>
                          <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">UI Reference</h2>
                        </div>
                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
                          {[
                            { name: "Homepage", src: "/images/homepage.jpg" },
                            { name: "Focused Pill", src: "/images/focused_pill.jpg" },
                            { name: "Expanded Search", src: "/images/recent_history.jpg" },
                            { name: "Dashboard", src: "/images/dashboard.jpg" },
                            { name: "Tabs Grid", src: "/images/tab_grid.jpg" },
                            { name: "Tabs List", src: "/images/tab_rows.jpg" },
                            { name: "Bookmarks", src: "/images/bookmarks.jpg" },
                            { name: "History", src: "/images/history.jpg" },
                            { name: "Power Tools", src: "/images/power_tools.jpg" },
                            { name: "Settings", src: "/images/settings.jpg" },
                          ].map((img) => (
                            <div key={img.name} className="space-y-2 group">
                              <div className="bg-black rounded-2xl overflow-hidden border border-white/5 shadow-lg group-hover:scale-[1.02] transition-transform">
                                <img src={getAssetPath(img.src)} alt={img.name} className="w-full h-auto block" />
                              </div>
                              <p className="text-center text-[10px] font-bold text-white/40 uppercase tracking-widest">{img.name}</p>
                            </div>
                          ))}
                        </div>
                      </motion.div>
          
                      {/* Tech Stack */}
                      <motion.div id="tech-stack" {...fadeInUp} className="space-y-8">
                        <div className="flex items-center gap-3 mb-6">
                          <div className="p-2 rounded-xl bg-purple-500/10 text-purple-600">
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
                            items={["Android System WebView", "kotlinx.serialization (JSON)", "StevenBlack hosts (ad/tracker blocklist)"]} 
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
                          <div className="p-2 rounded-xl bg-green-500/10 text-green-600">
                            <FolderTree size={24} />
                          </div>
                          <h2 className="text-3xl font-bold tracking-tight">Project Structure</h2>
                        </div>
                        <div className="bg-black/5 dark:bg-white/5 rounded-3xl p-8 border border-black/5 dark:border-white/5 font-mono text-sm leading-relaxed overflow-x-auto">
                          <pre>{PROJECT_STRUCTURE}</pre>
                        </div>
                      </motion.div>
          
                      {/* Development Setup */}
                      <motion.div id="setup" {...fadeInUp} className="space-y-8">              <div className="flex items-center gap-3 mb-6">
                <div className="p-2 rounded-xl bg-orange-500/10 text-orange-600">
                  <Terminal size={24} />
                </div>
                <h2 className="text-3xl font-bold tracking-tight">Development Setup</h2>
              </div>
              
              <div className="space-y-12">
                <div className="space-y-4">
                  <h3 className="text-xl font-bold">Prerequisites</h3>
                  <ul className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {["Android Studio (latest stable)", "JDK 17+", "Android SDK 37", "A device or emulator (Android 8.0+)"].map(item => (
                      <li key={item} className="flex items-center gap-3 text-white/60 font-medium">
                        <div className="w-1.5 h-1.5 rounded-full bg-orange-500" />
                        {item}
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold">Installation</h3>
                  <CodeBlock 
                    command={`git clone https://github.com/jleescy/mi-browser.git\ncd mi-browser`}
                  />
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold text-white">Running the App</h3>
                  <CodeBlock 
                    command={`./gradlew installDebug        # build + install on the connected device\n./gradlew testDebugUnitTest    # run the logic tests`}
                  />
                  <div className="flex gap-4 text-sm font-medium text-white/50 bg-white/5 p-4 rounded-2xl border border-white/5">
                    <Info size={18} className="shrink-0" />
                    <p>Or open the project folder in Android Studio and press Run. There is no JavaScript toolchain anymore: the app is a single Gradle module.</p>
                  </div>
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold text-white">Core Concepts</h3>
                  <div className="grid sm:grid-cols-2 gap-4">
                    <div className="p-6 rounded-2xl bg-white/5 border border-white/5">
                      <h4 className="font-bold text-sm mb-2">State Management</h4>
                      <p className="text-xs text-white/50 leading-relaxed">All state lives in BrowserViewModel as Compose state and is saved (debounced) as JSON files in app-private storage. WebViews live outside Compose in WebViewHost, one per tab.</p>
                    </div>
                    <div className="p-6 rounded-2xl bg-white/5 border border-white/5">
                      <h4 className="font-bold text-sm mb-2">Gesture Logic</h4>
                      <p className="text-xs text-white/50 leading-relaxed">The Pill, sheets, and drawer share one pan detector that tracks the finger in screen coordinates and always settles on release, animated with Material 3 Expressive springs.</p>
                    </div>
                  </div>
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold text-white">Building</h3>
                  <p className="text-sm text-white/60">Build a minified release APK with Gradle:</p>
                  <CodeBlock 
                    command={`./gradlew assembleRelease\n# → app/build/outputs/apk/release/`}
                  />
                  <div className="p-4 rounded-xl border border-blue-500/20 bg-blue-500/5 text-xs text-blue-400/80">
                    <p><strong>Signing:</strong> create a <code className="bg-white/10 px-1 rounded">keystore.properties</code> file in the project root (it is git-ignored) with <code className="bg-white/10 px-1 rounded">storeFile</code>, <code className="bg-white/10 px-1 rounded">storePassword</code>, <code className="bg-white/10 px-1 rounded">keyAlias</code> and <code className="bg-white/10 px-1 rounded">keyPassword</code>. Without it, the release APK is built unsigned.</p>
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

function TechCard({ title, items, color }: { title: string, items: string[], color: string }) {
  const colors: any = {
    blue: "bg-blue-500",
    purple: "bg-purple-500",
    pink: "bg-pink-500",
    cyan: "bg-cyan-500",
  };

  return (
    <div className="p-8 rounded-[2rem] bg-white dark:bg-white/5 border border-black/5 dark:border-white/5 shadow-sm">
      <h4 className="font-bold text-lg mb-4">{title}</h4>
      <ul className="space-y-2">
        {items.map(item => (
          <li key={item} className="flex items-center gap-2 text-white/60 font-medium">
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
      <div className="absolute -inset-0.5 bg-gradient-to-r from-blue-600 to-purple-600 rounded-2xl blur opacity-20 group-hover:opacity-40 transition duration-1000"></div>
      <div className="relative bg-black rounded-2xl p-6 font-mono text-sm text-white overflow-x-auto border border-white/10">
        <pre>{command}</pre>
      </div>
    </div>
  );
}
