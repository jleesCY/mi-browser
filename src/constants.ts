import { Dimensions, Platform, UIManager } from "react-native";

export const APP_VERSION = "0.8.0";

if (
  Platform.OS === "android" &&
  UIManager.setLayoutAnimationEnabledExperimental
) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

export const BAR_HEIGHT = 70;
export const SWAP_DISTANCE = 80;
export const HIDDEN_TRANSLATE_Y = 110;
export const SCREEN_WIDTH = Dimensions.get("window").width;
export const SCREEN_HEIGHT = Dimensions.get("window").height;

export const SNAP_CLOSED = 0;
export const SNAP_DEFAULT = SCREEN_HEIGHT * 0.6;
export const SNAP_FULL = SCREEN_HEIGHT * 0.95;

export const HOME_LOGO_TEXT = "mi.";

export const COLORS = {
  light: {
    bg: "#E5E5EA",
    surface: "#ffffff",
    glass: "#FFFFFF",
    glassBorder: "#FFFFFF",
    text: "#000000",
    textSec: "#666666",
    card: "#f0f0f0",
    sheetHeader: "#f2f2f7",
    inputBg: "#F2F2F7",
    placeholder: "#999",
    isDark: false,
  },
  dark: {
    bg: "#000000",
    surface: "#1c1c1e",
    glass: "#1C1C1E",
    glassBorder: "#1C1C1E",
    text: "#ffffff",
    textSec: "#888888",
    card: "#2c2c2e",
    sheetHeader: "#252527",
    inputBg: "#2C2C2E",
    placeholder: "#aaa",
    isDark: true,
  },
};

// GRID: 3 Rows x 6 Columns
// Order: Red, Orange, Yellow, Green, Blue, Purple
export const ACCENTS = [
  // Row 1: Standard / Vibrant (Unchanged)
  "#FF3B30", // Red
  "#FF9500", // Orange
  "#FFCC00", // Yellow
  "#34C759", // Green
  "#007AFF", // Blue
  "#AF52DE", // Purple

  // Row 2: Lighter / Pastel (Brightened)
  "#FF8E86", // Light Red
  "#FFC565", // Light Orange
  "#FFF060", // Light Yellow
  "#69E6B5", // Mint/Teal
  "#75D6FF", // Sky Blue
  "#E4A9FF", // Lavender

  // Row 3: Darker / Deep (Darkened)
  "#8A0F0F", // Deep Red
  "#8F4D00", // Deep Orange
  "#946C00", // Deep Gold
  "#156625", // Deep Green
  "#002E99", // Deep Blue
  "#40136E", // Deep Purple
];

// EXPANDED SEARCH ENGINES
export const SEARCH_ENGINES = [
  {
    name: "Google",
    url: "https://www.google.com/search?q=",
    icon: "logo-google",
  },
  { name: "Bing", url: "https://www.bing.com/search?q=", icon: "grid-sharp" },
  { name: "DuckDuckGo", url: "https://duckduckgo.com/?q=", icon: "logo-tux" },
  {
    name: "Brave",
    url: "https://search.brave.com/search?q=",
    icon: "shield-half-outline",
  },
  { name: "Ecosia", url: "https://www.ecosia.org/search?q=", icon: "leaf" },
  {
    name: "Yahoo",
    url: "https://search.yahoo.com/search?p=",
    icon: "logo-yahoo",
  },
];

export const HISTORY_RANGES = [
  { label: "Last Hour", ms: 3600000 },
  { label: "Last 24 Hours", ms: 86400000 },
  { label: "Last 7 Days", ms: 604800000 },
  { label: "Last 4 Weeks", ms: 2419200000 },
  { label: "All Time", ms: -1 },
];

export const INJECTED_CONTEXT_MENU_SCRIPT = `
      (function() {
        function getParentLink(el) {
          while (el && el.tagName !== 'A') {
            el = el.parentElement;
          }
          return el;
        }
    
        window.oncontextmenu = function(e) {
          var target = e.target;
          var link = getParentLink(target);
          var img = target.tagName === 'IMG' ? target : null;
          
          // Only capture if it's a link or an image
          if (link || img) {
            e.preventDefault();
            e.stopPropagation();
            
            window.ReactNativeWebView.postMessage(JSON.stringify({
              type: 'CONTEXT_MENU',
              data: {
                url: link ? link.href : null,
                imgUrl: img ? img.src : null,
                text: link ? link.innerText.substring(0, 50) : (img ? (img.alt || 'Image') : '')
              }
            }));
            return false;
          }
        };
      })();
    `;
