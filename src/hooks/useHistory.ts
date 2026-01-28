import { useState, useEffect } from 'react';
import { LayoutAnimation } from 'react-native';
import { loadStorage, saveStorage, getDisplayHost, getHistoryTitle, generateId } from '../utils';
import { HistoryItem } from '../types';

export const useHistory = (isAppReady: boolean) => {
  const [history, setHistory] = useState<HistoryItem[]>([]);

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
             // If we generated a new ID, we don't add it to seenIds immediately 
             // (unless we want to track the *new* ones, which are guaranteed unique by generateId logic hopefully)
             // But simpler: just assign new ID.
             return { ...item, id: newId };
          }
          seenIds.add(item.id);
          return item;
        });
        
        setHistory(validHistory);
      }
    };
    loadHistory();
  }, []);

  useEffect(() => {
    if (!isAppReady) return;

    const saveTimeout = setTimeout(() => {
      saveStorage("history", history);
    }, 1000);

    return () => clearTimeout(saveTimeout);
  }, [history, isAppReady]);

  const addToHistory = (url: string, title?: string | null) => {
    if (!url || url === "about:blank") return;

    const finalTitle = getHistoryTitle(url, title);

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
        timestamp: Date.now(),
      };

      return [newItem, ...cleanedHistory].slice(0, 1000);
    });
  };

  const deleteHistory = (milliseconds: number) => {
    if (milliseconds === -1) setHistory([]);
    else {
      const cutoff = Date.now() - milliseconds;
      setHistory((prev) => prev.filter((item) => item.timestamp < cutoff));
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

  return {
    history,
    addToHistory,
    deleteHistory,
    deleteHistoryItem
  };
};
