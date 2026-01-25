import React from 'react';
import { View, Text, StyleSheet, Modal, TouchableOpacity, Dimensions } from 'react-native';

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
  fontScale: number;
  onDismiss?: () => void;
}

export const CustomAlert: React.FC<CustomAlertProps> = ({
  visible,
  title,
  message,
  buttons = [{ text: 'OK', style: 'default', onPress: () => {} }],
  theme,
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
      <View style={styles.centeredView}>
        <View style={[styles.modalView, { backgroundColor: theme.surface }]}>
          <Text style={[styles.modalTitle, { color: theme.text, fontSize: 18 * fontScale }]}>
            {title}
          </Text>
          <Text style={[styles.modalMessage, { color: theme.textSec, fontSize: 14 * fontScale }]}>
            {message}
          </Text>

          <View style={styles.buttonContainer}>
            {buttons.map((btn, index) => (
              <TouchableOpacity
                key={index}
                style={[
                  styles.button,
                  btn.style === 'cancel' ? styles.cancelButton : 
                  btn.style === 'destructive' ? styles.destructiveButton : styles.defaultButton,
                  // If it's the only button, make it full width (optional, but good for "OK")
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
                    { fontSize: 16 * fontScale },
                    btn.style === 'cancel' ? { color: theme.text } : 
                    btn.style === 'destructive' ? { color: 'white' } : 
                    { color: 'white' } // default
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
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.5)",
    padding: 20
  },
  modalView: {
    width: '100%',
    maxWidth: 320,
    borderRadius: 20,
    padding: 24,
    alignItems: "center",
    shadowColor: "#000",
    shadowOffset: {
      width: 0,
      height: 2
    },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 5
  },
  modalTitle: {
    fontWeight: "bold",
    textAlign: "center",
    marginBottom: 10
  },
  modalMessage: {
    textAlign: "center",
    marginBottom: 24,
    lineHeight: 22
  },
  buttonContainer: {
    flexDirection: "row",
    justifyContent: "flex-end",
    width: "100%",
    gap: 10
  },
  button: {
    borderRadius: 10,
    padding: 10,
    paddingHorizontal: 20,
    minWidth: 80,
    alignItems: 'center',
    justifyContent: 'center',
    flex: 1
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
    borderColor: '#ccc' // Will be overridden if needed or we can pass theme border color
  },
  textStyle: {
    fontWeight: "bold",
    textAlign: "center"
  },
});
