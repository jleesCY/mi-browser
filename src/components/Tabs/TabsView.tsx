import { Ionicons } from "@expo/vector-icons";
import React, { useRef, useState, useEffect } from 'react';
import { Animated, Keyboard, LayoutAnimation, TextInput, TouchableOpacity, View, ScrollView, Text, Pressable } from 'react-native';
import { TabItem } from '../../types';
import SwipeableTabRow from "./SwipeableTabRow";
import TabCard from "./TabCard";
import { SNAP_DEFAULT, SCREEN_WIDTH } from '../../constants';
import { SortableGrid } from './SortableGrid';

interface TabsViewProps {
  tabs: TabItem[];
  activeTabId: string;
  theme: any;
  accentColor: string;
  cornerRadius: number;
  fontScale: number;
  uiPadding: "compact" | "normal" | "airy";
  tabViewMode: "rows" | "cards";
  showTabLogo: boolean;
  showTabPreview: boolean;
  searchText: string;
  setSearchText: (text: string) => void;
  onReorderTabs: (from: number, to: number) => void;
  onPressTab: (id: string, url: string | null) => void;
  onCloseTab: (id: string) => void;
  onRenameTab: (id: string, title: string) => void;
  onNewTab: () => void;
  onClearAllTabs: () => void;
  onFocusSearch: () => void;
  overlayHeightAnim: Animated.Value;
}

const getTabHeight = (uiPadding: string, fontScale: number) => {
  let base = 70;
  switch (uiPadding) {
    case "compact":
      base = 64; 
      break;
    case "normal":
      base = 74;
      break;
    case "airy":
      base = 88;
      break;
  }
  return base * fontScale;
};

const getMargin = (uiPadding: string) => {
  switch (uiPadding) {
    case "compact":
      return 8;
    case "normal":
      return 15;
    case "airy":
      return 25;
    default:
      return 15;
  }
};

const SearchHeader = React.memo(({ theme, cornerRadius, searchText, onFocusSearch, setSearchText, onClearAllTabs }: any) => {
  const [menuVisible, setMenuVisible] = useState(false);
  const inputRef = useRef<TextInput>(null);

  return (
    <View
      style={{
        marginBottom: 10,
        backgroundColor: theme.card,
        borderRadius: cornerRadius,
        flexDirection: "row",
        alignItems: "center",
        paddingHorizontal: 15,
        height: 50,
        width: '100%',
      }}
    >
      <Ionicons
        name="search"
        size={20}
        color={theme.textSec}
        style={{ marginRight: 10 }}
      />
      <TextInput
        ref={inputRef}
        style={{
            flex: 1,
            color: theme.text,
            fontFamily: "Nunito_600SemiBold",
            fontSize: 16,
        }}
        placeholder="Search Tabs..."
        placeholderTextColor={theme.textSec}
        value={searchText}
        onFocus={onFocusSearch}
        onChangeText={(text) => {
            LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
            setSearchText(text);
        }}
      />
      {searchText !== "" ? (
        <TouchableOpacity
          onPress={() => {
            setSearchText("");
            inputRef.current?.focus();
          }}
        >
          <Ionicons
            name="close-circle"
            size={20}
            color={theme.textSec}
          />
        </TouchableOpacity>
      ) : (
        <View style={{ position: 'relative' }}>
          <TouchableOpacity
            onPress={() => setMenuVisible(!menuVisible)}
            style={{ padding: 5 }}
          >
            <Ionicons
              name="ellipsis-vertical"
              size={20}
              color={theme.textSec}
            />
          </TouchableOpacity>
          {menuVisible && (
            <>
              <Pressable 
                style={{
                  position: 'absolute',
                  top: -1000,
                  left: -1000,
                  right: -1000,
                  bottom: -1000,
                  backgroundColor: 'transparent',
                  zIndex: 1
                }}
                onPress={() => setMenuVisible(false)}
              />
              <View
                style={{
                  position: 'absolute',
                  top: 30,
                  right: 0,
                  backgroundColor: theme.surface,
                  borderRadius: 10,
                  padding: 5,
                  shadowColor: "#000",
                  shadowOffset: { width: 0, height: 2 },
                  shadowOpacity: 0.25,
                  shadowRadius: 3.84,
                  elevation: 5,
                  zIndex: 2,
                  minWidth: 150,
                  borderWidth: 1,
                  borderColor: theme.bg
                }}
              >
                <TouchableOpacity
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    padding: 10,
                  }}
                  onPress={() => {
                    setMenuVisible(false);
                    onClearAllTabs();
                  }}
                >
                  <Ionicons name="trash-outline" size={18} color={theme.text} style={{ marginRight: 10 }} />
                  <Text style={{ color: theme.text, fontFamily: 'Nunito_600SemiBold' }}>Clear all tabs</Text>
                </TouchableOpacity>
              </View>
            </>
          )}
        </View>
      )}
    </View>
  );
});

SearchHeader.displayName = 'SearchHeader';

