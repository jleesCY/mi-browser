import { useState, useEffect, useRef } from 'react';
import { Linking, LayoutAnimation } from 'react-native';
import { loadStorage, saveStorage, getDisplayHost, parseDeepLinkUrl } from '../utils';
import { TabItem } from '../types';

interface UseTabsProps {
  areSettingsLoaded: boolean;
  startupTabMode: "new" | "last";
  backgroundRefresh: boolean;
}

export const useTabs = ({ areSettingsLoaded, startupTabMode, backgroundRefresh }: UseTabsProps) => {
  const [tabs, setTabs] = useState<TabItem[]>([
    { id: "1", url: null, title: "New Tab", showLogo: true },
  ]);
  const [activeTabId, setActiveTabId] = useState("1");
  const [activeUrl, setActiveUrl] = useState<string | null>(null);
  const [inputUrl, setInputUrl] = useState("");
  
  // Ref to track active ID for async operations/callbacks
  const activeTabIdRef = useRef(activeTabId);

  const [areTabsLoaded, setAreTabsLoaded] = useState(false);

  // Sync ref
  useEffect(() => {
    activeTabIdRef.current = activeTabId;
  }, [activeTabId]);

  // Load Tabs on Startup
  useEffect(() => {
    if (!areSettingsLoaded) return;

    const loadTabs = async () => {
      const savedTabs = await loadStorage("tabs");
      const savedActiveTabId = await loadStorage("activeTabId");

      const existingTabs = (savedTabs || []).map((t: any) => ({
        ...t,
        initialUrl: t.initialUrl || t.url,
        requestedUrl: t.url, // Reset requestedUrl to last known url on startup
        hasLoadedOnce: backgroundRefresh
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
              hasLoadedOnce: true
          };

          setTabs([startupTab, ...existingTabs]);
          setActiveTabId(newId);
          setActiveUrl(targetUrl);
          setInputUrl(getDisplayHost(targetUrl));
        } else {
           if (startupTabMode === "last" && existingTabs.length > 0) {
             let targetTab = existingTabs.find((t: any) => t.id === savedActiveTabId);
             if (!targetTab) targetTab = existingTabs[0];

             const finalTabs = existingTabs.map((t: any) => 
               t.id === targetTab.id ? { ...t, hasLoadedOnce: true } : t
             );
             
             setTabs(finalTabs);
             setActiveTabId(targetTab.id);
             setActiveUrl(targetTab.url);
             setInputUrl(targetTab.url ? getDisplayHost(targetTab.url) : "");
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

        setTabs(finalTabs);
        setActiveTabId(targetTab.id);
        setActiveUrl(targetTab.url);
        setInputUrl(targetTab.url ? getDisplayHost(targetTab.url) : "");

      } else {
        // CASE: New
        // Check if we already have a blank tab in the saved list to reuse
        const existingBlankTab = existingTabs.find((t: any) => !t.url);

        if (existingBlankTab) {
          const finalTabs = existingTabs.map((t: any) => 
             t.id === existingBlankTab.id ? { ...t, hasLoadedOnce: true } : t
          );

          setTabs(finalTabs);
          setActiveTabId(existingBlankTab.id);
          setActiveUrl(null);
          setInputUrl("");
        } else {
          const newTabId = Date.now().toString();
          const newTab = {
            id: newTabId,
            url: null,
            initialUrl: null,
            title: "New Tab",
            showLogo: true,
            hasLoadedOnce: true
          };

          setTabs([newTab, ...existingTabs]);
          setActiveTabId(newTabId);
          setActiveUrl(null);
          setInputUrl("");
        }
      }
      setAreTabsLoaded(true);
    };

    loadTabs();
  }, [areSettingsLoaded]); // Run once when settings are ready

  // Save Tabs
  useEffect(() => {
    if (!areTabsLoaded) return;

    const saveTimeout = setTimeout(() => {
      const cleanTabs = tabs.map(
        ({ loading, canGoBack, canGoForward, hasLoadedOnce, ...rest }) => rest
      );
      saveStorage("tabs", cleanTabs);
      saveStorage("activeTabId", activeTabId);
    }, 500); 

    return () => clearTimeout(saveTimeout);
  }, [tabs, activeTabId, areTabsLoaded]);

  // Mark active tab as loaded
  useEffect(() => {
    setTabs((prev) => 
      prev.map((t) => {
        if (t.id === activeTabId && !t.hasLoadedOnce) {
          return { ...t, hasLoadedOnce: true };
        }
        return t;
      })
    );
  }, [activeTabId]);

  const addNewTab = (overrideUrl?: string) => {
    const newId = Date.now().toString();
    const newTab = {
      id: newId,
      url: overrideUrl || null,
      requestedUrl: overrideUrl || null,
      initialUrl: overrideUrl || null,
      title: "New Tab",
      showLogo: true,
      hasLoadedOnce: true
    };
    setTabs((prev) => [newTab, ...prev]);
    setActiveTabId(newId);
    setActiveUrl(overrideUrl || null);
    setInputUrl(overrideUrl ? getDisplayHost(overrideUrl) : "");
  };

  const deleteTab = (idToDelete: string) => {
    setTabs((prevTabs) => {
      const newTabs = prevTabs.filter((t) => t.id !== idToDelete);
      
      const currentActiveId = activeTabIdRef.current;

      if (currentActiveId === idToDelete) {
        if (newTabs.length > 0) {
          const indexToDelete = prevTabs.findIndex((t) => t.id === idToDelete);
          const nextIndex = Math.max(0, indexToDelete - 1);
          const safeIndex = Math.min(nextIndex, newTabs.length - 1);
          const nextTab = newTabs[safeIndex];

          setTimeout(() => {
            setActiveTabId(nextTab.id);
            setActiveUrl(nextTab.url);
            setInputUrl(nextTab.url ? getDisplayHost(nextTab.url) : "");
          }, 0);
        }
      }

      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);

      if (newTabs.length === 0) {
        const freshId = Date.now().toString();
        const freshTab = { id: freshId, url: null, requestedUrl: null, title: "New Tab", showLogo: true, hasLoadedOnce: true };
        
        setTimeout(() => {
          setActiveTabId(freshId);
          setActiveUrl(null);
          setInputUrl("");
        }, 0);
        return [freshTab];
      }
      
      return newTabs;
    });
  };

  const updateTab = (id: string, updates: Partial<TabItem>) => {
    setTabs(prev => prev.map(t => t.id === id ? { ...t, ...updates } : t));
  };

  return {
    tabs, setTabs,
    activeTabId, setActiveTabId,
    activeUrl, setActiveUrl,
    inputUrl, setInputUrl,
    areTabsLoaded,
    addNewTab,
    deleteTab,
    updateTab,
    activeTabIdRef
  };
};
