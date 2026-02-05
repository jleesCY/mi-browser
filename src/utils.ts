import AsyncStorage from "@react-native-async-storage/async-storage";
import { Platform, UIManager } from 'react-native';
import { SEARCH_ENGINES } from "./constants";

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

/**
 * Converts a hexadecimal color string to RGBA format
 * 
 * Supports 6-character (#RRGGBB) and 8-character (#RRGGBBAA) hex formats.
 * Optional alpha parameter overrides any alpha in the hex string.
 * 
 * @param hex - Hexadecimal color string (with or without # prefix)
 * @param alphaStr - Optional alpha value as hex string (00-FF)
 * @returns RGBA color string formatted as "rgba(r, g, b, a)"
 * 
 * @example
 * ```typescript
 * hexToRgba('#007AFF', 'CC');  // "rgba(0, 122, 255, 0.80)"
 * hexToRgba('FF5733');          // "rgba(255, 87, 51, 1.00)"
 * ```
 */
export const hexToRgba = (hex: string, alphaStr?: string) => {
  let r = 0, g = 0, b = 0, a = 1;

  if (hex.startsWith('#')) {
    hex = hex.substring(1);
  }

  if (hex.length === 6) {
    r = parseInt(hex.substring(0, 2), 16);
    g = parseInt(hex.substring(2, 4), 16);
    b = parseInt(hex.substring(4, 6), 16);
  } else if (hex.length === 8) {
    // Already has alpha? Not handling this case in basic helper if we append alpha separate
    // But let's support it just in case
    r = parseInt(hex.substring(0, 2), 16);
    g = parseInt(hex.substring(2, 4), 16);
    b = parseInt(hex.substring(4, 6), 16);
    a = parseInt(hex.substring(6, 8), 16) / 255;
  }

  if (alphaStr) {
    // alphaStr is like "FF", "F2", "99"
    const alphaVal = parseInt(alphaStr, 16) / 255;
    // If hex already had alpha, multiply? Or override? Override is simpler for our usage.
    a = alphaVal;
  }

  return `rgba(${r}, ${g}, ${b}, ${a.toFixed(2)})`;
};

/**
 * Generates an adaptive dark theme color palette based on an accent color
 * 
 * Creates a cohesive dark theme by deriving background, surface, card, and glass colors
 * from a single accent color. Includes fallback to default blue theme if parsing fails.
 * 
 * @param accentHex - Hex color string for the accent color (#RRGGBB format)
 * @returns Theme object containing:
 *   - bg: Background color
 *   - surface: Surface layer color
 *   - card: Card background color
 *   - text: Primary text color
 *   - textSec: Secondary text color
 *   - glass: Glassmorphic overlay color
 *   - glassBorder: Glass border color
 *   - sheetHeader: Bottom sheet header color
 *   - inputBg: Input field background color
 *   - placeholder: Placeholder text color
 *   - isDark: Boolean indicating dark theme
 * 
 * @example
 * ```typescript
 * const theme = generateAdaptiveTheme('#007AFF');
 * // Returns dark theme with blue-tinted backgrounds
 * ```
 */
