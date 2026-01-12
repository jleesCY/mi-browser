import {
  Nunito_400Regular,
  Nunito_600SemiBold,
  Nunito_700Bold,
  Nunito_800ExtraBold,
  useFonts,
} from "@expo-google-fonts/nunito";
import { Ionicons } from "@expo/vector-icons";
import * as FileSystem from 'expo-file-system/legacy';
import * as MediaLibrary from 'expo-media-library';
import React, { useEffect, useMemo, useRef, useState } from "react";
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
  SectionList,
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
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { WebView } from "react-native-webview";

import SwipeableHistoryRow from "@/src/components/SwipeableHistoryRow";
import SwipeableTabRow from "@/src/components/SwipeableTabRow";
import {
  ACCENTS,
  APP_VERSION,
  COLORS,
  HIDDEN_TRANSLATE_Y,
  HISTORY_RANGES,
  HOME_LOGO_TEXT,
  SEARCH_ENGINES,
  SNAP_CLOSED,
  SNAP_DEFAULT,
  SNAP_FULL,
  SWAP_DISTANCE
} from "../src/constants";
import { HistoryItem, TabItem } from "../src/types";
import {
  generateAdaptiveTheme,
  getDisplayHost,
  getSmartDate,
  groupHistoryByDate,
  loadStorage,
  saveStorage,
} from "../src/utils";

