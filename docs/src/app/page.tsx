"use client";

import { motion } from "framer-motion";
import { Download, ChevronRight, Zap, Palette, Shield, QrCode, Github, Sparkles } from "lucide-react";
import { Navbar } from "@/components/navbar";
import { Footer } from "@/components/footer";

// Helper to handle basePath for GitHub Pages
const getAssetPath = (path: string) => {
  const isProd = process.env.NODE_ENV === 'production';
  const repoName = process.env.NEXT_PUBLIC_REPO_NAME || "mi-browser";
  const basePath = isProd ? `/${repoName}` : "";
  return `${basePath}${path}`;
};

export default function Home() {
  const fadeInUp = {
    initial: { opacity: 0, y: 20 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true },
    transition: { duration: 0.6 }
  };

  const GITHUB_URL = "https://github.com/jleescy/mi-browser";

  return (
    <main className="min-h-screen bg-[#fafafa] dark:bg-[#050505] text-[#171717] dark:text-[#ededed] overflow-x-hidden transition-colors duration-300">
      <Navbar />

      {/* Hero Section */}
      <section className="pt-32 sm:pt-48 pb-16 sm:pb-20 px-6 relative">
        <div className="absolute top-20 left-1/2 -translate-x-1/2 w-full max-w-[600px] h-[300px] sm:h-[600px] bg-blue-500/10 dark:bg-blue-500/5 rounded-full blur-[80px] sm:blur-[120px] -z-10" />
        
        <div className="max-w-6xl mx-auto grid lg:grid-cols-2 gap-12 sm:gap-16 items-center text-center lg:text-left">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6 }}>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-500/10 text-blue-600 dark:text-blue-400 text-[10px] sm:text-xs font-bold tracking-widest uppercase mb-6 border border-blue-500/20">
               <Sparkles size={14} />
               Now in Beta
            </div>
            <h1 className="text-4xl sm:text-6xl lg:text-7xl font-extrabold tracking-tighter mb-6 leading-[1.1]">
              A minimal <span className="text-blue-600 italic">masterpiece</span> for the modern web.
            </h1>
            <p className="text-base sm:text-xl text-black/60 dark:text-white/60 mb-8 max-w-lg mx-auto lg:mx-0 leading-relaxed font-medium">
              mi. is a hyper-lightweight browser that replaces bulky toolbars with a single, intelligent Pill. Fast, private, and deeply customizable.
            </p>
            <div className="flex flex-col sm:flex-row justify-center lg:justify-start gap-4">
              <a href="#download" className="flex items-center justify-center gap-2 bg-blue-600 text-white px-8 py-4 rounded-2xl font-bold text-lg hover:shadow-xl hover:shadow-blue-500/25 transition-all active:scale-95">
                <Download size={20} />
                Download APK
              </a>
              <a href={GITHUB_URL} className="flex items-center justify-center gap-2 bg-black/5 dark:bg-white/5 px-8 py-4 rounded-2xl font-bold text-lg hover:bg-black/10 dark:hover:bg-white/10 transition-all border border-black/5 dark:border-white/5">
                View on GitHub
                <ChevronRight size={20} />
              </a>
            </div>
          </motion.div>

          <motion.div 
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.8, delay: 0.2 }}
            className="relative flex justify-center lg:justify-end mt-8 lg:mt-0"
          >
            <DeviceMockup src={getAssetPath("/images/homepage.jpg")} color="blue" />
          </motion.div>
        </div>
      </section>

      {/* Quick Specs Section */}
      <section className="py-16 sm:py-24 border-y border-black/5 dark:border-white/5 bg-white dark:bg-black/20">
        <div className="max-w-6xl mx-auto px-6 grid sm:grid-cols-2 md:grid-cols-3 gap-8 sm:gap-12">
           <FeatureBrief icon={<Zap size={24} className="text-yellow-500"/>} title="Instant Interaction" desc="Zero lag, gesture-first navigation." />
           <FeatureBrief icon={<Palette size={24} className="text-pink-500"/>} title="Adaptive UI" desc="Harmonizes with your chosen accent." />
           <FeatureBrief icon={<Shield size={24} className="text-blue-500"/>} title="Privacy Native" desc="Your data never leaves your device." />
        </div>
      </section>

      {/* Detailed Features: Alternating Sections */}
      <div className="space-y-24 sm:space-y-32 py-24 sm:py-32">
        
        {/* The Pill / Dashboard */}
        <FeatureDetail 
          image={getAssetPath("/images/dashboard.jpg")}
          tag="Interaction"
          tagColor="text-blue-600 dark:text-blue-400 bg-blue-500/10 border-blue-500/20"
          title="One Pill. Total Control."
          description="The dashboard is the heart of mi. Browser. A single swipe up on the Pill reveals your entire digital world—History, Bookmarks, and Settings—all logically organized in an elegant, glass-morphism sheet."
          bullets={[
            "Swipe up to open, down to hide",
            "One-handed optimization",
            "Haptic-feedback gestures",
            "Immersive full-screen mode"
          ]}
          imageSide="right"
          glowColor="blue"
        />

        {/* Tab Management */}
        <FeatureDetail 
          image={getAssetPath("/images/tab_grid.jpg")}
          tag="Organization"
          tagColor="text-purple-600 dark:text-purple-400 bg-purple-500/10 border-purple-500/20"
          title="Visual Tab Management."
          description="Stop guessing which tab is which. mi. provides a high-fidelity grid view with real-time snapshots of your open sessions, making it effortless to switch context or clean up your workspace."
          bullets={[
            "Visual snapshot previews",
            "Seamless reordering",
            "Clear-all tabs shortcut",
            "Hyper-fast tab switching"
          ]}
          imageSide="left"
          glowColor="purple"
        />

        {/* Customization */}
        <FeatureDetail 
          image={getAssetPath("/images/settings.jpg")}
          tag="Aesthetic"
          tagColor="text-pink-600 dark:text-pink-400 bg-pink-500/10 border-pink-500/20"
          title="Deeply, truly yours."
          description="Browser settings shouldn't be boring. mi. offers a robust customization engine that lets you adjust UI density, pill height, and corner radius. Change your accent color and watch the entire app adapt instantly."
          bullets={[
            "Real-time theme engine",
            "Adjustable corner geometry",
            "Custom search engine support",
            "UI density & font scaling"
          ]}
          imageSide="right"
          glowColor="pink"
        />

        {/* Power Tools / Context Menu */}
        <FeatureDetail 
          image={getAssetPath("/images/power_tools.jpg")}
          tag="Utility"
          tagColor="text-cyan-600 dark:text-cyan-400 bg-cyan-500/10 border-cyan-500/20"
          title="Power in your pocket."
          description="The Power Tools menu provides instant access to the utilities you use most. From built-in QR code generation and scanning to desktop mode toggles and a clean Reader View, everything is just a tap away in the Dashboard Menu."
          bullets={[
            "Integrated QR Toolbox",
            "One-tap Reader Mode",
            "Desktop/Mobile toggling",
            "Native Print & Share"
          ]}
          imageSide="left"
          glowColor="cyan"
        />

      </div>

      {/* Download CTA */}
      <section id="download" className="py-20 sm:py-40 bg-black dark:bg-white text-white dark:text-black rounded-[2rem] sm:rounded-[4rem] mx-4 mb-20 relative overflow-hidden">
        <div className="absolute top-0 left-0 w-full h-full opacity-40 pointer-events-none">
           <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-blue-600 rounded-full blur-[120px]" />
           <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-purple-600 rounded-full blur-[120px]" />
        </div>
        <div className="max-w-3xl mx-auto px-6 text-center relative z-10">
          <h2 className="text-4xl sm:text-6xl font-extrabold mb-8 tracking-tighter">Ready for the modern web?</h2>
          <p className="text-lg sm:text-xl opacity-70 mb-12 font-medium">
            Download the latest APK for Android and experience a browser that stays out of your way.
          </p>
          <div className="flex flex-col items-center gap-6">
             <a href={`${GITHUB_URL}/releases`} className="group flex items-center gap-4 bg-blue-600 text-white px-8 py-4 sm:px-12 sm:py-6 rounded-2xl sm:rounded-3xl font-black text-xl sm:text-2xl hover:scale-105 active:scale-95 transition-all shadow-2xl shadow-blue-500/40">
               <Download size={28} />
               Install Latest APK
             </a>
             <span className="text-sm opacity-40 font-bold tracking-widest uppercase">Requires Android 8.0+</span>
          </div>
        </div>
      </section>

      {/* Footer */}
      <Footer />
    </main>
  );
}

