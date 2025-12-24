import { Dimensions, Platform, UIManager } from 'react-native';

export const APP_VERSION = "0.3.3";

// Enable LayoutAnimation
if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
    UIManager.setLayoutAnimationEnabledExperimental(true);
}

export const BAR_HEIGHT = 70;
export const SWAP_DISTANCE = 80; 
export const HIDDEN_TRANSLATE_Y = 110; 
export const SCREEN_WIDTH = Dimensions.get('window').width;
export const SCREEN_HEIGHT = Dimensions.get('window').height;

export const SNAP_CLOSED = 0;
export const SNAP_DEFAULT = SCREEN_HEIGHT * 0.6;
export const SNAP_FULL = SCREEN_HEIGHT * 0.95;

export const COLORS = {
    light: {
        bg: '#E5E5EA',
        surface: '#ffffff',
        glass: 'rgba(255, 255, 255, 0.95)',
        glassBorder: 'rgba(0,0,0,0.1)',
        text: '#000000',
        textSec: '#666666',
        card: '#f0f0f0',
        sheetHeader: '#f2f2f7',
        inputBg: 'rgba(0,0,0,0.05)',
        placeholder: '#999',
    },
    dark: {
        bg: '#000000',
        surface: '#1c1c1e',
        glass: 'rgba(30, 30, 30, 0.95)',
        glassBorder: 'rgba(255,255,255,0.1)',
        text: '#ffffff',
        textSec: '#888888',
        card: '#2c2c2e',
        sheetHeader: '#252527',
        inputBg: 'rgba(255,255,255,0.1)',
        placeholder: '#aaa',
    }
};

export const ACCENTS = [
    '#007AFF', '#98989D', '#FF3B30', '#34C759', '#5856D6', '#FF9500',
];

export const SEARCH_ENGINES = [
    { name: 'Google', url: 'https://www.google.com/search?q=', icon: 'logo-google' },
    { name: 'DuckDuckGo', url: 'https://duckduckgo.com/?q=', icon: 'shield-checkmark-outline' },
    { name: 'Bing', url: 'https://www.bing.com/search?q=', icon: 'globe-outline' },
    { name: 'Ecosia', url: 'https://www.ecosia.org/search?q=', icon: 'leaf-outline' },
];

export const HISTORY_RANGES = [
    { label: 'Last Hour', ms: 3600 * 1000 },
    { label: 'Last 24 Hours', ms: 24 * 3600 * 1000 },
    { label: 'Last 7 Days', ms: 7 * 24 * 3600 * 1000 },
    { label: 'Last 4 Weeks', ms: 28 * 24 * 3600 * 1000 },
    { label: 'All Time', ms: -1 },
];