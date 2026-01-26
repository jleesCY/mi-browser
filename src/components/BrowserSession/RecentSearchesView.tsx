import React from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet } from 'react-native';
import { Ionicons } from "@expo/vector-icons";
import { HistoryItem } from '../../types';

interface RecentSearchesViewProps {
  historyItems: HistoryItem[];
  onSelect: (item: HistoryItem) => void;
  onRemove: (id: string) => void;
  onClear: () => void;
  theme: any;
  fontScale: number;
}

export const RecentSearchesView = ({
  historyItems,
  onSelect,
  onRemove,
  onClear,
  theme,
  fontScale
}: RecentSearchesViewProps) => {

  if (historyItems.length === 0) {
      return (
        <View style={{ flex: 1, backgroundColor: theme.surface, alignItems: 'center', paddingTop: 30 }}>
            <Text style={{ color: theme.textSec, fontFamily: "Nunito_600SemiBold", fontSize: 16 * fontScale }}>
                No recent history
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
          Recent History
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
        {historyItems.map((item) => (
          <TouchableOpacity
            key={item.id}
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
              <View style={{ flex: 1 }}>
                  <Text style={{ 
                    color: theme.text, 
                    fontFamily: "Nunito_600SemiBold", 
                    fontSize: 16 * fontScale 
                  }} numberOfLines={1}>
                    {item.title || item.url}
                  </Text>
                  <Text style={{ 
                    color: theme.textSec, 
                    fontFamily: "Nunito_600SemiBold", 
                    fontSize: 12 * fontScale,
                    marginTop: 2
                  }} numberOfLines={1}>
                    {item.url}
                  </Text>
              </View>
            </View>
            
            <TouchableOpacity 
              onPress={(e) => {
                  e.stopPropagation();
                  onRemove(item.id);
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
