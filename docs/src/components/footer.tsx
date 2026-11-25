"use client";

import Link from "next/link";
import { getAssetPath, GITHUB_URL } from "@/site";



export function Footer() {
  return (
    <footer className="py-20 border-t border-outline-variant">
      <div className="max-w-6xl mx-auto px-6 flex flex-col md:flex-row justify-between items-center gap-8 md:gap-12">
        <div className="flex items-center gap-3">
          <img src={getAssetPath("/icon.png")} alt="mi. logo" className="w-8 h-8 rounded-xl opacity-80" />
          <span className="font-bold text-lg opacity-80 tracking-tight">mi. Browser</span>
        </div>
        <div className="flex flex-wrap justify-center gap-6 md:gap-10 text-xs sm:text-sm font-bold text-on-surface-variant uppercase tracking-widest">
          <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="hover:text-primary transition-colors">GitHub</a>
          <Link href="/user" className="hover:text-primary transition-colors">User Guide</Link>
          <Link href="/developer" className="hover:text-primary transition-colors">Dev Guide</Link>
          <Link href="/privacy" className="hover:text-primary transition-colors">Privacy</Link>
        </div>
        <p className="text-sm text-on-surface-variant/80 font-medium">© 2026 mi. Open Source Project</p>
      </div>
    </footer>
  );
}
