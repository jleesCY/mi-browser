import {
  Nunito_400Regular,
  Nunito_600SemiBold,
  Nunito_700Bold,
  Nunito_800ExtraBold,
  useFonts,
} from "@expo-google-fonts/nunito";
import { Ionicons } from "@expo/vector-icons";
import * as Clipboard from 'expo-clipboard';
import * as FileSystem from 'expo-file-system/legacy';
import * as MediaLibrary from 'expo-media-library';
import * as Print from 'expo-print';
import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Animated,
  BackHandler,
  Keyboard,
  Linking,
  Modal,
  PanResponder,
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

import {
  HIDDEN_TRANSLATE_Y,
  HOME_LOGO_TEXT,
  INJECTED_CONTEXT_MENU_SCRIPT,
  SEARCH_ENGINES,
  SNAP_CLOSED,
  SNAP_DEFAULT,
  SNAP_FULL,
  SWAP_DISTANCE
} from "../src/constants";
import { handleExternalLink } from "../src/navigationUtils";
import { getDisplayHost } from "../src/utils";

// Custom Hooks
import { useBrowserSettings } from "../src/hooks/useBrowserSettings";
import { useHistory } from "../src/hooks/useHistory";
import { useTabs } from "../src/hooks/useTabs";

// Components
import { OverlaySheet } from "../src/components/BrowserOverlay/OverlaySheet";
import { BrowserWebView } from "../src/components/BrowserWebView";
import { HistoryView } from "../src/components/HistoryView";
import { SettingsView } from "../src/components/SettingsView";
import { TabsView } from "../src/components/TabsView";

