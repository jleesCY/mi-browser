import { Ionicons } from "@expo/vector-icons";
import * as FileSystem from 'expo-file-system/legacy';
import * as MediaLibrary from 'expo-media-library';
import * as Sharing from 'expo-sharing';
import React, { useRef, useState } from 'react';
import { Linking, Modal, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { flexRow } from '../../design-system/styles';
import { borderWidths, iconSizes, opacity, shadows, spacing, typography } from '../../design-system/tokens';
import { CustomAlert } from '../BrowserOverlay/CustomAlert';

interface QRGeneratorViewProps {
  isVisible: boolean;
  onClose: () => void;
  url: string;
  theme: any;
  accentColor: string;
  fontScale: number;
}

export const QRGeneratorView: React.FC<QRGeneratorViewProps> = ({
  isVisible,
  onClose,
  url,
  theme,
  accentColor,
  fontScale
}) => {
  const qrRef = useRef<any>(null);
  const [permissionResponse, requestPermission] = MediaLibrary.usePermissions({ writeOnly: true });

  // Alert State
  const [alertConfig, setAlertConfig] = useState({
    visible: false,
    title: "",
    message: "",
    buttons: [] as any[],
  });

  const showAlert = (title: string, message: string, buttons: any[] = []) => {
    setAlertConfig({
      visible: true,
      title,
      message,
      buttons: buttons.length ? buttons : [{ text: 'OK', onPress: () => setAlertConfig(prev => ({ ...prev, visible: false })) }]
    });
  };

  const hideAlert = () => {
    setAlertConfig(prev => ({ ...prev, visible: false }));
  }

  const saveToGallery = async () => {
    try {
      let currentStatus = permissionResponse?.status;

      if (currentStatus !== 'granted') {
        const { status, canAskAgain } = await requestPermission();
        currentStatus = status;

        if (currentStatus !== 'granted') {
          if (!canAskAgain) {
            showAlert(
              "Permission Required",
              "Photo access has been denied. Please enable it in your device settings to save QR codes.",
              [
                { text: "Cancel", style: "cancel" },
                { text: "Open Settings", onPress: () => Linking.openSettings() }
              ]
            );
          } else {
            showAlert("Permission Required", "This app needs access to your Photos to save the QR code.");
          }
          return;
        }
      }

      if (qrRef.current) {
        qrRef.current.toDataURL(async (data: string) => {
          try {
            const fileName = `qrcode_${Date.now()}.png`;
            const fileUri = FileSystem.cacheDirectory + fileName;

            // Handle potential data URI prefix
            const base64Data = data.startsWith('data:image')
              ? data.split('base64,')[1]
              : data;

            await FileSystem.writeAsStringAsync(fileUri, base64Data, {
              encoding: 'base64'
            });

            await MediaLibrary.saveToLibraryAsync(fileUri);
            showAlert("Success", "QR Code saved to gallery!");
          } catch (e: any) {
            console.log("Inner save error:", e);
            showAlert("Error", "Failed to save QR code: " + (e.message || "Unknown error"));
          }
        });
      }
    } catch (e: any) {
      console.log("Outer save error:", e);
      showAlert("Error", "Something went wrong: " + (e.message || "Unknown error"));
    }
  };

  const shareQR = () => {
    if (qrRef.current) {
      qrRef.current.toDataURL(async (data: string) => {
        try {
          const fileName = `qrcode_share_${Date.now()}.png`;
          const fileUri = FileSystem.cacheDirectory + fileName;

          const base64Data = data.startsWith('data:image')
            ? data.split('base64,')[1]
            : data;

          await FileSystem.writeAsStringAsync(fileUri, base64Data, {
            encoding: 'base64'
          });

          if (!(await Sharing.isAvailableAsync())) {
            showAlert("Error", "Sharing is not available on this device");
            return;
          }

          await Sharing.shareAsync(fileUri);
        } catch (e) {
          console.log("Share Error", e);
          showAlert("Error", "Failed to share QR code.");
        }
      });
    }
  }

  return (
    <Modal visible={isVisible} animationType="fade" transparent onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={[styles.container, { backgroundColor: theme.surface }]}>
          <View style={styles.header}>
            <Text style={[styles.title, { color: theme.text, fontSize: typography.sizes.lg * fontScale }]}>QR Code</Text>
            <TouchableOpacity onPress={onClose}>
              <Ionicons name="close" size={iconSizes.md} color={theme.text} />
            </TouchableOpacity>
          </View>

          <View style={styles.qrContainer}>
            <QRCode
              value={url}
              size={200}
              color="black"
              backgroundColor="white"
              getRef={(c) => (qrRef.current = c)}
              quietZone={10}
            />
          </View>

          <Text style={[styles.urlText, { color: theme.textSec, fontSize: typography.sizes.xs * fontScale }]} numberOfLines={2}>
            {url}
          </Text>

          <View style={styles.actions}>
            <TouchableOpacity onPress={saveToGallery} style={[styles.actionBtn, { backgroundColor: accentColor }]}>
              <Ionicons name="download-outline" size={iconSizes.sm} color="white" style={{ marginRight: spacing.xs }} />
              <Text style={{ color: "white", fontWeight: "bold" }}>Save</Text>
            </TouchableOpacity>

            <TouchableOpacity onPress={shareQR} style={[styles.actionBtn, { backgroundColor: theme.card, borderWidth: borderWidths.thin, borderColor: theme.bg }]}>
              <Ionicons name="share-social-outline" size={iconSizes.sm} color={theme.text} style={{ marginRight: spacing.xs }} />
              <Text style={{ color: theme.text, fontWeight: "bold" }}>Share</Text>
            </TouchableOpacity>
          </View>
        </View>
        <CustomAlert
          visible={alertConfig.visible}
          title={alertConfig.title}
          message={alertConfig.message}
          buttons={alertConfig.buttons}
          theme={theme}
          accentColor={accentColor}
          fontScale={fontScale}
          onDismiss={hideAlert}
        />
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20
  },
  container: {
    width: '100%',
    maxWidth: 320,
    borderRadius: spacing.lg,
    padding: spacing.lg,
    alignItems: 'center',
    ...shadows.lg,
  },
  header: {
    ...flexRow,
    width: '100%',
    justifyContent: 'space-between',
    marginBottom: spacing.lg,
  },
  title: {
    fontWeight: 'bold',
  },
  qrContainer: {
    padding: spacing.sm - 2,
    backgroundColor: 'white',
    borderRadius: spacing.sm - 2,
    marginBottom: spacing.md - 1,
  },
  urlText: {
    textAlign: 'center',
    marginBottom: spacing.lg,
    opacity: opacity.medium,
  },
  actions: {
    flexDirection: 'row',
    width: '100%',
    justifyContent: 'space-around',
    gap: 10
  },
  actionBtn: {
    flex: 1,
    ...flexRow,
    justifyContent: 'center',
    paddingVertical: spacing.xs + 4,
    borderRadius: spacing.sm - 2,
  }
});