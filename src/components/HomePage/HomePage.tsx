import { Ionicons } from "@expo/vector-icons";
import React, { useEffect, useState } from "react";
import { ActivityIndicator, Animated, Image, Keyboard, PanResponder, StyleSheet, Text, TouchableOpacity, TouchableWithoutFeedback, View } from "react-native";
import { HOME_LOGO_TEXT } from "../../constants";
import { BrowserSettings } from "../../hooks/useBrowserSettings";
import { useWeather } from "../../hooks/useWeather";

interface HomePageProps {
  settings: BrowserSettings;
  theme: any;
  isActive: boolean;
  onAction: (action: "newTab" | "qr" | "bookmarks" | "history") => void;
}

export const HomePage: React.FC<HomePageProps> = ({ settings, theme, isActive, onAction }) => {
  const [time, setTime] = useState(new Date());

  const {
    homeClockType,
    homeDateType,
    homeWeatherType,
    homeLogoType,
    homeBackgroundImage,
    showHomeShortcuts,
    homeShortcutAction,
    fontScale,
    accentColor
  } = settings;
  const { weather, loading: weatherLoading, error: weatherError } = useWeather(homeWeatherType !== "None" && isActive);

  // Logo Animation State
  const logoScale = React.useRef(new Animated.Value(1)).current;
  const logoPan = React.useRef(new Animated.ValueXY({ x: 0, y: 0 })).current;

  const logoResponder = React.useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        Animated.spring(logoScale, {
          toValue: 1.2,
          useNativeDriver: false,
        }).start();
        logoPan.setOffset({
          x: (logoPan.x as any)._value,
          y: (logoPan.y as any)._value,
        });
        logoPan.setValue({ x: 0, y: 0 });
      },
      onPanResponderMove: Animated.event(
        [null, { dx: logoPan.x, dy: logoPan.y }],
        { useNativeDriver: false },
      ),
      onPanResponderRelease: () => {
        logoPan.flattenOffset();
        Animated.spring(logoPan, {
          toValue: { x: 0, y: 0 },
          friction: 6,
          tension: 80,
          useNativeDriver: false,
        }).start();
        Animated.spring(logoScale, {
          toValue: 1,
          friction: 6,
          tension: 80,
          useNativeDriver: false,
        }).start();
      },
    }),
  ).current;

  // Clock Timer
  useEffect(() => {
    if (homeClockType === "None" && homeDateType === "None") return;
    const interval = setInterval(() => {
      setTime(new Date());
    }, 1000);
    return () => clearInterval(interval);
  }, [homeClockType, homeDateType]);

  if (!isActive) return null;

  const getWeatherIcon = (code: number) => {
    if (code === 0) return "sunny";
    if (code >= 1 && code <= 3) return "partly-sunny";
    if (code >= 45 && code <= 48) return "cloudy";
    if (code >= 51 && code <= 67) return "rainy";
    if (code >= 71 && code <= 77) return "snow";
    if (code >= 80 && code <= 82) return "rainy";
    if (code >= 85 && code <= 86) return "snow";
    if (code >= 95) return "thunderstorm";
    return "cloud-outline";
  };

  const getShortcutConfig = () => {
    switch (homeShortcutAction) {
      case "newTab":
        return { icon: "add", label: "New Tab" };
      case "bookmarks":
        return { icon: "bookmarks-outline", label: "Saved" };
      case "history":
        return { icon: "time-outline", label: "Recent" };
      case "qr":
      default:
        return { icon: "qr-code-outline", label: "Scan" };
    }
  };

  const shortcutConfig = getShortcutConfig();

  const renderDate = () => (
    <Text style={[styles.dateText, { color: theme.textSec, fontSize: 16 * fontScale, fontFamily: theme.fonts.semibold }]}>
      {time.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}
    </Text>
  );

  return (
    <TouchableWithoutFeedback onPress={Keyboard.dismiss} accessible={false}>
      <View style={[styles.container, { backgroundColor: theme.bg }]}>
        {homeBackgroundImage && (
          <Image
            source={{ uri: homeBackgroundImage }}
            style={StyleSheet.absoluteFillObject}
            resizeMode="cover"
          />
        )}

        {/* Top Widgets Container */}
        <View style={styles.topWidgets}>
          {homeClockType !== "None" && (
            <View style={styles.clockContainer}>
              {homeDateType === "Above" && renderDate()}
              <Text style={[styles.timeText, { color: theme.text, fontSize: 72 * fontScale, fontFamily: theme.fonts.bold }]}>
                {time.toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                  hour12: homeClockType === "12h"
                })}
              </Text>
              {homeDateType === "Below" && renderDate()}
            </View>
          )}

          {/* If clock is hidden but date is shown, just show date */}
          {homeClockType === "None" && homeDateType !== "None" && (
            <View style={styles.clockContainer}>
              {renderDate()}
            </View>
          )}

          {homeWeatherType !== "None" && (
            <View style={styles.weatherContainer}>
              {weatherLoading && !weather ? (
                <ActivityIndicator size="small" color={theme.textSec} />
              ) : weatherError ? (
                <Text style={[styles.weatherText, { color: theme.textSec, fontSize: 14 * fontScale, fontFamily: theme.fonts.regular }]}>{weatherError}</Text>
              ) : weather ? (
                <View style={{ alignItems: 'center', width: '100%' }}>

                  {/* Main Weather Display */}
                  <View style={styles.weatherMainRow}>
                    {/* Left: High/Low */}
                    {(homeWeatherType === "Detailed" || homeWeatherType === "Hourly") && (
                      <View style={{ alignItems: 'flex-end', marginRight: 15 }}>
                        <Text style={[styles.detailText, { color: theme.textSec, fontSize: 12 * fontScale, fontFamily: theme.fonts.semibold }]}>H: {weather.daily.tempMax}°</Text>
                        <Text style={[styles.detailText, { color: theme.textSec, fontSize: 12 * fontScale, fontFamily: theme.fonts.semibold }]}>L: {weather.daily.tempMin}°</Text>
                      </View>
                    )}

                    {/* Center: Icon + Temp + City */}
                    <View style={{ alignItems: 'center' }}>
                      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                        <Ionicons name={getWeatherIcon(weather.current.code)} size={32 * fontScale} color={theme.text} />
                        <Text style={[styles.tempText, { color: theme.text, fontSize: 24 * fontScale, marginLeft: 8, fontFamily: theme.fonts.bold }]}>
                          {weather.current.temp}°F
                        </Text>
                      </View>
                      <Text style={[styles.cityText, { color: theme.textSec, fontSize: 12 * fontScale, fontFamily: theme.fonts.regular }]}>
                        {weather.city}
                      </Text>
                    </View>

                    {/* Right: Wind/Precip */}
                    {(homeWeatherType === "Detailed" || homeWeatherType === "Hourly") && (
                      <View style={{ alignItems: 'flex-start', marginLeft: 15 }}>
                        <Text style={[styles.detailText, { color: theme.textSec, fontSize: 12 * fontScale, fontFamily: theme.fonts.semibold }]}>
                          <Ionicons name="filter" size={10} /> {weather.current.windSpeed} mph
                        </Text>
                        <Text style={[styles.detailText, { color: theme.textSec, fontSize: 12 * fontScale, fontFamily: theme.fonts.semibold }]}>
                          <Ionicons name="water" size={10} /> {weather.daily.precipProb}%
                        </Text>
                      </View>
                    )}
                  </View>

                  {/* Hourly Forecast (Only for Hourly) */}
                  {homeWeatherType === "Hourly" && (
                    <View style={{ marginTop: 20, width: '100%', alignItems: 'center' }}>
                      <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 25 }}>
                        {/* Limit to 5 items (Current + 4 future) to fit horizontally without scrolling */}
                        {weather.hourly.times.filter((_: any, i: number) => i % 3 === 0).slice(0, 5).map((t: string, i: number) => {
                          const date = new Date(t);
                          const hour = date.getHours();
                          const displayHour = hour === 0 ? "12am" : hour > 12 ? `${hour - 12}pm` : `${hour}am`;

                          // Since we slice data to start from current hour, the first item (i=0) is "Now"
                          const isCurrent = i === 0;

                          return (
                            <View key={i} style={{ alignItems: 'center', gap: 4, opacity: isCurrent ? 1 : 0.6 }}>
                              <Text style={{
                                color: isCurrent ? theme.text : theme.textSec,
                                fontSize: 10 * fontScale,
                                fontFamily: isCurrent ? theme.fonts.extrabold : theme.fonts.semibold
                              }}>
                                {isCurrent ? "Now" : displayHour}
                              </Text>
                              <Ionicons name={getWeatherIcon(weather.hourly.codes[i * 3])} size={18 * fontScale} color={theme.text} />
                              <Text style={{ color: theme.text, fontSize: 12 * fontScale, fontFamily: theme.fonts.bold }}>
                                {Math.round(weather.hourly.temps[i * 3])}°
                              </Text>
                            </View>
                          );
                        })}
                      </View>
                    </View>
                  )}
                </View>
              ) : null}
            </View>
          )}
        </View>

        {/* Center Logo - Render if explicitly enabled */}
        {homeLogoType !== "None" && (
          <View style={styles.centerLogoContainer}>
            {homeLogoType === "Fidget" ? (
              <Animated.View
                style={{
                  transform: [
                    { scale: logoScale },
                    { translateX: logoPan.x },
                    { translateY: logoPan.y },
                  ],
                  zIndex: 10,
                  alignItems: "center",
                  justifyContent: "center",
                }}
                {...logoResponder.panHandlers}
              >
                <Text
                  style={[
                    styles.homeText,
                    {
                      color: theme.text,
                      fontFamily: theme.fonts.extrabold,
                      fontSize: 60 * fontScale,
                    },
                  ]}
                >
                  {HOME_LOGO_TEXT}
                </Text>
              </Animated.View>
            ) : (
              <View
                style={{
                  zIndex: 10,
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <Text
                  style={[
                    styles.homeText,
                    {
                      color: theme.text,
                      fontFamily: theme.fonts.extrabold,
                      fontSize: 60 * fontScale,
                    },
                  ]}
                >
                  {HOME_LOGO_TEXT}
                </Text>
              </View>
            )}
          </View>
        )}

        {/* Single Configurable Shortcut - Bottom of screen */}
        {showHomeShortcuts && (
          <View style={styles.shortcutsContainer}>
            <TouchableOpacity onPress={() => onAction(homeShortcutAction)} style={styles.shortcutBtn}>
              <View style={[styles.shortcutIcon, { backgroundColor: theme.card }]}>
                <Ionicons name={shortcutConfig.icon as any} size={24} color={theme.text} />
              </View>
              <Text style={{ color: theme.text, fontFamily: theme.fonts.bold, fontSize: 12 * fontScale }}>{shortcutConfig.label}</Text>
            </TouchableOpacity>
          </View>
        )}
      </View>
    </TouchableWithoutFeedback>
  );
};

const styles = StyleSheet.create({
  container: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 0,
  },
  topWidgets: {
    position: 'absolute',
    top: 60, // Safe area inset approx
    left: 0,
    right: 0,
    alignItems: 'center',
    gap: 15,
    zIndex: 5
  },
  centerLogoContainer: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: "center",
    alignItems: "center",
    zIndex: 1
  },
  homeText: {
    letterSpacing: -1,
    opacity: 0.9
  },
  clockContainer: {
    alignItems: "center",
  },
  timeText: {
    includeFontPadding: false,
  },
  dateText: {
    marginVertical: 2,
  },
  weatherContainer: {
    alignItems: "center",
    minHeight: 50,
    justifyContent: 'center',
    width: '100%',
  },
  weatherMainRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  tempText: {
  },
  cityText: {
    marginTop: 2
  },
  weatherText: {
  },
  detailText: {
    marginBottom: 2
  },
  shortcutsContainer: {
    position: 'absolute',
    bottom: 100, // Above pill
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'center',
    zIndex: 10
  },
  shortcutBtn: {
    alignItems: 'center',
    gap: 8
  },
  shortcutIcon: {
    width: 60, // Slightly larger for single button focus
    height: 60,
    borderRadius: 30,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 5,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
  },
});
