"use client";

import { motion } from "framer-motion";
import { Download, ChevronRight, Zap, Palette, Shield, Sparkles, Puzzle, Check } from "lucide-react";
import { Navbar } from "@/components/navbar";
import { Footer } from "@/components/footer";
import { GITHUB_URL, getAssetPath, MI_APPS } from "@/site";
import { DeviceMockup } from "@/components/device-mockup";


export default function Home() {

  return (
    <main className="min-h-screen bg-surface-container-low text-on-surface overflow-x-hidden transition-colors duration-300">
      <Navbar />

      {/* Hero Section */}
      <section className="pt-32 sm:pt-48 pb-16 sm:pb-20 px-6 relative">
        <div className="absolute top-20 left-1/2 -translate-x-1/2 w-full max-w-[600px] h-[300px] sm:h-[600px] bg-primary/10 rounded-full blur-[80px] sm:blur-[120px] -z-10" />
        
        <div className="max-w-6xl mx-auto grid lg:grid-cols-2 gap-12 sm:gap-16 items-center text-center lg:text-left">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6 }}>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-primary-container text-primary text-[10px] sm:text-xs font-bold tracking-widest uppercase mb-6 border border-primary/20">
               <Sparkles size={14} />
               2.0 · For Android
            </div>
            <h1 className="text-4xl sm:text-6xl lg:text-7xl font-extrabold tracking-tighter mb-6 leading-[1.1]">
              A minimal <span className="text-primary italic">masterpiece</span> for the modern web.
            </h1>
            <p className="text-base sm:text-xl text-on-surface-variant mb-8 max-w-lg mx-auto lg:mx-0 leading-relaxed font-medium">
              A simple, private browser built around one idea: everything you need is in a single Pill at the bottom of the screen. Search, switch tabs and go back with a swipe, while ads and trackers are blocked for you. Add extensions, follow prices, keep any site on your home screen as its own app, and browse privately whenever you want.
            </p>
            <div className="flex flex-col sm:flex-row justify-center lg:justify-start gap-4">
              <a href="#download" className="flex items-center justify-center gap-2 bg-primary text-on-primary px-8 py-4 rounded-2xl font-bold text-lg hover:shadow-xl hover:shadow-primary/25 transition-all active:scale-95">
                <Download size={20} />
                Download APK
              </a>
              <a href={GITHUB_URL} className="flex items-center justify-center gap-2 bg-surface-container-high px-8 py-4 rounded-2xl font-bold text-lg hover:bg-surface-container-highest transition-all border border-outline-variant">
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
            <DeviceMockup src="/images/homepage.jpg" color="blue" />
          </motion.div>
        </div>
      </section>

      {/* Quick Specs Section */}
      <section className="py-16 sm:py-24 border-y border-outline-variant bg-surface-container">
        <div className="max-w-6xl mx-auto px-6 grid sm:grid-cols-2 md:grid-cols-3 gap-8 sm:gap-12">
           <FeatureBrief icon={<Zap size={24} />} title="Instant Interaction" desc="Zero lag, gesture-first navigation." />
           <FeatureBrief icon={<Palette size={24} />} title="Material You" desc="Colors from your accent or your wallpaper." />
           <FeatureBrief icon={<Shield size={24} />} title="Privacy Native" desc="Built-in ad and tracker blocking. Your data never leaves your device." />
        </div>
      </section>

      {/* Detailed Features: Alternating Sections */}
      <div className="space-y-24 sm:space-y-32 py-24 sm:py-32">
        
        {/* The Pill */}
        <FeatureDetail 
          image="/images/browsing.jpg"
          tag="Interaction"
          tagColor="text-primary bg-primary-container border-primary/20"
          title="One Pill. Total Control."
          description="The Pill is the heart of mi. Browser. Tap to search with live suggestions, swipe sideways to go back or forward, swipe up for the Tab Switcher, and swipe down to hide it."
          bullets={[
            "Swipe up for tabs, down to hide",
            "One-handed optimization",
            "Pin the recall button left, center, or right",
            "Immersive full-screen mode"
          ]}
          imageSide="right"
          glowColor="blue"
        />

        {/* Tab Management */}
        <FeatureDetail 
          image="/images/tab_zoom.jpg"
          tag="Organization"
          tagColor="text-tertiary bg-tertiary-container border-tertiary/20"
          title="Visual Tab Management."
          description="Swipe up and the page zooms out into a card, just like your phone's recent apps. Your tabs line up by when you last used them, with live previews. Swipe a card up to close it."
          bullets={[
            "Zooms with your finger",
            "Most recent tab on the right",
            "Swipe up to close, with undo",
            "Live previews of every tab"
          ]}
          imageSide="left"
          glowColor="purple"
        />

        {/* Customization */}
        <FeatureDetail 
          image="/images/settings.jpg"
          tag="Aesthetic"
          tagColor="text-tertiary bg-tertiary-container border-tertiary/20"
          title="Deeply, truly yours."
          description="Browser settings shouldn't be boring. mi. offers a robust customization engine that lets you adjust UI density, pill height, and corner radius. Pick an accent (or use your wallpaper) and the whole Material 3 color scheme regenerates instantly."
          bullets={[
            "Material You dynamic color",
            "Adjustable corner geometry",
            "Rounded Google Sans Flex option",
            "UI density & font scaling"
          ]}
          imageSide="right"
          glowColor="pink"
        />

        {/* Power Tools / Context Menu */}
        <FeatureDetail 
          image="/images/power_tools.jpg"
          tag="Utility"
          tagColor="text-secondary bg-secondary-container border-secondary/20"
          title="Power in your pocket."
          description="The tab menu puts the tools you use most a tap away: favorite, bookmark or pin the tab, follow a product's price, switch desktop site or Reader mode, find in page, and scan or create QR codes. It's the ⋮ in the Tab Switcher."
          bullets={[
            "Price following, on your phone",
            "Integrated QR Toolbox",
            "One-tap Reader Mode",
            "Desktop/Mobile toggling",
            "Native Print & Share"
          ]}
          imageSide="left"
          glowColor="cyan"
        />

        <Extensions />

      </div>

      {/* Download CTA */}
      <section id="download" className="py-20 sm:py-40 bg-primary-container text-on-primary-container rounded-[2rem] sm:rounded-[4rem] mx-4 mb-20 relative overflow-hidden">
        <div className="absolute top-0 left-0 w-full h-full opacity-40 pointer-events-none">
           <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-primary rounded-full blur-[120px]" />
           <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-tertiary rounded-full blur-[120px]" />
        </div>
        <div className="max-w-3xl mx-auto px-6 text-center relative z-10">
          <h2 className="text-4xl sm:text-6xl font-extrabold mb-8 tracking-tighter">Ready for the modern web?</h2>
          <p className="text-lg sm:text-xl opacity-70 mb-12 font-medium">
            Download the latest APK for Android and experience a browser that stays out of your way.
          </p>
          <div className="flex flex-col items-center gap-6">
             <a href={`${GITHUB_URL}/releases`} className="group flex items-center gap-4 bg-primary text-on-primary px-8 py-4 sm:px-12 sm:py-6 rounded-2xl sm:rounded-3xl font-black text-xl sm:text-2xl hover:scale-105 active:scale-95 transition-all shadow-2xl shadow-primary/30">
               <Download size={28} />
               Install Latest APK
             </a>
             <span className="text-sm opacity-40 font-bold tracking-widest uppercase">Requires Android 8.0+</span>
          </div>
        </div>
      </section>

      <MoreApps />

      {/* Footer */}
      <Footer />
    </main>
  );
}

