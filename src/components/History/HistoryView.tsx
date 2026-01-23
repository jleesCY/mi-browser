import { Ionicons } from "@expo/vector-icons";
import React, { useRef, useState, useEffect } from 'react';
import { Animated, FlatList, Keyboard, LayoutAnimation, TextInput, TouchableOpacity, View, SectionList, Text } from 'react-native';
import { HistoryItem } from '../../types';
import SwipeableHistoryRow from "./SwipeableHistoryRow";
import { groupHistoryByDate, getSmartDate } from "../../utils";

interface HistoryViewProps {
  history: HistoryItem[];
  theme: any;
  accentColor: string;
  cornerRadius: number;
  fontScale: number;
  uiPadding: "compact" | "normal" | "airy";
  searchText: string;
  setSearchText: (text: string) => void;
  onPressItem: (item: HistoryItem) => void;
  onDeleteItem: (id: string) => void;
  onFocusSearch: () => void;
  historyLoadCount: number;
  isIncognito?: boolean;
}

const getHistoryHeight = (uiPadding: string, fontScale: number) => {
  let base = 50;
  switch (uiPadding) {
    case "compact":
      base = 48;
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
      placeholder="Search History..."
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

export const HistoryView: React.FC<HistoryViewProps> = ({
  history,
  theme,
  accentColor,
  cornerRadius,
  fontScale,
  uiPadding,
  searchText,
  setSearchText,
  onPressItem,
  onDeleteItem,
  onFocusSearch,
  historyLoadCount,
  isIncognito = false
}) => {
  const [showScrollTop, setShowScrollTop] = useState(false);
  const sectionListRef = useRef<SectionList>(null);
  const [collapsedSections, setCollapsedSections] = useState<Set<string>>(new Set());

  const CHUNK_SIZE = historyLoadCount || 25;
  const [visibleCount, setVisibleCount] = useState(CHUNK_SIZE);

  // Reset visible count when search changes or load count changes
  useEffect(() => {
    setVisibleCount(CHUNK_SIZE);
  }, [searchText, CHUNK_SIZE]);

  const toggleSection = (title: string) => {
    setCollapsedSections(prev => {
        const newSet = new Set(prev);
        if (newSet.has(title)) {
            newSet.delete(title);
        } else {
            newSet.add(title);
        }
        LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
        return newSet;
    });
  };

  const isSearching = searchText.trim().length > 0;

  const filteredHistory = history.filter(
    (item) =>
      (item.title || "")
        .toLowerCase()
        .includes(searchText.toLowerCase()) ||
      item.url.toLowerCase().includes(searchText.toLowerCase())
  );

  const visibleHistory = filteredHistory.slice(0, visibleCount);
  const historySections = groupHistoryByDate(visibleHistory);

  // Auto-load more if nothing is visible due to collapses (ignore collapse if searching)
  useEffect(() => {
    if (visibleCount < filteredHistory.length) {
      const hasAnyVisible = historySections.some(s => (isSearching || !collapsedSections.has(s.title)) && s.data.length > 0);
      if (!hasAnyVisible && visibleHistory.length > 0) {
        setVisibleCount(prev => prev + CHUNK_SIZE);
      }
    }
  }, [collapsedSections, visibleHistory, historySections, filteredHistory.length, CHUNK_SIZE, isSearching]);

  const loadMore = () => {
    if (visibleCount < filteredHistory.length) {
        setVisibleCount((prev) => prev + CHUNK_SIZE);
    }
  };

  // Use a higher contrast theme for rows if needed, or just pass the theme
  const rowTheme = {
    ...theme,
    surface: theme.card, // Overriding surface to match card color for rows if that was the intent
    bg: theme.card,
  };

  return (
    <View style={{ flex: 1 }}>
      <SectionList
        ref={sectionListRef}
        sections={historySections}
        keyExtractor={(item) => item.id}
        initialNumToRender={10}
        maxToRenderPerBatch={5}
        windowSize={5}
        removeClippedSubviews={true}
        contentContainerStyle={{ padding: 20, paddingTop: 20, paddingBottom: 100 }}
        keyboardShouldPersistTaps="handled"
        stickySectionHeadersEnabled={false}
        onEndReached={loadMore}
        onEndReachedThreshold={0.5}
        ListHeaderComponent={
            !isIncognito ? (
            <SearchHeader 
                theme={theme} 
                cornerRadius={cornerRadius} 
                searchText={searchText} 
                onFocusSearch={onFocusSearch} 
                setSearchText={setSearchText} 
            />
            ) : null
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
        renderSectionHeader={({ section: { title } }) => (
          <TouchableOpacity onPress={() => toggleSection(title)} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 10 }}>
            <Text
                style={{
                color: theme.textSec,
                fontFamily: "Nunito_700Bold",
                fontSize: 14 * fontScale
                }}
            >
                {title}
            </Text>
            <Ionicons name={(collapsedSections.has(title) && !isSearching) ? "chevron-down" : "chevron-up"} size={16} color={theme.textSec} />
          </TouchableOpacity>
        )}
        renderItem={({ item, section }) => {
          if (collapsedSections.has(section.title) && !isSearching) return null;
          return (
            <SwipeableHistoryRow
            item={item}
            theme={rowTheme}
            accent={accentColor}
            radius={cornerRadius}
            height={getHistoryHeight(uiPadding, fontScale)}
            margin={getMargin(uiPadding)}
            fontScale={fontScale}
            timeString={getSmartDate(item.timestamp)}
            onPress={onPressItem}
            onDelete={onDeleteItem}
          />
          );
        }}
        ListEmptyComponent={
          <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', marginTop: 50 }}>
            <Ionicons
              name={isIncognito ? "glasses" : "time-outline"}
              size={50}
              color={theme.textSec}
            />
            <Text
              style={{
                color: theme.text,
                fontFamily: "Nunito_600SemiBold",
                marginTop: 10
              }}
            >
              {isIncognito ? "Incognito mode does not save history." : "No history found."}
            </Text>
          </View>
        }
      />
       {showScrollTop && (
        <TouchableOpacity
          style={{
            position: 'absolute',
            bottom: 20,
            right: 20,
            width: 44,
            height: 44,
            borderRadius: 22,
            backgroundColor: theme.card,
            justifyContent: 'center',
            alignItems: 'center',
            shadowColor: "#000",
            shadowOffset: { width: 0, height: 2 },
            shadowOpacity: 0.25,
            shadowRadius: 3.84,
            elevation: 5,
            borderWidth: 1,
            borderColor: theme.bg
          }}
          onPress={() => sectionListRef.current?.scrollToLocation({ sectionIndex: 0, itemIndex: 0, animated: true })}
        >
          <Ionicons name="arrow-up" size={24} color={theme.text} />
        </TouchableOpacity>
      )}
    </View>
  );
};