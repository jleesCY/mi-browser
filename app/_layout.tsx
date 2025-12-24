import { Stack } from 'expo-router';
import { useColorScheme } from 'react-native';

export default function Layout() {
  const colorScheme = useColorScheme();
  
  return (
    <Stack
      screenOptions={{
        headerShown: false, 
        // Use the system theme for the initial background to prevent "flashing"
        contentStyle: { backgroundColor: colorScheme === 'dark' ? '#000' : '#fff' },
        animation: 'fade', // Smoother transition
      }}
    />
  );
}