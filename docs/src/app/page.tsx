"use client";

import { motion } from "framer-motion";
import { Download, ChevronRight, Zap, Palette, Shield, QrCode, Github } from "lucide-react";

// Helper to handle basePath for GitHub Pages
const getAssetPath = (path: string) => {
  const basePath = "/my-browser";
  return `${basePath}${path}`;
};

export default function Home() {
  const fadeInUp = {
    initial: { opacity: 0, y: 20 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0.6 }
  };

  const GITHUB_URL = "https://github.com/jleescy/my-browser";

  return (
    <main className="min-h-screen bg-[#fafafa] dark:bg-[#050505] text-[#171717] dark:text-[#ededed] overflow-x-hidden">
      {/* Header */}
      <nav className="fixed top-0 w-full z-50 bg-white/80 dark:bg-black/80 backdrop-blur-md border-b border-black/5 dark:border-white/5">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <img src={getAssetPath("/icon.png")} alt="mi. logo" className="w-8 h-8 rounded-lg" />
            <span className="font-bold text-xl tracking-tight">mi.</span>
          </div>
          <div className="flex items-center gap-6">
            <a href={GITHUB_URL} className="hover:opacity-60 transition-opacity">
              <Github size={20} />
            </a>
            <a href="#download" className="bg-black dark:bg-white text-white dark:text-black px-4 py-2 rounded-full text-sm font-semibold hover:scale-105 transition-transform">
              Download
            </a>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="pt-32 pb-20 px-6">
        <div className="max-w-6xl mx-auto grid lg:grid-cols-2 gap-12 items-center">
          <motion.div {...fadeInUp}>
            <h1 className="text-6xl lg:text-7xl font-extrabold tracking-tighter mb-6 leading-tight">
              A minimal masterpiece for the modern web.
            </h1>
            <p className="text-xl text-black/60 dark:text-white/60 mb-8 max-w-lg leading-relaxed">
              mi. is a hyper-lightweight browser that replaces bulky toolbars with a single, intelligent Pill. Fast, private, and deeply customizable.
            </p>
            <div className="flex flex-wrap gap-4">
              <a href="#download" className="flex items-center gap-2 bg-black dark:bg-white text-white dark:text-black px-8 py-4 rounded-2xl font-bold text-lg hover:shadow-xl transition-all active:scale-95">
                <Download size={20} />
                Download APK
              </a>
              <a href={GITHUB_URL} className="flex items-center gap-2 bg-black/5 dark:bg-white/5 px-8 py-4 rounded-2xl font-bold text-lg hover:bg-black/10 dark:hover:bg-white/10 transition-all">
                View on GitHub
                <ChevronRight size={20} />
              </a>
            </div>
          </motion.div>

          <motion.div 
            initial={{ opacity: 0, scale: 0.9, rotate: 2 }}
            animate={{ opacity: 1, scale: 1, rotate: 0 }}
            transition={{ duration: 0.8, delay: 0.2 }}
            className="relative flex justify-center lg:justify-end"
          >
            <div className="relative w-[300px] aspect-[9/19.5] bg-black rounded-[3rem] p-3 shadow-2xl border-4 border-black/10 dark:border-white/10 overflow-hidden">
              <img 
                src={getAssetPath("/images/homepage.png")} 
                alt="mi. Browser Homepage" 
                className="w-full h-full object-cover rounded-[2.5rem]"
              />
            </div>
            {/* Decorative elements */}
            <div className="absolute -bottom-6 -left-6 w-32 h-32 bg-blue-500/20 rounded-full blur-3xl -z-10" />
            <div className="absolute -top-6 -right-6 w-32 h-32 bg-purple-500/20 rounded-full blur-3xl -z-10" />
          </motion.div>
        </div>
      </section>

      {/* Features Grid */}
      <section className="py-20 bg-black/5 dark:bg-white/5">
        <div className="max-w-6xl mx-auto px-6">
          <div className="text-center mb-16">
            <h2 className="text-4xl font-bold tracking-tight mb-4">Reimagined from the ground up</h2>
            <p className="text-black/50 dark:text-white/50 text-lg">Every detail crafted for an effortless browsing experience.</p>
          </div>

          <div className="grid md:grid-cols-3 gap-8">
            <FeatureCard 
              icon={<Zap className="text-yellow-500" />}
              title="The Pill"
              description="A single gesture-driven control center at your thumb's reach. Tap, swipe, and browse with zero friction."
            />
            <FeatureCard 
              icon={<Palette className="text-pink-500" />}
              title="Fully Adaptive"
              description="Customize every pixel. From accent colors to corner radius, mi. adapts to your unique aesthetic."
            />
            <FeatureCard 
              icon={<Shield className="text-blue-500" />}
              title="Privacy First"
              description="HTTPS only, cookie blocking, and granular history control. Your data stays on your device."
            />
          </div>
        </div>
      </section>

      {/* Visual Showcase */}
      <section className="py-20 overflow-hidden">
        <div className="max-w-6xl mx-auto px-6">
          <div className="flex flex-col lg:flex-row items-center gap-16">
            <div className="flex-1 space-y-12">
              <div className="space-y-4">
                <span className="text-blue-500 font-bold tracking-widest text-xs uppercase">Customization</span>
                <h3 className="text-4xl font-bold tracking-tight">Make it yours.</h3>
                <p className="text-black/60 dark:text-white/60 text-lg leading-relaxed">
                  Adjust UI density, pill height, and visual shaping. mi. is the first browser that actually lets you design its interface.
                </p>
              </div>
              <div className="space-y-4">
                <span className="text-purple-500 font-bold tracking-widest text-xs uppercase">Tab Management</span>
                <h3 className="text-4xl font-bold tracking-tight">Visual focus.</h3>
                <p className="text-black/60 dark:text-white/60 text-lg leading-relaxed">
                  A beautiful grid view with live snapshots keeps your browsing organized and visually intuitive.
                </p>
              </div>
            </div>

            <div className="flex-1 flex gap-4 rotate-3 scale-110 origin-center">
              <ShowcaseDevice src={getAssetPath("/images/settings.png")} delay={0} />
              <ShowcaseDevice src={getAssetPath("/images/tabs.png")} delay={0.2} />
              <ShowcaseDevice src={getAssetPath("/images/dashboard.png")} delay={0.4} />
            </div>
          </div>
        </div>
      </section>

      {/* QR Toolbox Section */}
      <section className="py-20 bg-black text-white rounded-[4rem] mx-4 mb-20 overflow-hidden relative">
        <div className="absolute inset-0 bg-gradient-to-br from-blue-600/20 to-purple-600/20 opacity-50" />
        <div className="max-w-4xl mx-auto px-6 text-center relative z-10">
          <div className="inline-block p-4 bg-white/10 rounded-3xl mb-8 backdrop-blur-xl border border-white/20">
            <QrCode size={48} />
          </div>
          <h2 className="text-5xl font-extrabold tracking-tight mb-6">Tools at the speed of thought.</h2>
          <p className="text-xl text-white/70 mb-10 max-w-2xl mx-auto">
            Built-in QR scanner, generator, desktop mode toggles, and article reader. All one tap away in the context menu.
          </p>
          <div className="max-w-xl mx-auto rounded-3xl overflow-hidden border border-white/10 shadow-2xl bg-white/5 backdrop-blur-md">
             <img src={getAssetPath("/images/context-menu.png")} alt="Context Menu" className="w-full h-auto" />
          </div>
        </div>
      </section>

      {/* Download CTA */}
      <section id="download" className="py-32 text-center">
        <div className="max-w-2xl mx-auto px-6">
          <h2 className="text-5xl font-bold mb-6 tracking-tight">Ready to switch?</h2>
          <p className="text-xl text-black/60 dark:text-white/60 mb-10">
            Download the latest APK for Android and experience the web without distraction.
          </p>
          <div className="flex flex-col items-center gap-4">
             <a href={`${GITHUB_URL}/releases`} className="inline-flex items-center gap-3 bg-black dark:bg-white text-white dark:text-black px-12 py-5 rounded-[2rem] font-black text-xl hover:scale-105 active:scale-95 transition-all shadow-xl">
               <Download size={24} />
               Install Latest APK
             </a>
             <span className="text-sm text-black/40 dark:text-white/40">Requires Android 8.0 or later</span>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="py-20 border-t border-black/5 dark:border-white/5">
        <div className="max-w-6xl mx-auto px-6 flex flex-col md:flex-row justify-between items-center gap-8">
          <div className="flex items-center gap-2">
            <img src={getAssetPath("/icon.png")} alt="mi. logo" className="w-6 h-6 rounded-lg opacity-50" />
            <span className="font-bold opacity-50">mi. Browser</span>
          </div>
          <div className="flex gap-8 text-sm font-medium text-black/40 dark:text-white/40">
            <a href={GITHUB_URL} className="hover:text-black dark:hover:text-white transition-colors">GitHub</a>
            <a href="https://github.com/jleescy/my-browser/blob/main/DEVELOPMENT.md" className="hover:text-black dark:hover:text-white transition-colors">Developers</a>
            <a href="#" className="hover:text-black dark:hover:text-white transition-colors">Privacy</a>
          </div>
          <p className="text-sm text-black/30 dark:text-white/30">© 2026 mi. Open Source Project</p>
        </div>
      </footer>
    </main>
  );
}

function FeatureCard({ icon, title, description }: { icon: React.ReactNode, title: string, description: string }) {
  return (
    <div className="p-8 bg-white dark:bg-black/40 rounded-[2.5rem] border border-black/5 dark:border-white/5 shadow-sm hover:shadow-xl transition-shadow">
      <div className="mb-6 scale-125 origin-left">{icon}</div>
      <h3 className="text-2xl font-bold mb-3 tracking-tight">{title}</h3>
      <p className="text-black/60 dark:text-white/60 leading-relaxed">{description}</p>
    </div>
  );
}

function ShowcaseDevice({ src, delay }: { src: string, delay: number }) {
  return (
    <motion.div 
      initial={{ opacity: 0, y: 50 }}
      whileInView={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.8, delay }}
      viewport={{ once: true }}
      className="w-[220px] aspect-[9/19.5] bg-black rounded-[2.5rem] p-2 shadow-2xl border-4 border-black/10 dark:border-white/10 overflow-hidden shrink-0"
    >
      <img src={src} alt="Showcase" className="w-full h-full object-cover rounded-[2rem]" />
    </motion.div>
  );
}