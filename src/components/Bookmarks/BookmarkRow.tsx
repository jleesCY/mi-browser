import React from 'react';
import { View, Text, TouchableOpacity, Image } from 'react-native';
import { Ionicons } from "@expo/vector-icons";
import { BookmarkNode } from '../../types';
import { getFaviconUrl } from '../../utils';
import { Swipeable } from 'react-native-gesture-handler';

interface BookmarkRowProps {
  item: BookmarkNode;
  theme: any;
  accent: string;
  radius: number;
  height: number;
  margin: number;
  fontScale: number;
  onPress: () => void;
  onDelete: () => void;
  onRename: () => void;
  onMove?: () => void; // Future: Move to folder
}

export const BookmarkRow: React.FC<BookmarkRowProps> = ({
  item,
  theme,
  accent,
  radius,
  height,
  margin,
  fontScale,
  onPress,
  onDelete,
  onRename,
  onMove
}) => {
  const isFolder = item.type === 'folder';

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
          backgroundColor: theme.surface,
          borderRadius: radius,
          flexDirection: 'row',
          alignItems: 'center',
          paddingHorizontal: 15,
          shadowColor: "#000",
          shadowOffset: { width: 0, height: 1 },
          shadowOpacity: 0.1,
          shadowRadius: 2,
          elevation: 2,
        }}
      >
        <View style={{ 
            width: 40, 
            height: 40, 
            justifyContent: 'center', 
            alignItems: 'center',
            backgroundColor: isFolder ? theme.bg : 'transparent',
            borderRadius: 10,
            marginRight: 15
        }}>
          {isFolder ? (
            <Ionicons name="folder" size={24} color={accent} />
          ) : (
            <>
              {item.url ? (
                  <Image 
                    source={{ uri: getFaviconUrl(item.url) || "" }}
                    style={{ width: 24, height: 24, borderRadius: 4 }}
                    defaultSource={require('../../../assets/images/icon.png')} // Fallback? 
                  />
              ) : (
                  <Ionicons name="globe-outline" size={24} color={theme.text} />
              )}
            </>
          )}
        </View>

        <View style={{ flex: 1, justifyContent: 'center' }}>
          <Text
            numberOfLines={1}
            style={{
              color: theme.text,
              fontFamily: "Nunito_700Bold",
              fontSize: 16 * fontScale,
              marginBottom: 2
            }}
          >
            {item.title}
          </Text>
          {!isFolder && (
             <Text
               numberOfLines={1}
               style={{
                 color: theme.textSec,
                 fontFamily: "Nunito_400Regular",
                 fontSize: 12 * fontScale,
               }}
             >
               {item.url}
             </Text>
          )}
        </View>

        {isFolder && (
             <Ionicons name="chevron-forward" size={20} color={theme.textSec} />
        )}
      </TouchableOpacity>
    </Swipeable>
  );
};
