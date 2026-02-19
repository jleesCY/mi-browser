import { useCallback, useEffect, useState } from 'react';
import { BookmarkFolder, BookmarkItem, BookmarkNode } from '../types';
import { getFaviconUrl, loadStorage, saveStorage } from '../utils';

export const useBookmarks = (isAppReady: boolean) => {
  const [bookmarks, setBookmarks] = useState<BookmarkNode[]>([]);
  const [hasLoaded, setHasLoaded] = useState(false);

  // Load bookmarks on startup
  useEffect(() => {
    const loadBookmarks = async () => {
      const saved = await loadStorage("bookmarks");
      if (Array.isArray(saved)) {
        setBookmarks(saved);
      } else {
        // Initialize with default empty state if needed, or just []
        setBookmarks([]);
      }
      setHasLoaded(true);
    };
    loadBookmarks();
  }, []);

  // Save bookmarks on change
  useEffect(() => {
    if (!isAppReady || !hasLoaded) return;

    const saveTimeout = setTimeout(() => {
      const cleanBookmarks = bookmarks.map(b => {
        if (b.type === 'folder') {
          return {
            id: b.id,
            type: 'folder',
            title: b.title,
            parentId: b.parentId,
            order: b.order
          };
        }
        return {
          id: b.id,
          type: 'bookmark',
          title: b.title,
          url: (b as any).url,
          parentId: b.parentId,
          order: b.order,
          icon: (b as any).icon
        };
      });
      saveStorage("bookmarks", cleanBookmarks);
    }, 500);

    return () => clearTimeout(saveTimeout);
  }, [bookmarks, isAppReady, hasLoaded]);

  const addBookmark = useCallback((title: string, url: string, parentId: string | null = null) => {
    setBookmarks(prev => {
      const siblings = prev.filter(b => b.parentId === parentId);
      const maxOrder = siblings.length > 0 ? Math.max(...siblings.map(s => s.order)) : -1;

      const icon = getFaviconUrl(url) || undefined;

      const newBookmark: BookmarkItem = {
        id: Date.now().toString(),
        type: "bookmark",
        title: title || url,
        url,
        parentId,
        order: maxOrder + 1,
        icon
      };
      return [...prev, newBookmark];
    });
  }, []);

  const addFolder = useCallback((title: string, parentId: string | null = null) => {
    setBookmarks(prev => {
      const siblings = prev.filter(b => b.parentId === parentId);
      const maxOrder = siblings.length > 0 ? Math.max(...siblings.map(s => s.order)) : -1;

      const newFolder: BookmarkFolder = {
        id: Date.now().toString(),
        type: "folder",
        title,
        parentId,
        order: maxOrder + 1
      };
      return [...prev, newFolder];
    });
  }, []);

  const deleteBookmark = useCallback((id: string) => {
    setBookmarks(prev => {
      // If folder, we might want to delete children too? 
      // Current simple implementation: delete the item. 
      // If it's a folder, children become orphans (or we should delete them).
      // Let's recursively delete children for safety.

      const idsToDelete = new Set<string>();
      const findChildren = (targetId: string) => {
        idsToDelete.add(targetId);
        const children = prev.filter(b => b.parentId === targetId);
        children.forEach(c => findChildren(c.id));
      };

      findChildren(id);

      return prev.filter(b => !idsToDelete.has(b.id));
    });
  }, []);

  const updateBookmark = useCallback((id: string, updates: Partial<BookmarkNode>) => {
    setBookmarks(prev => prev.map(b => {
      if (b.id === id) {
        const updated = { ...b, ...updates } as BookmarkNode;
        // If it is a bookmark and URL changed, update icon
        if (updated.type === 'bookmark' && 'url' in updates && updates.url && updates.url !== (b as BookmarkItem).url) {
          (updated as BookmarkItem).icon = getFaviconUrl(updates.url) || undefined;
        }
        return updated;
      }
      return b;
    }));
  }, []);

  const moveBookmark = useCallback((id: string, newParentId: string | null) => {
    setBookmarks(prev => {
      // calculate new order (append to end of new folder)
      const siblings = prev.filter(b => b.parentId === newParentId);
      const maxOrder = siblings.length > 0 ? Math.max(...siblings.map(s => s.order)) : -1;

      return prev.map(b => b.id === id ? { ...b, parentId: newParentId, order: maxOrder + 1 } : b);
    });
  }, []);

  const reorderBookmarks = useCallback((parentId: string | null, fromIndex: number, toIndex: number) => {
    setBookmarks(prev => {
      const siblings = prev.filter(b => b.parentId === parentId).sort((a, b) => a.order - b.order);
      if (fromIndex < 0 || fromIndex >= siblings.length || toIndex < 0 || toIndex >= siblings.length) return prev;

      const itemMoved = siblings[fromIndex];
      const newSiblings = [...siblings];
      newSiblings.splice(fromIndex, 1);
      newSiblings.splice(toIndex, 0, itemMoved);

      // Re-assign order based on new index
      const reorderedSiblings = newSiblings.map((item, index) => ({
        ...item,
        order: index
      }));

      // Merge back into main list
      // 1. Remove all old siblings
      const otherBookmarks = prev.filter(b => b.parentId !== parentId);
      // 2. Add updated siblings
      return [...otherBookmarks, ...reorderedSiblings];
    });
  }, []);

  const clearBookmarks = useCallback(() => {
    setBookmarks([]);
  }, []);

  const replaceBookmarks = useCallback((newBookmarks: BookmarkNode[]) => {
    setBookmarks(newBookmarks);
    setHasLoaded(true);
  }, []);

  return {
    bookmarks,
    addBookmark,
    addFolder,
    deleteBookmark,
    updateBookmark,
    moveBookmark,
    reorderBookmarks,
    clearBookmarks,
    replaceBookmarks
  };
};
