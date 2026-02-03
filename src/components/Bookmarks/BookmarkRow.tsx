import { Ionicons } from "@expo/vector-icons";
import React, { useEffect, useRef, useState } from 'react';
import { Animated, Image, Text, TouchableOpacity, View } from 'react-native';
import { Swipeable } from 'react-native-gesture-handler';
import { flexCenter } from '../../design-system/styles';
import { animations, iconSizes, spacing, touchTargets, typography, withOpacity } from '../../design-system/tokens';
import { BookmarkNode } from '../../types';
import { getFaviconUrl } from '../../utils';

interface BookmarkRowProps {
  item: BookmarkNode;
  theme: any;
  accent: string;
  radius: number;
  height: number;
  margin: number;
  fontScale: number;
  showIcon?: boolean;
  onPress: () => void;
  onDelete: () => void;
  onRename: () => void;
  onMove?: () => void;
}

export const BookmarkRow: React.FC<BookmarkRowProps> = ({
  item,
  theme,
  accent,
  radius,
  height,
  margin,
  fontScale,
  showIcon = true,
  onPress,
  onDelete,
  onRename,
  onMove
}) => {
  const isFolder = item.type === 'folder';
  const [imageError, setImageError] = useState(false);
  const fadeAnim = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    setImageError(false);
  }, [isFolder ? null : (item as any).url]);

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
            backgroundColor: theme.surface,
            borderRadius: radius,
            flexDirection: 'row',
            alignItems: 'center',
            paddingHorizontal: spacing.md - 1,
          }}
        >
          <View style={{
            width: touchTargets.minimum,
            height: touchTargets.minimum,
            ...flexCenter,
            backgroundColor: withOpacity(theme.text, theme.isDark ? 0.1 : 0.05),
            borderRadius: radius === 22 ? touchTargets.minimum / 2 : radius / 2,
            marginRight: spacing.md - 1
          }}>
            {isFolder ? (
              <Ionicons name="folder" size={iconSizes.xl} color={accent} />
            ) : (
              <>
                {showIcon ? (
                  <>
                    {((item as any).icon || getFaviconUrl(item.url)) && !imageError ? (
                      <Image
                        source={{ uri: (item as any).icon || getFaviconUrl(item.url) }}
                        style={{
                          width: iconSizes.xl + 4,
                          height: iconSizes.xl + 4,
                          borderRadius: radius === 22 ? (iconSizes.xl + 4) / 2 : radius / 2.5
                        }}
                        onError={() => setImageError(true)}
                      />
                    ) : (
                      <Ionicons name="globe-outline" size={iconSizes.xl} color={theme.text} />
                    )}
                  </>
                ) : (
                  <Text style={{
                    color: theme.text,
                    fontFamily: typography.families.extrabold,
                    fontSize: typography.sizes.lg * fontScale
                  }}>
                    {(item.title || "?").charAt(0).toUpperCase()}
                  </Text>
                )}
              </>
            )}
          </View>

          <View style={{ flex: 1, justifyContent: 'center' }}>
            <Text
              numberOfLines={1}
              style={{
                color: theme.text,
                fontFamily: typography.families.bold,
                fontSize: typography.sizes.base * fontScale,
                marginBottom: spacing.xxs / 2
              }}
            >
              {item.title}
            </Text>
            {!isFolder && (
              <Text
                numberOfLines={1}
                style={{
                  color: theme.textSec,
                  fontFamily: typography.families.regular,
                  fontSize: typography.sizes.xs * fontScale,
                }}
              >
                {item.url}
              </Text>
            )}
          </View>

          {isFolder && (
            <Ionicons name="chevron-forward" size={iconSizes.sm} color={theme.textSec} />
          )}
        </TouchableOpacity>
      </Swipeable>
    </Animated.View>
  );
};
