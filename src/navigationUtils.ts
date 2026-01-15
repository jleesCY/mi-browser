import { Alert, Linking } from 'react-native';
import { getDisplayHost } from './utils';

// --- ROBUST EXTERNAL LINK HANDLER ---
export const handleExternalLink = async (
  url: string,
  activeTabId: string,
  setTabs: (callback: (prev: any[]) => any[]) => void,
  setActiveUrl: (url: string) => void,
  setInputUrl: (url: string) => void
) => {
  try {
    // 1. Handle Special "Intent" Schemes (Android)
    if (url.startsWith("intent://") || url.includes("#Intent;")) {
      await handleIntent(url, activeTabId, setTabs, setActiveUrl, setInputUrl);
      return;
    }

    // 2. Handle Standard External Schemes (mailto, tel, market, etc.)
    const supported = await Linking.canOpenURL(url);
    if (supported) {
      await Linking.openURL(url);
    } else {
      // Fallback
      await Linking.openURL(url);
    }
  } catch (err: any) {
    Alert.alert(
      "Link Error",
      `Could not open this link.\n\nError: ${err.message || "Unknown error"}\n\nURL: ${url}`,
      [{ text: "OK" }]
    );
  }
};

// --- INTENT HANDLER ---
export const handleIntent = async (
    intentUrl: string,
    activeTabId: string,
    setTabs: (callback: (prev: any[]) => any[]) => void,
    setActiveUrl: (url: string) => void,
    setInputUrl: (url: string) => void
) => {
  try {
    // Attempt 1: Extract and Open the Clean Deep Link
    const schemeMatch = intentUrl.match(/scheme=([^;]+)/);
    if (schemeMatch && schemeMatch[1]) {
      const scheme = schemeMatch[1];
      let pathPart = intentUrl.substring(0, intentUrl.indexOf("#Intent;"));
      pathPart = pathPart.replace(/^intent:\/\/|^intent:/, "");

      const cleanDeepLink = `${scheme}://${pathPart}`;

      try {
        await Linking.openURL(cleanDeepLink);
        return; 
      } catch {
        // Continue
      }
    }

    // Attempt 2: Open directly
    try {
      await Linking.openURL(intentUrl);
      return;
    } catch {
      // Continue
    }

    // Attempt 3: Extract "browser_fallback_url"
    const fallbackMatch = intentUrl.match(/browser_fallback_url=([^;]+)/);
    if (fallbackMatch && fallbackMatch[1]) {
      const fallbackUrl = decodeURIComponent(fallbackMatch[1]);
      // Load fallback in OUR browser
      setTabs((prev) =>
        prev.map((t) =>
          t.id === activeTabId ? { ...t, url: fallbackUrl, requestedUrl: fallbackUrl } : t
        )
      );
      setActiveUrl(fallbackUrl);
      setInputUrl(getDisplayHost(fallbackUrl));
      return;
    }

    // Attempt 4: Extract Package ID
    const packageMatch = intentUrl.match(/package=([^;]+)/);
    if (packageMatch && packageMatch[1]) {
      const packageName = packageMatch[1];
      Alert.alert(
        "App Required",
        `This feature requires an external app (${packageName}). Would you like to view it in the store?`,
        [
          { text: "Cancel", style: "cancel" },
          { 
            text: "View Store", 
            onPress: () => Linking.openURL(`market://details?id=${packageName}`)
          }
        ]
      );
      return;
    }

    Alert.alert(
      "Action Failed",
      "Could not handle this action and no fallback was provided by the website.",
      [{ text: "OK" }]
    );
  } catch (err: any) {
     console.log("Intent Error:", err);
  }
};
