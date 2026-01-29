import React, { useState, useEffect } from 'react';
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
  showIcon?: boolean;
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
  showIcon = true,
  onPress,
  onDelete,
  onRename,
  onMove
}) => {
  const isFolder = item.type === 'folder';
  const [imageError, setImageError] = useState(false);

  useEffect(() => {
    setImageError(false);
  }, [isFolder ? null : (item as any).url]);

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
        }}
      >
        <View style={{ 
            width: 44, 
            height: 44, 
            justifyContent: 'center', 
            alignItems: 'center',
            backgroundColor: (theme.isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.05)'),
            borderRadius: 22,
            marginRight: 15
        }}>
          {isFolder ? (
            <Ionicons name="folder" size={36} color={accent} />
          ) : (
            <>
              {showIcon ? (
                <>
                  {((item as any).icon || getFaviconUrl(item.url)) && !imageError ? (
                      <Image 
                        source={{ uri: (item as any).icon || getFaviconUrl(item.url) }}
                        style={{ width: 36, height: 36, borderRadius: 18 }}
                        onError={() => setImageError(true)}
                      />
                  ) : (
                      <Ionicons name="globe-outline" size={36} color={theme.text} />
                  )}
                </>
              ) : (
                <Text style={{ 
                  color: theme.text, 
                  fontFamily: 'Nunito_800ExtraBold', 
                  fontSize: 20 * fontScale 
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
