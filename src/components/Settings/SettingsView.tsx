import { Ionicons } from "@expo/vector-icons";
import React, { ReactNode } from "react";
import {
  Keyboard,
  LayoutAnimation,
  ScrollView,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import {
  ACCENTS,
  APP_VERSION,
  HISTORY_RANGES,
  SEARCH_ENGINES,
} from "../../constants";

interface SettingsViewProps {
  settings: any;
  searchText: string;
  setSearchText: (text: string) => void;
  onFocusSearch: () => void;
  onRequestReset: () => void;
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
    effectiveTheme,
  } = settings;

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
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
            paddingVertical: 12,
            paddingHorizontal: 15,
          },
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
          hasSeparator && { borderTopWidth: 1, borderColor: effectiveTheme.bg },
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
            fontFamily: "Nunito_700Bold",
            marginTop: 20,
            marginBottom: 10,
            fontSize: 14 * fontScale,
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
    <ScrollView
      style={{ flex: 1 }}
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={{ padding: 20, paddingBottom: 100 }}
    >
      <View
        style={{
          marginBottom: 20,
          backgroundColor: effectiveTheme.card,
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
      </SettingsGroup>

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

      {/* --- HISTORY --- */}
      <SettingsGroup title="History">
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

      <SettingRow label="Reset all settings" onPress={onRequestReset}>
        <View style={{ flexDirection: "row", alignItems: "center" }}>
          <Ionicons
            name="refresh-circle-outline"
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
            Reset all settings
          </Text>
        </View>
        <Ionicons
          name="chevron-forward"
          size={16}
          color={effectiveTheme.textSec}
        />
      </SettingRow>

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
        mi. browser v{APP_VERSION}
      </Text>
    </ScrollView>
  );
};
