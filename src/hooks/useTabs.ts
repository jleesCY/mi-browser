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
  // --- REGULAR TABS STATE ---
  const [regularTabs, setRegularTabs] = useState<TabItem[]>([
    { id: "1", url: null, title: "New Tab", showLogo: true },
  ]);
  const [activeRegularTabId, setActiveRegularTabId] = useState("1");

  // --- INCOGNITO TABS STATE ---
  const [isIncognito, setIsIncognito] = useState(false);
  const [incognitoTabs, setIncognitoTabs] = useState<TabItem[]>([]);
  const [activeIncognitoTabId, setActiveIncognitoTabId] = useState<string | null>(null);

  // --- SHARED UI STATE (Derived or Synced) ---
  const [activeUrl, setActiveUrl] = useState<string | null>(null);
  const [inputUrl, setInputUrl] = useState("");
  const [areTabsLoaded, setAreTabsLoaded] = useState(false);
  const hasLoadedTabs = useRef(false);

  // Helpers to get current active set
  const tabs = isIncognito ? incognitoTabs : regularTabs;
  const activeTabId = isIncognito ? (activeIncognitoTabId || "") : activeRegularTabId;
  
  // Ref to track active ID for async operations/callbacks
  const activeTabIdRef = useRef(activeTabId);

  // Update activeTabIdRef whenever activeTabId changes
  useEffect(() => {
    activeTabIdRef.current = activeTabId;
  }, [activeTabId]);

  // Sync activeUrl and inputUrl when switching modes or active tab changes
  useEffect(() => {
    const currentTabs = isIncognito ? incognitoTabs : regularTabs;
    const currentId = isIncognito ? activeIncognitoTabId : activeRegularTabId;
    const tab = currentTabs.find(t => t.id === currentId);

    if (tab) {
        setActiveUrl(tab.url);
        setInputUrl(tab.url ? getDisplayHost(tab.url) : "");
    } else {
        // Fallback if no tab (shouldn't happen often)
        setActiveUrl(null);
        setInputUrl("");
    }
  }, [isIncognito, activeIncognitoTabId, activeRegularTabId, regularTabs, incognitoTabs]);


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

          setRegularTabs([startupTab, ...existingTabs]);
          setActiveRegularTabId(newId);
        } else {
           if (startupTabMode === "last" && existingTabs.length > 0) {
             let targetTab = existingTabs.find((t: any) => t.id === savedActiveTabId);
             if (!targetTab) targetTab = existingTabs[0];

             const finalTabs = existingTabs.map((t: any) => 
               t.id === targetTab.id ? { ...t, hasLoadedOnce: true } : t
             );
             
             setRegularTabs(finalTabs);
             setActiveRegularTabId(targetTab.id);
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

        setRegularTabs(finalTabs);
        setActiveRegularTabId(targetTab.id);

      } else {
        // CASE: New
        // Check if we already have a blank tab in the saved list to reuse
        const existingBlankTab = existingTabs.find((t: any) => !t.url);

        if (existingBlankTab) {
          const finalTabs = existingTabs.map((t: any) => 
             t.id === existingBlankTab.id ? { ...t, hasLoadedOnce: true } : t
          );

          setRegularTabs(finalTabs);
          setActiveRegularTabId(existingBlankTab.id);
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

          setRegularTabs([newTab, ...existingTabs]);
          setActiveRegularTabId(newTabId);
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
      const cleanTabs = regularTabs.map(
        ({ loading, canGoBack, canGoForward, hasLoadedOnce, ...rest }) => rest
      );
      saveStorage("tabs", cleanTabs);
      saveStorage("activeTabId", activeRegularTabId);
    }, 500); 

    return () => clearTimeout(saveTimeout);
  }, [regularTabs, activeRegularTabId, areTabsLoaded]);

  // Mark active tab as loaded
  useEffect(() => {
    // Only applies to Regular tabs for now as Incognito are always "loaded" in session or don't need persistence opt-in
    // But we should set hasLoadedOnce for incognito too to render the WebView
    if (isIncognito) {
        setIncognitoTabs((prev) => 
            prev.map((t) => {
              if (t.id === activeIncognitoTabId && !t.hasLoadedOnce) {
                return { ...t, hasLoadedOnce: true };
              }
              return t;
            })
          );
    } else {
        setRegularTabs((prev) => 
            prev.map((t) => {
              if (t.id === activeRegularTabId && !t.hasLoadedOnce) {
                return { ...t, hasLoadedOnce: true };
              }
              return t;
            })
          );
    }
  }, [activeRegularTabId, activeIncognitoTabId, isIncognito]);

  const toggleIncognitoMode = () => {
      if (!isIncognito) {
          // Switching TO Incognito
          // If no incognito tabs exist, create one
          if (incognitoTabs.length === 0) {
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
            setIncognitoTabs([newTab]);
            setActiveIncognitoTabId(newId);
          }
      } else {
          // Switching OFF Incognito (Exit)
          // Clear all incognito tabs to ensure fresh session next time
          setTimeout(() => {
            setIncognitoTabs([]);
            setActiveIncognitoTabId(null);
          }, 500); // Small delay to allow fade out animation if any, or just immediate. 
          // Actually, instant clear is safer for "leaving".
          setIncognitoTabs([]);
          setActiveIncognitoTabId(null);
      }
      setIsIncognito(!isIncognito);
  };

  const setTabs = (action: React.SetStateAction<TabItem[]>) => {
      if (isIncognito) {
          setIncognitoTabs(action);
      } else {
          setRegularTabs(action);
      }
  };

  const setActiveTabId = (id: string) => {
      if (isIncognito) {
          setActiveIncognitoTabId(id);
      } else {
          setActiveRegularTabId(id);
      }
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

    if (isIncognito) {
        setIncognitoTabs((prev) => [newTab, ...prev]);
        setActiveIncognitoTabId(newId);
    } else {
        setRegularTabs((prev) => [newTab, ...prev]);
        setActiveRegularTabId(newId);
    }
    
    // UI state sync happens via Effect
  };

  const deleteTab = (idToDelete: string) => {
    const setTargetTabs = isIncognito ? setIncognitoTabs : setRegularTabs;
    const targetActiveIdRef = isIncognito ? activeIncognitoTabId : activeRegularTabId;

    setTargetTabs((prevTabs) => {
      const newTabs = prevTabs.filter((t) => t.id !== idToDelete);
      
      // We use the passed-in param or state, but inside setState updater 'activeTabId' might be stale?
      // We used a ref in original code.
      const currentActiveId = targetActiveIdRef;

      if (currentActiveId === idToDelete) {
        if (newTabs.length > 0) {
          const indexToDelete = prevTabs.findIndex((t) => t.id === idToDelete);
          const nextIndex = Math.max(0, indexToDelete - 1);
          const safeIndex = Math.min(nextIndex, newTabs.length - 1);
          const nextTab = newTabs[safeIndex];

          setTimeout(() => {
            if (isIncognito) setActiveIncognitoTabId(nextTab.id);
            else setActiveRegularTabId(nextTab.id);
          }, 0);
        }
      }

      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);

      if (newTabs.length === 0) {
        // If we deleted the last tab, create a new blank one
        const freshId = Date.now().toString();
        const freshTab = { 
            id: freshId, 
            url: null, 
            requestedUrl: null, 
            title: isIncognito ? "Incognito Tab" : "New Tab", 
            showLogo: true, 
            hasLoadedOnce: true,
            historyStack: [],
            currentIndex: -1
        };
        
        setTimeout(() => {
          if (isIncognito) setActiveIncognitoTabId(freshId);
          else setActiveRegularTabId(freshId);
        }, 0);
        return [freshTab];
      }
      
      return newTabs;
    });
  };

  const updateTab = (id: string, updates: Partial<TabItem>) => {
    if (isIncognito) {
        setIncognitoTabs(prev => prev.map(t => t.id === id ? { ...t, ...updates } : t));
    } else {
        setRegularTabs(prev => prev.map(t => t.id === id ? { ...t, ...updates } : t));
    }
  };

  const reorderTabs = (fromIndex: number, toIndex: number) => {
    const setTargetTabs = isIncognito ? setIncognitoTabs : setRegularTabs;
    setTargetTabs((prevTabs) => {
      const newTabs = [...prevTabs];
      const [movedTab] = newTabs.splice(fromIndex, 1);
      newTabs.splice(toIndex, 0, movedTab);
      return newTabs;
    });
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
    reorderTabs,
    activeTabIdRef,
    isIncognito, toggleIncognitoMode
  };
};

