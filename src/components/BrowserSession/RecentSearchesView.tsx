import { Ionicons } from "@expo/vector-icons";
import { Image } from "expo-image";
import React from "react";
import { ScrollView, Text, TouchableOpacity, View } from "react-native";
import { HistoryItem } from "../../types";
import { getFaviconUrl } from "../../utils";

interface RecentSearchesViewProps {
  historyItems: HistoryItem[];
  onSelect: (item: HistoryItem) => void;
  onRemove: (id: string) => void;
  onClear: () => void;
  onClose: () => void;
  theme: any;
  fontScale: number;
}

export const RecentSearchesView = ({
  historyItems,
  onSelect,
  onRemove,
  onClear,
  onClose,
  theme,
  fontScale,
}: RecentSearchesViewProps) => {
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

      {/* Floating Close Button */}
      <TouchableOpacity
        onPress={onClose}
        style={{
          position: "absolute",
          bottom: 20,
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
