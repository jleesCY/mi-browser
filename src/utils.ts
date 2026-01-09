import AsyncStorage from "@react-native-async-storage/async-storage";

export const generateAdaptiveTheme = (accentHex: string) => {
  const r = parseInt(accentHex.substr(1, 2), 16);
  const g = parseInt(accentHex.substr(3, 2), 16);
  const b = parseInt(accentHex.substr(5, 2), 16);

  const mix = (base: number, channel: number, strength: number) => {
    return Math.floor(base * (1 - strength) + channel * strength);
  };

  const bgR = mix(10, r, 0.15);
  const bgG = mix(10, g, 0.15);
  const bgB = mix(10, b, 0.15);
  const bg = `#${bgR.toString(16).padStart(2, "0")}${bgG
    .toString(16)
    .padStart(2, "0")}${bgB.toString(16).padStart(2, "0")}`;

  const surfaceR = mix(36, r, 0.2);
  const surfaceG = mix(36, g, 0.2);
  const surfaceB = mix(36, b, 0.2);
  const surface = `#${surfaceR.toString(16).padStart(2, "0")}${surfaceG
    .toString(16)
    .padStart(2, "0")}${surfaceB.toString(16).padStart(2, "0")}`;

  const cardR = mix(45, r, 0.2);
  const cardG = mix(45, g, 0.2);
  const cardB = mix(45, b, 0.2);
  const card = `#${cardR.toString(16).padStart(2, "0")}${cardG
    .toString(16)
    .padStart(2, "0")}${cardB.toString(16).padStart(2, "0")}`;

  return {
    bg: bg,
    surface: surface,
    card: card,
    text: "#eaeaea",
    textSec: "#aaaaaa",
    glass: bg + "F5",
    glassBorder: accentHex + "30",
    sheetHeader: card,
    inputBg: "#ffffff15",
    placeholder: "#888",
  };
};

export const getDisplayHost = (url: string | null) => {
  if (!url) return "";
  try {
    const parsed = new URL(url);
    return parsed.hostname;
  } catch (e) {
    return url;
  }
};

export const getFaviconUrl = (url: string | null) => {
  if (!url) return null;
  try {
    const domain = new URL(url).hostname;
    return `https://www.google.com/s2/favicons?domain=${domain}&sz=128`;
  } catch (e) {
    return null;
  }
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
