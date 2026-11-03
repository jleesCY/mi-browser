import { Stack } from 'expo-router';
import { useColorScheme } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

export default function Layout() {
  const colorScheme = useColorScheme();
  
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <Stack
        screenOptions={{
          headerShown: false, 
          // Use the system theme for the initial background to prevent "flashing"
          contentStyle: { backgroundColor: colorScheme === 'dark' ? '#000' : '#fff' },
          animation: 'fade', // Smoother transition
        }}
      />
    </GestureHandlerRootView>
  );
}