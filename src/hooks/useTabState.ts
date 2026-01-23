import { useState } from 'react';
import { LayoutAnimation } from 'react-native';
import { TabItem } from '../types';

export const useTabState = (initialTabs: TabItem[], initialActiveId: string) => {
  const [tabs, setTabs] = useState<TabItem[]>(initialTabs);
  const [activeTabId, setActiveTabId] = useState<string | null>(initialActiveId);
  
  // Helpers
  const addTab = (newTab: TabItem) => {
    setTabs((prev) => [newTab, ...prev]);
    setActiveTabId(newTab.id);
  };

  const deleteTab = (idToDelete: string) => {
    setTabs((prevTabs) => {
      const newTabs = prevTabs.filter((t) => t.id !== idToDelete);
      
      if (activeTabId === idToDelete) {
        if (newTabs.length > 0) {
          const indexToDelete = prevTabs.findIndex((t) => t.id === idToDelete);
          const nextIndex = Math.max(0, indexToDelete - 1);
          const safeIndex = Math.min(nextIndex, newTabs.length - 1);
          const nextTab = newTabs[safeIndex];

          setTimeout(() => {
            setActiveTabId(nextTab.id);
          }, 0);
        } else {
             // Create a new tab if list is empty
             const freshId = Date.now().toString();
             const freshTab: TabItem = { 
                 id: freshId, 
                 url: null, 
                 requestedUrl: null, 
                 title: "New Tab", 
                 showLogo: true, 
                 hasLoadedOnce: true,
                 historyStack: [],
                 currentIndex: -1
             };
             
             setTimeout(() => {
                setActiveTabId(freshId);
             }, 0);
             return [freshTab];
        }
      }

      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
      return newTabs;
    });
  };

  const updateTab = (id: string, updates: Partial<TabItem>) => {
    setTabs(prev => prev.map(t => t.id === id ? { ...t, ...updates } : t));
  };

  const reorderTabs = (fromIndex: number, toIndex: number) => {
    setTabs((prevTabs) => {
      const newTabs = [...prevTabs];
      const [movedTab] = newTabs.splice(fromIndex, 1);
      newTabs.splice(toIndex, 0, movedTab);
      return newTabs;
    });
  };

  const resetTabs = (newTabs: TabItem[], newActiveId: string | null) => {
      setTabs(newTabs);
      setActiveTabId(newActiveId);
  };

  return {
    tabs,
    activeTabId,
    setTabs,
    setActiveTabId,
    addTab,
    deleteTab,
    updateTab,
    reorderTabs,
    resetTabs
  };
};
