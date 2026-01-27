"use client";

import Link from "next/link";

const getAssetPath = (path: string) => {
  const isProd = process.env.NODE_ENV === 'production';
  const repoName = process.env.NEXT_PUBLIC_REPO_NAME || "mi-browser";
  const basePath = isProd ? `/${repoName}` : "";
  return `${basePath}${path}`;
};

const GITHUB_URL = "https://github.com/jleescy/mi-browser";

export function Footer() {
  return (
    <footer className="py-20 border-t border-black/5 dark:border-white/5">
      <div className="max-w-6xl mx-auto px-6 flex flex-col md:flex-row justify-between items-center gap-8 md:gap-12">
        <div className="flex items-center gap-3">
          <img src={getAssetPath("/icon.png")} alt="mi. logo" className="w-8 h-8 rounded-xl opacity-80" />
          <span className="font-bold text-lg opacity-80 tracking-tight">mi. Browser</span>
        </div>
        <div className="flex flex-wrap justify-center gap-6 md:gap-10 text-xs sm:text-sm font-bold text-black/40 dark:text-white/40 uppercase tracking-widest">
          <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors">GitHub</a>
          <Link href="/user" className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors">User Guide</Link>
          <Link href="/developer" className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors">Dev Guide</Link>
          <a href="#" className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors">Privacy</a>
        </div>
        <p className="text-sm text-black/30 dark:text-white/30 font-medium">© 2026 mi. Open Source Project</p>
      </div>
    </footer>
  );
}
