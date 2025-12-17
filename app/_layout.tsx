import { Stack } from 'expo-router';

export default function Layout() {
  return (
    <Stack
      screenOptions={{
        // This removes the header globally for all screens
        headerShown: false, 
        // Ensure the background is white
        contentStyle: { backgroundColor: '#fff' },
      }}
    />
  );
}
