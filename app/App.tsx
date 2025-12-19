import {
    Nunito_400Regular,
    Nunito_600SemiBold,
    Nunito_700Bold,
    Nunito_800ExtraBold,
    useFonts
} from '@expo-google-fonts/nunito';
import { Ionicons } from '@expo/vector-icons';
import React, { useEffect, useRef, useState } from 'react';
import {
    ActivityIndicator,
    Animated,
    Dimensions,
    FlatList,
    Image,
    Keyboard,
    LayoutAnimation,
    Modal,
    PanResponder,
    Platform,
    ScrollView,
    StatusBar,
    StyleSheet,
    Switch,
    Text,
    TextInput,
    TouchableOpacity,
    TouchableWithoutFeedback,
    UIManager,
    View
} from 'react-native';
import { WebView } from 'react-native-webview';

// --- Global Constants ---
const APP_VERSION = "0.1.5";

// Enable LayoutAnimation
if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
    UIManager.setLayoutAnimationEnabledExperimental(true);
}

// --- Configuration ---
const BAR_HEIGHT = 70;
const SWAP_DISTANCE = 80; 
const HIDDEN_TRANSLATE_Y = 110; 
const SCREEN_WIDTH = Dimensions.get('window').width;
const SCREEN_HEIGHT = Dimensions.get('window').height;

const SNAP_CLOSED = 0;
const SNAP_DEFAULT = SCREEN_HEIGHT * 0.6;
const SNAP_FULL = SCREEN_HEIGHT * 0.95;

// --- Color Logic Helper ---
// Mixes an accent color with a dark base to create muted, adaptive themes
const generateAdaptiveTheme = (accentHex: string) => {
    // 1. Parse Hex to RGB
    const r = parseInt(accentHex.substr(1, 2), 16);
    const g = parseInt(accentHex.substr(3, 2), 16);
    const b = parseInt(accentHex.substr(5, 2), 16);

    // 2. Mix with Dark Base (#121212 - rgb(18,18,18))
    // We keep 85% base, 15% accent for a subtle "muted" tint
    const mix = (base: number, channel: number, strength: number) => {
        return Math.floor(base * (1 - strength) + channel * strength);
    };

    // Background: Very dark, subtle tint
    const bgR = mix(18, r, 0.15); 
    const bgG = mix(18, g, 0.15);
    const bgB = mix(18, b, 0.15);
    const bg = `#${bgR.toString(16).padStart(2,'0')}${bgG.toString(16).padStart(2,'0')}${bgB.toString(16).padStart(2,'0')}`;

    // Surface/Card: Slightly lighter, slightly more color
    const cardR = mix(35, r, 0.2); 
    const cardG = mix(35, g, 0.2);
    const cardB = mix(35, b, 0.2);
    const card = `#${cardR.toString(16).padStart(2,'0')}${cardG.toString(16).padStart(2,'0')}${cardB.toString(16).padStart(2,'0')}`;

    return {
        bg: bg,
        surface: card,
        card: card,
        text: '#eaeaea',
        textSec: '#aaaaaa',
        glass: bg + 'F5', // High opacity for glass
        glassBorder: accentHex + '30',
        sheetHeader: card,
        inputBg: '#ffffff15',
        placeholder: '#888',
    };
};

// --- Interfaces ---
interface HistoryItem { 
    id: string; 
    url: string; 
    title: string; 
    timestamp: number; 
}
interface TabItem { 
    id: string; 
    url: string | null; 
    title: string; 
    showLogo: boolean; 
}

// --- Colors ---
const COLORS = {
    light: {
        bg: '#f2f2f7',
        surface: '#ffffff',
        glass: 'rgba(255, 255, 255, 0.95)',
        glassBorder: 'rgba(0,0,0,0.1)',
        text: '#000000',
        textSec: '#666666',
        card: '#ffffff',
        sheetHeader: '#e5e5ea',
        inputBg: 'rgba(0,0,0,0.05)',
        placeholder: '#999',
    },
    dark: {
        bg: '#000000',
        surface: '#1c1c1e',
        glass: 'rgba(30, 30, 30, 0.95)',
        glassBorder: 'rgba(255,255,255,0.1)',
        text: '#ffffff',
        textSec: '#888888',
        card: '#2c2c2e',
        sheetHeader: '#252527',
        inputBg: 'rgba(255,255,255,0.1)',
        placeholder: '#aaa',
    }
};

const ACCENTS = [
    '#007AFF', // Blue
    '#98989D', // Gray (Lightened slightly to avoid black crush)
    '#FF3B30', // Red
    '#34C759', // Green
    '#5856D6', // Purple
    '#FF9500', // Orange
];

const SEARCH_ENGINES = [
    { name: 'Google', url: 'https://www.google.com/search?q=', icon: 'logo-google' },
    { name: 'DuckDuckGo', url: 'https://duckduckgo.com/?q=', icon: 'shield-checkmark-outline' },
    { name: 'Bing', url: 'https://www.bing.com/search?q=', icon: 'globe-outline' },
    { name: 'Ecosia', url: 'https://www.ecosia.org/search?q=', icon: 'leaf-outline' },
];

const HISTORY_RANGES = [
    { label: 'Last Hour', ms: 3600 * 1000 },
    { label: 'Last 24 Hours', ms: 24 * 3600 * 1000 },
    { label: 'Last 7 Days', ms: 7 * 24 * 3600 * 1000 },
    { label: 'Last 4 Weeks', ms: 28 * 24 * 3600 * 1000 },
    { label: 'All Time', ms: -1 },
];

// --- Helper ---
const getDisplayHost = (url: string | null) => {
    if (!url) return '';
    try {
        const parsed = new URL(url);
        return parsed.hostname;
    } catch (e) {
        return url;
    }
};

const getFaviconUrl = (url: string | null) => {
    if (!url) return null;
    try {
        const domain = new URL(url).hostname;
        return `https://www.google.com/s2/favicons?domain=${domain}&sz=128`;
    } catch (e) {
        return null;
    }
};

