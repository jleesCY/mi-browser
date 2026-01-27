"use client";

import { useEffect } from 'react';
import { usePathname } from 'next/navigation';

export function useHighlight() {
  const pathname = usePathname();

  useEffect(() => {
    const handleHashHighlight = () => {
      const hash = window.location.hash.replace('#', '');
      if (hash) {
        const element = document.getElementById(hash);
        if (element) {
          // Add a small delay to ensure page is rendered and scroll-margin is respected
          setTimeout(() => {
            element.scrollIntoView({ behavior: 'smooth', block: 'start' });
            element.classList.remove('highlight-section');
            void element.offsetWidth;
            element.classList.add('highlight-section');
          }, 500);
        }
      }
    };

    handleHashHighlight();
    // Also listen for hash changes (e.g. from browser back/forward)
    window.addEventListener('hashchange', handleHashHighlight);
    return () => window.removeEventListener('hashchange', handleHashHighlight);
  }, [pathname]);
}