export default function App() {
  const insets = useSafeAreaInsets();

  let [fontsLoaded] = useFonts({
    Nunito_400Regular,
    Nunito_600SemiBold,
    Nunito_700Bold,
    Nunito_800ExtraBold,
  });

  // --- STATE MANAGEMENT VIA HOOKS ---
  const settings = useBrowserSettings(fontsLoaded); // Pass true/false or separate ready state
  const {
    accentColor, searchEngineIndex, cornerRadius, uiPadding,
    fontScale, showStatusBar, pillHeight, progressBarMode,
    startupTabMode, desktopMode, forceSearchMode, setForceSearchMode, jsEnabled, httpsOnly, blockCookies,
    effectiveTheme, areSettingsLoaded, backgroundRefresh, readerModeEnabled
  } = settings;

  const { history, addToHistory, deleteHistory, deleteHistoryItem } = useHistory(areSettingsLoaded);
  
  const {
    tabs, setTabs, activeTabId, setActiveTabId, 
    activeUrl, setActiveUrl, inputUrl, setInputUrl, 
    areTabsLoaded, addNewTab, deleteTab, updateTab, activeTabIdRef
  } = useTabs({ areSettingsLoaded, startupTabMode, backgroundRefresh });

  const currentTab = tabs.find((t) => t.id === activeTabId);

  const isAppReady = fontsLoaded && areSettingsLoaded && areTabsLoaded;

  // --- LOCAL UI STATE ---
  const progressAnim = useRef(new Animated.Value(0)).current;
  const [isLoading, setIsLoading] = useState(false);

  // Navigation State (UI reflection)
  const canGoBackRef = useRef(false);
  const canGoForwardRef = useRef(false);

  // Modals & Overlays
  const [isConfirmModalVisible, setIsConfirmModalVisible] = useState(false);
  const [confirmActionType, setConfirmActionType] = useState<"history" | "resetSettings" | "bgRefresh" | null>(null);
  const [confirmHistoryPayload, setConfirmHistoryPayload] = useState<{ ms: number; label: string; } | null>(null);

  const [contextMenuVisible, setContextMenuVisible] = useState(false);
  const [contextMenuData, setContextMenuData] = useState<{ url: string | null; imgUrl: string | null; text: string; } | null>(null);

  const [activeView, setActiveView] = useState<"none" | "tabs" | "history" | "settings">("none");
  const [isSearchActive, setIsSearchActive] = useState(true);
  const isSearchActiveRef = useRef(true);
  const [isInputFocused, setIsInputFocused] = useState(false);

  // Search States for Sub-views
  const [settingsSearch, setSettingsSearch] = useState("");
  const [tabsSearch, setTabsSearch] = useState("");
  const [historySearch, setHistorySearch] = useState("");

  // Rename Modal
  const [isRenameModalVisible, setIsRenameModalVisible] = useState(false);
  const [tabToRename, setTabToRename] = useState<string | null>(null);
  const [renameText, setRenameText] = useState("");
  const [renameShowLogo, setRenameShowLogo] = useState(true);

  // Sub Menu State
  const [isSubMenuVisible, setIsSubMenuVisible] = useState(false);

  const [isFullscreen, setIsFullscreen] = useState(false);
  const [isBarHiddenState, setIsBarHiddenState] = useState(false);

  // Refs
  const webViewRefs = useRef<{ [key: string]: WebView | null }>({});
  const ignoreNextScroll = useRef(false);
  const isBarHidden = useRef(false);

  // Animations
  const scrollTranslateY = useRef(new Animated.Value(0)).current;
  const currentScrollTrans = useRef(0);
  const lastScrollY = useRef(0);
  const animVal = useRef(new Animated.Value(0)).current;
  const horizontalDrag = useRef(new Animated.Value(0)).current;
  const overlayHeightAnim = useRef(new Animated.Value(SNAP_CLOSED)).current;
  const currentOverlayHeight = useRef(SNAP_CLOSED);
  const keyboardHeight = useRef(new Animated.Value(0)).current;
  const isPillFocusedAnim = useRef(new Animated.Value(0)).current;
  const logoScale = useRef(new Animated.Value(1)).current;
  const logoPan = useRef(new Animated.ValueXY({ x: 0, y: 0 })).current;

  // --- SYNC UI WHEN SWITCHING TABS ---
  useEffect(() => {
    if (currentTab) {
      ignoreNextScroll.current = true;
      // URL/Input handled by hook mostly, but we sync just in case
      // Buttons
      canGoBackRef.current = currentTab.canGoBack || false;
      canGoForwardRef.current = currentTab.canGoForward || false;
      // Loading
      setIsLoading(currentTab.loading || false);
      // Progress
      progressAnim.setValue(currentTab.loading ? 0.2 : 0);
    }
  }, [activeTabId, tabs, currentTab, progressAnim]);

  useEffect(() => {
    Animated.timing(isPillFocusedAnim, {
      toValue: isInputFocused ? 1 : 0,
      duration: 200,
      useNativeDriver: false
    }).start();
  }, [isInputFocused, isPillFocusedAnim]);

  useEffect(() => {
    isSearchActiveRef.current = isSearchActive;
    if (isSearchActive) setIsSubMenuVisible(false);
  }, [isSearchActive]);

  useEffect(() => {
    if (activeView !== "none") {
        setIsSubMenuVisible(false);
        settings.setIsSearchEngineOpen(false);
        settings.setIsClearHistoryOpen(false);

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
  }, [keyboardHeight]);

  // --- UI ACTIONS ---
  const snapToSearch = useCallback(() => {
    setIsSearchActive(true);
    Animated.spring(animVal, {
      toValue: 0,
      tension: 60,
      friction: 9,
      useNativeDriver: false,
    }).start();
  }, [animVal]);

  const goHome = useCallback(() => {
    setActiveUrl(null);
    setInputUrl("");
    updateTab(activeTabId, { url: null, requestedUrl: null, title: "New Tab", showLogo: true });
    snapToSearch();
  }, [activeTabId, updateTab, snapToSearch, setActiveUrl, setInputUrl]);

  const closeOverlay = useCallback(() => {
    Keyboard.dismiss();
    Animated.timing(overlayHeightAnim, {
      toValue: SNAP_CLOSED,
      duration: 250,
      useNativeDriver: false,
    }).start(() => {
      setActiveView("none");
      snapToSearch();
      setSettingsSearch("");
      setTabsSearch("");
      setHistorySearch("");
    });
    currentOverlayHeight.current = SNAP_CLOSED;
  }, [overlayHeightAnim, snapToSearch]);

  const showBar = useCallback(() => {
    isBarHidden.current = false;
    setIsBarHiddenState(false);
    currentScrollTrans.current = 0;
    Animated.timing(scrollTranslateY, {
      toValue: 0,
      duration: 100,
      useNativeDriver: false,
    }).start();
  }, [scrollTranslateY]);

  const hideBar = useCallback(() => {
    isBarHidden.current = true;
    setIsBarHiddenState(true);
    currentScrollTrans.current = HIDDEN_TRANSLATE_Y;
    Animated.timing(scrollTranslateY, {
      toValue: HIDDEN_TRANSLATE_Y,
      duration: 100,
      useNativeDriver: false,
    }).start();
  }, [scrollTranslateY]);

  // --- NAVIGATION LOGIC ---
  const handleIncomingUrl = React.useCallback((url: string | null) => {
    // This is now mostly handled by useTabs startup logic for INITIAL url.
    // For runtime incoming URLs:
    if (!url) return;
    // useTabs doesn't export the parser, but we don't strictly need it if we assume addNewTab handles raw logic?
    // Actually addNewTab expects a URL. We should use Linking listener.
    // Ideally we'd reuse logic. For now, let's just add new tab.
    addNewTab(url);
    setActiveView("none");
  }, [addNewTab]);

  useEffect(() => {
    const handleDeepLink = (event: { url: string }) => {
      handleIncomingUrl(event.url);
    };
    const subscription = Linking.addEventListener("url", handleDeepLink);
    return () => subscription.remove();
  }, [handleIncomingUrl]);

  // Back Button Handler
  useEffect(() => {
    const onBackPress = () => {
      if (activeView !== "none") {
        closeOverlay();
        return true;
      }
      if (isInputFocused) {
        Keyboard.dismiss();
        setIsInputFocused(false);
        return true;
      }
      if (canGoBackRef.current && webViewRefs.current[activeTabId]) {
        webViewRefs.current[activeTabId]?.goBack();
        showBar();
        return true;
      }
      return false;
    };
    const subscription = BackHandler.addEventListener("hardwareBackPress", onBackPress);
    return () => subscription.remove();
  }, [activeView, isInputFocused, activeTabId, closeOverlay, showBar]);

  const handleGoPress = () => {
    Keyboard.dismiss();
    const text = inputUrl.trim();
    if (!text) return;

    let targetUrl = "";
    
    if (forceSearchMode) {
       targetUrl = `${SEARCH_ENGINES[searchEngineIndex].url}${encodeURIComponent(text)}`;
    } else {
        // 1. Check if it explicitly starts with http/https
        if (/^(http|https):\/\//i.test(text)) {
          if (httpsOnly && text.startsWith("http://")) {
            targetUrl = text.replace(/^http:\/\//i, "https://");
          } else {
            targetUrl = text;
          }
        } else {
          const domainRegex = /^([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(\/.*)?$/;
          const ipRegex = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(\/.*)?$/;
          const localhostRegex = /^localhost(?::[0-9]{1,5})?(\/.*)?$/;

          if (!text.includes(" ") && (domainRegex.test(text) || ipRegex.test(text) || localhostRegex.test(text))) {
            if (localhostRegex.test(text) || ipRegex.test(text)) {
              targetUrl = `http://${text}`;
            } else {
              targetUrl = `https://${text}`;
            }
          } else {
            targetUrl = `${SEARCH_ENGINES[searchEngineIndex].url}${encodeURIComponent(text)}`;
          }
        }
    }

    setActiveUrl(targetUrl);
    updateTab(activeTabId, { url: targetUrl, requestedUrl: targetUrl, title: text });

    if (activeUrl === targetUrl && webViewRefs.current[activeTabId]) {
      webViewRefs.current[activeTabId]?.reload();
    }
    snapToSearch();
  };

  const handleShare = async () => {
    if (!activeUrl) return;
    try {
      await Share.share({ message: activeUrl, url: activeUrl, title: "Share Link" });
    } catch {}
  };

  const handleCopyLink = async () => {
    if (!activeUrl) return;
    setIsSubMenuVisible(false);
    await Clipboard.setStringAsync(activeUrl);
    Alert.alert("Copied", "Link copied to clipboard");
  };

  const handlePrint = async () => {
    if (!activeUrl) return;
    setIsSubMenuVisible(false);
    
    if (webViewRefs.current[activeTabId]) {
        const js = `
            window.ReactNativeWebView.postMessage(JSON.stringify({
                type: 'PRINT_HTML',
                html: document.documentElement.outerHTML
            }));
        `;
        webViewRefs.current[activeTabId]?.injectJavaScript(js);
    } else {
        // Fallback if webview ref not found (shouldn't happen if active)
        try {
            await Print.printAsync({ uri: activeUrl });
        } catch {
            Alert.alert("Error", "Could not print this page.");
        }
    }
  };

  const handleDownloadImage = async () => {
    const url = contextMenuData?.imgUrl;
    if (!url) return;
    try {
      const { status } = await MediaLibrary.requestPermissionsAsync(true);
      if (status !== 'granted') {
        Alert.alert("Permission Required", "This app needs access to your Photos.");
        return;
      }

      let extension = '.jpg';
      if (url.includes('.png')) extension = '.png';
      else if (url.includes('.gif')) extension = '.gif';
      else if (url.includes('.webp')) extension = '.webp';
      else if (url.startsWith('data:image/png')) extension = '.png';
      
      const fileName = `download_${Date.now()}${extension}`; 
      const fileUri = FileSystem.documentDirectory + fileName;

      if (url.startsWith('data:')) {
        const base64Code = url.split('base64,')[1];
        await FileSystem.writeAsStringAsync(fileUri, base64Code, {
          encoding: FileSystem.EncodingType.Base64,
        });
      } else {
        const downloadRes = await FileSystem.downloadAsync(url, fileUri);
        if (downloadRes.status !== 200) throw new Error("Download failed");
      }

      await MediaLibrary.saveToLibraryAsync(fileUri);
      Alert.alert("Success", "Image saved to gallery!");
      setContextMenuVisible(false);

    } catch (e: any) {
      Alert.alert("Save Error", e.message || "Unknown error");
    }
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
    if (Math.abs(dy) > 1) {
      if (dy > 0 && !isBarHidden.current) hideBar();
      else if (dy < 0 && isBarHidden.current) showBar();
    }
    lastScrollY.current = y;
  };

  // --- PAN RESPONDERS (Gestures) ---
  const logoResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        Animated.spring(logoScale, { toValue: 1.2, useNativeDriver: false }).start();
        logoPan.setOffset({ x: (logoPan.x as any)._value, y: (logoPan.y as any)._value });
        logoPan.setValue({ x: 0, y: 0 });
      },
      onPanResponderMove: Animated.event([null, { dx: logoPan.x, dy: logoPan.y }], { useNativeDriver: false }),
      onPanResponderRelease: () => {
        logoPan.flattenOffset();
        Animated.spring(logoPan, { toValue: { x: 0, y: 0 }, friction: 6, tension: 80, useNativeDriver: false }).start();
        Animated.spring(logoScale, { toValue: 1, friction: 6, tension: 80, useNativeDriver: false }).start();
      },
    })
  ).current;

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => false,
      onMoveShouldSetPanResponder: (_, gestureState) => Math.abs(gestureState.dy) > 10 || Math.abs(gestureState.dx) > 10,
      onPanResponderGrant: () => { animVal.stopAnimation(); scrollTranslateY.stopAnimation(); },
      onPanResponderMove: (_, gestureState) => {
        const { dy, dx } = gestureState;
        if (isSearchActiveRef.current) {
          if (Math.abs(dx) > Math.abs(dy)) horizontalDrag.setValue(dx);
          else {
            if (dy < 0) animVal.setValue(dy);
            else {
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
            const currentTabId = activeTabIdRef.current;
            const currentWebView = webViewRefs.current[currentTabId];
            if (dx > 0) currentWebView?.goBack();
            else if (dx < 0) currentWebView?.goForward();

            Animated.spring(horizontalDrag, { toValue: 0, useNativeDriver: false }).start();
            Animated.spring(animVal, { toValue: 0, useNativeDriver: false }).start();
            showBar();
            return;
          }
          Animated.spring(horizontalDrag, { toValue: 0, useNativeDriver: false }).start();
          if (dy < -30) {
            setIsSearchActive(false);
            Animated.spring(animVal, { toValue: -SWAP_DISTANCE, tension: 60, friction: 9, useNativeDriver: false }).start();
          } else {
            Animated.spring(animVal, { toValue: 0, useNativeDriver: false }).start();
            if (vy > 0.5 || currentScrollTrans.current > HIDDEN_TRANSLATE_Y / 2) hideBar();
            else showBar();
          }
        } else {
          if (dy > 30 || vy > 0.5) {
            setIsSearchActive(true);
            Animated.spring(animVal, { toValue: 0, tension: 60, friction: 9, useNativeDriver: false }).start();
          } else {
            Animated.spring(animVal, { toValue: -SWAP_DISTANCE, tension: 60, friction: 9, useNativeDriver: false }).start();
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
        if (gestureState.dy < 0) scrollTranslateY.setValue(Math.max(0, HIDDEN_TRANSLATE_Y + gestureState.dy));
      },
      onPanResponderRelease: (_, gestureState) => {
        if (gestureState.dy < -20 || gestureState.vy < -0.5) showBar();
        else Animated.spring(scrollTranslateY, { toValue: HIDDEN_TRANSLATE_Y, useNativeDriver: false }).start(() => { currentScrollTrans.current = HIDDEN_TRANSLATE_Y; });
      },
    })
  ).current;

  const sheetPanResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) => Math.abs(gestureState.dy) > 5,
      onPanResponderGrant: () => overlayHeightAnim.stopAnimation((val) => { currentOverlayHeight.current = val; }),
      onPanResponderMove: (_, gestureState) => {
        overlayHeightAnim.setValue(Math.min(currentOverlayHeight.current - gestureState.dy, SNAP_FULL));
      },
      onPanResponderRelease: (_, gestureState) => {
        const { dy, vy } = gestureState;
        const finalHeight = currentOverlayHeight.current - dy;
        let target = SNAP_DEFAULT;
        if (finalHeight > (SNAP_FULL + SNAP_DEFAULT) / 2) target = SNAP_FULL;
        else if (finalHeight > SNAP_DEFAULT * 0.7) target = SNAP_DEFAULT;
        else target = SNAP_CLOSED;
        if (vy < -1) target = SNAP_FULL;
        if (vy > 1) target = finalHeight > SNAP_DEFAULT ? SNAP_DEFAULT : SNAP_CLOSED;
        if (target === SNAP_CLOSED) closeOverlay();
        else {
          Animated.spring(overlayHeightAnim, { toValue: target, tension: 50, friction: 8, useNativeDriver: false }).start();
          currentOverlayHeight.current = target;
        }
      },
    })
  ).current;

  // --- STYLE INTERPOLATIONS ---
  const searchPillTranslateY = animVal;
  const searchPillOpacity = animVal.interpolate({ inputRange: [-SWAP_DISTANCE, -SWAP_DISTANCE / 2, 0], outputRange: [0, 0, 1], extrapolate: "clamp" });
  const menuPillScale = animVal.interpolate({ inputRange: [-SWAP_DISTANCE, 0], outputRange: [1, 0.9], extrapolate: "clamp" });
  const menuPillOpacity = animVal.interpolate({ inputRange: [-SWAP_DISTANCE, -10, 0], outputRange: [1, 0, 0], extrapolate: "clamp" });
  const containerScale = scrollTranslateY.interpolate({ inputRange: [0, HIDDEN_TRANSLATE_Y], outputRange: [1, 0.6], extrapolate: "clamp" });
  const containerOpacity = scrollTranslateY.interpolate({ inputRange: [0, HIDDEN_TRANSLATE_Y * 0.75, HIDDEN_TRANSLATE_Y], outputRange: [1, 0.5, 0], extrapolate: "clamp" });
  const recallOpacity = scrollTranslateY.interpolate({ inputRange: [0, HIDDEN_TRANSLATE_Y - 20, HIDDEN_TRANSLATE_Y], outputRange: [0, 0, 1], extrapolate: "clamp" });
  const backArrowOpacity = horizontalDrag.interpolate({ inputRange: [0, 50], outputRange: [0, 1], extrapolate: "clamp" });
  const forwardArrowOpacity = horizontalDrag.interpolate({ inputRange: [-50, 0], outputRange: [1, 0], extrapolate: "clamp" });
  const contentOpacity = horizontalDrag.interpolate({ inputRange: [-50, 0, 50], outputRange: [0, 1, 0], extrapolate: "clamp" });

  // Calculate effective radius for the pill based on settings
  // If 'Round' (22) or higher, we force a perfect geometric pill (height/2)
  // Otherwise (Square/Soft), we use the scaled cornerRadius (usually * 2 looks best for container)
  const effectivePillRadius = cornerRadius >= 20 ? pillHeight / 2 : cornerRadius * 2;

  // Keyboard Adaptation Interpolations
  // Only adapt pill visuals if the PILL INPUT itself is focused
  const effectiveKeyboardHeight = Animated.multiply(keyboardHeight, isPillFocusedAnim);

  const pillCornerRadiusAnim = effectiveKeyboardHeight.interpolate({
    inputRange: [0, 100],
    outputRange: [effectivePillRadius, 0],
    extrapolate: "clamp",
  });
  const pillShadowOpacityAnim = effectiveKeyboardHeight.interpolate({
    inputRange: [0, 100],
    outputRange: [0.2, 0],
    extrapolate: "clamp",
  });
  const containerPaddingHAnim = effectiveKeyboardHeight.interpolate({
    inputRange: [0, 100],
    outputRange: [10, 0],
    extrapolate: "clamp",
  });
  const containerPaddingBAnim = effectiveKeyboardHeight.interpolate({
    inputRange: [0, 100],
    outputRange: [Math.max(insets.bottom + 10, 10), 0],
    extrapolate: "clamp",
  });
  const pillBackgroundAnim = effectiveKeyboardHeight.interpolate({
    inputRange: [0, 100],
    outputRange: [effectiveTheme.glass, effectiveTheme.glass],
    extrapolate: "clamp",
  });
  const inputBackgroundAnim = effectiveKeyboardHeight.interpolate({
    inputRange: [0, 100],
    outputRange: [effectiveTheme.inputBg, effectiveTheme.inputBg],
    extrapolate: "clamp",
  });
  const pillElevationAnim = effectiveKeyboardHeight.interpolate({
    inputRange: [0, 10],
    outputRange: [10, 0],
    extrapolate: "clamp",
  });

  if (!isAppReady) {
    return (
      <View style={{ flex: 1, backgroundColor: "#000", justifyContent: "center", alignItems: "center" }}>
        <ActivityIndicator size="large" color={accentColor || "#007AFF"} />
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: effectiveTheme.bg, paddingTop: showStatusBar && !isFullscreen ? StatusBar.currentHeight || 0 : 0 }]}>
      {!isFullscreen && (
        <StatusBar translucent hidden={!showStatusBar} backgroundColor="transparent" barStyle={effectiveTheme.isDark ? "light-content" : "dark-content"} />
      )}

      <Animated.View style={[styles.webViewContainer, { paddingBottom: isFullscreen ? 0 : Animated.add(keyboardHeight, insets.bottom), backgroundColor: effectiveTheme.bg }]}>
        {tabs.map((tab) => {
          if (!tab.url) return null;
          const isActive = tab.id === activeTabId;
          if (!tab.hasLoadedOnce) return null;

          return (
            <BrowserWebView
                key={tab.id}
                ref={(ref: any) => webViewRefs.current[tab.id] = ref}
                tab={tab}
                isActive={isActive}
                isFullscreen={isFullscreen}
                settings={{ jsEnabled, desktopMode, blockCookies, accentColor, pillHeight, httpsOnly, searchEngineIndex, readerModeEnabled }}
                effectiveTheme={effectiveTheme}
                onUpdateTab={updateTab}
                onActiveTabUpdate={(updates) => {
                    canGoBackRef.current = updates.canGoBack;
                    canGoForwardRef.current = updates.canGoForward;
                    setIsLoading(updates.loading);
                    if (!isInputFocused && updates.url) {
                        setActiveUrl(updates.url);
                        setInputUrl(getDisplayHost(updates.url));
                        if (!updates.loading && updates.url !== "about:blank") addToHistory(updates.url, updates.title);
                    }
                }}
                onLoadProgress={(p) => Animated.timing(progressAnim, { toValue: p, duration: 200, useNativeDriver: false }).start()}
                onLoadStart={() => {
                    ignoreNextScroll.current = true;
                    showBar();
                    setIsLoading(true);
                    progressAnim.setValue(0);
                    Animated.timing(progressAnim, { toValue: 0.1, duration: 300, useNativeDriver: false }).start();
                }}
                onLoadEnd={() => {
                    setIsLoading(false);
                    Animated.timing(progressAnim, { toValue: 1, duration: 200, useNativeDriver: false }).start(() => setTimeout(() => progressAnim.setValue(0), 200));
                }}
                onScroll={handleScroll}
                onScrollEnd={() => { if (isBarHidden.current) hideBar(); else showBar(); }}
                onTouchStart={() => { if (isInputFocused) Keyboard.dismiss(); }}
                onFullScreen={setIsFullscreen}
                onPermissionRequest={(event) => {
                    // Simple wrapper for now, or extract logic if complex
                    event.nativeEvent.grant(event.nativeEvent.resources); 
                }}
                onExternalLink={(url) => handleExternalLink(url, activeTabId, setTabs, setActiveUrl, setInputUrl)}
                onMessage={(event) => {
                    const nativeEvent = event.nativeEvent;
                    if (nativeEvent.data) {
                        try {
                            const dataString = nativeEvent.data;
                            if (typeof dataString === 'string') {
                                const parsed = JSON.parse(dataString);
                                if (parsed.type === 'CONTEXT_MENU') {
                                    setTimeout(() => {
                                        setContextMenuData(parsed.data);
                                        setContextMenuVisible(true);
                                    }, 0);
                                } else if (parsed.type === 'PRINT_HTML') {
                                    setTimeout(async () => {
                                        try {
                                            await Print.printAsync({ html: parsed.html });
                                        } catch {
                                            Alert.alert("Print Error", "Could not print content.");
                                        }
                                    }, 0);
                                }
                            }
                        } catch {}
                    }
                }}
                injectedJavaScript={INJECTED_CONTEXT_MENU_SCRIPT}
            />
          );
        })}

        {!activeUrl && (
          <View style={[styles.homeContainer, { backgroundColor: effectiveTheme.bg }]} onTouchStart={() => { if (isInputFocused) Keyboard.dismiss(); }}>
            <Animated.View style={{ transform: [{ scale: logoScale }, { translateX: logoPan.x }, { translateY: logoPan.y }], zIndex: 10, padding: 20 }} {...logoResponder.panHandlers}>
              <Text style={[styles.homeText, { color: effectiveTheme.text, fontFamily: "Nunito_800ExtraBold", fontSize: 60 * fontScale }]}>{HOME_LOGO_TEXT}</Text>
            </Animated.View>
          </View>
        )}
      </Animated.View>

      {!isFullscreen && (
        <>
          {activeView !== "none" && (
            <View style={styles.overlayBackdrop}>
                <TouchableWithoutFeedback onPress={closeOverlay}><View style={styles.backdropTouchArea} /></TouchableWithoutFeedback>
                <OverlaySheet
                    activeView={activeView}
                    overlayHeightAnim={overlayHeightAnim}
                    panHandlers={sheetPanResponder.panHandlers}
                    title={activeView === 'history' ? 'History' : activeView === 'tabs' ? 'Tabs' : 'Settings'}
                    onClose={closeOverlay}
                    theme={effectiveTheme}
                    cornerRadius={cornerRadius}
                    fontScale={fontScale}
                    accentColor={accentColor}
                    keyboardHeight={keyboardHeight}
                >
                    {activeView === 'history' && (
                        <HistoryView
                            history={history}
                            theme={effectiveTheme}
                            accentColor={accentColor}
                            cornerRadius={cornerRadius}
                            fontScale={fontScale}
                            uiPadding={uiPadding}
                            historyLoadCount={settings.historyLoadCount}
                            searchText={historySearch}
                            setSearchText={setHistorySearch}
                            onPressItem={(item) => {
                                setActiveUrl(item.url);
                                updateTab(activeTabId, { url: item.url, requestedUrl: item.url, title: item.title || getDisplayHost(item.url) });
                                closeOverlay();
                            }}
                            onDeleteItem={deleteHistoryItem}
                            onFocusSearch={() => {
                                Animated.spring(overlayHeightAnim, { toValue: SNAP_FULL, tension: 60, friction: 9, useNativeDriver: false }).start();
                                currentOverlayHeight.current = SNAP_FULL;
                            }}
                        />
                    )}
                    {activeView === 'tabs' && (
                        <TabsView
                            tabs={tabs}
                            activeTabId={activeTabId}
                            theme={effectiveTheme}
                            accentColor={accentColor}
                            cornerRadius={cornerRadius}
                            fontScale={fontScale}
                            uiPadding={uiPadding}
                            searchText={tabsSearch}
                            setSearchText={setTabsSearch}
                            onPressTab={(id, url) => {
                                setActiveTabId(id);
                                setActiveUrl(url);
                                setInputUrl(url ? getDisplayHost(url) : "");
                            }}
                            onCloseTab={(id) => {
                                Keyboard.dismiss();
                                deleteTab(id);
                            }}
                            onRenameTab={(id, title, showLogo) => {
                                setTabToRename(id);
                                setRenameText(title);
                                setRenameShowLogo(showLogo);
                                setIsRenameModalVisible(true);
                            }}
                            onNewTab={() => {
                                addNewTab();
                                closeOverlay();
                            }}
                            onFocusSearch={() => {
                                Animated.spring(overlayHeightAnim, { toValue: SNAP_FULL, tension: 60, friction: 9, useNativeDriver: false }).start();
                                currentOverlayHeight.current = SNAP_FULL;
                            }}
                            overlayHeightAnim={overlayHeightAnim}
                        />
                    )}
                    {activeView === 'settings' && (
                        <SettingsView
                            settings={settings}
                            searchText={settingsSearch}
                            setSearchText={setSettingsSearch}
                            onFocusSearch={() => {
                                Animated.spring(overlayHeightAnim, { toValue: SNAP_FULL, tension: 60, friction: 9, useNativeDriver: false }).start();
                                currentOverlayHeight.current = SNAP_FULL;
                            }}
                            onRequestReset={() => {
                                setConfirmActionType("resetSettings");
                                setIsConfirmModalVisible(true);
                            }}
                            onRequestClearHistory={(ms, label) => {
                                setConfirmHistoryPayload({ ms, label });
                                setConfirmActionType("history");
                                setIsConfirmModalVisible(true);
                            }}
                            onRequestBgRefreshConfirm={(val) => {
                                setConfirmActionType("bgRefresh");
                                setIsConfirmModalVisible(true);
                            }}
                        />
                    )}
                </OverlaySheet>
            </View>
          )}

          <Animated.View style={[styles.recallContainer, { opacity: recallOpacity, bottom: Math.max(insets.bottom + 10, 10), zIndex: 3 }]}>
            <TouchableOpacity activeOpacity={0.8} onPress={showBar} {...recallPanResponder.panHandlers} style={[styles.recallButton, { backgroundColor: effectiveTheme.glass, borderWidth: 0, borderRadius: 25, overflow: "hidden" }]}>
              <View style={{ ...StyleSheet.absoluteFillObject, backgroundColor: effectiveTheme.inputBg }} />
              <Ionicons name="chevron-up" size={24} color={effectiveTheme.text} />
            </TouchableOpacity>
          </Animated.View>

          {activeView === "none" && (
            <View style={styles.floatingLayer} pointerEvents="box-none">
              {isSubMenuVisible && (
                <TouchableWithoutFeedback onPress={() => setIsSubMenuVisible(false)}>
                  <View style={StyleSheet.absoluteFill} />
                </TouchableWithoutFeedback>
              )}

              <Animated.View style={[styles.bottomAreaContainer, { paddingBottom: containerPaddingBAnim, paddingHorizontal: containerPaddingHAnim, opacity: containerOpacity }]} pointerEvents={isBarHiddenState ? 'none' : 'box-none'}>
                <Animated.View style={{ width: "100%", alignItems: "center", transform: [{ translateY: Animated.subtract(scrollTranslateY, effectiveKeyboardHeight) }, { scale: containerScale }] }}>
                  
                  {isSubMenuVisible && (
                    <Animated.View style={{
                        position: 'absolute',
                        bottom: pillHeight + 4,
                        right: 0,
                        width: 220,
                        backgroundColor: pillBackgroundAnim,
                        borderRadius: cornerRadius,
                        overflow: 'hidden',
                        shadowColor: "#000",
                        shadowOffset: { width: 0, height: 4 },
                        shadowOpacity: 0.2,
                        shadowRadius: 8,
                        elevation: 20,
                        zIndex: 10
                    }}>
                       <TouchableOpacity style={{ flexDirection: 'row', alignItems: 'center', padding: 15, borderBottomWidth: 1, borderBottomColor: effectiveTheme.bg }} onPress={() => { handleShare(); setIsSubMenuVisible(false); }}>
                         <Ionicons name="share-social-outline" size={20} color={effectiveTheme.text} style={{ marginRight: 12 }} />
                         <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_700Bold', fontSize: 14 * fontScale }}>Share</Text>
                       </TouchableOpacity>
                       <TouchableOpacity style={{ flexDirection: 'row', alignItems: 'center', padding: 15, borderBottomWidth: 1, borderBottomColor: effectiveTheme.bg }} onPress={handleCopyLink}>
                         <Ionicons name="copy-outline" size={20} color={effectiveTheme.text} style={{ marginRight: 12 }} />
                         <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_700Bold', fontSize: 14 * fontScale }}>Copy Link</Text>
                       </TouchableOpacity>
                       <TouchableOpacity style={{ flexDirection: 'row', alignItems: 'center', padding: 15, borderBottomWidth: 1, borderBottomColor: effectiveTheme.bg }} onPress={handlePrint}>
                         <Ionicons name="print-outline" size={20} color={effectiveTheme.text} style={{ marginRight: 12 }} />
                         <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_700Bold', fontSize: 14 * fontScale }}>Print</Text>
                       </TouchableOpacity>
                       <TouchableOpacity 
                         style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 0, paddingHorizontal: 15, borderBottomWidth: 1, borderBottomColor: effectiveTheme.bg, minHeight: 44 }}
                         onPress={() => {
                            const val = !(currentTab?.desktopMode ?? desktopMode);
                            updateTab(activeTabId, { desktopMode: val, requestedUrl: currentTab?.url });
                            if (webViewRefs.current[activeTabId]) {
                                webViewRefs.current[activeTabId]?.reload();
                            }
                         }}
                       >
                         <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                            <Ionicons name="desktop-outline" size={20} color={effectiveTheme.text} style={{ marginRight: 12 }} />
                            <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_700Bold', fontSize: 14 * fontScale }}>Desktop Mode</Text>
                         </View>
                         <View pointerEvents="none">
                           <Switch
                              value={currentTab?.desktopMode ?? desktopMode}
                              trackColor={{ false: "#767577", true: accentColor }}
                              thumbColor={"#f4f3f4"}
                           />
                         </View>
                       </TouchableOpacity>
                       <TouchableOpacity 
                         style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 0, paddingHorizontal: 15, minHeight: 44 }}
                         onPress={() => {
                            const val = !(currentTab?.readerMode ?? readerModeEnabled);
                            updateTab(activeTabId, { readerMode: val, requestedUrl: currentTab?.url });
                            if (webViewRefs.current[activeTabId]) {
                                webViewRefs.current[activeTabId]?.reload();
                            }
                         }}
                       >
                         <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                            <Ionicons name="book-outline" size={20} color={effectiveTheme.text} style={{ marginRight: 12 }} />
                            <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_700Bold', fontSize: 14 * fontScale }}>Reader Mode</Text>
                         </View>
                         <View pointerEvents="none">
                           <Switch
                              value={currentTab?.readerMode ?? readerModeEnabled}
                              trackColor={{ false: "#767577", true: accentColor }}
                              thumbColor={"#f4f3f4"}
                           />
                         </View>
                       </TouchableOpacity>
                    </Animated.View>
                  )}

                  <View style={[styles.gestureArea, { height: pillHeight }]} {...panResponder.panHandlers}>
                    <Animated.View style={[styles.pillBase, { height: pillHeight, backgroundColor: pillBackgroundAnim, borderTopLeftRadius: effectivePillRadius, borderTopRightRadius: effectivePillRadius, borderBottomLeftRadius: pillCornerRadiusAnim, borderBottomRightRadius: pillCornerRadiusAnim, shadowColor: "#000", shadowOffset: { width: 0, height: 5 }, shadowOpacity: pillShadowOpacityAnim, shadowRadius: 15, elevation: pillElevationAnim }, { zIndex: 1, opacity: menuPillOpacity, transform: [{ scale: menuPillScale }] }]} pointerEvents={!isSearchActive ? "auto" : "none"}>
                      <View style={styles.barTabContent}>
                        <TouchableOpacity style={styles.menuItem} onPress={() => setActiveView("tabs")}>
                          <Ionicons name="copy-outline" size={24} color={effectiveTheme.text} />
                          <Text style={[styles.menuLabel, { color: effectiveTheme.text, fontFamily: "Nunito_700Bold", fontSize: 10 * fontScale }]}>Tabs</Text>
                        </TouchableOpacity>
                        <TouchableOpacity style={styles.menuItem} onPress={() => setActiveView("history")}>
                          <Ionicons name="time-outline" size={24} color={effectiveTheme.text} />
                          <Text style={[styles.menuLabel, { color: effectiveTheme.text, fontFamily: "Nunito_700Bold", fontSize: 10 * fontScale }]}>History</Text>
                        </TouchableOpacity>
                        <TouchableOpacity style={styles.menuItem} onPress={() => setActiveView("settings")}>
                          <Ionicons name="settings-outline" size={24} color={effectiveTheme.text} />
                          <Text style={[styles.menuLabel, { color: effectiveTheme.text, fontFamily: "Nunito_700Bold", fontSize: 10 * fontScale }]}>Settings</Text>
                        </TouchableOpacity>
                        <TouchableOpacity style={styles.menuItem} onPress={goHome}>
                          <Ionicons name="home-outline" size={24} color={effectiveTheme.text} />
                          <Text style={[styles.menuLabel, { color: effectiveTheme.text, fontFamily: "Nunito_700Bold", fontSize: 10 * fontScale }]}>Home</Text>
                        </TouchableOpacity>
                        <TouchableOpacity style={styles.menuItem} onPress={() => setIsSubMenuVisible(!isSubMenuVisible)}>
                          <Ionicons name="menu-outline" size={24} color={effectiveTheme.text} />
                          <Text style={[styles.menuLabel, { color: effectiveTheme.text, fontFamily: "Nunito_700Bold", fontSize: 10 * fontScale }]}>Menu</Text>
                        </TouchableOpacity>
                      </View>
                    </Animated.View>

                    <Animated.View style={[styles.pillBase, { height: pillHeight, backgroundColor: pillBackgroundAnim, borderTopLeftRadius: effectivePillRadius, borderTopRightRadius: effectivePillRadius, borderBottomLeftRadius: pillCornerRadiusAnim, borderBottomRightRadius: pillCornerRadiusAnim, shadowColor: "#000", shadowOffset: { width: 0, height: 5 }, shadowOpacity: pillShadowOpacityAnim, shadowRadius: 15, elevation: pillElevationAnim }, { zIndex: 2, opacity: searchPillOpacity, transform: [{ translateY: searchPillTranslateY }] }]} pointerEvents={isSearchActive ? "auto" : "none"}>
                      <View style={styles.barTabContent}>
                        <Animated.View pointerEvents="none" style={[styles.navArrowContainer, { left: 20, opacity: backArrowOpacity }]}>
                          <Ionicons name="arrow-back" size={28} color={effectiveTheme.text} />
                        </Animated.View>
                        <Animated.View pointerEvents="none" style={[styles.navArrowContainer, { right: 20, opacity: forwardArrowOpacity }]}>
                          <Ionicons name="arrow-forward" size={28} color={effectiveTheme.text} />
                        </Animated.View>

                        <Animated.View style={[styles.inputWrapper, { backgroundColor: inputBackgroundAnim, opacity: contentOpacity, borderRadius: cornerRadius * 1.5, height: pillHeight * 0.7, overflow: "hidden" }]}>
                          {progressBarMode !== "none" && isLoading && (
                            <Animated.View style={{ position: "absolute", top: 0, bottom: 0, backgroundColor: accentColor, opacity: 0.2, zIndex: 0, ...(progressBarMode === "center" ? { left: 0, right: 0, transform: [{ scaleX: progressAnim }] } : { left: 0, width: progressAnim.interpolate({ inputRange: [0, 1], outputRange: ["0%", "100%"] }) }) }} />
                          )}
                          <TextInput
                            style={[styles.urlInput, { color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold", zIndex: 1, fontSize: 16 * fontScale }]}
                            value={inputUrl}
                            onChangeText={setInputUrl}
                            onSubmitEditing={handleGoPress}
                            placeholder="Search"
                            placeholderTextColor={effectiveTheme.textSec}
                            autoCapitalize="none"
                            keyboardType="url"
                            returnKeyType="go"
                            selectTextOnFocus
                            onFocus={() => { setIsInputFocused(true); setInputUrl(activeUrl || ""); }}
                            onBlur={() => { setIsInputFocused(false); setInputUrl(getDisplayHost(activeUrl)); }}
                          />
                          <View style={[styles.actionButtons, { zIndex: 1, flexDirection: 'row' }]}>
                            <TouchableOpacity onPress={() => setForceSearchMode(!forceSearchMode)} style={{ marginRight: 8 }}>
                                <Ionicons name={SEARCH_ENGINES[searchEngineIndex].icon as any} size={22} color={forceSearchMode ? accentColor : effectiveTheme.textSec} />
                            </TouchableOpacity>
                            {isInputFocused ? (
                              <TouchableOpacity onPress={handleGoPress}><Ionicons name="search" size={22} color={accentColor} /></TouchableOpacity>
                            ) : (
                              <TouchableOpacity disabled={!activeUrl} onPress={() => webViewRefs.current[activeTabId]?.reload()} style={!activeUrl && styles.disabledBtn}>
                                <Ionicons name={isLoading ? "close" : "refresh"} size={22} color={effectiveTheme.text} />
                              </TouchableOpacity>
                            )}
                          </View>
                        </Animated.View>
                      </View>
                    </Animated.View>
                  </View>
                </Animated.View>
              </Animated.View>
            </View>
          )}
        </>
      )}

      {/* --- CONFIRM MODAL --- */}
      <Modal visible={isConfirmModalVisible} transparent animationType="fade" onRequestClose={() => setIsConfirmModalVisible(false)}>
        <View style={styles.modalOverlay}>
          <View style={[styles.modalContent, { backgroundColor: effectiveTheme.surface, borderRadius: cornerRadius }]}>
            <Text style={[styles.modalTitle, { color: effectiveTheme.text, fontFamily: "Nunito_700Bold" }]}>Are you sure?</Text>
            <Text style={{ color: effectiveTheme.textSec, fontFamily: "Nunito_600SemiBold", marginBottom: 20, fontSize: 16 }}>
              {confirmActionType === "history"
                ? `This will permanently delete history for: ${confirmHistoryPayload?.label}.`
                : confirmActionType === "bgRefresh"
                ? "Enabling background refresh will reload all open tabs immediately when the app starts. This may consume significant battery and data."
                : "This will restore all app settings to their default values. Your history and tabs will be preserved."}
            </Text>
            <View style={styles.modalButtons}>
              <TouchableOpacity onPress={() => setIsConfirmModalVisible(false)} style={[styles.modalBtn, { borderRadius: cornerRadius / 2 }]}>
                <Text style={{ color: effectiveTheme.textSec, fontFamily: "Nunito_700Bold" }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                onPress={() => {
                  if (confirmActionType === "history" && confirmHistoryPayload) {
                    deleteHistory(confirmHistoryPayload.ms);
                  } else if (confirmActionType === "resetSettings") {
                    settings.resetSettings();
                  } else if (confirmActionType === "bgRefresh") {
                    settings.setBackgroundRefresh(true);
                  }
                  setIsConfirmModalVisible(false);
                }}
                style={[styles.modalBtn, { backgroundColor: "#ff3b30", borderRadius: cornerRadius / 2 }]}
              >
                <Text style={{ color: "#fff", fontWeight: "bold", fontFamily: "Nunito_700Bold" }}>
                  {confirmActionType === "resetSettings" ? "Reset" : confirmActionType === "bgRefresh" ? "Enable" : "Delete"}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

            {/* --- RENAME TAB MODAL --- */}

            <Modal visible={isRenameModalVisible} transparent animationType="fade" onRequestClose={() => setIsRenameModalVisible(false)}>

              <View style={styles.modalOverlay}>

                <View style={[styles.modalContent, { backgroundColor: effectiveTheme.surface, borderRadius: cornerRadius }]}>

                  <Text style={[styles.modalTitle, { color: effectiveTheme.text, fontFamily: "Nunito_700Bold" }]}>Edit Tab</Text>

                  <TextInput

                    style={[styles.modalInput, { backgroundColor: effectiveTheme.inputBg, color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold", borderRadius: cornerRadius / 2 }]}

                    value={renameText}

                    onChangeText={setRenameText}

                    autoFocus

                  />

                  <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>

                    <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold" }}>Show Site Logo</Text>

                    <Switch value={renameShowLogo} onValueChange={setRenameShowLogo} trackColor={{ false: "#767577", true: accentColor }} thumbColor={"#f4f3f4"} />

                  </View>

                  <View style={styles.modalButtons}>

                    <TouchableOpacity onPress={() => setIsRenameModalVisible(false)} style={[styles.modalBtn, { borderRadius: cornerRadius / 2 }]}>

                      <Text style={{ color: effectiveTheme.textSec, fontFamily: "Nunito_700Bold" }}>Cancel</Text>

                    </TouchableOpacity>

                    <TouchableOpacity

                      onPress={() => {

                        if (tabToRename) {

                          updateTab(tabToRename, { title: renameText || "New Tab", showLogo: renameShowLogo });

                        }

                        setIsRenameModalVisible(false);

                        setTabToRename(null);

                      }}

                      style={[styles.modalBtn, { backgroundColor: accentColor, borderRadius: cornerRadius / 2 }]}

                    >

                      <Text style={{ color: "#fff", fontWeight: "bold", fontFamily: "Nunito_700Bold" }}>Save</Text>

                    </TouchableOpacity>

                  </View>

                </View>

              </View>

            </Modal>

      

            {/* --- CONTEXT MENU (Global) --- */}
      {contextMenuVisible && (
        <View style={[StyleSheet.absoluteFill, { zIndex: 99999, elevation: 99999, justifyContent: 'center', alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.4)' }]}>
          <TouchableWithoutFeedback onPress={() => setContextMenuVisible(false)}><View style={StyleSheet.absoluteFill} /></TouchableWithoutFeedback>
          <View style={{ width: '70%', maxWidth: 280, backgroundColor: effectiveTheme.surface, borderRadius: cornerRadius, shadowColor: "#000", shadowOffset: { width: 0, height: 10 }, shadowOpacity: 0.3, shadowRadius: 20, elevation: 10, overflow: 'hidden' }}>
            <View style={{ padding: 15, borderBottomWidth: 1, borderBottomColor: effectiveTheme.bg }}>
              <Text numberOfLines={2} style={{ color: effectiveTheme.text, fontFamily: 'Nunito_700Bold', fontSize: 14 * fontScale, marginBottom: 2 }}>{contextMenuData?.text || (contextMenuData?.imgUrl ? "Image" : "Link")}</Text>
              <Text numberOfLines={1} style={{ color: effectiveTheme.textSec, fontFamily: 'Nunito_600SemiBold', fontSize: 11 * fontScale }}>{contextMenuData?.url || contextMenuData?.imgUrl}</Text>
            </View>
            <View style={{ paddingVertical: 2 }}>
              {(contextMenuData?.url || contextMenuData?.imgUrl) && (
                <TouchableOpacity style={{ flexDirection: 'row', alignItems: 'center', padding: 12 }} onPress={() => { const target = contextMenuData?.url || contextMenuData?.imgUrl; if (target) addNewTab(target); setContextMenuVisible(false); }}>
                  <Ionicons name="open-outline" size={18 * fontScale} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                  <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 14 * fontScale }}>Open in New Tab</Text>
                </TouchableOpacity>
              )}
              <TouchableOpacity style={{ flexDirection: 'row', alignItems: 'center', padding: 12 }} onPress={() => { const target = contextMenuData?.url || contextMenuData?.imgUrl; if (target) { setInputUrl(target); setIsInputFocused(true); setActiveView('none'); } setContextMenuVisible(false); }}>
                 <Ionicons name="copy-outline" size={18 * fontScale} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                 <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 14 * fontScale }}>Copy to Address Bar</Text>
              </TouchableOpacity>
              <TouchableOpacity style={{ flexDirection: 'row', alignItems: 'center', padding: 12 }} onPress={async () => { const target = contextMenuData?.url || contextMenuData?.imgUrl; if (target) await Share.share({ message: target, url: target }); setContextMenuVisible(false); }}>
                 <Ionicons name="share-social-outline" size={18 * fontScale} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                 <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 14 * fontScale }}>Share</Text>
              </TouchableOpacity>
              {contextMenuData?.imgUrl && (
                <TouchableOpacity style={{ flexDirection: 'row', alignItems: 'center', padding: 12 }} onPress={handleDownloadImage}>
                  <Ionicons name="download-outline" size={18 * fontScale} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                  <Text style={{ color: effectiveTheme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 14 * fontScale }}>Save Image</Text>
                </TouchableOpacity>
              )}
            </View>
            <TouchableOpacity onPress={() => setContextMenuVisible(false)} style={{ padding: 12, alignItems: 'center', backgroundColor: effectiveTheme.card, borderTopWidth: 1, borderTopColor: effectiveTheme.bg }}>
              <Text style={{ color: effectiveTheme.textSec, fontFamily: 'Nunito_700Bold', fontSize: 13 * fontScale }}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
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
    left: "50%",
    marginLeft: -25, // Half of width (50)
    width: 50,
    zIndex: 3,
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
    width: "100%",
    alignItems: "center",
  },
  gestureArea: { width: "100%", justifyContent: "center" },
  pillBase: {
    position: "absolute",
    left: 0,
    right: 0,
    overflow: "hidden",
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
