import React from 'react';
import { View, Animated, Text, TouchableOpacity } from 'react-native';
import { Ionicons } from "@expo/vector-icons";
import { SNAP_FULL } from '../../constants';

interface OverlaySheetProps {
  activeView: string;
  overlayHeightAnim: Animated.Value;
  panHandlers: any;
  title: string;
  onClose: () => void;
  children: React.ReactNode;
  theme: any;
  cornerRadius: number;
  fontScale: number;
  accentColor: string;
  keyboardHeight: Animated.Value;
}

export const OverlaySheet: React.FC<OverlaySheetProps> = ({
  activeView,
  overlayHeightAnim,
  panHandlers,
  title,
  onClose,
  children,
  theme,
  cornerRadius,
  fontScale,
  accentColor,
  keyboardHeight
}) => {
  if (activeView === "none") return null;

  return (
    <Animated.View
      style={[
        {
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          borderTopLeftRadius: cornerRadius,
          borderTopRightRadius: cornerRadius,
          shadowColor: "#000",
          shadowOffset: { width: 0, height: -5 },
          shadowOpacity: 0.3,
          shadowRadius: 10,
          elevation: 20,
          overflow: 'hidden',
        },
        {
          height: overlayHeightAnim,
          backgroundColor: theme.surface,
        },
      ]}
    >
      <View
        style={[
          {
            width: '100%',
            paddingTop: 15,
            paddingBottom: 10,
            alignItems: 'center',
            borderBottomWidth: 1,
          },
          {
            backgroundColor: theme.sheetHeader,
            borderBottomColor: theme.bg,
          },
        ]}
        {...panHandlers}
      >
        <View style={{
          width: 40,
          height: 5,
          borderRadius: 2.5,
          backgroundColor: '#ccc',
          marginTop: 10,
          marginBottom: 10,
        }} />
        <View style={{
          flexDirection: 'row',
          justifyContent: 'space-between',
          alignItems: 'center',
          width: '100%',
          paddingHorizontal: 20,
        }}>
          <Text
            style={{
                color: theme.text,
                fontFamily: "Nunito_800ExtraBold",
                fontSize: 22 * fontScale,
            }}
          >
            {title}
          </Text>
          <View style={{ flexDirection: "row" }}>
            <TouchableOpacity onPress={onClose} style={{ padding: 5 }}>
              <Ionicons name="close-circle" size={28} color={accentColor} />
            </TouchableOpacity>
          </View>
        </View>
      </View>
      <Animated.View style={{ flex: 1, paddingBottom: keyboardHeight }}>
        {children}
      </Animated.View>
    </Animated.View>
  );
};
