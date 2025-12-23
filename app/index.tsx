import {
    Nunito_400Regular,
    Nunito_600SemiBold,
    Nunito_700Bold,
    Nunito_800ExtraBold,
    useFonts,
} from "@expo-google-fonts/nunito";
import { Ionicons } from "@expo/vector-icons";
import React, { useEffect, useRef, useState } from "react";
import {
    ActivityIndicator,
    Alert,
    Animated,
    BackHandler,
    FlatList,
    Keyboard,
    LayoutAnimation,
    Linking,
    Modal,
    PanResponder,
    ScrollView,
    Share,
    StatusBar,
    StyleSheet,
    Switch,
    Text,
    TextInput,
    TouchableOpacity,
    TouchableWithoutFeedback,
    View
} from "react-native";
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { WebView } from "react-native-webview";

import SwipeableHistoryRow from "../src/components/SwipeableHistoryRow";
import SwipeableTabRow from "../src/components/SwipeableTabRow";
import {
    ACCENTS,
    APP_VERSION,
    COLORS,
    HIDDEN_TRANSLATE_Y,
    HISTORY_RANGES,
    SEARCH_ENGINES,
    SNAP_CLOSED,
    SNAP_DEFAULT,
    SNAP_FULL,
    SWAP_DISTANCE,
} from "../src/constants";
import { HistoryItem, TabItem } from "../src/types";
import {
    generateAdaptiveTheme,
    getDisplayHost,
    loadStorage,
    saveStorage,
} from "../src/utils";