// --- Component: Swipeable Tab Row ---
const SwipeableTabRow = ({ item, isActive, onPress, onDelete, onRename, theme, accent, radius, height, margin, fontScale }: any) => {
  const translateX = useRef(new Animated.Value(0)).current;
  const itemHeight = useRef(new Animated.Value(height)).current; 
  const opacity = useRef(new Animated.Value(1)).current;
  
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
      Animated.timing(itemHeight, { toValue: height, duration: 200, useNativeDriver: false }).start();
  }, [height]);

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) => {
        if (isDeleting) return false;
        return Math.abs(gestureState.dx) > 20 && Math.abs(gestureState.dx) > Math.abs(gestureState.dy);
      },
      onPanResponderMove: (_, gestureState) => {
        if (gestureState.dx < 0) {
            translateX.setValue(gestureState.dx);
        }
      },
      onPanResponderRelease: (_, gestureState) => {
        if (gestureState.dx < -SCREEN_WIDTH * 0.3) {
          setIsDeleting(true);
          Animated.parallel([
            Animated.timing(translateX, { toValue: -SCREEN_WIDTH, duration: 200, useNativeDriver: false }),
            Animated.timing(itemHeight, { toValue: 0, duration: 200, useNativeDriver: false }),
            Animated.timing(opacity, { toValue: 0, duration: 200, useNativeDriver: false }),
          ]).start(() => onDelete());
        } else {
          Animated.spring(translateX, { toValue: 0, useNativeDriver: false }).start();
        }
      },
    })
  ).current;

  const iconScale = translateX.interpolate({
      inputRange: [-100, -50, 0],
      outputRange: [1.2, 0.5, 0],
      extrapolate: 'clamp'
  });

  return (
    <Animated.View style={[styles.tabRowContainer, { height: itemHeight, opacity, marginBottom: margin }]}>
      <View style={styles.deleteLayer}>
         <Animated.View style={{ transform: [{ scale: iconScale }] }}>
            <Ionicons name="trash" size={28} color="#ff3b30" />
         </Animated.View>
      </View>

      <Animated.View
        style={[styles.tabCardWrapper, { transform: [{ translateX }] }]}
        {...panResponder.panHandlers}
      >
        <TouchableOpacity
          activeOpacity={0.9} 
          style={[
              styles.tabCard, 
              { backgroundColor: theme.card, borderRadius: radius, height: '100%' },
              isActive && { borderWidth: 2, borderColor: accent, backgroundColor: theme.surface }
          ]}
          onPress={onPress}
        >
          <View style={[styles.faviconContainer, { backgroundColor: isActive ? accent : '#555' }]}>
            {item.showLogo && item.url ? (
                <Image 
                    source={{ uri: getFaviconUrl(item.url) || '' }} 
                    style={{ width: 32, height: 32, resizeMode:'contain' }}
                />
            ) : (
                <Text style={[styles.faviconText, { fontFamily: 'Nunito_800ExtraBold', fontSize: (item.url ? 22 : 18) * fontScale }]}>
                   {item.url ? (item.title ? item.title.charAt(0).toUpperCase() : 'N') : 'mi.'}
                </Text>
            )}
          </View>

          <View style={styles.tabTextContainer}>
            <View style={{flexDirection:'row', alignItems:'center'}}>
                <Text style={[styles.tabTitleText, { color: theme.text, fontFamily: 'Nunito_700Bold', fontSize: 16 * fontScale }]} numberOfLines={1}>
                {item.title || 'New Tab'}
                </Text>
            </View>
            <Text style={[styles.tabUrlText, { color: theme.textSec, fontFamily: 'Nunito_600SemiBold', fontSize: 12 * fontScale }]} numberOfLines={1}>
              {getDisplayHost(item.url) || 'Home'} 
            </Text>
          </View>
          
          <TouchableOpacity onPress={onRename} style={[styles.pencilBtn, { borderColor: theme.textSec }]}>
             <Ionicons name="pencil" size={14} color={theme.text} />
          </TouchableOpacity>
          
        </TouchableOpacity>
      </Animated.View>
    </Animated.View>
  );
};

// --- Component: Swipeable History Row ---
const SwipeableHistoryRow = ({ item, onPress, onDelete, theme, accent, radius, height, margin, fontScale }: any) => {
    const translateX = useRef(new Animated.Value(0)).current;
    const itemHeight = useRef(new Animated.Value(height)).current; 
    const opacity = useRef(new Animated.Value(1)).current;
    const [isDeleting, setIsDeleting] = useState(false);

    useEffect(() => {
        Animated.timing(itemHeight, { toValue: height, duration: 200, useNativeDriver: false }).start();
    }, [height]);
  
    const panResponder = useRef(
      PanResponder.create({
        onMoveShouldSetPanResponder: (_, gestureState) => {
          if (isDeleting) return false;
          return gestureState.dx < -10 && Math.abs(gestureState.dx) > Math.abs(gestureState.dy);
        },
        onPanResponderMove: (_, gestureState) => {
          if (gestureState.dx < 0) translateX.setValue(gestureState.dx);
        },
        onPanResponderRelease: (_, gestureState) => {
          if (gestureState.dx < -SCREEN_WIDTH * 0.3) {
            setIsDeleting(true);
            Animated.parallel([
              Animated.timing(translateX, { toValue: -SCREEN_WIDTH, duration: 200, useNativeDriver: false }),
              Animated.timing(itemHeight, { toValue: 0, duration: 200, useNativeDriver: false }),
              Animated.timing(opacity, { toValue: 0, duration: 200, useNativeDriver: false }),
            ]).start(() => onDelete());
          } else {
            Animated.spring(translateX, { toValue: 0, useNativeDriver: false }).start();
          }
        },
      })
    ).current;
  
    const iconScale = translateX.interpolate({
        inputRange: [-100, -50, 0],
        outputRange: [1.2, 0.5, 0],
        extrapolate: 'clamp'
    });
  
    const timeStr = new Date(item.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    return (
      <Animated.View style={{ marginBottom: margin, height: itemHeight, opacity, justifyContent: 'center' }}>
        <View style={{position:'absolute', right: 20, top:0, bottom:0, justifyContent:'center'}}>
           <Animated.View style={{ transform: [{ scale: iconScale }] }}>
              <Ionicons name="trash" size={20} color="#ff3b30" />
           </Animated.View>
        </View>
  
        <Animated.View
          style={{ transform: [{ translateX }], backgroundColor: theme.card, borderRadius: radius, height: '100%', justifyContent:'center' }}
          {...panResponder.panHandlers}
        >
          <TouchableOpacity
            activeOpacity={0.9} 
            style={[styles.historyItem, { backgroundColor: theme.card, height: '100%' }]} 
            onPress={onPress}
          >
            <View style={[styles.historyIconBox, {width: 28, height: 28}]}><Ionicons name="time-outline" size={16} color="#555" /></View>
            <View style={{flex:1}}>
                <Text style={[styles.historyTitle, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 14 * fontScale }]} numberOfLines={1}>{item.title || "Untitled"}</Text>
                <View style={{flexDirection:'row', alignItems:'center'}}>
                    <Text style={{color: accent, fontSize: 10 * fontScale, marginRight: 6, fontFamily: 'Nunito_700Bold' }}>{timeStr}</Text>
                    <Text style={[styles.historyUrl, { color: theme.textSec, fontFamily: 'Nunito_400Regular', fontSize: 10 * fontScale }]} numberOfLines={1}>{getDisplayHost(item.url)}</Text>
                </View>
            </View>
            <Ionicons name="chevron-forward" size={14} color={theme.textSec} />
          </TouchableOpacity>
        </Animated.View>
      </Animated.View>
    );
  };

