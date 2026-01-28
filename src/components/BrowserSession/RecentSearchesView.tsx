import { Ionicons } from "@expo/vector-icons";
import { Image } from "expo-image";
import React, { useState, useRef, useEffect } from "react";
import { ScrollView, Text, TouchableOpacity, View, Alert, Modal, TextInput, Keyboard } from "react-native";
import { HistoryItem } from "../../types";
import { FavoriteItem } from "../../hooks/useFavorites";
import { getFaviconUrl, getDisplayHost } from "../../utils";

interface RecentSearchesViewProps {
  historyItems: HistoryItem[];
  favorites: FavoriteItem[];
  activeUrl: string | null;
  activeTitle: string | null;
  onSelect: (item: HistoryItem | FavoriteItem) => void;
  onRemove: (id: string) => void;
  onClear: () => void;
  onClose: () => void;
  onAddFavorite: (title: string, url: string) => void;
  onRemoveFavorite: (id: string) => void;
  onRequestDeleteFavorite: (id: string) => void;
  theme: any;
  accentColor: string;
  fontScale: number;
}

const FavoriteIcon = ({ item, theme, onPress, onLongPress }: any) => {
  const [error, setError] = useState(false);
  const favicon = item.icon || getFaviconUrl(item.url);

  return (
    <TouchableOpacity
      onPress={onPress}
      onLongPress={onLongPress}
      delayLongPress={300}
      style={{
        width: 44,
        height: 44,
        borderRadius: 22,
        backgroundColor: theme.isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.05)',
        justifyContent: 'center',
        alignItems: 'center',
        marginHorizontal: 8
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
        <Ionicons name="globe-outline" size={36} color={theme.text} />
      )}
    </TouchableOpacity>
  );
};

export const RecentSearchesView = ({
  historyItems,
  favorites,
  activeUrl,
  activeTitle,
  onSelect,
  onRemove,
  onClear,
  onClose,
  onAddFavorite,
  onRemoveFavorite,
  onRequestDeleteFavorite,
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
    if (historyItems.length === 0) {
      return (
        <View style={{ flex: 1, alignItems: "center", paddingTop: 50 }}>
          <Text
            style={{
              color: theme.textSec,
              fontFamily: "Nunito_600SemiBold",
              fontSize: 16 * fontScale,
            }}
          >
            No recent history
          </Text>
        </View>
      );
    }
    return (
      <ScrollView
        style={{ flex: 1 }}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          paddingVertical: 10,
          paddingBottom: 100,
        }}
      >
        {historyItems.map((item) => {
          const favicon = getFaviconUrl(item.url);
          return (
          <TouchableOpacity
            key={item.id}
            style={{
              flexDirection: "row",
              alignItems: "center",
              paddingVertical: 12,
              paddingHorizontal: 20,
            }}
            onPress={() => onSelect(item)}
          >
            <View
              style={{
                width: 24,
                height: 24,
                marginRight: 15,
                justifyContent: "center",
                alignItems: "center",
              }}
            >
              {favicon ? (
                  <Image
                    source={{ uri: favicon }}
                    style={{ width: 24, height: 24, borderRadius: 4 }}
                    contentFit="contain"
                    transition={200}
                  />
              ) : (
                  <Ionicons name="globe-outline" size={16} color={theme.textSec} />
              )}
            </View>

            <Text
              style={{
                flex: 1,
                color: theme.text,
                fontFamily: "Nunito_600SemiBold",
                fontSize: 16 * fontScale,
              }}
              numberOfLines={1}
            >
              {item.title || "Untitled"}
            </Text>
          </TouchableOpacity>
        ); })}
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
        paddingVertical: 10,
        backgroundColor: theme.glass,
        borderTopWidth: 1,
        borderTopColor: theme.bg
      }}>
        {favorites.map(item => (
          <FavoriteIcon 
            key={item.id} 
            item={item} 
            theme={theme} 
            onPress={() => onSelect(item)}
            onLongPress={() => onRequestDeleteFavorite(item.id)}
          />
        ))}
        {favorites.length < 5 && (
          <TouchableOpacity
            onPress={handleAddFavorite}
            style={{
              width: 44,
              height: 44,
              borderRadius: 22,
              backgroundColor: theme.card,
              justifyContent: 'center',
              alignItems: 'center',
              marginHorizontal: 8,
              borderWidth: 1,
              borderColor: theme.bg
            }}
          >
            <Ionicons name="add" size={28} color={theme.text} />
          </TouchableOpacity>
        )}
      </View>

      {/* Floating Close Button */}
      <TouchableOpacity
        onPress={onClose}
        style={{
          position: "absolute",
          bottom: 80, // Moved up to clear favorites bar
          right: 20,
          width: 40,
          height: 40,
          borderRadius: 20,
          backgroundColor: theme.card,
          justifyContent: "center",
          alignItems: "center",
          shadowColor: "#000",
          shadowOffset: { width: 0, height: 2 },
          shadowOpacity: 0.2,
          shadowRadius: 4,
          elevation: 5,
          borderWidth: 1,
          borderColor: theme.bg,
        }}
      >
        <Ionicons name="chevron-down" size={24} color={theme.text} />
      </TouchableOpacity>
    </View>
  );
};