export default function App() {

    const insets = useSafeAreaInsets();

  let [fontsLoaded] = useFonts({
    Nunito_400Regular,
    Nunito_600SemiBold,
    Nunito_700Bold,
    Nunito_800ExtraBold,
  });

  const [isAppReady, setIsAppReady] = useState(false);
  const [inputUrl, setInputUrl] = useState("");
  const [activeUrl, setActiveUrl] = useState<string | null>(null);

  const [progress, setProgress] = useState(0);
  const progressAnim = useRef(new Animated.Value(0)).current;

  const [activeTabId, setActiveTabId] = useState("1");
  const [canGoBack, setCanGoBack] = useState(false);
  const [canGoForward, setCanGoForward] = useState(false);
  const canGoBackRef = useRef(false);
  const canGoForwardRef = useRef(false);
  const activeTabIdRef = useRef(activeTabId);

  const [isLoading, setIsLoading] = useState(false);

  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [tabs, setTabs] = useState<TabItem[]>([
    { id: "1", url: null, title: "New Tab", showLogo: true },
  ]);

  // Confirmation Modal State
  const [isConfirmModalVisible, setIsConfirmModalVisible] = useState(false);
  const [confirmActionType, setConfirmActionType] = useState<'cache' | 'history' | null>(null);
  const [confirmHistoryPayload, setConfirmHistoryPayload] = useState<{ ms: number; label: string } | null>(null);

  const [activeView, setActiveView] = useState<
    "none" | "tabs" | "history" | "settings"
  >("none");
  const [isSearchActive, setIsSearchActive] = useState(true);
  const isSearchActiveRef = useRef(true);
  const [isInputFocused, setIsInputFocused] = useState(false);

  const [themeMode, setThemeMode] = useState<"light" | "dark" | "adaptive">(
    "dark"
  );
  const [accentColor, setAccentColor] = useState("#007AFF");
  const [searchEngineIndex, setSearchEngineIndex] = useState(0);
  const [isSearchEngineOpen, setIsSearchEngineOpen] = useState(false);
  const [isClearHistoryOpen, setIsClearHistoryOpen] = useState(false);

  // Cosmetic settings
  const [cornerRadius, setCornerRadius] = useState(22);
  const [uiPadding, setUiPadding] = useState<"compact" | "normal" | "airy">(
    "normal"
  );
  const [fontScale, setFontScale] = useState(1);
  const [barTransparency, setBarTransparency] = useState<
    "opaque" | "frosted" | "ghost"
  >("frosted");
  const [homeLogoText, setHomeLogoText] = useState("mi.");
  const [pillHeight, setPillHeight] = useState(70);
  const [progressBarMode, setProgressBarMode] = useState<"ltr" | "center" | "none">("ltr");
  const [recallPosition, setRecallPosition] = useState<"left" | "center" | "right">("center");

  const [startupTabMode, setStartupTabMode] = useState<"new" | "last">("new");

  // Functional settings
  const [desktopMode, setDesktopMode] = useState(false);
  const [jsEnabled, setJsEnabled] = useState(true);
  const [httpsOnly, setHttpsOnly] = useState(false);
  const [blockCookies, setBlockCookies] = useState(false);
  const [readerMode, setReaderMode] = useState(false);
  const [incognitoMode, setIncognitoMode] = useState(false);

  // UI State for Menus
  const [settingsSearch, setSettingsSearch] = useState("");
  const [tabsSearch, setTabsSearch] = useState("");
  const [historySearch, setHistorySearch] = useState("");

  // Rename Modal
  const [isRenameModalVisible, setIsRenameModalVisible] = useState(false);
  const [tabToRename, setTabToRename] = useState<string | null>(null);
  const [renameText, setRenameText] = useState("");
  const [renameShowLogo, setRenameShowLogo] = useState(true);

  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const loadAllData = async () => {
      // 1. Load Settings First
      const savedSettings = await loadStorage("settings");

      // Initialize a LOCAL variable. We use this for logic because state updates are too slow.
      let currentStartupMode = "new"; 

      if (savedSettings) {
        setThemeMode(savedSettings.themeMode ?? "dark");
        setAccentColor(savedSettings.accentColor ?? "#007AFF");
        setSearchEngineIndex(savedSettings.searchEngineIndex ?? 0);
        setCornerRadius(savedSettings.cornerRadius ?? 22);
        setUiPadding(savedSettings.uiPadding ?? "normal");
        setFontScale(savedSettings.fontScale ?? 1);
        setBarTransparency(savedSettings.barTransparency ?? "frosted");
        setHomeLogoText(savedSettings.homeLogoText ?? "mi.");
        setPillHeight(savedSettings.pillHeight ?? 70);
        setProgressBarMode(savedSettings.progressBarMode ?? "ltr");
        setRecallPosition(savedSettings.recallPosition ?? "center");

        setDesktopMode(savedSettings.desktopMode ?? false);
        setJsEnabled(savedSettings.jsEnabled ?? true);
        setHttpsOnly(savedSettings.httpsOnly ?? false);
        setBlockCookies(savedSettings.blockCookies ?? false);
        setReaderMode(savedSettings.readerMode ?? false);
        setIncognitoMode(savedSettings.incognitoMode ?? false);

        // --- FIX STARTS HERE ---
        // 1. Update the LOCAL variable directly from storage
        if (savedSettings.startupTabMode) {
            currentStartupMode = savedSettings.startupTabMode;
        }
        // 2. Sync the STATE for the UI
        setStartupTabMode(currentStartupMode as any);
        // --- FIX ENDS HERE ---
      }

      // 2. Load Data
      const savedHistory = await loadStorage("history");
      if (savedHistory) setHistory(savedHistory);

      const savedTabs = await loadStorage("tabs");
      const existingTabs = savedTabs || [];

      // 3. Handle Startup Logic
      const initialUrl = await Linking.getInitialURL();

      if (initialUrl && (initialUrl.startsWith("http://") || initialUrl.startsWith("https://"))) {
         // --- CASE A: Launched via Deep Link ---
         const newId = Date.now().toString();
         const newTab = {
           id: newId,
           url: initialUrl,
           title: "External Link",
           showLogo: false,
         };
         
         setTabs([newTab, ...existingTabs]);
         setActiveTabId(newId);
         setActiveUrl(initialUrl);
         setInputUrl(getDisplayHost(initialUrl));

      } else if (currentStartupMode === "last" && existingTabs.length > 0) {
        // --- CASE B: Resume Last Session ---
        // (Uses the local variable 'currentStartupMode' which is now guaranteed to be correct)
        setTabs(existingTabs);
        const savedActiveTabId = await loadStorage("activeTabId");
        
        // Failsafe: Try to match ID, otherwise find first tab with a URL
        let targetTab = existingTabs.find((t: any) => t.id === savedActiveTabId);
        if (!targetTab) {
             targetTab = existingTabs.find((t: any) => t.url) || existingTabs[0];
        }
        
        setActiveTabId(targetTab.id);
        setActiveUrl(targetTab.url);
        setInputUrl(targetTab.url ? getDisplayHost(targetTab.url) : "");
      
      } else {
        // --- CASE C: Start Fresh (New Tab) ---
        const existingBlankTab = existingTabs.find((t: any) => !t.url);

        if (existingBlankTab) {
          setTabs(existingTabs);
          setActiveTabId(existingBlankTab.id);
          setActiveUrl(null);
          setInputUrl("");
        } else {
          const newTabId = Date.now().toString();
          const newTab = {
            id: newTabId,
            url: null,
            title: "New Tab",
            showLogo: true,
          };
          
          setTabs([newTab, ...existingTabs]);
          setActiveTabId(newTabId);
          setActiveUrl(null);
          setInputUrl("");
        }
      }

      setIsAppReady(true);
    };
    loadAllData();
  }, []);

  // Keep the ref in sync with state
  useEffect(() => {
    activeTabIdRef.current = activeTabId;
  }, [activeTabId]);

  // --- SYNC UI WHEN SWITCHING TABS ---
  useEffect(() => {
    const currentTab = tabs.find((t) => t.id === activeTabId);
    if (currentTab) {
      // 1. Restore URL bar
      setActiveUrl(currentTab.url);
      setInputUrl(currentTab.url ? getDisplayHost(currentTab.url) : "");
      
      // 2. Restore Navigation Buttons
      setCanGoBack(currentTab.canGoBack || false);
      setCanGoForward(currentTab.canGoForward || false);
      canGoBackRef.current = currentTab.canGoBack || false;
      canGoForwardRef.current = currentTab.canGoForward || false;

      // 3. Restore Loading State
      setIsLoading(currentTab.loading || false);
      
      // 4. Reset or Restore Progress Bar
      // (For simplicity, we just reset it visually when switching)
      progressAnim.setValue(currentTab.loading ? 0.2 : 0);
    }
  }, [activeTabId]); // Only run when we switch tabs

  useEffect(() => {
    if (isAppReady && !incognitoMode) {
      // Strip transient state before saving
      const cleanTabs = tabs.map(({ loading, canGoBack, canGoForward, ...rest }) => rest);
      saveStorage("tabs", cleanTabs);
      saveStorage("activeTabId", activeTabId);
    }
  }, [tabs, activeTabId, isAppReady, incognitoMode]);

  useEffect(() => {
    if (isAppReady && !incognitoMode) {
      saveStorage("tabs", tabs);
      saveStorage("activeTabId", activeTabId);
    }
  }, [tabs, activeTabId, isAppReady, incognitoMode]);

  useEffect(() => {
    if (isAppReady) {
      const settingsToSave = {
        themeMode,
        accentColor,
        searchEngineIndex,
        cornerRadius,
        uiPadding,
        fontScale,
        barTransparency,
        homeLogoText,
        pillHeight,
        progressBarMode,
        recallPosition,
        startupTabMode,
        desktopMode,
        jsEnabled,
        httpsOnly,
        blockCookies,
        readerMode,
        incognitoMode,
      };
      saveStorage("settings", settingsToSave);
    }
  }, [
    themeMode,
    accentColor,
    searchEngineIndex,
    cornerRadius,
    uiPadding,
    fontScale,
    barTransparency,
    homeLogoText,
    pillHeight,
    progressBarMode,
    recallPosition,
    startupTabMode,
    desktopMode,
    jsEnabled,
    httpsOnly,
    blockCookies,
    readerMode,
    incognitoMode,
    isAppReady,
  ]);

  // --- FIX: Handle Android Hardware Back Button ---
  useEffect(() => {
    const onBackPress = () => {
      // 1. If an overlay is open (History, Tabs, Settings), close it first
      if (activeView !== "none") {
        closeOverlay();
        return true; // "true" tells Android we handled the event, so don't exit
      }

      // 2. If the user is typing in the search bar, close the keyboard/unfocus
      if (isInputFocused) {
        Keyboard.dismiss();
        setIsInputFocused(false);
        return true;
      }

      // 3. If the WebView has history to go back to, navigate back
      // We use the Ref we fixed earlier to get the instant "true/false" status
      // 3. If the WebView has history to go back to, navigate back
      if (canGoBackRef.current && webViewRefs.current[activeTabId]) {
        webViewRefs.current[activeTabId]?.goBack();
        return true;
      }

      // 4. If none of the above, let the default behavior happen (Exit/Minimize App)
      return false;
    };

    // Add the listener
    const subscription = BackHandler.addEventListener(
      "hardwareBackPress",
      onBackPress
    );

    // Remove listener on cleanup
    return () => subscription.remove();
  }, [activeView, isInputFocused]); // Re-create listener if UI state changes

  useEffect(() => {
    const handleDeepLink = (event: { url: string }) => {
      let data = event.url;
      if (data && (data.startsWith("http://") || data.startsWith("https://"))) {
        
        const newId = Date.now().toString();
        const newTab = {
          id: newId,
          url: data,
          title: "External Link",
          showLogo: false,
        };
        
        setTabs((prev) => [newTab, ...prev]);
        setActiveTabId(newId);
        setActiveUrl(data);
        setInputUrl(getDisplayHost(data));
        setActiveView("none");
      }
    };

    const subscription = Linking.addEventListener("url", handleDeepLink);
    return () => subscription.remove();
  }, []);

  const getTheme = () => {
    if (themeMode === "light") return COLORS.light;
    if (themeMode === "dark") return COLORS.dark;
    return generateAdaptiveTheme(accentColor);
  };
  const theme = getTheme();

  // Incognito overrides
  const effectiveTheme = incognitoMode
    ? { ...theme, glass: "#222222F2", glassBorder: "#444" }
    : theme;

  if (themeMode === "adaptive" && !incognitoMode) {
    let alpha = "F2";
    if (barTransparency === "opaque") alpha = "FF";
    if (barTransparency === "ghost") alpha = "99";
    effectiveTheme.glass = effectiveTheme.bg.substring(0, 7) + alpha;
  } else if (!incognitoMode) {
    let alpha = 0.95;
    if (barTransparency === "opaque") alpha = 1;
    if (barTransparency === "ghost") alpha = 0.6;
    if (themeMode === "light")
      effectiveTheme.glass = `rgba(255, 255, 255, ${alpha})`;
    if (themeMode === "dark")
      effectiveTheme.glass = `rgba(30, 30, 30, ${alpha})`;
  }

  const getTabHeight = () => {
    switch (uiPadding) {
      case "compact":
        return 60;
      case "normal":
        return 70;
      case "airy":
        return 85;
    }
  };
  const getHistoryHeight = () => {
    switch (uiPadding) {
      case "compact":
        return 40;
      case "normal":
        return 50;
      case "airy":
        return 65;
    }
  };
  const getMargin = () => {
    switch (uiPadding) {
      case "compact":
        return 8;
      case "normal":
        return 15;
      case "airy":
        return 25;
    }
  };

  const scrollTranslateY = useRef(new Animated.Value(0)).current;
  const currentScrollTrans = useRef(0);
  const lastScrollY = useRef(0);
  const animVal = useRef(new Animated.Value(0)).current;
  const horizontalDrag = useRef(new Animated.Value(0)).current;
  const overlayHeightAnim = useRef(new Animated.Value(SNAP_CLOSED)).current;
  const currentOverlayHeight = useRef(SNAP_CLOSED);
  const keyboardHeight = useRef(new Animated.Value(0)).current;
  const logoScale = useRef(new Animated.Value(1)).current;
  const logoPan = useRef(new Animated.ValueXY({ x: 0, y: 0 })).current;

  const webViewRefs = useRef<{ [key: string]: WebView | null }>({});
  const viewShotRef = useRef<View>(null);

  useEffect(() => {
    isSearchActiveRef.current = isSearchActive;
  }, [isSearchActive]);

  useEffect(() => {
    if (activeView !== "none") {
      setIsSearchEngineOpen(false);
      setIsClearHistoryOpen(false);
      
      Animated.spring(overlayHeightAnim, {
        toValue: SNAP_DEFAULT,
        tension: 60,
        friction: 9,
        useNativeDriver: false,
      }).start();
      currentOverlayHeight.current = SNAP_DEFAULT;
    } 
  }, [activeView]);

  useEffect(() => {
    const showSub = Keyboard.addListener("keyboardDidShow", (e) =>
      Animated.timing(keyboardHeight, {
        toValue: e.endCoordinates.height,
        duration: 150,
        useNativeDriver: false,
      }).start()
    );
    const hideSub = Keyboard.addListener("keyboardDidHide", (e) =>
      Animated.timing(keyboardHeight, {
        toValue: 0,
        duration: 150,
        useNativeDriver: false,
      }).start()
    );
    return () => {
      showSub.remove();
      hideSub.remove();
    };
  }, []);

  const expandToFullscreen = () => {
    Animated.spring(overlayHeightAnim, {
      toValue: SNAP_FULL,
      tension: 60,
      friction: 9,
      useNativeDriver: false,
    }).start();
    currentOverlayHeight.current = SNAP_FULL;
  };

  const snapBar = (velocity = 0) => {
    if (velocity > 0.5 || currentScrollTrans.current > HIDDEN_TRANSLATE_Y / 2) {
      hideBar();
    } else {
      showBar();
    }
  };

  const handleAndroidPermissionRequest = (event: any) => {
    const { resources } = event.nativeEvent;
    
    const resourceMap: { [key: string]: string } = {
        'android.webkit.resource.AUDIO_CAPTURE': 'Microphone',
        'android.webkit.resource.VIDEO_CAPTURE': 'Camera',
        'android.webkit.resource.PROTECTED_MEDIA_ID': 'Protected Media',
    };

    const friendlyResources = resources.map((res: string) => resourceMap[res] || res).join(' and ');

    Alert.alert(
        'Permission Request',
        `${activeUrl ? getDisplayHost(activeUrl) : 'This site'} is requesting access to your ${friendlyResources}.`,
        [
            {
                text: 'Deny',
                onPress: () => event.nativeEvent.deny(),
                style: 'cancel',
            },
            {
                text: 'Allow',
                onPress: () => event.nativeEvent.grant(resources),
            },
        ]
    );
};

  const handleGoPress = () => {
    Keyboard.dismiss();
    const text = inputUrl.trim();
    if (!text) return;

    let targetUrl = "";

    // 1. Construct the URL
    if (text.startsWith("http://") || text.startsWith("https://")) {
      targetUrl = text;
    } else {
      const isDomainLike = !text.includes(" ") && /\.[a-zA-Z]{2,}$/.test(text);

      if (isDomainLike) {
        targetUrl = `https://${text}`;
      } else {
        targetUrl = `${
          SEARCH_ENGINES[searchEngineIndex].url
        }${encodeURIComponent(text)}`;
      }
    }
    
    // 2. Update Address Bar State
    setActiveUrl(targetUrl);

    // 3. Update the Tabs Array (CRITICAL FIX)
    // This ensures the renderer sees a URL for this tab and actually mounts the WebView
    setTabs((prev) => 
        prev.map((t) => 
            t.id === activeTabId 
            ? { ...t, url: targetUrl, title: text } // Update URL immediately
            : t
        )
    );
    
    snapToSearch();
  };

  const handleShare = async () => {
    if (!activeUrl) return;
    try {
      await Share.share({
        message: activeUrl,
        url: activeUrl,
        title: "Share Link",
      });
    } catch (error) {}
  };

  const deleteHistory = (milliseconds: number) => {
    if (milliseconds === -1) setHistory([]);
    else {
      const cutoff = Date.now() - milliseconds;
      setHistory((prev) => prev.filter((item) => item.timestamp < cutoff));
    }
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setIsClearHistoryOpen(false);
  };

  // --- CONFIRMATION HANDLERS ---
  const requestClearCache = () => {
    setConfirmActionType('cache');
    setIsConfirmModalVisible(true);
  };

  const requestClearHistory = (ms: number, label: string) => {
    setConfirmHistoryPayload({ ms, label });
    setConfirmActionType('history');
    setIsConfirmModalVisible(true);
  };

  const executeConfirmAction = () => {
    if (confirmActionType === 'cache') {
      webViewRefs.current[activeTabId]?.clearCache(true);
      // Small delay to allow modal to close smoothly before alert
      // setTimeout(() => Alert.alert("Success", "Cache cleared."), 300);
    } else if (confirmActionType === 'history' && confirmHistoryPayload) {
      deleteHistory(confirmHistoryPayload.ms);
    }
    setIsConfirmModalVisible(false);
  };

  const deleteHistoryItem = (idToDelete: string) => {
    // USE FUNCTIONAL UPDATE (prevHistory)
    // This fixes the bug where swiping fast fails to delete items.
    setHistory((prevHistory) => {
      const newHistory = prevHistory.filter((item) => item.id !== idToDelete);
      
      // Optional: Animate the list layout
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
      
      return newHistory;
    });
  };

  const addNewTab = (overrideUrl?: string) => {
    const newId = Date.now().toString();
    const newTab = {
      id: newId,
      url: overrideUrl || null,
      title: "New Tab",
      showLogo: true,
    };
    setTabs((prev) => [newTab, ...prev]);
    setActiveTabId(newId);
    setActiveUrl(overrideUrl || null);
    setInputUrl(overrideUrl ? getDisplayHost(overrideUrl) : "");
  };

  const deleteTab = (idToDelete: string) => {
    // 1. Clean up the WebView ref to prevent leaks
    if (webViewRefs.current[idToDelete]) {
        webViewRefs.current[idToDelete] = null;
        delete webViewRefs.current[idToDelete];
    }

    // 2. If we are deleting the ACTIVE tab, switch to a neighbor first
    if (activeTabId === idToDelete) {
      const indexToDelete = tabs.findIndex((t) => t.id === idToDelete);
      const remainingTabs = tabs.filter((t) => t.id !== idToDelete);

      if (remainingTabs.length > 0) {
        const nextIndex = Math.min(indexToDelete, remainingTabs.length - 1);
        const nextTab = remainingTabs[nextIndex];
        setActiveTabId(nextTab.id);
        setActiveUrl(nextTab.url);
        setInputUrl(getDisplayHost(nextTab.url));
      }
    }

    // 3. Update the Tabs State
    setTabs((prevTabs) => {
      const newTabs = prevTabs.filter((t) => t.id !== idToDelete);
      
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);

      // --- FIX STARTS HERE ---
      // Handle "Empty List" Case (Create a new blank tab if we deleted the last one)
      if (newTabs.length === 0) {
        const freshId = Date.now().toString();
        
        // We use setTimeout to break out of the render cycle for these side effects
        setTimeout(() => {
          setActiveTabId(freshId);
          setActiveUrl(null);
          setInputUrl("");
          closeOverlay();
        }, 0);
        
        return [{ id: freshId, url: null, title: "New Tab", showLogo: true }];
      }

      return newTabs;
    });
  };

  const openRenameModal = (
    tabId: string,
    currentTitle: string,
    currentShowLogo: boolean
  ) => {
    setTabToRename(tabId);
    setRenameText(currentTitle);
    setRenameShowLogo(currentShowLogo);
    setIsRenameModalVisible(true);
  };

  const saveRenameTab = () => {
    if (tabToRename) {
      setTabs((prev) =>
        prev.map((t) =>
          t.id === tabToRename
            ? { ...t, title: renameText || "New Tab", showLogo: renameShowLogo }
            : t
        )
      );
    }
    setIsRenameModalVisible(false);
    setTabToRename(null);
  };

  const goHome = () => {
    setActiveUrl(null);
    setInputUrl("");
    setTabs((prev) =>
      prev.map((tab) =>
        tab.id === activeTabId ? { ...tab, url: null, title: "New Tab" } : tab
      )
    );
    snapToSearch();
  };

  const closeOverlay = () => {
    // 1. Animate the menu sliding down
    Animated.timing(overlayHeightAnim, {
      toValue: SNAP_CLOSED,
      duration: 250, // Slightly longer for smoothness
      useNativeDriver: false,
    }).start(() => {
      // 2. AFTER animation finishes, unmount the view
      setActiveView("none");
      snapToSearch();

      // 3. Reset search states
      setSettingsSearch("");
      setTabsSearch("");
      setHistorySearch("");
    });
    
    currentOverlayHeight.current = SNAP_CLOSED;
  };

  const snapToSearch = () => {
    setIsSearchActive(true);
    Animated.spring(animVal, {
      toValue: 0,
      tension: 60,
      friction: 9,
      useNativeDriver: true,
    }).start();
  };
  const showBar = () => {
    currentScrollTrans.current = 0;
    Animated.timing(scrollTranslateY, {
      toValue: 0,
      duration: 200,
      useNativeDriver: false,
    }).start();
  };
  const hideBar = () => {
    currentScrollTrans.current = HIDDEN_TRANSLATE_Y;
    Animated.timing(scrollTranslateY, {
      toValue: HIDDEN_TRANSLATE_Y,
      duration: 250,
      useNativeDriver: false,
    }).start();
  };

  const addToHistory = (url: string) => {
    // 1. Basic cleaning
    if (!url || url === "about:blank") return;

    const title = getDisplayHost(url);

    setHistory((prevHistory) => {
      // 2. Remove duplicates
      // We remove the old entry for this URL so we can bump it to the top
      const cleanedHistory = prevHistory.filter(
        (item) => item.url.replace(/\/$/, "") !== url.replace(/\/$/, "")
      );

      // 3. Create the new item
      const newItem = {
        id: Date.now().toString(),
        url,
        title,
        timestamp: Date.now(), // Now stores the full timestamp
      };

      // 4. Return updated list (Limit to 100)
      return [newItem, ...cleanedHistory].slice(0, 100);
    });
  };

  const handleScroll = (event: any) => {
    const y = event.nativeEvent.contentOffset.y;
    if (isInputFocused || activeView !== "none" || y < 0) return;
    const dy = y - lastScrollY.current;
    const newTrans = Math.max(
      0,
      Math.min(HIDDEN_TRANSLATE_Y, currentScrollTrans.current + dy)
    );
    if (newTrans !== currentScrollTrans.current) {
      scrollTranslateY.setValue(newTrans);
      currentScrollTrans.current = newTrans;
    }
    lastScrollY.current = y;
  };

  const handleFullScreen = (event: any) => {
    const { fullScreen } = event.nativeEvent;
    setIsFullscreen(fullScreen);
    StatusBar.setHidden(fullScreen);
  };

  const handleWebViewMessage = (event: any) => {
    try {
      const data = event.nativeEvent.data;
      const message = typeof data === "string" ? JSON.parse(data) : data;
    } catch (e) {}
  };

  // --- Pan Responders ---
  const logoResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        Animated.spring(logoScale, {
          toValue: 1.2,
          useNativeDriver: false,
        }).start();
        logoPan.setOffset({
          x: (logoPan.x as any)._value,
          y: (logoPan.y as any)._value,
        });
        logoPan.setValue({ x: 0, y: 0 });
      },
      onPanResponderMove: Animated.event(
        [null, { dx: logoPan.x, dy: logoPan.y }],
        { useNativeDriver: false }
      ),
      onPanResponderRelease: (_, gestureState) => {
        const { dy, dx, vy } = gestureState;

        if (isSearchActiveRef.current) {
          if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) {
            // FIX: Use ref to get the current tab ID
            const currentTabId = activeTabIdRef.current;
            const currentWebView = webViewRefs.current[currentTabId];

            if (dx > 0) {
                // FIX: Removed "&& canGoBackRef.current" check
                // We force the attempt. If no history exists, WebView just ignores it.
                currentWebView?.goBack();
            } else if (dx < 0) {
                // FIX: Removed "&& canGoForwardRef.current" check
                currentWebView?.goForward();
            }

            Animated.spring(horizontalDrag, {
              toValue: 0,
              useNativeDriver: true,
            }).start();
            Animated.spring(animVal, {
              toValue: 0,
              useNativeDriver: true,
            }).start();
            showBar();
            return;
          }

          Animated.spring(horizontalDrag, {
            toValue: 0,
            useNativeDriver: true,
          }).start();

          if (dy < -30) {
            setIsSearchActive(false);
            Animated.spring(animVal, {
              toValue: -SWAP_DISTANCE,
              tension: 60,
              friction: 9,
              useNativeDriver: true,
            }).start();
          } else {
            Animated.spring(animVal, {
              toValue: 0,
              useNativeDriver: true,
            }).start();
            snapBar(vy);
          }
        } else {
          if (dy > 30 || vy > 0.5) {
            setIsSearchActive(true);
            Animated.spring(animVal, {
              toValue: 0,
              tension: 60,
              friction: 9,
              useNativeDriver: true,
            }).start();
          } else {
            Animated.spring(animVal, {
              toValue: -SWAP_DISTANCE,
              tension: 60,
              friction: 9,
              useNativeDriver: true,
            }).start();
          }
        }
      },
    })
  ).current;

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) =>
        Math.abs(gestureState.dy) > 10 || Math.abs(gestureState.dx) > 10,

      onPanResponderGrant: () => {
        animVal.stopAnimation();
        scrollTranslateY.stopAnimation();
      },

      onPanResponderMove: (_, gestureState) => {
        const { dy, dx } = gestureState;

        if (isSearchActiveRef.current) {
          if (Math.abs(dx) > Math.abs(dy)) {
            horizontalDrag.setValue(dx);
          } else {
            if (dy < 0) {
              animVal.setValue(dy);
            } else {
              const newY = Math.max(0, Math.min(HIDDEN_TRANSLATE_Y, dy));
              scrollTranslateY.setValue(newY);
              currentScrollTrans.current = newY;
            }
          }
        } else {
          if (dy > 0) animVal.setValue(-SWAP_DISTANCE + dy);
        }
      },

      onPanResponderRelease: (_, gestureState) => {
        const { dy, dx, vy } = gestureState;

        if (isSearchActiveRef.current) {
          if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) {
            // FIX 1: Use activeTabIdRef.current to get the REAL active tab
            const currentTabId = activeTabIdRef.current;
            const currentWebView = webViewRefs.current[currentTabId];
            
            // FIX 2: Removed conditional checks (&& canGoBackRef.current). 
            // We let the WebView attempt navigation blindly. If history is empty, it just does nothing.
            if (dx > 0) {
                currentWebView?.goBack();
            } else if (dx < 0) {
                currentWebView?.goForward();
            }

            Animated.spring(horizontalDrag, {
              toValue: 0,
              useNativeDriver: true,
            }).start();
            Animated.spring(animVal, {
              toValue: 0,
              useNativeDriver: true,
            }).start();
            showBar();
            return;
          }

          Animated.spring(horizontalDrag, {
            toValue: 0,
            useNativeDriver: true,
          }).start();

          if (dy < -30) {
            setIsSearchActive(false);
            Animated.spring(animVal, {
              toValue: -SWAP_DISTANCE,
              tension: 60,
              friction: 9,
              useNativeDriver: true,
            }).start();
          } else {
            Animated.spring(animVal, {
              toValue: 0,
              useNativeDriver: true,
            }).start();
            snapBar(vy);
          }
        } else {
          if (dy > 30 || vy > 0.5) {
            setIsSearchActive(true);
            Animated.spring(animVal, {
              toValue: 0,
              tension: 60,
              friction: 9,
              useNativeDriver: true,
            }).start();
          } else {
            Animated.spring(animVal, {
              toValue: -SWAP_DISTANCE,
              tension: 60,
              friction: 9,
              useNativeDriver: true,
            }).start();
          }
        }
      },
    })
  ).current;

  const recallPanResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderMove: (_, gestureState) => {
        if (gestureState.dy < 0)
          scrollTranslateY.setValue(
            Math.max(0, HIDDEN_TRANSLATE_Y + gestureState.dy)
          );
      },
      onPanResponderRelease: (_, gestureState) => {
        if (gestureState.dy < -20 || gestureState.vy < -0.5) showBar();
        else
          Animated.spring(scrollTranslateY, {
            toValue: HIDDEN_TRANSLATE_Y,
            useNativeDriver: false,
          }).start(() => {
            currentScrollTrans.current = HIDDEN_TRANSLATE_Y;
          });
      },
    })
  ).current;

  const sheetPanResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) =>
        Math.abs(gestureState.dy) > 5,
      onPanResponderGrant: () =>
        overlayHeightAnim.stopAnimation((val) => {
          currentOverlayHeight.current = val;
        }),
      onPanResponderMove: (_, gestureState) => {
        overlayHeightAnim.setValue(
          Math.min(currentOverlayHeight.current - gestureState.dy, SNAP_FULL)
        );
      },
      onPanResponderRelease: (_, gestureState) => {
        const { dy, vy } = gestureState;
        const finalHeight = currentOverlayHeight.current - dy;
        let target = SNAP_DEFAULT;
        if (finalHeight > (SNAP_FULL + SNAP_DEFAULT) / 2) target = SNAP_FULL;
        else if (finalHeight > SNAP_DEFAULT * 0.7) target = SNAP_DEFAULT;
        else target = SNAP_CLOSED;
        if (vy < -1) target = SNAP_FULL;
        if (vy > 1)
          target = finalHeight > SNAP_DEFAULT ? SNAP_DEFAULT : SNAP_CLOSED;
        if (target === SNAP_CLOSED) closeOverlay();
        else {
          Animated.spring(overlayHeightAnim, {
            toValue: target,
            tension: 50,
            friction: 8,
            useNativeDriver: false,
          }).start();
          currentOverlayHeight.current = target;
        }
      },
    })
  ).current;

  // --- Styles ---
  const searchPillTranslateY = animVal;
  const searchPillOpacity = animVal.interpolate({
    inputRange: [-SWAP_DISTANCE, -SWAP_DISTANCE / 2, 0],
    outputRange: [0, 0, 1],
    extrapolate: "clamp",
  });
  const menuPillScale = animVal.interpolate({
    inputRange: [-SWAP_DISTANCE, 0],
    outputRange: [1, 0.9],
    extrapolate: "clamp",
  });
  const menuPillOpacity = animVal.interpolate({
    inputRange: [-SWAP_DISTANCE, -10, 0],
    outputRange: [1, 0, 0],
    extrapolate: "clamp",
  });
  const recallOpacity = scrollTranslateY.interpolate({
    inputRange: [0, HIDDEN_TRANSLATE_Y - 20, HIDDEN_TRANSLATE_Y],
    outputRange: [0, 0, 1],
    extrapolate: "clamp",
  });
  const backArrowOpacity = horizontalDrag.interpolate({
    inputRange: [0, 50],
    outputRange: [0, 1],
    extrapolate: "clamp",
  });
  const forwardArrowOpacity = horizontalDrag.interpolate({
    inputRange: [-50, 0],
    outputRange: [1, 0],
    extrapolate: "clamp",
  });
  const contentOpacity = horizontalDrag.interpolate({
    inputRange: [-50, 0, 50],
    outputRange: [0, 1, 0],
    extrapolate: "clamp",
  });

  const shouldShow = (label?: string) => {
    if (!label) return false;
    if (settingsSearch.trim() === "") return true;
    return label.toLowerCase().includes(settingsSearch.toLowerCase());
  };

  const SettingRow = ({
    label,
    children,
    onPress,
    hasSeparator,
  }: {
    label: string;
    children: React.ReactNode;
    onPress?: () => void;
    hasSeparator?: boolean;
  }) => {
    if (!shouldShow(label)) return null;
    const content = (
      <View
        style={[
          styles.settingRow,
          hasSeparator && { borderTopWidth: 1, borderColor: effectiveTheme.bg },
        ]}
      >
        {children}
      </View>
    );
    if (onPress)
      return <TouchableOpacity onPress={onPress}>{content}</TouchableOpacity>;
    return content;
  };

  const SettingsGroup = ({
    title,
    children,
  }: {
    title: string;
    children: React.ReactNode;
  }) => {
    const childrenArray = React.Children.toArray(children);
    const visibleChildren = childrenArray.filter(
      (child: any) => child && child.props && shouldShow(child.props.label)
    );
    if (visibleChildren.length === 0) return null;
    return (
      <>
        <Text
          style={[
            styles.sectionHeader,
            { color: effectiveTheme.textSec, fontFamily: "Nunito_700Bold" },
          ]}
        >
          {title}
        </Text>
        <View
          style={[
            styles.settingsGroup,
            {
              backgroundColor: effectiveTheme.card,
              borderRadius: cornerRadius,
            },
          ]}
        >
          {visibleChildren.map((child: any, index) =>
            React.cloneElement(child, { key: index, hasSeparator: index > 0 })
          )}
        </View>
      </>
    );
  };

  const renderOverlayContent = () => {
    let content = null;
    let title = "";

    if (activeView === "history") {
      title = "History";
      const filteredHistory = history.filter(
        (item) =>
          (item.title || "")
            .toLowerCase()
            .includes(historySearch.toLowerCase()) ||
          item.url.toLowerCase().includes(historySearch.toLowerCase())
      );

      content = (
        <View style={{ flex: 1 }}>
          <View
            style={{
              marginHorizontal: 20,
              marginTop: 20,
              marginBottom: 10,
              backgroundColor: effectiveTheme.inputBg,
              borderRadius: cornerRadius,
              flexDirection: "row",
              alignItems: "center",
              paddingHorizontal: 15,
              height: 50,
            }}
          >
            <Ionicons
              name="search"
              size={20}
              color={effectiveTheme.textSec}
              style={{ marginRight: 10 }}
            />
            <TextInput
              style={{
                flex: 1,
                color: effectiveTheme.text,
                fontFamily: "Nunito_600SemiBold",
                fontSize: 16,
              }}
              placeholder="Search History..."
              placeholderTextColor={effectiveTheme.textSec}
              value={historySearch}
              onFocus={expandToFullscreen}
              onChangeText={(text) => {
                LayoutAnimation.configureNext(
                  LayoutAnimation.Presets.easeInEaseOut
                );
                setHistorySearch(text);
              }}
            />
            {historySearch !== "" && (
              <TouchableOpacity
                onPress={() => {
                  setHistorySearch("");
                  Keyboard.dismiss();
                }}
              >
                <Ionicons
                  name="close-circle"
                  size={20}
                  color={effectiveTheme.textSec}
                />
              </TouchableOpacity>
            )}
          </View>

          <FlatList
            data={filteredHistory}
            keyExtractor={(item, index) => item.id + index}
            contentContainerStyle={{ padding: 20, paddingTop: 0 }}
            ListHeaderComponent={
              <Text
                style={[
                  styles.sectionHeader,
                  {
                    color: effectiveTheme.textSec,
                    fontFamily: "Nunito_700Bold",
                  },
                ]}
              >
                Recently Visited
              </Text>
            }
            renderItem={({ item }) => (
              <SwipeableHistoryRow
                item={item}
                theme={effectiveTheme}
                accent={accentColor}
                radius={cornerRadius}
                height={getHistoryHeight()}
                margin={getMargin()}
                fontScale={fontScale}
                onPress={() => {
                  const targetUrl = item.url;

                  // 1. Update Address Bar State immediately
                  setActiveUrl(targetUrl);
                  setInputUrl(getDisplayHost(targetUrl));

                  // 2. Update the Tabs State (This is what triggers the WebView to load the page)
                  setTabs((prev) =>
                    prev.map((t) =>
                      t.id === activeTabId
                        ? { ...t, url: targetUrl, title: item.title || getDisplayHost(targetUrl) }
                        : t
                    )
                  );

                  // 3. Close the menu
                  closeOverlay();
                }}
                onDelete={() => deleteHistoryItem(item.id)}
              />
            )}
            ListEmptyComponent={
              <View style={styles.emptyState}>
                <Ionicons
                  name="time-outline"
                  size={50}
                  color={effectiveTheme.textSec}
                />
                <Text
                  style={[
                    styles.emptyText,
                    {
                      color: effectiveTheme.text,
                      fontFamily: "Nunito_600SemiBold",
                    },
                  ]}
                >
                  No history found.
                </Text>
              </View>
            }
          />
        </View>
      );
    } else if (activeView === "tabs") {
      title = "Tabs";
      const filteredTabs = tabs.filter(
        (item) =>
          (item.title || "").toLowerCase().includes(tabsSearch.toLowerCase()) ||
          (item.url || "").toLowerCase().includes(tabsSearch.toLowerCase())
      );

      content = (
        <View style={{ flex: 1 }}>
          <View
            style={{
              marginHorizontal: 20,
              marginTop: 20,
              marginBottom: 10,
              backgroundColor: effectiveTheme.inputBg,
              borderRadius: cornerRadius,
              flexDirection: "row",
              alignItems: "center",
              paddingHorizontal: 15,
              height: 50,
            }}
          >
            <Ionicons
              name="search"
              size={20}
              color={effectiveTheme.textSec}
              style={{ marginRight: 10 }}
            />
            <TextInput
              style={{
                flex: 1,
                color: effectiveTheme.text,
                fontFamily: "Nunito_600SemiBold",
                fontSize: 16,
              }}
              placeholder="Search Tabs..."
              placeholderTextColor={effectiveTheme.textSec}
              value={tabsSearch}
              onFocus={expandToFullscreen}
              onChangeText={(text) => {
                LayoutAnimation.configureNext(
                  LayoutAnimation.Presets.easeInEaseOut
                );
                setTabsSearch(text);
              }}
            />
            {tabsSearch !== "" && (
              <TouchableOpacity
                onPress={() => {
                  setTabsSearch("");
                  Keyboard.dismiss();
                }}
              >
                <Ionicons
                  name="close-circle"
                  size={20}
                  color={effectiveTheme.textSec}
                />
              </TouchableOpacity>
            )}
          </View>

          <FlatList
            data={filteredTabs}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{
              padding: 20,
              paddingBottom: 100,
              paddingTop: 0,
            }}
            renderItem={({ item }) => (
              <SwipeableTabRow
                item={item}
                theme={effectiveTheme}
                accent={accentColor}
                radius={cornerRadius}
                height={getTabHeight()}
                margin={getMargin()}
                fontScale={fontScale}
                isActive={item.id === activeTabId}
                onPress={() => {
                  setActiveTabId(item.id);
                  setActiveUrl(item.url);
                  setInputUrl(getDisplayHost(item.url));
                }}
                onDelete={() => deleteTab(item.id)}
                onRename={() =>
                  openRenameModal(item.id, item.title, item.showLogo)
                }
              />
            )}
          />
          <View style={styles.fabContainer}>
            <TouchableOpacity
              style={[styles.fabButton, { backgroundColor: accentColor }]}
              onPress={() => addNewTab()}
            >
              <Ionicons name="add" size={32} color="#fff" />
            </TouchableOpacity>
          </View>
        </View>
      );
    } else if (activeView === "settings") {
      title = "Settings";
      content = (
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={{ padding: 20, paddingBottom: 100 }}
        >
          <View
            style={{
              marginBottom: 20,
              backgroundColor: effectiveTheme.inputBg,
              borderRadius: cornerRadius,
              flexDirection: "row",
              alignItems: "center",
              paddingHorizontal: 15,
              height: 50,
            }}
          >
            <Ionicons
              name="search"
              size={20}
              color={effectiveTheme.textSec}
              style={{ marginRight: 10 }}
            />
            <TextInput
              style={{
                flex: 1,
                color: effectiveTheme.text,
                fontFamily: "Nunito_600SemiBold",
                fontSize: 16,
              }}
              placeholder="Search Settings..."
              placeholderTextColor={effectiveTheme.textSec}
              value={settingsSearch}
              onFocus={expandToFullscreen}
              onChangeText={(text) => {
                LayoutAnimation.configureNext(
                  LayoutAnimation.Presets.easeInEaseOut
                );
                setSettingsSearch(text);
              }}
            />
            {settingsSearch !== "" && (
              <TouchableOpacity
                onPress={() => {
                  setSettingsSearch("");
                  Keyboard.dismiss();
                }}
              >
                <Ionicons
                  name="close-circle"
                  size={20}
                  color={effectiveTheme.textSec}
                />
              </TouchableOpacity>
            )}
          </View>

          <SettingsGroup title="Look & Feel">
            <SettingRow label="Theme">
              <Text
                style={[
                  styles.settingText,
                  {
                    color: effectiveTheme.text,
                    fontFamily: "Nunito_600SemiBold",
                    fontSize: 16 * fontScale,
                  },
                ]}
              >
                Theme
              </Text>
              <View style={{ flexDirection: "row" }}>
                {["light", "dark", "adaptive"].map((m) => (
                  <TouchableOpacity
                    key={m}
                    onPress={() => setThemeMode(m as any)}
                    style={[
                      styles.modeBtn,
                      themeMode === m && { backgroundColor: accentColor },
                    ]}
                  >
                    <Text
                      style={[
                        styles.modeBtnText,
                        themeMode === m
                          ? { color: "#fff" }
                          : { color: effectiveTheme.text },
                        {
                          fontFamily: "Nunito_700Bold",
                          fontSize: 12 * fontScale,
                        },
                      ]}
                    >
                      {m.charAt(0).toUpperCase() + m.slice(1)}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </SettingRow>

            <SettingRow label="Accent">
              <Text
                style={[
                  styles.settingText,
                  {
                    color: effectiveTheme.text,
                    fontFamily: "Nunito_600SemiBold",
                    fontSize: 16 * fontScale,
                  },
                ]}
              >
                Accent
              </Text>
              <View style={{ flexDirection: "row" }}>
                {ACCENTS.map((color) => (
                  <TouchableOpacity
                    key={color}
                    onPress={() => setAccentColor(color)}
                    style={[
                      styles.colorDot,
                      { backgroundColor: color },
                      accentColor === color && {
                        borderWidth: 2,
                        borderColor: effectiveTheme.text,
                      },
                    ]}
                  />
                ))}
              </View>
            </SettingRow>
          </SettingsGroup>

          <SettingsGroup title="Interface">
            <SettingRow label="Font Size">
              <View
                style={{
                  flexDirection: "column",
                  width: "100%",
                  justifyContent: "center",
                  paddingVertical: 5,
                }}
              >
                <View
                  style={{
                    flexDirection: "row",
                    width: "100%",
                    justifyContent: "space-between",
                    marginBottom: 15,
                    alignItems: "center",
                  }}
                >
                  <View style={{ flexDirection: "row", alignItems: "center" }}>
                    <Ionicons
                      name="text-outline"
                      size={22}
                      color={effectiveTheme.text}
                      style={{ marginRight: 10 }}
                    />
                    <Text
                      style={[
                        styles.settingText,
                        {
                          color: effectiveTheme.text,
                          fontFamily: "Nunito_600SemiBold",
                          fontSize: 16 * fontScale,
                        },
                      ]}
                    >
                      Font Size
                    </Text>
                  </View>
                  <Text
                    style={{
                      color: effectiveTheme.textSec,
                      fontFamily: "Nunito_700Bold",
                    }}
                  >
                    {(fontScale * 100).toFixed(0)}%
                  </Text>
                </View>
                <View
                  style={{
                    flexDirection: "row",
                    width: "100%",
                    justifyContent: "space-between",
                    alignItems: "center",
                    paddingHorizontal: 10,
                  }}
                >
                  <TouchableOpacity
                    onPress={() => setFontScale(Math.max(0.8, fontScale - 0.1))}
                  >
                    <Ionicons
                      name="remove-circle-outline"
                      size={28}
                      color={effectiveTheme.textSec}
                    />
                  </TouchableOpacity>
                  <View
                    style={{
                      height: 4,
                      flex: 1,
                      backgroundColor: effectiveTheme.bg,
                      marginHorizontal: 15,
                      borderRadius: 2,
                    }}
                  >
                    <View
                      style={{
                        height: "100%",
                        width: `${((fontScale - 0.8) / 0.4) * 100}%`,
                        backgroundColor: accentColor,
                        borderRadius: 2,
                      }}
                    />
                  </View>
                  <TouchableOpacity
                    onPress={() => setFontScale(Math.min(1.2, fontScale + 0.1))}
                  >
                    <Ionicons
                      name="add-circle-outline"
                      size={28}
                      color={effectiveTheme.textSec}
                    />
                  </TouchableOpacity>
                </View>
              </View>
            </SettingRow>

            <SettingRow label="Corner Radius">
              <View
                style={{
                  flexDirection: "column",
                  width: "100%",
                  justifyContent: "center",
                  paddingVertical: 5,
                }}
              >
                <View
                  style={{
                    flexDirection: "row",
                    width: "100%",
                    justifyContent: "space-between",
                    marginBottom: 15,
                    alignItems: "center",
                  }}
                >
                  <View style={{ flexDirection: "row", alignItems: "center" }}>
                    <Ionicons
                      name="shapes-outline"
                      size={22}
                      color={effectiveTheme.text}
                      style={{ marginRight: 10 }}
                    />
                    <Text
                      style={[
                        styles.settingText,
                        {
                          color: effectiveTheme.text,
                          fontFamily: "Nunito_600SemiBold",
                          fontSize: 16 * fontScale,
                        },
                      ]}
                    >
                      Corner Radius
                    </Text>
                  </View>
                  <Text
                    style={{
                      color: effectiveTheme.textSec,
                      fontFamily: "Nunito_700Bold",
                    }}
                  >
                    {Math.round(cornerRadius)}px
                  </Text>
                </View>
                <View
                  style={{
                    flexDirection: "row",
                    width: "100%",
                    justifyContent: "space-around",
                  }}
                >
                  {[0, 10, 22].map((rad, i) => (
                    <TouchableOpacity
                      key={i}
                      onPress={() => setCornerRadius(rad)}
                      style={{ alignItems: "center" }}
                    >
                      <View
                        style={{
                          width: 30,
                          height: 30,
                          borderWidth: 2,
                          borderColor: effectiveTheme.text,
                          borderRadius: rad === 22 ? 15 : rad === 10 ? 6 : 0,
                          marginBottom: 5,
                        }}
                      />
                      <Text
                        style={{
                          color: effectiveTheme.textSec,
                          fontSize: 10 * fontScale,
                        }}
                      >
                        {rad === 0 ? "Square" : rad === 10 ? "Soft" : "Round"}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            </SettingRow>

            <SettingRow label="UI Spacing">
              <View
                style={{
                  flexDirection: "column",
                  width: "100%",
                  justifyContent: "center",
                  paddingVertical: 5,
                }}
              >
                <View
                  style={{
                    flexDirection: "row",
                    alignItems: "center",
                    marginBottom: 15,
                  }}
                >
                  <Ionicons
                    name="resize-outline"
                    size={22}
                    color={effectiveTheme.text}
                    style={{ marginRight: 10 }}
                  />
                  <Text
                    style={[
                      styles.settingText,
                      {
                        color: effectiveTheme.text,
                        fontFamily: "Nunito_600SemiBold",
                        fontSize: 16 * fontScale,
                      },
                    ]}
                  >
                    UI Spacing
                  </Text>
                </View>
                <View
                  style={{
                    flexDirection: "row",
                    width: "100%",
                    justifyContent: "space-between",
                  }}
                >
                  {["compact", "normal", "airy"].map((mode) => (
                    <TouchableOpacity
                      key={mode}
                      onPress={() => setUiPadding(mode as any)}
                      style={[
                        styles.modeBtn,
                        uiPadding === mode && { backgroundColor: accentColor },
                        { paddingHorizontal: 20 },
                      ]}
                    >
                      <Text
                        style={[
                          styles.modeBtnText,
                          uiPadding === mode
                            ? { color: "#fff" }
                            : { color: effectiveTheme.text },
                          {
                            fontFamily: "Nunito_700Bold",
                            fontSize: 12 * fontScale,
                          },
                        ]}
                      >
                        {mode.charAt(0).toUpperCase() + mode.slice(1)}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            </SettingRow>

            <SettingRow label="Pill Height">
              <View
                style={{
                  flexDirection: "column",
                  width: "100%",
                  justifyContent: "center",
                  paddingVertical: 5,
                }}
              >
                <View
                  style={{
                    flexDirection: "row",
                    width: "100%",
                    justifyContent: "space-between",
                    marginBottom: 15,
                    alignItems: "center",
                  }}
                >
                  <View style={{ flexDirection: "row", alignItems: "center" }}>
                    <Ionicons
                      name="scan-outline"
                      size={22}
                      color={effectiveTheme.text}
                      style={{ marginRight: 10 }}
                    />
                    <Text
                      style={[
                        styles.settingText,
                        {
                          color: effectiveTheme.text,
                          fontFamily: "Nunito_600SemiBold",
                          fontSize: 16 * fontScale,
                        },
                      ]}
                    >
                      Pill Height
                    </Text>
                  </View>
                  <Text
                    style={{
                      color: effectiveTheme.textSec,
                      fontFamily: "Nunito_700Bold",
                    }}
                  >
                    {pillHeight}px
                  </Text>
                </View>
                <View
                  style={{
                    flexDirection: "row",
                    width: "100%",
                    justifyContent: "space-between",
                    alignItems: "center",
                    paddingHorizontal: 10,
                  }}
                >
                  <TouchableOpacity
                    onPress={() => setPillHeight(Math.max(60, pillHeight - 2))}
                  >
                    <Ionicons
                      name="remove-circle-outline"
                      size={28}
                      color={effectiveTheme.textSec}
                    />
                  </TouchableOpacity>
                  <View
                    style={{
                      height: 4,
                      flex: 1,
                      backgroundColor: effectiveTheme.bg,
                      marginHorizontal: 15,
                      borderRadius: 2,
                    }}
                  >
                    <View
                      style={{
                        height: "100%",
                        width: `${((pillHeight - 60) / 20) * 100}%`,
                        backgroundColor: accentColor,
                        borderRadius: 2,
                      }}
                    />
                  </View>
                  <TouchableOpacity
                    onPress={() => setPillHeight(Math.min(80, pillHeight + 2))}
                  >
                    <Ionicons
                      name="add-circle-outline"
                      size={28}
                      color={effectiveTheme.textSec}
                    />
                  </TouchableOpacity>
                </View>
              </View>
            </SettingRow>

            <SettingRow label="Pill Transparency">
              <View
                style={{
                  flexDirection: "column",
                  width: "100%",
                  justifyContent: "center",
                  paddingVertical: 5,
                }}
              >
                <View
                  style={{
                    flexDirection: "row",
                    alignItems: "center",
                    marginBottom: 15,
                  }}
                >
                  <Ionicons
                    name="water-outline"
                    size={22}
                    color={effectiveTheme.text}
                    style={{ marginRight: 10 }}
                  />
                  <Text
                    style={[
                      styles.settingText,
                      {
                        color: effectiveTheme.text,
                        fontFamily: "Nunito_600SemiBold",
                        fontSize: 16 * fontScale,
                      },
                    ]}
                  >
                    Pill Transparency
                  </Text>
                </View>
                <View
                  style={{
                    flexDirection: "row",
                    width: "100%",
                    justifyContent: "space-between",
                  }}
                >
                  {["opaque", "frosted", "ghost"].map((mode) => (
                    <TouchableOpacity
                      key={mode}
                      onPress={() => setBarTransparency(mode as any)}
                      style={[
                        styles.modeBtn,
                        barTransparency === mode && {
                          backgroundColor: accentColor,
                        },
                        { paddingHorizontal: 20 },
                      ]}
                    >
                      <Text
                        style={[
                          styles.modeBtnText,
                          barTransparency === mode
                            ? { color: "#fff" }
                            : { color: effectiveTheme.text },
                          {
                            fontFamily: "Nunito_700Bold",
                            fontSize: 12 * fontScale,
                          },
                        ]}
                      >
                        {mode.charAt(0).toUpperCase() + mode.slice(1)}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            </SettingRow>
            <SettingRow label="Pill Loading Bar">
              <View
                style={{
                  flexDirection: "column",
                  width: "100%",
                  justifyContent: "center",
                  paddingVertical: 5,
                }}
              >
                <View
                  style={{
                    flexDirection: "row",
                    alignItems: "center",
                    marginBottom: 15,
                  }}
                >
                  <Ionicons
                    name="hourglass-outline"
                    size={22}
                    color={effectiveTheme.text}
                    style={{ marginRight: 10 }}
                  />
                  <Text
                    style={[
                      styles.settingText,
                      {
                        color: effectiveTheme.text,
                        fontFamily: "Nunito_600SemiBold",
                        fontSize: 16 * fontScale,
                      },
                    ]}
                  >
                    Pill Loading Bar
                  </Text>
                </View>
                <View
                  style={{
                    flexDirection: "row",
                    width: "100%",
                    justifyContent: "space-between",
                  }}
                >
                  {["ltr", "center", "none"].map((mode) => (
                    <TouchableOpacity
                      key={mode}
                      onPress={() => setProgressBarMode(mode as any)}
                      style={[
                        styles.modeBtn,
                        progressBarMode === mode && {
                          backgroundColor: accentColor,
                        },
                        { paddingHorizontal: 15 }, // Adjusted padding to fit 3 buttons
                      ]}
                    >
                      <Text
                        style={[
                          styles.modeBtnText,
                          progressBarMode === mode
                            ? { color: "#fff" }
                            : { color: effectiveTheme.text },
                          {
                            fontFamily: "Nunito_700Bold",
                            fontSize: 12 * fontScale,
                          },
                        ]}
                      >
                        {mode === "ltr"
                          ? "Standard"
                          : mode === "center"
                          ? "Center Out"
                          : "Hidden"}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            </SettingRow>
          </SettingsGroup>
          <SettingsGroup title="Browsing">
            {shouldShow("Search Engine") && (
              <View label="Search Engine">
                <TouchableOpacity
                  onPress={() => {
                    LayoutAnimation.configureNext(
                      LayoutAnimation.Presets.easeInEaseOut
                    );
                    setIsSearchEngineOpen(!isSearchEngineOpen);
                  }}
                >
                  <View style={[styles.settingRow]}>
                    <View
                      style={{ flexDirection: "row", alignItems: "center" }}
                    >
                      <Ionicons
                        name="search-outline"
                        size={22}
                        color={effectiveTheme.text}
                        style={{ marginRight: 10 }}
                      />
                      <Text
                        style={[
                          styles.settingText,
                          {
                            color: effectiveTheme.text,
                            fontFamily: "Nunito_600SemiBold",
                            fontSize: 16 * fontScale,
                          },
                        ]}
                      >
                        Search Engine
                      </Text>
                    </View>
                    <View
                      style={{ flexDirection: "row", alignItems: "center" }}
                    >
                      <Ionicons
                        name={SEARCH_ENGINES[searchEngineIndex].icon as any}
                        size={18}
                        color={effectiveTheme.text}
                        style={{ marginRight: 5 }}
                      />
                      <Text
                        style={{
                          color: effectiveTheme.textSec,
                          marginRight: 5,
                          fontFamily: "Nunito_600SemiBold",
                          fontSize: 14 * fontScale,
                        }}
                      >
                        {SEARCH_ENGINES[searchEngineIndex].name}
                      </Text>
                      <Ionicons
                        name={
                          isSearchEngineOpen ? "chevron-up" : "chevron-down"
                        }
                        size={16}
                        color={effectiveTheme.textSec}
                      />
                    </View>
                  </View>
                </TouchableOpacity>
                {isSearchEngineOpen && (
                  <View
                    style={{
                      borderTopWidth: 1,
                      borderColor: effectiveTheme.bg,
                    }}
                  >
                    {SEARCH_ENGINES.map((engine, index) => (
                      <TouchableOpacity
                        key={engine.name}
                        style={[
                          styles.settingRow,
                          { paddingLeft: 40, paddingVertical: 12 },
                        ]}
                        onPress={() => {
                          setSearchEngineIndex(index);
                          LayoutAnimation.configureNext(
                            LayoutAnimation.Presets.easeInEaseOut
                          );
                          setIsSearchEngineOpen(false);
                        }}
                      >
                        <View
                          style={{ flexDirection: "row", alignItems: "center" }}
                        >
                          <Ionicons
                            name={engine.icon as any}
                            size={20}
                            color={effectiveTheme.text}
                            style={{ marginRight: 10 }}
                          />
                          <Text
                            style={{
                              color: effectiveTheme.text,
                              fontFamily: "Nunito_600SemiBold",
                              fontSize: 16 * fontScale,
                            }}
                          >
                            {engine.name}
                          </Text>
                        </View>
                        {searchEngineIndex === index && (
                          <Ionicons
                            name="checkmark"
                            size={18}
                            color={accentColor}
                          />
                        )}
                      </TouchableOpacity>
                    ))}
                  </View>
                )}
              </View>
            )}

            <SettingRow label="Startup Behavior">
              <View style={{ flexDirection: "column", width: "100%", paddingVertical: 5 }}>
                 <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 15 }}>
                    <Ionicons name="power-outline" size={22} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                    <Text style={[styles.settingText, { color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold", fontSize: 16 * fontScale }]}>
                      On Startup
                    </Text>
                 </View>
                 <View style={{ flexDirection: "row", width: "100%" }}>
                    {["new", "last"].map((mode) => (
                      <TouchableOpacity
                        key={mode}
                        onPress={() => setStartupTabMode(mode as any)}
                        style={[
                          styles.modeBtn,
                          startupTabMode === mode && { backgroundColor: accentColor },
                          { flex: 1, alignItems: 'center', justifyContent: 'center', marginHorizontal: 2 }
                        ]}
                      >
                        <Text style={[
                            styles.modeBtnText,
                            startupTabMode === mode ? { color: "#fff" } : { color: effectiveTheme.text },
                            { fontFamily: "Nunito_700Bold", fontSize: 12 * fontScale }
                          ]}
                        >
                          {mode === "new" ? "New Tab" : "Continue Session"}
                        </Text>
                      </TouchableOpacity>
                    ))}
                 </View>
              </View>
            </SettingRow>

            <SettingRow label="Incognito Mode">
              <View style={{ flexDirection: "row", alignItems: "center" }}>
                <Ionicons
                  name="eye-off-outline"
                  size={22}
                  color={effectiveTheme.text}
                  style={{ marginRight: 10 }}
                />
                <Text
                  style={[
                    styles.settingText,
                    {
                      color: effectiveTheme.text,
                      fontFamily: "Nunito_600SemiBold",
                      fontSize: 16 * fontScale,
                    },
                  ]}
                >
                  Incognito Mode
                </Text>
              </View>
              <Switch
                value={incognitoMode}
                onValueChange={setIncognitoMode}
                trackColor={{ false: "#767577", true: accentColor }}
                thumbColor={"#f4f3f4"}
              />
            </SettingRow>

            <SettingRow label="Desktop Mode">
              <View style={{ flexDirection: "row", alignItems: "center" }}>
                <Ionicons
                  name="desktop-outline"
                  size={22}
                  color={effectiveTheme.text}
                  style={{ marginRight: 10 }}
                />
                <Text
                  style={[
                    styles.settingText,
                    {
                      color: effectiveTheme.text,
                      fontFamily: "Nunito_600SemiBold",
                      fontSize: 16 * fontScale,
                    },
                  ]}
                >
                  Desktop Mode
                </Text>
              </View>
              <Switch
                value={desktopMode}
                onValueChange={setDesktopMode}
                trackColor={{ false: "#767577", true: accentColor }}
                thumbColor={"#f4f3f4"}
              />
            </SettingRow>

            <SettingRow label="Reader View">
              <View style={{ flexDirection: "row", alignItems: "center" }}>
                <Ionicons
                  name="book-outline"
                  size={22}
                  color={effectiveTheme.text}
                  style={{ marginRight: 10 }}
                />
                <Text
                  style={[
                    styles.settingText,
                    {
                      color: effectiveTheme.text,
                      fontFamily: "Nunito_600SemiBold",
                      fontSize: 16 * fontScale,
                    },
                  ]}
                >
                  Reader View
                </Text>
              </View>
              <Switch
                value={readerMode}
                onValueChange={setReaderMode}
                trackColor={{ false: "#767577", true: accentColor }}
                thumbColor={"#f4f3f4"}
              />
            </SettingRow>
          </SettingsGroup>

          <SettingsGroup title="Privacy">
            <SettingRow label="Enable JavaScript">
              <View style={{ flexDirection: "row", alignItems: "center" }}>
                <Ionicons
                  name="code-slash-outline"
                  size={22}
                  color={effectiveTheme.text}
                  style={{ marginRight: 10 }}
                />
                <Text
                  style={[
                    styles.settingText,
                    {
                      color: effectiveTheme.text,
                      fontFamily: "Nunito_600SemiBold",
                      fontSize: 16 * fontScale,
                    },
                  ]}
                >
                  Enable JavaScript
                </Text>
              </View>
              <Switch
                value={jsEnabled}
                onValueChange={setJsEnabled}
                trackColor={{ false: "#767577", true: accentColor }}
                thumbColor={"#f4f3f4"}
              />
            </SettingRow>

            <SettingRow label="HTTPS Only">
              <View style={{ flexDirection: "row", alignItems: "center" }}>
                <Ionicons
                  name="lock-closed-outline"
                  size={22}
                  color={effectiveTheme.text}
                  style={{ marginRight: 10 }}
                />
                <Text
                  style={[
                    styles.settingText,
                    {
                      color: effectiveTheme.text,
                      fontFamily: "Nunito_600SemiBold",
                      fontSize: 16 * fontScale,
                    },
                  ]}
                >
                  HTTPS Only
                </Text>
              </View>
              <Switch
                value={httpsOnly}
                onValueChange={setHttpsOnly}
                trackColor={{ false: "#767577", true: accentColor }}
                thumbColor={"#f4f3f4"}
              />
            </SettingRow>

            <SettingRow label="Block Cookies">
              <View style={{ flexDirection: "row", alignItems: "center" }}>
                <Ionicons
                  name="eye-off-outline"
                  size={22}
                  color={effectiveTheme.text}
                  style={{ marginRight: 10 }}
                />
                <Text
                  style={[
                    styles.settingText,
                    {
                      color: effectiveTheme.text,
                      fontFamily: "Nunito_600SemiBold",
                      fontSize: 16 * fontScale,
                    },
                  ]}
                >
                  Block Cookies
                </Text>
              </View>
              <Switch
                value={blockCookies}
                onValueChange={setBlockCookies}
                trackColor={{ false: "#767577", true: accentColor }}
                thumbColor={"#f4f3f4"}
              />
            </SettingRow>
          </SettingsGroup>

          <SettingsGroup title="Data">
            <SettingRow
                label="Clear Cache"
                // UPDATED: Now calls the request function
                onPress={requestClearCache}
                >
              <View style={{ flexDirection: "row", alignItems: "center" }}>
                <Ionicons
                  name="file-tray-full-outline"
                  size={22}
                  color={effectiveTheme.text}
                  style={{ marginRight: 10 }}
                />
                <Text
                  style={[
                    styles.settingText,
                    {
                      color: effectiveTheme.text,
                      fontFamily: "Nunito_600SemiBold",
                      fontSize: 16 * fontScale,
                    },
                  ]}
                >
                  Clear Cache
                </Text>
              </View>
              <Ionicons
                name="chevron-forward"
                size={16}
                color={effectiveTheme.textSec}
              />
            </SettingRow>

            {shouldShow("Clear History") && (
              <View label="Clear History">
                <TouchableOpacity
                  onPress={() => {
                    LayoutAnimation.configureNext(
                      LayoutAnimation.Presets.easeInEaseOut
                    );
                    setIsClearHistoryOpen(!isClearHistoryOpen);
                  }}
                >
                  <View
                    style={[
                      styles.settingRow,
                      { borderTopWidth: 1, borderColor: effectiveTheme.bg },
                    ]}
                  >
                    <View
                      style={{ flexDirection: "row", alignItems: "center" }}
                    >
                      <Ionicons
                        name="trash-outline"
                        size={22}
                        color="#ff3b30"
                        style={{ marginRight: 10 }}
                      />
                      <Text
                        style={[
                          styles.settingText,
                          {
                            color: "#ff3b30",
                            fontFamily: "Nunito_600SemiBold",
                            fontSize: 16 * fontScale,
                          },
                        ]}
                      >
                        Clear History
                      </Text>
                    </View>
                    <Ionicons
                      name={isClearHistoryOpen ? "chevron-up" : "chevron-down"}
                      size={16}
                      color={effectiveTheme.textSec}
                    />
                  </View>
                </TouchableOpacity>
                {isClearHistoryOpen && (
                  <View
                    style={{
                      borderTopWidth: 1,
                      borderColor: effectiveTheme.bg,
                    }}
                  >
                    {HISTORY_RANGES.map((range, index) => (
                      <TouchableOpacity
                        key={index}
                        style={[
                          styles.settingRow,
                          { paddingLeft: 40, paddingVertical: 12 },
                        ]}
                        // UPDATED: Passes label and ms to request function
                        onPress={() => requestClearHistory(range.ms, range.label)}
                      >
                        <Text
                          style={{
                            color: effectiveTheme.text,
                            fontFamily: "Nunito_600SemiBold",
                            fontSize: 16 * fontScale,
                          }}
                        >
                          {range.label}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                )}
              </View>
            )}
          </SettingsGroup>

          <Text style={styles.versionText}>mi. browser v{APP_VERSION}</Text>
        </ScrollView>
      );
    }

    if (!fontsLoaded || !isAppReady)
      return (
        <View
          style={{
            flex: 1,
            backgroundColor: "#000",
            justifyContent: "center",
            alignItems: "center",
          }}
        >
          <ActivityIndicator size="large" color="#007AFF" />
        </View>
      );

    return (
      <View style={{ flex: 1 }} ref={viewShotRef} collapsable={false}>
        <Animated.View
          style={[
            styles.sheetContainer,
            {
              height: overlayHeightAnim,
              backgroundColor: effectiveTheme.surface,
              borderTopLeftRadius: cornerRadius,
              borderTopRightRadius: cornerRadius,
            },
          ]}
        >
          <View
            style={[
              styles.sheetHeader,
              {
                backgroundColor: effectiveTheme.sheetHeader,
                borderBottomColor: effectiveTheme.bg,
              },
            ]}
            {...sheetPanResponder.panHandlers}
          >
            <View style={styles.sheetHandle} />
            <View style={styles.sheetHeaderRow}>
              <Text
                style={[
                  styles.sheetTitle,
                  {
                    color: effectiveTheme.text,
                    fontFamily: "Nunito_800ExtraBold",
                    fontSize: 22 * fontScale,
                  },
                ]}
              >
                {title}
              </Text>
              <View style={{ flexDirection: "row" }}>
                <TouchableOpacity onPress={closeOverlay} style={styles.iconBtn}>
                  <Ionicons name="close-circle" size={28} color={accentColor} />
                </TouchableOpacity>
              </View>
            </View>
          </View>
          <Animated.View style={{ flex: 1, paddingBottom: keyboardHeight }}>
            {content}
          </Animated.View>
        </Animated.View>

        {/* Confirmation Modal */}
        <Modal
          visible={isConfirmModalVisible}
          transparent
          animationType="fade"
          onRequestClose={() => setIsConfirmModalVisible(false)}
        >
          <View style={styles.modalOverlay}>
            <View
              style={[
                styles.modalContent,
                {
                  backgroundColor: effectiveTheme.surface,
                  borderRadius: cornerRadius,
                },
              ]}
            >
              <Text
                style={[
                  styles.modalTitle,
                  { color: effectiveTheme.text, fontFamily: "Nunito_700Bold" },
                ]}
              >
                Are you sure?
              </Text>
              
              <Text
                style={{
                  color: effectiveTheme.textSec,
                  fontFamily: "Nunito_600SemiBold",
                  marginBottom: 20,
                  fontSize: 16
                }}
              >
                {confirmActionType === 'cache' 
                    ? "This will clear all cache for the current website."
                    : `This will permanently delete history for: ${confirmHistoryPayload?.label}.`
                }
              </Text>

              <View style={styles.modalButtons}>
                <TouchableOpacity
                  onPress={() => setIsConfirmModalVisible(false)}
                  style={[styles.modalBtn, { borderRadius: cornerRadius / 2 }]}
                >
                  <Text
                    style={{
                      color: effectiveTheme.textSec,
                      fontFamily: "Nunito_700Bold",
                    }}
                  >
                    Cancel
                  </Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={executeConfirmAction}
                  style={[
                    styles.modalBtn,
                    {
                      backgroundColor: "#ff3b30", // Red for destructive action
                      borderRadius: cornerRadius / 2,
                    },
                  ]}
                >
                  <Text
                    style={{
                      color: "#fff",
                      fontWeight: "bold",
                      fontFamily: "Nunito_700Bold",
                    }}
                  >
                    {confirmActionType === 'cache' ? "Clear Cache" : "Delete"}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        </Modal>

        {/* Rename Modal */}
        <Modal
          visible={isRenameModalVisible}
          transparent
          animationType="fade"
          onRequestClose={() => setIsRenameModalVisible(false)}
        >
          <View style={styles.modalOverlay}>
            <View
              style={[
                styles.modalContent,
                {
                  backgroundColor: effectiveTheme.surface,
                  borderRadius: cornerRadius,
                },
              ]}
            >
              <Text
                style={[
                  styles.modalTitle,
                  { color: effectiveTheme.text, fontFamily: "Nunito_700Bold" },
                ]}
              >
                Edit Tab
              </Text>
              <TextInput
                style={[
                  styles.modalInput,
                  {
                    backgroundColor: effectiveTheme.inputBg,
                    color: effectiveTheme.text,
                    fontFamily: "Nunito_600SemiBold",
                    borderRadius: cornerRadius / 2,
                  },
                ]}
                value={renameText}
                onChangeText={setRenameText}
                autoFocus
              />

              <View
                style={{
                  flexDirection: "row",
                  alignItems: "center",
                  justifyContent: "space-between",
                  marginBottom: 20,
                }}
              >
                <Text
                  style={{
                    color: effectiveTheme.text,
                    fontFamily: "Nunito_600SemiBold",
                  }}
                >
                  Show Site Logo
                </Text>
                <Switch
                  value={renameShowLogo}
                  onValueChange={setRenameShowLogo}
                  trackColor={{ false: "#767577", true: accentColor }}
                  thumbColor={"#f4f3f4"}
                />
              </View>

              <View style={styles.modalButtons}>
                <TouchableOpacity
                  onPress={() => setIsRenameModalVisible(false)}
                  style={[styles.modalBtn, { borderRadius: cornerRadius / 2 }]}
                >
                  <Text
                    style={{
                      color: effectiveTheme.textSec,
                      fontFamily: "Nunito_700Bold",
                    }}
                  >
                    Cancel
                  </Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={saveRenameTab}
                  style={[
                    styles.modalBtn,
                    {
                      backgroundColor: accentColor,
                      borderRadius: cornerRadius / 2,
                    },
                  ]}
                >
                  <Text
                    style={{
                      color: "#fff",
                      fontWeight: "bold",
                      fontFamily: "Nunito_700Bold",
                    }}
                  >
                    Save
                  </Text>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        </Modal>
      </View>
    );
  };

  // 1. Re-define the handler to be accessible
  const handleShouldStartLoadWithRequest = (request: any) => {
    const { url } = request;
    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")) {
      return true;
    }
    Linking.openURL(url).catch(err => console.error("Link Error:", err));
    return false;
  };

  // 2. Create a Factory for Tab Props
  const getWebViewProps = (tabId: string) => ({
    ref: (ref: WebView | null) => (webViewRefs.current[tabId] = ref),
    source: { uri: tabs.find((t) => t.id === tabId)?.url || "" },
    originWhitelist: ["*"],
    onShouldStartLoadWithRequest: handleShouldStartLoadWithRequest,
    
    // NAVIGATION CHANGE
    onNavigationStateChange: (navState: any) => {
      const { url, title, canGoBack, canGoForward, loading } = navState;

      // A. Always update the specific tab's state in the list
      setTabs((prev) =>
        prev.map((t) => {
          if (t.id !== tabId) return t;
          return {
            ...t,
            url,
            title: (title && title.length > 0) ? title : getDisplayHost(url),
            canGoBack,
            canGoForward,
            loading
          };
        })
      );

      // B. If this is the ACTIVE tab, update the Global UI refs immediately
      if (tabId === activeTabId) {
        setCanGoBack(canGoBack);
        setCanGoForward(canGoForward);
        canGoBackRef.current = canGoBack;
        canGoForwardRef.current = canGoForward;
        setIsLoading(loading);

        if (!isInputFocused && url) {
          setActiveUrl(url);
          setInputUrl(getDisplayHost(url));
        }
        
        // Add to history (Active tab only)
        if (url && !loading && url !== "about:blank") {
             addToHistory(url);
        }
      }
    },

    // PROGRESS
    onLoadProgress: ({ nativeEvent }: any) => {
      if (tabId === activeTabId) {
        Animated.timing(progressAnim, {
          toValue: nativeEvent.progress,
          duration: 200,
          useNativeDriver: false,
        }).start();
      }
    },

    // LOADING START
    onLoadStart: () => {
      if (tabId === activeTabId) {
        setIsLoading(true);
        progressAnim.setValue(0);
        Animated.timing(progressAnim, { toValue: 0.1, duration: 300, useNativeDriver: false }).start();
      }
    },

    // LOADING END
    onLoadEnd: () => {
      if (tabId === activeTabId) {
        setIsLoading(false);
        Animated.timing(progressAnim, { toValue: 1, duration: 200, useNativeDriver: false }).start(() => {
          setTimeout(() => progressAnim.setValue(0), 200);
        });
      }
    },
    
    // ERROR HANDLING
    onError: (e: any) => {
       if (tabId === activeTabId) setIsLoading(false);
       
       // DNS Failure / Search Fallback logic
       const { nativeEvent } = e;
       if (nativeEvent.description === "net::ERR_NAME_NOT_RESOLVED" || nativeEvent.code === -2) {
            const failedUrl = nativeEvent.url;
            const isAlreadySearch = SEARCH_ENGINES.some(se => failedUrl?.startsWith(se.url));
            if (!isAlreadySearch && failedUrl && tabId === activeTabId) {
                let query = failedUrl.replace(/^https?:\/\//, "").replace(/\/$/, "");
                const searchUrl = `${SEARCH_ENGINES[searchEngineIndex].url}${encodeURIComponent(query)}`;
                // Redirect via state update
                setTabs(prev => prev.map(t => t.id === tabId ? {...t, url: searchUrl} : t));
                if(tabId === activeTabId) {
                    setActiveUrl(searchUrl);
                    setInputUrl(query);
                }
            }
       }
    },

    // COMMON PROPS
    onMessage: handleWebViewMessage,
    onScroll: handleScroll,
    onScrollEndDrag: () => snapBar(0),
    onMomentumScrollEnd: () => snapBar(0),
    onTouchStart: () => { if (isInputFocused) Keyboard.dismiss(); },
    overScrollMode: "never",
    scrollEventThrottle: 16,
    startInLoadingState: true,
    renderLoading: () => (
      <View style={styles.loadingOverlay}>
        <ActivityIndicator size="large" color={accentColor} />
      </View>
    ),
    javaScriptEnabled: jsEnabled,
    userAgent: desktopMode ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" : undefined,
    sharedCookiesEnabled: !blockCookies,
    domStorageEnabled: true,
    androidLayerType: "hardware" as const,
    pullToRefreshEnabled: false,
    incognito: incognitoMode,
    allowsFullscreenVideo: true,
    mediaPlaybackRequiresUserAction: false,
    javaScriptCanOpenWindowsAutomatically: true,
    onFullScreen: handleFullScreen,
    contentInset: isFullscreen ? { top: 0, bottom: 0, left: 0, right: 0 } : { bottom: pillHeight + 20 },
    geolocationEnabled: true,
    onPermissionRequest: handleAndroidPermissionRequest,
  });

  if (!fontsLoaded || !isAppReady) return null;

  return (
    <View style={[styles.container, { backgroundColor: effectiveTheme.bg }]}>
      {/* Status Bar is handled by the handleFullScreen function now, but we keep this for default state */}
      {!isFullscreen && (
        <StatusBar
          translucent
          backgroundColor="transparent"
          barStyle={themeMode === "dark" ? "light-content" : "dark-content"}
        />
      )}

      <Animated.View
        style={[
          styles.webViewContainer,
          {
            paddingBottom: isFullscreen 
              ? 0 
              : Animated.add(keyboardHeight, insets.bottom),
            backgroundColor: effectiveTheme.bg,
          },
        ]}
      >
        {/* 1. RENDER ALL TABS WITH URLs */}
        {tabs.map((tab) => {
          // If tab has no URL (New Tab), don't render a WebView for it
          if (!tab.url) return null;

          const isActive = tab.id === activeTabId;
          
          return (
            <View 
                key={tab.id} 
                style={[
                    StyleSheet.absoluteFill, 
                    { 
                        // Hide inactive tabs using opacity/z-index to keep them alive
                        opacity: isActive ? 1 : 0,
                        zIndex: isActive ? 1 : 0,
                        // Important: move them off-screen if hidden to prevent touch events
                        transform: [{ translateX: isActive ? 0 : 9999 }] 
                    }
                ]}
            >
                <WebView
                    {...getWebViewProps(tab.id)}
                    style={isFullscreen ? { flex: 1, backgroundColor: "#000" } : { flex: 1 }}
                />
            </View>
          );
        })}

        {/* 2. RENDER HOME SCREEN (If active tab has no URL) */}
        {!activeUrl && (
          <View
            style={[
              styles.homeContainer,
              { backgroundColor: effectiveTheme.bg },
            ]}
            onTouchStart={() => {
              if (isInputFocused) Keyboard.dismiss();
            }}
          >
            <Animated.View
              style={{
                transform: [
                  { scale: logoScale },
                  { translateX: logoPan.x },
                  { translateY: logoPan.y },
                ],
                zIndex: 10,
                padding: 20,
              }}
              {...logoResponder.panHandlers}
            >
              <Text
                style={[
                  styles.homeText,
                  {
                    color: effectiveTheme.text,
                    fontFamily: "Nunito_800ExtraBold",
                  },
                ]}
              >
                {homeLogoText}
              </Text>
            </Animated.View>
          </View>
        )}
      </Animated.View>

      {/* WRAP THE REST OF THE UI IN A CONDITIONAL CHECK */}
      {!isFullscreen && (
        <>
          {activeView !== "none" && (
            <View style={styles.overlayBackdrop}>
              <TouchableWithoutFeedback onPress={closeOverlay}>
                <View style={styles.backdropTouchArea} />
              </TouchableWithoutFeedback>
              {renderOverlayContent()}
            </View>
          )}

          <Animated.View
            style={[
              styles.recallContainer,
              { 
                 opacity: recallOpacity,
                 bottom: Math.max(insets.bottom + 10, 10)
              }
            ]}
            pointerEvents="box-none"
          >
            <TouchableOpacity
              activeOpacity={0.8}
              onPress={showBar}
              {...recallPanResponder.panHandlers}
              style={[
                styles.recallButton,
                {
                  // 1. Base Layer: The Glass (provides the opacity/blur solidness)
                  backgroundColor: effectiveTheme.glass,
                  // Remove the border to match the input style
                  borderWidth: 0,
                  borderRadius: 25,
                  overflow: "hidden", // Ensures the inner view respects the circle
                },
              ]}
            >
              {/* 2. Tint Layer: The Input Background (sits on top of glass) */}
              <View
                style={{
                  ...StyleSheet.absoluteFillObject,
                  backgroundColor: effectiveTheme.inputBg,
                }}
              />
              
              {/* Icon sits on top */}
              <Ionicons
                name="chevron-up"
                size={24}
                color={effectiveTheme.text}
              />
            </TouchableOpacity>
          </Animated.View>

          {activeView === "none" && (
            <View style={styles.floatingLayer} pointerEvents="box-none">
              <Animated.View
                style={[
                  styles.bottomAreaContainer,
                  {
                    paddingBottom: Math.max(insets.bottom + 10, 10),
                    
                    transform: [
                      {
                        translateY: Animated.subtract(
                          scrollTranslateY,
                          keyboardHeight
                        ),
                      },
                    ],
                  },
                ]}
              >
                <View
                  style={[styles.gestureArea, { height: pillHeight }]}
                  {...panResponder.panHandlers}
                >
                  {/* Menu Pill */}
                  <Animated.View
                    style={[
                      styles.pillBase,
                      {
                        height: pillHeight,
                        backgroundColor: effectiveTheme.glass,
                        borderColor: effectiveTheme.glassBorder,
                        borderRadius: cornerRadius * 2,
                      },
                      {
                        zIndex: 1,
                        opacity: menuPillOpacity,
                        transform: [{ scale: menuPillScale }],
                      },
                    ]}
                  >
                    <View style={styles.barTabContent}>
                      <TouchableOpacity
                        style={styles.menuItem}
                        onPress={() => setActiveView("tabs")}
                      >
                        <Ionicons
                          name="copy-outline"
                          size={24}
                          color={effectiveTheme.text}
                        />
                        <Text
                          style={[
                            styles.menuLabel,
                            {
                              color: effectiveTheme.text,
                              fontFamily: "Nunito_700Bold",
                            },
                          ]}
                        >
                          Tabs
                        </Text>
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={styles.menuItem}
                        onPress={() => setActiveView("history")}
                      >
                        <Ionicons
                          name="time-outline"
                          size={24}
                          color={effectiveTheme.text}
                        />
                        <Text
                          style={[
                            styles.menuLabel,
                            {
                              color: effectiveTheme.text,
                              fontFamily: "Nunito_700Bold",
                            },
                          ]}
                        >
                          History
                        </Text>
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={styles.menuItem}
                        onPress={() => setActiveView("settings")}
                      >
                        <Ionicons
                          name="settings-outline"
                          size={24}
                          color={effectiveTheme.text}
                        />
                        <Text
                          style={[
                            styles.menuLabel,
                            {
                              color: effectiveTheme.text,
                              fontFamily: "Nunito_700Bold",
                            },
                          ]}
                        >
                          Settings
                        </Text>
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={styles.menuItem}
                        onPress={handleShare}
                      >
                        <Ionicons
                          name="share-social-outline"
                          size={24}
                          color={effectiveTheme.text}
                        />
                        <Text
                          style={[
                            styles.menuLabel,
                            {
                              color: effectiveTheme.text,
                              fontFamily: "Nunito_700Bold",
                            },
                          ]}
                        >
                          Share
                        </Text>
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={styles.menuItem}
                        onPress={goHome}
                      >
                        <Ionicons
                          name="home-outline"
                          size={24}
                          color={effectiveTheme.text}
                        />
                        <Text
                          style={[
                            styles.menuLabel,
                            {
                              color: effectiveTheme.text,
                              fontFamily: "Nunito_700Bold",
                            },
                          ]}
                        >
                          Home
                        </Text>
                      </TouchableOpacity>
                    </View>
                  </Animated.View>

                  {/* Search Pill */}
                  <Animated.View
                    style={[
                      styles.pillBase,
                      {
                        height: pillHeight,
                        backgroundColor: effectiveTheme.glass,
                        borderColor: effectiveTheme.glassBorder,
                        borderRadius: cornerRadius * 2,
                      },
                      {
                        zIndex: 2,
                        opacity: searchPillOpacity,
                        transform: [{ translateY: searchPillTranslateY }],
                      },
                    ]}
                    pointerEvents={isSearchActive ? "auto" : "none"}
                  >
                    {/* NO PROGRESS VIEWS HERE - They have been removed */}

                    <View style={styles.barTabContent}>
                      <Animated.View
                        pointerEvents="none"
                        style={[
                          styles.navArrowContainer,
                          { left: 20, opacity: backArrowOpacity },
                        ]}
                      >
                        <Ionicons
                          name="arrow-back"
                          size={28}
                          color={effectiveTheme.text}
                        />
                      </Animated.View>

                      <Animated.View
                        pointerEvents="none"
                        style={[
                          styles.navArrowContainer,
                          { right: 20, opacity: forwardArrowOpacity },
                        ]}
                      >
                        <Ionicons
                          name="arrow-forward"
                          size={28}
                          color={effectiveTheme.text}
                        />
                      </Animated.View>

                      {/* Input Wrapper: The progress bar is NOW inside here */}
                      <Animated.View
                        style={[
                          styles.inputWrapper,
                          {
                            backgroundColor: effectiveTheme.inputBg,
                            opacity: contentOpacity,
                            borderRadius: cornerRadius,
                            height: pillHeight * 0.7,
                            overflow: "hidden", // This cuts off the bar at the input corners
                          },
                        ]}
                      >
                        {/* Progress Bar (Restricted to Input Area) */}
                        {progressBarMode !== "none" && isLoading && (
                          <Animated.View
                            style={{
                              position: "absolute",
                              top: 0,
                              bottom: 0,
                              backgroundColor: accentColor,
                              opacity: 0.2,
                              zIndex: 0,
                              ...(progressBarMode === "center"
                                ? {
                                    // Center Mode: Scale outward from middle
                                    left: 0,
                                    right: 0,
                                    transform: [{ scaleX: progressAnim }],
                                  }
                                : {
                                    // Standard Mode: Grow from left
                                    left: 0,
                                    width: progressAnim.interpolate({
                                      inputRange: [0, 1],
                                      outputRange: ["0%", "100%"],
                                    }),
                                  }),
                            }}
                          />
                        )}

                        <TextInput
                          style={[
                            styles.urlInput,
                            {
                              color: effectiveTheme.text,
                              fontFamily: "Nunito_600SemiBold",
                              zIndex: 1, // On top of the progress bar
                            },
                          ]}
                          value={inputUrl}
                          onChangeText={setInputUrl}
                          onSubmitEditing={handleGoPress}
                          placeholder="Search"
                          placeholderTextColor={effectiveTheme.textSec}
                          autoCapitalize="none"
                          keyboardType="url"
                          returnKeyType="go"
                          selectTextOnFocus
                          onFocus={() => {
                            setIsInputFocused(true);
                            setInputUrl(activeUrl || "");
                          }}
                          onBlur={() => {
                            setIsInputFocused(false);
                            setInputUrl(getDisplayHost(activeUrl));
                          }}
                        />
                        <View style={[styles.actionButtons, { zIndex: 1 }]}>
                          {isInputFocused ? (
                            <TouchableOpacity onPress={handleGoPress}>
                              <Ionicons
                                name="search"
                                size={22}
                                color={accentColor}
                              />
                            </TouchableOpacity>
                          ) : (
                            <TouchableOpacity
                            disabled={!activeUrl}
                            // FIX: Use the ref map with the activeTabId
                            onPress={() => webViewRefs.current[activeTabId]?.reload()} 
                            style={!activeUrl && styles.disabledBtn}
                            >
                            <Ionicons
                                name={isLoading ? "close" : "refresh"}
                                size={22}
                                color={effectiveTheme.text}
                            />
                            </TouchableOpacity>
                          )}
                        </View>
                      </Animated.View>
                    </View>
                  </Animated.View>
                </View>
              </Animated.View>
            </View>
          )}
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, paddingTop: StatusBar.currentHeight || 0 },
  webViewContainer: { flex: 1, width: "100%", zIndex: 1 },
  homeContainer: { flex: 1, justifyContent: "center", alignItems: "center" },
  homeText: { fontSize: 60, letterSpacing: -1, opacity: 0.9 },
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(0,0,0,0.1)",
    justifyContent: "center",
    alignItems: "center",
  },
  floatingLayer: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    top: 0,
    zIndex: 2,
    justifyContent: "flex-end",
  },
  recallContainer: {
    position: "absolute",
    left: 0,
    right: 0,
    zIndex: 1,
    alignItems: "center",
  },
  recallButton: {
    width: 50,
    height: 30,
    justifyContent: "center",
    alignItems: "center",
    borderWidth: 1,
    borderBottomWidth: 0,
  },
  bottomAreaContainer: {
    paddingHorizontal: 10, 
    width: "100%",
    alignItems: "center",
  },
  gestureArea: { width: "100%", justifyContent: "center" },
  pillBase: {
    position: "absolute",
    left: 0,
    right: 0,
    overflow: "hidden",
    borderWidth: 1,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.2,
    shadowRadius: 15,
    elevation: 10,
  },
  barTabContent: {
    height: "100%",
    width: "100%",
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 10,
    justifyContent: "space-around",
  },
  inputWrapper: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    height: 44,
    paddingRight: 12,
  },
  urlInput: { flex: 1, height: "100%", paddingHorizontal: 16, fontSize: 16 },
  actionButtons: {
    justifyContent: "center",
    alignItems: "center",
    marginLeft: 5,
  },
  disabledBtn: { opacity: 0.3 },
  menuItem: { flex: 1, alignItems: "center", justifyContent: "center" },
  menuLabel: { fontSize: 10, marginTop: 3 },
  navArrowContainer: {
    position: "absolute",
    top: 0,
    bottom: 0,
    justifyContent: "center",
    alignItems: "center",
    zIndex: 5,
  },
  overlayBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(0,0,0,0.5)",
    zIndex: 100,
    justifyContent: "flex-end",
  },
  backdropTouchArea: { flex: 1 },
  sheetContainer: {
    width: "100%",
    position: "absolute",
    bottom: 0,
    overflow: "hidden",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: -5 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 20,
  },
  sheetHeader: {
    paddingHorizontal: 20,
    paddingTop: 15,
    paddingBottom: 10,
    alignItems: "center",
    borderBottomWidth: 1,
  },
  sheetHandle: {
    width: 40,
    height: 4,
    backgroundColor: "#999",
    borderRadius: 2,
    marginBottom: 10,
  },
  sheetHeaderRow: {
    flexDirection: "row",
    width: "100%",
    justifyContent: "space-between",
    alignItems: "center",
  },
  sheetTitle: { fontSize: 22 },
  iconBtn: { padding: 5 },
  sectionHeader: {
    fontSize: 14,
    textTransform: "uppercase",
    marginTop: 20,
    marginBottom: 10,
    marginLeft: 5,
  },
  emptyState: { alignItems: "center", marginTop: 50, opacity: 0.5 },
  emptyText: { marginTop: 10, fontSize: 16 },
  settingsGroup: { overflow: "hidden" },
  settingRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    padding: 16,
    minHeight: 60,
  },
  settingText: { fontSize: 16 },
  settingBtn: {
    backgroundColor: "#444",
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  versionText: {
    color: "#888",
    marginTop: 30,
    marginBottom: 50,
    textAlign: "center",
    fontSize: 12,
  },
  modeBtn: {
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 15,
    marginLeft: 5,
    backgroundColor: "rgba(120,120,120,0.1)",
  },
  modeBtnText: { fontSize: 12 },
  colorDot: { width: 24, height: 24, borderRadius: 12, marginLeft: 10 },
  fabContainer: { position: "absolute", bottom: 40, right: 20 },
  fabButton: {
    width: 60,
    height: 60,
    borderRadius: 30,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 8,
    elevation: 8,
  },
  modalOverlay: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.5)",
  },
  modalContent: { width: "80%", padding: 20, elevation: 5 },
  modalTitle: { fontSize: 20, marginBottom: 15 },
  modalInput: {
    borderWidth: 1,
    borderColor: "rgba(128,128,128,0.2)",
    padding: 12,
    marginBottom: 20,
    fontSize: 16,
  },
  modalButtons: { flexDirection: "row", justifyContent: "flex-end" },
  modalBtn: { paddingHorizontal: 15, paddingVertical: 10, marginLeft: 10 },
  paginationContainer: {
    position: "absolute",
    bottom: 5,
    left: 0,
    right: 0,
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginHorizontal: 3,
    opacity: 0.7,
  },
});