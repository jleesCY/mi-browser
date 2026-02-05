import { Ionicons } from "@expo/vector-icons";
import * as Device from 'expo-device';
import React, { forwardRef, useRef, useState } from 'react';
import { Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { WebView } from 'react-native-webview';
import { TabItem } from '../types';
import { getDisplayHost } from '../utils';

/**
 * Props for BrowserWebView component
 */
interface BrowserWebViewProps {
  /** Tab data to display in the WebView */
  tab: TabItem;

  /** Whether this is the currently active tab */
  isActive: boolean;

  /** Whether the WebView is in fullscreen mode */
  isFullscreen: boolean;

  /** Browser settings (theme, security, etc.) */
  settings: any; // BrowserSettings

  /** Computed theme object with colors */
  effectiveTheme: any;

  /** Callback to update tab state */
  onUpdateTab: (id: string, updates: Partial<TabItem>) => void;

  /** Callback to update active tab metadata (navigation state) */
  onActiveTabUpdate: (updates: { canGoBack: boolean; canGoForward: boolean; loading: boolean; url?: string; title?: string }) => void;

  /** Progress callback (0-1) during page load */
  onLoadProgress: (progress: number) => void;

  /** Callback when page load starts */
  onLoadStart: () => void;

  /** Callback when page load completes */
  onLoadEnd: () => void;

  /** Scroll event callback */
  onScroll: (event: any) => void;

  /** Callback when scrolling ends */
  onScrollEnd: () => void;

  /** Callback when user touches the WebView */
  onTouchStart: () => void;

  /** Fullscreen change callback */
  onFullScreen: (isFullScreen: boolean) => void;

  /** Permission request callback (camera, location, etc.) */
  onPermissionRequest: (event: any) => void;

  /** External link callback (for intent:// or custom schemes) */
  onExternalLink: (url: string) => void;

  /** New window request callback (target="_blank" links) */
  onNewWindow?: (url: string) => void;

  /** Message callback from injected JavaScript */
  onMessage: (event: any) => void;

  /** JavaScript code to inject into page */
  injectedJavaScript: string;

  /** Whether to block gesture handling */
  blockGestures?: boolean;

  /** Ref to the container View */
  containerRef?: React.Ref<View>;

  /** Find-in-page configuration */
  findInPageConfig?: { query: string; forward: boolean; timestamp: number } | null;
}

/**
 * Core WebView wrapper component for browser functionality
 * 
 * Wraps React Native WebView with browser-specific features:
 * - SSL error handling with user bypass option
 * - HTTP error display (timeouts, connection failures)
 * - Reader mode support
 * - Desktop mode user agent switching
 * - JavaScript enable/disable
 * - Cookie blocking
 * - HTTPS-only mode
 * - Find-in-page functionality
 * - Custom error overlays
 * 
 * **Security Features**:
 * - Restricted origin whitelist (http, https, about only)
 * - SSL certificate validation with user warning
 * - Debug logs wrapped in __DEV__ checks
 * 
 * @component
 * @example
 * ```tsx
 * <BrowserWebView
 *   ref={webViewRef}
 *   tab={currentTab}
 *   isActive={true}
 *   isFullscreen={false}
 *   settings={browserSettings}
 *   effectiveTheme={theme}
 *   onUpdateTab={handleUpdateTab}
 *   onActiveTabUpdate={handleActiveUpdate}
 *   // ... other callbacks
 * />
 * ```
 */
export const BrowserWebView = forwardRef((props: BrowserWebViewProps, ref: React.Ref<WebView>) => {
  const {
    tab,
    isActive,
    isFullscreen,
    settings,
    effectiveTheme,
    onUpdateTab,
    onActiveTabUpdate,
    onLoadProgress,
    onLoadStart,
    onLoadEnd,
    onScroll,
    onScrollEnd,
    onTouchStart,
    onFullScreen,
    onPermissionRequest,
    onExternalLink,
    onNewWindow,
    onMessage,
    injectedJavaScript,
    blockGestures = false,
    containerRef,
    findInPageConfig
  } = props;
  const localRef = useRef<WebView>(null);
  const failingUrlRef = useRef<string | null>(null);
  const [sslErrorState, setSslErrorState] = useState<{ url: string } | null>(null);
  const [errorState, setErrorState] = useState<{
    errorDomain?: string;
    errorCode: number;
    errorDesc: string;
  } | null>(null);

  const {
    ignoredHosts,
    setIgnoredHosts
  } = settings;

  // Handle Find In Page
  React.useEffect(() => {
    if (isActive && findInPageConfig && localRef.current) {
      const { query, forward } = findInPageConfig;
      if (!query) {
        // Clear selection logic if needed, usually window.getSelection().removeAllRanges()
        localRef.current.injectJavaScript(`
                (function(){
                    window.getSelection().removeAllRanges();
                })();
             `);
      } else {
        // window.find(aString, aCaseSensitive, aBackwards, aWrapAround, aWholeWord, aSearchInFrames, aShowDialog);
        // Android WebView supports: window.find(string) mostly.
        // Standard: window.find(str, caseSensitive, backwards, wrapAround)
        const js = `
                (function(){
                    if (window.find) {
                        window.find("${query}", false, ${!forward}, true);
                    }
                })();
             `;
        localRef.current.injectJavaScript(js);
      }
    }
  }, [findInPageConfig]);

  const {
    jsEnabled,
    desktopMode,
    blockCookies,
    // accentColor, // Removed from destructuring as per instruction
    pillHeight,
    httpsOnly,
    readerModeEnabled
  } = settings;

  const accentColor = settings.accentColor;
  const fontScale = settings.fontScale || 1;

  const isDesktop = tab.desktopMode ?? desktopMode;
  const isReader = tab.readerMode ?? readerModeEnabled;

  // Calculate User Agent
  let userAgent = "";
  if (isDesktop) {
    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
  } else if (Platform.OS === 'ios') {
    userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
  } else {
    // Dynamic Android UA
    const model = Device.modelName || "Pixel 7";
    const osVer = Device.osVersion || "13";
    userAgent = `Mozilla/5.0 (Linux; Android ${osVer}; ${model}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36`;
  }

  // READER MODE SCRIPT
  const readerScript = isReader ? `
    (function() {
      var style = document.createElement('style');
      style.type = 'text/css';
      style.appendChild(document.createTextNode('body { max-width: 800px; margin: 0 auto; padding: 20px; font-size: 18px; line-height: 1.6; color: #333; background: #fff; } img { max-width: 100%; height: auto; } nav, header, footer, .ad, .advertisement, .sidebar { display: none !important; }'));
      document.head.appendChild(style);
    })();
  ` : '';

  // SECURITY: finalInjectedJavaScript combines parent-provided script with reader mode.
  // WARNING: Never include user input or external data in injectedJavaScript as it
  // executes in the context of the loaded page and could lead to XSS vulnerabilities.
  const finalInjectedJavaScript = injectedJavaScript + readerScript;

  // Render Error View
  const renderError = (
    errorDomain: string | undefined,
    errorCode: number,
    errorDesc: string
  ) => {
    let friendlyTitle = "Can't Load Page";
    let friendlyDesc = "Something went wrong while loading this website.";
    let iconName = "alert-circle-outline";

    // Detect common SSL/Security errors to offer specific actions
    const isSslError =
      errorDesc.includes("ERR_CERT") ||
      errorDesc.includes("ERR_SSL") ||
      errorDesc.includes("ssl_error") ||
      errorCode === -11 ||
      (errorCode <= -1200 && errorCode >= -1206); // Android SSL error range

    const isCleartextError = errorDesc.includes("ERR_CLEARTEXT_NOT_PERMITTED");

    if (errorDesc.includes("ERR_NAME_NOT_RESOLVED") || errorCode === -2 || errorCode === -1003) {
      friendlyTitle = "Site Not Found";
      friendlyDesc = "We couldn't find this site. Please check your spelling.";
      iconName = "search-outline";
    } else if (errorDesc.includes("ERR_INTERNET_DISCONNECTED") || errorCode === -1009) {
      friendlyTitle = "No Internet";
      friendlyDesc = "Please check your Wi-Fi or mobile data connection.";
      iconName = "wifi-outline";
    } else if (errorDesc.includes("ERR_CONNECTION_TIMED_OUT") || errorCode === -1001) {
      friendlyTitle = "Connection Timed Out";
      friendlyDesc = "The server took too long to respond.";
      iconName = "time-outline";
    } else if (errorDesc.includes("ERR_CONNECTION_REFUSED") || errorCode === -1004) {
      friendlyTitle = "Connection Refused";
      friendlyDesc = "The website refused the connection.";
      iconName = "hand-left-outline";
    } else if (isCleartextError) {
      friendlyTitle = "Security Error";
      friendlyDesc = "This site requires a secure HTTPS connection.";
      iconName = "lock-closed-outline";
    } else if (isSslError) {
      friendlyTitle = "Security Warning";
      friendlyDesc = "The connection to this site is not secure.";
      iconName = "warning-outline";
    }

    return (
      <View
        style={{
          position: "absolute",
          top: 0,
          left: 0,
          bottom: 0,
          right: 0,
          justifyContent: 'center',
          alignItems: 'center',
          padding: 20,
          backgroundColor: effectiveTheme.bg,
          zIndex: 100,
        }}
      >
        <Ionicons
          name={iconName as any}
          size={64}
          color={effectiveTheme.textSec}
          style={{ marginBottom: 20 }}
        />

        <Text
          style={{
            color: effectiveTheme.text,
            fontFamily: effectiveTheme.fonts.extrabold,
            fontSize: 20 * fontScale,
            marginBottom: 10,
            textAlign: 'center'
          }}
        >
          {friendlyTitle}
        </Text>

        <Text
          numberOfLines={1}
          style={{
            color: accentColor,
            fontFamily: effectiveTheme.fonts.bold,
            fontSize: 14 * fontScale,
            marginBottom: 10,
            marginTop: 0,
            maxWidth: '85%',
            textAlign: 'center'
          }}
        >
          {tab.url}
        </Text>

        <Text
          style={{
            color: effectiveTheme.textSec,
            fontFamily: effectiveTheme.fonts.semibold,
            textAlign: 'center',
            marginBottom: 30
          }}
        >
          {friendlyDesc}
        </Text>

        <TouchableOpacity
          style={{
            paddingHorizontal: 20,
            paddingVertical: 12,
            borderRadius: 12,
            backgroundColor: accentColor
          }}
          onPress={() => {
            setErrorState(null); // Clear general error state
            setSslErrorState(null); // Clear SSL error state
            if (localRef.current) {
              localRef.current.reload();
            }
          }}
        >
          <Text
            style={{
              color: "#fff",
              fontFamily: effectiveTheme.fonts.bold,
              fontSize: 16 * fontScale,
            }}
          >
            Try Again
          </Text>
        </TouchableOpacity >

        {/* Upgrade to HTTPS Option */}
        {
          (tab.url && tab.url.startsWith("http://")) && (
            <TouchableOpacity
              style={{
                marginTop: 15,
                paddingHorizontal: 20,
                paddingVertical: 12,
                borderRadius: 12,
                backgroundColor: effectiveTheme.card
              }}
              onPress={() => {
                const newUrl = tab.url!.replace(/^http:\/\//i, "https://");
                onUpdateTab(tab.id, { url: newUrl, requestedUrl: newUrl });
                if (isActive) {
                  onActiveTabUpdate({ canGoBack: tab.canGoBack || false, canGoForward: tab.canGoForward || false, loading: true, url: newUrl });
                }
              }}
            >
              <Text style={{
                color: effectiveTheme.text, fontFamily: effectiveTheme.fonts.bold, fontSize: 16
              }}>
                Upgrade to HTTPS
              </Text>
            </TouchableOpacity >
          )
        }

        {/* Proceed to Unsafe Site Option */}
        {
          (isSslError || isCleartextError) && (
            <TouchableOpacity
              style={{
                marginTop: 15,
                paddingHorizontal: 20,
                paddingVertical: 14,
                borderRadius: 12,
                backgroundColor: "#ff3b30"
              }}
              onPress={() => {
                try {
                  const targetUrl = failingUrlRef.current || tab.url;
                  const host = getDisplayHost(targetUrl);

                  // Always clear error states, even if host extraction fails
                  setErrorState(null);
                  setSslErrorState(null);

                  if (host && host.length > 0) {
                    // Add to ignored hosts if not already present
                    if (!ignoredHosts.includes(host)) {
                      setIgnoredHosts([...ignoredHosts, host]);
                    }

                    // Short delay to let state update then reload
                    setTimeout(() => {
                      if (localRef.current) {
                        localRef.current.reload();
                      }
                    }, 100);
                  } else {
                    // If we can't get the host, just try to reload anyway
                    if (localRef.current) {
                      localRef.current.reload();
                    }
                  }
                } catch (error) {
                  if (__DEV__) {
                    console.error('[SSL Error] Error handling trust button:', error);
                  }
                  // Clear error states anyway
                  setErrorState(null);
                  setSslErrorState(null);
                }
              }}
            >
              <View style={{ alignItems: 'center' }}>
                <Text style={{
                  color: '#fff',
                  fontFamily: effectiveTheme.fonts.bold,
                  fontSize: 16
                }}>
                  ⚠️ Trust This Site Anyway
                </Text>
                <Text style={{
                  color: '#fff',
                  fontFamily: effectiveTheme.fonts.regular,
                  fontSize: 12,
                  marginTop: 4,
                  opacity: 0.9
                }}>
                  This will disable security checks for {getDisplayHost(failingUrlRef.current || tab.url)}
                </Text>
              </View>
            </TouchableOpacity>
          )
        }
      </View >
    );
  };

  const handleShouldStartLoadWithRequest = (request: any) => {
    const { url } = request;

    if (url.startsWith("blob:") || url.startsWith("data:")) return true;
    if (url.startsWith("mibrowser://")) {
      // Need to handle this in parent or passed down
      // returning false to block here, but we need to notify parent
      onExternalLink(url);
      return false;
    }

    if (httpsOnly && url.startsWith("http://")) {
      // Skip HTTPS upgrade for IPs and localhost
      const host = getDisplayHost(url);
      const isIp = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$/.test(host) || host === 'localhost';

      if (!isIp) {
        const secureUrl = url.replace(/^http:\/\//i, "https://");
        // Redirect
        onUpdateTab(tab.id, { url: secureUrl, requestedUrl: secureUrl, loading: true });
        onActiveTabUpdate({ canGoBack: tab.canGoBack || false, canGoForward: tab.canGoForward || false, loading: true, url: secureUrl });
        return false;
      }
    }

    if (url.startsWith("googleapp://")) return false;

    const isStandardScheme =
      url.startsWith("http://") ||
      url.startsWith("https://") ||
      url.startsWith("about:");

    if (isStandardScheme) {
      if (url.includes("intent://") || url.includes("#Intent;")) {
        onExternalLink(url);
        return false;
      }
      return true;
    }

    onExternalLink(url);
    return false;
  };

  return (
    <View
      ref={containerRef}
      collapsable={false}
      style={[
        StyleSheet.absoluteFill,
        {
          opacity: isActive ? 1 : 0,
          zIndex: isActive ? 1 : -1,
          transform: [{ translateX: isActive ? 0 : 9999 }],
        },
      ]}
      pointerEvents={isActive ? "auto" : "none"}
    >
      <WebView
        key={tab.id}
        ref={(r) => {
          localRef.current = r;
          if (typeof ref === 'function') ref(r);
          else if (ref) ref.current = r;
        }}
        // @ts-ignore
        pauseJavaScriptBeforeUnmount={true}
        source={{ uri: tab.requestedUrl || tab.url || tab.initialUrl || "" }}
        style={{ backgroundColor: '#ffffff' }}
        containerStyle={
          isFullscreen ? { backgroundColor: "#000" } : { backgroundColor: effectiveTheme.bg }
        }
        renderError={renderError}
        originWhitelist={["http://*", "https://*", "about:*"]}
        onShouldStartLoadWithRequest={handleShouldStartLoadWithRequest}
        injectedJavaScript={finalInjectedJavaScript}
        onReceivedSslError={(event: any) => {
          const url = event.nativeEvent.url;
          const host = getDisplayHost(url);
          if (__DEV__) {
            console.log('[SSL Error]', { url, host, ignoredHosts });
          }
          // Check if host is in ignoredHosts array
          if (ignoredHosts && ignoredHosts.includes(host)) {
            if (__DEV__) {
              console.log('[SSL Error] Proceeding - host is ignored');
            }
            event.nativeEvent.proceed();
            setSslErrorState(null); // Clear any existing error state
          } else {
            if (__DEV__) {
              console.log('[SSL Error] Canceling and showing error UI');
            }
            failingUrlRef.current = url;
            setSslErrorState({ url }); // Set error state to trigger UI
            event.nativeEvent.cancel();
          }
        }}
        onNavigationStateChange={(navState) => {
          const { url, title, canGoBack, canGoForward, loading } = navState;

          if (url && (url.startsWith("intent://") || url.startsWith("android-app://") || url === "about:blank")) {
            return;
          }

          // Clear error states on successful navigation
          if (url && !loading) {
            setErrorState(null);
            setSslErrorState(null);
          }

          if (__DEV__) {
            console.log('[Navigation]', { url, loading });
          }
          // Only update if changed
          const hasChanged =
            tab.url !== url ||
            tab.title !== title ||
            tab.canGoBack !== canGoBack ||
            tab.canGoForward !== canGoForward ||
            tab.loading !== loading;

          if (!hasChanged) return;

          const newTitle =
            title && title.length > 0 && !title.includes("://")
              ? title
              : url
                ? getDisplayHost(url)
                : "New Tab";

          const updatePayload: Partial<TabItem> = {
            url,
            canGoBack,
            canGoForward,
            loading,
          };

          if (!tab.isCustomTitle) {
            updatePayload.title = newTitle;
          }

          onUpdateTab(tab.id, updatePayload);

          if (isActive) {
            onActiveTabUpdate({ canGoBack, canGoForward, loading, url, title: newTitle });
          }
        }}
        onLoadProgress={({ nativeEvent }) => {
          if (isActive) onLoadProgress(nativeEvent.progress);
        }}
        onLoadStart={() => {
          if (isActive) onLoadStart();
        }}
        onLoadEnd={() => {
          if (isActive) onLoadEnd();
        }}
        onError={(e) => {
          if (__DEV__) {
            console.log('[WebView Error]', e.nativeEvent);
          }
          if (isActive) {
            onLoadEnd(); // Stop loading indicator
            // Set error state to trigger error UI
            setErrorState({
              errorDomain: e.nativeEvent.domain,
              errorCode: e.nativeEvent.code,
              errorDesc: e.nativeEvent.description || 'Unknown error'
            });
          }
        }}
        onMessage={onMessage}
        onScroll={onScroll}
        onScrollEndDrag={onScrollEnd}
        onMomentumScrollEnd={onScrollEnd}
        onTouchStart={onTouchStart}
        overScrollMode="never"
        scrollEventThrottle={16}
        startInLoadingState={false}
        javaScriptEnabled={jsEnabled}
        userAgent={userAgent}
        sharedCookiesEnabled={!blockCookies}
        cacheEnabled={true}
        domStorageEnabled={true}
        saveFormDataDisabled={false}
        databaseEnabled={true}
        geolocationEnabled={true}
        pullToRefreshEnabled={false}
        allowsFullscreenVideo={true}
        mediaPlaybackRequiresUserAction={false}
        javaScriptCanOpenWindowsAutomatically={true}
        setSupportMultipleWindows={true}
        onCreateWindow={(syntheticEvent: any) => {
          const { targetUrl } = syntheticEvent.nativeEvent;
          if (targetUrl) {
            if (onNewWindow) {
              onNewWindow(targetUrl);
            } else {
              onExternalLink(targetUrl);
            }
          }
        }}
        onFullScreen={(event: any) => onFullScreen(event.nativeEvent.fullScreen)}
        contentInset={isFullscreen
          ? { top: 0, bottom: 0, left: 0, right: 0 }
          : { bottom: pillHeight + 20 }}
        onPermissionRequest={onPermissionRequest}
        allowsBackForwardNavigationGestures={true}
      />
      {blockGestures && (
        <View
          style={[
            StyleSheet.absoluteFill,
            { zIndex: 99, backgroundColor: 'transparent' }
          ]}
          onTouchStart={onTouchStart}
        />
      )}
      {/* Render Error Overlays */}
      {errorState && (() => {
        if (__DEV__) {
          console.log('[WebView Error] Rendering error overlay:', errorState);
        }
        return renderError(errorState.errorDomain, errorState.errorCode, errorState.errorDesc);
      })()}
      {sslErrorState && (() => {
        if (__DEV__) {
          console.log('[SSL Error] Rendering error overlay for:', sslErrorState.url);
        }
        return renderError(undefined, -1200, "ERR_SSL_PROTOCOL_ERROR");
      })()}
    </View>
  );
});

BrowserWebView.displayName = 'BrowserWebView';

