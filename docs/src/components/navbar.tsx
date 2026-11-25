"use client";

import { Github, Menu, X as CloseIcon } from "lucide-react";
import { Search } from "./search";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { getAssetPath, GITHUB_URL } from "@/site";



export function Navbar() {
  const pathname = usePathname();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const isHome = pathname === "/" || pathname === "/mi-browser/";
  const isDocsPage = pathname.includes('/user') || pathname.includes('/developer');

  return (
    <nav className="fixed top-0 w-full z-50 h-16">
      {/* Background with blur */}
      <div className="absolute inset-0 bg-surface-container-low/80 backdrop-blur-md border-b border-outline-variant -z-10" />
      
      <div className="max-w-6xl mx-auto px-4 h-full flex items-center relative">
        {/* Logo and Search Container */}
        <div className="flex-1 flex items-center gap-4 min-w-0">
          <Link href="/" className="flex items-center gap-2 hover:opacity-80 transition-opacity shrink-0">
            <img src={getAssetPath("/icon.png")} alt="mi. logo" className="w-8 h-8 rounded-lg shadow-lg shadow-primary/20 shrink-0" />
            <span className="font-bold text-lg sm:text-xl tracking-tight text-on-surface hidden sm:block truncate">mi.</span>
          </Link>

          {/* Search bar: Centered on mobile via absolute positioning, left-aligned on desktop via flex */}
          <div className="absolute left-1/2 -translate-x-1/2 w-full max-w-[110px] xs:max-w-[140px] md:relative md:left-0 md:translate-x-0 md:max-w-sm md:w-auto">
            {isDocsPage && <Search />}
          </div>
        </div>

        {/* Right Side: Desktop Nav & Mobile Toggle */}
        <div className="flex items-center gap-4 shrink-0">
          <div className="hidden md:flex items-center gap-6 lg:gap-8 text-sm font-bold text-on-surface-variant uppercase tracking-widest shrink-0">
            <Link href="/user" className={`hover:text-primary transition-colors ${pathname.includes('/user') ? 'text-primary' : ''}`}>
              User Guide
            </Link>
            <Link href="/developer" className={`hover:text-primary transition-colors ${pathname.includes('/developer') ? 'text-primary' : ''}`}>
              Developer
            </Link>
            <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="hover:opacity-60 transition-opacity text-on-surface-variant">
              <Github size={20} />
            </a>
          </div>

          <Link href="/" className="bg-primary hover:bg-primary/90 text-on-primary px-4 py-2 rounded-full text-sm font-semibold shadow-lg shadow-primary/20 hover:scale-105 transition-all hidden lg:block whitespace-nowrap">
            {isHome ? 'Download' : 'Get App'}
          </Link>
          
          {/* Mobile Menu Toggle */}
          <button 
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            className="md:hidden p-2 text-on-surface-variant hover:text-on-surface transition-colors shrink-0"
          >
            {isMobileMenuOpen ? <CloseIcon size={24} /> : <Menu size={24} />}
          </button>
        </div>
      </div>

      {/* Mobile Menu Overlay */}
      {isMobileMenuOpen && (
        <div className="md:hidden absolute top-16 left-0 w-full bg-surface-container-low/95 backdrop-blur-xl border-b border-outline-variant py-8 px-6 flex flex-col gap-6 animate-in slide-in-from-top duration-300">
          <Link 
            href="/user" 
            onClick={() => setIsMobileMenuOpen(false)}
            className={`text-lg font-bold uppercase tracking-widest ${pathname.includes('/user') ? 'text-primary' : 'text-on-surface-variant'}`}
          >
            User Guide
          </Link>
          <Link 
            href="/developer" 
            onClick={() => setIsMobileMenuOpen(false)}
            className={`text-lg font-bold uppercase tracking-widest ${pathname.includes('/developer') ? 'text-primary' : 'text-on-surface-variant'}`}
          >
            Developer
          </Link>
          <div className="h-px bg-surface-container-high w-full" />
          <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 text-on-surface-variant font-bold uppercase tracking-widest">
            <Github size={20} /> GitHub
          </a>
          {!isHome && (
            <Link 
              href="/" 
              onClick={() => setIsMobileMenuOpen(false)}
              className="bg-primary text-on-primary px-6 py-3 rounded-2xl text-center font-bold"
            >
              Get App
            </Link>
          )}
        </div>
      )}
    </nav>
  );
}
