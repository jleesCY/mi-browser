import { Ionicons } from "@expo/vector-icons";
import React from 'react';
import { Keyboard, LayoutAnimation, SectionList, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { HistoryItem } from '../types';
import { getSmartDate, groupHistoryByDate } from '../utils';
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
  onFocusSearch
}) => {
  const filteredHistory = history.filter(
    (item) =>
      (item.title || "")
        .toLowerCase()
        .includes(searchText.toLowerCase()) ||
      item.url.toLowerCase().includes(searchText.toLowerCase())
  );

  const historySections = groupHistoryByDate(filteredHistory);

  // Use a higher contrast theme for rows if needed, or just pass the theme
  const rowTheme = {
    ...theme,
    surface: theme.card, // Overriding surface to match card color for rows if that was the intent
    bg: theme.card,
  };

  return (
    <View style={{ flex: 1 }}>
      <View
        style={{
          marginHorizontal: 20,
          marginTop: 20,
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

      <SectionList
        sections={historySections}
        keyExtractor={(item, index) => item.id + index}
        contentContainerStyle={{ padding: 20, paddingTop: 0 }}
        keyboardShouldPersistTaps="handled"
        stickySectionHeadersEnabled={false}
        renderSectionHeader={({ section: { title } }) => (
          <Text
            style={{
              color: theme.textSec,
              fontFamily: "Nunito_700Bold",
              marginTop: 20,
              marginBottom: 10,
              fontSize: 14 * fontScale
            }}
          >
            {title}
          </Text>
        )}
        renderItem={({ item }) => (
          <SwipeableHistoryRow
            item={item}
            theme={rowTheme}
            accent={accentColor}
            radius={cornerRadius}
            height={getHistoryHeight(uiPadding, fontScale)}
            margin={getMargin(uiPadding)}
            fontScale={fontScale}
            timeString={getSmartDate(item.timestamp)}
            onPress={() => onPressItem(item)}
            onDelete={() => onDeleteItem(item.id)}
          />
        )}
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
                fontFamily: "Nunito_600SemiBold",
                marginTop: 10
              }}
            >
              No history found.
            </Text>
          </View>
        }
      />
    </View>
  );
};
