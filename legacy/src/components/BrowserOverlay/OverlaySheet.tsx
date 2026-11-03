import { Ionicons } from "@expo/vector-icons";
import React from 'react';
import { Animated, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { flexRow } from '../../design-system/styles';
import { borderWidths, iconSizes, shadows, spacing, typography, withOpacity } from '../../design-system/tokens';

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
        styles.container,
        {
          borderTopLeftRadius: cornerRadius,
          borderTopRightRadius: cornerRadius,
          height: overlayHeightAnim,
          backgroundColor: theme.surface,
        },
        shadows.xl,
      ]}
    >
      <View
        style={[
          styles.header,
          {
            backgroundColor: theme.sheetHeader,
            borderBottomColor: withOpacity(theme.bg, 0.5),
          },
        ]}
        {...panHandlers}
      >
        <View style={[
          styles.dragHandle,
          { backgroundColor: withOpacity(theme.text, 0.3) }
        ]} />
        <View style={styles.headerContent}>
          <Text
            style={{
              color: theme.text,
              fontFamily: theme.fonts.extrabold,
              fontSize: typography.sizes.xxl * fontScale,
            }}
          >
            {title}
          </Text>
          <View style={{ flexDirection: "row" }}>
            <TouchableOpacity
              onPress={onClose}
              style={styles.closeButton}
            >
              <Ionicons name="close-circle" size={iconSizes.lg} color={accentColor} />
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

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    overflow: 'hidden',
  },
  header: {
    width: '100%',
    paddingTop: spacing.md - 1,
    paddingBottom: spacing.sm - 2,
    alignItems: 'center',
    borderBottomWidth: borderWidths.thin,
  },
  dragHandle: {
    width: 40,
    height: 5,
    borderRadius: 2.5,
    marginTop: spacing.sm - 2,
    marginBottom: spacing.sm - 2,
  },
  headerContent: {
    ...flexRow,
    justifyContent: 'space-between',
    width: '100%',
    paddingHorizontal: spacing.lg,
  },
  closeButton: {
    padding: spacing.xxs + 1,
  },
});
