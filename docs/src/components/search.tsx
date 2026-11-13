"use client";

import { Code2, FileText, Search as SearchIcon, Settings2, Shield, User, X } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

interface SearchResult {
  title: string;
  type: "User Guide" | "Dev Guide" | "Settings" | "Architecture";
  href: string;
  excerpt: string;
}

const MOCK_DATA: SearchResult[] = [
  // User Guide Sections
  { title: "Core Interactions", type: "User Guide", href: "/user#the-pill", excerpt: "Master the core interaction element: tap and swipe-up gestures." },
  { title: "The Pill", type: "User Guide", href: "/user#the-pill", excerpt: "Master the core interaction element: tap and swipe-up gestures." },
  { title: "Navigation Gestures", type: "User Guide", href: "/user#gestures", excerpt: "Learn horizontal swipe gestures for back/forward navigation." },
  { title: "Site Settings", type: "User Guide", href: "/user#site-settings", excerpt: "One site's permissions, pop-ups, ad blocking and data." },
  { title: "Recently Closed Tabs", type: "User Guide", href: "/user#recently-closed", excerpt: "History's Recently Closed section: reopen tabs you closed." },
  { title: "Search & Favorites", type: "User Guide", href: "/user#recent-history", excerpt: "Suggestions inside the Pill, inline answers, and favorites on the home page." },
  { title: "Inline Answers", type: "User Guide", href: "/user#recent-history", excerpt: "Calculator, unit conversions and more, right above the field." },
  { title: "Paste and Go", type: "User Guide", href: "/user#the-pill", excerpt: "Open a link you just copied from the Pill." },
  { title: "Downloads", type: "User Guide", href: "/user#downloads", excerpt: "Pause, resume and manage downloads beside Bookmarks in the Library." },
  { title: "Find in Page", type: "User Guide", href: "/user#find-in-page", excerpt: "Find text on the page, with a match count." },
  // Settings (the app's sections: Search, Web Pages, Media, Look & Feel, Pill, Home Page, Ad Blocking, Privacy & Security, Tabs, Gestures, Library, History, Backup & Reset, About)
  { title: "Theme", type: "Settings", href: "/user#setting-theme", excerpt: "Light, Dark, or System." },
  { title: "Pure Black", type: "Settings", href: "/user#setting-pure-black", excerpt: "True black dark theme for OLED screens." },
  { title: "Accent Color", type: "Settings", href: "/user#setting-accent-color", excerpt: "Adaptive (wallpaper), Gray, Blue, Green, Red, Orange, Yellow, or Purple." },
  { title: "Font Size", type: "Settings", href: "/user#setting-font-size", excerpt: "The app's text size, 80% to 120%." },
  { title: "Font", type: "Settings", href: "/user#setting-font", excerpt: "Standard, rounded, or your phone's system font." },
  { title: "Corners", type: "Settings", href: "/user#setting-corners", excerpt: "Square, Soft, or Round." },
  { title: "Spacing", type: "Settings", href: "/user#setting-spacing", excerpt: "Compact, Normal, or Airy." },
  { title: "Status Bar", type: "Settings", href: "/user#setting-status-bar", excerpt: "Show or hide the system status bar." },
  { title: "Haptic Feedback", type: "Settings", href: "/user#setting-haptic-feedback", excerpt: "Light taps at gesture thresholds." },
  { title: "Loading Bar", type: "Settings", href: "/user#setting-loading-bar", excerpt: "Standard, Center Out, or Hidden." },
  { title: "Hide on Scroll", type: "Settings", href: "/user#setting-hide-on-scroll", excerpt: "Hide the Pill while you scroll down a page, or keep it put." },
  { title: "Quick Actions", type: "Settings", href: "/user#setting-quick-actions", excerpt: "Paste and Go and other actions above the Pill." },
  { title: "Inline Answers", type: "Settings", href: "/user#setting-inline-answers", excerpt: "Math, units, time and more, answered as you type." },
  { title: "Shortcut Suggestions", type: "Settings", href: "/user#setting-shortcut-suggestions", excerpt: "Your !shortcuts, listed as you type one." },
  { title: "Recent Searches", type: "Settings", href: "/user#setting-recent-searches", excerpt: "Your past searches above the Pill." },
  { title: "Favorites", type: "Settings", href: "/user#setting-favorites", excerpt: "Your favorites at the bottom of the home page." },
  { title: "Picture-in-Picture", type: "Settings", href: "/user#setting-picture-in-picture", excerpt: "Fullscreen videos keep playing in a small window." },
  { title: "Background Playback", type: "Settings", href: "/user#setting-background-playback", excerpt: "Audio and video keep playing when you leave mi." },
  { title: "Swipe Left, Swipe Right, Swipe Up", type: "Settings", href: "/user#setting-swipe-left,-swipe-right,-swipe-up", excerpt: "Choose which page each switcher swipe opens." },
  { title: "Long Press to Search", type: "Settings", href: "/user#setting-long-press-to-search", excerpt: "Hold the switcher's Pill to search your tabs." },
  { title: "Clear Recently Closed", type: "Settings", href: "/user#setting-clear-recently-closed", excerpt: "Forget the tabs you've closed." },
  { title: "Report a Bug or Suggest a Feature", type: "Settings", href: "/user#setting-report-a-bug-or-suggest-a-feature", excerpt: "Open a new issue on GitHub." },
  { title: "Logo", type: "Settings", href: "/user#setting-logo", excerpt: "None, Static, or Fidget." },
  { title: "Clock", type: "Settings", href: "/user#setting-clock", excerpt: "None, 12h, or 24h." },
  { title: "Date", type: "Settings", href: "/user#setting-date", excerpt: "None, Above Clock, or Below Clock." },
  { title: "Search Engine", type: "Settings", href: "/user#setting-search-engine", excerpt: "Google, Bing, DuckDuckGo, Brave, Ecosia, Yahoo, or your own." },
  { title: "Search Suggestions", type: "Settings", href: "/user#setting-search-suggestions", excerpt: "Suggestions from your search engine while typing." },
  { title: "Suggestions in Incognito", type: "Settings", href: "/user#setting-suggestions-in-incognito", excerpt: "Whether incognito tabs get search suggestions." },
  { title: "Search Shortcuts", type: "Settings", href: "/user#setting-search-shortcuts", excerpt: "!w, !yt, !se and your own: search one site directly." },
  { title: "Page Text Size", type: "Settings", href: "/user#setting-page-text-size", excerpt: "Text size on websites." },
  { title: "Darken Web Pages", type: "Settings", href: "/user#setting-darken-web-pages", excerpt: "Dark mode for sites without one." },
  { title: "Block Video Autoplay", type: "Settings", href: "/user#setting-block-video-autoplay", excerpt: "Videos wait for a tap." },
  { title: "Desktop Site by Default", type: "Settings", href: "/user#setting-desktop-site-by-default", excerpt: "Request desktop websites in every tab." },
  { title: "Reader Mode by Default", type: "Settings", href: "/user#setting-reader-mode-by-default", excerpt: "Simplify every page for reading." },
  { title: "Pull to Refresh", type: "Settings", href: "/user#setting-pull-to-refresh", excerpt: "Pull down at the very top of a page to reload." },
  { title: "On Startup", type: "Settings", href: "/user#setting-on-startup", excerpt: "Start with a New Tab or Continue your last session." },
  { title: "Site Icons on Tabs", type: "Settings", href: "/user#setting-site-icons-on-tabs", excerpt: "Website icons in the tab switcher." },
  { title: "Site Icons on Bookmarks", type: "Settings", href: "/user#setting-site-icons-on-bookmarks", excerpt: "Website icons in your bookmarks." },
  { title: "Import Bookmarks", type: "Settings", href: "/user#setting-import-bookmarks", excerpt: "From Chrome, Firefox, Edge, Safari or mi." },
  { title: "Export Bookmarks", type: "Settings", href: "/user#setting-export-bookmarks", excerpt: "Save bookmarks to a file any browser can import." },
  { title: "Group History By", type: "Settings", href: "/user#setting-group-history-by", excerpt: "Organize history by Time or by Site." },
  { title: "Clear History", type: "Settings", href: "/user#setting-clear-history", excerpt: "Delete history from the last hour, day, week, month, or all time." },
  { title: "Block Ads", type: "Settings", href: "/user#setting-block-ads", excerpt: "Ads and ad networks (EasyList and a malware and ad domain list)." },
  { title: "Block Trackers", type: "Settings", href: "/user#setting-block-trackers", excerpt: "Analytics and tracking scripts (EasyPrivacy)." },
  { title: "Hide Cookie Banners", type: "Settings", href: "/user#setting-hide-cookie-banners", excerpt: "Cookie-consent pop-ups (EasyList Cookie List)." },
  { title: "Remove Tracking from Links", type: "Settings", href: "/user#setting-remove-tracking-from-links", excerpt: "Strip utm_, fbclid and gclid from links." },
  { title: "Allowed Sites", type: "Settings", href: "/user#setting-allowed-sites", excerpt: "Sites where blocking is off." },
  { title: "Update Filter Lists", type: "Settings", href: "/user#setting-update-filter-lists", excerpt: "Update the blocking lists now." },
  { title: "Warn Every Time", type: "Settings", href: "/user#setting-warn-every-time", excerpt: "With HTTPS Only, ask before every insecure visit." },
  { title: "HTTPS Only", type: "Settings", href: "/user#setting-https-only", excerpt: "Force secure connections." },
  { title: "Allow Cookies", type: "Settings", href: "/user#setting-allow-cookies", excerpt: "Allow or block websites from storing cookies." },
  { title: "Block Third-Party Cookies", type: "Settings", href: "/user#setting-block-third-party-cookies", excerpt: "Stop cross-site cookies (off by default)." },
  { title: "JavaScript", type: "Settings", href: "/user#setting-javascript", excerpt: "Turn JavaScript on or off." },
  { title: "Pop-ups", type: "Settings", href: "/user#setting-pop-ups", excerpt: "Pop-ups open on a tap; allow sites that open them automatically." },
  { title: "Not Secure Marker", type: "Settings", href: "/user#setting-not-secure-marker", excerpt: "Shown for pages without an encrypted connection." },
  { title: "Download With", type: "Settings", href: "/user#setting-download-with", excerpt: "Download with mi or Android's download manager." },
  { title: "Download Location", type: "Settings", href: "/user#setting-download-location", excerpt: "Save downloads to Downloads or a folder you choose." },
  { title: "Ask Where to Save", type: "Settings", href: "/user#setting-ask-where-to-save", excerpt: "Choose the folder and name for each download." },
  { title: "Clear on Exit", type: "Settings", href: "/user#setting-clear-on-exit", excerpt: "Erase history, site data or tabs when mi closes." },
  { title: "Clear Browsing Data", type: "Settings", href: "/user#setting-clear-browsing-data", excerpt: "Delete cookies, site storage and cache." },
  { title: "Export Settings", type: "Settings", href: "/user#setting-export-settings", excerpt: "Save all settings to a file for backup." },
  { title: "Import Settings", type: "Settings", href: "/user#setting-import-settings", excerpt: "Restore settings from an exported file." },
  { title: "Reset All Settings", type: "Settings", href: "/user#setting-reset-all-settings", excerpt: "Revert all settings to their defaults." },
  { title: "Wipe All Data", type: "Settings", href: "/user#setting-wipe-all-data", excerpt: "Permanently delete all app data." },
  { title: "Default Browser", type: "Settings", href: "/user#setting-default-browser", excerpt: "Make mi the app that opens links." },
  { title: "Keep Recently Closed", type: "Settings", href: "/user#setting-keep-recently-closed", excerpt: "How long closed tabs stay in History: until mi closes, 1 day or 1 week." },
  { title: "Gesture Walkthrough", type: "Settings", href: "/user#setting-gesture-walkthrough", excerpt: "Go through mi's gestures again, hands-on." },
  { title: "Check for Updates", type: "Settings", href: "/user#setting-check-for-updates", excerpt: "Look for a new mi release." },
  { title: "Privacy Policy", type: "Settings", href: "/user#setting-privacy-policy", excerpt: "What mi does (and doesn't do) with your data." },
  { title: "Credits", type: "Settings", href: "/user#setting-credits", excerpt: "Open-source credits and licenses." },
  { title: "Search Widget", type: "User Guide", href: "/user#search-widget", excerpt: "A home-screen search bar with incognito and QR buttons." },
  { title: "Pin Tabs", type: "User Guide", href: "/user#pin-tabs", excerpt: "Pinned tabs can't be swiped away or closed by Close all tabs." },
  { title: "Incognito", type: "User Guide", href: "/user#incognito", excerpt: "Private tabs with their own storage, erased when closed." },
  { title: "Tab Switcher", type: "User Guide", href: "/user#dashboard", excerpt: "Swipe up on the Pill: the page zooms into a recent-apps style view of your tabs." },
  { title: "Recent Searches", type: "User Guide", href: "/user#recent-history", excerpt: "Drag the search handle up to reveal recent searches and favorites." },
  { title: "Recall Button", type: "User Guide", href: "/user#recall-button", excerpt: "Swipe the recall button sideways to pin it left, center, or right." },
  { title: "Tab Management", type: "User Guide", href: "/user#tabs", excerpt: "Visual snapshots, swipe to close, and undo." },
  { title: "Tab Editing", type: "User Guide", href: "/user#tab-editing", excerpt: "Rename tabs for better organization." },
  { title: "Bookmark Management", type: "User Guide", href: "/user#bookmarks", excerpt: "Organize favorite sites with folders and reordering." },
  { title: "Bookmark Editing", type: "User Guide", href: "/user#bookmark-swiping", excerpt: "Swipe to edit or delete bookmarks and folders." },
  { title: "History Management", type: "User Guide", href: "/user#history", excerpt: "Local-only records of your visits with search and bulk clear." },
  { title: "Settings Reference", type: "User Guide", href: "/user#customization", excerpt: "Complete guide to every customization and browsing setting." },
  { title: "Power Tools", type: "User Guide", href: "/user#power-tools", excerpt: "Advanced features like QR Scanner, Reader Mode, and Desktop Site." },
  { title: "Quick Bookmark", type: "User Guide", href: "/user#quick-bookmark", excerpt: "Save pages to your library with a single tap from the menu." },
  { title: "QR Toolbox", type: "User Guide", href: "/user#qr-toolbox", excerpt: "Scan physical codes, upload images, or generate QR links." },
  { title: "Privacy Policy", type: "User Guide", href: "/privacy", excerpt: "mi. collects no personal data. What stays on your device and every connection mi. makes." },
  { title: "Privacy Commitment", type: "User Guide", href: "/user#privacy", excerpt: "Our local-first, zero-tracking browsing philosophy." },

  // Settings (Granular - every setting, matching SettingItem IDs)
  // Colors

  // Interface

  // Pill

  // Home Page

  // Tabs

  // History

  // Bookmarks

  // Browsing

  // Security

  // App Data

  // Dev Guide Sections
  { title: "UI Reference", type: "Dev Guide", href: "/developer#ui-reference", excerpt: "High-fidelity mockups of the app's core screens." },
  { title: "Tech Stack", type: "Architecture", href: "/developer#tech-stack", excerpt: "Kotlin, Jetpack Compose, Material 3 Expressive, and Android WebView." },
  { title: "Project Structure", type: "Architecture", href: "/developer#project-structure", excerpt: "Deep dive into the file and folder organization." },
  { title: "Development Setup", type: "Dev Guide", href: "/developer#setup", excerpt: "Step-by-step guide to cloning and running the project." },
  { title: "Building & Deployment", type: "Dev Guide", href: "/developer#setup", excerpt: "Build debug and release APKs with Gradle." },
];