function MoreApps() {
  return (
    <section className="max-w-6xl mx-auto px-6 mb-20">
      <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tighter mb-8 text-center">More mi. Apps</h2>
      <div className="grid sm:grid-cols-2 gap-4">
        {MI_APPS.map((a) => (
          <a key={a.name} href={a.href} className="flex items-center gap-5 rounded-[2rem] bg-surface-container p-6 hover:bg-surface-container-high transition-colors">
            <img src={getAssetPath(a.icon)} alt={`${a.name} icon`} className="w-14 h-14 rounded-full shrink-0" />
            <div>
              <div className="text-lg font-extrabold">{a.name}</div>
              <div className="text-sm text-on-surface-variant font-medium leading-snug">{a.line}</div>
            </div>
            <ChevronRight size={20} className="ml-auto shrink-0 text-on-surface-variant" />
          </a>
        ))}
      </div>
    </section>
  );
}

/** Extensions: what mi runs, and the ones tested by hand (the app's "Tested in mi" list). */
function Extensions() {
  const tested = ["Dark Reader", "uBlock Origin", "uBlock Origin Lite", "Keepa", "SponsorBlock", "Privacy Badger", "ClearURLs", "LocalCDN"];
  return (
    <section className="max-w-6xl mx-auto px-6">
      <motion.div
        initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.6 }}
        className="rounded-[2rem] sm:rounded-[3rem] bg-surface-container p-8 sm:p-14 grid lg:grid-cols-2 gap-10 items-center"
      >
        <div className="space-y-6 text-center lg:text-left">
          <span className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full text-[10px] sm:text-xs font-bold tracking-[0.2em] uppercase bg-tertiary-container text-on-tertiary-container">
            <Puzzle size={14} /> New in 2.0
          </span>
          <h3 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold tracking-tighter leading-tight">Extensions, made for phones.</h3>
          <p className="text-base sm:text-xl text-on-surface-variant leading-relaxed font-medium">
            mi runs extensions from Firefox Add-ons for Android on its own engine. The store offers only extensions made for phones, mi tells you how well each one works before you add it, and the ones below have been tested by hand.
          </p>
        </div>
        <div className="flex flex-wrap gap-3 justify-center lg:justify-start">
          {tested.map((name) => (
            <span key={name} className="inline-flex items-center gap-2 pl-3 pr-4 py-2.5 rounded-full bg-surface-container-highest text-on-surface font-bold text-sm sm:text-base">
              <Check size={16} className="text-primary" /> {name}
            </span>
          ))}
        </div>
      </motion.div>
    </section>
  );
}

function FeatureBrief({ icon, title, desc }: { icon: React.ReactNode, title: string, desc: string }) {
  return (
    <div className="space-y-3">
      <div className="bg-primary-container text-on-primary-container w-12 h-12 flex items-center justify-center rounded-full mb-4">
        {icon}
      </div>
      <h4 className="font-bold text-lg tracking-tight">{title}</h4>
      <p className="text-on-surface-variant leading-relaxed font-medium">{desc}</p>
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
        <p className="text-base sm:text-xl text-on-surface-variant leading-relaxed font-medium">
          {description}
        </p>
      </div>
      <ul className="space-y-4 inline-block lg:block text-left">
        {bullets.map((b: string) => (
          <li key={b} className="flex items-center gap-3 font-bold text-on-surface text-sm sm:text-base">
            <div className={`w-2 h-2 rounded-full shrink-0 ${
              glowColor === 'blue' ? 'bg-primary' : 
              glowColor === 'purple' ? 'bg-tertiary' : 
              glowColor === 'pink' ? 'bg-tertiary' : 'bg-secondary'
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

