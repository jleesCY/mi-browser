import { Ionicons } from "@expo/vector-icons";
import React, { useRef, useState } from 'react';
import { Animated, FlatList, Keyboard, LayoutAnimation, TextInput, TouchableOpacity, View } from 'react-native';
import { TabItem } from '../types';
import SwipeableTabRow from "./SwipeableTabRow";
import TabCard from "./TabCard";
import { SNAP_DEFAULT } from '../constants';

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
  onPressTab: (id: string, url: string | null) => void;
  onCloseTab: (id: string) => void;
  onRenameTab: (id: string, title: string) => void;
  onNewTab: () => void;
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
  }
};

const SearchHeader = React.memo(({ theme, cornerRadius, searchText, onFocusSearch, setSearchText }: any) => (
  <View
    style={{
      marginBottom: 10,
      backgroundColor: theme.card,
      borderRadius: cornerRadius,
      flexDirection: "row",
      alignItems: "center",
      paddingHorizontal: 15,
      height: 50,
      width: '100%'
    }}
  >
    <Ionicons
      name="search"
      size={20}
      color={theme.textSec}
      style={{ marginRight: 10 }}
    />
    <TextInput
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
          color={theme.textSec}
        />
      </TouchableOpacity>
    )}
  </View>
));

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
  onPressTab,
  onCloseTab,
  onRenameTab,
  onNewTab,
  onFocusSearch,
  overlayHeightAnim
}) => {
  const [showScrollTop, setShowScrollTop] = useState(false);
  const flatListRef = useRef<FlatList>(null);

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

  return (
    <View style={{ flex: 1 }}>
      <FlatList
        ref={flatListRef}
        data={filteredTabs}
        key={tabViewMode} // Force re-render when switching modes
        numColumns={tabViewMode === "cards" ? 2 : 1}
        columnWrapperStyle={tabViewMode === "cards" ? { justifyContent: 'space-between', paddingHorizontal: 0 } : undefined}
        keyExtractor={(item) => item.id}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          padding: 20,
          paddingBottom: 100,
          paddingTop: 20,
        }}
        ListHeaderComponent={
            <SearchHeader 
                theme={theme} 
                cornerRadius={cornerRadius} 
                searchText={searchText} 
                onFocusSearch={onFocusSearch} 
                setSearchText={setSearchText} 
            />
        }
        onScroll={(e) => {
            const offsetY = e.nativeEvent.contentOffset.y;
            if (offsetY > 100 && !showScrollTop) {
                setShowScrollTop(true);
            } else if (offsetY <= 100 && showScrollTop) {
                setShowScrollTop(false);
            }
        }}
        scrollEventThrottle={16}
        renderItem={({ item }) => 
          tabViewMode === "cards" ? (
            <TabCard
              item={item}
              theme={theme}
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
      <Animated.View style={{
        position: 'absolute',
        bottom: overlayHeightAnim.interpolate({
            inputRange: [0, SNAP_DEFAULT],
            outputRange: [-100, 20],
            extrapolate: 'clamp'
        }),
        right: 20,
        alignItems: 'center'
      }}>
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
             onPress={() => flatListRef.current?.scrollToOffset({ offset: 0, animated: true })}
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