export function Search() {
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [mounted, setMounted] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (query.trim() === "") {
      setResults([]);
      return;
    }
    const filtered = MOCK_DATA.filter(item =>
      item.title.toLowerCase().includes(query.toLowerCase()) ||
      item.excerpt.toLowerCase().includes(query.toLowerCase())
    );
    setResults(filtered);
  }, [query]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setIsOpen(true);
      }
      if (e.key === "Escape") setIsOpen(false);
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  const handleResultClick = (e: React.MouseEvent, href: string) => {
    setIsOpen(false);

    // Check if we're navigating to an anchor on the current page
    const [path, hash] = href.split('#');
    const currentPath = window.location.pathname;
    const normalizedPath = currentPath.endsWith('/') ? currentPath.slice(0, -1) : currentPath;
    const targetPath = path.endsWith('/') ? path.slice(0, -1) : path;

    if (hash && (normalizedPath === targetPath || targetPath === "")) {
      e.preventDefault();
      const element = document.getElementById(hash);
      if (element) {
        // Use 'start' to ensure we see the top of the section
        element.scrollIntoView({ behavior: 'smooth', block: 'start' });

        // Add highlight effect
        element.classList.remove('highlight-section');
        // Trigger reflow
        void element.offsetWidth;
        element.classList.add('highlight-section');

        // Update URL without jump
        window.history.pushState(null, '', href);
      }
    }
  };

  const ModalContent = (
    <div className="fixed inset-0 z-[9999] bg-black/90 backdrop-blur-md p-4 sm:p-4 flex items-start justify-center overflow-hidden">
      <div
        ref={searchRef}
        className="w-full max-w-2xl bg-[#0a0a0a] rounded-3xl shadow-2xl border border-white/10 overflow-hidden mt-12 sm:mt-20 flex flex-col max-h-[70vh] sm:max-h-[80vh]"
      >
        <div className="flex items-center p-4 sm:p-6 border-b border-white/5 shrink-0">
          <SearchIcon className="text-white/40 mr-3 sm:mr-4" size={20} />
          <input
            autoFocus
            type="text"
            placeholder="Search..."
            className="flex-1 bg-transparent border-none outline-none text-lg sm:text-xl text-white font-medium placeholder:text-white/20"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <button
            onClick={() => setIsOpen(false)}
            className="p-2 hover:bg-white/5 rounded-xl transition-colors ml-2"
          >
            <X size={20} className="text-white/40" />
          </button>
        </div>
        <div className="p-2 overflow-y-auto flex-1">
          {results.length > 0 ? (
            <div className="space-y-1">
              {results.map((result, idx) => (
                <Link
                  key={idx}
                  href={result.href}
                  onClick={(e) => handleResultClick(e, result.href)}
                  className="flex items-start gap-4 p-4 rounded-2xl hover:bg-white/5 transition-colors group"
                >
                  <div className="mt-1 p-2 rounded-lg bg-blue-500/10 text-blue-400 group-hover:bg-blue-500 group-hover:text-white transition-colors">
                    {result.type === "Settings" ? <Settings2 size={18} /> :
                      result.type === "Architecture" ? <Code2 size={18} /> :
                        result.title === "Privacy Commitment" ? <Shield size={18} /> :
                          result.type === "User Guide" ? <User size={18} /> : <FileText size={18} />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-bold text-white truncate">{result.title}</span>
                      <span className="shrink-0 text-[9px] font-bold uppercase tracking-widest text-white/30 px-1.5 py-0.5 rounded-md border border-white/5">
                        {result.type}
                      </span>
                    </div>
                    <p className="text-xs sm:text-sm text-white/50 line-clamp-2">{result.excerpt}</p>
                  </div>
                </Link>
              ))}
            </div>
          ) : query ? (
            <div className="p-12 text-center text-white/20">
              <p className="text-lg font-medium">No results found for &quot;{query}&quot;</p>
            </div>
          ) : (
            <div className="p-12 text-center text-white/20">
              <p className="text-sm font-medium">Try searching for &quot;Gestures&quot; or &quot;Tech Stack&quot;</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        className="flex items-center justify-center gap-1 sm:gap-2 px-2 sm:px-4 py-2 rounded-xl bg-white/5 border border-white/5 text-white/40 hover:bg-white/10 transition-all text-xs sm:text-sm font-semibold w-full sm:max-w-sm"
      >
        <SearchIcon size={14} className="sm:size-4" />
        <span className="text-center">Search</span>
        <kbd className="hidden sm:inline-flex h-5 select-none items-center gap-1 rounded border border-white/10 bg-white/5 px-1.5 font-mono text-[10px] font-bold opacity-100">
          <span className="text-xs">⌘</span>K
        </kbd>
      </button>

      {isOpen && mounted && createPortal(ModalContent, document.body)}
    </>
  );
}