export const generateAdaptiveTheme = (accentHex: string) => {
  // SANITY CHECK: Fallback if accentHex is missing or invalid
  const safeAccent = (accentHex && accentHex.startsWith('#') && accentHex.length === 7)
    ? accentHex
    : '#007AFF';

  try {
    const r = parseInt(safeAccent.substring(1, 3), 16);
    const g = parseInt(safeAccent.substring(3, 5), 16);
    const b = parseInt(safeAccent.substring(5, 7), 16);

    // If parsing failed (NaN), throw to catch block
    if (isNaN(r) || isNaN(g) || isNaN(b)) throw new Error("Invalid Color");

    const mix = (base: number, channel: number, strength: number) => {
      return Math.floor(base * (1 - strength) + channel * strength);
    };

    const bgR = mix(10, r, 0.15);
    const bgG = mix(10, g, 0.15);
    const bgB = mix(10, b, 0.15);

    // Helper to force 2 digits
    const toHex = (n: number) => n.toString(16).padStart(2, "0");

    const bg = `#${toHex(bgR)}${toHex(bgG)}${toHex(bgB)}`;

    const surfaceR = mix(25, r, 0.2);
    const surfaceG = mix(25, g, 0.2);
    const surfaceB = mix(25, b, 0.2);
    const surface = `#${toHex(surfaceR)}${toHex(surfaceG)}${toHex(surfaceB)}`;

    const cardR = mix(45, r, 0.2);
    const cardG = mix(45, g, 0.2);
    const cardB = mix(45, b, 0.2);
    const card = `#${toHex(cardR)}${toHex(cardG)}${toHex(cardB)}`;

    // Calculate luminance of the background to determine if text should be light or dark
    // Formula: 0.299*R + 0.587*G + 0.114*B
    const luminance = 0.299 * bgR + 0.587 * bgG + 0.114 * bgB;
    const isDark = luminance < 128;

    // Calculate a distinct glass color (lighter base 60) for better contrast against BG (base 10)
    const glassR = mix(30, r, 0.15);
    const glassG = mix(30, g, 0.15);
    const glassB = mix(30, b, 0.15);
    const glassColor = `#${toHex(glassR)}${toHex(glassG)}${toHex(glassB)}`;

    const glassBorderColor = glassColor;

    // Calculate adaptive solid inputBg (base 25 is lighter than bg base 10)
    const inR = mix(50, r, 0.15);
    const inG = mix(50, g, 0.15);
    const inB = mix(50, b, 0.15);
    const adaptiveInputBg = `#${toHex(inR)}${toHex(inG)}${toHex(inB)}`;

    return {
      bg: bg,
      surface: surface,
      card: card,
      text: "#eaeaea",
      textSec: "#aaaaaa",
      glass: glassColor,
      glassBorder: glassBorderColor,
      sheetHeader: card,
      inputBg: adaptiveInputBg,
      placeholder: "#888",
      isDark: isDark,
    };
  } catch {
    // FALLBACK THEME (Dark Mode Standard) if math fails
    return {
      bg: '#000000',
      surface: '#1c1c1e',
      card: '#2c2c2e',
      text: '#ffffff',
      textSec: '#888888',
      glass: '#1c1c1e',
      glassBorder: '#1c1c1e',
      sheetHeader: '#252527',
      inputBg: '#2c2c2e',
      placeholder: '#aaa',
      isDark: true,
    };
  }
};



/**
 * Extracts the search query from a search engine URL
 * 
 * Recognizes major search engines (Google, DuckDuckGo, Bing, etc.) and extracts
 * the query parameter from their URL structure.
 * 
 * @param url - Full URL string to parse
 * @returns Extracted search query string, or null if not a recognized search engine
 * 
 * @example
 * ```typescript
 * getSearchQueryFromUrl('https://www.google.com/search?q=react+native');
 * // Returns: "react native"
 * ```
 */
export const getSearchQueryFromUrl = (url: string) => {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();

    for (const engine of SEARCH_ENGINES) {
      const engineUrl = new URL(engine.url);
      if (host.includes(engineUrl.hostname.replace("www.", ""))) {
        const params = new URLSearchParams(parsed.search);
        const query = params.get("q") || params.get("p");
        if (query) return query;
      }
    }
  } catch { }
  return null;
};

/**
 * Generates a user-friendly title for a history entry
 * 
 * Creates meaningful titles for history items by:
 * 1. Detecting search queries and formatting as "Engine Search: query"
 * 2. Using page title if available and valid
 * 3. Falling back to hostname
 * 
 * @param url - URL of the page
 * @param pageTitle - Optional page title from WebView
 * @returns Formatted history title
 * 
 * @example
 * ```typescript
 * getHistoryTitle('https://google.com/search?q=test', null);
 * // Returns: "Google Search: test"
 * 
 * getHistoryTitle('https://example.com', 'Example Domain');
 * // Returns: "Example Domain"
 * ```
 */
export const getHistoryTitle = (url: string, pageTitle?: string | null) => {
  try {
    const query = getSearchQueryFromUrl(url);
    if (query) {
      // Find engine name for display?
      const parsed = new URL(url);
      const host = parsed.hostname.toLowerCase();
      const engine = SEARCH_ENGINES.find(e => {
        const u = new URL(e.url);
        return host.includes(u.hostname.replace("www.", ""));
      });
      return engine ? `${engine.name} Search: ${query}` : query;
    }
  } catch { }

  // Fallback to title or hostname
  if (pageTitle && pageTitle.length > 0 && !pageTitle.includes("://")) {
    return pageTitle;
  }
  return getDisplayHost(url);
};

