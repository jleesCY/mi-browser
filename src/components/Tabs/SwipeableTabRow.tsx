import React, { useState, useEffect } from "react";
import {
  Image,
  Text,
  TouchableOpacity,
  View,
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

  useEffect(() => {
    setImageError(false);
  }, [item.url]);

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
          onPress={onDelete}
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
  );
};

export default SwipeableTabRow;
