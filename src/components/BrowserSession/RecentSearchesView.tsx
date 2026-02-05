import { Ionicons } from "@expo/vector-icons";
import { Image } from "expo-image";
import React, { useState } from "react";
import { ScrollView, Text, TouchableOpacity, View } from "react-native";
import { flexCenter } from "../../design-system/styles";
import { borderWidths, iconSizes, shadows, spacing, touchTargets, typography, withOpacity } from "../../design-system/tokens";
import { FavoriteItem } from "../../hooks/useFavorites";
import { HistoryItem } from "../../types";
import { getDisplayHost, getFaviconUrl } from "../../utils";
import { HorizontalSortableList } from "./HorizontalSortableList";

interface RecentSearchesViewProps {
  historyItems: HistoryItem[];
  favorites: FavoriteItem[];
  activeUrl: string | null;
  activeTitle: string | null;
  filterText?: string | null;
  onSelect: (item: HistoryItem | FavoriteItem) => void;
  onRemove: (id: string) => void;
  onClear: () => void;
  onClose: () => void;
  onAddFavorite: (title: string, url: string) => void;
  onRemoveFavorite: (id: string) => void;
  onRequestDeleteFavorite: (id: string) => void;
  onReorderFavorites: (from: number, to: number) => void;
  onDragStart?: () => void;
  onDragEnd?: (id: string, absoluteX: number, absoluteY: number) => void;
  theme: any;
  accentColor: string;
  fontScale: number;
}

const FavoriteIcon = ({ item, theme, onPress }: any) => {
  const [error, setError] = useState(false);
  const favicon = item.icon || getFaviconUrl(item.url);

  return (
    <TouchableOpacity
      onPress={onPress}
      // Long press is handled by the SortableList parent now
      activeOpacity={0.8}
      style={{
        width: touchTargets.minimum,
        height: touchTargets.minimum,
        borderRadius: touchTargets.minimum / 2,
        backgroundColor: withOpacity(theme.text, theme.isDark ? 0.1 : 0.05),
        ...flexCenter,
      }}
    >
      {favicon && !error ? (
        <Image
          source={{ uri: favicon }}
          style={{ width: 36, height: 36, borderRadius: 18 }}
          contentFit="cover"
          transition={200}
          onError={() => setError(true)}
        />
      ) : (
        <Ionicons name="globe-outline" size={iconSizes.lg + 4} color={theme.text} />
      )}
    </TouchableOpacity>
  );
};

export const RecentSearchesView = ({
  historyItems,
  favorites,
  activeUrl,
  activeTitle,
  filterText,
  onSelect,
  onRemove,
  onClear,
  onClose,
  onAddFavorite,
  onRemoveFavorite,
  onRequestDeleteFavorite,
  onReorderFavorites,
  onDragStart,
  onDragEnd,
  theme,
  accentColor,
  fontScale,
}: RecentSearchesViewProps) => {

  const handleAddFavorite = () => {
    if (activeUrl && activeUrl !== "about:blank") {
      onAddFavorite(activeTitle || getDisplayHost(activeUrl), activeUrl);
    }
  };

  const renderContent = () => {
    // ... (existing logic)
    // Filter items based on filterText
    const filteredItems = historyItems.filter((item) => {
      if (!filterText) return true;
      // Don't filter if the text matches the current page URL (initial focus)
      if (activeUrl && (filterText === activeUrl || filterText === getDisplayHost(activeUrl))) {
        return true;
      }
      return item.title.toLowerCase().includes(filterText.toLowerCase());
    });

    return (
      <ScrollView
        style={{ flex: 1 }}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          paddingVertical: 4,
          paddingBottom: 100,
          flexGrow: 1, // Ensure content fills height for centering empty message
        }}
      >
        {filteredItems.length === 0 ? (
          <View style={{ flex: 1, alignItems: "center", paddingTop: 50 }}>
            <Text
              style={{
                color: theme.textSec,
                fontFamily: theme.fonts.semibold,
                fontSize: typography.sizes.base * fontScale,
              }}
            >
              {historyItems.length === 0 ? "No recent history" : "No matches found"}
            </Text>
          </View>
        ) : (
          filteredItems.map((item) => {
            return (
              <TouchableOpacity
                key={item.id}
                style={{
                  flexDirection: "row",
                  alignItems: "center",
                  paddingVertical: spacing.xs - 1,
                  paddingHorizontal: spacing.lg - 2,
                }}
                onPress={() => onSelect(item)}
              >
                <Text
                  style={{
                    flex: 1,
                    color: theme.text,
                    fontFamily: theme.fonts.semibold,
                    fontSize: (typography.sizes.base - 1) * fontScale,
                  }}
                  numberOfLines={1}
                >
                  {item.title || "Untitled"}
                </Text>

                <TouchableOpacity
                  onPress={(e) => {
                    e.stopPropagation();
                    onRemove(item.id);
                  }}
                  style={{
                    padding: spacing.xs - 2,
                    marginLeft: spacing.xs,
                  }}
                >
                  <Ionicons name="close" size={iconSizes.xs} color={theme.textSec} />
                </TouchableOpacity>
              </TouchableOpacity>
            );
          })
        )}
      </ScrollView>
    );
  };

  return (
    <View
      style={{ flex: 1, backgroundColor: theme.surface, position: "relative" }}
    >
      {renderContent()}

      {/* Favorites Bar at Bottom */}
      <View style={{
        flexDirection: 'row',
        justifyContent: 'center',
        alignItems: 'center',
        paddingVertical: spacing.sm - 2,
        backgroundColor: theme.glass,
        borderTopWidth: borderWidths.thin,
        borderTopColor: theme.bg
      }}>
        <HorizontalSortableList
          data={favorites}
          keyExtractor={(item) => item.id}
          itemWidth={60}
          itemHeight={touchTargets.minimum}
          contentContainerStyle={{ justifyContent: 'center', alignItems: 'center' }}
          onReorder={onReorderFavorites}
          onDragStart={onDragStart}
          onDragEnd={onDragEnd}
          renderItem={({ item }) => (
            <View style={{ width: 60, alignItems: 'center' }}>
              <FavoriteIcon
                item={item}
                theme={theme}
                onPress={() => onSelect(item)}
              />
            </View>
          )}
        />

        {favorites.length < 5 && (
          <TouchableOpacity
            onPress={handleAddFavorite}
            style={{
              width: touchTargets.minimum,
              height: touchTargets.minimum,
              borderRadius: touchTargets.minimum / 2,
              backgroundColor: theme.card,
              ...flexCenter,
              marginHorizontal: spacing.xs,
              borderWidth: borderWidths.thin,
              borderColor: theme.bg
            }}
          >
            <Ionicons name="add" size={iconSizes.lg} color={theme.text} />
          </TouchableOpacity>
        )}
      </View>

      {/* Floating Close Button */}
      <TouchableOpacity
        onPress={onClose}
        style={{
          position: "absolute",
          bottom: 80,
          right: spacing.lg,
          width: 40,
          height: 40,
          borderRadius: 20,
          backgroundColor: theme.card,
          ...flexCenter,
          ...shadows.md,
          borderWidth: borderWidths.thin,
          borderColor: theme.bg,
        }}
      >
        <Ionicons name="chevron-down" size={iconSizes.md} color={theme.text} />
      </TouchableOpacity>
    </View>
  );
};