/**
 * Extracts the hostname from a URL string
 * 
 * Safely parses URLs and returns only the hostname (domain).
 * Returns the original string if parsing fails.
 * 
 * @param url - URL string to parse (can be null)
 * @returns Hostname (e.g., "example.com") or empty string if null
 * 
 * @example
 * ```typescript
 * getDisplayHost('https://www.example.com/path');
 * // Returns: "www.example.com"
 * ```
 */
export const getDisplayHost = (url: string | null) => {
  if (!url) return "";
  try {
    const parsed = new URL(url);
    return parsed.hostname;
  } catch {
    return url;
  }
};

/**
 * Generates a favicon URL for a given website URL
 * 
 * Uses Google's favicon service to retrieve site favicon in 128x128 size.
 * Automatically handles URLs with or without http/https protocol.
 * 
 * @param url - Website URL (can be null)
 * @returns Favicon URL from Google's service, or null if URL is invalid
 * 
 * @example
 * ```typescript
 * getFaviconUrl('https://github.com');
 * // Returns: "https://www.google.com/s2/favicons?domain=github.com&sz=128"
 * ```
 */
export const getFaviconUrl = (url: string | null) => {
  if (!url) return null;
  try {
    let targetUrl = url;
    if (!url.startsWith("http") && !url.includes("://")) {
      targetUrl = "https://" + url;
    }
    const domain = new URL(targetUrl).hostname;
    return `https://www.google.com/s2/favicons?domain=${domain}&sz=128`;
  } catch {
    return null;
  }
};

/**
 * Parses deep link URLs and extracts the target web URL
 * 
 * Handles custom URL schemes:
 * - mi://https://example.com
 * - mi-browser://https://example.com
 * - mi://open?url=https://example.com
 * 
 * @param url - Deep link URL to parse
 * @returns Extracted HTTP/HTTPS URL, or null if not valid
 * 
 * @example
 * ```typescript
 * parseDeepLinkUrl('mi://https://example.com');
 * // Returns: "https://example.com"
 * 
 * parseDeepLinkUrl('mi://open?url=https://github.com');
 * // Returns: "https://github.com"
 * ```
 */
export const parseDeepLinkUrl = (url: string) => {
  if (!url) return null;
  try {
    // Handle standard http/https
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }

    // Handle custom schemes: mi://open?url=... or mi://https://...
    const parsed = new URL(url);
    if (parsed.protocol === 'mi:' || parsed.protocol === 'mi-browser:') {
      // Handle mi:// or mi-browser:// links
      const rest = url.replace(/^(mi|mi-browser):\/\//, '');
      if (rest.startsWith('http://') || rest.startsWith('https://')) {
        return rest;
      }

      // Case 2: mi://open?url=https://google.com
      const params = new URLSearchParams(parsed.search);
      const target = params.get('url') || params.get('href');
      if (target) return target;
    }
  } catch { }
  return null;
};

// --- Storage Helpers ---

/**
 * Saves data to AsyncStorage with JSON serialization
 * 
 * **Note**: This stores data in plaintext. For sensitive data (like security settings),
 * use `saveSecure()` from `utils/secureStorage.ts` instead.
 * 
 * @param key - Storage key identifier
 * @param value - Any JSON-serializable value to store
 * 
 * @example
 * ```typescript
 * await saveStorage('settings', { theme: 'dark', fontSize: 16 });
 * ```
 * 
 * @see {@link loadStorage} for loading data
 * @see `utils/secureStorage.ts` for encrypted storage
 */
export const saveStorage = async (key: string, value: any) => {
  try {
    const jsonValue = JSON.stringify(value);
    await AsyncStorage.setItem(key, jsonValue);
  } catch (e) {
    console.error("Failed to save data", e);
  }
};

/**
 * Loads data from AsyncStorage with JSON deserialization
 * 
 * **Note**: This reads plaintext data. For sensitive data, use `loadSecure()`.
 * 
 * @param key - Storage key identifier
 * @returns Parsed data object, or null if key doesn't exist or parsing fails
 * 
 * @example
 * ```typescript
 * const settings = await loadStorage('settings');
 * if (settings) {
 *   console.log(settings.theme);
 * }
 * ```
 * 
 * @see {@link saveStorage} for saving data
 */
