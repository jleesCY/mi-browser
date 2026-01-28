import React, { forwardRef, useRef, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Platform } from 'react-native';
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
  onActiveTabUpdate: (updates: { canGoBack: boolean; canGoForward: boolean; loading: boolean; url?: string; title?: string }) => void;
  onLoadProgress: (progress: number) => void;
  onLoadStart: () => void;
  onLoadEnd: () => void;
  onScroll: (event: any) => void;
  onScrollEnd: () => void;
  onTouchStart: () => void;
  onFullScreen: (isFullScreen: boolean) => void;
  onPermissionRequest: (event: any) => void;
  onExternalLink: (url: string) => void;
  onNewWindow?: (url: string) => void;
  onMessage: (event: any) => void;
  injectedJavaScript: string;
  blockGestures?: boolean;
  containerRef?: React.Ref<View>;
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
  onNewWindow,
  onMessage,
  injectedJavaScript,
  blockGestures = false,
  containerRef,
}, ref) => {
  const localRef = useRef<WebView>(null);
  const [ignoredHosts, setIgnoredHosts] = useState<Set<string>>(new Set());
  const {
    jsEnabled,
    desktopMode,
    blockCookies,
    accentColor,
    pillHeight,
    httpsOnly,
    readerModeEnabled
  } = settings;

  const isDesktop = tab.desktopMode ?? desktopMode;
  const isReader = tab.readerMode ?? readerModeEnabled;

  // Calculate User Agent
  let userAgent = "";
  if (isDesktop) {
     userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  } else if (Platform.OS === 'ios') {
     userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1";
  } else {
     userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
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
    const isSslError = errorDesc.includes("ERR_CERT") || errorDesc.includes("ERR_SSL") || errorDesc.includes("ssl_error") || errorCode === -11 || errorCode === -1200 || errorCode === -1201 || errorCode === -1202;
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
              if (localRef.current) {
                  localRef.current.reload();
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

        {/* Upgrade to HTTPS Option */}
        {(tab.url && tab.url.startsWith("http://")) && (
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
             <Text style={{ color: effectiveTheme.text, fontFamily: "Nunito_700Bold", fontSize: 16 }}>
               Upgrade to HTTPS
             </Text>
           </TouchableOpacity>
        )}

        {/* Proceed to Unsafe Site Option */}
        {(isSslError || isCleartextError) && (
             <TouchableOpacity
             style={{
               marginTop: 15,
               paddingHorizontal: 20,
               paddingVertical: 12,
               borderRadius: 12,
               backgroundColor: "#ff3b30"
             }}
             onPress={() => {
                 const host = getDisplayHost(tab.url);
                 if (host) {
                    setIgnoredHosts(prev => {
                        const next = new Set(prev);
                        next.add(host);
                        return next;
                    });
                    // Short delay to let state update then reload
                    setTimeout(() => {
                        if (localRef.current) {
                            localRef.current.reload();
                        }
                    }, 100);
                 }
             }}
           >
             <Text style={{ color: "#fff", fontFamily: "Nunito_700Bold", fontSize: 16 }}>
               Proceed to Unsafe Site
             </Text>
           </TouchableOpacity>
        )}
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
        originWhitelist={["*"]}
        onShouldStartLoadWithRequest={handleShouldStartLoadWithRequest}
        injectedJavaScript={finalInjectedJavaScript}
        onReceivedSslError={(event: any) => {
            const url = event.nativeEvent.url;
            const host = getDisplayHost(url);
            if (ignoredHosts.has(host)) {
                event.nativeEvent.proceed();
            } else {
                event.nativeEvent.cancel();
            }
        }}
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
    </View>
  );
});

BrowserWebView.displayName = 'BrowserWebView';