const INJECTED_CONTEXT_MENU_SCRIPT = `
  (function() {
    function getParentLink(el) {
      while (el && el.tagName !== 'A') {
        el = el.parentElement;
      }
      return el;
    }

    window.oncontextmenu = function(e) {
      var target = e.target;
      var link = getParentLink(target);
      var img = target.tagName === 'IMG' ? target : null;
      
      // Only capture if it's a link or an image
      if (link || img) {
        e.preventDefault();
        e.stopPropagation();
        
        window.ReactNativeWebView.postMessage(JSON.stringify({
          type: 'CONTEXT_MENU',
          data: {
            url: link ? link.href : null,
            imgUrl: img ? img.src : null,
            text: link ? link.innerText.substring(0, 50) : (img ? (img.alt || 'Image') : '')
          }
        }));
        return false;
      }
    };
  })();
`;

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
  const lastErrorTimestamp = useRef(0);

  const [isLoading, setIsLoading] = useState(false);

  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [tabs, setTabs] = useState<TabItem[]>([
    { id: "1", url: null, title: "New Tab", showLogo: true },
  ]);

  // Confirmation Modal State
  const [isConfirmModalVisible, setIsConfirmModalVisible] = useState(false);
  const [confirmActionType, setConfirmActionType] = useState<
    "history" | "resetSettings" | "bgRefresh" | null
  >(null);
  const [confirmHistoryPayload, setConfirmHistoryPayload] = useState<{
    ms: number;
    label: string;
  } | null>(null);

  const [contextMenuVisible, setContextMenuVisible] = useState(false);
  const [contextMenuData, setContextMenuData] = useState<{
    url: string | null;
    imgUrl: string | null;
    text: string;
  } | null>(null);

  const [activeView, setActiveView] = useState<
    "none" | "tabs" | "history" | "settings"
  >("none");
  const [isSearchActive, setIsSearchActive] = useState(true);
  const isSearchActiveRef = useRef(true);
  const [isInputFocused, setIsInputFocused] = useState(false);

  const [themeMode, setThemeMode] = useState<"light" | "gray" | "dark" | "adaptive">(
    "dark"
  );
  const [accentColor, setAccentColor] = useState("#007AFF");
  const [searchEngineIndex, setSearchEngineIndex] = useState(0);
  const [isSearchEngineOpen, setIsSearchEngineOpen] = useState(false);
  const [isClearHistoryOpen, setIsClearHistoryOpen] = useState(false);

  const [backgroundRefresh, setBackgroundRefresh] = useState(false);

  // Cosmetic settings
  const [cornerRadius, setCornerRadius] = useState(22);
  const [uiPadding, setUiPadding] = useState<"compact" | "normal" | "airy">(
    "normal"
  );
  const [fontScale, setFontScale] = useState(1);
  const [barTransparency, setBarTransparency] = useState<
    "opaque" | "frosted" | "ghost"
  >("frosted");
  const [showStatusBar, setShowStatusBar] = useState(true);
  const [pillHeight, setPillHeight] = useState(70);
  const [progressBarMode, setProgressBarMode] = useState<
    "ltr" | "center" | "none"
  >("ltr");
  const [recallPosition, setRecallPosition] = useState<
    "left" | "center" | "right"
  >("center");

  const [startupTabMode, setStartupTabMode] = useState<"new" | "last">("new");

  // Functional settings
  const [desktopMode, setDesktopMode] = useState(false);
  const [jsEnabled, setJsEnabled] = useState(true);
  const [httpsOnly, setHttpsOnly] = useState(false);
  const [blockCookies, setBlockCookies] = useState(false);

  const [isAccentExpanded, setIsAccentExpanded] = useState(false);

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

  // --- HELPER: Parse Deep Links (Expo Go & Production) ---
  const parseDeepLinkUrl = (originalUrl: string) => {
    let payload = originalUrl;

    // 1. Strip Scheme / Prefix
    if (payload.startsWith("exp://") || payload.startsWith("exps://")) {
        // Handle Expo Go: exp://IP:PORT/--/payload
        const split = payload.split("/--/");
        if (split.length > 1) {
            payload = split[1];
            console.log("Parsed Expo Go deep link payload:", payload);
        } else {
            // Just opening the app root (no link)
            return null; 
        }
    } else if (payload.startsWith("mibrowser://")) {
        payload = payload.replace("mibrowser://", "");
    }

    // 2. Handle specific "url=" query param (common in Android Intents)
    if (payload.includes("url=") || payload.includes("URL=")) {
        try {
          const match = payload.match(/(?:url|URL)=([^&]+)/);
          if (match && match[1]) {
            return decodeURIComponent(match[1]);
          }
        } catch(e) {}
    }

    // 3. General Cleanup: Remove leading query markers and decode
    payload = payload.replace(/^[?&]/, "");
    try {
        payload = decodeURIComponent(payload);
    } catch (e) {}

    // 4. Ensure Protocol
    if (
        payload &&
        !payload.startsWith("http://") &&
        !payload.startsWith("https://") &&
        !payload.startsWith("about:")
    ) {
        payload = "https://" + payload;
    }

    return payload;
  };

  // --- HELPER TO HANDLE INCOMING LINKS ---
  const handleIncomingUrl = React.useCallback((url: string | null) => {
    if (!url) return;

    // Use the shared parser to clean the URL
    const targetUrl = parseDeepLinkUrl(url);

    // Verify it's a valid web URL before opening
    if (
      targetUrl &&
      (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))
    ) {
      const newId = Date.now().toString();
      const newTab = {
        id: newId,
        url: targetUrl,
        initialUrl: targetUrl,
        title: "External Link",
        showLogo: false,
      };

      setTabs((prev) => [newTab, ...prev]);
      setActiveTabId(newId);
      setActiveUrl(targetUrl);
      setInputUrl(getDisplayHost(targetUrl));
      setActiveView("none"); 
    }
  }, []);

  // --- ROBUST EXTERNAL LINK HANDLER ---
  const handleExternalLink = async (url: string) => {
    try {
      // 1. Handle Special "Intent" Schemes (Android)
      if (url.startsWith("intent://") || url.includes("#Intent;")) {
        await handleIntent(url);
        return;
      }

      // 2. Handle Standard External Schemes (mailto, tel, market, etc.)
      const supported = await Linking.canOpenURL(url);
      if (supported) {
        await Linking.openURL(url);
      } else {
        // Fallback: Attempt to open anyway, sometimes canOpenURL returns false on Android 11+ but openURL works
        await Linking.openURL(url);
      }
    } catch (err: any) {
      Alert.alert(
        "Link Error",
        `Could not open this link.\n\nError: ${err.message || "Unknown error"}\n\nURL: ${url}`,
        [{ text: "OK" }]
      );
    }
  };

  // --- ROBUST EXTERNAL LINK HANDLER ---
  const handleIntent = async (intentUrl: string) => {
    try {
      // Attempt 1: Extract and Open the Clean Deep Link (Best for installed apps)
      // We do this FIRST because Linking.openURL often fails on the raw "intent://" string
      // but succeeds on "scheme://" if the app is installed.
      const schemeMatch = intentUrl.match(/scheme=([^;]+)/);
      if (schemeMatch && schemeMatch[1]) {
        const scheme = schemeMatch[1];
        // Extract the path part: "intent://example.com/foo#Intent;..." -> "example.com/foo"
        let pathPart = intentUrl.substring(0, intentUrl.indexOf("#Intent;"));
        pathPart = pathPart.replace(/^intent:\/\/|^intent:/, "");

        // Reconstruct standard deep link: "myscheme://example.com/foo"
        const cleanDeepLink = `${scheme}://${pathPart}`;

        try {
          await Linking.openURL(cleanDeepLink);
          return; // Success! App opened.
        } catch (e) {
          // If this fails, the app is likely not installed. 
          // Continue to the next attempts (Fallback URL or Store).
        }
      }

      // Attempt 2: Open the intent URL directly
      // Sometimes the OS can handle the intent wrapper if the package is known
      try {
        await Linking.openURL(intentUrl);
        return;
      } catch(e) {
        // Continue if this fails
      }

      // Attempt 3: Extract the "browser_fallback_url"
      const fallbackMatch = intentUrl.match(/browser_fallback_url=([^;]+)/);
      if (fallbackMatch && fallbackMatch[1]) {
        const fallbackUrl = decodeURIComponent(fallbackMatch[1]);
        // Load fallback in OUR browser
        if (activeTabIdRef.current === activeTabId) {
          setTabs((prev) =>
            prev.map((t) =>
              t.id === activeTabId ? { ...t, url: fallbackUrl } : t
            )
          );
          setActiveUrl(fallbackUrl);
          setInputUrl(getDisplayHost(fallbackUrl));
        }
        return;
      }

      // Attempt 4: Extract Package ID and offer Play Store
      const packageMatch = intentUrl.match(/package=([^;]+)/);
      if (packageMatch && packageMatch[1]) {
        const packageName = packageMatch[1];
        Alert.alert(
          "App Required",
          `This feature requires an external app (${packageName}). Would you like to view it in the store?`,
          [
            { text: "Cancel", style: "cancel" },
            { 
              text: "View Store", 
              onPress: () => Linking.openURL(`market://details?id=${packageName}`) 
            }
          ]
        );
        return;
      }

      // Final Fail
      Alert.alert(
        "Action Failed",
        "Could not handle this action and no fallback was provided by the website.",
        [{ text: "OK" }]
      );
    } catch (err: any) {
       console.log("Intent Error:", err);
    }
  };

  useEffect(() => {
    const loadAllData = async () => {
      // 1. Load Settings First
      const savedSettings = await loadStorage("settings");
      let currentStartupMode = "new";
      // Default to false (save resources) if not set
      let shouldBackgroundRefresh = false;

      if (savedSettings) {
        setThemeMode(savedSettings.themeMode ?? "dark");
        setAccentColor(savedSettings.accentColor ?? "#007AFF");
        const savedIndex = savedSettings.searchEngineIndex ?? 0;
        setSearchEngineIndex(
          savedIndex >= 0 && savedIndex < SEARCH_ENGINES.length ? savedIndex : 0
        );
        setCornerRadius(savedSettings.cornerRadius ?? 22);
        setUiPadding(savedSettings.uiPadding ?? "normal");
        setFontScale(savedSettings.fontScale ?? 1);
        setBarTransparency(savedSettings.barTransparency ?? "frosted");
        setShowStatusBar(savedSettings.showStatusBar !== undefined ? savedSettings.showStatusBar : true);
        setPillHeight(savedSettings.pillHeight ?? 70);
        setProgressBarMode(savedSettings.progressBarMode ?? "ltr");
        setRecallPosition(savedSettings.recallPosition ?? "center");

        setDesktopMode(savedSettings.desktopMode ?? false);
        setJsEnabled(savedSettings.jsEnabled ?? true);
        setHttpsOnly(savedSettings.httpsOnly ?? false);
        setBlockCookies(savedSettings.blockCookies ?? false);
        
        // Load the new setting
        shouldBackgroundRefresh = savedSettings.backgroundRefresh ?? false;
        setBackgroundRefresh(shouldBackgroundRefresh);

        if (savedSettings.startupTabMode) {
          currentStartupMode = savedSettings.startupTabMode;
        }
        setStartupTabMode(currentStartupMode as any);
      }

      // 2. Load History
      const savedHistory = await loadStorage("history");
      if (savedHistory) setHistory(savedHistory);

      // 3. Load Tabs
      const savedTabs = await loadStorage("tabs");
      const savedActiveTabId = await loadStorage("activeTabId");

      // Initialize existing tabs. 
      // If background refresh is ON, mark all as loaded. 
      // If OFF, mark all as NOT loaded (we will manually set the active one to true in the specific cases below).
      const existingTabs = (savedTabs || []).map((t: any) => ({
        ...t,
        initialUrl: t.initialUrl || t.url, // Polyfill
        hasLoadedOnce: shouldBackgroundRefresh
      }));

      // 4. Handle Startup Logic
      const initialUrl = await Linking.getInitialURL();

      if (initialUrl) {
        // --- CASE A: Launched via Deep Link ---
        const targetUrl = parseDeepLinkUrl(initialUrl);

        if (targetUrl) {
          const newId = Date.now().toString();
          const startupTab = {
              id: newId,
              url: targetUrl,
              initialUrl: targetUrl,
              title: "External Link",
              showLogo: false,
              hasLoadedOnce: true // Deep link tab must always load
          };

          setTabs([startupTab, ...existingTabs]);
          setActiveTabId(newId);
          setActiveUrl(targetUrl);
          setInputUrl(getDisplayHost(targetUrl));
        } else {
           // Fallback if deep link parsing failed: check startup mode
           if (currentStartupMode === "last" && existingTabs.length > 0) {
             // We need to identify the active tab and force it to load
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
      } else if (currentStartupMode === "last" && existingTabs.length > 0) {
        // --- CASE B: Resume Last Session ---
        let targetTab = existingTabs.find(
          (t: any) => t.id === savedActiveTabId
        );
        if (!targetTab) {
          targetTab = existingTabs.find((t: any) => t.url) || existingTabs[0];
        }

        // Ensure the active tab is marked loaded, even if background refresh is off
        const finalTabs = existingTabs.map((t: any) => 
            t.id === targetTab.id ? { ...t, hasLoadedOnce: true } : t
        );

        setTabs(finalTabs);
        setActiveTabId(targetTab.id);
        setActiveUrl(targetTab.url);
        setInputUrl(targetTab.url ? getDisplayHost(targetTab.url) : "");

      } else {
        // --- CASE C: Start Fresh (New Tab) ---
        // Check if we already have a blank tab in the saved list to reuse
        const existingBlankTab = existingTabs.find((t: any) => !t.url);

        if (existingBlankTab) {
          // Mark the blank tab as loaded (it's empty anyway, but for consistency)
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
            title: "New Tab",
            showLogo: true,
            hasLoadedOnce: true // New active tab must load
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

  useEffect(() => {
    setTabs((prev) => 
      prev.map((t) => {
        // If this is the active tab and it hasn't loaded yet, mark it
        if (t.id === activeTabId && !t.hasLoadedOnce) {
          return { ...t, hasLoadedOnce: true };
        }
        return t;
      })
    );
  }, [activeTabId]);

  // --- SYNC UI WHEN SWITCHING TABS ---
  useEffect(() => {
    const currentTab = tabs.find((t) => t.id === activeTabId);
    if (currentTab) {
      ignoreNextScroll.current = true;

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
      progressAnim.setValue(currentTab.loading ? 0.2 : 0);
    }
  }, [activeTabId]);

  useEffect(() => {
    if (!isAppReady) return;

    const saveTimeout = setTimeout(() => {
      const cleanTabs = tabs.map(
        ({ loading, canGoBack, canGoForward, hasLoadedOnce, ...rest }) => rest
      );
      saveStorage("tabs", cleanTabs);
      saveStorage("activeTabId", activeTabId);
    }, 500); 

    return () => clearTimeout(saveTimeout);
  }, [tabs, activeTabId, isAppReady]);

  useEffect(() => {
    if (!isAppReady) return;

    const saveTimeout = setTimeout(() => {
      saveStorage("history", history);
    }, 1000);

    return () => clearTimeout(saveTimeout);
  }, [history, isAppReady]);

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
        showStatusBar,
        pillHeight,
        progressBarMode,
        recallPosition,
        startupTabMode,
        desktopMode,
        jsEnabled,
        httpsOnly,
        blockCookies,
        backgroundRefresh,
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
    showStatusBar,
    pillHeight,
    progressBarMode,
    recallPosition,
    startupTabMode,
    desktopMode,
    jsEnabled,
    httpsOnly,
    blockCookies,
    isAppReady,
    backgroundRefresh
  ]);

  // --- FIX: Handle Android Hardware Back Button ---
  useEffect(() => {
    const onBackPress = () => {
      // 1. If an overlay is open (History, Tabs, Settings), close it first
      if (activeView !== "none") {
        closeOverlay();
        return true;
      }

      // 2. If the user is typing in the search bar, close the keyboard/unfocus
      if (isInputFocused) {
        Keyboard.dismiss();
        setIsInputFocused(false);
        return true;
      }

      // 3. If the WebView has history to go back to, navigate back
      if (canGoBackRef.current && webViewRefs.current[activeTabId]) {
        webViewRefs.current[activeTabId]?.goBack();
        showBar();
        return true;
      }

      return false;
    };

    const subscription = BackHandler.addEventListener(
      "hardwareBackPress",
      onBackPress
    );

    return () => subscription.remove();
  }, [activeView, isInputFocused]);

  useEffect(() => {
    const handleDeepLink = (event: { url: string }) => {
      handleIncomingUrl(event.url);
    };

    const subscription = Linking.addEventListener("url", handleDeepLink);
    return () => subscription.remove();
  }, [handleIncomingUrl]);

  const effectiveTheme = useMemo(() => {
    let selectedTheme;
    if (themeMode === "light") selectedTheme = COLORS.light;
    else if (themeMode === "dark") selectedTheme = COLORS.dark;
    else if (themeMode === "gray") selectedTheme = COLORS.gray; // <--- Uses the constant now
    else selectedTheme = generateAdaptiveTheme(accentColor);

    const finalTheme = { ...selectedTheme };

    if (themeMode === "adaptive" || themeMode === "gray") {
      let alpha = "F2";
      if (barTransparency === "opaque") alpha = "FF";
      if (barTransparency === "ghost") alpha = "99";
      
      if (finalTheme.bg && finalTheme.bg.startsWith("#")) {
        finalTheme.glass = finalTheme.bg.substring(0, 7) + alpha;
      }
    }
    return finalTheme;
  }, [themeMode, accentColor, barTransparency]);

  const getTabHeight = () => {
    let base = 70;
    switch (uiPadding) {
      case "compact":
        base = 64; // Increased base slightly to fit content
        break;
      case "normal":
        base = 74;
        break;
      case "airy":
        base = 88;
        break;
    }
    // Scale the height based on font selection so large text doesn't overflow
    return base * fontScale;
  };

  const getHistoryHeight = () => {
    let base = 50;
    switch (uiPadding) {
      case "compact":
        base = 48; // Increased slightly
        break;
      case "normal":
        base = 58;
        break;
      case "airy":
        base = 72;
        break;
    }
    return base * fontScale;
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

  const ignoreNextScroll = useRef(false);
  const isBarHidden = useRef(false);

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
      "android.webkit.resource.AUDIO_CAPTURE": "Microphone",
      "android.webkit.resource.VIDEO_CAPTURE": "Camera",
      "android.webkit.resource.PROTECTED_MEDIA_ID": "Protected Media",
    };

    const friendlyResources = resources
      .map((res: string) => resourceMap[res] || res)
      .join(" and ");

    Alert.alert(
      "Permission Request",
      `${
        activeUrl ? getDisplayHost(activeUrl) : "This site"
      } is requesting access to your ${friendlyResources}.`,
      [
        {
          text: "Deny",
          onPress: () => event.nativeEvent.deny(),
          style: "cancel",
        },
        {
          text: "Allow",
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

    // 1. Check if it explicitly starts with http/https
    if (/^(http|https):\/\//i.test(text)) {
      if (httpsOnly && text.startsWith("http://")) {
        targetUrl = text.replace(/^http:\/\//i, "https://");
      } else {
        targetUrl = text;
      }
    } else {
      // 2. Advanced URL Detection
      // Standard Domains (example.com, sub.site.co.uk)
      const domainRegex = /^([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(\/.*)?$/;
      // IPv4 Addresses (192.168.1.1, 127.0.0.1:8080)
      const ipRegex = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(\/.*)?$/;
      // Localhost (localhost, localhost:3000)
      const localhostRegex = /^localhost(?::[0-9]{1,5})?(\/.*)?$/;

      // Check if it matches any valid URL pattern and has no spaces
      if (
        !text.includes(" ") &&
        (domainRegex.test(text) || ipRegex.test(text) || localhostRegex.test(text))
      ) {
        // Localhost and IPs usually need HTTP, not HTTPS (unless specified)
        if (localhostRegex.test(text) || ipRegex.test(text)) {
          targetUrl = `http://${text}`;
        } else {
          targetUrl = `https://${text}`;
        }
      } else {
        // 3. Fallback to Search Engine
        targetUrl = `${
          SEARCH_ENGINES[searchEngineIndex].url
        }${encodeURIComponent(text)}`;
      }
    }

    // 4. Navigate
    setActiveUrl(targetUrl);
    setTabs((prev) =>
      prev.map((t) =>
        t.id === activeTabId
          ? { ...t, url: targetUrl, title: text }
          : t
      )
    );

    // Force reload if URL is identical
    if (activeUrl === targetUrl && webViewRefs.current[activeTabId]) {
      webViewRefs.current[activeTabId]?.reload();
    }

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

  const requestClearHistory = (ms: number, label: string) => {
    setConfirmHistoryPayload({ ms, label });
    setConfirmActionType("history");
    setIsConfirmModalVisible(true);
  };

  const requestResetSettings = () => {
    setConfirmActionType("resetSettings");
    setIsConfirmModalVisible(true);
  };

  const toggleBackgroundRefresh = (value: boolean) => {
  if (value) {
    setConfirmActionType("bgRefresh");
    setIsConfirmModalVisible(true);
  } else {
    setBackgroundRefresh(false);
  }
};

  const executeConfirmAction = () => {
    if (confirmActionType === "history" && confirmHistoryPayload) {
      deleteHistory(confirmHistoryPayload.ms);
    } else if (confirmActionType === "resetSettings") {
      // --- RESET TO DEFAULTS ---
      setThemeMode("dark");
      setAccentColor("#007AFF");
      setSearchEngineIndex(0);
      setCornerRadius(22);
      setUiPadding("normal");
      setFontScale(1);
      setBarTransparency("frosted");
      setPillHeight(70);
      setProgressBarMode("ltr");
      setRecallPosition("center");
      setStartupTabMode("new");
      setDesktopMode(false);
      setJsEnabled(true);
      setHttpsOnly(false);
      setBlockCookies(false);

      // Clear settings from storage
      saveStorage("settings", null);
    }
    else if (confirmActionType === "bgRefresh") {
      setBackgroundRefresh(true);
    }
    setIsConfirmModalVisible(false);
  };

  const deleteHistoryItem = (idToDelete: string) => {
    setHistory((prevHistory) => {
      const newHistory = prevHistory.filter((item) => item.id !== idToDelete);
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
      return newHistory;
    });
  };

  const addNewTab = (overrideUrl?: string) => {
    const newId = Date.now().toString();
    const newTab = {
      id: newId,
      url: overrideUrl || null,
      initialUrl: overrideUrl || null,
      title: "New Tab",
      showLogo: true,
    };
    setTabs((prev) => [newTab, ...prev]);
    setActiveTabId(newId);
    setActiveUrl(overrideUrl || null);
    setInputUrl(overrideUrl ? getDisplayHost(overrideUrl) : "");
  };

  const deleteTab = (idToDelete: string) => {
    // 1. Clean up the WebView ref
    const ref = webViewRefs.current[idToDelete];
    if (ref) {
      try {
        ref.stopLoading();
      } catch (e) {
        // Ignore errors if the view is already detached
      }
      webViewRefs.current[idToDelete] = null;
      delete webViewRefs.current[idToDelete];
    }

    // 2. Update State Atomically
    setTabs((prevTabs) => {
      const newTabs = prevTabs.filter((t) => t.id !== idToDelete);
      
      // Use Ref to ensure we compare against the truly current active ID inside this callback
      const currentActiveId = activeTabIdRef.current;

      // If we are deleting the currently active tab...
      if (currentActiveId === idToDelete) {
        if (newTabs.length > 0) {
          // Calculate the new tab to focus on based on the PREVIOUS list state
          const indexToDelete = prevTabs.findIndex((t) => t.id === idToDelete);
          
          // Try to go to the left (previous) tab, otherwise stay at index 0
          const nextIndex = Math.max(0, indexToDelete - 1);
          
          // Ensure index is within bounds of the NEW array
          const safeIndex = Math.min(nextIndex, newTabs.length - 1);
          const nextTab = newTabs[safeIndex];

          // Defer the side-effect state updates to avoid conflicts during rendering
          setTimeout(() => {
            setActiveTabId(nextTab.id);
            setActiveUrl(nextTab.url);
            setInputUrl(nextTab.url ? getDisplayHost(nextTab.url) : "");
          }, 0);
        }
      }

      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);

      // Handle "Empty List" - Create a new blank tab
      if (newTabs.length === 0) {
        const freshId = Date.now().toString();
        const freshTab = { id: freshId, url: null, title: "New Tab", showLogo: true };
        
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
    Keyboard.dismiss();
    Animated.timing(overlayHeightAnim, {
      toValue: SNAP_CLOSED,
      duration: 250,
      useNativeDriver: false,
    }).start(() => {
      setActiveView("none");
      snapToSearch();

      // Reset search states
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
    isBarHidden.current = false; // <--- Update state
    currentScrollTrans.current = 0;
    Animated.timing(scrollTranslateY, {
      toValue: 0,
      duration: 100, // <--- Faster
      useNativeDriver: false,
    }).start();
  };

  const hideBar = () => {
    isBarHidden.current = true;
    currentScrollTrans.current = HIDDEN_TRANSLATE_Y;
    Animated.timing(scrollTranslateY, {
      toValue: HIDDEN_TRANSLATE_Y,
      duration: 100,
      useNativeDriver: false,
    }).start();
  };

  const addToHistory = (url: string) => {
    if (!url || url === "about:blank") return;

    const title = getDisplayHost(url);

    setHistory((prevHistory) => {
      // OPTIMIZATION: If the top history item is the same URL, don't do anything.
      // This prevents "thrashing" when SPAs update the URL fragment/query rapidly.
      if (prevHistory.length > 0 && prevHistory[0].url === url) {
        return prevHistory;
      }

      // Remove duplicates (Expensive operation, now only runs when necessary)
      const cleanedHistory = prevHistory.filter(
        (item) => item.url.replace(/\/$/, "") !== url.replace(/\/$/, "")
      );

      const newItem = {
        id: Date.now().toString(),
        url,
        title,
        timestamp: Date.now(),
      };

      return [newItem, ...cleanedHistory].slice(0, 100);
    });
  };

  const handleScroll = (event: any) => {
    const y = event.nativeEvent.contentOffset.y;

    if (ignoreNextScroll.current) {
      lastScrollY.current = y;
      ignoreNextScroll.current = false;
      return;
    }

    if (isInputFocused || activeView !== "none" || y < 0) return;

    const dy = y - lastScrollY.current;

    // Use a small threshold (10px) to prevent jitter on tiny movements
    if (Math.abs(dy) > 1) {
      if (dy > 0 && !isBarHidden.current) {
        // Scrolling Down -> Immediately Hide
        hideBar();
      } else if (dy < 0 && isBarHidden.current) {
        // Scrolling Up -> Immediately Show
        showBar();
      }
    }
    
    lastScrollY.current = y;
  };

  const handleFullScreen = (event: any) => {
    const { fullScreen } = event.nativeEvent;
    setIsFullscreen(fullScreen);
    StatusBar.setHidden(fullScreen);
  };

  // --- PERMISSION HELPER ---
  const requestManualPermission = (featureName: string) => {
    Alert.alert(
      "Permission Required",
      `This app needs access to your ${featureName} to function. Please grant permission in your device settings.`,
      [
        { text: "Cancel", style: "cancel" },
        { 
          text: "Open Settings", 
          onPress: () => Linking.openSettings() 
        }
      ]
    );
  };

  const handleDownloadImage = async () => {
    const url = contextMenuData?.imgUrl;
    if (!url) return;

    try {
      // 1. Request Permission (Write Only)
      // We check status immediately.
      const { status } = await MediaLibrary.requestPermissionsAsync(true);
      
      // 2. Handle Denial (Soft or Hard)
      if (status !== 'granted') {
        requestManualPermission("Photos");
        return;
      }

      // 3. Determine File Extension
      let extension = '.jpg';
      if (url.includes('.png')) extension = '.png';
      else if (url.includes('.gif')) extension = '.gif';
      else if (url.includes('.webp')) extension = '.webp';
      else if (url.startsWith('data:image/png')) extension = '.png';
      
      const fileName = `download_${Date.now()}${extension}`; 
      const fileUri = FileSystem.documentDirectory + fileName;

      // 4. Download or Write File
      if (url.startsWith('data:')) {
        const base64Code = url.split('base64,')[1];
        await FileSystem.writeAsStringAsync(fileUri, base64Code, {
          encoding: FileSystem.EncodingType.Base64,
        });
      } else {
        const downloadRes = await FileSystem.downloadAsync(url, fileUri);
        if (downloadRes.status !== 200) {
          throw new Error(`Download failed with status: ${downloadRes.status}`);
        }
      }

      // 5. Save to Device Gallery
      await MediaLibrary.saveToLibraryAsync(fileUri);
      
      Alert.alert("Success", "Image saved to gallery!");
      setContextMenuVisible(false);

    } catch (e: any) {
      console.error(e);
      // If the save fails specifically due to permissions (rare if step 1 passed), prompt again
      if (e.message && e.message.includes("permission")) {
        requestManualPermission("Photos");
      } else {
        Alert.alert("Save Error", e.message || "An unknown error occurred.");
      }
    }
  };

  const handleWebViewMessage = (event: any) => {
    const nativeEvent = event.nativeEvent;
    if (!nativeEvent.data) return;

    try {
      const dataString = nativeEvent.data;
      if (typeof dataString === 'string' && dataString.includes('CONTEXT_MENU')) {
        const parsed = JSON.parse(dataString);
        if (parsed.type === 'CONTEXT_MENU') {
          // Force update on next tick
          setTimeout(() => {
            setContextMenuData(parsed.data);
            setContextMenuVisible(true);
          }, 0);
        }
      }
    } catch (e) {
      // Ignore
    }
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
      onPanResponderRelease: () => {
        logoPan.flattenOffset();
        Animated.spring(logoPan, {
          toValue: { x: 0, y: 0 },
          friction: 6,
          tension: 80,
          useNativeDriver: false,
        }).start();

        Animated.spring(logoScale, {
          toValue: 1,
          friction: 6,
          tension: 80,
          useNativeDriver: false,
        }).start();
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
            // FIX: Use activeTabIdRef.current to get the REAL active tab
            const currentTabId = activeTabIdRef.current;
            const currentWebView = webViewRefs.current[currentTabId];

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
  const containerScale = scrollTranslateY.interpolate({
    inputRange: [0, HIDDEN_TRANSLATE_Y],
    outputRange: [1, 0.6],
    extrapolate: "clamp",
  });
  const containerOpacity = scrollTranslateY.interpolate({
    inputRange: [0, HIDDEN_TRANSLATE_Y * 0.75, HIDDEN_TRANSLATE_Y],
    outputRange: [1, 0.5, 0],
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
    const rowTheme = {
      ...effectiveTheme,
      surface: effectiveTheme.card,
      bg: effectiveTheme.card,
    };

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

      const historySections = groupHistoryByDate(filteredHistory);

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

          <SectionList
            sections={historySections}
            keyExtractor={(item, index) => item.id + index}
            contentContainerStyle={{ padding: 20, paddingTop: 0 }}
            keyboardShouldPersistTaps="handled"
            stickySectionHeadersEnabled={false}
            renderSectionHeader={({ section: { title } }) => (
              <Text
                style={[
                  styles.sectionHeader,
                  {
                    color: effectiveTheme.textSec,
                    fontFamily: "Nunito_700Bold",
                    marginTop: 20,
                    marginBottom: 10,
                  },
                ]}
              >
                {title}
              </Text>
            )}
            renderItem={({ item }) => (
              <SwipeableHistoryRow
                item={item}
                theme={rowTheme} // Use the higher contrast rowTheme
                accent={accentColor}
                radius={cornerRadius}
                height={getHistoryHeight()}
                margin={getMargin()}
                fontScale={fontScale}
                timeString={getSmartDate(item.timestamp)} // Pass formatted time string
                onPress={() => {
                  const targetUrl = item.url;
                  setActiveUrl(targetUrl);
                  setInputUrl(getDisplayHost(targetUrl));

                  setTabs((prev) =>
                    prev.map((t) =>
                      t.id === activeTabId
                        ? {
                            ...t,
                            url: targetUrl,
                            initialUrl: targetUrl, // <--- ADD THIS
                            title: item.title || getDisplayHost(targetUrl),
                          }
                        : t
                    )
                  );
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
            keyboardShouldPersistTaps="handled"
            contentContainerStyle={{
              padding: 20,
              paddingBottom: 100,
              paddingTop: 0,
            }}
            renderItem={({ item }) => (
              <SwipeableTabRow
                item={item}
                theme={rowTheme} // Use the higher contrast rowTheme
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
          keyboardShouldPersistTaps="handled"
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
                    name="color-palette-outline"
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
                    Theme
                  </Text>
                </View>
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={{
                    flexDirection: "row",
                    alignItems: "center",
                  }}
                >
                  {["light", "gray", "dark", "adaptive"].map((m) => (
                    <TouchableOpacity
                      key={m}
                      onPress={() => setThemeMode(m as any)}
                      style={[
                        styles.modeBtn,
                        themeMode === m && { backgroundColor: accentColor },
                        { paddingHorizontal: 20 },
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
                </ScrollView>
              </View>
            </SettingRow>

            <SettingRow label="Accent">
              <View style={{ width: '100%', flexDirection: 'column', paddingVertical: 5 }}>
                 <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 }}>
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
                    <TouchableOpacity 
                        onPress={() => {
                            LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
                            setIsAccentExpanded(!isAccentExpanded);
                        }}
                        style={{ padding: 5 }}
                    >
                        <Text style={{ color: accentColor, fontFamily: 'Nunito_700Bold', fontSize: 12 * fontScale }}>
                            {isAccentExpanded ? 'Show Less' : 'Show More'}
                        </Text>
                    </TouchableOpacity>
                 </View>
                  <View 
                    style={{ 
                      flexDirection: 'row', 
                      flexWrap: 'wrap', 
                      width: '100%',
                      justifyContent: 'space-between',
                    }}
                  >
                    {(isAccentExpanded ? ACCENTS : ACCENTS.slice(0, 6)).map((color) => (
                      <TouchableOpacity
                        key={color}
                        onPress={() => setAccentColor(color)}
                        style={[
                          styles.colorDot,
                          { 
                              backgroundColor: color, 
                              // 15% * 6 = 90%. Fits 6 items perfectly.
                              // 15% * 7 = 105%. Forces 7th item to wrap.
                              width: '15%', 
                              aspectRatio: 1, 
                              borderRadius: 10, 
                              // Vertical spacing only. Horizontal is handled by space-between.
                              marginBottom: 15,
                              // Ensure no side margins interfere with calculation
                              marginLeft: 0,
                              marginRight: 0,
                              marginTop: 0,
                          },
                          accentColor === color && {
                            borderWidth: 3,
                            borderColor: effectiveTheme.text,
                            transform: [{ scale: 0.9 }] 
                          },
                        ]}
                      />
                    ))}
                  </View>
              </View>
            </SettingRow>

            <SettingRow label="Show Status Bar">
              <View style={{ flexDirection: "row", alignItems: "center" }}>
                <Ionicons
                  name="battery-charging-outline"
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
                  Show Status Bar
                </Text>
              </View>
              <Switch
                value={showStatusBar}
                onValueChange={setShowStatusBar}
                trackColor={{ false: "#767577", true: accentColor }}
                thumbColor={"#f4f3f4"}
              />
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
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={{
                    flexDirection: "row",
                    alignItems: "center",
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
                </ScrollView>
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
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={{
                    flexDirection: "row",
                    alignItems: "center",
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
                </ScrollView>
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
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={{
                    flexDirection: "row",
                    alignItems: "center",
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
                        { paddingHorizontal: 15 },
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
                </ScrollView>
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

            <SettingRow label="Background Refresh">
              <View style={{ flexDirection: "column", width: "100%" }}>
                <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
                  <View style={{ flexDirection: "row", alignItems: "center" }}>
                    <Ionicons
                      name="flash-outline"
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
                      Background Refresh
                    </Text>
                  </View>
                  <Switch
                    value={backgroundRefresh}
                    onValueChange={toggleBackgroundRefresh}
                    trackColor={{ false: "#767577", true: accentColor }}
                    thumbColor={"#f4f3f4"}
                  />
                </View>
              </View>
            </SettingRow>

            <SettingRow label="Startup Behavior">
              <View
                style={{
                  flexDirection: "column",
                  width: "100%",
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
                    name="power-outline"
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
                    On Startup
                  </Text>
                </View>
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={{
                    flexDirection: "row",
                    alignItems: "center",
                    width: "100%",
                  }}
                >
                  {["new", "last"].map((mode) => (
                    <TouchableOpacity
                      key={mode}
                      onPress={() => setStartupTabMode(mode as any)}
                      style={[
                        styles.modeBtn,
                        startupTabMode === mode && {
                          backgroundColor: accentColor,
                        },
                        {
                          alignItems: "center",
                          justifyContent: "center",
                          paddingHorizontal: 20,
                        },
                      ]}
                    >
                      <Text
                        style={[
                          styles.modeBtnText,
                          startupTabMode === mode
                            ? { color: "#fff" }
                            : { color: effectiveTheme.text },
                          {
                            fontFamily: "Nunito_700Bold",
                            fontSize: 12 * fontScale,
                          },
                        ]}
                      >
                        {mode === "new" ? "New Tab" : "Continue Session"}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </ScrollView>
              </View>
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
              label="Reset all settings"
              onPress={requestResetSettings}
            >
              <View style={{ flexDirection: "row", alignItems: "center" }}>
                <Ionicons
                  name="refresh-circle-outline"
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
                  Reset all settings
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
                        onPress={() =>
                          requestClearHistory(range.ms, range.label)
                        }
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
              shadowColor: "#000",
              shadowOffset: { width: 0, height: -5 },
              shadowOpacity: 0.3,
              shadowRadius: 10,
              elevation: 20,
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
                  fontSize: 16,
                }}
              >
                {confirmActionType === "history"
                  ? `This will permanently delete history for: ${confirmHistoryPayload?.label}.`
                  : confirmActionType === "bgRefresh"
                  ? "Enabling background refresh will reload all open tabs immediately when the app starts. This may consume significant battery and data if you have many tabs open."
                  : "This will restore all app settings to their default values. Your history and tabs will be preserved."}
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
                      backgroundColor: "#ff3b30",
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
                    {confirmActionType === "resetSettings"
                      ? "Reset"
                      : confirmActionType === "bgRefresh" 
                      ? "Enable"
                      : "Delete"}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        </Modal>

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

  const handleShouldStartLoadWithRequest = (request: any) => {
    const { url } = request;

    // --- 1. Allow Internal Blob/Data URLs (PDFs, Images) ---
    if (url.startsWith("blob:") || url.startsWith("data:")) {
      return true;
    }

    // --- 2. Handle Internal "mibrowser://" Scheme ---
    if (url.startsWith("mibrowser://")) {
      handleIncomingUrl(url);
      return false;
    }

    // --- 3. Strict HTTPS Only Mode ---
    if (httpsOnly && url.startsWith("http://")) {
      const secureUrl = url.replace(/^http:\/\//i, "https://");
      // Redirect internally
      if (activeTabIdRef.current === activeTabId) {
        setTabs((prev) =>
          prev.map((t) => (t.id === activeTabId ? { ...t, url: secureUrl } : t))
        );
        setActiveUrl(secureUrl);
        setInputUrl(getDisplayHost(secureUrl));
      }
      return false;
    }

    // --- 4. EXPLICITLY BLOCK KNOWN "BAD" SCHEMES ---
    // Google Image Search often redirects to 'googleapp://' or 'intent://...package=com.google.android.googlequicksearchbox'
    // If we let WebView try to load 'googleapp://', it shows the error page.
    if (url.startsWith("googleapp://")) {
       return false; // Simply block it. Usually Google falls back to the web version automatically if this is blocked silently.
    }

    // --- 5. THE CORE RULE: ONLY Allow Standard Web Schemes in WebView ---
    // We explicitly check for the valid web protocols.
    const isStandardScheme = 
      url.startsWith("http://") || 
      url.startsWith("https://") || 
      url.startsWith("about:");

    if (isStandardScheme) {
      // ONE EXCEPTION: Check if it's an Intent masquerading as https (rare but happens with deep linking)
      // Usually these are caught by the OS, but sometimes not.
      if (url.includes("intent://") || url.includes("#Intent;")) {
          handleExternalLink(url);
          return false;
      }
      return true; // Load inside the browser
    }

    // --- 6. Handle Everything Else Externally ---
    // If we are here, it is NOT http, https, or about. It is an external app scheme.
    handleExternalLink(url);
    return false;
  };

  const renderErrorView = (tabId: string) => (
    errorDomain: string | undefined,
    errorCode: number,
    errorDesc: string
  ) => {
    // 1. Get the URL that failed from your tabs state
    const targetUrl = tabs.find((t) => t.id === tabId)?.url || "Unknown URL";

    // 2. Translate cryptic errors into human-readable text
    let friendlyTitle = "Can't Load Page";
    let friendlyDesc = "Something went wrong while loading this website.";
    let iconName = "alert-circle-outline";

    // Common Android & iOS WebKit Errors
    if (errorDesc.includes("ERR_NAME_NOT_RESOLVED") || errorCode === -2 || errorCode === -1003) {
        friendlyTitle = "Site Not Found";
        friendlyDesc = "We couldn't find this site. Please check your spelling.";
        iconName = "search-outline";
    } else if (errorDesc.includes("ERR_INTERNET_DISCONNECTED") || errorCode === -1009) {
        friendlyTitle = "No Internet";
        friendlyDesc = "Please check your Wi-Fi or mobile data connection.";
        iconName = "wifi-outline";
    } else if (errorDesc.includes("ERR_CONNECTION_TIMED_OUT") || errorCode === -1001) {
        friendlyTitle = "Connection Timed Out";
        friendlyDesc = "The server took too long to respond.";
        iconName = "time-outline";
    } else if (errorDesc.includes("ERR_CONNECTION_REFUSED") || errorCode === -1004) {
        friendlyTitle = "Connection Refused";
        friendlyDesc = "The website refused the connection.";
        iconName = "hand-left-outline";
    } else if (errorDesc.includes("ERR_CLEARTEXT_NOT_PERMITTED")) {
        friendlyTitle = "Security Error";
        friendlyDesc = "This site requires a secure HTTPS connection.";
        iconName = "lock-closed-outline";
    }

    return (
      <View
        style={[
          styles.errorContainer,
          // Ensure opaque background to cover native error
          { backgroundColor: effectiveTheme.bg }, 
        ]}
      >
        <Ionicons
          name={iconName as any}
          size={64}
          color={effectiveTheme.textSec}
          style={{ marginBottom: 20 }}
        />
        
        <Text
          style={[
            styles.errorTitle,
            { color: effectiveTheme.text, fontFamily: "Nunito_800ExtraBold" },
          ]}
        >
          {friendlyTitle}
        </Text>

        <Text
            numberOfLines={1}
            style={{
                color: accentColor,
                fontFamily: "Nunito_700Bold",
                fontSize: 14,
                marginBottom: 10,
                marginTop: 0,
                maxWidth: '85%',
                textAlign: 'center'
            }}
        >
            {targetUrl}
        </Text>

        <Text
          style={[
            styles.errorDesc,
            { color: effectiveTheme.textSec, fontFamily: "Nunito_600SemiBold" },
          ]}
        >
          {friendlyDesc}
        </Text>

        <TouchableOpacity
          style={[styles.retryBtn, { backgroundColor: accentColor }]}
          onPress={() => webViewRefs.current[tabId]?.reload()}
        >
          <Text
            style={{
              color: "#fff",
              fontFamily: "Nunito_700Bold",
              fontSize: 16,
            }}
          >
            Try Again
          </Text>
        </TouchableOpacity>
      </View>
    );
  };

  const getWebViewProps = (tabId: string) => ({
    ref: (ref: WebView | null) => (webViewRefs.current[tabId] = ref),
    renderError: renderErrorView(tabId),
    source: { uri: tabs.find((t) => t.id === tabId)?.url || "" },
    originWhitelist: ["*"],
    onShouldStartLoadWithRequest: handleShouldStartLoadWithRequest,
    injectedJavaScript: INJECTED_CONTEXT_MENU_SCRIPT,

    onNavigationStateChange: (navState: any) => {
      const { url, title, canGoBack, canGoForward, loading } = navState;

      if (url && (url.startsWith("intent://") || url.startsWith("android-app://"))) {
        return;
      }

      // 1. PERFORMANCE FIX: Only update state if something meaningful changed
      const currentTab = tabs.find((t) => t.id === tabId);
      if (currentTab) {
        const hasChanged =
          currentTab.url !== url ||
          currentTab.title !== title ||
          currentTab.canGoBack !== canGoBack ||
          currentTab.canGoForward !== canGoForward ||
          currentTab.loading !== loading;

        if (!hasChanged) return;
      }

      // 2. Smart Title Fallback (Don't show http://... as the title if possible)
      const newTitle =
        title && title.length > 0 && !title.includes("://")
          ? title
          : url
          ? getDisplayHost(url)
          : "New Tab";

      setTabs((prev) =>
        prev.map((t) => {
          if (t.id !== tabId) return t;
          return {
            ...t,
            url,
            title: newTitle,
            canGoBack,
            canGoForward,
            loading,
          };
        })
      );

      // 3. Update Active State
      if (tabId === activeTabId) {
        setCanGoBack(canGoBack);
        setCanGoForward(canGoForward);
        canGoBackRef.current = canGoBack;
        canGoForwardRef.current = canGoForward;
        setIsLoading(loading);

        // Only update the address bar if the user isn't currently typing in it
        if (!isInputFocused && url) {
          setActiveUrl(url);
          setInputUrl(getDisplayHost(url));
        }

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
        ignoreNextScroll.current = true;
        showBar();
        setIsLoading(true);
        progressAnim.setValue(0);
        Animated.timing(progressAnim, {
          toValue: 0.1,
          duration: 300,
          useNativeDriver: false,
        }).start();
      }
    },

    // LOADING END
    onLoadEnd: () => {
      if (tabId === activeTabId) {
        setIsLoading(false);
        Animated.timing(progressAnim, {
          toValue: 1,
          duration: 200,
          useNativeDriver: false,
        }).start(() => {
          setTimeout(() => progressAnim.setValue(0), 200);
        });
      }
    },

    // ERROR HANDLING
    onError: (e: any) => {
      if (tabId === activeTabId) setIsLoading(false);

      const { nativeEvent } = e;
      const currentTime = Date.now();

      // 1. LOOP BREAKER
      if (currentTime - lastErrorTimestamp.current < 1000) {
        return;
      }
      lastErrorTimestamp.current = currentTime;

      // NEW: Handle Intent Redirects that bypassed onShouldStartLoadWithRequest
      // -10 is ERR_UNKNOWN_URL_SCHEME often used for intents/custom schemes
      const isUnknownScheme = nativeEvent.code === -10 || nativeEvent.description?.includes("ERR_UNKNOWN_URL_SCHEME");
      const isIntent = nativeEvent.url?.startsWith("intent://") || nativeEvent.url?.startsWith("android-app://");

      if (isUnknownScheme || isIntent) {
        // Hand off to the external handler
        handleExternalLink(nativeEvent.url);
        
        // Go back to the previous page so we don't sit on an error screen
        // Use the ref directly to avoid closure stale state
        const ref = webViewRefs.current[tabId];
        if (ref) ref.goBack();
        return;
      }

      // Existing Auto-Search Logic for DNS errors
      if (
        nativeEvent.description === "net::ERR_NAME_NOT_RESOLVED" ||
        nativeEvent.code === -2
      ) {
        const failedUrl = nativeEvent.url;
        const currentSearchEngine = SEARCH_ENGINES[searchEngineIndex];
        const isAlreadySearch = failedUrl?.startsWith(currentSearchEngine.url);

        if (!isAlreadySearch && failedUrl && tabId === activeTabId) {
          let query = failedUrl.replace(/^https?:\/\//, "").replace(/\/$/, "");
          const searchUrl = `${
            currentSearchEngine.url
          }${encodeURIComponent(query)}`;
          
          setTabs((prev) =>
            prev.map((t) => (t.id === tabId ? { ...t, url: searchUrl } : t))
          );
          if (tabId === activeTabId) {
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
    onTouchStart: () => {
      if (isInputFocused) Keyboard.dismiss();
    },
    overScrollMode: "never",
    scrollEventThrottle: 16,
    startInLoadingState: true,
    renderLoading: () => (
      <View style={styles.loadingOverlay}>
        <ActivityIndicator size="large" color={accentColor} />
      </View>
    ),
    javaScriptEnabled: jsEnabled,
    userAgent: desktopMode
      ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
      : "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    sharedCookiesEnabled: !blockCookies,
    domStorageEnabled: true,
    androidLayerType: "hardware" as const,
    pullToRefreshEnabled: false,
    allowsFullscreenVideo: true,
    mediaPlaybackRequiresUserAction: false,
    javaScriptCanOpenWindowsAutomatically: true,
    onFullScreen: handleFullScreen,
    contentInset: isFullscreen
      ? { top: 0, bottom: 0, left: 0, right: 0 }
      : { bottom: pillHeight + 20 },
    geolocationEnabled: true,
    onPermissionRequest: handleAndroidPermissionRequest,
    allowsBackForwardNavigationGestures: true,
  });

  if (!fontsLoaded || !isAppReady) return null;

  return (
    <View
      style={[
        styles.container,
        {
          backgroundColor: effectiveTheme.bg,
          paddingTop:
            showStatusBar && !isFullscreen ? StatusBar.currentHeight || 0 : 0,
        },
      ]}
    >
      {!isFullscreen && (
        <StatusBar
          translucent
          hidden={!showStatusBar}
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

        {tabs.map((tab) => {
          // If the tab has no URL (New Tab Page), don't render a WebView
          if (!tab.url) return null;

          const isActive = tab.id === activeTabId;

          if (!tab.hasLoadedOnce) {
             return null; 
          }

          return (
            <View
              key={tab.id}
              style={[
                StyleSheet.absoluteFill,
                {
                  // Using opacity 0 and zIndex -1 hides it visually
                  opacity: isActive ? 1 : 0,
                  zIndex: isActive ? 1 : -1,
                  // Move off-screen to ensure no touch events are captured
                  transform: [{ translateX: isActive ? 0 : 9999 }],
                },
              ]}
              // IMPORTANT: "none" prevents the hidden WebViews from capturing touches
              pointerEvents={isActive ? "auto" : "none"}
            >
              <WebView
                {...getWebViewProps(tab.id)}
                // Pausing javascript on background tabs saves massive CPU/Battery
                // Only strictly pause if not active and desktop mode isn't forcing keep-alive
                pauseJavaScriptBeforeUnmount={true}
                source={{ uri: tab.url || tab.initialUrl || "" }}
                containerStyle={
                  isFullscreen ? { backgroundColor: "#000" } : undefined
                }
              />
            </View>
          );
        })}

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
                {HOME_LOGO_TEXT}
              </Text>
            </Animated.View>
          </View>
        )}
      </Animated.View>

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
                bottom: Math.max(insets.bottom + 10, 10),
              },
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
                  backgroundColor: effectiveTheme.glass,
                  borderWidth: 0,
                  borderRadius: 25,
                  overflow: "hidden",
                },
              ]}
            >
              <View
                style={{
                  ...StyleSheet.absoluteFillObject,
                  backgroundColor: effectiveTheme.inputBg,
                }}
              />
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
                    opacity: containerOpacity,
                    transform: [
                      {
                        translateY: Animated.subtract(
                          scrollTranslateY,
                          keyboardHeight
                        ),
                      },
                      { scale: containerScale }, // <--- Shrink effect
                    ],
                  },
                ]}
              >
                <View
                  style={[styles.gestureArea, { height: pillHeight }]}
                  {...panResponder.panHandlers}
                >
                  <Animated.View
                    style={[
                      styles.pillBase,
                      {
                        height: pillHeight,
                        backgroundColor: effectiveTheme.glass,
                        borderColor: effectiveTheme.glassBorder,
                        borderRadius: cornerRadius * 2,
                        shadowColor: "#000",
                        shadowOffset: { width: 0, height: 5 },
                        shadowOpacity: 0.2,
                        shadowRadius: 15,
                        elevation: 10,
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

                  <Animated.View
                    style={[
                      styles.pillBase,
                      {
                        height: pillHeight,
                        backgroundColor: effectiveTheme.glass,
                        borderColor: effectiveTheme.glassBorder,
                        borderRadius: cornerRadius * 2,
                        shadowColor: "#000",
                        shadowOffset: { width: 0, height: 5 },
                        shadowOpacity: 0.2,
                        shadowRadius: 15,
                        elevation: 10,
                      },
                      {
                        zIndex: 2,
                        opacity: searchPillOpacity,
                        transform: [{ translateY: searchPillTranslateY }],
                      },
                    ]}
                    pointerEvents={isSearchActive ? "auto" : "none"}
                  >
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

                      <Animated.View
                        style={[
                          styles.inputWrapper,
                          {
                            backgroundColor: effectiveTheme.inputBg,
                            opacity: contentOpacity,
                            borderRadius: cornerRadius,
                            height: pillHeight * 0.7,
                            overflow: "hidden",
                          },
                        ]}
                      >

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
                              zIndex: 1,
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
                              onPress={() =>
                                webViewRefs.current[activeTabId]?.reload()
                              }
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

      {contextMenuVisible && (
        <View 
          style={[
            StyleSheet.absoluteFill, 
            { 
              zIndex: 99999, 
              elevation: 99999, 
              justifyContent: 'center', 
              alignItems: 'center',
              backgroundColor: 'rgba(0,0,0,0.4)' 
            }
          ]}
        >
          <TouchableWithoutFeedback onPress={() => setContextMenuVisible(false)}>
             <View style={StyleSheet.absoluteFill} />
          </TouchableWithoutFeedback>

          <View 
            style={{ 
               width: '70%',      
               maxWidth: 280,     
               backgroundColor: effectiveTheme.surface, 
               borderRadius: cornerRadius, 
               shadowColor: "#000",
               shadowOffset: { width: 0, height: 10 },
               shadowOpacity: 0.3,
               shadowRadius: 20,
               elevation: 10,
               overflow: 'hidden'
            }}
          >

            <View style={{ padding: 15, borderBottomWidth: 1, borderBottomColor: effectiveTheme.bg }}>
              <Text numberOfLines={2} style={{ color: effectiveTheme.text, fontFamily: 'Nunito_700Bold', fontSize: 14 * fontScale, marginBottom: 2 }}>
                {contextMenuData?.text || (contextMenuData?.imgUrl ? "Image" : "Link")}
              </Text>
              <Text numberOfLines={1} style={{ color: effectiveTheme.textSec, fontFamily: 'Nunito_600SemiBold', fontSize: 11 * fontScale }}>
                {contextMenuData?.url || contextMenuData?.imgUrl}
              </Text>
            </View>

            <View style={{ paddingVertical: 2 }}>
              
              {(contextMenuData?.url || contextMenuData?.imgUrl) && (
                <TouchableOpacity 
                  style={{ flexDirection: 'row', alignItems: 'center', padding: 12 }}
                  onPress={() => {
                    const target = contextMenuData?.url || contextMenuData?.imgUrl;
                    if (target) addNewTab(target);
                    setContextMenuVisible(false);
                  }}
                >
                  <Ionicons name="open-outline" size={18 * fontScale} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                  <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 14 * fontScale }}>
                    Open in New Tab
                  </Text>
                </TouchableOpacity>
              )}

              <TouchableOpacity 
                style={{ flexDirection: 'row', alignItems: 'center', padding: 12 }}
                onPress={() => {
                   const target = contextMenuData?.url || contextMenuData?.imgUrl;
                   if (target) {
                     setInputUrl(target);
                     setIsInputFocused(true);
                     setActiveView('none'); 
                   }
                   setContextMenuVisible(false);
                }}
              >
                 <Ionicons name="copy-outline" size={18 * fontScale} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                 <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 14 * fontScale }}>
                    Copy to Address Bar
                 </Text>
              </TouchableOpacity>

              <TouchableOpacity 
                style={{ flexDirection: 'row', alignItems: 'center', padding: 12 }}
                onPress={async () => {
                   const target = contextMenuData?.url || contextMenuData?.imgUrl;
                   if (target) await Share.share({ message: target, url: target });
                   setContextMenuVisible(false);
                }}
              >
                 <Ionicons name="share-social-outline" size={18 * fontScale} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                 <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 14 * fontScale }}>
                    Share
                 </Text>
              </TouchableOpacity>

              {contextMenuData?.imgUrl && (
                <TouchableOpacity 
                  style={{ flexDirection: 'row', alignItems: 'center', padding: 12 }}
                  onPress={handleDownloadImage}
                >
                  <Ionicons name="download-outline" size={18 * fontScale} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                  <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 14 * fontScale }}>
                    Save Image
                  </Text>
                </TouchableOpacity>
              )}

            </View>

            <TouchableOpacity 
              onPress={() => setContextMenuVisible(false)}
              style={{ 
                padding: 12, 
                alignItems: 'center', 
                backgroundColor: effectiveTheme.card, 
                borderTopWidth: 1, 
                borderTopColor: effectiveTheme.bg 
              }}
            >
              <Text style={{ color: effectiveTheme.textSec, fontFamily: 'Nunito_700Bold', fontSize: 13 * fontScale }}>Cancel</Text>
            </TouchableOpacity>

          </View>
        </View>
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
  wrapRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    width: "100%",
    justifyContent: "flex-start",
    gap: 10,
    marginBottom: 5,
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
  colorDot: { width: 20, height: 20, borderRadius: 12, marginLeft: 10 },
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
  errorContainer: {
    position: "absolute",
    top: 0,
    bottom: 0,
    left: 0,
    right: 0,
    zIndex: 100,
    width: "100%",
    height: "100%",
    justifyContent: "center",
    alignItems: "center",
    padding: 30,
  },
  errorTitle: {
    fontSize: 24,
    marginBottom: 10,
    textAlign: "center",
  },
  errorDesc: {
    fontSize: 16,
    textAlign: "center",
    marginBottom: 30,
    lineHeight: 22,
  },
  retryBtn: {
    paddingHorizontal: 25,
    paddingVertical: 12,
    borderRadius: 25,
    elevation: 3,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
  },
});