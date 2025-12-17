import { Ionicons } from '@expo/vector-icons';
import React, { useEffect, useRef, useState } from 'react';
import {
    ActivityIndicator,
    Animated,
    Dimensions,
    Easing,
    FlatList,
    Keyboard,
    PanResponder,
    Platform,
    SafeAreaView,
    StatusBar,
    StyleSheet,
    Text,
    TextInput,
    TouchableOpacity,
    TouchableWithoutFeedback,
    View
} from 'react-native';
import { WebView } from 'react-native-webview';

// --- Configuration ---
const BAR_HEIGHT = 70;
const MAX_OFFSET = 120;
const SCREEN_WIDTH = Dimensions.get('window').width;
const SCREEN_HEIGHT = Dimensions.get('window').height;

// --- Interfaces ---
interface HistoryItem {
  id: string;
  url: string;
  title: string;
}

interface TabItem {
  id: string;
  url: string | null;
  title: string;
}

// --- Component: Swipeable Tab Row ---
const SwipeableTabRow = ({ item, isActive, onPress, onDelete }: { item: TabItem, isActive: boolean, onPress: () => void, onDelete: () => void }) => {
  const translateX = useRef(new Animated.Value(0)).current;
  const itemHeight = useRef(new Animated.Value(80)).current; 
  const opacity = useRef(new Animated.Value(1)).current;

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) => {
        return Math.abs(gestureState.dx) > 20 && Math.abs(gestureState.dx) > Math.abs(gestureState.dy);
      },
      onPanResponderMove: (_, gestureState) => {
        if (gestureState.dx < 0) {
          translateX.setValue(gestureState.dx);
        }
      },
      onPanResponderRelease: (_, gestureState) => {
        if (gestureState.dx < -SCREEN_WIDTH * 0.3) {
          Animated.parallel([
            Animated.timing(translateX, { toValue: -SCREEN_WIDTH, duration: 250, useNativeDriver: false }),
            Animated.timing(itemHeight, { toValue: 0, duration: 250, useNativeDriver: false }),
            Animated.timing(opacity, { toValue: 0, duration: 250, useNativeDriver: false }),
          ]).start(() => onDelete());
        } else {
          Animated.spring(translateX, { toValue: 0, useNativeDriver: false }).start();
        }
      },
    })
  ).current;

  return (
    <Animated.View style={[styles.tabRowContainer, { height: itemHeight, opacity }]}>
      <View style={styles.deleteLayer}>
        <Ionicons name="trash-outline" size={24} color="#fff" />
      </View>

      <Animated.View
        style={[styles.tabCardWrapper, { transform: [{ translateX }] }]}
        {...panResponder.panHandlers}
      >
        <TouchableOpacity
          activeOpacity={1} 
          style={[styles.tabCard, isActive && styles.activeTabCard]}
          onPress={onPress}
        >
          <View style={styles.faviconContainer}>
            <Text style={styles.faviconText}>
              {item.title ? item.title.charAt(0).toUpperCase() : 'N'}
            </Text>
          </View>

          <View style={styles.tabTextContainer}>
            <Text style={styles.tabTitleText} numberOfLines={1}>
              {item.title || 'New Tab'}
            </Text>
            <Text style={styles.tabUrlText} numberOfLines={1}>
              {item.url || 'Home'}
            </Text>
          </View>
        </TouchableOpacity>
      </Animated.View>
    </Animated.View>
  );
};

