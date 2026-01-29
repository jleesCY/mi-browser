import { useState, useEffect, useMemo } from 'react';
import { loadStorage, saveStorage, generateAdaptiveTheme } from '../utils';
import { COLORS, SEARCH_ENGINES } from '../constants';

export interface BrowserSettings {
  themeMode: "light" | "dark" | "adaptive";
  accentColor: string;
  searchEngineIndex: number;
  cornerRadius: number;
  uiPadding: "compact" | "normal" | "airy";
  fontScale: number;
  showStatusBar: boolean;
  pillHeight: number;
  progressBarMode: "ltr" | "center" | "none";
  recallPosition: "left" | "center" | "right";
  startupTabMode: "new" | "last";
  tabViewMode: "rows" | "cards";
  showTabLogo: boolean;
  showTabPreview: boolean;
  expandMenus: boolean;
  showBookmarkIcons: boolean;
  desktopMode: boolean;
  forceSearchMode: boolean;
  readerModeEnabled: boolean;
  recentSearchesExpanded: boolean;
  showFavoritesDefault: boolean;
  jsEnabled: boolean;
  httpsOnly: boolean;
  blockCookies: boolean;
  backgroundRefresh: boolean;
  historyGrouping: "Time" | "Site";
}

export const useBrowserSettings = (isAppReady: boolean) => {
  const [themeMode, setThemeMode] = useState<"light" | "dark" | "adaptive">("dark");
  const [accentColor, setAccentColor] = useState("#007AFF");
  const [searchEngineIndex, setSearchEngineIndex] = useState(0);
  const [backgroundRefresh, setBackgroundRefresh] = useState(false);

  // Cosmetic settings
  const [cornerRadius, setCornerRadius] = useState(22);
  const [uiPadding, setUiPadding] = useState<"compact" | "normal" | "airy">("normal");
  const [fontScale, setFontScale] = useState(1);
  const [showStatusBar, setShowStatusBar] = useState(true);
  const [pillHeight, setPillHeight] = useState(70);
  const [progressBarMode, setProgressBarMode] = useState<"ltr" | "center" | "none">("ltr");
  const [recallPosition, setRecallPosition] = useState<"left" | "center" | "right">("center");
  const [startupTabMode, setStartupTabMode] = useState<"new" | "last">("new");
  const [tabViewMode, setTabViewMode] = useState<"rows" | "cards">("rows");
  const [showTabLogo, setShowTabLogo] = useState(true);
  const [showTabPreview, setShowTabPreview] = useState(true);
  const [expandMenus, setExpandMenus] = useState(false);
  const [showBookmarkIcons, setShowBookmarkIcons] = useState(true);

  // Functional settings
  const [desktopMode, setDesktopMode] = useState(false);
  const [forceSearchMode, setForceSearchMode] = useState(false);
  const [recentSearchesExpanded, setRecentSearchesExpanded] = useState(false);
  const [showFavoritesDefault, setShowFavoritesDefault] = useState(false);
  const [readerModeEnabled, setReaderModeEnabled] = useState(false);
  const [jsEnabled, setJsEnabled] = useState(true);
  const [httpsOnly, setHttpsOnly] = useState(false);
  const [blockCookies, setBlockCookies] = useState(false);
  const [historyLoadCount, setHistoryLoadCount] = useState(10);
  const [historyGrouping, setHistoryGrouping] = useState<"Time" | "Site">("Time");

  // UI state for settings (not persisted per se, but part of the settings UI)
  const [isAccentExpanded, setIsAccentExpanded] = useState(false);
  const [isSearchEngineOpen, setIsSearchEngineOpen] = useState(false);
  const [isClearHistoryOpen, setIsClearHistoryOpen] = useState(false);
  
  const [areSettingsLoaded, setAreSettingsLoaded] = useState(false);

  useEffect(() => {
    const loadSettings = async () => {
      const savedSettings = await loadStorage("settings");

      if (savedSettings && typeof savedSettings === 'object' && !Array.isArray(savedSettings)) {
        setThemeMode(savedSettings.themeMode ?? "dark");
        setAccentColor(savedSettings.accentColor ?? "#007AFF");
        const savedIndex = savedSettings.searchEngineIndex ?? 0;
        setSearchEngineIndex(
          savedIndex >= 0 && savedIndex < SEARCH_ENGINES.length ? savedIndex : 0
        );
        setCornerRadius(savedSettings.cornerRadius ?? 22);
        setUiPadding(savedSettings.uiPadding ?? "normal");
        setFontScale(savedSettings.fontScale ?? 1);
        setShowStatusBar(savedSettings.showStatusBar !== undefined ? savedSettings.showStatusBar : true);
        setPillHeight(savedSettings.pillHeight ?? 70);
        setProgressBarMode(savedSettings.progressBarMode ?? "ltr");
        setRecallPosition(savedSettings.recallPosition ?? "center");

        setDesktopMode(savedSettings.desktopMode ?? false);
        setForceSearchMode(savedSettings.forceSearchMode ?? false);
        setRecentSearchesExpanded(savedSettings.recentSearchesExpanded ?? false);
        setShowFavoritesDefault(savedSettings.showFavoritesDefault ?? false);
        setJsEnabled(savedSettings.jsEnabled ?? true);
        setHttpsOnly(savedSettings.httpsOnly ?? false);
        setBlockCookies(savedSettings.blockCookies ?? false);
        setHistoryLoadCount(savedSettings.historyLoadCount ?? 10);
        setHistoryGrouping(savedSettings.historyGrouping ?? "Time");
        
        setBackgroundRefresh(savedSettings.backgroundRefresh ?? false);

        if (savedSettings.startupTabMode) {
          setStartupTabMode(savedSettings.startupTabMode);
        }
        setTabViewMode(savedSettings.tabViewMode ?? "rows");
        setShowTabLogo(savedSettings.showTabLogo ?? true);
        setShowTabPreview(savedSettings.showTabPreview ?? true);
        setExpandMenus(savedSettings.expandMenus ?? false);
        setShowBookmarkIcons(savedSettings.showBookmarkIcons ?? true);
        setReaderModeEnabled(savedSettings.readerModeEnabled ?? false);
      }
      setAreSettingsLoaded(true);
    };
    loadSettings();
  }, []); // Only run on mount

  useEffect(() => {
    if (isAppReady) {
      const settingsToSave = {
        themeMode,
        accentColor,
        searchEngineIndex,
        cornerRadius,
        uiPadding,
        fontScale,
        showStatusBar,
        pillHeight,
        progressBarMode,
        recallPosition,
        startupTabMode,
        tabViewMode,
        showTabLogo,
        showTabPreview,
        expandMenus,
        showBookmarkIcons,
        desktopMode,
        forceSearchMode,
        recentSearchesExpanded,
        showFavoritesDefault,
        readerModeEnabled,
        jsEnabled,
        httpsOnly,
        blockCookies,
        historyLoadCount,
        backgroundRefresh,
        historyGrouping,
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
    showStatusBar,
    pillHeight,
    progressBarMode,
    recallPosition,
    startupTabMode,
    tabViewMode,
    showTabLogo,
    showTabPreview,
    expandMenus,
    showBookmarkIcons,
    desktopMode,
    forceSearchMode,
    recentSearchesExpanded,
    showFavoritesDefault,
    readerModeEnabled,
    jsEnabled,
    httpsOnly,
    blockCookies,
    historyLoadCount,
    isAppReady,
    backgroundRefresh,
    historyGrouping
  ]);

  const effectiveTheme = useMemo(() => {
    let selectedTheme;
    if (themeMode === "light") selectedTheme = COLORS.light;
    else if (themeMode === "dark") selectedTheme = COLORS.dark;
    else selectedTheme = generateAdaptiveTheme(accentColor);

    return { ...selectedTheme };
  }, [themeMode, accentColor]);

  const resetSettings = () => {
    setThemeMode("dark");
    setAccentColor("#007AFF");
    setSearchEngineIndex(0);
    setCornerRadius(22);
    setUiPadding("normal");
    setFontScale(1);
    setPillHeight(70);
    setProgressBarMode("ltr");
    setRecallPosition("center");
    setStartupTabMode("new");
    setTabViewMode("rows");
    setShowTabLogo(true);
    setShowTabPreview(true);
    setExpandMenus(false);
    setShowBookmarkIcons(true);
    setDesktopMode(false);
    setForceSearchMode(false);
    setRecentSearchesExpanded(false);
    setShowFavoritesDefault(false);
    setReaderModeEnabled(false);
    setJsEnabled(true);
    setHttpsOnly(false);
    setBlockCookies(false);
    setHistoryLoadCount(10);
    setHistoryGrouping("Time");
    setBackgroundRefresh(false);

    saveStorage("settings", null);
  };

  return {
    themeMode, setThemeMode,
    accentColor, setAccentColor,
    searchEngineIndex, setSearchEngineIndex,
    backgroundRefresh, setBackgroundRefresh,
    cornerRadius, setCornerRadius,
    uiPadding, setUiPadding,
    fontScale, setFontScale,
    showStatusBar, setShowStatusBar,
    pillHeight, setPillHeight,
    progressBarMode, setProgressBarMode,
    recallPosition, setRecallPosition,
    startupTabMode, setStartupTabMode,
    tabViewMode, setTabViewMode,
    showTabLogo, setShowTabLogo,
    showTabPreview, setShowTabPreview,
    expandMenus, setExpandMenus,
    showBookmarkIcons, setShowBookmarkIcons,
    desktopMode, setDesktopMode,
    forceSearchMode, setForceSearchMode,
    recentSearchesExpanded, setRecentSearchesExpanded,
    showFavoritesDefault, setShowFavoritesDefault,
    readerModeEnabled, setReaderModeEnabled,
    jsEnabled, setJsEnabled,
    httpsOnly, setHttpsOnly,
    blockCookies, setBlockCookies,
    historyLoadCount, setHistoryLoadCount,
    historyGrouping, setHistoryGrouping,
    isAccentExpanded, setIsAccentExpanded,
    isSearchEngineOpen, setIsSearchEngineOpen,
    isClearHistoryOpen, setIsClearHistoryOpen,
    effectiveTheme,
    resetSettings,
    areSettingsLoaded
  };
};