export default function App() {
  
  let [fontsLoaded] = useFonts({
    Nunito_400Regular, Nunito_600SemiBold, Nunito_700Bold, Nunito_800ExtraBold,
  });

  const [inputUrl, setInputUrl] = useState(''); 
  const [activeUrl, setActiveUrl] = useState<string | null>(null);
  
  const [canGoBack, setCanGoBack] = useState(false);
  const [canGoForward, setCanGoForward] = useState(false);
  const canGoBackRef = useRef(false);
  const canGoForwardRef = useRef(false);

  const [isLoading, setIsLoading] = useState(false);

  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [tabs, setTabs] = useState<TabItem[]>([{ id: '1', url: null, title: 'New Tab', showLogo: true }]);
  const [activeTabId, setActiveTabId] = useState('1');

  const [activeView, setActiveView] = useState<'none' | 'tabs' | 'history' | 'settings'>('none');
  const [isSearchActive, setIsSearchActive] = useState(true);
  const isSearchActiveRef = useRef(true); 
  const [isInputFocused, setIsInputFocused] = useState(false);

  // Settings
  const [themeMode, setThemeMode] = useState<'light'|'dark'|'adaptive'>('dark');
  const [accentColor, setAccentColor] = useState('#007AFF');
  const [searchEngineIndex, setSearchEngineIndex] = useState(0);
  const [isSearchEngineOpen, setIsSearchEngineOpen] = useState(false);
  const [isClearHistoryOpen, setIsClearHistoryOpen] = useState(false);
  
  // Cosmetic Settings
  const [cornerRadius, setCornerRadius] = useState(22); 
  const [uiPadding, setUiPadding] = useState<'compact'|'normal'|'airy'>('normal');
  const [fontScale, setFontScale] = useState(1); 

  // Functional Settings
  const [desktopMode, setDesktopMode] = useState(false);
  const [jsEnabled, setJsEnabled] = useState(true);
  const [httpsOnly, setHttpsOnly] = useState(false);
  const [blockCookies, setBlockCookies] = useState(false);
  const [readerMode, setReaderMode] = useState(false); // Mock reader toggle

  // Rename Modal
  const [isRenameModalVisible, setIsRenameModalVisible] = useState(false);
  const [tabToRename, setTabToRename] = useState<string | null>(null);
  const [renameText, setRenameText] = useState('');
  const [renameShowLogo, setRenameShowLogo] = useState(true);

  // --- Dynamic Theme Calculation ---
  const getTheme = () => {
      if (themeMode === 'light') return COLORS.light;
      if (themeMode === 'dark') return COLORS.dark;
      // Adaptive
      return generateAdaptiveTheme(accentColor);
  };

  const theme = getTheme();

  // Dynamic Metrics based on UI Spacing
  const getTabHeight = () => {
      switch(uiPadding) {
          case 'compact': return 60;
          case 'normal': return 70;
          case 'airy': return 85;
      }
  };
  const getHistoryHeight = () => {
      switch(uiPadding) {
          case 'compact': return 40;
          case 'normal': return 50;
          case 'airy': return 65;
      }
  };
  const getMargin = () => {
      switch(uiPadding) {
          case 'compact': return 8;
          case 'normal': return 15;
          case 'airy': return 25;
      }
  };

  // --- Animation Refs ---
  const scrollTranslateY = useRef(new Animated.Value(0)).current;
  const currentScrollTrans = useRef(0); 
  const lastScrollY = useRef(0);
  
  const animVal = useRef(new Animated.Value(0)).current;
  const horizontalDrag = useRef(new Animated.Value(0)).current;

  const overlayHeightAnim = useRef(new Animated.Value(SNAP_CLOSED)).current;
  const currentOverlayHeight = useRef(SNAP_CLOSED);
  const keyboardHeight = useRef(new Animated.Value(0)).current;

  // Logo Refs
  const logoScale = useRef(new Animated.Value(1)).current;
  const logoPan = useRef(new Animated.ValueXY({ x: 0, y: 0 })).current;

  const webViewRef = useRef<WebView>(null);

  // --- Effects ---
  useEffect(() => { isSearchActiveRef.current = isSearchActive; }, [isSearchActive]);

  useEffect(() => {
    if (activeView !== 'none') {
      setIsSearchEngineOpen(false);
      setIsClearHistoryOpen(false);
      Animated.spring(overlayHeightAnim, { toValue: SNAP_DEFAULT, tension: 60, friction: 9, useNativeDriver: false }).start();
      currentOverlayHeight.current = SNAP_DEFAULT;
    } else {
      Animated.timing(overlayHeightAnim, { toValue: SNAP_CLOSED, duration: 200, useNativeDriver: false }).start();
      currentOverlayHeight.current = SNAP_CLOSED;
    }
  }, [activeView]);

  useEffect(() => {
    const showSub = Keyboard.addListener('keyboardDidShow', (e) => {
      Animated.timing(keyboardHeight, { toValue: e.endCoordinates.height, duration: 150, useNativeDriver: false }).start();
    });
    const hideSub = Keyboard.addListener('keyboardDidHide', (e) => {
      Animated.timing(keyboardHeight, { toValue: 0, duration: 150, useNativeDriver: false }).start();
    });
    return () => { showSub.remove(); hideSub.remove(); };
  }, []);

  const handleGoPress = () => {
    Keyboard.dismiss();
    let text = inputUrl.trim();
    if (!text) return; 
    
    // HTTPS Only check
    if (httpsOnly && !text.startsWith('http')) {
        text = 'https://' + text.replace(/^http:\/\//, '');
    }

    const hasSpace = text.includes(' ');
    const hasDot = text.includes('.');
    let destination = '';
    
    if (text.startsWith('http')) {
        destination = text;
    } else if (!hasSpace && hasDot) {
        destination = `https://${text}`;
    } else {
        const engineUrl = SEARCH_ENGINES[searchEngineIndex].url;
        destination = `${engineUrl}${encodeURIComponent(text)}`;
    }
    setActiveUrl(destination);
    snapToSearch(); 
  };

  const deleteHistory = (milliseconds: number) => {
      if (milliseconds === -1) setHistory([]);
      else {
          const cutoff = Date.now() - milliseconds;
          setHistory(prev => prev.filter(item => item.timestamp < cutoff));
      }
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
      setIsClearHistoryOpen(false);
  };

  const deleteHistoryItem = (id: string) => {
      setHistory(prev => prev.filter(item => item.id !== id));
  };

  const addNewTab = () => {
    const newId = Date.now().toString();
    const newTab = { id: newId, url: null, title: 'New Tab', showLogo: true };
    setTabs(prev => [newTab, ...prev]); 
    setActiveTabId(newId);
    setActiveUrl(null);
    setInputUrl('');
  };

  const deleteTab = (idToDelete: string) => {
        const indexToDelete = tabs.findIndex(t => t.id === idToDelete);
        const newTabs = tabs.filter(t => t.id !== idToDelete);
        
        LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
        
        if (newTabs.length === 0) {
            const freshId = Date.now().toString();
            setTabs([{ id: freshId, url: null, title: 'New Tab', showLogo: true }]);
            setActiveTabId(freshId);
            setActiveUrl(null);
            if (activeView === 'none') setInputUrl('');
        } else {
            setTabs(newTabs);
            if (activeTabId === idToDelete) {
                const nextIndex = Math.min(indexToDelete, newTabs.length - 1);
                const nextTab = newTabs[nextIndex];
                setActiveTabId(nextTab.id);
                setActiveUrl(nextTab.url);
                setInputUrl(getDisplayHost(nextTab.url));
            }
        }
  };

  const openRenameModal = (tabId: string, currentTitle: string, currentShowLogo: boolean) => {
      setTabToRename(tabId);
      setRenameText(currentTitle);
      setRenameShowLogo(currentShowLogo);
      setIsRenameModalVisible(true);
  };

  const saveRenameTab = () => {
      if (tabToRename) {
          setTabs(prev => prev.map(t => 
              t.id === tabToRename ? { ...t, title: renameText || "New Tab", showLogo: renameShowLogo } : t
          ));
      }
      setIsRenameModalVisible(false);
      setTabToRename(null);
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
      setActiveView('none'); 
      const currentTab = tabs.find(t => t.id === activeTabId);
        if (currentTab && currentTab.url !== activeUrl) {
            setActiveUrl(currentTab.url);
            setInputUrl(getDisplayHost(currentTab.url));
        }
        snapToSearch(); 
  };

  const snapToSearch = () => {
    setIsSearchActive(true);
    Animated.spring(animVal, { toValue: 0, tension: 60, friction: 9, useNativeDriver: true }).start();
  };

  const showBar = () => {
    currentScrollTrans.current = 0;
    Animated.timing(scrollTranslateY, { toValue: 0, duration: 200, useNativeDriver: false }).start();
  };

  const hideBar = () => {
      currentScrollTrans.current = HIDDEN_TRANSLATE_Y;
      Animated.timing(scrollTranslateY, { toValue: HIDDEN_TRANSLATE_Y, duration: 250, useNativeDriver: false }).start();
  };

  const addToHistory = (url: string, title: string = 'Page') => {
    if (!url) return; 
    setHistory(prev => [{ id: Date.now().toString(), url, title, timestamp: Date.now() }, ...prev]);
    setTabs(prev => prev.map(tab => tab.id === activeTabId ? { ...tab, url, title } : tab));
  };

  // --- Pan Responders ---
  const logoResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        Animated.spring(logoScale, { toValue: 1.2, useNativeDriver: false }).start();
        logoPan.setOffset({ x: (logoPan.x as any)._value, y: (logoPan.y as any)._value });
        logoPan.setValue({ x: 0, y: 0 });
      },
      onPanResponderMove: Animated.event([null, { dx: logoPan.x, dy: logoPan.y }], { useNativeDriver: false }),
      onPanResponderRelease: () => {
        logoPan.flattenOffset();
        Animated.spring(logoScale, { toValue: 1, friction: 3, tension: 40, useNativeDriver: false }).start();
        Animated.spring(logoPan, { toValue: { x: 0, y: 0 }, friction: 4, tension: 80, useNativeDriver: false }).start();
      }
    })
  ).current;

  // --- Search Bar PanResponder ---
  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) => Math.abs(gestureState.dy) > 5 || Math.abs(gestureState.dx) > 5,
      onPanResponderGrant: () => {
        animVal.stopAnimation();
        scrollTranslateY.stopAnimation();
      },
      onPanResponderMove: (_, gestureState) => {
        const { dy, dx } = gestureState;
        
        if (isSearchActiveRef.current) {
             if (Math.abs(dx) > Math.abs(dy)) {
                 horizontalDrag.setValue(dx);
             } else {
                 if (dy < 0) animVal.setValue(dy); 
                 else scrollTranslateY.setValue(dy);
             }
        } else {
             if (dy > 0) animVal.setValue(-SWAP_DISTANCE + dy);
        }
      },
      onPanResponderRelease: (_, gestureState) => {
        const { dy, dx } = gestureState;

        if (isSearchActiveRef.current) {
            // Horizontal Gestures
            if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) {
                if (dx > 0 && canGoBackRef.current) webViewRef.current?.goBack();
                else if (dx < 0 && canGoForwardRef.current) webViewRef.current?.goForward();
                
                Animated.spring(horizontalDrag, { toValue: 0, useNativeDriver: true }).start();
                
                Animated.parallel([
                    Animated.spring(animVal, { toValue: 0, useNativeDriver: true }),
                    Animated.spring(scrollTranslateY, { toValue: 0, useNativeDriver: false })
                ]).start();
                return;
            }
            
            Animated.spring(horizontalDrag, { toValue: 0, useNativeDriver: true }).start();

            // Vertical Gestures
            if (dy < -50) {
                setIsSearchActive(false);
                Animated.spring(animVal, { toValue: -SWAP_DISTANCE, tension: 60, friction: 9, useNativeDriver: true }).start();
            } else if (dy > 50) {
                Animated.spring(animVal, { toValue: 0, useNativeDriver: true }).start();
                hideBar();
            } else {
                Animated.parallel([
                    Animated.spring(animVal, { toValue: 0, useNativeDriver: true }),
                    Animated.spring(scrollTranslateY, { toValue: 0, useNativeDriver: false })
                ]).start();
            }
        } else {
            if (dy > 50) {
                setIsSearchActive(true);
                Animated.spring(animVal, { toValue: 0, tension: 60, friction: 9, useNativeDriver: true }).start();
            } else {
                Animated.spring(animVal, { toValue: -SWAP_DISTANCE, tension: 60, friction: 9, useNativeDriver: true }).start();
            }
        }
      },
    })
  ).current;

  const recallPanResponder = useRef(
      PanResponder.create({
          onStartShouldSetPanResponder: () => true,
          onMoveShouldSetPanResponder: () => true,
          onPanResponderMove: (_, gestureState) => {
              if (gestureState.dy < 0) {
                  scrollTranslateY.setValue(Math.max(0, HIDDEN_TRANSLATE_Y + gestureState.dy));
              }
          },
          onPanResponderRelease: (_, gestureState) => {
              if (gestureState.dy < -20 || gestureState.vy < -0.5) showBar();
              else Animated.spring(scrollTranslateY, { toValue: HIDDEN_TRANSLATE_Y, useNativeDriver: false }).start(() => { currentScrollTrans.current = HIDDEN_TRANSLATE_Y; });
          }
      })
  ).current;

  const sheetPanResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) => Math.abs(gestureState.dy) > 5,
      onPanResponderGrant: () => overlayHeightAnim.stopAnimation((val) => { currentOverlayHeight.current = val; }),
      onPanResponderMove: (_, gestureState) => {
         const newHeight = currentOverlayHeight.current - gestureState.dy;
         overlayHeightAnim.setValue(Math.min(newHeight, SNAP_FULL));
      },
      onPanResponderRelease: (_, gestureState) => {
        const { dy, vy } = gestureState;
        const finalHeight = currentOverlayHeight.current - dy;
        let target = SNAP_DEFAULT;
        if (finalHeight > (SNAP_FULL + SNAP_DEFAULT) / 2) target = SNAP_FULL;
        else if (finalHeight > SNAP_DEFAULT * 0.7) target = SNAP_DEFAULT;
        else target = SNAP_CLOSED;

        if (vy < -1) target = SNAP_FULL; 
        if (vy > 1) target = finalHeight > SNAP_DEFAULT ? SNAP_DEFAULT : SNAP_CLOSED;

        if (target === SNAP_CLOSED) closeOverlay();
        else {
            Animated.spring(overlayHeightAnim, { toValue: target, tension: 50, friction: 8, useNativeDriver: false }).start();
            currentOverlayHeight.current = target;
        }
      }
    })
  ).current;

  // --- Interpolations ---
  const searchPillTranslateY = animVal; 
  const searchPillOpacity = animVal.interpolate({ inputRange: [-SWAP_DISTANCE, -SWAP_DISTANCE/2, 0], outputRange: [0, 0, 1], extrapolate: 'clamp' });
  const menuPillScale = animVal.interpolate({ inputRange: [-SWAP_DISTANCE, 0], outputRange: [1, 0.9], extrapolate: 'clamp' });
  const menuPillOpacity = animVal.interpolate({ inputRange: [-SWAP_DISTANCE, -10, 0], outputRange: [1, 0, 0], extrapolate: 'clamp' });
  const recallOpacity = scrollTranslateY.interpolate({ inputRange: [0, HIDDEN_TRANSLATE_Y - 20, HIDDEN_TRANSLATE_Y], outputRange: [0, 0, 1], extrapolate: 'clamp' });

  // Arrow Animations
  const backArrowOpacity = horizontalDrag.interpolate({ inputRange: [0, 50], outputRange: [0, 1], extrapolate: 'clamp' });
  const forwardArrowOpacity = horizontalDrag.interpolate({ inputRange: [-50, 0], outputRange: [1, 0], extrapolate: 'clamp' });
  const contentOpacity = horizontalDrag.interpolate({ inputRange: [-50, 0, 50], outputRange: [0, 1, 0], extrapolate: 'clamp' });

  const handleNavigationStateChange = (navState: any) => {
    setCanGoBack(navState.canGoBack);
    canGoBackRef.current = navState.canGoBack;
    setCanGoForward(navState.canGoForward);
    canGoForwardRef.current = navState.canGoForward;
    
    if (!navState.loading) { setIsLoading(false); addToHistory(navState.url, navState.title); }
    else setIsLoading(true);
    if (!isInputFocused) setInputUrl(getDisplayHost(navState.url));
  };

  const handleScroll = (event: any) => {
    const y = event.nativeEvent.contentOffset.y;
    if (isInputFocused || activeView !== 'none' || y < 0) return;
    const dy = y - lastScrollY.current;
    const newTrans = Math.max(0, Math.min(HIDDEN_TRANSLATE_Y, currentScrollTrans.current + dy));
    if (newTrans !== currentScrollTrans.current) {
        scrollTranslateY.setValue(newTrans);
        currentScrollTrans.current = newTrans;
    }
    lastScrollY.current = y;
  };

  const renderOverlayContent = () => {
    let content = null;
    let title = "";

    if (activeView === 'history') {
      title = "History";
      content = (
        <FlatList 
          data={history}
          keyExtractor={(item, index) => item.id + index}
          contentContainerStyle={{ padding: 20 }}
          ListHeaderComponent={<Text style={[styles.sectionHeader, { color: theme.textSec, fontFamily: 'Nunito_700Bold' }]}>Recently Visited</Text>}
          renderItem={({item}) => (
              <SwipeableHistoryRow 
                item={item}
                theme={theme}
                accent={accentColor}
                radius={cornerRadius}
                height={getHistoryHeight()}
                margin={getMargin()}
                fontScale={fontScale}
                onPress={() => { setActiveUrl(item.url); setInputUrl(getDisplayHost(item.url)); closeOverlay(); }}
                onDelete={() => deleteHistoryItem(item.id)}
              />
          )}
          ListEmptyComponent={<View style={styles.emptyState}><Ionicons name="time-outline" size={50} color={theme.textSec} /><Text style={[styles.emptyText, { color: theme.text, fontFamily: 'Nunito_600SemiBold' }]}>No history yet.</Text></View>}
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
                    theme={theme}
                    accent={accentColor}
                    radius={cornerRadius}
                    height={getTabHeight()}
                    margin={getMargin()}
                    fontScale={fontScale}
                    isActive={item.id === activeTabId}
                    onPress={() => { setActiveTabId(item.id); setActiveUrl(item.url); setInputUrl(getDisplayHost(item.url)); }}
                    onDelete={() => deleteTab(item.id)}
                    onRename={() => openRenameModal(item.id, item.title, item.showLogo)}
                />
              )}
            />
            <View style={styles.fabContainer}>
                <TouchableOpacity style={[styles.fabButton, { backgroundColor: accentColor }]} onPress={addNewTab}><Ionicons name="add" size={32} color="#fff" /></TouchableOpacity>
            </View>
        </View>
      );
    } else if (activeView === 'settings') {
      title = "Settings";
      content = (
        <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 20, paddingBottom: 100 }}>
            <Text style={[styles.sectionHeader, { color: theme.textSec, fontFamily: 'Nunito_700Bold' }]}>Appearance</Text>
            <View style={[styles.settingsGroup, { backgroundColor: theme.card, borderRadius: cornerRadius }]}>
                {/* Theme */}
                <View style={[styles.settingRow, { borderBottomWidth: 1, borderColor: theme.bg }]}>
                    <Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>Theme</Text>
                    <View style={{ flexDirection: 'row' }}>
                        {['light', 'dark', 'adaptive'].map((m) => (
                            <TouchableOpacity key={m} onPress={() => setThemeMode(m as any)} style={[styles.modeBtn, themeMode === m && { backgroundColor: accentColor }]}>
                                <Text style={[styles.modeBtnText, themeMode === m ? { color: '#fff' } : { color: theme.text }, { fontFamily: 'Nunito_700Bold', fontSize: 12 * fontScale }]}>{m.charAt(0).toUpperCase() + m.slice(1)}</Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                </View>
                {/* Accent */}
                <View style={[styles.settingRow, { borderBottomWidth: 1, borderColor: theme.bg }]}>
                    <Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>Accent</Text>
                    <View style={{ flexDirection: 'row' }}>
                        {ACCENTS.map(color => (
                            <TouchableOpacity key={color} onPress={() => setAccentColor(color)} style={[styles.colorDot, { backgroundColor: color }, accentColor === color && { borderWidth: 2, borderColor: theme.text }]} />
                        ))}
                    </View>
                </View>
                
                {/* Font Scale (New) */}
                <View style={[styles.settingRow, { borderBottomWidth: 1, borderColor: theme.bg, height: 100, flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'center' }]}>
                    <View style={{flexDirection:'row', width:'100%', justifyContent:'space-between', marginBottom: 10}}>
                        <View style={{flexDirection:'row', alignItems:'center'}}>
                            <Ionicons name="text-outline" size={22} color={theme.text} style={{marginRight:10}} />
                            <Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>Font Size</Text>
                        </View>
                        <Text style={{color:theme.textSec, fontFamily: 'Nunito_700Bold'}}>{(fontScale * 100).toFixed(0)}%</Text>
                    </View>
                    <View style={{flexDirection:'row', width:'100%', justifyContent:'space-between', alignItems:'center', paddingHorizontal:10}}>
                        <TouchableOpacity onPress={() => setFontScale(Math.max(0.8, fontScale - 0.1))}><Ionicons name="remove-circle-outline" size={28} color={theme.textSec} /></TouchableOpacity>
                        <View style={{height: 4, flex:1, backgroundColor: theme.bg, marginHorizontal:15, borderRadius:2}}>
                            <View style={{height:'100%', width: `${((fontScale-0.8)/0.4)*100}%`, backgroundColor: accentColor, borderRadius:2}} />
                        </View>
                        <TouchableOpacity onPress={() => setFontScale(Math.min(1.2, fontScale + 0.1))}><Ionicons name="add-circle-outline" size={28} color={theme.textSec} /></TouchableOpacity>
                    </View>
                </View>

                {/* Corner Radius */}
                <View style={[styles.settingRow, { borderBottomWidth: 1, borderColor: theme.bg, height: 100, flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'center' }]}>
                    <View style={{flexDirection:'row', width:'100%', justifyContent:'space-between', marginBottom: 10}}>
                        <View style={{flexDirection:'row', alignItems:'center'}}>
                            <Ionicons name="shapes-outline" size={22} color={theme.text} style={{marginRight:10}} />
                            <Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>Corner Radius</Text>
                        </View>
                        <Text style={{color:theme.textSec, fontFamily: 'Nunito_700Bold'}}>{Math.round(cornerRadius)}px</Text>
                    </View>
                    <View style={{flexDirection:'row', width:'100%', justifyContent: 'space-around'}}>
                        <TouchableOpacity onPress={() => setCornerRadius(0)} style={{alignItems:'center'}}>
                            <View style={{width:30, height:30, borderWidth:2, borderColor:theme.text, marginBottom:5}}/>
                            <Text style={{color:theme.textSec, fontSize:10 * fontScale}}>Square</Text>
                        </TouchableOpacity>
                        <TouchableOpacity onPress={() => setCornerRadius(10)} style={{alignItems:'center'}}>
                            <View style={{width:30, height:30, borderWidth:2, borderColor:theme.text, borderRadius:6, marginBottom:5}}/>
                            <Text style={{color:theme.textSec, fontSize:10 * fontScale}}>Soft</Text>
                        </TouchableOpacity>
                        <TouchableOpacity onPress={() => setCornerRadius(22)} style={{alignItems:'center'}}>
                            <View style={{width:30, height:30, borderWidth:2, borderColor:theme.text, borderRadius:15, marginBottom:5}}/>
                            <Text style={{color:theme.textSec, fontSize:10 * fontScale}}>Round</Text>
                        </TouchableOpacity>
                    </View>
                </View>
                
                {/* UI Spacing */}
                <View style={[styles.settingRow, { height: 100, flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'center' }]}>
                    <View style={{flexDirection:'row', alignItems:'center', marginBottom: 15}}>
                        <Ionicons name="resize-outline" size={22} color={theme.text} style={{marginRight:10}} />
                        <Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>UI Spacing</Text>
                    </View>
                    <View style={{flexDirection:'row', width:'100%', justifyContent:'space-between'}}>
                        {['compact','normal','airy'].map(mode => (
                            <TouchableOpacity key={mode} onPress={() => setUiPadding(mode as any)} style={[styles.modeBtn, uiPadding === mode && {backgroundColor:accentColor}, {paddingHorizontal: 20}]}>
                                <Text style={[styles.modeBtnText, uiPadding === mode ? {color:'#fff'} : {color:theme.text}, {fontFamily:'Nunito_700Bold', fontSize: 12 * fontScale}]}>{mode.charAt(0).toUpperCase() + mode.slice(1)}</Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                </View>
            </View>

            <Text style={[styles.sectionHeader, { color: theme.textSec, fontFamily: 'Nunito_700Bold' }]}>General</Text>
            <View style={[styles.settingsGroup, { backgroundColor: theme.card, borderRadius: cornerRadius }]}>
                {/* Search Engine */}
                <TouchableOpacity onPress={() => { LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut); setIsSearchEngineOpen(!isSearchEngineOpen); }} style={[styles.settingRow, { borderBottomWidth: 1, borderColor: theme.bg }]}>
                     <View style={{flexDirection: 'row', alignItems: 'center'}}><Ionicons name="search-outline" size={22} color={theme.text} style={{marginRight: 10}} /><Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>Search Engine</Text></View>
                     <View style={{flexDirection:'row', alignItems:'center'}}><Ionicons name={SEARCH_ENGINES[searchEngineIndex].icon as any} size={18} color={theme.text} style={{marginRight:5}} /><Text style={{color: theme.textSec, marginRight: 5, fontFamily: 'Nunito_600SemiBold', fontSize: 14 * fontScale}}>{SEARCH_ENGINES[searchEngineIndex].name}</Text><Ionicons name={isSearchEngineOpen ? "chevron-up" : "chevron-down"} size={16} color={theme.textSec} /></View>
                </TouchableOpacity>
                {isSearchEngineOpen && <View style={{ borderTopWidth: 1, borderColor: theme.bg }}>{SEARCH_ENGINES.map((engine, index) => (<TouchableOpacity key={engine.name} style={[styles.settingRow, { paddingLeft: 40, paddingVertical: 12 }]} onPress={() => { setSearchEngineIndex(index); LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut); setIsSearchEngineOpen(false); }}><View style={{flexDirection:'row', alignItems:'center'}}><Ionicons name={engine.icon as any} size={20} color={theme.text} style={{marginRight:10}} /><Text style={{ color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }}>{engine.name}</Text></View>{searchEngineIndex === index && <Ionicons name="checkmark" size={18} color={accentColor} />}</TouchableOpacity>))}</View>}
                
                {/* Desktop Mode */}
                <View style={[styles.settingRow, { borderBottomWidth: 1, borderColor: theme.bg }]}>
                    <View style={{flexDirection: 'row', alignItems: 'center'}}><Ionicons name="desktop-outline" size={22} color={theme.text} style={{marginRight: 10}} /><Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>Desktop Mode</Text></View>
                    <Switch value={desktopMode} onValueChange={setDesktopMode} trackColor={{false: '#767577', true: accentColor}} thumbColor={'#f4f3f4'} />
                </View>

                {/* Reader Mode */}
                <View style={[styles.settingRow, { borderBottomWidth: 1, borderColor: theme.bg }]}>
                    <View style={{flexDirection: 'row', alignItems: 'center'}}><Ionicons name="book-outline" size={22} color={theme.text} style={{marginRight: 10}} /><Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>Reader View</Text></View>
                    <Switch value={readerMode} onValueChange={setReaderMode} trackColor={{false: '#767577', true: accentColor}} thumbColor={'#f4f3f4'} />
                </View>

                {/* JS Enabled */}
                <View style={[styles.settingRow, { borderBottomWidth: 1, borderColor: theme.bg }]}>
                    <View style={{flexDirection: 'row', alignItems: 'center'}}><Ionicons name="code-slash-outline" size={22} color={theme.text} style={{marginRight: 10}} /><Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>Enable JavaScript</Text></View>
                    <Switch value={jsEnabled} onValueChange={setJsEnabled} trackColor={{false: '#767577', true: accentColor}} thumbColor={'#f4f3f4'} />
                </View>

                {/* HTTPS Only */}
                <View style={[styles.settingRow, { borderBottomWidth: 1, borderColor: theme.bg }]}>
                    <View style={{flexDirection: 'row', alignItems: 'center'}}><Ionicons name="lock-closed-outline" size={22} color={theme.text} style={{marginRight: 10}} /><Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>HTTPS Only</Text></View>
                    <Switch value={httpsOnly} onValueChange={setHttpsOnly} trackColor={{false: '#767577', true: accentColor}} thumbColor={'#f4f3f4'} />
                </View>

                {/* Block Cookies */}
                <View style={styles.settingRow}>
                    <View style={{flexDirection: 'row', alignItems: 'center'}}><Ionicons name="eye-off-outline" size={22} color={theme.text} style={{marginRight: 10}} /><Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>Block Cookies</Text></View>
                    <Switch value={blockCookies} onValueChange={setBlockCookies} trackColor={{false: '#767577', true: accentColor}} thumbColor={'#f4f3f4'} />
                </View>
            </View>

            <Text style={[styles.sectionHeader, { color: theme.textSec, fontFamily: 'Nunito_700Bold' }]}>Data</Text>
            <View style={[styles.settingsGroup, { backgroundColor: theme.card, borderRadius: cornerRadius }]}>
                {/* Clear Cache */}
                <TouchableOpacity onPress={() => webViewRef.current?.clearCache(true)} style={[styles.settingRow, { borderBottomWidth: 1, borderColor: theme.bg }]}>
                    <View style={{flexDirection: 'row', alignItems: 'center'}}><Ionicons name="file-tray-full-outline" size={22} color={theme.text} style={{marginRight: 10}} /><Text style={[styles.settingText, { color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }]}>Clear Cache</Text></View>
                    <Ionicons name="chevron-forward" size={16} color={theme.textSec} />
                </TouchableOpacity>

                {/* Clear History */}
                <TouchableOpacity onPress={() => { LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut); setIsClearHistoryOpen(!isClearHistoryOpen); }} style={styles.settingRow}>
                    <View style={{flexDirection: 'row', alignItems: 'center'}}><Ionicons name="trash-outline" size={22} color="#ff3b30" style={{marginRight: 10}} /><Text style={[styles.settingText, {color: '#ff3b30', fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale}]}>Clear History</Text></View>
                    <Ionicons name={isClearHistoryOpen ? "chevron-up" : "chevron-down"} size={16} color={theme.textSec} />
                </TouchableOpacity>
                {isClearHistoryOpen && <View style={{ borderTopWidth: 1, borderColor: theme.bg }}>{HISTORY_RANGES.map((range, index) => (<TouchableOpacity key={index} style={[styles.settingRow, { paddingLeft: 40, paddingVertical: 12 }]} onPress={() => deleteHistory(range.ms)}><Text style={{ color: theme.text, fontFamily: 'Nunito_600SemiBold', fontSize: 16 * fontScale }}>{range.label}</Text></TouchableOpacity>))}</View>}
            </View>
            <Text style={styles.versionText}>mi. browser v{APP_VERSION}</Text>
        </ScrollView>
      );
    }

    if (!fontsLoaded) return <View style={{flex:1, backgroundColor:'#000', justifyContent:'center', alignItems:'center'}}><ActivityIndicator size="large" color="#007AFF" /></View>;

    return (
      <View style={{flex: 1}}>
        <Animated.View style={[styles.sheetContainer, { height: overlayHeightAnim, backgroundColor: theme.surface, borderTopLeftRadius: cornerRadius, borderTopRightRadius: cornerRadius }]}>
            <View style={[styles.sheetHeader, { backgroundColor: theme.sheetHeader, borderBottomColor: theme.bg }]} {...sheetPanResponder.panHandlers}>
                <View style={styles.sheetHandle} /> 
                <View style={styles.sheetHeaderRow}>
                    <Text style={[styles.sheetTitle, { color: theme.text, fontFamily: 'Nunito_800ExtraBold', fontSize: 22 * fontScale }]}>{title}</Text>
                    <View style={{flexDirection: 'row'}}>
                        <TouchableOpacity onPress={closeOverlay} style={styles.iconBtn}><Ionicons name="close-circle" size={28} color={accentColor} /></TouchableOpacity>
                    </View>
                </View>
            </View>
            <Animated.View style={{ flex: 1, paddingBottom: keyboardHeight }}>{content}</Animated.View>
        </Animated.View>

        {/* Rename Modal */}
        <Modal visible={isRenameModalVisible} transparent animationType="fade" onRequestClose={() => setIsRenameModalVisible(false)}>
            <View style={styles.modalOverlay}>
                <View style={[styles.modalContent, { backgroundColor: theme.surface, borderRadius: cornerRadius }]}>
                    <Text style={[styles.modalTitle, { color: theme.text, fontFamily:'Nunito_700Bold' }]}>Edit Tab</Text>
                    <TextInput style={[styles.modalInput, { backgroundColor: theme.inputBg, color: theme.text, fontFamily: 'Nunito_600SemiBold', borderRadius: cornerRadius/2 }]} value={renameText} onChangeText={setRenameText} autoFocus selectAllOnFocus />
                    
                    <View style={{flexDirection:'row', alignItems:'center', justifyContent:'space-between', marginBottom: 20}}>
                        <Text style={{color: theme.text, fontFamily: 'Nunito_600SemiBold'}}>Show Site Logo</Text>
                        <Switch value={renameShowLogo} onValueChange={setRenameShowLogo} trackColor={{false: '#767577', true: accentColor}} thumbColor={'#f4f3f4'} />
                    </View>

                    <View style={styles.modalButtons}>
                        <TouchableOpacity onPress={() => setIsRenameModalVisible(false)} style={[styles.modalBtn, {borderRadius: cornerRadius/2}]}><Text style={{ color: theme.textSec, fontFamily: 'Nunito_700Bold' }}>Cancel</Text></TouchableOpacity>
                        <TouchableOpacity onPress={saveRenameTab} style={[styles.modalBtn, { backgroundColor: accentColor, borderRadius: cornerRadius/2 }]}><Text style={{ color: '#fff', fontWeight: 'bold', fontFamily: 'Nunito_700Bold' }}>Save</Text></TouchableOpacity>
                    </View>
                </View>
            </View>
        </Modal>
      </View>
    );
  };

  const webViewProps = {
    ref: webViewRef,
    source: { uri: activeUrl || '' }, 
    onNavigationStateChange: handleNavigationStateChange,
    onLoadStart: () => setIsLoading(true),
    onError: () => setIsLoading(false),
    onLoadEnd: () => setIsLoading(false),
    onScroll: handleScroll,
    onTouchStart: () => { if (isInputFocused) Keyboard.dismiss(); },
    overScrollMode: 'never',
    scrollEventThrottle: 16,
    startInLoadingState: true,
    renderLoading: () => <View style={styles.loadingOverlay}><ActivityIndicator size="large" color={accentColor} /></View>,
    javaScriptEnabled: jsEnabled,
    userAgent: desktopMode ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" : undefined,
    sharedCookiesEnabled: !blockCookies,
    domStorageEnabled: true,
    androidLayerType: 'hardware' as const,
    pullToRefreshEnabled: false, 
    contentInset: { bottom: BAR_HEIGHT + 20 },
  };

  if (!fontsLoaded) return null;

  return (
    <View style={[styles.container, { backgroundColor: theme.bg }]}>
      <StatusBar translucent backgroundColor="transparent" barStyle={themeMode === 'dark' ? 'light-content' : 'dark-content'} />
      <Animated.View style={[styles.webViewContainer, { paddingBottom: keyboardHeight, backgroundColor: theme.bg }]}>
        {activeUrl ? <WebView {...webViewProps} /> : <View style={[styles.homeContainer, { backgroundColor: theme.bg }]} onTouchStart={() => { if (isInputFocused) Keyboard.dismiss(); }}><Animated.View style={{ transform: [{ scale: logoScale }, { translateX: logoPan.x }, { translateY: logoPan.y }], zIndex: 10, padding: 20 }} {...logoResponder.panHandlers}><Text style={[styles.homeText, { color: theme.text, fontFamily: 'Nunito_800ExtraBold' }]}>mi.</Text></Animated.View></View>}
      </Animated.View>
      {activeView !== 'none' && <View style={styles.overlayBackdrop}><TouchableWithoutFeedback onPress={closeOverlay}><View style={styles.backdropTouchArea} /></TouchableWithoutFeedback>{renderOverlayContent()}</View>}
      <Animated.View style={[styles.recallContainer, { opacity: recallOpacity }]} pointerEvents="box-none"><TouchableOpacity activeOpacity={0.8} onPress={showBar} {...recallPanResponder.panHandlers} style={[styles.recallButton, { backgroundColor: theme.glass, borderColor: theme.glassBorder, borderTopLeftRadius: cornerRadius, borderTopRightRadius: cornerRadius }]}><Ionicons name="chevron-up" size={24} color={theme.text} /></TouchableOpacity></Animated.View>
      
      {/* Floating Control Bar */}
      {activeView === 'none' && <View style={styles.floatingLayer} pointerEvents="box-none">
        <Animated.View style={[styles.bottomAreaContainer, { transform: [{ translateY: Animated.subtract(scrollTranslateY, keyboardHeight) }] }]}>
            <View style={styles.gestureArea} {...panResponder.panHandlers}>
                
                {/* Menu Pill */}
                <Animated.View style={[styles.pillBase, { backgroundColor: theme.glass, borderColor: theme.glassBorder, borderRadius: cornerRadius * 2 }, { zIndex: 1, opacity: menuPillOpacity, transform: [{ scale: menuPillScale }] }]}>
                    <View style={styles.barTabContent}>
                        <TouchableOpacity style={styles.menuItem} onPress={() => setActiveView('tabs')}><Ionicons name="copy-outline" size={24} color={theme.text} /><Text style={[styles.menuLabel, { color: theme.text, fontFamily: 'Nunito_700Bold' }]}>Tabs</Text></TouchableOpacity>
                        <TouchableOpacity style={styles.menuItem} onPress={() => setActiveView('history')}><Ionicons name="time-outline" size={24} color={theme.text} /><Text style={[styles.menuLabel, { color: theme.text, fontFamily: 'Nunito_700Bold' }]}>History</Text></TouchableOpacity>
                        <TouchableOpacity style={styles.menuItem} onPress={() => setActiveView('settings')}><Ionicons name="settings-outline" size={24} color={theme.text} /><Text style={[styles.menuLabel, { color: theme.text, fontFamily: 'Nunito_700Bold' }]}>Settings</Text></TouchableOpacity>
                        <TouchableOpacity style={styles.menuItem} onPress={goHome}><Ionicons name="home-outline" size={24} color={theme.text} /><Text style={[styles.menuLabel, { color: theme.text, fontFamily: 'Nunito_700Bold' }]}>Home</Text></TouchableOpacity>
                    </View>
                </Animated.View>

                {/* Search Pill */}
                <Animated.View style={[styles.pillBase, { backgroundColor: theme.glass, borderColor: theme.glassBorder, borderRadius: cornerRadius * 2 }, { zIndex: 2, opacity: searchPillOpacity, transform: [{ translateY: searchPillTranslateY }] }]} pointerEvents={isSearchActive ? 'auto' : 'none'}>
                    <View style={styles.barTabContent}>
                        
                        {/* Navigation Arrows */}
                        <Animated.View style={[styles.navArrowContainer, { left: 20, opacity: backArrowOpacity }]}>
                            <Ionicons name="arrow-back" size={28} color={theme.text} />
                        </Animated.View>
                        <Animated.View style={[styles.navArrowContainer, { right: 20, opacity: forwardArrowOpacity }]}>
                            <Ionicons name="arrow-forward" size={28} color={theme.text} />
                        </Animated.View>

                        {/* Input Content */}
                        <Animated.View style={[styles.inputWrapper, { backgroundColor: theme.inputBg, opacity: contentOpacity, borderRadius: cornerRadius }]}>
                            <TextInput style={[styles.urlInput, { color: theme.text, fontFamily: 'Nunito_600SemiBold' }]} value={inputUrl} onChangeText={setInputUrl} onSubmitEditing={handleGoPress} placeholder="Search" placeholderTextColor={theme.placeholder} autoCapitalize="none" keyboardType="url" returnKeyType="go" selectTextOnFocus onFocus={() => { setIsInputFocused(true); setInputUrl(activeUrl || ''); }} onBlur={() => { setIsInputFocused(false); setInputUrl(getDisplayHost(activeUrl)); }} />
                            <View style={styles.actionButtons}>{isLoading ? <ActivityIndicator size="small" color={accentColor} /> : isInputFocused ? <TouchableOpacity onPress={handleGoPress}><Ionicons name="search" size={22} color={accentColor} /></TouchableOpacity> : <TouchableOpacity disabled={!activeUrl} onPress={() => webViewRef.current?.reload()} style={!activeUrl && styles.disabledBtn}><Ionicons name="refresh" size={22} color={theme.text} /></TouchableOpacity>}</View>
                        </Animated.View>
                    </View>
                </Animated.View>

            </View>
        </Animated.View>
      </View>}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, paddingTop: StatusBar.currentHeight || 0 },
  webViewContainer: { flex: 1, width: '100%', zIndex: 1 },
  homeContainer: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  homeText: { fontSize: 60, letterSpacing: -1, opacity: 0.9 },
  loadingOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.1)', justifyContent: 'center', alignItems: 'center' },
  floatingLayer: { position: 'absolute', left: 0, right: 0, bottom: 0, top: 0, zIndex: 2, justifyContent: 'flex-end' },
  recallContainer: { position: 'absolute', bottom: 0, left: 0, right: 0, zIndex: 1, alignItems: 'center' },
  recallButton: { width: 50, height: 30, justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderBottomWidth: 0 },
  bottomAreaContainer: { paddingHorizontal: 10, paddingBottom: 15, width: '100%', alignItems: 'center' },
  gestureArea: { width: '100%', height: BAR_HEIGHT, justifyContent: 'center' },
  pillBase: { position: 'absolute', left: 0, right: 0, height: BAR_HEIGHT, overflow: 'hidden', borderWidth: 1, shadowColor: '#000', shadowOffset: { width: 0, height: 5 }, shadowOpacity: 0.2, shadowRadius: 15, elevation: 10 },
  barTabContent: { height: '100%', width: '100%', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10 },
  inputWrapper: { flex: 1, flexDirection: 'row', alignItems: 'center', height: 44, paddingRight: 12 },
  urlInput: { flex: 1, height: '100%', paddingHorizontal: 16, fontSize: 16 },
  actionButtons: { justifyContent: 'center', alignItems: 'center', marginLeft: 5 },
  disabledBtn: { opacity: 0.3 },
  menuItem: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  menuLabel: { fontSize: 10, marginTop: 3 },
  navArrowContainer: { position: 'absolute', top: 0, bottom: 0, justifyContent: 'center', alignItems: 'center', zIndex: 5 },
  overlayBackdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 100, justifyContent: 'flex-end' },
  backdropTouchArea: { flex: 1 },
  sheetContainer: { width: '100%', position: 'absolute', bottom: 0, overflow: 'hidden', shadowColor: '#000', shadowOffset: { width: 0, height: -5 }, shadowOpacity: 0.3, shadowRadius: 10, elevation: 20 },
  sheetHeader: { paddingHorizontal: 20, paddingTop: 15, paddingBottom: 10, alignItems: 'center', borderBottomWidth: 1 },
  sheetHandle: { width: 40, height: 4, backgroundColor: '#999', borderRadius: 2, marginBottom: 10 },
  sheetHeaderRow: { flexDirection: 'row', width: '100%', justifyContent: 'space-between', alignItems: 'center' },
  sheetTitle: { fontSize: 22 },
  iconBtn: { padding: 5 },
  sectionHeader: { fontSize: 14, textTransform: 'uppercase', marginTop: 20, marginBottom: 10, marginLeft: 5 },
  historyItem: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12, paddingHorizontal: 15, marginBottom: 10 },
  historyIconBox: { width: 36, height: 36, borderRadius: 18, backgroundColor: 'rgba(120,120,120,0.2)', alignItems: 'center', justifyContent: 'center', marginRight: 15 },
  historyTitle: { fontSize: 16, marginBottom: 2 },
  historyUrl: { fontSize: 12 },
  emptyState: { alignItems: 'center', marginTop: 50, opacity: 0.5 },
  emptyText: { marginTop: 10, fontSize: 16 },
  settingsGroup: { overflow: 'hidden' },
  settingRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, height: 60 },
  settingText: { fontSize: 16 },
  settingBtn: { backgroundColor: '#444', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 6 },
  versionText: { color: '#888', marginTop: 30, marginBottom: 50, textAlign: 'center', fontSize: 12 },
  modeBtn: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 15, marginLeft: 5, backgroundColor: 'rgba(120,120,120,0.1)' },
  modeBtnText: { fontSize: 12 },
  colorDot: { width: 24, height: 24, borderRadius: 12, marginLeft: 10 },
  tabRowContainer: { width: '100%', justifyContent: 'center' },
  deleteLayer: { position: 'absolute', top: 0, bottom: 0, left: 0, right: 0, alignItems: 'center', justifyContent: 'flex-end', paddingRight: 20, zIndex: 0, flexDirection: 'row' },
  tabCardWrapper: { backgroundColor: 'transparent' },
  tabCard: { flexDirection: 'row', alignItems: 'center', padding: 15, shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.1, shadowRadius: 6, elevation: 5 },
  faviconContainer: { width: 42, height: 42, borderRadius: 21, alignItems: 'center', justifyContent: 'center', marginRight: 15 },
  faviconText: { color: '#fff', fontSize: 20 },
  tabTextContainer: { flex: 1, justifyContent: 'center' },
  tabTitleText: { fontSize: 16, marginBottom: 4 },
  tabUrlText: { fontSize: 12 },
  fabContainer: { position: 'absolute', bottom: 40, right: 20 },
  fabButton: { width: 60, height: 60, borderRadius: 30, alignItems: 'center', justifyContent: 'center', shadowColor: '#000', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.4, shadowRadius: 8, elevation: 8 },
  pencilBtn: { width: 32, height: 32, borderRadius: 16, borderTopWidth: 1, borderBottomWidth: 1, borderLeftWidth: 1, borderRightWidth: 1, alignItems: 'center', justifyContent: 'center' },
  modalOverlay: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.5)' },
  modalContent: { width: '80%', padding: 20, elevation: 5 },
  modalTitle: { fontSize: 20, marginBottom: 15 },
  modalInput: { borderWidth: 1, borderColor: 'rgba(128,128,128,0.2)', padding: 12, marginBottom: 20, fontSize: 16 },
  modalButtons: { flexDirection: 'row', justifyContent: 'flex-end' },
  modalBtn: { paddingHorizontal: 15, paddingVertical: 10, marginLeft: 10 },
});