import { useState, useEffect } from 'react';
import { LayoutAnimation } from 'react-native';
import { loadStorage, saveStorage, getDisplayHost } from '../utils';
import { HistoryItem } from '../types';

export const useHistory = (isAppReady: boolean) => {
  const [history, setHistory] = useState<HistoryItem[]>([]);

  useEffect(() => {
    const loadHistory = async () => {
      const savedHistory = await loadStorage("history");
      if (savedHistory) setHistory(savedHistory);
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

  const addToHistory = (url: string) => {
    if (!url || url === "about:blank") return;

    const title = getDisplayHost(url);

    setHistory((prevHistory) => {
      if (prevHistory.length > 0 && prevHistory[0].url === url) {
        return prevHistory;
      }

      const cleanedHistory = prevHistory.filter(
        (item) => item.url.replace(/\/$/, "") !== url.replace(/\/$/, "")
      );

      const newItem = {
        id: Date.now().toString(),
        url,
        title,
        timestamp: Date.now(),
      };

      return [newItem, ...cleanedHistory].slice(0, 100);
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
