import React, { useState, useEffect, useRef } from "react";
import {
  Image,
  Text,
  TouchableOpacity,
  View,
  Animated,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Swipeable } from "react-native-gesture-handler";
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
      duration: 150,
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
            marginRight: 2,
            borderRadius: radius,
          }}
        >
          <Ionicons name="pencil" size={24} color="#fff" />
        </TouchableOpacity>
        <TouchableOpacity
          onPress={handleDelete}
          style={{
            backgroundColor: '#FF3B30',
            justifyContent: 'center',
            alignItems: 'center',
            width: 70,
            height: '100%',
            borderTopRightRadius: radius,
            borderBottomRightRadius: radius,
            borderTopLeftRadius: radius,
            borderBottomLeftRadius: radius,
          }}
        >
          <Ionicons name="trash" size={24} color="#fff" />
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
            paddingHorizontal: 15,
            borderWidth: 2,
            borderColor: isActive ? accent : 'transparent'
          }}
        >
          <View style={{ 
              width: 44, 
              height: 44, 
              justifyContent: 'center', 
              alignItems: 'center',
              backgroundColor: isActive ? accent : (theme.isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.05)'),
              borderRadius: 22,
              marginRight: 15
          }}>
            {showTabLogo && item.url && !imageError ? (
                <Image 
                  source={{ uri: getFaviconUrl(item.url) || '' }} 
                  style={{ width: 36, height: 36, borderRadius: 18, resizeMode: 'cover' }}
                  onError={() => setImageError(true)}
                />
            ) : (
                <Ionicons 
                  name="globe-outline" 
                  size={36} 
                  color={isActive ? "#fff" : theme.text} 
                />
            )}
          </View>

          <View style={{ flex: 1, justifyContent: 'center' }}>
            <Text
              style={{
                color: theme.text,
                fontFamily: "Nunito_700Bold",
                fontSize: 16 * fontScale,
                marginBottom: 2
              }}
              numberOfLines={1}
            >
              {item.title || "New Tab"}
            </Text>
            <Text
              style={{
                color: theme.textSec,
                fontFamily: "Nunito_600SemiBold",
                fontSize: 12 * fontScale,
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
