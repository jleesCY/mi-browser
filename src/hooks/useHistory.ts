import { useEffect, useState } from 'react';
import { LayoutAnimation } from 'react-native';
import { HistoryItem } from '../types';
import { generateId, getHistoryTitle, getSearchQueryFromUrl, loadStorage, saveStorage } from '../utils';

export const useHistory = (isAppReady: boolean) => {
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [recentSearches, setRecentSearches] = useState<HistoryItem[]>([]);

  useEffect(() => {
    const loadHistory = async () => {
      const savedHistory = await loadStorage("history");
      if (Array.isArray(savedHistory)) {
        let validHistory = savedHistory.filter((item: any) => item && typeof item === 'object' && item.url);

        // Ensure unique IDs to fix any existing corruption
        const seenIds = new Set();
        validHistory = validHistory.map((item: any) => {
          if (!item.id || seenIds.has(item.id)) {
            const newId = generateId();
            return { ...item, id: newId };
          }
          seenIds.add(item.id);
          return item;
        });

        setHistory(validHistory);
      }

      const savedRecent = await loadStorage("recent_searches");
      if (Array.isArray(savedRecent)) {
        // Fix potential ID corruption in recent searches
        const seenRecentIds = new Set();
        const validRecent = savedRecent.map((item: any) => {
          if (!item.id || seenRecentIds.has(item.id)) {
            return { ...item, id: generateId() };
          }
          seenRecentIds.add(item.id);
          return item;
        });
        setRecentSearches(validRecent);
      }
    };
    loadHistory();
  }, []);

  useEffect(() => {
    if (!isAppReady) return;

    const saveTimeout = setTimeout(() => {
      const cleanHistory = history.map(item => ({
        url: item.url,
        title: item.title,
        timestamp: item.timestamp
        // id is transient, generated on load
      }));
      saveStorage("history", cleanHistory);
    }, 1000);

    return () => clearTimeout(saveTimeout);
  }, [history, isAppReady]);

  useEffect(() => {
    if (!isAppReady) return;
    const cleanRecent = recentSearches.map(item => ({
      url: item.url,
      title: item.title,
      timestamp: item.timestamp
    }));
    saveStorage("recent_searches", cleanRecent);
  }, [recentSearches, isAppReady]);

  const addToHistory = (url: string, title?: string | null) => {
    if (!url || url === "about:blank") return;

    const finalTitle = getHistoryTitle(url, title);
    const timestamp = Date.now();
    const searchQuery = getSearchQueryFromUrl(url);

    // 1. Update General History
    setHistory((prevHistory) => {
      if (prevHistory.length > 0 && prevHistory[0].url === url) {
        return prevHistory;
      }

      const cleanedHistory = prevHistory.filter(
        (item) => item.url.replace(/\/$/, "") !== url.replace(/\/$/, "")
      );

      const newItem = {
        id: generateId(),
        url,
        title: finalTitle,
        timestamp,
      };

      return [newItem, ...cleanedHistory].slice(0, 1000);
    });

    // 2. Update Recent Searches (if it's a search)
    if (searchQuery) {
      setRecentSearches((prev) => {
        // Remove existing identical search queries to avoid duplicates
        // We compare the actual query string, not just the URL
        const filtered = prev.filter(item => {
          const itemQuery = getSearchQueryFromUrl(item.url);
          return itemQuery !== searchQuery;
        });

        const newSearchItem = {
          id: generateId(),
          url,
          title: searchQuery, // Store the query as title for easier display
          timestamp,
        };

        return [newSearchItem, ...filtered].slice(0, 20);
      });
    }
  };

  const deleteHistory = (milliseconds: number) => {
    if (milliseconds === -1) {
      setHistory([]);
      setRecentSearches([]); // Clear recent searches too
    } else {
      const cutoff = Date.now() - milliseconds;
      setHistory((prev) => prev.filter((item) => item.timestamp < cutoff));
      setRecentSearches((prev) => prev.filter((item) => item.timestamp < cutoff));
    }
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
  };

  const deleteHistoryItem = (idToDelete: string) => {
    setHistory((prevHistory) => {
      const newHistory = prevHistory.filter((item) => item.id !== idToDelete);
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
      return newHistory;
    });
  };

  const deleteRecentSearch = (idToDelete: string) => {
    setRecentSearches((prev) => {
      const newRecent = prev.filter((item) => item.id !== idToDelete);
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
      return newRecent;
    });
  };

  const replaceHistory = (newHistory: HistoryItem[]) => {
    setHistory(newHistory);
  };

  return {
    history,
    recentSearches,
    addToHistory,
    deleteHistory,
    deleteHistoryItem,
    deleteRecentSearch,
    replaceHistory
  };
};
