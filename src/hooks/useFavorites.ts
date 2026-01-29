import { useState, useEffect, useCallback } from 'react';
import { loadStorage, saveStorage, getFaviconUrl } from '../utils';

export interface FavoriteItem {
  id: string;
  url: string;
  title: string;
  icon?: string;
}

export const useFavorites = (isAppReady: boolean) => {
  const [favorites, setFavorites] = useState<FavoriteItem[]>([]);

  useEffect(() => {
    const load = async () => {
      const saved = await loadStorage("favorites");
      if (Array.isArray(saved)) {
        setFavorites(saved);
      }
    };
    load();
  }, []);

  useEffect(() => {
    if (!isAppReady) return;
    const saveTimeout = setTimeout(() => {
      saveStorage("favorites", favorites);
    }, 500);
    return () => clearTimeout(saveTimeout);
  }, [favorites, isAppReady]);

  const addFavorite = useCallback((title: string, url: string) => {
    setFavorites(prev => {
      if (prev.length >= 5) return prev; // Max 5
      // Check for duplicates
      if (prev.some(f => f.url === url)) return prev;

      const icon = getFaviconUrl(url) || undefined;
      const newItem: FavoriteItem = {
        id: Date.now().toString(),
        title: title || "Favorite",
        url,
        icon
      };
      return [...prev, newItem];
    });
  }, []);

  const removeFavorite = useCallback((id: string) => {
    setFavorites(prev => prev.filter(f => f.id !== id));
  }, []);

  const updateFavorite = useCallback((id: string, url: string, title?: string) => {
    setFavorites(prev => prev.map(f => {
      if (f.id === id) {
        const icon = getFaviconUrl(url) || undefined;
        return { ...f, url, title: title || f.title, icon };
      }
      return f;
    }));
  }, []);

  const reorderFavorites = useCallback((fromIndex: number, toIndex: number) => {
    setFavorites((prev) => {
      const newList = [...prev];
      const [movedItem] = newList.splice(fromIndex, 1);
      newList.splice(toIndex, 0, movedItem);
      return newList;
    });
  }, []);

  return {
    favorites,
    addFavorite,
    removeFavorite,
    updateFavorite,
    reorderFavorites
  };
};
