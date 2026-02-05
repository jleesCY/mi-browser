import { Ionicons } from "@expo/vector-icons";
import React, { useEffect, useRef, useState } from "react";
import {
  Animated,
  Image,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { Swipeable } from "react-native-gesture-handler";
import { flexCenter } from "../../design-system/styles";
import { animations, borderWidths, iconSizes, spacing, touchTargets, typography, withOpacity } from "../../design-system/tokens";
import { getDisplayHost, getFaviconUrl } from "../../utils";

const SwipeableTabRow = ({
  item,
  isActive,
  onPress,
  onDelete,
  onRename,
  theme,
  accent,
  radius,
  height,
  margin,
  fontScale,
  showTabLogo
}: any) => {
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

  const renderRightActions = () => {
    return (
      <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: margin, height: height }}>
        <TouchableOpacity
          onPress={onRename}
          style={{
            backgroundColor: theme.textSec,
            justifyContent: 'center',
            alignItems: 'center',
            width: 70,
            height: '100%',
            marginRight: spacing.xxs / 2,
            borderRadius: radius,
          }}
        >
          <Ionicons name="pencil" size={iconSizes.md} color="#fff" />
        </TouchableOpacity>
        <TouchableOpacity
          onPress={handleDelete}
          style={{
            backgroundColor: '#FF3B30',
            justifyContent: 'center',
            alignItems: 'center',
            width: 70,
            height: '100%',
            borderRadius: radius,
          }}
        >
          <Ionicons name="trash" size={iconSizes.md} color="#fff" />
        </TouchableOpacity>
      </View>
    );
  };

  return (
    <Animated.View
      style={{
        opacity: fadeAnim.interpolate({
          inputRange: [0, 0.3, 1],
          outputRange: [0, 1, 1]
        }),
        transform: [{ scale: fadeAnim }]
      }}
    >
      <Swipeable renderRightActions={renderRightActions}>
        <TouchableOpacity
          activeOpacity={0.9}
          onPress={onPress}
          style={{
            height: height,
            marginBottom: margin,
            backgroundColor: isActive ? theme.card : theme.surface,
            borderRadius: radius,
            flexDirection: 'row',
            alignItems: 'center',
            paddingHorizontal: spacing.md - 1,
            borderWidth: borderWidths.regular,
            borderColor: isActive ? accent : 'transparent'
          }}
        >
          <View style={{
            width: touchTargets.minimum,
            height: touchTargets.minimum,
            ...flexCenter,
            backgroundColor: isActive ? accent : withOpacity(theme.text, theme.isDark ? 0.1 : 0.05),
            borderRadius: radius === 22 ? touchTargets.minimum / 2 : radius / 2,
            marginRight: spacing.md - 1
          }}>
            {showTabLogo && item.url && !imageError ? (
              <Image
                source={{ uri: getFaviconUrl(item.url) || '' }}
                style={{
                  width: iconSizes.xl + 4,
                  height: iconSizes.xl + 4,
                  borderRadius: radius === 22 ? (iconSizes.xl + 4) / 2 : radius / 2.5,
                  resizeMode: 'cover'
                }}
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

          <View style={{ flex: 1, justifyContent: 'center' }}>
            <Text
              style={{
                color: theme.text,
                fontFamily: theme.fonts.bold,
                fontSize: typography.sizes.base * fontScale,
                marginBottom: spacing.xxs / 2
              }}
              numberOfLines={1}
            >
              {item.title || "New Tab"}
            </Text>
            <Text
              style={{
                color: theme.textSec,
                fontFamily: theme.fonts.semibold,
                fontSize: typography.sizes.xs * fontScale,
              }}
              numberOfLines={1}
            >
              {getDisplayHost(item.url) || "Home"}
            </Text>
          </View>
        </TouchableOpacity>
      </Swipeable>
    </Animated.View>
  );
};

export default SwipeableTabRow;
