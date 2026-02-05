import React from "react";
import { Modal, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { flexCenter } from "../../design-system/styles";
import { shadows, spacing, typography, withOpacity } from "../../design-system/tokens";

interface AlertButton {
  text: string;
  onPress?: () => void;
  style?: 'default' | 'cancel' | 'destructive';
}

interface CustomAlertProps {
  visible: boolean;
  title: string;
  message: string;
  buttons?: AlertButton[];
  theme: any;
  accentColor: string;
  fontScale: number;
  onDismiss?: () => void;
}

export const CustomAlert: React.FC<CustomAlertProps> = ({
  visible,
  title,
  message,
  buttons = [{ text: 'OK', style: 'default', onPress: () => { } }],
  theme,
  accentColor,
  fontScale,
  onDismiss
}) => {
  return (
    <Modal
      animationType="fade"
      transparent={true}
      visible={visible}
      onRequestClose={onDismiss}
    >
      <View style={[styles.centeredView, { backgroundColor: withOpacity('#000000', 0.5) }]}>
        <View style={[
          styles.modalView,
          {
            backgroundColor: theme.surface,
            borderRadius: spacing.lg,
          }
        ]}>
          <Text style={[
            styles.modalTitle,
            {
              color: theme.text,
              fontSize: typography.sizes.lg * fontScale,
              fontFamily: theme.fonts.bold,
            }
          ]}>
            {title}
          </Text>
          <Text style={[
            styles.modalMessage,
            {
              color: theme.textSec,
              fontSize: typography.sizes.sm * fontScale,
              fontFamily: theme.fonts.regular,
            }
          ]}>
            {message}
          </Text>

          <View style={styles.buttonContainer}>
            {buttons.map((btn, index) => (
              <TouchableOpacity
                key={index}
                style={[
                  styles.button,
                  btn.style === 'cancel' ? [styles.cancelButton, { borderColor: theme.textSec }] :
                    btn.style === 'destructive' ? styles.destructiveButton : [styles.defaultButton, { backgroundColor: accentColor }],
                  buttons.length === 1 && { flex: 1 }
                ]}
                onPress={() => {
                  if (btn.onPress) btn.onPress();
                  if (onDismiss) onDismiss();
                }}
              >
                <Text
                  style={[
                    styles.textStyle,
                    {
                      fontSize: typography.sizes.base * fontScale,
                      fontFamily: theme.fonts.bold,
                    },
                    btn.style === 'cancel' ? { color: theme.text } :
                      btn.style === 'destructive' ? { color: 'white' } :
                        { color: 'white' }
                  ]}
                >
                  {btn.text}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  centeredView: {
    flex: 1,
    ...flexCenter,
    padding: spacing.lg,
  },
  modalView: {
    width: '100%',
    maxWidth: 320,
    padding: spacing.xl,
    alignItems: "center",
    ...shadows.lg,
  },
  modalTitle: {
    textAlign: "center",
    marginBottom: spacing.sm - 2,
  },
  modalMessage: {
    textAlign: "center",
    marginBottom: spacing.xl,
    lineHeight: 22,
  },
  buttonContainer: {
    flexDirection: "row",
    justifyContent: "flex-end",
    width: "100%",
    gap: spacing.sm - 2,
  },
  button: {
    borderRadius: spacing.sm - 2,
    padding: spacing.sm - 2,
    paddingHorizontal: spacing.lg,
    minWidth: 80,
    ...flexCenter,
    flex: 1,
  },
  defaultButton: {
    backgroundColor: "#2196F3",
  },
  destructiveButton: {
    backgroundColor: "#FF3B30",
  },
  cancelButton: {
    backgroundColor: "transparent",
    borderWidth: 1,
  },
  textStyle: {
    textAlign: "center",
  },
});
