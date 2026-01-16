import React, { forwardRef } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { WebView } from 'react-native-webview';
import { Ionicons } from "@expo/vector-icons";
import { TabItem } from '../types';
import { getDisplayHost } from '../utils';

interface BrowserWebViewProps {
  tab: TabItem;
  isActive: boolean;
  isFullscreen: boolean;
  settings: any; // BrowserSettings
  effectiveTheme: any;
  onUpdateTab: (id: string, updates: Partial<TabItem>) => void;
  onActiveTabUpdate: (updates: { canGoBack: boolean; canGoForward: boolean; loading: boolean; url?: string }) => void;
  onLoadProgress: (progress: number) => void;
  onLoadStart: () => void;
  onLoadEnd: () => void;
  onScroll: (event: any) => void;
  onScrollEnd: () => void;
  onTouchStart: () => void;
  onFullScreen: (isFullScreen: boolean) => void;
  onPermissionRequest: (event: any) => void;
  onExternalLink: (url: string) => void;
  onMessage: (event: any) => void;
  injectedJavaScript: string;
}

export const BrowserWebView = forwardRef<WebView, BrowserWebViewProps>(({
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
  onMessage,
  injectedJavaScript
}, ref) => {
  const {
    jsEnabled,
    desktopMode,
    blockCookies,
    accentColor,
    pillHeight,
    httpsOnly,
    readerModeEnabled // <--- Added
  } = settings;

  const isDesktop = tab.desktopMode ?? desktopMode;
  const isReader = tab.readerMode ?? readerModeEnabled;

  // READER MODE SCRIPT
  const readerScript = isReader ? `
    (function() {
      var style = document.createElement('style');
      style.type = 'text/css';
      style.appendChild(document.createTextNode('body { max-width: 800px; margin: 0 auto; padding: 20px; font-size: 18px; line-height: 1.6; color: #333; background: #fff; } img { max-width: 100%; height: auto; } nav, header, footer, .ad, .advertisement, .sidebar { display: none !important; }'));
      document.head.appendChild(style);
    })();
  ` : '';

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
    } else if (errorDesc.includes("ERR_CLEARTEXT_NOT_PERMITTED")) {
        friendlyTitle = "Security Error";
        friendlyDesc = "This site requires a secure HTTPS connection.";
        iconName = "lock-closed-outline";
    }

    return (
      <View
        style={{
          flex: 1,
          justifyContent: 'center',
          alignItems: 'center',
          padding: 20,
          backgroundColor: effectiveTheme.bg,
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
            fontFamily: "Nunito_800ExtraBold",
            fontSize: 20,
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
                fontFamily: "Nunito_700Bold",
                fontSize: 14,
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
            fontFamily: "Nunito_600SemiBold",
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
              if (ref && 'current' in ref && ref.current) {
                  (ref.current as any).reload();
              } else if (ref && typeof ref === 'function') {
                  // Cannot easily reload if ref is a callback function without storing it
              } else if (ref) {
                  (ref as any).reload();
              }
          }}
        >
          <Text
            style={{
              color: "#fff",
              fontFamily: "Nunito_700Bold",
              fontSize: 16,
            }}
          >
            Try Again
          </Text>
        </TouchableOpacity>
      </View>
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
      const secureUrl = url.replace(/^http:\/\//i, "https://");
      // Redirect
      onUpdateTab(tab.id, { url: secureUrl, requestedUrl: secureUrl, loading: true });
      onActiveTabUpdate({ canGoBack: tab.canGoBack || false, canGoForward: tab.canGoForward || false, loading: true, url: secureUrl });
      return false;
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
        ref={ref}
        // @ts-ignore
        pauseJavaScriptBeforeUnmount={true}
        source={{ uri: tab.requestedUrl || tab.url || tab.initialUrl || "" }}
        style={{ backgroundColor: '#ffffff' }}
        containerStyle={
            isFullscreen ? { backgroundColor: "#000" } : { backgroundColor: effectiveTheme.bg }
        }
        renderError={renderError}
        originWhitelist={["*"]}
        onShouldStartLoadWithRequest={handleShouldStartLoadWithRequest}
        injectedJavaScript={finalInjectedJavaScript}
        onNavigationStateChange={(navState) => {
            const { url, title, canGoBack, canGoForward, loading } = navState;

            if (url && (url.startsWith("intent://") || url.startsWith("android-app://"))) {
                return;
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

            onUpdateTab(tab.id, {
                url,
                title: newTitle,
                canGoBack,
                canGoForward,
                loading,
            });

            if (isActive) {
                onActiveTabUpdate({ canGoBack, canGoForward, loading, url });
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
            if (isActive) onLoadEnd(); // Stop loading indicator
            // Error handling logic (like auto-search) can be complex to move here completely
            // without more props, but let's stick to basics.
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
        userAgent={isDesktop
            ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            : "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"}
        sharedCookiesEnabled={!blockCookies}
        domStorageEnabled={true}
        androidLayerType="hardware"
        pullToRefreshEnabled={false}
        allowsFullscreenVideo={true}
        mediaPlaybackRequiresUserAction={false}
        javaScriptCanOpenWindowsAutomatically={true}
        onFullScreen={(event: any) => onFullScreen(event.nativeEvent.fullScreen)}
        contentInset={isFullscreen
            ? { top: 0, bottom: 0, left: 0, right: 0 }
            : { bottom: pillHeight + 20 }}
        geolocationEnabled={true}
        onPermissionRequest={onPermissionRequest}
        allowsBackForwardNavigationGestures={true}
        />
    </View>
  );
});

BrowserWebView.displayName = 'BrowserWebView';
