import { Ionicons } from "@expo/vector-icons";
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, NativeModules, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { flexCenter } from '../../design-system/styles';
import { iconSizes, shadows, spacing, touchTargets, typography } from '../../design-system/tokens';
import { detectQRPureJS } from '../../utils/qrPolyfill';
import { CustomAlert } from '../BrowserOverlay/CustomAlert';

interface QRScannerViewProps {
  isVisible: boolean;
  onClose: () => void;
  onScan: (data: string) => void;
  theme: any;
  accentColor: string;
  fontScale: number;
}

export const QRScannerView: React.FC<QRScannerViewProps> = ({
  isVisible,
  onClose,
  onScan,
  theme,
  accentColor,
  fontScale
}) => {
  const [permission, requestPermission] = useCameraPermissions();
  const [scanned, setScanned] = useState(false);
  const [processing, setProcessing] = useState(false);

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

  useEffect(() => {
    if (isVisible) {
      setScanned(false);
      setProcessing(false);
      if (!permission?.granted) {
        requestPermission();
      }
    }
  }, [isVisible, permission, requestPermission]);

  const handleBarCodeScanned = ({ data }: { data: string }) => {
    if (scanned || processing) return;
    setScanned(true);
    onScan(data);
  };

  const pickImage = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        quality: 1,
        allowsEditing: false,
      });

      if (!result.canceled && result.assets && result.assets.length > 0) {
        const uri = result.assets[0].uri;
        setProcessing(true);

        // 1. Try Native RNQRGenerator (Lazy Load)
        if (NativeModules.RNQrGenerator) {
          try {
            const RNQRGenerator = require('rn-qr-generator').default;
            const response = await RNQRGenerator.detect({ uri: uri });
            if (response.values && response.values.length > 0) {
              setProcessing(false);
              setScanned(true);
              onScan(response.values[0]);
              return;
            }
          } catch (e) {
            console.log("Native QR Scan failed, falling back to JS", e);
          }
        } else {
          console.log("Native RNQrGenerator not found, skipping.");
        }

        // 2. Fallback to Pure JS
        try {
          const jsResult = await detectQRPureJS(uri);
          setProcessing(false);
          if (jsResult) {
            setScanned(true);
            onScan(jsResult);
            return;
          }
        } catch (e) {
          console.log("JS QR Scan failed", e);
        }

        setProcessing(false);
        showAlert("No QR Code Found", "Could not detect a QR code in this image.");
      }
    } catch (e) {
      setProcessing(false);
      console.log("Image Picker Error", e);
    }
  };

  if (!permission) {
    // Camera permissions are still loading.
    return <View />;
  }

  if (!permission.granted) {
    return (
      <Modal visible={isVisible} animationType="slide" transparent>
        <View style={[styles.container, { backgroundColor: theme.bg }]}>
          <Text style={{ color: theme.text, textAlign: 'center', marginBottom: 20 }}>
            We need your permission to show the camera
          </Text>
          <TouchableOpacity onPress={requestPermission} style={[styles.button, { backgroundColor: accentColor }]}>
            <Text style={styles.buttonText}>Grant Permission</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={onClose} style={[styles.button, { backgroundColor: theme.card, marginTop: 10 }]}>
            <Text style={[styles.buttonText, { color: theme.text }]}>Close</Text>
          </TouchableOpacity>
        </View>
      </Modal>
    );
  }

  return (
    <Modal visible={isVisible} animationType="slide" onRequestClose={onClose}>
      <View style={[styles.container, { backgroundColor: "black" }]}>
        <CameraView
          style={StyleSheet.absoluteFillObject}
          onBarcodeScanned={(scanned || processing) ? undefined : handleBarCodeScanned}
          barcodeScannerSettings={{
            barcodeTypes: ["qr"],
          }}
        />

        {/* Overlay */}
        <View style={StyleSheet.absoluteFillObject}>
          <View style={styles.header}>
            <TouchableOpacity onPress={onClose} style={[styles.themeButton, { backgroundColor: theme.surface }]}>
              <Ionicons name="close" size={iconSizes.lg + 2} color={theme.text} />
            </TouchableOpacity>
            <Text style={styles.title}>Scan QR Code</Text>

            <TouchableOpacity onPress={pickImage} style={[styles.themeButton, { marginLeft: 'auto', backgroundColor: theme.surface }]} disabled={processing}>
              {processing ? <ActivityIndicator color={theme.text} size="small" /> : <Ionicons name="image-outline" size={iconSizes.lg + 2} color={theme.text} />}
            </TouchableOpacity>
          </View>

          <View style={styles.centerMarkerContainer}>
            <View style={styles.marker} />
          </View>

          <View style={styles.footer}>
            <Text style={styles.instructionText}>Align the QR code within the frame</Text>
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
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  button: {
    padding: 15,
    borderRadius: 10,
    alignItems: 'center',
  },
  buttonText: {
    color: 'white',
    fontWeight: 'bold',
  },
  header: {
    position: 'absolute',
    top: 50,
    left: 0,
    right: 0,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    zIndex: 10,
  },
  themeButton: {
    padding: spacing.sm - 2,
    borderRadius: 25,
    width: touchTargets.minimum,
    height: touchTargets.minimum,
    ...flexCenter,
    ...shadows.md,
  },
  title: {
    color: 'white',
    fontSize: typography.sizes.lg,
    fontWeight: 'bold',
    marginLeft: spacing.md - 1,
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: -1, height: 1 },
    textShadowRadius: 10
  },
  centerMarkerContainer: {
    flex: 1,
    ...flexCenter,
  },
  marker: {
    width: 250,
    height: 250,
    borderWidth: 2,
    borderColor: 'white',
    borderRadius: 20,
    backgroundColor: 'transparent',
  },
  footer: {
    position: 'absolute',
    bottom: 80,
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  instructionText: {
    color: 'white',
    fontSize: typography.sizes.base,
    backgroundColor: 'rgba(0,0,0,0.6)',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm - 2,
    borderRadius: spacing.lg,
    overflow: 'hidden',
  }
});