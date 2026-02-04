import { Ionicons } from "@expo/vector-icons";
import * as ImagePicker from 'expo-image-picker';
import React, { ReactNode, useRef } from "react";
import {
  Alert,
  Dimensions,
  Image,
  LayoutAnimation,
  ScrollView,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";
import {
  ACCENTS,
  DEFAULT_MENU_BAR_ORDER,
  HISTORY_RANGES,
  MenuItemId,
  SEARCH_ENGINES
} from "../../constants";
import { flexRow } from "../../design-system/styles";
import { borderWidths, iconSizes, spacing, typography } from "../../design-system/tokens";
import { HorizontalSortableList } from "../BrowserSession/HorizontalSortableList";

interface SettingsViewProps {
  settings: any;
  searchText: string;
  setSearchText: (text: string) => void;
  onFocusSearch: () => void;
  onRequestReset: () => void;
  onRequestWipeData: () => void;
  onRequestClearHistory: (ms: number, label: string) => void;
  onRequestBgRefreshConfirm: (value: boolean) => void;
  onOpenHelp: () => void;
}

export const SettingsView: React.FC<SettingsViewProps> = ({
  settings,
  searchText,
  setSearchText,
  onFocusSearch,
  onRequestReset,
  onRequestWipeData,
  onRequestClearHistory,
  onRequestBgRefreshConfirm,
  onOpenHelp,
}) => {
  const {
    themeMode,
    setThemeMode,
    accentColor,
    setAccentColor,
    searchEngineIndex,
    setSearchEngineIndex,
    backgroundRefresh,
    setBackgroundRefresh,
    cornerRadius,
    setCornerRadius,
    uiPadding,
    setUiPadding,
    fontScale,
    setFontScale,
    showStatusBar,
    setShowStatusBar,
    pillHeight,
    setPillHeight,
    progressBarMode,
    setProgressBarMode,
    tabViewMode,
    setTabViewMode,
    showTabLogo,
    setShowTabLogo,
    showTabPreview,
    setShowTabPreview,
    startupTabMode,
    setStartupTabMode,
    jsEnabled,
    setJsEnabled,
    httpsOnly,
    setHttpsOnly,
    blockCookies,
    setBlockCookies,
    historyGrouping,
    setHistoryGrouping,
    recentSearchesExpanded,
    setRecentSearchesExpanded,
    showFavoritesDefault,
    setShowFavoritesDefault,
    expandMenus,
    setExpandMenus,
    showBookmarkIcons,
    setShowBookmarkIcons,
    isAccentExpanded,
    setIsAccentExpanded,
    isSearchEngineOpen,
    setIsSearchEngineOpen,
    isClearHistoryOpen,
    setIsClearHistoryOpen,
    menuBarOrder,
    setMenuBarOrder,
    effectiveTheme,
    homeClockType,
    setHomeClockType,
    homeDateType,
    setHomeDateType,
    homeWeatherType,
    setHomeWeatherType,
    homeLogoType,
    setHomeLogoType,
    homeBackgroundImage,
    setHomeBackgroundImage,
    showHomeShortcuts,
    setShowHomeShortcuts,
    homeShortcutAction,
    setHomeShortcutAction,
    isShortcutMenuOpen,
    setIsShortcutMenuOpen,
  } = settings;

  const searchInputRef = useRef<TextInput>(null);
  const scrollViewRef = useRef<ScrollView>(null);
  const [activeCategory, setActiveCategory] = React.useState<string | null>(null);

  React.useEffect(() => {
    scrollViewRef.current?.scrollTo({ y: 0, animated: false });
  }, [activeCategory, searchText]);

  const pickImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('Permission needed', 'Sorry, we need camera roll permissions to make this work!');
      return;
    }

    const { width, height } = Dimensions.get('window');

    let result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      quality: 1,
      aspect: [width, height],
    });

    if (!result.canceled) {
      setHomeBackgroundImage(result.assets[0].uri);
    }
  };

  const CategoryButton = ({
    label,
    icon,
    color,
    onPress,
    hasSeparator = false,
  }: {
    label: string;
    icon: any;
    color: string;
    onPress: () => void;
    hasSeparator?: boolean;
  }) => (
    <View>
      {hasSeparator && (
        <View
          style={{
            height: 1,
            width: "100%",
            backgroundColor: effectiveTheme.bg,
            alignSelf: "center",
          }}
        />
      )}
      <TouchableOpacity
        onPress={onPress}
        style={{
          flexDirection: "row",
          alignItems: "center",
          justifyContent: "space-between",
          padding: spacing.md,
        }}
      >
        <View style={{ flexDirection: "row", alignItems: "center" }}>
          <Ionicons
            name={icon}
            size={20}
            color={color}
            style={{ marginRight: spacing.md }}
          />
          <Text
            style={{
              color: effectiveTheme.text,
              fontFamily: typography.families.bold,
              fontSize: typography.sizes.base * fontScale,
            }}
          >
            {label}
          </Text>
        </View>
      </TouchableOpacity>
    </View>
  );

  const shouldShow = (label?: string) => {
    if (!label) return false;
    if (searchText.trim() === "") return true;
    return label.toLowerCase().includes(searchText.toLowerCase());
  };

  const SettingRow = ({
    label,
    children,
    onPress,
    hasSeparator,
    forceVisible = false,
  }: {
    label: string;
    children: ReactNode;
    onPress?: () => void;
    hasSeparator?: boolean;
    forceVisible?: boolean;
  }) => {
    if (!forceVisible && !shouldShow(label)) return null;
    const content = (
      <View
        style={[
          {
            ...flexRow,
            justifyContent: "space-between",
            paddingVertical: spacing.xs + 4,
            paddingHorizontal: spacing.md - 1,
          },
          hasSeparator && { borderTopWidth: borderWidths.thin, borderColor: effectiveTheme.bg },
        ]}
      >
        {children}
      </View>
    );
    if (onPress)
      return <TouchableOpacity onPress={onPress}>{content}</TouchableOpacity>;
    return content;
  };

  const CustomSettingRow = ({
    label,
    children,
    hasSeparator,
    forceVisible = false,
  }: {
    label: string;
    children: ReactNode;
    hasSeparator?: boolean;
    forceVisible?: boolean;
  }) => {
    if (!forceVisible && !shouldShow(label)) return null;
    return (
      <View
        style={[
          hasSeparator && { borderTopWidth: borderWidths.thin, borderColor: effectiveTheme.bg },
        ]}
      >
        {children}
      </View>
    );
  }

  const SettingsGroup = ({
    title,
    children,
  }: {
    title: string;
    children: ReactNode;
  }) => {
    const isGroupMatch = shouldShow(title) && searchText.trim() !== "";
    const childrenArray = React.Children.toArray(children);

    // Determine which children to show.
    // If group matches, we show all (forceVisible=true).
    // Otherwise, we rely on individual child matching.

    // We need to clone children to pass the props
    const visibleChildren = childrenArray.map((child: any) => {
      if (!child || !child.props) return null;
      const childMatches = shouldShow(child.props.label);
      if (isGroupMatch || childMatches) {
        return React.cloneElement(child, {
          forceVisible: isGroupMatch // Pass forceVisible if group matched
        });
      }
      return null;
    }).filter(Boolean);

    if (visibleChildren.length === 0) return null;
    return (
      <>
        <Text
          style={{
            color: effectiveTheme.textSec,
            fontFamily: typography.families.bold,
            marginTop: spacing.lg,
            marginBottom: spacing.sm - 2,
            fontSize: typography.sizes.sm * fontScale,
          }}
        >
          {title}
        </Text>
        <View
          style={{
            backgroundColor: effectiveTheme.card,
            borderRadius: cornerRadius,
            overflow: "hidden",
          }}
        >
          {visibleChildren.map((child: any, index) =>
            React.cloneElement(child, { key: index, hasSeparator: index > 0 }),
          )}
        </View>
      </>
    );
  };

  return (
    <View style={{ flex: 1 }}>
      <View
        style={{
          position: "absolute",
          top: 0,
          left: 0,
          right: 0,
          zIndex: 100,
          paddingTop: 20, // Status bar padding
        }}
      >
        <View
          style={{
            marginBottom: spacing.md,
            backgroundColor: effectiveTheme.bg,
            borderRadius: cornerRadius,
            ...flexRow,
            paddingHorizontal: spacing.md - 1,
            height: 50,
            marginHorizontal: 20,
            borderWidth: borderWidths.thin,
            borderColor: effectiveTheme.card,
          }}
        >
          <Ionicons
            name="search"
            size={iconSizes.sm}
            color={effectiveTheme.textSec}
            style={{ marginRight: spacing.sm - 2 }}
          />
          <TextInput
            ref={searchInputRef}
            style={{
              flex: 1,
              color: effectiveTheme.text,
              fontFamily: typography.families.semibold,
              fontSize: typography.sizes.base,
            }}
            placeholder="Search Settings..."
            placeholderTextColor={effectiveTheme.textSec}
            value={searchText}
            onFocus={onFocusSearch}
            onChangeText={(text) => {
              LayoutAnimation.configureNext(
                LayoutAnimation.Presets.easeInEaseOut,
              );
              setSearchText(text);
            }}
          />
          {searchText !== "" && (
            <TouchableOpacity
              onPress={() => {
                setSearchText("");
                searchInputRef.current?.focus();
              }}
            >
              <Ionicons
                name="close-circle"
                size={iconSizes.sm}
                color={effectiveTheme.textSec}
              />
            </TouchableOpacity>
          )}
        </View>
      </View>
      <ScrollView
        ref={scrollViewRef}
        style={{ flex: 1 }}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 100, paddingTop: 90 }}
      >

        {searchText === "" && activeCategory === null && (
          <View style={{ marginTop: spacing.md }}>
            {/* Group 1: Appearance */}
            <View
              style={{
                backgroundColor: effectiveTheme.card,
                borderRadius: cornerRadius,
                overflow: "hidden",
              }}
            >
              <CategoryButton
                label="Colors"
                icon="color-palette-outline"
                color={effectiveTheme.text}
                onPress={() => setActiveCategory("colors")}
              />
              <CategoryButton
                label="Interface"
                icon="resize-outline"
                color={effectiveTheme.text}
                onPress={() => setActiveCategory("interface")}
                hasSeparator
              />
              <CategoryButton
                label="Pill"
                icon="scan-outline"
                color={effectiveTheme.text}
                onPress={() => setActiveCategory("pill")}
                hasSeparator
              />
            </View>

            <View style={{ height: spacing.lg }} />

            {/* Group 2: Content */}
            <View
              style={{
                backgroundColor: effectiveTheme.card,
                borderRadius: cornerRadius,
                overflow: "hidden",
              }}
            >
              <CategoryButton
                label="Tabs"
                icon="grid-outline"
                color={effectiveTheme.text}
                onPress={() => setActiveCategory("tabs")}
              />
              <CategoryButton
                label="History"
                icon="time-outline"
                color={effectiveTheme.text}
                onPress={() => setActiveCategory("history")}
                hasSeparator
              />
              <CategoryButton
                label="Bookmarks"
                icon="bookmarks-outline"
                color={effectiveTheme.text}
                onPress={() => setActiveCategory("bookmarks")}
                hasSeparator
              />
            </View>

            <View style={{ height: spacing.lg }} />

            {/* Group 3: General */}
            <View
              style={{
                backgroundColor: effectiveTheme.card,
                borderRadius: cornerRadius,
                overflow: "hidden",
              }}
            >
              <CategoryButton
                label="Browsing"
                icon="search-outline"
                color={effectiveTheme.text}
                onPress={() => setActiveCategory("browsing")}
              />
              <CategoryButton
                label="Security"
                icon="lock-closed-outline"
                color={effectiveTheme.text}
                onPress={() => setActiveCategory("security")}
                hasSeparator
              />
              <CategoryButton
                label="Home Page"
                icon="home-outline"
                color={effectiveTheme.text}
                onPress={() => setActiveCategory("homepage")}
                hasSeparator
              />
            </View>

            <View style={{ height: spacing.lg }} />

            {/* Group 4: Reset */}
            <View
              style={{
                backgroundColor: effectiveTheme.card,
                borderRadius: cornerRadius,
                overflow: "hidden",
              }}
            >
              <CategoryButton
                label="Reset Data"
                icon="trash-outline"
                color="#ff3b30"
                onPress={() => setActiveCategory("reset")}
              />
            </View>
          </View>
        )}

        {searchText === "" && activeCategory !== null && (
          <TouchableOpacity
            onPress={() => setActiveCategory(null)}
            style={{
              flexDirection: "row",
              alignItems: "center",
              marginBottom: spacing.md,
              marginTop: spacing.sm,
            }}
          >
            <Ionicons name="chevron-back" size={24} color={accentColor} />
            <Text
              style={{
                color: accentColor,
                fontFamily: typography.families.bold,
                fontSize: 16 * fontScale,
                marginLeft: 5,
              }}
            >
              Back
            </Text>
          </TouchableOpacity>
        )}

        {(searchText !== "" || activeCategory === "colors") && (
          <>
            {/* --- COLORS --- */}
            <SettingsGroup title="Colors">
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
                      size={iconSizes.md - 2}
                      color={effectiveTheme.text}
                      style={{ marginRight: spacing.sm - 2 }}
                    />
                    <Text
                      style={{
                        color: effectiveTheme.text,
                        fontFamily: typography.families.semibold,
                        fontSize: typography.sizes.base * fontScale,
                      }}
                    >
                      Theme
                    </Text>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      flexWrap: "wrap",
                      alignItems: "center",
                      gap: 10,
                    }}
                  >
                    {["light", "dark", "adaptive"].map((m) => (
                      <TouchableOpacity
                        key={m}
                        onPress={() => setThemeMode(m as any)}
                        style={[
                          {
                            paddingVertical: 8,
                            borderRadius: cornerRadius,
                          },
                          themeMode === m && { backgroundColor: accentColor },
                          { paddingHorizontal: 20 },
                        ]}
                      >
                        <Text
                          style={[
                            {
                              fontSize: 14,
                              fontFamily: "Nunito_600SemiBold",
                            },
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
                </View>
              </SettingRow>

              <CustomSettingRow label="Accent Color">
                <View
                  style={{
                    width: "100%",
                    flexDirection: "column",
                    paddingVertical: 12,
                    paddingHorizontal: 15,
                  }}
                >
                  <View
                    style={{
                      flexDirection: "row",
                      justifyContent: "space-between",
                      alignItems: "center",
                      marginBottom: 15,
                    }}
                  >
                    <Text
                      style={{
                        color: effectiveTheme.text,
                        fontFamily: "Nunito_600SemiBold",
                        fontSize: 16 * fontScale,
                      }}
                    >
                      Accent Color
                    </Text>
                    <TouchableOpacity
                      onPress={() => {
                        LayoutAnimation.configureNext(
                          LayoutAnimation.Presets.easeInEaseOut,
                        );
                        setIsAccentExpanded(!isAccentExpanded);
                      }}
                      hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                      style={{ padding: 5 }}
                    >
                      <Text
                        style={{
                          color: accentColor,
                          fontFamily: "Nunito_700Bold",
                          fontSize: 12 * fontScale,
                        }}
                      >
                        {isAccentExpanded ? "Show Less" : "Show More"}
                      </Text>
                    </TouchableOpacity>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      flexWrap: "wrap",
                      width: "100%",
                      gap: 8,
                      justifyContent: "center",
                    }}
                  >
                    {(isAccentExpanded ? ACCENTS : ACCENTS.slice(0, 6)).map(
                      (color) => (
                        <View
                          key={color}
                          style={{
                            width: "14%",
                            aspectRatio: 1,
                          }}
                        >
                          <TouchableOpacity
                            onPress={() => setAccentColor(color)}
                            style={[
                              {
                                flex: 1,
                                backgroundColor: color,
                                borderRadius: 10,
                              },
                              accentColor === color && {
                                borderWidth: 3,
                                borderColor: effectiveTheme.text,
                                transform: [{ scale: 0.9 }],
                              },
                            ]}
                          />
                        </View>
                      ),
                    )}
                  </View>
                </View>
              </CustomSettingRow>
            </SettingsGroup>
          </>
        )}

        {(searchText !== "" || activeCategory === "interface") && (
          <>
            {/* --- INTERFACE --- */}
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
                        style={{
                          color: effectiveTheme.text,
                          fontFamily: "Nunito_600SemiBold",
                          fontSize: 16 * fontScale,
                        }}
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

              <SettingRow label="Corners">
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
                        style={{
                          color: effectiveTheme.text,
                          fontFamily: "Nunito_600SemiBold",
                          fontSize: 16 * fontScale,
                        }}
                      >
                        Corners
                      </Text>
                    </View>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      width: "100%",
                      justifyContent: "space-around",
                    }}
                  >
                    {[
                      { label: "Square", val: 0 },
                      { label: "Semi", val: 10 },
                      { label: "Round", val: 22 },
                    ].map((opt, i) => (
                      <TouchableOpacity
                        key={i}
                        onPress={() => setCornerRadius(opt.val)}
                        style={[
                          {
                            paddingHorizontal: 15,
                            paddingVertical: 8,
                            borderRadius: cornerRadius,
                          },
                          cornerRadius === opt.val && { backgroundColor: accentColor },
                        ]}
                      >
                        <Text
                          style={[
                            {
                              fontSize: 12 * fontScale,
                              fontFamily: "Nunito_700Bold",
                            },
                            cornerRadius === opt.val
                              ? { color: "#fff" }
                              : { color: effectiveTheme.text },
                          ]}
                        >
                          {opt.label}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>

              <SettingRow label="Spacing">
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
                      style={{
                        color: effectiveTheme.text,
                        fontFamily: "Nunito_600SemiBold",
                        fontSize: 16 * fontScale,
                      }}
                    >
                      Spacing
                    </Text>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      flexWrap: "wrap",
                      alignItems: "center",
                      gap: 10,
                    }}
                  >
                    {["compact", "normal", "airy"].map((mode) => (
                      <TouchableOpacity
                        key={mode}
                        onPress={() => setUiPadding(mode as any)}
                        style={[
                          {
                            paddingHorizontal: 20,
                            paddingVertical: 8,
                            borderRadius: cornerRadius,
                          },
                          uiPadding === mode && { backgroundColor: accentColor },
                        ]}
                      >
                        <Text
                          style={[
                            {
                              fontSize: 12 * fontScale,
                              fontFamily: "Nunito_700Bold",
                            },
                            uiPadding === mode
                              ? { color: "#fff" }
                              : { color: effectiveTheme.text },
                          ]}
                        >
                          {mode.charAt(0).toUpperCase() + mode.slice(1)}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>

              <SettingRow label="Status Bar">
                <View style={{ flexDirection: "row", alignItems: "center" }}>
                  <Ionicons
                    name="battery-charging-outline"
                    size={22}
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
                    Status Bar
                  </Text>
                </View>
                <Switch
                  value={showStatusBar}
                  onValueChange={setShowStatusBar}
                  trackColor={{ false: "#767577", true: accentColor }}
                  thumbColor={"#f4f3f4"}
                />
              </SettingRow>

              <SettingRow label="Expand Menus">
                <View style={{ flexDirection: "row", alignItems: "center" }}>
                  <Ionicons
                    name="expand-outline"
                    size={22}
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
                    Expand Menus
                  </Text>
                </View>
                <Switch
                  value={expandMenus}
                  onValueChange={setExpandMenus}
                  trackColor={{ false: "#767577", true: accentColor }}
                  thumbColor={"#f4f3f4"}
                />
              </SettingRow>
            </SettingsGroup>
          </>
        )}

        {(searchText !== "" || activeCategory === "pill") && (
          <>
            {/* --- PILL --- */}
            <SettingsGroup title="Pill">
              <SettingRow label="Size">
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
                        style={{
                          color: effectiveTheme.text,
                          fontFamily: "Nunito_600SemiBold",
                          fontSize: 16 * fontScale,
                        }}
                      >
                        Size
                      </Text>
                    </View>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      width: "100%",
                      justifyContent: "space-around",
                    }}
                  >
                    {[
                      { label: "Thin", val: 60 },
                      { label: "Normal", val: 70 },
                      { label: "Tall", val: 80 },
                    ].map((opt, i) => (
                      <TouchableOpacity
                        key={i}
                        onPress={() => setPillHeight(opt.val)}
                        style={[
                          {
                            paddingHorizontal: 15,
                            paddingVertical: 8,
                            borderRadius: cornerRadius,
                          },
                          pillHeight === opt.val && { backgroundColor: accentColor },
                        ]}
                      >
                        <Text
                          style={[
                            {
                              fontSize: 12 * fontScale,
                              fontFamily: "Nunito_700Bold",
                            },
                            pillHeight === opt.val
                              ? { color: "#fff" }
                              : { color: effectiveTheme.text },
                          ]}
                        >
                          {opt.label}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>


              <SettingRow label="Loading Bar">
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
                      style={{
                        color: effectiveTheme.text,
                        fontFamily: "Nunito_600SemiBold",
                        fontSize: 16 * fontScale,
                      }}
                    >
                      Loading Bar
                    </Text>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      flexWrap: "wrap",
                      alignItems: "center",
                      gap: 10,
                    }}
                  >
                    {["ltr", "center", "none"].map((mode) => (
                      <TouchableOpacity
                        key={mode}
                        onPress={() => setProgressBarMode(mode as any)}
                        style={[
                          {
                            paddingHorizontal: 15,
                            paddingVertical: 8,
                            borderRadius: cornerRadius,
                          },
                          progressBarMode === mode && {
                            backgroundColor: accentColor,
                          },
                        ]}
                      >
                        <Text
                          style={[
                            {
                              fontSize: 12 * fontScale,
                              fontFamily: "Nunito_700Bold",
                            },
                            progressBarMode === mode
                              ? { color: "#fff" }
                              : { color: effectiveTheme.text },
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


              <SettingRow label="Pin Favorites">
                <View
                  style={{
                    flexDirection: "row",
                    alignItems: "center",
                    justifyContent: "space-between",
                    width: "100%",
                  }}
                >
                  <View style={{ flexDirection: "row", alignItems: "center" }}>
                    <Ionicons
                      name="star-outline"
                      size={22}
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
                      Pin Favorites
                    </Text>
                  </View>
                  <Switch
                    value={showFavoritesDefault}
                    onValueChange={setShowFavoritesDefault}
                    trackColor={{ false: "#767577", true: accentColor }}
                    thumbColor={"#f4f3f4"}
                  />
                </View>
              </SettingRow>

              <SettingRow label="Expand Searches">
                <View
                  style={{
                    flexDirection: "row",
                    alignItems: "center",
                    justifyContent: "space-between",
                    width: "100%",
                  }}
                >
                  <View style={{ flexDirection: "row", alignItems: "center" }}>
                    <Ionicons
                      name="list-outline"
                      size={22}
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
                      Expand Searches
                    </Text>
                  </View>
                  <Switch
                    value={recentSearchesExpanded}
                    onValueChange={setRecentSearchesExpanded}
                    trackColor={{ false: "#767577", true: accentColor }}
                    thumbColor={"#f4f3f4"}
                  />
                </View>
              </SettingRow>

              <SettingRow label="Reorder Icons">
                <View style={{ flexDirection: "column", width: "100%", justifyContent: "center", paddingVertical: 5 }}>
                  <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 15 }}>
                    <Ionicons name="reorder-four-outline" size={22} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                    <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold", fontSize: 16 * fontScale }}>Reorder Icons</Text>
                  </View>
                  <View style={{ width: "100%", alignItems: "center", justifyContent: "center" }}>
                    <HorizontalSortableList
                      data={menuBarOrder.length === 5 ? [...menuBarOrder] : [...DEFAULT_MENU_BAR_ORDER]}
                      keyExtractor={(item) => item}
                      renderItem={({ item }: { item: MenuItemId }) => {
                        const menuIcons: Record<MenuItemId, string> = { tabs: "copy-outline", bookmarks: "bookmarks-outline", history: "time-outline", settings: "settings-outline", menu: "menu-outline" };
                        const menuLabels: Record<MenuItemId, string> = { tabs: "Tabs", bookmarks: "Bookmarks", history: "History", settings: "Settings", menu: "Menu" };
                        return (
                          <View style={{ width: 48, height: 60, backgroundColor: "transparent", justifyContent: "center", alignItems: "center", marginRight: 2 }}>
                            <Ionicons name={menuIcons[item] as any} size={24} color={"#fff"} />
                            <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_700Bold", fontSize: 10 * fontScale, marginTop: 4 }}>{menuLabels[item]}</Text>
                          </View>
                        );
                      }}
                      itemWidth={48 + 2}
                      itemHeight={60}
                      onReorder={(fromIndex, toIndex) => { const newOrder = [...menuBarOrder]; const [moved] = newOrder.splice(fromIndex, 1); newOrder.splice(toIndex, 0, moved); setMenuBarOrder(newOrder as readonly MenuItemId[]); }}
                    />
                  </View>
                </View>
              </SettingRow>
            </SettingsGroup>
          </>
        )}

        {(searchText !== "" || activeCategory === "homepage") && (
          <>
            {/* --- HOME PAGE --- */}
            <SettingsGroup title="Home Page">
              <SettingRow label="Clock">
                <View style={{ flexDirection: "column", width: "100%", paddingVertical: 5 }}>
                  <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 15 }}>
                    <Ionicons name="time-outline" size={22} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                    <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold", fontSize: 16 * fontScale }}>Clock</Text>
                  </View>
                  <View style={{ flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: 10 }}>
                    {["None", "12h", "24h"].map((type) => (
                      <TouchableOpacity
                        key={type}
                        onPress={() => setHomeClockType(type as any)}
                        style={[
                          { paddingHorizontal: 20, paddingVertical: 8, borderRadius: cornerRadius },
                          homeClockType === type && { backgroundColor: accentColor }
                        ]}
                      >
                        <Text style={[
                          { fontSize: 12 * fontScale, fontFamily: "Nunito_700Bold" },
                          homeClockType === type ? { color: "#fff" } : { color: effectiveTheme.text }
                        ]}>{type}</Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>

              <SettingRow label="Date">
                <View style={{ flexDirection: "column", width: "100%", paddingVertical: 5 }}>
                  <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 15 }}>
                    <Ionicons name="calendar-outline" size={22} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                    <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold", fontSize: 16 * fontScale }}>Date</Text>
                  </View>
                  <View style={{ flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: 10 }}>
                    {["None", "Above", "Below"].map((type) => (
                      <TouchableOpacity
                        key={type}
                        onPress={() => setHomeDateType(type as any)}
                        style={[
                          { paddingHorizontal: 20, paddingVertical: 8, borderRadius: cornerRadius },
                          homeDateType === type && { backgroundColor: accentColor }
                        ]}
                      >
                        <Text style={[
                          { fontSize: 12 * fontScale, fontFamily: "Nunito_700Bold" },
                          homeDateType === type ? { color: "#fff" } : { color: effectiveTheme.text }
                        ]}>{type}</Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>

              <SettingRow label="Weather">
                <View style={{ flexDirection: "column", width: "100%", paddingVertical: 5 }}>
                  <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 15 }}>
                    <Ionicons name="cloud-outline" size={22} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                    <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold", fontSize: 16 * fontScale }}>Weather</Text>
                  </View>
                  <View style={{ flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: 10 }}>
                    {["None", "Simple", "Detailed", "Hourly"].map((type) => (
                      <TouchableOpacity
                        key={type}
                        onPress={() => setHomeWeatherType(type as any)}
                        style={[
                          { paddingHorizontal: 20, paddingVertical: 8, borderRadius: cornerRadius },
                          homeWeatherType === type && { backgroundColor: accentColor }
                        ]}
                      >
                        <Text style={[
                          { fontSize: 12 * fontScale, fontFamily: "Nunito_700Bold" },
                          homeWeatherType === type ? { color: "#fff" } : { color: effectiveTheme.text }
                        ]}>{type}</Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>

              <SettingRow label="Center Logo">
                <View style={{ flexDirection: "column", width: "100%", paddingVertical: 5 }}>
                  <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 15 }}>
                    <Ionicons name="scan-outline" size={22} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                    <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold", fontSize: 16 * fontScale }}>Logo</Text>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      flexWrap: "wrap",
                      alignItems: "center",
                      width: "100%",
                      gap: 10,
                    }}
                  >
                    {["None", "Static", "Fidget"].map((type) => (
                      <TouchableOpacity
                        key={type}
                        onPress={() => setHomeLogoType(type as any)}
                        style={[
                          {
                            paddingVertical: 8,
                            borderRadius: cornerRadius,
                            paddingHorizontal: 20,
                          },
                          homeLogoType === type && { backgroundColor: accentColor },
                        ]}
                      >
                        <Text
                          style={[
                            {
                              fontSize: 12 * fontScale,
                              fontFamily: "Nunito_700Bold",
                            },
                            homeLogoType === type
                              ? { color: "#fff" }
                              : { color: effectiveTheme.text },
                          ]}
                        >
                          {type}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>

              <SettingRow label="Shortcut">
                <View style={{ flexDirection: "column", width: "100%", paddingVertical: 5 }}>
                  <View style={{ flexDirection: "row", alignItems: "center", justifyContent: 'space-between', marginBottom: showHomeShortcuts ? 15 : 0 }}>
                    <View style={{ flexDirection: "row", alignItems: "center" }}>
                      <Ionicons
                        name="apps-outline"
                        size={22}
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
                        Shortcut
                      </Text>
                    </View>
                    <Switch
                      value={showHomeShortcuts}
                      onValueChange={setShowHomeShortcuts}
                      trackColor={{ false: "#767577", true: accentColor }}
                      thumbColor={"#f4f3f4"}
                    />
                  </View>

                  {showHomeShortcuts && (
                    <CustomSettingRow label="Action">
                      <TouchableOpacity
                        onPress={() => {
                          LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
                          setIsShortcutMenuOpen(!isShortcutMenuOpen);
                        }}
                      >
                        <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingVertical: 12, paddingHorizontal: 15 }}>
                          <View style={{ flexDirection: "row", alignItems: "center" }}>
                            <Text style={{ color: effectiveTheme.textSec, marginRight: 10, fontFamily: "Nunito_600SemiBold", fontSize: 14 * fontScale }}>Action</Text>
                          </View>
                          <View style={{ flexDirection: "row", alignItems: "center" }}>
                            <Text style={{ color: effectiveTheme.text, marginRight: 5, fontFamily: "Nunito_600SemiBold", fontSize: 14 * fontScale }}>
                              {homeShortcutAction === "newTab" ? "New Tab" : homeShortcutAction === "qr" ? "Scan" : homeShortcutAction === "bookmarks" ? "Saved" : "Recent"}
                            </Text>
                            <Ionicons name={isShortcutMenuOpen ? "chevron-up" : "chevron-down"} size={16} color={effectiveTheme.textSec} />
                          </View>
                        </View>
                      </TouchableOpacity>
                      {isShortcutMenuOpen && (
                        <View style={{ borderTopWidth: 1, borderColor: effectiveTheme.bg }}>
                          {([
                            { label: "New Tab", val: "newTab", icon: "add" },
                            { label: "Scan", val: "qr", icon: "qr-code-outline" },
                            { label: "Saved", val: "bookmarks", icon: "bookmarks-outline" },
                            { label: "Recent", val: "history", icon: "time-outline" }
                          ]).map((opt) => (
                            <TouchableOpacity
                              key={opt.val}
                              style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingVertical: 12, paddingHorizontal: 15, paddingLeft: 20 }}
                              onPress={() => {
                                setHomeShortcutAction(opt.val as any);
                                LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
                                setIsShortcutMenuOpen(false);
                              }}
                            >
                              <View style={{ flexDirection: "row", alignItems: "center" }}>
                                <Ionicons name={opt.icon as any} size={20} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                                <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold", fontSize: 16 * fontScale }}>{opt.label}</Text>
                              </View>
                              {homeShortcutAction === opt.val && <Ionicons name="checkmark" size={18} color={accentColor} />}
                            </TouchableOpacity>
                          ))}
                        </View>
                      )}
                    </CustomSettingRow>
                  )}
                </View>
              </SettingRow>

              <SettingRow label="Background Image">
                <View style={{ flexDirection: "column", width: "100%", paddingVertical: 5 }}>
                  <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 15 }}>
                    <Ionicons name="image-outline" size={22} color={effectiveTheme.text} style={{ marginRight: 10 }} />
                    <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_600SemiBold", fontSize: 16 * fontScale }}>Background Image</Text>
                  </View>

                  <View style={{ width: '100%' }}>
                    {(() => {
                      const { width, height } = Dimensions.get('window');
                      const screenAspectRatio = width / height;

                      return (
                        <View style={{ alignItems: 'center', width: '100%' }}>
                          {homeBackgroundImage ? (
                            <TouchableOpacity
                              onPress={() => setHomeBackgroundImage(null)}
                              style={{
                                backgroundColor: "#ff3b30",
                                paddingVertical: 8,
                                paddingHorizontal: 16,
                                borderRadius: cornerRadius,
                                marginBottom: 15,
                              }}
                            >
                              <Text style={{ color: "white", fontFamily: "Nunito_700Bold", fontSize: 12 * fontScale }}>Remove Image</Text>
                            </TouchableOpacity>
                          ) : (
                            <TouchableOpacity
                              onPress={pickImage}
                              style={{
                                backgroundColor: effectiveTheme.card,
                                paddingVertical: 10,
                                paddingHorizontal: 16,
                                alignItems: "center",
                                borderRadius: cornerRadius,
                                borderWidth: 1,
                                borderColor: effectiveTheme.border,
                                marginBottom: 15,
                              }}
                            >
                              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                                <Ionicons name="image-outline" size={18} color={effectiveTheme.text} />
                                <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_700Bold", fontSize: 12 * fontScale }}>Select from Gallery</Text>
                              </View>
                            </TouchableOpacity>
                          )}

                          <View
                            style={{
                              width: '60%',
                              aspectRatio: screenAspectRatio,
                              backgroundColor: effectiveTheme.bg,
                              borderRadius: cornerRadius * 0.8,
                              borderWidth: 1,
                              borderColor: effectiveTheme.border,
                              overflow: 'hidden',
                              marginBottom: 10,
                              alignItems: 'center',
                              justifyContent: 'center',
                              elevation: 5,
                              shadowColor: "#000",
                              shadowOffset: { width: 0, height: 2 },
                              shadowOpacity: 0.1,
                              shadowRadius: 4,
                            }}
                          >
                            {homeBackgroundImage ? (
                              <Image
                                source={{ uri: homeBackgroundImage }}
                                style={{ width: '100%', height: '100%' }}
                                resizeMode="cover"
                              />
                            ) : (
                              <View style={{ alignItems: 'center', justifyContent: 'center' }}>
                                <Text style={{ color: effectiveTheme.textSec, opacity: 0.5, fontSize: 10 }}>Preview</Text>
                              </View>
                            )}
                          </View>
                        </View>
                      );
                    })()}
                  </View>
                </View>
              </SettingRow>
            </SettingsGroup>
          </>
        )}

        {(searchText !== "" || activeCategory === "tabs") && (
          <>
            {/* --- TABS --- */}
            <SettingsGroup title="Tabs">
              <SettingRow label="Style">
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
                      name="grid-outline"
                      size={22}
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
                      Style
                    </Text>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      flexWrap: "wrap",
                      alignItems: "center",
                      gap: 10,
                    }}
                  >
                    {["rows", "cards"].map((mode) => (
                      <TouchableOpacity
                        key={mode}
                        onPress={() => setTabViewMode(mode as any)}
                        style={[
                          {
                            paddingHorizontal: 20,
                            paddingVertical: 8,
                            borderRadius: cornerRadius,
                          },
                          tabViewMode === mode && { backgroundColor: accentColor },
                        ]}
                      >
                        <Text
                          style={[
                            {
                              fontSize: 12 * fontScale,
                              fontFamily: "Nunito_700Bold",
                            },
                            tabViewMode === mode
                              ? { color: "#fff" }
                              : { color: effectiveTheme.text },
                          ]}
                        >
                          {mode.charAt(0).toUpperCase() + mode.slice(1)}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>

              <SettingRow label="Site Logo">
                <View style={{ flexDirection: "row", alignItems: "center" }}>
                  <Ionicons
                    name="image-outline"
                    size={22}
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
                    Site Logo
                  </Text>
                </View>
                <Switch
                  value={showTabLogo}
                  onValueChange={setShowTabLogo}
                  trackColor={{ false: "#767577", true: accentColor }}
                  thumbColor={"#f4f3f4"}
                />
              </SettingRow>

              {tabViewMode === "cards" && (
                <SettingRow label="Preview Content">
                  <View style={{ flexDirection: "row", alignItems: "center" }}>
                    <Ionicons
                      name="easel-outline"
                      size={22}
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
                      Preview Content
                    </Text>
                  </View>
                  <Switch
                    value={showTabPreview}
                    onValueChange={setShowTabPreview}
                    trackColor={{ false: "#767577", true: accentColor }}
                    thumbColor={"#f4f3f4"}
                  />
                </SettingRow>
              )}

              <SettingRow label="Background Refresh">
                <View style={{ flexDirection: "column", width: "100%" }}>
                  <View
                    style={{
                      flexDirection: "row",
                      alignItems: "center",
                      justifyContent: "space-between",
                    }}
                  >
                    <View style={{ flexDirection: "row", alignItems: "center" }}>
                      <Ionicons
                        name="flash-outline"
                        size={22}
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
                        Background Refresh
                      </Text>
                    </View>
                    <Switch
                      value={backgroundRefresh}
                      onValueChange={(val) => {
                        if (val) onRequestBgRefreshConfirm(true);
                        else setBackgroundRefresh(false);
                      }}
                      trackColor={{ false: "#767577", true: accentColor }}
                      thumbColor={"#f4f3f4"}
                    />
                  </View>
                </View>
              </SettingRow>
            </SettingsGroup>
          </>
        )}

        {(searchText !== "" || activeCategory === "history") && (
          <>
            {/* --- HISTORY --- */}
            <SettingsGroup title="History">
              <SettingRow label="Group By">
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
                      name="layers-outline"
                      size={22}
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
                      Group By
                    </Text>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      flexWrap: "wrap",
                      alignItems: "center",
                      width: "100%",
                      gap: 10,
                    }}
                  >
                    {["Time", "Site"].map((mode) => (
                      <TouchableOpacity
                        key={mode}
                        onPress={() => setHistoryGrouping(mode as any)}
                        style={[
                          {
                            alignItems: "center",
                            justifyContent: "center",
                            paddingHorizontal: 20,
                            paddingVertical: 8,
                            borderRadius: cornerRadius,
                          },
                          historyGrouping === mode && { backgroundColor: accentColor },
                        ]}
                      >
                        <Text
                          style={[
                            {
                              fontSize: 12 * fontScale,
                              fontFamily: "Nunito_700Bold",
                            },
                            historyGrouping === mode
                              ? { color: "#fff" }
                              : { color: effectiveTheme.text },
                          ]}
                        >
                          {mode}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>

              <SettingRow label="Load Count">
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
                      name="list-outline"
                      size={22}
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
                      Load Count
                    </Text>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      flexWrap: "wrap",
                      alignItems: "center",
                      width: "100%",
                      gap: 10,
                    }}
                  >
                    {[10, 25, 50, 100].map((count) => (
                      <TouchableOpacity
                        key={count}
                        onPress={() => settings.setHistoryLoadCount(count)}
                        style={[
                          {
                            alignItems: "center",
                            justifyContent: "center",
                            paddingHorizontal: 20,
                            paddingVertical: 8,
                            borderRadius: cornerRadius,
                          },
                          settings.historyLoadCount === count && {
                            backgroundColor: accentColor,
                          },
                        ]}
                      >
                        <Text
                          style={[
                            {
                              fontSize: 12 * fontScale,
                              fontFamily: "Nunito_700Bold",
                            },
                            settings.historyLoadCount === count
                              ? { color: "#fff" }
                              : { color: effectiveTheme.text },
                          ]}
                        >
                          {count}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>

              <CustomSettingRow label="Clear History">
                <TouchableOpacity
                  onPress={() => {
                    LayoutAnimation.configureNext(
                      LayoutAnimation.Presets.easeInEaseOut,
                    );
                    setIsClearHistoryOpen(!isClearHistoryOpen);
                  }}
                >
                  <View
                    style={[
                      {
                        flexDirection: "row",
                        justifyContent: "space-between",
                        alignItems: "center",
                        paddingVertical: 12,
                        paddingHorizontal: 15,
                      },
                    ]}
                  >
                    <View style={{ flexDirection: "row", alignItems: "center" }}>
                      <Ionicons
                        name="trash-outline"
                        size={22}
                        color="#ff3b30"
                        style={{ marginRight: 10 }}
                      />
                      <Text
                        style={{
                          color: "#ff3b30",
                          fontFamily: "Nunito_600SemiBold",
                          fontSize: 16 * fontScale,
                        }}
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
                        style={{
                          paddingVertical: 12,
                          paddingHorizontal: 15,
                          paddingLeft: 40,
                          borderRadius: cornerRadius,
                        }}
                        onPress={() => onRequestClearHistory(range.ms, range.label)}
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
              </CustomSettingRow>
            </SettingsGroup>
          </>
        )}

        {(searchText !== "" || activeCategory === "bookmarks") && (
          <>
            {/* --- BOOKMARKS --- */}
            <SettingsGroup title="Bookmarks">
              <SettingRow label="Site Logo">
                <View style={{ flexDirection: "row", alignItems: "center" }}>
                  <Ionicons
                    name="image-outline"
                    size={22}
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
                    Site Logo
                  </Text>
                </View>
                <Switch
                  value={showBookmarkIcons}
                  onValueChange={setShowBookmarkIcons}
                  trackColor={{ false: "#767577", true: accentColor }}
                  thumbColor={"#f4f3f4"}
                />
              </SettingRow>
            </SettingsGroup>
          </>
        )}

        {(searchText !== "" || activeCategory === "browsing") && (
          <>
            {/* --- BROWSING --- */}
            <SettingsGroup title="Browsing">
              <CustomSettingRow label="Search Engine">
                <TouchableOpacity
                  onPress={() => {
                    LayoutAnimation.configureNext(
                      LayoutAnimation.Presets.easeInEaseOut,
                    );
                    setIsSearchEngineOpen(!isSearchEngineOpen);
                  }}
                >
                  <View
                    style={{
                      flexDirection: "row",
                      justifyContent: "space-between",
                      alignItems: "center",
                      paddingVertical: 12,
                      paddingHorizontal: 15,
                    }}
                  >
                    <View style={{ flexDirection: "row", alignItems: "center" }}>
                      <Ionicons
                        name="search-outline"
                        size={22}
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
                        Search Engine
                      </Text>
                    </View>
                    <View style={{ flexDirection: "row", alignItems: "center" }}>
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
                        name={isSearchEngineOpen ? "chevron-up" : "chevron-down"}
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
                        style={{
                          flexDirection: "row",
                          justifyContent: "space-between",
                          alignItems: "center",
                          paddingVertical: 12,
                          paddingHorizontal: 15,
                          paddingLeft: 40,
                          borderRadius: cornerRadius,
                        }}
                        onPress={() => {
                          setSearchEngineIndex(index);
                          LayoutAnimation.configureNext(
                            LayoutAnimation.Presets.easeInEaseOut,
                          );
                          setIsSearchEngineOpen(false);
                        }}
                      >
                        <View style={{ flexDirection: "row", alignItems: "center" }}>
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
                          <Ionicons name="checkmark" size={18} color={accentColor} />
                        )}
                      </TouchableOpacity>
                    ))}
                  </View>
                )}
              </CustomSettingRow>

              <SettingRow label="On Startup">
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
                      style={{
                        color: effectiveTheme.text,
                        fontFamily: "Nunito_600SemiBold",
                        fontSize: 16 * fontScale,
                      }}
                    >
                      On Startup
                    </Text>
                  </View>
                  <View
                    style={{
                      flexDirection: "row",
                      flexWrap: "wrap",
                      alignItems: "center",
                      width: "100%",
                      gap: 10,
                    }}
                  >
                    {["new", "last"].map((mode) => (
                      <TouchableOpacity
                        key={mode}
                        onPress={() => setStartupTabMode(mode as any)}
                        style={[
                          {
                            alignItems: "center",
                            justifyContent: "center",
                            paddingHorizontal: 20,
                            paddingVertical: 8,
                            borderRadius: cornerRadius,
                          },
                          startupTabMode === mode && { backgroundColor: accentColor },
                        ]}
                      >
                        <Text
                          style={[
                            {
                              fontSize: 12 * fontScale,
                              fontFamily: "Nunito_700Bold",
                            },
                            startupTabMode === mode
                              ? { color: "#fff" }
                              : { color: effectiveTheme.text },
                          ]}
                        >
                          {mode === "new" ? "New Tab" : "Continue Session"}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              </SettingRow>
            </SettingsGroup>
          </>
        )}

        {(searchText !== "" || activeCategory === "security") && (
          <>
            <SettingsGroup title="Security">
              <SettingRow label="Enable JavaScript">
                <View style={{ flexDirection: "row", alignItems: "center" }}>
                  <Ionicons
                    name="code-slash-outline"
                    size={22}
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

              <SettingRow label="Enable Cookies">
                <View style={{ flexDirection: "row", alignItems: "center" }}>
                  <Ionicons
                    name="cafe-outline"
                    size={22}
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
                    Enable Cookies
                  </Text>
                </View>
                <Switch
                  value={!blockCookies}
                  onValueChange={(val) => setBlockCookies(!val)}
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
                    style={{
                      color: effectiveTheme.text,
                      fontFamily: "Nunito_600SemiBold",
                      fontSize: 16 * fontScale,
                    }}
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
            </SettingsGroup>
          </>
        )}

        {(searchText !== "" || activeCategory === "reset") && (
          <View style={{ marginTop: 20 }}>
            <TouchableOpacity
              onPress={onRequestReset}
              style={{
                backgroundColor: "#ff3b30",
                paddingVertical: 15,
                paddingHorizontal: 20,
                borderRadius: cornerRadius,
                flexDirection: "row",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <Ionicons
                name="refresh-circle-outline"
                size={24}
                color="#fff"
                style={{ marginRight: 10 }}
              />
              <Text
                style={{
                  color: "#fff",
                  fontFamily: "Nunito_700Bold",
                  fontSize: 16 * fontScale,
                }}
              >
                Reset all settings
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              onPress={onRequestWipeData}
              style={{
                marginTop: 15,
                backgroundColor: "#ff3b30",
                paddingVertical: 15,
                paddingHorizontal: 20,
                borderRadius: cornerRadius,
                flexDirection: "row",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <Ionicons
                name="trash-bin-outline"
                size={24}
                color="#fff"
                style={{ marginRight: 10 }}
              />
              <Text
                style={{
                  color: "#fff",
                  fontFamily: "Nunito_700Bold",
                  fontSize: 16 * fontScale,
                }}
              >
                Wipe All Data
              </Text>
            </TouchableOpacity>
          </View>
        )}

        <TouchableOpacity
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 6,
            opacity: 0.8,
            marginTop: 30,
            marginBottom: 5
          }}
          onPress={onOpenHelp}
        >
          <Ionicons name="help-circle-outline" size={18} color={effectiveTheme.textSec} />
          <Text style={{
            color: effectiveTheme.textSec,
            fontFamily: 'Nunito_700Bold',
            fontSize: 14 * fontScale
          }}>Help</Text>
        </TouchableOpacity>

        <Text
          style={{
            textAlign: "center",
            color: effectiveTheme.textSec,
            fontFamily: "Nunito_600SemiBold",
            marginTop: 10,
            marginBottom: 40,
            fontSize: 12 * fontScale,
          }}
        >
        </Text>
      </ScrollView>
    </View >
  );
};