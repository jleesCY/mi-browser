import { Linking } from 'react-native';
import { getDisplayHost } from './utils';

/**
 *  Handles navigation to external URLs and custom URL schemes
 * 
 * This function serves as the main entry point for handling URLs that cannot be
 * displayed in the WebView directly, including:
 * - Android Intent URLs (intent://...)
 * - External app schemes (tel:, mailto:, market:, etc.)
 * - Deep links to other applications
 * 
 * **Flow**:
 * 1. Detects and routes Intent URLs to {@link handleIntent}
 * 2. Attempts to open external schemes via React Native's Linking API
 * 3. Shows error alert if URL cannot be opened
 * 
 * @param url - URL to handle (can be http, https, or custom scheme)
 * @param activeTabId - ID of the currently active tab
 * @param setTabs - State setter function for tabs array
 * @param setActiveUrl - State setter for active URL
 * @param setInputUrl - State setter for address bar display URL
 * @param onShowAlert - Function to display alert dialogs to user
 * 
 * @throws Shows alert dialog on error rather than throwing
 * 
 * @example
 * ```typescript
 * await handleExternalLink(
 *   'mailto:example@email.com',
 *   activeTabId,
 *   setTabs,
 *   setActiveUrl,
 *   setInputUrl,
 *   onShowAlert
 * );
 * ```
 * 
 * @see {@link handleIntent} for Intent URL handling
 */
export const handleExternalLink = async (
  url: string,
  activeTabId: string,
  setTabs: (callback: (prev: any[]) => any[]) => void,
  setActiveUrl: (url: string) => void,
  setInputUrl: (url: string) => void,
  onShowAlert: (title: string, message: string, buttons?: any[]) => void
) => {
  try {
    // 1. Handle Special "Intent" Schemes (Android)
    if (url.startsWith("intent://") || url.includes("#Intent;")) {
      await handleIntent(url, activeTabId, setTabs, setActiveUrl, setInputUrl, onShowAlert);
      return;
    }

    // 2. Handle Standard External Schemes (mailto, tel, market, etc.)
    const supported = await Linking.canOpenURL(url);
    if (supported) {
      await Linking.openURL(url);
    }
  } catch (err: any) {
    onShowAlert(
      "Link Error",
      `Could not open this link.\n\nError: ${err.message || "Unknown error"}\n\nURL: ${url}`,
      [{ text: "OK" }]
    );
  }
};

/**
 * Handles Android Intent URLs with multiple fallback strategies
 * 
 * Android Intent URLs (intent://...) are a special format used by Android apps
 * to trigger app launches with fallback URLs. This function implements a comprehensive
 * fallback chain to ensure the user can always access the intended content.
 * 
 * **Fallback Strategy (in order)**:
 * 1. Extract scheme and attempt to open the constructed deep link
 * 2. Attempt to open the raw intent URL
 * 3. Extract and validate browser_fallback_url, load in WebView
 * 4. Extract package name and prompt user to view in app store
 * 5. Show error if all attempts fail
 * 
 * **Security**: Validates fallback URLs to ensure they use http/https schemes only.
 * This prevents injection of javascript: or other dangerous URLs.
 * 
 * @param intentUrl - Android Intent URL to process
 * @param activeTabId - ID of the currently active tab
 * @param setTabs - State setter function for tabs array
 * @param setActiveUrl - State setter for active URL
 * @param setInputUrl - State setter for address bar display URL
 * @param onShowAlert - Function to display alert dialogs to user
 * 
 * @example
 * ```typescript
 * // Intent URL with fallback
 * await handleIntent(
 *   'intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;browser_fallback_url=https://example.com;end',
 *   activeTabId,
 *   setTabs,
 *   setActiveUrl,
 *   setInputUrl,
 *   onShowAlert
 * );
 * // Will attempt to open ZXing scanner, fallback to example.com if app not installed
 * ```
 * 
 * @security Validates fallback URLs - only http/https schemes allowed
 */
export const handleIntent = async (
  intentUrl: string,
  activeTabId: string,
  setTabs: (callback: (prev: any[]) => any[]) => void,
  setActiveUrl: (url: string) => void,
  setInputUrl: (url: string) => void,
  onShowAlert: (title: string, message: string, buttons?: any[]) => void
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

      // SECURITY: Validate fallback URL scheme to prevent dangerous URLs
      if (!fallbackUrl.startsWith('http://') && !fallbackUrl.startsWith('https://')) {
        onShowAlert(
          "Invalid Fallback URL",
          "The fallback URL provided is not a valid web address.",
          [{ text: "OK" }]
        );
        return;
      }

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
      onShowAlert(
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

    onShowAlert(
      "Action Failed",
      "Could not handle this action and no fallback was provided by the website.",
      [{ text: "OK" }]
    );
  } catch (err: any) {
    console.log("Intent Error:", err);
  }
};