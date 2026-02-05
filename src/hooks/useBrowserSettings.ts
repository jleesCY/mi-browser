import { useEffect, useMemo, useState } from 'react';
import { COLORS, DEFAULT_MENU_BAR_ORDER, MenuItemId, SEARCH_ENGINES } from '../constants';
import { generateAdaptiveTheme, loadStorage, saveStorage } from '../utils';

export interface BrowserSettings {
  themeMode: "light" | "dark" | "adaptive";
  accentColor: string;
  searchEngineIndex: number;
  cornerRadius: number;
  uiPadding: "compact" | "normal" | "airy";
  fontScale: number;
  showStatusBar: boolean;
  pillHeight: number;
  fontWeight: "light" | "normal" | "bold";
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
  menuBarOrder: readonly MenuItemId[];
  homeClockType: "None" | "12h" | "24h";
  homeDateType: "None" | "Above" | "Below";
  homeWeatherType: "None" | "Simple" | "Detailed" | "Hourly";
  homeLogoType: "None" | "Static" | "Fidget";
  homeBackgroundImage: string | null;
  showHomeShortcuts: boolean;
  homeShortcutAction: "newTab" | "qr" | "bookmarks" | "history";
  ignoredHosts: string[];
}

export const useBrowserSettings = (isAppReady: boolean) => {
  const [themeMode, setThemeMode] = useState<"light" | "dark" | "adaptive">("dark");
  const [accentColor, setAccentColor] = useState("#007AFF");
  const [searchEngineIndex, setSearchEngineIndex] = useState(0);
  const [backgroundRefresh, setBackgroundRefresh] = useState(false);

  // Home Page Settings
  const [homeClockType, setHomeClockType] = useState<"None" | "12h" | "24h">("None");
  const [homeDateType, setHomeDateType] = useState<"None" | "Above" | "Below">("None");
  const [homeWeatherType, setHomeWeatherType] = useState<"None" | "Simple" | "Detailed" | "Hourly">("None");
  const [homeLogoType, setHomeLogoType] = useState<"None" | "Static" | "Fidget">("Fidget");
  const [homeBackgroundImage, setHomeBackgroundImage] = useState<string | null>(null);
  const [showHomeShortcuts, setShowHomeShortcuts] = useState(false);
  const [homeShortcutAction, setHomeShortcutAction] = useState<"newTab" | "qr" | "bookmarks" | "history">("qr");

  // Security
  const [ignoredHosts, setIgnoredHosts] = useState<string[]>([]);

  // Cosmetic settings
  const [cornerRadius, setCornerRadius] = useState(22);
  const [uiPadding, setUiPadding] = useState<"compact" | "normal" | "airy">("normal");
  const [fontScale, setFontScale] = useState(1);
  const [fontWeight, setFontWeight] = useState<"light" | "normal" | "bold">("normal");
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
  const [menuBarOrder, setMenuBarOrder] = useState<readonly MenuItemId[]>(DEFAULT_MENU_BAR_ORDER);

  // UI state for settings (not persisted per se, but part of the settings UI)
  const [isAccentExpanded, setIsAccentExpanded] = useState(false);
  const [isSearchEngineOpen, setIsSearchEngineOpen] = useState(false);
  const [isShortcutMenuOpen, setIsShortcutMenuOpen] = useState(false);
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
        setFontWeight(savedSettings.fontWeight ?? "normal");
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

        setHomeClockType(savedSettings.homeClockType ?? "None");
        setHomeDateType(savedSettings.homeDateType ?? "None");
        setHomeWeatherType(savedSettings.homeWeatherType ?? "None");
        setHomeLogoType(savedSettings.homeLogoType ?? "Fidget");
        setHomeBackgroundImage(savedSettings.homeBackgroundImage ?? null);
        setShowHomeShortcuts(savedSettings.showHomeShortcuts ?? false);
        setHomeShortcutAction(savedSettings.homeShortcutAction ?? "qr");
        setIgnoredHosts(savedSettings.ignoredHosts ?? []);

        if (savedSettings.startupTabMode) {
          setStartupTabMode(savedSettings.startupTabMode);
        }
        setTabViewMode(savedSettings.tabViewMode ?? "rows");
        setShowTabLogo(savedSettings.showTabLogo ?? true);
        setShowTabPreview(savedSettings.showTabPreview ?? true);
        setExpandMenus(savedSettings.expandMenus ?? false);
        setShowBookmarkIcons(savedSettings.showBookmarkIcons ?? true);

        // Load menu bar order with migration for missing items
        if (savedSettings.menuBarOrder && Array.isArray(savedSettings.menuBarOrder)) {
          const loadedOrder = savedSettings.menuBarOrder as string[];
          // Find missing items
          const missingItems = DEFAULT_MENU_BAR_ORDER.filter(item => !loadedOrder.includes(item));

          if (missingItems.length > 0) {
            // Add missing items to the end
            const migratedOrder = [...loadedOrder.filter(item => DEFAULT_MENU_BAR_ORDER.includes(item as MenuItemId)), ...missingItems] as readonly MenuItemId[];
            setMenuBarOrder(migratedOrder);
          } else if (loadedOrder.length === DEFAULT_MENU_BAR_ORDER.length) {
            setMenuBarOrder(loadedOrder as readonly MenuItemId[]);
          } else {
            // Reset to default if corrupted
            setMenuBarOrder(DEFAULT_MENU_BAR_ORDER);
          }
        }
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
        menuBarOrder,
        homeClockType,
        homeDateType,
        homeWeatherType,
        homeLogoType,
        homeBackgroundImage,
        showHomeShortcuts,
        homeShortcutAction,
        fontWeight,
        ignoredHosts
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
    historyGrouping,
    menuBarOrder,
    homeClockType,
    homeDateType,
    homeWeatherType,
    homeLogoType,
    homeBackgroundImage,
    showHomeShortcuts,
    showHomeShortcuts,
    showHomeShortcuts,
    homeShortcutAction,
    fontWeight,
    ignoredHosts
  ]);

  const effectiveTheme = useMemo(() => {
    let selectedTheme;
    if (themeMode === "light") selectedTheme = COLORS.light;
    else if (themeMode === "dark") selectedTheme = COLORS.dark;
    else selectedTheme = generateAdaptiveTheme(accentColor);

    // Dynamic Font Mapping
    let fonts = {
      regular: "Nunito_400Regular",
      semibold: "Nunito_600SemiBold",
      bold: "Nunito_700Bold",
      extrabold: "Nunito_800ExtraBold",
    };

    if (fontWeight === 'bold') {
      fonts = {
        regular: "Nunito_600SemiBold",
        semibold: "Nunito_700Bold",
        bold: "Nunito_800ExtraBold",
        extrabold: "Nunito_800ExtraBold", // Maxed out
      };
    } else if (fontWeight === 'light') {
      fonts = {
        regular: "Nunito_400Regular", // Min available
        semibold: "Nunito_400Regular",
        bold: "Nunito_600SemiBold",
        extrabold: "Nunito_700Bold",
      };
    }

    return { ...selectedTheme, fonts };
  }, [themeMode, accentColor, fontWeight]);

  const resetSettings = () => {
    setThemeMode("dark");
    setAccentColor("#007AFF");
    setSearchEngineIndex(0);
    setCornerRadius(22);
    setUiPadding("normal");
    setFontScale(1);
    setFontWeight("normal");
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
    setHomeClockType("None");
    setHomeDateType("None");
    setHomeWeatherType("None");
    setHomeLogoType("Fidget");
    setHomeBackgroundImage(null);
    setShowHomeShortcuts(false);
    setHomeShortcutAction("qr");
    setIgnoredHosts([]);

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
    fontWeight, setFontWeight,
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
    menuBarOrder, setMenuBarOrder,
    homeClockType, setHomeClockType,
    homeDateType, setHomeDateType,
    homeWeatherType, setHomeWeatherType,
    homeLogoType, setHomeLogoType,
    homeBackgroundImage, setHomeBackgroundImage,
    showHomeShortcuts, setShowHomeShortcuts,
    homeShortcutAction, setHomeShortcutAction,
    ignoredHosts, setIgnoredHosts,
    isAccentExpanded, setIsAccentExpanded,
    isSearchEngineOpen, setIsSearchEngineOpen,
    isShortcutMenuOpen, setIsShortcutMenuOpen,
    isClearHistoryOpen, setIsClearHistoryOpen,
    effectiveTheme,
    resetSettings,
    areSettingsLoaded
  };
};