export const loadStorage = async (key: string) => {
  try {
    const jsonValue = await AsyncStorage.getItem(key);
    return jsonValue != null ? JSON.parse(jsonValue) : null;
  } catch (e) {
    console.error("Failed to load data", e);
    return null;
  }
};

/**
 * Clears all data from AsyncStorage
 * 
 * **Warning**: This removes ALL stored data including settings, history, and bookmarks.
 * Use with caution, typically only for debugging or user-initiated reset.
 * 
 * @example
 * ```typescript
 * await clearStorage();
 * ```
 */
export const clearStorage = async () => {
  try {
    await AsyncStorage.clear();
  } catch (e) {
    console.error("Failed to clear storage", e);
  }
};

/**
 * Formats a timestamp into a smart, relative date string
 * 
 * Returns human-friendly date strings:
 * - "Today" for same day
 * - "Yesterday" for previous day  
 * - "This Week" for current week
 * - Full date ("Jan 15, 2024") for older dates
 * 
 * @param timestamp - Unix timestamp in milliseconds
 * @returns Formatted date string
 * 
 * @example
 * ```typescript
 * getSmartDate(Date.now());  // "Today"
 * getSmartDate(Date.now() - 86400000);  // "Yesterday"
 * ```
 */
export const getSmartDate = (timestamp: number) => {
  const date = new Date(timestamp);
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();

  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  const isYesterday = yesterday.toDateString() === date.toDateString();

  if (isToday) {
    // Just time: "10:45 AM"
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } else if (isYesterday) {
    // "Yesterday, 10:45 AM"
    return `Yesterday, ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  } else {
    // "Jan 15, 10:45 AM"
    return `${date.toLocaleDateString([], { month: 'short', day: 'numeric' })}, ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  }
};

export const groupHistoryByDate = (historyItems: any[]) => {
  const sections: { title: string; data: any[] }[] = [];

  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  // Last 7 days cutoff
  const lastWeek = new Date();
  lastWeek.setDate(lastWeek.getDate() - 7);

  const groups = {
    "Today": [] as any[],
    "Yesterday": [] as any[],
    "Last 7 Days": [] as any[],
    "Older": [] as any[],
  };

  historyItems.forEach((item) => {
    const date = new Date(item.timestamp);
    if (date.toDateString() === today.toDateString()) {
      groups["Today"].push(item);
    } else if (date.toDateString() === yesterday.toDateString()) {
      groups["Yesterday"].push(item);
    } else if (date > lastWeek) {
      groups["Last 7 Days"].push(item);
    } else {
      groups["Older"].push(item);
    }
  });

  if (groups["Today"].length > 0) sections.push({ title: "Today", data: groups["Today"] });
  if (groups["Yesterday"].length > 0) sections.push({ title: "Yesterday", data: groups["Yesterday"] });
  if (groups["Last 7 Days"].length > 0) sections.push({ title: "Last 7 Days", data: groups["Last 7 Days"] });
  if (groups["Older"].length > 0) sections.push({ title: "Older", data: groups["Older"] });

  return sections;
};

export const groupHistoryBySite = (historyItems: any[]) => {
  const groups: Record<string, any[]> = {};

  historyItems.forEach((item) => {
    const host = getDisplayHost(item.url) || "Unknown";
    if (!groups[host]) {
      groups[host] = [];
    }
    groups[host].push(item);
  });

  const sortedKeys = Object.keys(groups).sort();

  return sortedKeys.map((key) => {
    return {
      title: key,
      data: groups[key]
    };
  });
};

/**
 * Generates a unique identifier string
 * 
 * Creates a pseudo-random unique ID using timestamp and random numbers.
 * Suitable for client-side ID generation (tabs, bookmarks, history items).
 * 
 * **Note**: Not cryptographically secure. Do not use for security-sensitive purposes.
 * 
 * @returns Unique identifier string in format "timestamp-random1-random2"
 * 
 * @example
 * ```typescript
 * const tabId = generateUniqueId();
 * // Returns something like: "1707073200000-123-456"
 * ```
 */
export const generateUniqueId = () => {
  return Date.now().toString(36) + Math.random().toString(36).substr(2, 9);
};

// Backward compatibility alias
export const generateId = generateUniqueId;

