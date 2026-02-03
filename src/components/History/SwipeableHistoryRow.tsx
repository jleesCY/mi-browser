import { Ionicons } from "@expo/vector-icons";
import React, { useEffect, useRef, useState } from "react";
import {
  Animated,
  PanResponder,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { SCREEN_WIDTH } from "../../constants";
import { flexCenter, flexRow } from "../../design-system/styles";
import { animations, iconSizes, spacing, typography, withOpacity } from "../../design-system/tokens";
import { getDisplayHost } from "../../utils";

const SwipeableHistoryRow = ({
  item,
  onPress,
  onDelete,
  theme,
  accent,
  radius,
  height,
  margin,
  fontScale,
  timeString,
}: any) => {
  const translateX = useRef(new Animated.Value(0)).current;
  const itemHeight = useRef(new Animated.Value(height)).current;
  const opacity = useRef(new Animated.Value(1)).current;
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    Animated.timing(itemHeight, {
      toValue: height,
      duration: animations.normal,
      useNativeDriver: false,
    }).start();
  }, [height, itemHeight]);

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) => {
        if (isDeleting) return false;
        return (
          gestureState.dx < -10 &&
          Math.abs(gestureState.dx) > Math.abs(gestureState.dy)
        );
      },
      onPanResponderMove: (_, gestureState) => {
        if (gestureState.dx < 0) translateX.setValue(gestureState.dx);
      },
      onPanResponderRelease: (_, gestureState) => {
        if (gestureState.dx < -SCREEN_WIDTH * 0.3) {
          setIsDeleting(true);
          Animated.parallel([
            Animated.timing(translateX, {
              toValue: -SCREEN_WIDTH,
              duration: animations.normal,
              useNativeDriver: false,
            }),
            Animated.timing(itemHeight, {
              toValue: 0,
              duration: animations.normal,
              useNativeDriver: false,
            }),
            Animated.timing(opacity, {
              toValue: 0,
              duration: animations.normal,
              useNativeDriver: false,
            }),
          ]).start(() => onDelete(item.id));
        } else {
          Animated.spring(translateX, {
            toValue: 0,
            useNativeDriver: false,
          }).start();
        }
      },
    })
  ).current;

  const iconScale = translateX.interpolate({
    inputRange: [-100, -50, 0],
    outputRange: [1.2, 0.5, 0],
    extrapolate: "clamp",
  });

  return (
    <Animated.View
      style={{
        marginBottom: margin,
        height: itemHeight,
        opacity,
        justifyContent: "center",
      }}
    >
      <View
        style={{
          position: "absolute",
          right: spacing.lg,
          top: 0,
          bottom: 0,
          justifyContent: "center",
        }}
      >
        <Animated.View style={{ transform: [{ scale: iconScale }] }}>
          <Ionicons name="trash" size={iconSizes.sm} color="#ff3b30" />
        </Animated.View>
      </View>

      <Animated.View
        style={{
          transform: [{ translateX }],
          backgroundColor: "transparent",
          height: "100%",
          justifyContent: "center",
        }}
        {...panResponder.panHandlers}
      >
        <TouchableOpacity
          activeOpacity={0.9}
          style={[
            styles.historyItem,
            {
              backgroundColor: theme.card,
              height: "100%",
              borderRadius: radius,
            },
          ]}
          onPress={() => onPress(item)}
        >
          <View style={[
            styles.historyIconBox,
            {
              width: spacing.lg + 4,
              height: spacing.lg + 4,
              borderRadius: radius / 2.5,
              backgroundColor: withOpacity(theme.text, 0.2),
              marginRight: spacing.md - 1,
            }
          ]}>
            <Ionicons name="time-outline" size={iconSizes.xs} color={theme.textSec} />
          </View>
          <View style={{ flex: 1 }}>
            <Text
              style={[
                styles.historyTitle,
                {
                  color: theme.text,
                  fontFamily: typography.families.semibold,
                  fontSize: typography.sizes.sm * fontScale,
                },
              ]}
              numberOfLines={1}
            >
              {item.title || "Untitled"}
            </Text>
            <View style={{ ...flexRow }}>
              <Text
                style={{
                  color: accent,
                  fontSize: typography.sizes.xs * fontScale,
                  marginRight: spacing.xs - 2,
                  fontFamily: typography.families.bold,
                }}
              >
                {timeString}
              </Text>
              <Text
                style={[
                  styles.historyUrl,
                  {
                    color: theme.textSec,
                    fontFamily: typography.families.regular,
                    fontSize: typography.sizes.xs * fontScale,
                  },
                ]}
                numberOfLines={1}
              >
                {getDisplayHost(item.url)}
              </Text>
            </View>
          </View>
          <Ionicons name="chevron-forward" size={iconSizes.sm} color={theme.textSec} />
        </TouchableOpacity>
      </Animated.View>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  historyItem: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.md - 1,
  },
  historyIconBox: {
    ...flexCenter,
  },
  historyTitle: {
    marginBottom: spacing.xxs / 2
  },
  historyUrl: {},
});

export default React.memo(SwipeableHistoryRow);