export default function App() {
  const [inputUrl, setInputUrl] = useState(''); 
  const [activeUrl, setActiveUrl] = useState<string | null>(null);
  const [canGoBack, setCanGoBack] = useState(false);
  const [canGoForward, setCanGoForward] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const [isIncognito, setIsIncognito] = useState(false);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [tabs, setTabs] = useState<TabItem[]>([{ id: '1', url: null, title: 'New Tab' }]);
  const [activeTabId, setActiveTabId] = useState('1');

  const [activeView, setActiveView] = useState<'none' | 'tabs' | 'history' | 'settings'>('none');
  const [isSearchActive, setIsSearchActive] = useState(true);
  const [isInputFocused, setIsInputFocused] = useState(false);

  const scrollTranslateY = useRef(new Animated.Value(0)).current;
  const lastScrollY = useRef(0);
  const animVal = useRef(new Animated.Value(0)).current;
  const currentSnapPoint = useRef(0);
  const overlayAnim = useRef(new Animated.Value(0)).current;
  
  // --- Keyboard Animation (iOS Only) ---
  // On Android, 'adjustResize' handles this natively. On iOS we animate manually.
  const keyboardHeight = useRef(new Animated.Value(0)).current;

  const webViewRef = useRef<WebView>(null);

  // --- Effects ---
  useEffect(() => {
    if (activeView !== 'none') {
      overlayAnim.setValue(0);
      Animated.spring(overlayAnim, { toValue: 1, tension: 40, friction: 8, useNativeDriver: true }).start();
    }
  }, [activeView]);

  // --- Keyboard Listeners (iOS) ---
  useEffect(() => {
    if (Platform.OS === 'ios') {
      const showSub = Keyboard.addListener('keyboardWillShow', (e) => {
        Animated.timing(keyboardHeight, {
          toValue: e.endCoordinates.height,
          duration: e.duration,
          easing: Easing.out(Easing.ease),
          useNativeDriver: false, // height/padding changes require false
        }).start();
      });
      const hideSub = Keyboard.addListener('keyboardWillHide', (e) => {
        Animated.timing(keyboardHeight, {
          toValue: 0,
          duration: e.duration,
          easing: Easing.out(Easing.ease),
          useNativeDriver: false,
        }).start();
      });
      return () => {
        showSub.remove();
        hideSub.remove();
      };
    }
  }, []);

  const handleGoPress = () => {
    Keyboard.dismiss();
    const text = inputUrl.trim();
    if (!text) return; 

    const hasSpace = text.includes(' ');
    const hasDot = text.includes('.');
    const startsWithProtocol = text.startsWith('http://') || text.startsWith('https://');

    let destination = '';
    if (startsWithProtocol) {
        destination = text;
    } else if (!hasSpace && hasDot) {
        destination = `https://${text}`;
    } else {
        destination = `https://www.google.com/search?q=${encodeURIComponent(text)}`;
    }

    setActiveUrl(destination);
    setInputUrl(destination);
    snapToSearch(); 
  };

  const addNewTab = () => {
    const newId = Date.now().toString();
    const newTab = { id: newId, url: null, title: 'New Tab' };
    setTabs(prev => [newTab, ...prev]); 
    setActiveTabId(newId);
    setActiveUrl(null);
    setInputUrl('');
  };

  const deleteTab = (idToDelete: string) => {
    setTimeout(() => {
        const newTabs = tabs.filter(t => t.id !== idToDelete);
        if (newTabs.length === 0) {
            const freshId = Date.now().toString();
            setTabs([{ id: freshId, url: null, title: 'New Tab' }]);
            setActiveTabId(freshId);
            setActiveUrl(null);
            if (activeView === 'none') setInputUrl('');
        } else {
            setTabs(newTabs);
            if (activeTabId === idToDelete) {
                const nextTab = newTabs[0];
                setActiveTabId(nextTab.id);
                setActiveUrl(nextTab.url);
                setInputUrl(nextTab.url || '');
            }
        }
    }, 200);
  };

  const goHome = () => {
    setActiveUrl(null);
    setInputUrl('');
    setTabs(prev => prev.map(tab => 
      tab.id === activeTabId ? { ...tab, url: null, title: 'New Tab' } : tab
    ));
    snapToSearch();
  };

  const closeOverlay = () => {
    Animated.timing(overlayAnim, {
      toValue: 0,
      duration: 250, 
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) {
        setActiveView('none');
        const currentTab = tabs.find(t => t.id === activeTabId);
        if (currentTab && currentTab.url !== activeUrl) {
            setActiveUrl(currentTab.url);
            setInputUrl(currentTab.url || '');
        }
        snapToSearch(); 
      }
    });
  };

  const snapToSearch = () => {
    currentSnapPoint.current = 0;
    setIsSearchActive(true);
    Animated.spring(animVal, {
      toValue: 0,
      tension: 60,
      friction: 9,
      useNativeDriver: true,
    }).start();
  };

  const addToHistory = (url: string, title: string = 'Page') => {
    if (isIncognito || !url) return; 
    setHistory(prev => {
      if (prev.length > 0 && prev[0].url === url) return prev;
      return [{ id: Date.now().toString(), url, title }, ...prev];
    });
    setTabs(prev => prev.map(tab => 
      tab.id === activeTabId ? { ...tab, url, title } : tab
    ));
  };

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) => Math.abs(gestureState.dy) > 5,
      onPanResponderGrant: () => {
        animVal.stopAnimation((value) => {
             animVal.setOffset(value); 
             animVal.setValue(0);
        });
      },
      onPanResponderMove: (_, gestureState) => {
        animVal.setValue(gestureState.dy);
      },
      onPanResponderRelease: (_, gestureState) => {
        animVal.flattenOffset();
        const draggedDist = gestureState.dy;
        const velocity = gestureState.vy;
        
        let target = 0;
        let nextIsSearchActive = true;

        if (currentSnapPoint.current === 0) {
            if (draggedDist < -50 || velocity < -0.5) {
                target = -MAX_OFFSET;
                nextIsSearchActive = false; 
            } else if (draggedDist > 50 || velocity > 0.5) {
                target = MAX_OFFSET;
                nextIsSearchActive = false; 
            } else {
                target = 0;
                nextIsSearchActive = true; 
            }
        } else {
            if (Math.abs(draggedDist) > 50 || Math.abs(velocity) > 0.5) {
                target = 0;
                nextIsSearchActive = true; 
            } else {
                target = currentSnapPoint.current;
                nextIsSearchActive = false; 
            }
        }

        currentSnapPoint.current = target;
        setIsSearchActive(nextIsSearchActive);

        Animated.spring(animVal, {
          toValue: target,
          tension: 60,
          friction: 9,
          useNativeDriver: true,
        }).start();
      },
    })
  ).current;

  const sheetPanResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) => gestureState.dy > 5,
      onPanResponderGrant: () => {
        overlayAnim.stopAnimation();
      },
      onPanResponderMove: (_, gestureState) => {
        const newVal = 1 - (gestureState.dy / SCREEN_HEIGHT);
        if (newVal <= 1) overlayAnim.setValue(newVal);
      },
      onPanResponderRelease: (_, gestureState) => {
        if (gestureState.dy > 100 || gestureState.vy > 0.8) {
             closeOverlay();
        } else {
             Animated.spring(overlayAnim, { toValue: 1, tension: 50, friction: 7, useNativeDriver: true }).start();
        }
      }
    })
  ).current;

  // --- Interpolations ---
  const searchTranslateY = animVal;
  const searchOpacity = animVal.interpolate({
    inputRange: [-MAX_OFFSET, 0, MAX_OFFSET],
    outputRange: [0, 1, 0],
    extrapolate: 'clamp',
  });
  const searchScale = animVal.interpolate({
    inputRange: [-MAX_OFFSET, 0, MAX_OFFSET],
    outputRange: [0.8, 1, 0.8],
    extrapolate: 'clamp',
  });
  const menuScale = animVal.interpolate({
    inputRange: [-MAX_OFFSET, 0, MAX_OFFSET],
    outputRange: [1, 0.85, 1],
    extrapolate: 'clamp',
  });
  const menuOpacity = animVal.interpolate({
    inputRange: [-MAX_OFFSET, -MAX_OFFSET/2, 0, MAX_OFFSET/2, MAX_OFFSET],
    outputRange: [1, 1, 0, 1, 1],
    extrapolate: 'clamp',
  });

  const overlayTranslateY = overlayAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [SCREEN_HEIGHT, 0], 
  });

  const handleNavigationStateChange = (navState: any) => {
    setCanGoBack(navState.canGoBack);
    setCanGoForward(navState.canGoForward);
    setInputUrl(navState.url);
    if (!navState.loading) {
        addToHistory(navState.url, navState.title);
    }
  };

  const handleScroll = (event: any) => {
    const y = event.nativeEvent.contentOffset.y;
    // Don't auto-hide if typing
    if (isInputFocused) return;

    if (Math.abs(currentSnapPoint.current) < 10 && activeView === 'none') {
       if (y <= 0) {
         Animated.timing(scrollTranslateY, { toValue: 0, duration: 300, useNativeDriver: true }).start();
       } else if (y - lastScrollY.current > 10) {
         Animated.timing(scrollTranslateY, { toValue: 200, duration: 300, useNativeDriver: true }).start();
       } else if (y - lastScrollY.current < -10) {
         Animated.timing(scrollTranslateY, { toValue: 0, duration: 300, useNativeDriver: true }).start();
       }
    }
    lastScrollY.current = y;
  };

  const toggleIncognito = () => {
    setIsIncognito(!isIncognito);
  };

  const renderOverlayContent = () => {
    let content = null;
    let title = "";

    if (activeView === 'history') {
      title = "History";
      content = (
        <FlatList 
          data={history}
          keyExtractor={item => item.id}
          contentContainerStyle={{ padding: 20 }}
          renderItem={({item}) => (
            <TouchableOpacity 
              style={styles.historyItem} 
              onPress={() => {
                setActiveUrl(item.url);
                setInputUrl(item.url);
                closeOverlay();
              }}
            >
              <Ionicons name="time-outline" size={20} color="#888" style={{marginRight:10}} />
              <View style={{flex:1}}>
                  <Text style={styles.historyTitle} numberOfLines={1}>{item.title || item.url}</Text>
                  <Text style={styles.historyUrl} numberOfLines={1}>{item.url}</Text>
              </View>
            </TouchableOpacity>
          )}
          ListEmptyComponent={<Text style={styles.emptyText}>No history yet.</Text>}
        />
      );
    } else if (activeView === 'tabs') {
      title = "Tabs";
      content = (
        <View style={{ flex: 1 }}>
            <FlatList 
              data={tabs}
              keyExtractor={item => item.id}
              contentContainerStyle={{ padding: 20, paddingBottom: 100 }}
              renderItem={({item}) => (
                <SwipeableTabRow 
                    item={item} 
                    isActive={item.id === activeTabId}
                    onPress={() => {
                        setActiveTabId(item.id);
                        setActiveUrl(item.url);
                        setInputUrl(item.url || '');
                    }}
                    onDelete={() => deleteTab(item.id)}
                />
              )}
            />
            <View style={styles.fabContainer}>
                <TouchableOpacity style={styles.fabButton} onPress={addNewTab}>
                    <Ionicons name="add" size={30} color="#fff" />
                </TouchableOpacity>
            </View>
        </View>
      );
    } else if (activeView === 'settings') {
      title = "Settings";
      content = (
        <View style={{ padding: 20 }}>
            <View style={styles.settingRow}>
                <Text style={styles.settingText}>Incognito Mode</Text>
                <TouchableOpacity onPress={toggleIncognito}>
                     <Ionicons name={isIncognito ? "toggle" : "toggle-outline"} size={32} color={isIncognito ? "#A020F0" : "#888"} />
                </TouchableOpacity>
            </View>
            <View style={styles.settingRow}>
                <Text style={styles.settingText}>Clear History</Text>
                <TouchableOpacity onPress={() => setHistory([])} style={styles.settingBtn}>
                     <Text style={{color: 'white'}}>Clear</Text>
                </TouchableOpacity>
            </View>
            <Text style={{color:'#666', marginTop: 20, textAlign:'center'}}>Version 1.9.0</Text>
        </View>
      );
    }

    return (
      <Animated.View style={[styles.sheetContainer, { transform: [{ translateY: overlayTranslateY }] }]}>
          <View style={styles.sheetHeader} {...sheetPanResponder.panHandlers}>
              <View style={styles.sheetHandle} /> 
              <View style={styles.sheetHeaderRow}>
                 <Text style={styles.sheetTitle}>{title}</Text>
                 <TouchableOpacity onPress={closeOverlay} style={styles.closeIconBtn}>
                     <Ionicons name="chevron-down" size={28} color="#007AFF" />
                 </TouchableOpacity>
              </View>
          </View>
          {content}
      </Animated.View>
    );
  };

  const webViewProps = {
    ref: webViewRef,
    source: { uri: activeUrl || '' }, 
    onNavigationStateChange: handleNavigationStateChange,
    onLoadStart: () => setIsLoading(true),
    onLoadEnd: () => {
      setIsLoading(false);
    },
    onScroll: handleScroll,
    overScrollMode: 'never',
    incognito: isIncognito,
    scrollEventThrottle: 16,
    startInLoadingState: true,
    renderLoading: () => (
      <View style={styles.loadingOverlay}>
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    ),
    javaScriptEnabled: true,
    domStorageEnabled: true,
    androidLayerType: 'hardware' as const,
    pullToRefreshEnabled: false, 
    // Add extra bottom padding to content so it's not hidden by the bar
    contentInset: { bottom: BAR_HEIGHT + 20 },
  };

  return (
    <SafeAreaView style={[styles.container, isIncognito && styles.incognitoContainer]}>
      <StatusBar translucent={false} backgroundColor={isIncognito ? "#1a1a1a" : "#000"} barStyle="light-content" />

      {/* --- Main Content Area --- */}
      {/* ANIMATED CONTAINER for Webview 
          On iOS, we animate paddingBottom to shrink the view when keyboard opens.
      */}
      <Animated.View style={[styles.webViewContainer, { paddingBottom: keyboardHeight }]}>
        {activeUrl ? (
            <WebView {...webViewProps} />
        ) : (
            <View style={styles.homeContainer}>
                <Text style={styles.homeText}>Jawshy's Browser</Text>
            </View>
        )}
      </Animated.View>

      {/* --- Card Overlay --- */}
      {activeView !== 'none' && (
         <View style={styles.overlayBackdrop}>
             <TouchableWithoutFeedback onPress={closeOverlay}>
                 <View style={styles.backdropTouchArea} />
             </TouchableWithoutFeedback>
             {renderOverlayContent()}
         </View>
      )}

      {/* --- Floating Control Bar --- */}
      {/* We use absolute positioning but animate the translateY based on 
          BOTH scroll direction AND keyboard height (negative value moves it up).
      */}
      {activeView === 'none' && (
        <View style={styles.floatingLayer} pointerEvents="box-none">
          <Animated.View
            style={[
              styles.bottomAreaContainer,
              { 
                transform: [
                  { translateY: Animated.add(scrollTranslateY, Animated.multiply(keyboardHeight, -1)) }
                ] 
              },
            ]}
          >
            <View style={styles.floatingBarShadowWrapper}>
              <View style={[styles.floatingBarGlass, isIncognito && styles.incognitoGlass]}>
                <View style={styles.gestureArea} {...panResponder.panHandlers}>
                  
                  {/* --- MENU CARD --- */}
                  <Animated.View
                    style={[
                      styles.cardContainer,
                      { 
                          opacity: menuOpacity, 
                          transform: [{ scale: menuScale }], 
                          zIndex: 1 
                      },
                    ]}
                    pointerEvents={isSearchActive ? 'none' : 'auto'}
                  >
                    <View style={styles.barTabContent}>
                      <TouchableOpacity style={styles.menuItem} onPress={() => setActiveView('tabs')}>
                          <Ionicons name="copy-outline" size={24} color="#fff" />
                          <Text style={styles.menuLabel}>Tabs</Text>
                      </TouchableOpacity>

                      <TouchableOpacity style={styles.menuItem} onPress={() => setActiveView('history')}>
                          <Ionicons name="time-outline" size={24} color="#fff" />
                          <Text style={styles.menuLabel}>History</Text>
                      </TouchableOpacity>

                      <TouchableOpacity style={styles.menuItem} onPress={() => setActiveView('settings')}>
                          <Ionicons name="settings-outline" size={24} color="#fff" />
                          <Text style={styles.menuLabel}>Settings</Text>
                      </TouchableOpacity>

                      <TouchableOpacity style={styles.menuItem} onPress={goHome}>
                          <Ionicons 
                              name="home-outline" 
                              size={24} 
                              color="#fff" 
                          />
                          <Text style={styles.menuLabel}>Home</Text>
                      </TouchableOpacity>
                    </View>
                  </Animated.View>

                  {/* --- SEARCH CARD --- */}
                  <Animated.View
                    style={[
                      styles.cardContainer,
                      { 
                          opacity: searchOpacity, 
                          transform: [{ translateY: searchTranslateY }, { scale: searchScale }], 
                          zIndex: 2 
                      },
                    ]}
                    pointerEvents={isSearchActive ? 'auto' : 'none'}
                  >
                    <View style={styles.barTabContent}>
                      <View style={styles.navButtons}>
                          <TouchableOpacity
                              disabled={!canGoBack || !activeUrl}
                              onPress={() => webViewRef.current?.goBack()}
                              style={[styles.btn, (!canGoBack || !activeUrl) && styles.disabledBtn]}
                          >
                              <Ionicons name="chevron-back" size={24} color="#fff" />
                          </TouchableOpacity>
                          
                          <TouchableOpacity
                              disabled={!canGoForward || !activeUrl}
                              onPress={() => webViewRef.current?.goForward()}
                              style={[styles.btn, (!canGoForward || !activeUrl) && styles.disabledBtn]}
                          >
                              <Ionicons name="chevron-forward" size={24} color="#fff" />
                          </TouchableOpacity>
                      </View>

                      <TextInput
                        style={[styles.urlInput, isIncognito && styles.incognitoInput]}
                        value={inputUrl}
                        onChangeText={setInputUrl}
                        onSubmitEditing={handleGoPress}
                        placeholder={isIncognito ? "Incognito Search" : "Search"}
                        placeholderTextColor="#aaa"
                        autoCapitalize="none"
                        keyboardType="url"
                        returnKeyType="go"
                        selectTextOnFocus
                        onFocus={() => setIsInputFocused(true)}
                        onBlur={() => setIsInputFocused(false)}
                      />

                      <View style={styles.actionButtons}>
                        {isLoading ? (
                          <ActivityIndicator size="small" color="#fff" />
                        ) : isInputFocused ? (
                          <TouchableOpacity onPress={handleGoPress}>
                             <Ionicons name="search" size={22} color="#fff" />
                          </TouchableOpacity>
                        ) : (
                          <TouchableOpacity 
                            disabled={!activeUrl}
                            onPress={() => webViewRef.current?.reload()}
                            style={!activeUrl && styles.disabledBtn}
                          >
                             <Ionicons name="refresh" size={22} color="#fff" />
                          </TouchableOpacity>
                        )}
                      </View>
                    </View>
                  </Animated.View>

                </View>
              </View>
            </View>
          </Animated.View>
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0,
  },
  incognitoContainer: {
    backgroundColor: '#1a1a1a',
  },
  webViewContainer: {
    flex: 1,
    width: '100%',
    zIndex: 1,
    backgroundColor: '#000',
  },
  homeContainer: {
    flex: 1,
    backgroundColor: '#000',
    justifyContent: 'center',
    alignItems: 'center',
  },
  homeText: {
    color: '#fff',
    fontSize: 28,
    fontWeight: 'bold',
    letterSpacing: 1,
    opacity: 0.8,
  },
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.8)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  floatingLayer: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    top: 0,
    zIndex: 2,
    justifyContent: 'flex-end',
  },
  bottomAreaContainer: {
    paddingHorizontal: 10,
    paddingBottom: Platform.OS === 'ios' ? 25 : 15, 
    width: '100%',
    alignItems: 'center',
  },
  floatingBarShadowWrapper: {
    width: '100%',
    borderRadius: 40,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.6,
    shadowRadius: 15,
    elevation: 10,
    backgroundColor: 'transparent',
  },
  floatingBarGlass: {
    borderRadius: 40,
    overflow: 'hidden',
    width: '100%',
    height: BAR_HEIGHT,
    backgroundColor: 'rgba(30, 30, 30, 0.9)', 
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)', 
  },
  incognitoGlass: {
    backgroundColor: 'rgba(45, 15, 55, 0.9)', 
    borderColor: 'rgba(200, 100, 255, 0.3)', 
  },
  gestureArea: {
    width: '100%',
    height: '100%',
  },
  cardContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'center',
  },
  barTabContent: {
    height: '100%',
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 5, 
    justifyContent: 'space-between', 
  },
  navButtons: {
    flexDirection: 'row',
    marginRight: 4, 
  },
  actionButtons: {
    marginLeft: 8,
    width: 30,
    alignItems: 'center',
  },
  btn: {
    paddingHorizontal: 8, 
    paddingVertical: 8,
  },
  disabledBtn: {
    opacity: 0.3,
  },
  urlInput: {
    flex: 1,
    height: 44,
    backgroundColor: 'rgba(255,255,255,0.1)', 
    borderRadius: 22,
    paddingHorizontal: 16,
    fontSize: 16,
    color: '#fff',
    marginHorizontal: 5,
  },
  incognitoInput: {
      backgroundColor: 'rgba(200, 150, 255, 0.1)', 
      borderColor: '#553366',
      borderWidth: 1,
  },
  menuItem: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  menuLabel: {
    color: '#fff',
    fontSize: 10,
    marginTop: 3,
    fontWeight: '600',
  },
  
  // --- Sheet Styles ---
  overlayBackdrop: {
      ...StyleSheet.absoluteFillObject,
      backgroundColor: 'rgba(0,0,0,0.5)',
      zIndex: 100,
      justifyContent: 'flex-end',
  },
  backdropTouchArea: {
      flex: 1,
  },
  sheetContainer: {
      height: '85%',
      width: '100%',
      backgroundColor: '#1c1c1e',
      borderTopLeftRadius: 25,
      borderTopRightRadius: 25,
      overflow: 'hidden',
      shadowColor: '#000',
      shadowOffset: { width: 0, height: -5 },
      shadowOpacity: 0.3,
      shadowRadius: 10,
      elevation: 20,
  },
  sheetHeader: {
      paddingHorizontal: 20,
      paddingTop: 15,
      paddingBottom: 15,
      backgroundColor: '#2c2c2e', 
      alignItems: 'center',
  },
  sheetHandle: {
      width: 40,
      height: 4,
      backgroundColor: '#555',
      borderRadius: 2,
      marginBottom: 10,
  },
  sheetHeaderRow: {
      flexDirection: 'row',
      width: '100%',
      justifyContent: 'space-between',
      alignItems: 'center',
  },
  sheetTitle: {
      fontSize: 22,
      fontWeight: 'bold',
      color: '#fff',
  },
  closeIconBtn: {
      padding: 5,
  },
  historyItem: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingVertical: 15,
      borderBottomWidth: 1,
      borderBottomColor: '#2c2c2e',
  },
  historyTitle: {
      color: '#fff',
      fontSize: 16,
      marginBottom: 4,
  },
  historyUrl: {
      color: '#888',
      fontSize: 12,
  },
  emptyText: {
      color: '#666',
      textAlign: 'center',
      marginTop: 50,
      fontSize: 16,
  },
  
  // --- Tab Styles ---
  tabRowContainer: {
      marginBottom: 15,
      width: '100%',
      justifyContent: 'center',
  },
  deleteLayer: {
      ...StyleSheet.absoluteFillObject,
      backgroundColor: '#ff3b30', 
      borderRadius: 18,
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'flex-end',
      paddingRight: 20,
  },
  tabCardWrapper: {
      backgroundColor: 'transparent',
  },
  tabCard: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: '#3A3A3C',
      padding: 15,
      borderRadius: 18, 
      height: 80, 
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.2,
      shadowRadius: 4,
      elevation: 3,
  },
  activeTabCard: {
      borderWidth: 1,
      borderColor: '#007AFF',
      backgroundColor: '#444446',
  },
  faviconContainer: {
      width: 40,
      height: 40,
      borderRadius: 20,
      backgroundColor: '#555',
      alignItems: 'center',
      justifyContent: 'center',
      marginRight: 15,
  },
  faviconText: {
      color: '#fff',
      fontWeight: 'bold',
      fontSize: 18,
  },
  tabTextContainer: {
      flex: 1,
      justifyContent: 'center',
  },
  tabTitleText: {
      color: '#fff',
      fontSize: 16,
      fontWeight: 'bold',
      marginBottom: 4,
  },
  tabUrlText: {
      color: '#aaa',
      fontSize: 12,
  },
  
  // --- Floating New Tab Button ---
  fabContainer: {
      position: 'absolute',
      bottom: 40,
      right: 25,
  },
  fabButton: {
      width: 56,
      height: 56,
      borderRadius: 28,
      backgroundColor: 'rgba(60, 60, 60, 0.9)',
      borderWidth: 1,
      borderColor: 'rgba(255,255,255,0.1)',
      alignItems: 'center',
      justifyContent: 'center',
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.3,
      shadowRadius: 8,
      elevation: 6,
  },
  
  settingRow: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      paddingVertical: 15,
      borderBottomWidth: 1,
      borderBottomColor: '#2c2c2e',
  },
  settingText: {
      color: '#fff',
      fontSize: 17,
  },
  settingBtn: {
      backgroundColor: '#333',
      paddingHorizontal: 15,
      paddingVertical: 8,
      borderRadius: 8,
  },
});