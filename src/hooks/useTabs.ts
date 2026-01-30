import { useState, useEffect, useRef } from 'react';
import { Linking } from 'react-native';
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
  
  // --- SHARED UI STATE (Derived or Synced) ---
  const [activeUrl, setActiveUrl] = useState<string | null>(null);
  const [inputUrl, setInputUrl] = useState("");
  const [areTabsLoaded, setAreTabsLoaded] = useState(false);
  const hasLoadedTabs = useRef(false);

  const tabs = regular.tabs;
  const activeTabId = regular.activeTabId || "";
  
  // Ref to track active ID for async operations/callbacks
  const activeTabIdRef = useRef(activeTabId);

  // Update activeTabIdRef whenever activeTabId changes
  useEffect(() => {
    activeTabIdRef.current = activeTabId;
  }, [activeTabId]);

  // Sync activeUrl and inputUrl when active tab changes
  useEffect(() => {
    const tab = regular.tabs.find(t => t.id === regular.activeTabId);

    if (tab) {
        setActiveUrl(tab.url);
        setInputUrl(tab.url ? getDisplayHost(tab.url) : "");
    } else {
        // Fallback if no tab (shouldn't happen often)
        setActiveUrl(null);
        setInputUrl("");
    }
  }, [regular.activeTabId, regular.tabs]);


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
  }, [areSettingsLoaded, startupTabMode, backgroundRefresh, regular.resetTabs]); 

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
    if (regular.activeTabId) {
        regular.setTabs((prev) => 
            prev.map((t) => {
              if (t.id === regular.activeTabId && !t.hasLoadedOnce) {
                return { ...t, hasLoadedOnce: true };
              }
              return t;
            })
        );
    }
  }, [regular.activeTabId]);

  const addNewTab = (overrideUrl?: string) => {
    const newId = Date.now().toString();
    const newTab = {
      id: newId,
      url: overrideUrl || null,
      requestedUrl: overrideUrl || null,
      initialUrl: overrideUrl || null,
      title: "New Tab",
      showLogo: true,
      hasLoadedOnce: true,
      historyStack: overrideUrl ? [overrideUrl] : [],
      currentIndex: overrideUrl ? 0 : -1
    };

    regular.addTab(newTab);
  };

  const deleteTab = (id: string) => regular.deleteTab(id);
  const updateTab = (id: string, updates: Partial<TabItem>) => regular.updateTab(id, updates);
  const reorderTabs = (from: number, to: number) => regular.reorderTabs(from, to);
  
  // Wrapper for setActiveTabId to delegate
  const setActiveTabId = (id: string) => regular.setActiveTabId(id);
  // Wrapper for setTabs to delegate
  const setTabsWrapper = (action: any) => regular.setTabs(action);

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
    activeTabIdRef
  };
};