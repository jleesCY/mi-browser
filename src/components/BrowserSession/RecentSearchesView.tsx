import React from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet } from 'react-native';
import { Ionicons } from "@expo/vector-icons";

interface RecentSearchesViewProps {
  searches: string[];
  onSelect: (text: string) => void;
  onRemove: (text: string) => void;
  onClear: () => void;
  theme: any;
  fontScale: number;
}

export const RecentSearchesView = ({
  searches,
  onSelect,
  onRemove,
  onClear,
  theme,
  fontScale
}: RecentSearchesViewProps) => {

  if (searches.length === 0) {
      return (
        <View style={{ flex: 1, backgroundColor: theme.surface, alignItems: 'center', paddingTop: 30 }}>
            <Text style={{ color: theme.textSec, fontFamily: "Nunito_600SemiBold", fontSize: 16 * fontScale }}>
                No recent searches
            </Text>
        </View>
      );
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.surface }}>
      <View style={{ 
        flexDirection: 'row', 
        justifyContent: 'space-between', 
        alignItems: 'center', 
        padding: 15,
        borderBottomWidth: 1,
        borderBottomColor: theme.bg
      }}>
        <Text style={{ 
          color: theme.textSec, 
          fontFamily: "Nunito_700Bold", 
          fontSize: 14 * fontScale 
        }}>
          Recent Searches
        </Text>
        <TouchableOpacity onPress={onClear}>
            <Text style={{ 
                color: theme.textSec, 
                fontFamily: "Nunito_600SemiBold", 
                fontSize: 12 * fontScale 
            }}>
                Clear All
            </Text>
        </TouchableOpacity>
      </View>
      
      <ScrollView keyboardShouldPersistTaps="handled">
        {searches.map((item, index) => (
          <TouchableOpacity
            key={index}
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              paddingVertical: 12,
              paddingHorizontal: 15,
              borderBottomWidth: 1,
              borderBottomColor: theme.bg
            }}
            onPress={() => onSelect(item)}
          >
            <View style={{ flexDirection: 'row', alignItems: 'center', flex: 1 }}>
              <Ionicons name="time-outline" size={20} color={theme.textSec} style={{ marginRight: 15 }} />
              <Text style={{ 
                color: theme.text, 
                fontFamily: "Nunito_600SemiBold", 
                fontSize: 16 * fontScale 
              }} numberOfLines={1}>
                {item}
              </Text>
            </View>
            
            <TouchableOpacity 
              onPress={(e) => {
                  e.stopPropagation();
                  onRemove(item);
              }}
              style={{ padding: 5 }}
            >
              <Ionicons name="close" size={18} color={theme.textSec} />
            </TouchableOpacity>
          </TouchableOpacity>
        ))}
        {/* Spacer for bottom padding if needed */}
        <View style={{ height: 20 }} />
      </ScrollView>
    </View>
  );
};
