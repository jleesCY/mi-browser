import AsyncStorage from "@react-native-async-storage/async-storage";

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

import { LayoutAnimation, Platform, UIManager } from 'react-native';
import { SEARCH_ENGINES } from "./constants";

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

export const getHistoryTitle = (url: string, pageTitle?: string | null) => {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();

    // Check against known search engines
    for (const engine of SEARCH_ENGINES) {
      const engineUrl = new URL(engine.url);
      if (host.includes(engineUrl.hostname.replace("www.", ""))) {
        // Find the query parameter (q or p)
        const params = new URLSearchParams(parsed.search);
        const query = params.get("q") || params.get("p");
        if (query) {
          return `${engine.name} Search: ${query}`;
        }
      }
    }
  } catch (e) {}

  // Fallback to title or hostname
  if (pageTitle && pageTitle.length > 0 && !pageTitle.includes("://")) {
    return pageTitle;
  }
  return getDisplayHost(url);
};

export const getDisplayHost = (url: string | null) => {
  if (!url) return "";
  try {
    const parsed = new URL(url);
    return parsed.hostname;
  } catch {
    return url;
  }
};

export const getFaviconUrl = (url: string | null) => {
  if (!url) return null;
  try {
    const domain = new URL(url).hostname;
    return `https://www.google.com/s2/favicons?domain=${domain}&sz=128`;
  } catch {
    return null;
  }
};

export const parseDeepLinkUrl = (url: string) => {
  if (!url) return null;
  try {
    // Handle standard http/https
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    
    // Handle custom schemes: mi://open?url=... or mi://https://...
    const parsed = new URL(url);
    if (parsed.protocol === 'mi:' || parsed.protocol === 'my-browser:') {
      // Case 1: mi://https://google.com
      const rest = url.replace(/^(mi|my-browser):\/\//, '');
      if (rest.startsWith('http://') || rest.startsWith('https://')) {
          return rest;
      }
      
      // Case 2: mi://open?url=https://google.com
      const params = new URLSearchParams(parsed.search);
      const target = params.get('url') || params.get('href');
      if (target) return target;
    }
  } catch (e) {}
  return null;
};

// --- Storage Helpers ---
export const saveStorage = async (key: string, value: any) => {
  try {
    const jsonValue = JSON.stringify(value);
    await AsyncStorage.setItem(key, jsonValue);
  } catch (e) {
    console.error("Failed to save data", e);
  }
};

export const loadStorage = async (key: string) => {
  try {
    const jsonValue = await AsyncStorage.getItem(key);
    return jsonValue != null ? JSON.parse(jsonValue) : null;
  } catch (e) {
    console.error("Failed to load data", e);
    return null;
  }
};

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
