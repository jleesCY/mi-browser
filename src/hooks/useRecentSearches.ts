import { useState, useEffect } from 'react';
import { loadStorage, saveStorage } from '../utils';

export const useRecentSearches = () => {
  const [recentSearches, setRecentSearches] = useState<string[]>([]);

  useEffect(() => {
    loadStorage('recentSearches').then((data) => {
      if (Array.isArray(data)) {
        setRecentSearches(data);
      }
    });
  }, []);

  const addRecentSearch = (text: string) => {
    const trimmed = text.trim();
    if (!trimmed) return;
    
    setRecentSearches((prev) => {
      const filtered = prev.filter((t) => t.toLowerCase() !== trimmed.toLowerCase());
      const next = [trimmed, ...filtered].slice(0, 20); // Keep last 20
      saveStorage('recentSearches', next);
      return next;
    });
  };

  const removeRecentSearch = (text: string) => {
    setRecentSearches((prev) => {
      const next = prev.filter((t) => t !== text);
      saveStorage('recentSearches', next);
      return next;
    });
  };

  const clearRecentSearches = () => {
    setRecentSearches([]);
    saveStorage('recentSearches', []);
  };

  return {
    recentSearches,
    addRecentSearch,
    removeRecentSearch,
    clearRecentSearches
  };
};
