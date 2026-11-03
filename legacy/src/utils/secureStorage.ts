import * as SecureStore from 'expo-secure-store';

/**
 * Save data securely using native keychain (iOS) or KeyStore (Android)
 * @param key Storage key
 * @param value Data to store (will be JSON stringified)
 */
export const saveSecure = async (key: string, value: any): Promise<void> => {
    try {
        const jsonValue = JSON.stringify(value);
        await SecureStore.setItemAsync(key, jsonValue);
    } catch (e) {
        if (__DEV__) {
            console.error('[SecureStorage] Error saving secure data:', e);
        }
        // Fallback: could optionally save to AsyncStorage with a warning
        throw e;
    }
};

/**
 * Load data securely from native keychain (iOS) or KeyStore (Android)
 * @param key Storage key
 * @returns Parsed data or null if not found
 */
export const loadSecure = async <T>(key: string): Promise<T | null> => {
    try {
        const jsonValue = await SecureStore.getItemAsync(key);
        return jsonValue != null ? JSON.parse(jsonValue) : null;
    } catch (e) {
        if (__DEV__) {
            console.error('[SecureStorage] Error loading secure data:', e);
        }
        return null;
    }
};

/**
 * Delete data from secure storage
 * @param key Storage key
 */
export const deleteSecure = async (key: string): Promise<void> => {
    try {
        await SecureStore.deleteItemAsync(key);
    } catch (e) {
        if (__DEV__) {
            console.error('[SecureStorage] Error deleting secure data:', e);
        }
    }
};
