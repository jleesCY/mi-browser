import { useState, useEffect } from 'react';
import * as Location from 'expo-location';

// Module-level cache (persists while app is open)
let cachedWeatherData: any = null;
let isFetching = false;
let fetchError: string | null = null;
let listeners: ((data: any) => void)[] = [];

const notifyListeners = () => {
  listeners.forEach(l => l(cachedWeatherData));
};

export const useWeather = (enabled: boolean) => {
  const [weather, setWeather] = useState(cachedWeatherData);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(fetchError);

  useEffect(() => {
    if (!enabled) return;

    // If we have data, just set it and stop
    if (cachedWeatherData) {
      setWeather(cachedWeatherData);
      return;
    }

    // If already fetching, just wait (could implement a listener if needed, but simple is fine)
    if (isFetching) {
        setLoading(true);
        const checkInterval = setInterval(() => {
            if (!isFetching) {
                setWeather(cachedWeatherData);
                setError(fetchError);
                setLoading(false);
                clearInterval(checkInterval);
            }
        }, 500);
        return () => clearInterval(checkInterval);
    }

    const fetchWeather = async () => {
      isFetching = true;
      setLoading(true);
      setError(null);

      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== 'granted') {
          throw new Error("Permission denied");
        }

        const location = await Location.getCurrentPositionAsync({});
        const { latitude, longitude } = location.coords;

        // Fetch comprehensive weather data
        // Daily: Max/Min Temp, Precip Prob Max, Precip Sum
        // Hourly: Temp, Precip Prob, Weather Code (for forecast)
        // Current: Temp, Wind, Weather Code
        const weatherRes = await fetch(
          `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current_weather=true&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum&hourly=temperature_2m,precipitation_probability,weathercode&temperature_unit=fahrenheit&windspeed_unit=mph&timezone=auto`
        );
        const weatherData = await weatherRes.json();
        
        const reverseGeo = await Location.reverseGeocodeAsync({ latitude, longitude });
        const city = reverseGeo[0]?.city || reverseGeo[0]?.region || "Your Location";

        // Find index of current hour
        const now = new Date();
        const currentHourIndex = weatherData.hourly?.time?.findIndex((t: string) => new Date(t).getTime() >= now.getTime() - 60 * 60 * 1000) ?? 0;
        const startIndex = Math.max(0, currentHourIndex);
        const endIndex = startIndex + 24;

        cachedWeatherData = {
          current: {
            temp: weatherData.current_weather.temperature,
            code: weatherData.current_weather.weathercode,
            windSpeed: weatherData.current_weather.windspeed,
          },
          daily: {
            tempMax: weatherData.daily?.temperature_2m_max?.[0],
            tempMin: weatherData.daily?.temperature_2m_min?.[0],
            precipProb: weatherData.daily?.precipitation_probability_max?.[0],
            precipSum: weatherData.daily?.precipitation_sum?.[0],
          },
          hourly: {
            times: weatherData.hourly?.time?.slice(startIndex, endIndex),
            temps: weatherData.hourly?.temperature_2m?.slice(startIndex, endIndex),
            codes: weatherData.hourly?.weathercode?.slice(startIndex, endIndex),
            precip: weatherData.hourly?.precipitation_probability?.slice(startIndex, endIndex),
          },
          city: city,
          timestamp: Date.now()
        };
        
        setWeather(cachedWeatherData);
      } catch (err: any) {
        console.error("Weather fetch error", err);
        fetchError = err.message || "Failed to load";
        setError(fetchError);
      } finally {
        isFetching = false;
        setLoading(false);
      }
    };

    fetchWeather();
  }, [enabled]);

  return { weather, loading, error };
};
