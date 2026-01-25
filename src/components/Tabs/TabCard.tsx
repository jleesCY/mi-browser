import { Ionicons } from "@expo/vector-icons";
import React from "react";
import {
  Image,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { getDisplayHost, getFaviconUrl } from "../../utils";
import { TabItem } from "../../types";
import { SCREEN_WIDTH } from "../../constants";

interface TabCardProps {
  item: TabItem;
  isActive: boolean;
  onPress: () => void;
  onDelete: () => void;
  onRename: () => void;
  theme: any;
  accent: string;
  radius: number;
  fontScale: number;
  showTabLogo: boolean;
  showTabPreview: boolean;
}

const CARD_WIDTH = (SCREEN_WIDTH - 60) / 2;

const TabCard = ({
  item,
  isActive,
  onPress,
  onDelete,
  onRename,
  theme,
  accent,
  radius,
  fontScale,
  showTabLogo,
  showTabPreview,
}: TabCardProps) => {
  const showPreview = showTabPreview && item.previewImage;

  return (
    <View style={styles.container}>
      <TouchableOpacity
        activeOpacity={0.9}
        onPress={onPress}
        style={[
          styles.card,
          {
            backgroundColor: isActive ? theme.card : theme.surface,
            borderRadius: radius,
            borderColor: isActive ? accent : "transparent",
            borderWidth: 2,
            overflow: 'hidden'
          },
        ]}
      >
        {showPreview && (
            <View style={StyleSheet.absoluteFill}>
                <Image 
                    key={item.previewImage} // Force re-render on URI change
                    source={{ uri: item.previewImage }} 
                    style={{ width: '100%', height: '100%', resizeMode: 'cover', opacity: 0.6 }} 
                />
                <View style={{ ...StyleSheet.absoluteFillObject, backgroundColor: theme.isDark ? 'rgba(0,0,0,0.5)' : 'rgba(255,255,255,0.7)' }} />
            </View>
        )}

        <View style={styles.header}>
            <View
            style={[
                styles.faviconContainer,
                { backgroundColor: isActive ? accent : "#555" },
            ]}
            >
            {showTabLogo && item.url ? (
                <Image
                source={{ uri: getFaviconUrl(item.url) || "" }}
                style={styles.faviconImage}
                />
            ) : (
                <Text
                style={[
                    styles.faviconText,
                    {
                    fontFamily: "Nunito_800ExtraBold",
                    fontSize: (item.url ? 18 : 16) * fontScale,
                    },
                ]}
                >
                {item.url
                    ? item.title
                    ? item.title.charAt(0).toUpperCase()
                    : "N"
                    : "mi."}
                </Text>
            )}
            </View>
            <TouchableOpacity
                onPress={onDelete}
                style={[styles.closeBtn, { backgroundColor: theme.bg }]}
            >
                <Ionicons name="close" size={16} color={theme.text} />
            </TouchableOpacity>
        </View>

        <View style={styles.content}>
          <Text
            style={[
              styles.title,
              {
                color: theme.text,
                fontFamily: "Nunito_700Bold",
                fontSize: 14 * fontScale,
              },
            ]}
            numberOfLines={2}
          >
            {item.title || "New Tab"}
          </Text>
          <Text
            style={[
              styles.url,
              {
                color: theme.textSec,
                fontFamily: "Nunito_600SemiBold",
                fontSize: 10 * fontScale,
              },
            ]}
            numberOfLines={1}
          >
            {getDisplayHost(item.url) || "Home"}
          </Text>
        </View>
        
        <TouchableOpacity
            onPress={onRename}
            style={[styles.editBtn, { borderColor: theme.textSec }]}
        >
            <Ionicons name="pencil" size={12} color={theme.textSec} />
        </TouchableOpacity>

      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: CARD_WIDTH,
    marginVertical: 8,
    aspectRatio: 0.85,
  },
  card: {
    flex: 1,
    padding: 12,
    justifyContent: "space-between",
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  faviconContainer: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: "center",
    justifyContent: "center",
    overflow: "hidden",
  },
  faviconImage: {
    width: 36,
    height: 36,
    borderRadius: 18,
    resizeMode: "cover",
  },
  faviconText: {
    color: "#fff",
  },
  closeBtn: {
    width: 24,
    height: 24,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  content: {
    flex: 1,
    justifyContent: "center",
    marginTop: 8,
    marginBottom: 8,
  },
  title: {
    marginBottom: 4,
  },
  url: {
    opacity: 0.7,
  },
  editBtn: {
      position: 'absolute',
      bottom: 10,
      right: 10,
      width: 24,
      height: 24,
      borderRadius: 12,
      borderWidth: 1,
      alignItems: 'center',
      justifyContent: 'center',
      opacity: 0.6
  }
});

export default TabCard;
