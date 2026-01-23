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

          // Use timeout to ensure state updates don't conflict during render cycle if called from UI
          setTimeout(() => {
            setActiveTabId(nextTab.id);
          }, 0);
        } else {
             // Handled by consumer if empty list needs a new tab, 
             // but strictly this hook just manages the list.
             // We can optionally return "wasEmpty" or handle it here?
             // Let's handle "ensure one tab" in the consumer to keep this generic.
             setTimeout(() => {
                setActiveTabId(null);
             }, 0);
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
