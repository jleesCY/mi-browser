"use client";

import { Github, Menu, X as CloseIcon } from "lucide-react";
import { Search } from "./search";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

const getAssetPath = (path: string) => {
  const isProd = process.env.NODE_ENV === 'production';
  const repoName = process.env.NEXT_PUBLIC_REPO_NAME || "mi-browser";
  const basePath = isProd ? `/${repoName}` : "";
  return `${basePath}${path}`;
};

const GITHUB_URL = "https://github.com/jleescy/mi-browser";

export function Navbar() {
  const pathname = usePathname();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const isHome = pathname === "/" || pathname === "/mi-browser/";
  const isDocsPage = pathname.includes('/user') || pathname.includes('/developer');

  return (
    <nav className="fixed top-0 w-full z-50 h-16">
      {/* Background with blur */}
      <div className="absolute inset-0 bg-black/80 backdrop-blur-md border-b border-white/5 -z-10" />
      
      <div className="max-w-6xl mx-auto px-4 h-full flex items-center relative">
        {/* Logo and Search Container */}
        <div className="flex-1 flex items-center gap-4 min-w-0">
          <Link href="/" className="flex items-center gap-2 hover:opacity-80 transition-opacity shrink-0">
            <img src={getAssetPath("/icon.png")} alt="mi. logo" className="w-8 h-8 rounded-lg shadow-lg shadow-blue-500/20 shrink-0" />
            <span className="font-bold text-lg sm:text-xl tracking-tight bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent hidden sm:block truncate">mi.</span>
          </Link>

          {/* Search bar: Centered on mobile via absolute positioning, left-aligned on desktop via flex */}
          <div className="absolute left-1/2 -translate-x-1/2 w-full max-w-[110px] xs:max-w-[140px] md:relative md:left-0 md:translate-x-0 md:max-w-sm md:w-auto">
            {isDocsPage && <Search />}
          </div>
        </div>

        {/* Right Side: Desktop Nav & Mobile Toggle */}
        <div className="flex items-center gap-4 shrink-0">
          <div className="hidden md:flex items-center gap-6 lg:gap-8 text-sm font-bold text-white/50 uppercase tracking-widest shrink-0">
            <Link href="/user" className={`hover:text-blue-400 transition-colors ${pathname.includes('/user') ? 'text-blue-400' : ''}`}>
              User Guide
            </Link>
            <Link href="/developer" className={`hover:text-blue-400 transition-colors ${pathname.includes('/developer') ? 'text-blue-400' : ''}`}>
              Developer
            </Link>
            <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="hover:opacity-60 transition-opacity text-white/50">
              <Github size={20} />
            </a>
          </div>

          <Link href="/" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-full text-sm font-semibold shadow-lg shadow-blue-500/20 hover:scale-105 transition-all hidden lg:block whitespace-nowrap">
            {isHome ? 'Download' : 'Get App'}
          </Link>
          
          {/* Mobile Menu Toggle */}
          <button 
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            className="md:hidden p-2 text-white/50 hover:text-white transition-colors shrink-0"
          >
            {isMobileMenuOpen ? <CloseIcon size={24} /> : <Menu size={24} />}
          </button>
        </div>
      </div>

      {/* Mobile Menu Overlay */}
      {isMobileMenuOpen && (
        <div className="md:hidden absolute top-16 left-0 w-full bg-black/95 backdrop-blur-xl border-b border-white/5 py-8 px-6 flex flex-col gap-6 animate-in slide-in-from-top duration-300">
          <Link 
            href="/user" 
            onClick={() => setIsMobileMenuOpen(false)}
            className={`text-lg font-bold uppercase tracking-widest ${pathname.includes('/user') ? 'text-blue-400' : 'text-white/50'}`}
          >
            User Guide
          </Link>
          <Link 
            href="/developer" 
            onClick={() => setIsMobileMenuOpen(false)}
            className={`text-lg font-bold uppercase tracking-widest ${pathname.includes('/developer') ? 'text-blue-400' : 'text-white/50'}`}
          >
            Developer
          </Link>
          <div className="h-px bg-white/5 w-full" />
          <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 text-white/50 font-bold uppercase tracking-widest">
            <Github size={20} /> GitHub
          </a>
          {!isHome && (
            <Link 
              href="/" 
              onClick={() => setIsMobileMenuOpen(false)}
              className="bg-blue-600 text-white px-6 py-3 rounded-2xl text-center font-bold"
            >
              Get App
            </Link>
          )}
        </div>
      )}
    </nav>
  );
}
