import React from 'react';
import { View, Text, StyleSheet, Animated } from 'react-native';
import { Ionicons } from "@expo/vector-icons";

interface IncognitoHomeBadgeProps {
  theme: any;
  fontScale: number;
}

export const IncognitoHomeBadge: React.FC<IncognitoHomeBadgeProps> = ({ theme, fontScale }) => {
  return (
    <View style={styles.homeBadgeContainer}>
        <Ionicons name="glasses" size={20 * fontScale} color={theme.textSec} style={{ marginRight: 6 }} />
        <Text style={{ color: theme.textSec, fontFamily: "Nunito_700Bold", fontSize: 14 * fontScale }}>Incognito</Text>
    </View>
  );
};

interface IncognitoFloatingBadgeProps {
  theme: any;
  accentColor: string;
  opacity: any; // Animated.Value
}

export const IncognitoFloatingBadge: React.FC<IncognitoFloatingBadgeProps> = ({ theme, accentColor, opacity }) => {
  return (
     <Animated.View style={[styles.floatingBadgeContainer, { opacity }]}>
         <View style={[styles.floatingBadge, { backgroundColor: theme.card, borderColor: theme.bg }]}>
             <Ionicons name="glasses" size={20} color={accentColor} />
         </View>
     </Animated.View>
  );
};

const styles = StyleSheet.create({
  homeBadgeContainer: {
    position: 'absolute', 
    bottom: -5, 
    flexDirection: 'row', 
    alignItems: 'center' 
  },
  floatingBadgeContainer: {
    marginBottom: -12, 
    zIndex: 10, 
    alignItems: 'center' 
  },
  floatingBadge: {
    width: 36, 
    height: 36, 
    borderRadius: 18, 
    justifyContent: 'center', 
    alignItems: 'center', 
    shadowColor: "#000", 
    shadowOffset: { width: 0, height: 2 }, 
    shadowOpacity: 0.2, 
    shadowRadius: 4, 
    elevation: 5, 
    borderWidth: 2
  }
});
