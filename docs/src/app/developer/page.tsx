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
├── app/                 # Expo Router pages (UI screens)
├── assets/              # Static assets (images, fonts)
├── src/
│   ├── components/      # Reusable UI components
│   ├── hooks/           # Custom hooks (logic extraction)
│   ├── utils/           # Helper functions
│   └── constants.ts     # App-wide configuration
└── ...config files
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
                            { name: "Dashboard", src: "/images/dashboard.jpg" },
                            { name: "Tabs Grid", src: "/images/tab_grid.jpg" },
                            { name: "Tabs List", src: "/images/tab_rows.jpg" },
                            { name: "Bookmarks", src: "/images/bookmarks.jpg" },
                            { name: "History", src: "/images/history.jpg" },
                            { name: "Recent History", src: "/images/recent_history.jpg" },
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
                            items={["React Native", "Expo (SDK 52+)", "Expo Router"]} 
                            color="blue"
                          />
                          <TechCard 
                            title="Engines & Logic" 
                            items={["react-native-webview", "React Hooks", "AsyncStorage"]} 
                            color="purple"
                          />
                          <TechCard 
                            title="Animation & Interaction" 
                            items={["Reanimated", "Gesture Handler", "PanResponder"]} 
                            color="pink"
                          />
                          <TechCard 
                            title="System Integration" 
                            items={["Quick Actions", "File System", "Haptics", "Print"]} 
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
                    {["Node.js (LTS)", "npm or yarn", "Expo CLI (npx)", "Expo Go or Emulator"].map(item => (
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
                    command={`git clone https://github.com/jleescy/mi-browser.git\ncd mi-browser\nnpm install`}
                  />
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold text-white">Running the App</h3>
                  <CodeBlock 
                    command={`npx expo start`}
                  />
                  <div className="flex gap-4 text-sm font-medium text-white/50 bg-white/5 p-4 rounded-2xl border border-white/5">
                    <Info size={18} className="shrink-0" />
                    <p>Press <code className="bg-white/10 px-1 rounded">a</code> for Android Emulator or scan the QR code for Expo Go on your physical device.</p>
                  </div>
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold text-white">Core Concepts</h3>
                  <div className="grid sm:grid-cols-2 gap-4">
                    <div className="p-6 rounded-2xl bg-white/5 border border-white/5">
                      <h4 className="font-bold text-sm mb-2">State Management</h4>
                      <p className="text-xs text-white/50 leading-relaxed">Most app state is centralized in custom hooks (useBrowserSettings, useTabs, etc.) and persisted using AsyncStorage.</p>
                    </div>
                    <div className="p-6 rounded-2xl bg-white/5 border border-white/5">
                      <h4 className="font-bold text-sm mb-2">Gesture Logic</h4>
                      <p className="text-xs text-white/50 leading-relaxed">The Pill uses a combination of PanResponder and Reanimated to handle complex multi-directional swipes with haptic feedback.</p>
                    </div>
                  </div>
                </div>

                <div className="space-y-6">
                  <h3 className="text-xl font-bold text-white">Building</h3>
                  <p className="text-sm text-white/60">Standard EAS builds can be triggered via:</p>
                  <CodeBlock 
                    command={`eas build -p android --profile preview`}
                  />
                  
                  <div className="pt-4 space-y-4">
                    <h4 className="text-lg font-bold text-white/80">Local APK Generation</h4>
                    <p className="text-sm text-white/60">For faster local iterations without waiting for EAS queues, use the provided build script. This requires <code className="bg-white/10 px-1 rounded">bundletool</code> and a local Android environment.</p>
                    <CodeBlock 
                      command={`chmod +x build_apk.sh\n./build_apk.sh --version 1.0.0`}
                    />
                    <div className="p-4 rounded-xl border border-blue-500/20 bg-blue-500/5 text-xs text-blue-400/80">
                      <p><strong>Note:</strong> The script performs a local EAS build, extracts the AAB, and uses bundletool to generate a universal installable APK in the <code className="bg-white/10 px-1 rounded">./builds</code> folder.</p>
                    </div>
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