function FeatureBrief({ icon, title, desc }: { icon: React.ReactNode, title: string, desc: string }) {
  return (
    <div className="space-y-3">
      <div className="text-black dark:text-white bg-black/5 dark:bg-white/5 w-12 h-12 flex items-center justify-center rounded-2xl mb-4 border border-black/5 dark:border-white/5 shadow-sm">
        {icon}
      </div>
      <h4 className="font-bold text-lg tracking-tight">{title}</h4>
      <p className="text-black/50 dark:text-white/50 leading-relaxed font-medium">{desc}</p>
    </div>
  );
}

function FeatureDetail({ image, tag, tagColor, title, description, bullets, imageSide, glowColor }: any) {
  const content = (
    <motion.div 
      initial={{ opacity: 0, x: imageSide === 'right' ? -30 : 30 }}
      whileInView={{ opacity: 1, x: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.8 }}
      className="flex-1 space-y-8 text-center lg:text-left"
    >
      <div className="space-y-4">
        <span className={`inline-block px-4 py-1.5 rounded-full text-[10px] sm:text-xs font-bold tracking-[0.2em] uppercase border ${tagColor}`}>
          {tag}
        </span>
        <h3 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold tracking-tighter leading-tight">{title}</h3>
        <p className="text-base sm:text-xl text-black/60 dark:text-white/60 leading-relaxed font-medium">
          {description}
        </p>
      </div>
      <ul className="space-y-4 inline-block lg:block text-left">
        {bullets.map((b: string) => (
          <li key={b} className="flex items-center gap-3 font-bold text-black/80 dark:text-white/80 text-sm sm:text-base">
            <div className={`w-2 h-2 rounded-full shrink-0 ${
              glowColor === 'blue' ? 'bg-blue-500' : 
              glowColor === 'purple' ? 'bg-purple-500' : 
              glowColor === 'pink' ? 'bg-pink-500' : 'bg-cyan-500'
            }`} />
            {b}
          </li>
        ))}
      </ul>
    </motion.div>
  );

  const imgBlock = (
    <div className="flex-1 flex justify-center mt-8 lg:mt-0">
      <DeviceMockup src={image} color={glowColor} />
    </div>
  );

  return (
    <section className="max-w-6xl mx-auto px-6">
      <div className={`flex flex-col ${imageSide === 'right' ? 'lg:flex-row' : 'lg:flex-row-reverse'} items-center gap-12 lg:gap-20`}>
        {content}
        {imgBlock}
      </div>
    </section>
  );
}

function DeviceMockup({ src, color }: { src: string, color?: string }) {
  const glowStyles: any = {
    blue: "bg-blue-500/20 shadow-blue-500/40",
    purple: "bg-purple-500/20 shadow-purple-500/40",
    pink: "bg-pink-500/20 shadow-pink-500/40",
    orange: "bg-orange-500/20 shadow-orange-500/40",
    cyan: "bg-cyan-500/20 shadow-cyan-500/40",
  };

  return (
    <div className="relative group">
      <div className={`absolute -inset-10 ${color ? glowStyles[color].split(' ')[0] : 'bg-blue-500/10'} rounded-full blur-[80px] group-hover:opacity-100 transition-opacity opacity-50`} />
      <div className={`relative w-[220px] sm:w-[280px] bg-black rounded-[2rem] sm:rounded-[2.5rem] p-2 sm:p-3 shadow-2xl border-[4px] sm:border-[6px] border-[#1a1a1a] overflow-hidden transition-transform duration-500 group-hover:scale-105 group-hover:-rotate-1`}>
        <img 
          src={src} 
          alt="mi. App Screenshot" 
          className="w-full h-auto block rounded-[1.4rem] sm:rounded-[1.8rem]"
        />
      </div>
    </div>
  );
}
