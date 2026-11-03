import { Ionicons } from "@expo/vector-icons";
import { Image } from "expo-image";
import React, { useEffect, useRef, useState } from 'react';
import { LayoutAnimation, Pressable, SectionList, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { HISTORY_RANGES } from '../../constants';
import { flexCenter, flexRow } from '../../design-system/styles';
import { borderWidths, iconSizes, shadows, spacing, touchTargets, typography } from '../../design-system/tokens';
import { HistoryItem } from '../../types';
import { getFaviconUrl, getSmartDate, groupHistoryByDate, groupHistoryBySite } from "../../utils";
import SwipeableHistoryRow from "./SwipeableHistoryRow";

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
  onRequestClearHistory: (ms: number, label: string) => void;
  historyGrouping?: "Time" | "Site";
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
      return spacing.xs;
    case "normal":
      return spacing.md - 1;
    case "airy":
      return spacing.xl + 1;
  }
};

const SearchHeader = React.memo(React.forwardRef(({ theme, cornerRadius, searchText, onFocusSearch, setSearchText, onRequestClearHistory, fontScale }: any, ref: any) => {
  const [menuVisible, setMenuVisible] = useState(false);
  const [clearHistoryVisible, setClearHistoryVisible] = useState(false);

  return (
    <View
      style={{
        marginBottom: spacing.sm - 2,
        backgroundColor: theme.bg,
        borderRadius: cornerRadius,
        ...flexRow,
        paddingHorizontal: spacing.md - 1,
        height: 50,
        zIndex: 1000,
        borderWidth: borderWidths.thin,
        borderColor: theme.card,
      }}
    >
      <Ionicons
        name="search"
        size={iconSizes.sm}
        color={theme.textSec}
        style={{ marginRight: spacing.sm - 2 }}
      />
      <TextInput
        ref={ref}
        style={{
          flex: 1,
          color: theme.text,
          fontFamily: theme.fonts.semibold,
          fontSize: typography.sizes.base * fontScale,
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
      {searchText !== "" ? (
        <TouchableOpacity
          onPress={() => {
            setSearchText("");
            ref.current?.focus();
          }}
        >
          <Ionicons
            name="close-circle"
            size={iconSizes.sm}
            color={theme.textSec}
          />
        </TouchableOpacity>
      ) : (
        <View style={{ position: 'relative' }}>
          <TouchableOpacity
            onPress={() => setMenuVisible(!menuVisible)}
            style={{ padding: spacing.xxs + 1 }}
          >
            <Ionicons
              name="ellipsis-vertical"
              size={iconSizes.sm}
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
                onPress={() => {
                  setMenuVisible(false);
                  setClearHistoryVisible(false);
                }}
              />
              <View
                style={{
                  position: 'absolute',
                  top: 30,
                  right: 0,
                  backgroundColor: theme.surface,
                  borderRadius: spacing.sm - 2,
                  padding: spacing.xxs + 1,
                  ...shadows.md,
                  zIndex: 2,
                  minWidth: 180,
                  borderWidth: borderWidths.thin,
                  borderColor: theme.bg
                }}
              >
                <TouchableOpacity
                  style={{
                    ...flexRow,
                    justifyContent: 'space-between',
                    padding: spacing.sm - 2,
                  }}
                  onPress={() => {
                    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
                    setClearHistoryVisible(!clearHistoryVisible);
                  }}
                >
                  <View style={{ ...flexRow }}>
                    <Ionicons name="trash-outline" size={iconSizes.sm - 2} color={theme.text} style={{ marginRight: spacing.sm - 2 }} />
                    <Text style={{ color: theme.text, fontFamily: theme.fonts.semibold, fontSize: typography.sizes.sm * fontScale }}>Clear History</Text>
                  </View>
                  <Ionicons name={clearHistoryVisible ? "chevron-up" : "chevron-down"} size={iconSizes.xs} color={theme.text} />
                </TouchableOpacity>

                {clearHistoryVisible && (
                  <View style={{ borderTopWidth: 1, borderTopColor: theme.bg, marginTop: 5, paddingTop: 5 }}>
                    {HISTORY_RANGES.map((range: any, index: number) => (
                      <TouchableOpacity
                        key={index}
                        style={{
                          paddingVertical: 10,
                          paddingHorizontal: 15,
                          paddingLeft: 38, // Indent to align with text above
                        }}
                        onPress={() => {
                          setMenuVisible(false);
                          setClearHistoryVisible(false);
                          onRequestClearHistory(range.ms, range.label);
                        }}
                      >
                        <Text style={{ color: theme.text, fontFamily: theme.fonts.semibold, fontSize: typography.sizes.xs * fontScale * 1.08 }}>
                          {range.label}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                )}
              </View>
            </>
          )}
        </View>
      )}
    </View>
  );
}));

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
  onRequestClearHistory,
  historyGrouping = "Time"
}) => {
  useEffect(() => {
    console.log("=== HISTORY VIEW OPENED ===");
    console.log(`Total History Items: ${history.length}`);
    console.log("Latest History Data:", JSON.stringify(history.slice(0, 5).map(h => ({
      url: h.url,
      title: h.title,
      timestamp: h.timestamp
    })), null, 2));
  }, []);

  const [showScrollTop, setShowScrollTop] = useState(false);
  const sectionListRef = useRef<SectionList>(null);
  const [collapsedSections, setCollapsedSections] = useState<Set<string>>(new Set());

  const CHUNK_SIZE = historyLoadCount || 25;
  const [visibleCount, setVisibleCount] = useState(CHUNK_SIZE);
  const searchInputRef = useRef<TextInput>(null);

  useEffect(() => {
    // Force blur when history items change (e.g. deletion)
    if (searchInputRef.current?.isFocused()) {
      searchInputRef.current.blur();
    }
  }, [history.length]);

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

  const historySections = React.useMemo(() => {
    if (historyGrouping === "Site") {
      return groupHistoryBySite(visibleHistory);
    }
    return groupHistoryByDate(visibleHistory);
  }, [visibleHistory, historyGrouping]);

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
      <View style={{
        width: '100%',
        paddingHorizontal: spacing.lg,
        paddingTop: spacing.lg,
        zIndex: 1000,
        elevation: 10,
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
      }}>
        <SearchHeader
          ref={searchInputRef}
          theme={theme}
          cornerRadius={cornerRadius}
          searchText={searchText}
          onFocusSearch={onFocusSearch}
          setSearchText={setSearchText}
          onRequestClearHistory={onRequestClearHistory}
          fontScale={fontScale}
        />
      </View>
      <SectionList
        ref={sectionListRef}
        sections={historySections}
        keyExtractor={(item) => item.id}
        initialNumToRender={10}
        maxToRenderPerBatch={5}
        windowSize={5}
        removeClippedSubviews={true}
        contentContainerStyle={{ padding: 20, paddingTop: 80, paddingBottom: 100 }} // 80 = Header Height
        keyboardShouldPersistTaps="handled"
        stickySectionHeadersEnabled={false}
        onEndReached={loadMore}
        onEndReachedThreshold={0.5}
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
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              {historyGrouping === "Site" && (
                <Image
                  source={{ uri: getFaviconUrl(title) || "" }}
                  style={{ width: 16, height: 16, borderRadius: 2, marginRight: 8 }}
                  contentFit="contain"
                />
              )}
              <Text
                style={{
                  color: theme.textSec,
                  fontFamily: theme.fonts.bold,
                  fontSize: typography.sizes.sm * fontScale
                }}
              >
                {title}
              </Text>
            </View>
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
              name="time-outline"
              size={50}
              color={theme.textSec}
            />
            <Text
              style={{
                color: theme.text,
                fontFamily: theme.fonts.semibold,
                marginTop: 10
              }}
            >
              No history found.
            </Text>
          </View>
        }
      />
      {showScrollTop && (
        <TouchableOpacity
          style={{
            position: 'absolute',
            bottom: spacing.lg,
            right: spacing.lg,
            width: touchTargets.minimum,
            height: touchTargets.minimum,
            borderRadius: touchTargets.minimum / 2,
            backgroundColor: theme.card,
            ...flexCenter,
            ...shadows.md,
            borderWidth: borderWidths.thin,
            borderColor: theme.bg
          }}
          onPress={() => sectionListRef.current?.scrollToLocation({ sectionIndex: 0, itemIndex: 0, animated: true })}
        >
          <Ionicons name="arrow-up" size={iconSizes.md} color={theme.text} />
        </TouchableOpacity>
      )}
    </View>
  );
};