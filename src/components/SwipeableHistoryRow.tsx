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
import { SCREEN_WIDTH } from "../constants";
import { getDisplayHost } from "../utils";

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
}: any) => {
  const translateX = useRef(new Animated.Value(0)).current;
  const itemHeight = useRef(new Animated.Value(height)).current;
  const opacity = useRef(new Animated.Value(1)).current;
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    Animated.timing(itemHeight, {
      toValue: height,
      duration: 200,
      useNativeDriver: false,
    }).start();
  }, [height]);

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
              duration: 200,
              useNativeDriver: false,
            }),
            Animated.timing(itemHeight, {
              toValue: 0,
              duration: 200,
              useNativeDriver: false,
            }),
            Animated.timing(opacity, {
              toValue: 0,
              duration: 200,
              useNativeDriver: false,
            }),
          ]).start(() => onDelete());
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

  const timeStr = new Date(item.timestamp).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
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
          right: 20,
          top: 0,
          bottom: 0,
          justifyContent: "center",
        }}
      >
        <Animated.View style={{ transform: [{ scale: iconScale }] }}>
          <Ionicons name="trash" size={20} color="#ff3b30" />
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
          // FIXED: Added borderRadius: radius to match the theme setting
          style={[
            styles.historyItem,
            {
              backgroundColor: theme.card,
              height: "100%",
              borderRadius: radius,
            },
          ]}
          onPress={onPress}
        >
          <View style={[styles.historyIconBox, { width: 28, height: 28 }]}>
            <Ionicons name="time-outline" size={16} color="#555" />
          </View>
          <View style={{ flex: 1 }}>
            <Text
              style={[
                styles.historyTitle,
                {
                  color: theme.text,
                  fontFamily: "Nunito_600SemiBold",
                  fontSize: 14 * fontScale,
                },
              ]}
              numberOfLines={1}
            >
              {item.title || "Untitled"}
            </Text>
            <View style={{ flexDirection: "row", alignItems: "center" }}>
              <Text
                style={{
                  color: accent,
                  fontSize: 10 * fontScale,
                  marginRight: 6,
                  fontFamily: "Nunito_700Bold",
                }}
              >
                {timeStr}
              </Text>
              <Text
                style={[
                  styles.historyUrl,
                  {
                    color: theme.textSec,
                    fontFamily: "Nunito_400Regular",
                    fontSize: 10 * fontScale,
                  },
                ]}
                numberOfLines={1}
              >
                {getDisplayHost(item.url)}
              </Text>
            </View>
          </View>
          <Ionicons name="chevron-forward" size={14} color={theme.textSec} />
        </TouchableOpacity>
      </Animated.View>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  historyItem: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 8,
    paddingHorizontal: 15,
  },
  historyIconBox: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "rgba(120,120,120,0.2)",
    alignItems: "center",
    justifyContent: "center",
    marginRight: 15,
  },
  historyTitle: { fontSize: 16, marginBottom: 2 },
  historyUrl: { fontSize: 12 },
});

export default SwipeableHistoryRow;