export const TabsView: React.FC<TabsViewProps> = ({
  tabs,
  activeTabId,
  theme,
  accentColor,
  cornerRadius,
  fontScale,
  uiPadding,
  tabViewMode,
  showTabLogo,
  showTabPreview,
  searchText,
  setSearchText,
  onReorderTabs,
  onPressTab,
  onCloseTab,
  onRenameTab,
  onNewTab,
  onClearAllTabs,
  onFocusSearch,
  overlayHeightAnim
}) => {
  const [showScrollTop, setShowScrollTop] = useState(false);
  const [isKeyboardVisible, setIsKeyboardVisible] = useState(false);
  const scrollViewRef = useRef<ScrollView>(null);

  useEffect(() => {
    const showSub = Keyboard.addListener("keyboardDidShow", () => {
        setIsKeyboardVisible(true);
    });
    const hideSub = Keyboard.addListener("keyboardDidHide", () => {
        setIsKeyboardVisible(false);
    });

    return () => {
        if (showSub) showSub.remove();
        if (hideSub) hideSub.remove();
    };
  }, []);

  const filteredTabs = tabs.filter(
    (item) =>
      (item.title || "").toLowerCase().includes(searchText.toLowerCase()) ||
      (item.url || "").toLowerCase().includes(searchText.toLowerCase())
  );

  const rowTheme = {
    ...theme,
    surface: theme.card,
    bg: theme.card,
  };

  // Dimensions
  const isCards = tabViewMode === "cards";
  const numColumns = isCards ? 2 : 1;
  
  // Card dims
  const cardWidth = (SCREEN_WIDTH - 60) / 2;
  const cardHeight = (cardWidth / 0.85) + 16; // 16 is vertical margin total

  // Row dims
  const rowHeightItem = getTabHeight(uiPadding, fontScale);
  const rowMargin = getMargin(uiPadding);
  const rowTotalHeight = rowHeightItem + rowMargin;

  const slotWidth = isCards ? (SCREEN_WIDTH - 40) / 2 : SCREEN_WIDTH - 40;
  const slotHeight = isCards ? cardHeight : rowTotalHeight;

  return (
    <View style={{ flex: 1 }}>
      <View style={{ 
          width: '100%', 
          paddingHorizontal: 20, 
          paddingTop: 20, 
          zIndex: 1000, 
      }}>
        <SearchHeader 
            theme={theme} 
            cornerRadius={cornerRadius} 
            searchText={searchText} 
            onFocusSearch={onFocusSearch} 
            setSearchText={setSearchText}
            onClearAllTabs={onClearAllTabs}
        />
      </View>
      <SortableGrid
        ref={scrollViewRef}
        data={filteredTabs}
        key={tabViewMode}
        keyExtractor={(item) => item.id}
        numColumns={numColumns}
        itemHeight={slotHeight}
        itemWidth={slotWidth}
        gridPaddingTop={10} // Reduced padding since header is outside
        gridPaddingSide={20}
        contentContainerStyle={{
          paddingHorizontal: 20, 
          paddingBottom: 140,
          paddingTop: 0, // Handled by gridPaddingTop and header being outside
        }}
        onReorder={(from, to) => {
            if (searchText === "") {
                onReorderTabs(from, to);
            }
        }}
        onScroll={(e) => {
            const offsetY = e.nativeEvent.contentOffset.y;
            if (offsetY > 100 && !showScrollTop) {
                setShowScrollTop(true);
            } else if (offsetY <= 100 && showScrollTop) {
                setShowScrollTop(false);
            }
        }}
        renderItem={({ item, isActive }) => 
          tabViewMode === "cards" ? (
            <TabCard
              item={item}
              theme={rowTheme}
              accent={accentColor}
              radius={cornerRadius}
              fontScale={fontScale}
              isActive={item.id === activeTabId}
              showTabLogo={showTabLogo}
              showTabPreview={showTabPreview}
              onPress={() => onPressTab(item.id, item.url)}
              onDelete={() => onCloseTab(item.id)}
              onRename={() => onRenameTab(item.id, item.title)}
            />
          ) : (
            <SwipeableTabRow
              item={item}
              theme={rowTheme}
              accent={accentColor}
              radius={cornerRadius}
              height={getTabHeight(uiPadding, fontScale)}
              margin={getMargin(uiPadding)}
              fontScale={fontScale}
              isActive={item.id === activeTabId}
              showTabLogo={showTabLogo}
              onPress={() => onPressTab(item.id, item.url)}
              onDelete={() => onCloseTab(item.id)}
              onRename={() => onRenameTab(item.id, item.title)}
            />
          )
        }
      />
      <Animated.View 
        pointerEvents={isKeyboardVisible ? "none" : "auto"}
        style={{
            position: 'absolute',
            bottom: overlayHeightAnim.interpolate({
                inputRange: [0, SNAP_DEFAULT],
                outputRange: [-100, 20],
                extrapolate: 'clamp'
            }),
            right: 20,
            alignItems: 'center',
            opacity: isKeyboardVisible ? 0 : 1 // Hide when keyboard is active
        }}
      >
        {showScrollTop && (
             <TouchableOpacity
             style={{
               width: 40,
               height: 40,
               borderRadius: 20,
               backgroundColor: theme.card,
               justifyContent: 'center',
               alignItems: 'center',
               shadowColor: "#000",
               shadowOffset: { width: 0, height: 2 },
               shadowOpacity: 0.25,
               shadowRadius: 3.84,
               elevation: 5,
               marginBottom: 15,
               borderWidth: 1,
               borderColor: theme.bg
             }}
             onPress={() => scrollViewRef.current?.scrollTo({ y: 0, animated: true })}
           >
             <Ionicons name="arrow-up" size={24} color={theme.text} />
           </TouchableOpacity>
        )}
        <TouchableOpacity
          style={{
            width: 56,
            height: 56,
            borderRadius: 28,
            backgroundColor: accentColor,
            justifyContent: 'center',
            alignItems: 'center',
            shadowColor: "#000",
            shadowOffset: { width: 0, height: 4 },
            shadowOpacity: 0.3,
            shadowRadius: 4.65,
            elevation: 8,
          }}
          onPress={onNewTab}
        >
          <Ionicons name="add" size={32} color="#fff" />
        </TouchableOpacity>
      </Animated.View>
    </View>
  );
};