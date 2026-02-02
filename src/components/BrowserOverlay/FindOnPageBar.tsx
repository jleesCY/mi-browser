import React, { useRef, useEffect } from 'react';
import { View, TextInput, TouchableOpacity, Text, StyleSheet, Animated } from 'react-native';
import { Ionicons } from "@expo/vector-icons";

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
        useNativeDriver: false, // height/bottom animations usually don't support native driver with layout props perfectly, but transform does. 
        // We are using 'bottom' with Animated.Value (keyboardHeight), which works better with useNativeDriver: false on some versions, 
        // but let's try false to be safe with mixing style props.
        friction: 8
      }).start(() => {
          inputRef.current?.focus();
      });
    } else {
      Animated.timing(slideAnim, {
        toValue: 100,
        duration: 200,
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
      <View style={[styles.inputContainer, { backgroundColor: theme.inputBg, borderRadius: cornerRadius - 4 }]}>
        <Ionicons name="search" size={20} color={theme.textSec} style={{ marginLeft: 8 }} />
        <TextInput
            ref={inputRef}
            style={[styles.input, { color: theme.text, fontSize: 16 * fontScale }]}
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
            <Ionicons name="chevron-up" size={24} color={theme.text} />
          </TouchableOpacity>
          <TouchableOpacity onPress={onNext} style={styles.btn}>
            <Ionicons name="chevron-down" size={24} color={theme.text} />
          </TouchableOpacity>
          <TouchableOpacity onPress={onClose} style={[styles.btn, { marginLeft: 8 }]}>
            <Ionicons name="close" size={24} color={theme.textSec} />
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
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    zIndex: 100,
    elevation: 20,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  inputContainer: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    height: 40,
    borderRadius: 20,
    marginRight: 10,
  },
  input: {
    flex: 1,
    height: '100%',
    paddingHorizontal: 8,
    fontFamily: "Nunito_600SemiBold",
  },
  controls: {
      flexDirection: 'row',
      alignItems: 'center'
  },
  btn: {
      padding: 4,
      marginHorizontal: 2
  }
});
