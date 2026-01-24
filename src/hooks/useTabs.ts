import { useState, useEffect, useRef } from 'react';
import { Linking } from 'react-native';
import * as FileSystem from 'expo-file-system/legacy';
import { loadStorage, saveStorage, getDisplayHost, parseDeepLinkUrl } from '../utils';
import { TabItem } from '../types';
import { useTabState } from './useTabState';

interface UseTabsProps {
  areSettingsLoaded: boolean;
  startupTabMode: "new" | "last";
  backgroundRefresh: boolean;
}

export const useTabs = ({ areSettingsLoaded, startupTabMode, backgroundRefresh }: UseTabsProps) => {
  // --- STATE MANAGEMENT ---
  const regular = useTabState(
    [{ id: "1", url: null, title: "New Tab", showLogo: true }], 
    "1",
    () => ({
        id: Date.now().toString(),
        url: null,
        requestedUrl: null,
        initialUrl: null,
        title: "New Tab",
        showLogo: true,
        hasLoadedOnce: true,
        historyStack: [],
        currentIndex: -1
    })
  );
  
  const incognito = useTabState([], null, () => ({
        id: Date.now().toString(),
        url: null,
        requestedUrl: null,
        initialUrl: null,
        title: "Incognito Tab",
        showLogo: true,
        hasLoadedOnce: true,
        historyStack: [],
        currentIndex: -1
  }));

  const [isIncognito, setIsIncognito] = useState(false);

  // --- SHARED UI STATE (Derived or Synced) ---
  const [activeUrl, setActiveUrl] = useState<string | null>(null);
  const [inputUrl, setInputUrl] = useState("");
  const [areTabsLoaded, setAreTabsLoaded] = useState(false);
  const hasLoadedTabs = useRef(false);

  // Helpers to get current active set
  const currentManager = isIncognito ? incognito : regular;
  const tabs = currentManager.tabs;
  const activeTabId = currentManager.activeTabId || "";
  
  // Ref to track active ID for async operations/callbacks
  const activeTabIdRef = useRef(activeTabId);

  // Update activeTabIdRef whenever activeTabId changes
  useEffect(() => {
    activeTabIdRef.current = activeTabId;
  }, [activeTabId]);

  // Sync activeUrl and inputUrl when switching modes or active tab changes
  useEffect(() => {
    // We look at the "current" manager state
    const tab = currentManager.tabs.find(t => t.id === currentManager.activeTabId);

    if (tab) {
        setActiveUrl(tab.url);
        setInputUrl(tab.url ? getDisplayHost(tab.url) : "");
    } else {
        // Fallback if no tab (shouldn't happen often)
        setActiveUrl(null);
        setInputUrl("");
    }
  }, [isIncognito, currentManager.activeTabId, currentManager.tabs]);


  // Load Tabs on Startup (Regular Only)
  useEffect(() => {
    if (!areSettingsLoaded || hasLoadedTabs.current) return;
    hasLoadedTabs.current = true;

    const loadTabs = async () => {
      const rawTabs = await loadStorage("tabs");
      const savedTabs = Array.isArray(rawTabs) ? rawTabs : [];
      const savedActiveTabId = await loadStorage("activeTabId");

      const existingTabs = savedTabs
        .filter((t: any) => t && typeof t === 'object')
        .map((t: any) => ({
          ...t,
          initialUrl: t.initialUrl || t.url,
          requestedUrl: t.url, // Reset requestedUrl to last known url on startup
          hasLoadedOnce: backgroundRefresh,
          historyStack: t.historyStack || (t.url ? [t.url] : []),
          currentIndex: t.currentIndex !== undefined ? t.currentIndex : (t.url ? 0 : -1)
        }));

      const initialUrl = await Linking.getInitialURL();

      if (initialUrl) {
        const targetUrl = parseDeepLinkUrl(initialUrl);

        if (targetUrl) {
          const newId = Date.now().toString();
          const startupTab = {
              id: newId,
              url: targetUrl,
              requestedUrl: targetUrl,
              initialUrl: targetUrl,
              title: "External Link",
              showLogo: false,
              hasLoadedOnce: true,
              historyStack: [targetUrl],
              currentIndex: 0
          };

          regular.resetTabs([startupTab, ...existingTabs], newId);
        } else {
           if (startupTabMode === "last" && existingTabs.length > 0) {
             let targetTab = existingTabs.find((t: any) => t.id === savedActiveTabId);
             if (!targetTab) targetTab = existingTabs[0];

             const finalTabs = existingTabs.map((t: any) => 
               t.id === targetTab.id ? { ...t, hasLoadedOnce: true } : t
             );
             
             regular.resetTabs(finalTabs, targetTab.id);
           }
        }
      } else if (startupTabMode === "last" && existingTabs.length > 0) {
        let targetTab = existingTabs.find(
          (t: any) => t.id === savedActiveTabId
        );
        if (!targetTab) {
          targetTab = existingTabs.find((t: any) => t.url) || existingTabs[0];
        }

        const finalTabs = existingTabs.map((t: any) => 
            t.id === targetTab.id ? { ...t, hasLoadedOnce: true } : t
        );

        regular.resetTabs(finalTabs, targetTab.id);

      } else {
        // CASE: New
        // Check if we already have a blank tab in the saved list to reuse
        const existingBlankTab = existingTabs.find((t: any) => !t.url);

        if (existingBlankTab) {
          const finalTabs = existingTabs.map((t: any) => 
             t.id === existingBlankTab.id ? { ...t, hasLoadedOnce: true } : t
          );

          regular.resetTabs(finalTabs, existingBlankTab.id);
        } else {
          const newTabId = Date.now().toString();
          const newTab = {
            id: newTabId,
            url: null,
            initialUrl: null,
            title: "New Tab",
            showLogo: true,
            hasLoadedOnce: true,
            historyStack: [],
            currentIndex: -1
          };

          regular.resetTabs([newTab, ...existingTabs], newTabId);
        }
      }
      setAreTabsLoaded(true);
    };

    loadTabs();
  }, [areSettingsLoaded, startupTabMode, backgroundRefresh]); 

  // Save Tabs (Regular Only)
  useEffect(() => {
    if (!areTabsLoaded) return;

    const saveTimeout = setTimeout(() => {
      const cleanTabs = regular.tabs.map(
        ({ loading, canGoBack, canGoForward, hasLoadedOnce, ...rest }) => rest
      );
      saveStorage("tabs", cleanTabs);
      saveStorage("activeTabId", regular.activeTabId);
    }, 500); 

    return () => clearTimeout(saveTimeout);
  }, [regular.tabs, regular.activeTabId, areTabsLoaded]);

  // Mark active tab as loaded
  useEffect(() => {
    // When active tab changes in CURRENT mode, mark it as loaded
    if (currentManager.activeTabId) {
        currentManager.setTabs((prev) => 
            prev.map((t) => {
              if (t.id === currentManager.activeTabId && !t.hasLoadedOnce) {
                return { ...t, hasLoadedOnce: true };
              }
              return t;
            })
        );
    }
  }, [currentManager.activeTabId, isIncognito]);

  const toggleIncognitoMode = () => {
      if (!isIncognito) {
          // Switching TO Incognito
          // If no incognito tabs exist, create one
          if (incognito.tabs.length === 0) {
            const newId = Date.now().toString();
            const newTab = {
                id: newId,
                url: null,
                requestedUrl: null,
                initialUrl: null,
                title: "Incognito Tab",
                showLogo: true,
                hasLoadedOnce: true,
                historyStack: [],
                currentIndex: -1
            };
            incognito.addTab(newTab);
          }
      } else {
          // Switching OFF Incognito (Exit)
          // Clear all incognito tabs to ensure fresh session next time
          
          // Delete preview images
          incognito.tabs.forEach(tab => {
              if (tab.previewImage) {
                  FileSystem.deleteAsync(tab.previewImage, { idempotent: true }).catch(() => {});
              }
          });

          setTimeout(() => {
            incognito.resetTabs([], null);
          }, 500); 
          incognito.resetTabs([], null);
      }
      setIsIncognito(!isIncognito);
  };

  const addNewTab = (overrideUrl?: string) => {
    const newId = Date.now().toString();
    const newTab = {
      id: newId,
      url: overrideUrl || null,
      requestedUrl: overrideUrl || null,
      initialUrl: overrideUrl || null,
      title: isIncognito ? "Incognito Tab" : "New Tab",
      showLogo: true,
      hasLoadedOnce: true,
      historyStack: overrideUrl ? [overrideUrl] : [],
      currentIndex: overrideUrl ? 0 : -1
    };

    currentManager.addTab(newTab);
  };

  const deleteTab = (id: string) => currentManager.deleteTab(id);
  const updateTab = (id: string, updates: Partial<TabItem>) => currentManager.updateTab(id, updates);
  const reorderTabs = (from: number, to: number) => currentManager.reorderTabs(from, to);
  
  // Wrapper for setActiveTabId to delegate
  const setActiveTabId = (id: string) => currentManager.setActiveTabId(id);
  // Wrapper for setTabs to delegate
  const setTabsWrapper = (action: any) => currentManager.setTabs(action);

  return {
    tabs, 
    setTabs: setTabsWrapper,
    activeTabId, setActiveTabId,
    activeUrl, setActiveUrl,
    inputUrl, setInputUrl,
    areTabsLoaded,
    addNewTab,
    deleteTab,
    updateTab,
    reorderTabs,
    activeTabIdRef,
    isIncognito, toggleIncognitoMode
  };
};

