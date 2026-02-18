import * as FileSystem from 'expo-file-system/legacy';
import { useEffect, useRef, useState } from 'react';
import { Linking } from 'react-native';
import { TabItem } from '../types';
import { getDisplayHost, loadStorage, parseDeepLinkUrl, saveStorage } from '../utils';
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

  const prevActiveTabId = useRef(regular.activeTabId);

  // Sync activeUrl and inputUrl when active tab changes
  useEffect(() => {
    const tab = regular.tabs.find(t => t.id === regular.activeTabId);

    if (tab) {
      setActiveUrl(tab.url);
    } else {
      // Fallback if no tab (shouldn't happen often)
      setActiveUrl(null);
    }

    // Only update inputUrl if the active tab HAS CHANGED.
    // In-tab navigation/updates are handled by the WebView callback in App.tsx to respect focus
    if (regular.activeTabId !== prevActiveTabId.current) {
      if (tab) {
        setInputUrl(tab.url ? getDisplayHost(tab.url) : "");
      } else {
        setInputUrl("");
      }
      prevActiveTabId.current = regular.activeTabId;
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
        .map((t: any) => {
          const stack = t.historyStack || (t.url ? [t.url] : []);
          const index = t.currentIndex !== undefined ? t.currentIndex : (stack.length > 0 ? stack.length - 1 : -1);
          const currentUrl = (index >= 0 && index < stack.length) ? stack[index] : null;

          return {
            ...t,
            id: t.id,
            url: currentUrl,
            requestedUrl: currentUrl,
            initialUrl: null,
            title: t.title || "New Tab",
            hasLoadedOnce: backgroundRefresh,
            historyStack: stack,
            currentIndex: index,
          };
        });

      const initialUrl = await Linking.getInitialURL();

      let tabsToSet: any[] = [];
      let activeIdToSet: string = "";

      if (initialUrl) {
        const targetUrl = parseDeepLinkUrl(initialUrl);

        if (targetUrl) {
          const newId = Date.now().toString();
          const startupTab = {
            id: newId,
            url: targetUrl,
            requestedUrl: targetUrl,
            initialUrl: null,
            title: "External Link",
            showLogo: false,
            hasLoadedOnce: true,
            historyStack: [targetUrl],
            currentIndex: 0
          };

          tabsToSet = [startupTab, ...existingTabs];
          activeIdToSet = newId;
        } else {
          if (startupTabMode === "last" && existingTabs.length > 0) {
            let targetTab = existingTabs.find((t: any) => t.id === savedActiveTabId);
            if (!targetTab) targetTab = existingTabs[0];

            tabsToSet = existingTabs.map((t: any) =>
              t.id === targetTab.id ? { ...t, hasLoadedOnce: true } : t
            );
            activeIdToSet = targetTab.id;
          }
        }
      }

      if (tabsToSet.length === 0) {
        if (startupTabMode === "last" && existingTabs.length > 0) {
          let targetTab = existingTabs.find(
            (t: any) => t.id === savedActiveTabId
          );
          if (!targetTab) {
            targetTab = existingTabs.find((t: any) => t.url) || existingTabs[0];
          }

          tabsToSet = existingTabs.map((t: any) =>
            t.id === targetTab.id ? { ...t, hasLoadedOnce: true } : t
          );
          activeIdToSet = targetTab.id;

        } else {
          // CASE: New
          // Check if we already have a blank tab in the saved list to reuse
          const existingBlankTab = existingTabs.find((t: any) => !t.url);

          if (existingBlankTab) {
            tabsToSet = existingTabs.map((t: any) =>
              t.id === existingBlankTab.id ? { ...t, hasLoadedOnce: true } : t
            );
            activeIdToSet = existingBlankTab.id;
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

            tabsToSet = [newTab, ...existingTabs];
            activeIdToSet = newTabId;
          }
        }
      }

      regular.resetTabs(tabsToSet, activeIdToSet);
      setAreTabsLoaded(true);

      // --- Cleanup Orphaned Preview Images ---
      try {
        const validPreviewPaths = new Set(
          tabsToSet
            .filter((t: any) => t && t.previewImage)
            .map((t: any) => t.previewImage)
        );

        const files = await FileSystem.readDirectoryAsync(FileSystem.cacheDirectory || "");
        const previewFiles = files.filter(f => f.startsWith("preview_") && f.endsWith(".png"));

        for (const file of previewFiles) {
          const fullPath = `${FileSystem.cacheDirectory}${file}`;
          if (!validPreviewPaths.has(fullPath)) {
            await FileSystem.deleteAsync(fullPath, { idempotent: true }).catch(() => { });
          }
        }
      } catch (e) {
        console.log("Failed to clean up orphaned images", e);
      }
    };

    loadTabs();
  }, [areSettingsLoaded, startupTabMode, backgroundRefresh, regular.resetTabs]);

  // Save Tabs (Regular Only)
  useEffect(() => {
    if (!areTabsLoaded) return;

    const saveTimeout = setTimeout(() => {
      const cleanTabs = regular.tabs.map(
        (tab) => ({
          id: tab.id,
          title: tab.title,
          historyStack: tab.historyStack,
          currentIndex: tab.currentIndex,
          desktopMode: tab.desktopMode,
          readerMode: tab.readerMode,
          previewImage: tab.previewImage,
          isCustomTitle: tab.isCustomTitle,
          // We explicitly DO NOT save: url, requestedUrl, initialUrl, loading, canGoBack, etc.
        })
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
      initialUrl: null,
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