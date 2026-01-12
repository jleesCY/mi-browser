import { useRootNavigationState, useRouter } from "expo-router";
import { useEffect } from "react";
import { View } from "react-native";

export default function Unmatched() {
  const router = useRouter();
  const rootNavigationState = useRootNavigationState();

  useEffect(() => {
    if (rootNavigationState?.key) {
      const timer = setTimeout(() => {
        router.replace("/");
      }, 0);

      return () => clearTimeout(timer);
    }
  }, [rootNavigationState?.key]);

  return <View style={{ flex: 1, backgroundColor: '#000' }} />;
}