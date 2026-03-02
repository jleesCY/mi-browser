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
  { title: "The Dashboard", type: "User Guide", href: "/user#dashboard", excerpt: "Instant access to Tabs, Bookmarks, History, and Settings via swipe-up." },
  { title: "Recent History", type: "User Guide", href: "/user#recent-history", excerpt: "Drag the search handle up to reveal recent searches and visits." },
  { title: "Tab Management", type: "User Guide", href: "/user#tabs", excerpt: "Visual snapshots, reordering, and clearing browser tabs." },
  { title: "Tab Editing", type: "User Guide", href: "/user#tab-editing", excerpt: "Rename tabs and change their icons for better organization." },
  { title: "Bookmark Management", type: "User Guide", href: "/user#bookmarks", excerpt: "Organize favorite sites with folders and reordering." },
  { title: "Bookmark Editing", type: "User Guide", href: "/user#bookmark-swiping", excerpt: "Swipe to edit or delete bookmarks and folders." },
  { title: "History Management", type: "User Guide", href: "/user#history", excerpt: "Local-only records of your visits with search and bulk clear." },
  { title: "Settings Reference", type: "User Guide", href: "/user#customization", excerpt: "Complete guide to every customization and browsing setting." },
  { title: "Power Tools", type: "User Guide", href: "/user#power-tools", excerpt: "Advanced features like QR Scanner, Reader Mode, and Desktop Site." },
  { title: "Home Button", type: "User Guide", href: "/user#home-button", excerpt: "Instantly return to the home screen from any page." },
  { title: "Quick Bookmark", type: "User Guide", href: "/user#quick-bookmark", excerpt: "Save pages to your library with a single tap from the menu." },
  { title: "QR Toolbox", type: "User Guide", href: "/user#qr-toolbox", excerpt: "Scan physical codes, upload images, or generate QR links." },
  { title: "Privacy Commitment", type: "User Guide", href: "/user#privacy", excerpt: "Our local-first, zero-tracking browsing philosophy." },

  // Settings (Granular - every setting, matching SettingItem IDs)
  // Colors
  { title: "Theme Mode", type: "Settings", href: "/user#setting-theme", excerpt: "Switch between Light, Dark, or Adaptive UI themes." },
  { title: "Accent Color", type: "Settings", href: "/user#setting-accent-color", excerpt: "Pick from 18 hand-selected colors for buttons and highlights." },

  // Interface
  { title: "Font Size", type: "Settings", href: "/user#setting-font-size", excerpt: "Adjust the global font scale from 80% to 120%." },
  { title: "Font Weight", type: "Settings", href: "/user#setting-font-weight", excerpt: "Choose between Light, Normal, or Bold text weight." },
  { title: "Corner Radius", type: "Settings", href: "/user#setting-corners", excerpt: "Adjust the roundness of UI elements: Square, Semi-Round, or Round." },
  { title: "UI Spacing", type: "Settings", href: "/user#setting-spacing", excerpt: "Change UI density between Compact, Normal, and Airy." },
  { title: "Status Bar", type: "Settings", href: "/user#setting-status-bar", excerpt: "Toggle the visibility of the system status bar." },
  { title: "Expand Menus", type: "Settings", href: "/user#setting-expand-menus", excerpt: "Open menus to full-screen height immediately." },

  // Pill
  { title: "Pill Size", type: "Settings", href: "/user#setting-size", excerpt: "Choose between Thin, Normal, or Tall pill height." },
  { title: "Loading Bar", type: "Settings", href: "/user#setting-loading-bar", excerpt: "Choose between Standard, Center Out, or Hidden progress bars." },
  { title: "Pin Favorites", type: "Settings", href: "/user#setting-pin-favorites", excerpt: "Keep the Favorites Bar visible when the Pill is focused." },
  { title: "Expand Searches", type: "Settings", href: "/user#setting-expand-searches", excerpt: "Auto-expand recent searches when opening the Pill." },
  { title: "Reorder Icons", type: "Settings", href: "/user#setting-reorder-icons", excerpt: "Drag and drop Dashboard menu bar icons into your preferred order." },

  // Home Page
  { title: "Center Logo", type: "Settings", href: "/user#setting-center-logo", excerpt: "Choose the home page logo: None, Static, or Fidget." },
  { title: "Clock", type: "Settings", href: "/user#setting-clock", excerpt: "Select the home page clock format: None, 12h, or 24h." },
  { title: "Date", type: "Settings", href: "/user#setting-date", excerpt: "Choose date placement: None, Above Clock, or Below Clock." },
  { title: "Weather", type: "Settings", href: "/user#setting-weather", excerpt: "Weather widget: None, Simple, Detailed, or Hourly." },
  { title: "Shortcut", type: "Settings", href: "/user#setting-shortcut", excerpt: "Toggle the home page shortcut button and choose its action." },
  { title: "Background Image", type: "Settings", href: "/user#setting-background-image", excerpt: "Set a custom home page background with a darkness overlay." },

  // Tabs
  { title: "Tab View Style", type: "Settings", href: "/user#setting-style", excerpt: "Toggle between Rows (dense list) or Cards (visual grid)." },
  { title: "Tab Site Logo", type: "Settings", href: "/user#tabs", excerpt: "Toggle favicons in the tab switcher list." },
  { title: "Preview Content", type: "Settings", href: "/user#setting-preview-content", excerpt: "Show visual website snapshots in Card view." },
  { title: "Background Refresh", type: "Settings", href: "/user#setting-background-refresh", excerpt: "Keep all tabs alive on startup for instant switching." },

  // History
  { title: "Group By", type: "Settings", href: "/user#setting-group-by", excerpt: "Organize history by Time or by Site." },
  { title: "History Load Count", type: "Settings", href: "/user#setting-load-count", excerpt: "Set how many history items to load at once (10-100)." },
  { title: "Clear History", type: "Settings", href: "/user#setting-clear-history", excerpt: "Delete browsing data from the last hour, day, or all time." },

  // Bookmarks
  { title: "Bookmark Site Logo", type: "Settings", href: "/user#bookmarks", excerpt: "Toggle favicons in the bookmarks list." },

  // Browsing
  { title: "Search Engine", type: "Settings", href: "/user#setting-search-engine", excerpt: "Set default provider: Google, DuckDuckGo, Bing, Brave, Ecosia, or Yahoo." },
  { title: "On Startup", type: "Settings", href: "/user#setting-on-startup", excerpt: "Start with a New Tab or Continue your last session." },

  // Security
  { title: "Enable JavaScript", type: "Settings", href: "/user#setting-enable-javascript", excerpt: "Toggle JS execution for enhanced privacy or performance." },
  { title: "Enable Cookies", type: "Settings", href: "/user#setting-enable-cookies", excerpt: "Allow or block websites from storing cookies." },
  { title: "HTTPS Only", type: "Settings", href: "/user#setting-https-only", excerpt: "Force secure connections for all browsing sessions." },

  // App Data
  { title: "Export Settings", type: "Settings", href: "/user#setting-export-settings", excerpt: "Save all settings to a shareable file for backup." },
  { title: "Import Settings", type: "Settings", href: "/user#setting-import-settings", excerpt: "Restore settings from a previously exported file." },
  { title: "Reset All Settings", type: "Settings", href: "/user#setting-reset-all-settings", excerpt: "Revert all settings to factory defaults." },
  { title: "Wipe All Data", type: "Settings", href: "/user#setting-wipe-all-data", excerpt: "Permanently delete all app data including bookmarks and history." },

  // Dev Guide Sections
  { title: "UI Reference", type: "Dev Guide", href: "/developer#ui-reference", excerpt: "High-fidelity mockups of the app's core screens." },
  { title: "Tech Stack", type: "Architecture", href: "/developer#tech-stack", excerpt: "React Native, Expo, Reanimated, and WebView core." },
  { title: "Project Structure", type: "Architecture", href: "/developer#project-structure", excerpt: "Deep dive into the file and folder organization." },
  { title: "Development Setup", type: "Dev Guide", href: "/developer#setup", excerpt: "Step-by-step guide to cloning and running the project." },
  { title: "Building & Deployment", type: "Dev Guide", href: "/developer#setup", excerpt: "Instructions for EAS builds and APK generation." },
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