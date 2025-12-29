import { Ionicons } from "@expo/vector-icons";
import React, { useEffect, useRef, useState } from "react";
import {
  Animated,
  Image,
  PanResponder,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { SCREEN_WIDTH } from "../constants";
import { getDisplayHost, getFaviconUrl } from "../utils";

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
          Math.abs(gestureState.dx) > 20 &&
          Math.abs(gestureState.dx) > Math.abs(gestureState.dy)
        );
      },
      onPanResponderMove: (_, gestureState) => {
        if (gestureState.dx < 0) {
          translateX.setValue(gestureState.dx);
        }
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

  return (
    <Animated.View
      style={[
        styles.tabRowContainer,
        { height: itemHeight, opacity, marginBottom: margin },
      ]}
    >
      <View style={styles.deleteLayer}>
        <Animated.View style={{ transform: [{ scale: iconScale }] }}>
          <Ionicons name="trash" size={28} color="#ff3b30" />
        </Animated.View>
      </View>

      <Animated.View
        style={[styles.tabCardWrapper, { transform: [{ translateX }] }]}
        {...panResponder.panHandlers}
      >
        <TouchableOpacity
          activeOpacity={0.9}
          onPress={onPress}
          style={[
            styles.tabCard,
            {
              backgroundColor: isActive ? theme.card : theme.surface,
              borderRadius: radius,
              height: "100%",
            },
          ]}
        >
          <View style={[styles.faviconContainer, { backgroundColor: isActive ? accent : '#555' }]}>
            {item.showLogo && item.url ? (
                <Image 
                    source={{ uri: getFaviconUrl(item.url) || '' }} 
                    style={{ width: 42, height: 42, borderRadius: 21, resizeMode: 'cover' }}
                />
            ) : (
                <Text style={[styles.faviconText, { fontFamily: 'Nunito_800ExtraBold', fontSize: (item.url ? 22 : 18) * fontScale }]}>
                   {item.url ? (item.title ? item.title.charAt(0).toUpperCase() : 'N') : 'mi.'}
                </Text>
            )}
          </View>

          <View style={styles.tabTextContainer}>
            <View style={{ flexDirection: "row", alignItems: "center" }}>
              <Text
                style={[
                  styles.tabTitleText,
                  {
                    color: theme.text,
                    fontFamily: "Nunito_700Bold",
                    fontSize: 16 * fontScale,
                  },
                ]}
                numberOfLines={1}
              >
                {item.title || "New Tab"}
              </Text>
            </View>
            <Text
              style={[
                styles.tabUrlText,
                {
                  color: theme.textSec,
                  fontFamily: "Nunito_600SemiBold",
                  fontSize: 12 * fontScale,
                },
              ]}
              numberOfLines={1}
            >
              {getDisplayHost(item.url) || "Home"}
            </Text>
          </View>

          <TouchableOpacity
            onPress={onRename}
            style={[styles.pencilBtn, { borderColor: theme.textSec }]}
          >
            <Ionicons name="pencil" size={14} color={theme.text} />
          </TouchableOpacity>
        </TouchableOpacity>
      </Animated.View>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  tabRowContainer: { width: "100%", justifyContent: "center" },
  deleteLayer: {
    position: "absolute",
    top: 0,
    bottom: 0,
    left: 0,
    right: 0,
    alignItems: "center",
    justifyContent: "flex-end",
    paddingRight: 20,
    zIndex: 0,
    flexDirection: "row",
  },
  tabCardWrapper: { backgroundColor: "transparent" },
  // Update styles at the bottom
  tabCard: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 15,
    paddingVertical: 8,
    shadowColor: '#000', 
    shadowOffset: { width: 0, height: 4 }, 
    shadowOpacity: 0.1, 
    shadowRadius: 6, 
    elevation: 5
  },
  faviconContainer: { 
      width: 42, 
      height: 42, 
      borderRadius: 21, 
      alignItems: 'center', 
      justifyContent: 'center', 
      marginRight: 15,
      overflow: 'hidden' 
  },
  faviconText: { color: "#fff", fontSize: 20 },
  tabTextContainer: { flex: 1, justifyContent: "center" },
  tabTitleText: { fontSize: 16, marginBottom: 4 },
  tabUrlText: { fontSize: 12 },
  pencilBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderLeftWidth: 1,
    borderRightWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },
});

export default SwipeableTabRow;
