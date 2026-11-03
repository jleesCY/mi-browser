import AsyncStorage from '@react-native-async-storage/async-storage';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import JSZip from 'jszip';
import { Alert, Platform } from 'react-native';
import { loadSecure, saveSecure } from './secureStorage';

const CACHE_DIR = FileSystem.cacheDirectory;


// Simplified Data Structure
export interface AppData {
    settings: any;
    ignoredHosts: string[] | null;
    backgroundImageName: string | null;
}



export const exportData = async () => {
    try {
        const zip = new JSZip();

        // 1. Load Settings
        const settingsStr = await AsyncStorage.getItem('settings');
        const settings = settingsStr ? JSON.parse(settingsStr) : {};

        // 2. Load Ignored Hosts
        const ignoredHosts = await loadSecure<string[]>('ignoredHosts');

        let bgImageName: string | null = null;

        // 3. Handle Background Image
        if (settings.homeBackgroundImage) {
            const fileInfo = await FileSystem.getInfoAsync(settings.homeBackgroundImage);
            if (fileInfo.exists) {
                const imgContent = await FileSystem.readAsStringAsync(settings.homeBackgroundImage, {
                    encoding: 'base64',
                });

                const parts = settings.homeBackgroundImage.split('.');
                const ext = parts.length > 1 ? parts[parts.length - 1] : 'jpg';
                bgImageName = `background.${ext}`;
                zip.file(bgImageName, imgContent, { base64: true });
            }
        }

        // 4. Create Data JSON
        const data: AppData = {
            settings: settings,
            ignoredHosts: ignoredHosts,
            backgroundImageName: bgImageName
        };

        zip.file('data.json', JSON.stringify(data));

        // 5. Generate Zip
        const base64Zip = await zip.generateAsync({ type: 'base64' });
        const fileName = `settings_${Date.now()}.midata`;
        const fileUri = `${CACHE_DIR}${fileName}`;

        // 6. Write to File
        await FileSystem.writeAsStringAsync(fileUri, base64Zip, {
            encoding: 'base64',
        });

        // 7. Save / Share
        if (Platform.OS === 'android') {
            const permissions = await FileSystem.StorageAccessFramework.requestDirectoryPermissionsAsync();
            if (permissions.granted) {
                const uri = await FileSystem.StorageAccessFramework.createFileAsync(permissions.directoryUri, fileName, 'application/zip');
                await FileSystem.writeAsStringAsync(uri, base64Zip, { encoding: 'base64' });
                Alert.alert("Success", "Data exported successfully!");
            } else {
                // Fallback to share if permission denied? Or just return
                // Let's fallback to share just in case they want that path
                if (await Sharing.isAvailableAsync()) {
                    await Sharing.shareAsync(fileUri);
                }
            }
        } else {
            // iOS and others
            if (await Sharing.isAvailableAsync()) {
                await Sharing.shareAsync(fileUri, {
                    UTI: 'public.data', // generic
                    mimeType: 'application/zip',
                    dialogTitle: 'Save your data'
                });
            } else {
                Alert.alert("Error", "Sharing is not available on this device");
            }
        }

    } catch (error: any) {
        console.error("Export failed:", error);
        Alert.alert("Export Failed", error.message || "Unknown error occurred");
    }
};

export const parseImportFile = async (): Promise<AppData | null> => {
    try {
        const result = await DocumentPicker.getDocumentAsync({
            copyToCacheDirectory: true,
            type: '*/*' // We can try to restrict to .midata if possible, but */* is safer
        });

        if (result.canceled) return null;

        const fileUri = result.assets[0].uri;
        const fileName = result.assets[0].name;

        if (!fileName.endsWith('.midata')) {
            // Optional: Allow user to import zip if they renamed it? 
            // For now, strict check as requested -> actually wait, request said "check if formatted properly"
            // Extension check is good first step
            if (!fileName.endsWith('.zip')) {
                Alert.alert("Invalid File", "Please select a valid .midata file.");
                return null;
            }
        }

        // Read Zip
        const base64Zip = await FileSystem.readAsStringAsync(fileUri, {
            encoding: 'base64'
        });

        const zip = await JSZip.loadAsync(base64Zip, { base64: true });

        // Check for data.json
        const dataFile = zip.file('data.json');
        if (!dataFile) {
            Alert.alert("Invalid Data", "The selected file does not contain valid app data.");
            return null;
        }

        const jsonStr = await dataFile.async('string');
        const data = JSON.parse(jsonStr) as AppData;

        // Validate structure roughly
        if (!data.settings) {
            Alert.alert("Invalid Data", "Structure mismatch.");
            return null;
        }

        // Helper to get image data lazily? No, just pass the parsed data is fine, 
        // we will extract image during restore
        // BUT we need to attach the zip instance to extract image later!
        // Or we extract image to cache now?

        // Let's attach zip to data temporarily or just extract to cache now?
        // Extracting to cache now is easier. 
        if (data.backgroundImageName) {
            const bgFile = zip.file(data.backgroundImageName);
            if (bgFile) {
                const bgBase64 = await bgFile.async('base64');
                const tempPath = `${CACHE_DIR}imported_${data.backgroundImageName}`;
                await FileSystem.writeAsStringAsync(tempPath, bgBase64, { encoding: 'base64' });
                // Update the data object to point to this temp path for restoration
                data.backgroundImageName = tempPath;
            }
        }

        return data;

    } catch (error: any) {
        console.error("Import parsing failed:", error);
        Alert.alert("Import Failed", "Could not parse file. " + error.message);
        return null;
    }
};

export const restoreData = async (data: AppData) => {
    try {
        const promises = [];

        // Handle Settings + Ignored Hosts + Image
        if (data.settings) {
            const newSettings = { ...data.settings };

            // Handle Background Image
            if (data.backgroundImageName) {
                const fileName = data.backgroundImageName.split('/').pop() || 'background.jpg';
                const uniqueName = `bg_${Date.now()}_${fileName}`;
                const permPath = `${FileSystem.documentDirectory}${uniqueName}`;

                await FileSystem.moveAsync({
                    from: data.backgroundImageName,
                    to: permPath
                });

                newSettings.homeBackgroundImage = permPath;
            }

            promises.push(AsyncStorage.setItem('settings', JSON.stringify(newSettings)));
        }

        if (data.ignoredHosts) {
            await saveSecure('ignoredHosts', data.ignoredHosts);
        }

        await Promise.all(promises);
        return true;
    } catch (e: any) {
        console.error("Restore failed", e);
        Alert.alert("Restore Error", e.message);
        return false;
    }
};
