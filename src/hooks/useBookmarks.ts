import { useState, useEffect, useCallback } from 'react';
import { loadStorage, saveStorage } from '../utils';
import { BookmarkNode, BookmarkItem, BookmarkFolder } from '../types';

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
      saveStorage("bookmarks", bookmarks);
    }, 500);

    return () => clearTimeout(saveTimeout);
  }, [bookmarks, isAppReady, hasLoaded]);

  const addBookmark = useCallback((title: string, url: string, parentId: string | null = null) => {
    setBookmarks(prev => {
      const siblings = prev.filter(b => b.parentId === parentId);
      const maxOrder = siblings.length > 0 ? Math.max(...siblings.map(s => s.order)) : -1;
      
      const newBookmark: BookmarkItem = {
        id: Date.now().toString(),
        type: "bookmark",
        title: title || url,
        url,
        parentId,
        order: maxOrder + 1
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
    setBookmarks(prev => prev.map(b => b.id === id ? { ...b, ...updates } as BookmarkNode : b));
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

  return {
    bookmarks,
    addBookmark,
    addFolder,
    deleteBookmark,
    updateBookmark,
    moveBookmark,
    reorderBookmarks
  };
};
