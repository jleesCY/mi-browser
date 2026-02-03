import { Ionicons } from "@expo/vector-icons";
import React, { useEffect, useRef } from 'react';
import { Animated, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { flexRow } from '../../design-system/styles';
import { animations, iconSizes, shadows, spacing, typography, zIndex } from '../../design-system/tokens';

interface FindOnPageBarProps {
  visible: boolean;
  onClose: () => void;
  onNext: () => void;
  onPrev: () => void;
  onChangeText: (text: string) => void;
  theme: any;
  accentColor: string;
  fontScale: number;
  cornerRadius: number;
  keyboardHeight: Animated.Value;
}

export const FindOnPageBar = ({
  visible,
  onClose,
  onNext,
  onPrev,
  onChangeText,
  theme,
  accentColor,
  fontScale,
  cornerRadius,
  keyboardHeight
}: FindOnPageBarProps) => {
  const slideAnim = useRef(new Animated.Value(100)).current;
  const inputRef = useRef<TextInput>(null);

  useEffect(() => {
    if (visible) {
      Animated.spring(slideAnim, {
        toValue: 0,
        useNativeDriver: false,
        friction: 8
      }).start(() => {
        inputRef.current?.focus();
      });
    } else {
      Animated.timing(slideAnim, {
        toValue: 100,
        duration: animations.normal,
        useNativeDriver: false
      }).start();
      inputRef.current?.blur();
    }
  }, [visible]);

  if (!visible) return null;

  return (
    <Animated.View style={[
      styles.container,
      {
        backgroundColor: theme.surface,
        borderTopLeftRadius: cornerRadius,
        borderTopRightRadius: cornerRadius,
        bottom: keyboardHeight,
        transform: [{ translateY: slideAnim }]
      }
    ]}>
      <View style={[
        styles.inputContainer,
        {
          backgroundColor: theme.inputBg,
          borderRadius: cornerRadius - spacing.xxs
        }
      ]}>
        <Ionicons
          name="search"
          size={iconSizes.sm}
          color={theme.textSec}
          style={{ marginLeft: spacing.xs }}
        />
        <TextInput
          ref={inputRef}
          style={[
            styles.input,
            {
              color: theme.text,
              fontSize: typography.sizes.base * fontScale,
              fontFamily: typography.families.semibold,
            }
          ]}
          placeholder="Find in page..."
          placeholderTextColor={theme.textSec}
          onChangeText={onChangeText}
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="search"
          onSubmitEditing={onNext}
        />
      </View>

      <View style={styles.controls}>
        <TouchableOpacity onPress={onPrev} style={styles.btn}>
          <Ionicons name="chevron-up" size={iconSizes.md} color={theme.text} />
        </TouchableOpacity>
        <TouchableOpacity onPress={onNext} style={styles.btn}>
          <Ionicons name="chevron-down" size={iconSizes.md} color={theme.text} />
        </TouchableOpacity>
        <TouchableOpacity onPress={onClose} style={[styles.btn, { marginLeft: spacing.xs }]}>
          <Ionicons name="close" size={iconSizes.md} color={theme.textSec} />
        </TouchableOpacity>
      </View>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 60,
    ...flexRow,
    paddingHorizontal: spacing.sm - 2,
    zIndex: zIndex.modal,
    ...shadows.md,
  },
  inputContainer: {
    flex: 1,
    ...flexRow,
    height: 40,
    marginRight: spacing.sm - 2,
  },
  input: {
    flex: 1,
    height: '100%',
    paddingHorizontal: spacing.xs,
  },
  controls: {
    ...flexRow,
  },
  btn: {
    padding: spacing.xxs,
    marginHorizontal: spacing.xxs / 2,
  }
});
