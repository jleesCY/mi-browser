import { Ionicons } from "@expo/vector-icons";
import React, { useEffect, useRef, useState } from "react";
import {
  Animated,
  Image,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { SCREEN_WIDTH } from "../../constants";
import { flexCenter } from "../../design-system/styles";
import { animations, borderWidths, iconSizes, shadows, spacing, touchTargets, typography, withOpacity } from "../../design-system/tokens";
import { TabItem } from "../../types";
import { getDisplayHost, getFaviconUrl } from "../../utils";

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
  const [imageError, setImageError] = useState(false);
  const fadeAnim = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    setImageError(false);
  }, [item.url]);

  const handleDelete = () => {
    Animated.timing(fadeAnim, {
      toValue: 0,
      duration: animations.fast,
      useNativeDriver: true,
    }).start(() => {
      onDelete();
    });
  };

  return (
    <View style={styles.container}>
      <Animated.View
        style={{
          flex: 1,
          opacity: fadeAnim.interpolate({
            inputRange: [0, 0.3, 1],
            outputRange: [0, 1, 1]
          }),
          transform: [{ scale: fadeAnim }]
        }}
      >
        <TouchableOpacity
          activeOpacity={0.9}
          onPress={onPress}
          style={[
            styles.card,
            {
              backgroundColor: isActive ? theme.card : theme.surface,
              borderRadius: radius,
              borderColor: isActive ? accent : "transparent",
              borderWidth: borderWidths.regular,
              overflow: 'hidden'
            },
            shadows.sm,
          ]}
        >
          {showPreview && (
            <View style={StyleSheet.absoluteFill}>
              <Image
                key={item.previewImage}
                source={{ uri: item.previewImage }}
                style={{ width: '100%', height: '100%', resizeMode: 'cover', opacity: 0.6 }}
              />
              <View style={{
                ...StyleSheet.absoluteFillObject,
                backgroundColor: theme.isDark ? withOpacity('#000000', 0.5) : withOpacity('#FFFFFF', 0.7)
              }} />
            </View>
          )}

          <View style={styles.header}>
            <View
              style={[
                styles.faviconContainer,
                {
                  backgroundColor: isActive
                    ? accent
                    : withOpacity(theme.text, theme.isDark ? 0.1 : 0.05),
                  borderRadius: radius === 22 ? touchTargets.minimum / 2 : radius / 2,
                },
              ]}
            >
              {showTabLogo && item.url && !imageError ? (
                <Image
                  source={{ uri: getFaviconUrl(item.url) || "" }}
                  style={[styles.faviconImage, { borderRadius: radius === 22 ? touchTargets.minimum / 2 : radius / 2.5 }]}
                  onError={() => setImageError(true)}
                />
              ) : (
                <Ionicons
                  name="globe-outline"
                  size={iconSizes.xl}
                  color={isActive ? "#fff" : theme.text}
                />
              )}
            </View>
            <TouchableOpacity
              onPress={handleDelete}
              style={[
                styles.closeBtn,
                {
                  backgroundColor: theme.bg,
                  borderRadius: radius / 2,
                }
              ]}
            >
              <Ionicons name="close" size={iconSizes.xs} color={theme.text} />
            </TouchableOpacity>
          </View>

          <View style={styles.content}>
            <Text
              style={[
                styles.title,
                {
                  color: theme.text,
                  fontFamily: typography.families.bold,
                  fontSize: typography.sizes.sm * fontScale,
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
                  fontFamily: typography.families.semibold,
                  fontSize: typography.sizes.xs * fontScale,
                },
              ]}
              numberOfLines={1}
            >
              {getDisplayHost(item.url) || "Home"}
            </Text>
          </View>

          <TouchableOpacity
            onPress={onRename}
            style={[
              styles.editBtn,
              {
                borderColor: theme.textSec,
                borderRadius: radius / 2,
              }
            ]}
          >
            <Ionicons name="pencil" size={iconSizes.xs - 4} color={theme.textSec} />
          </TouchableOpacity>

        </TouchableOpacity>
      </Animated.View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: CARD_WIDTH,
    marginVertical: spacing.xs,
    aspectRatio: 0.85,
  },
  card: {
    flex: 1,
    padding: spacing.sm,
    justifyContent: "space-between",
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  faviconContainer: {
    width: touchTargets.minimum,
    height: touchTargets.minimum,
    ...flexCenter,
    overflow: "hidden",
  },
  faviconImage: {
    width: iconSizes.xl + 4,
    height: iconSizes.xl + 4,
    resizeMode: "cover",
  },
  faviconText: {
    color: "#fff",
  },
  closeBtn: {
    width: spacing.xl,
    height: spacing.xl,
    ...flexCenter,
  },
  content: {
    flex: 1,
    justifyContent: "center",
    marginTop: spacing.xs,
    marginBottom: spacing.xs,
  },
  title: {
    marginBottom: spacing.xxs,
  },
  url: {
    opacity: 0.7,
  },
  editBtn: {
    position: 'absolute',
    bottom: spacing.sm - 2,
    right: spacing.sm - 2,
    width: spacing.xl,
    height: spacing.xl,
    borderWidth: borderWidths.thin,
    ...flexCenter,
    opacity: 0.6
  }
});

export default TabCard;
