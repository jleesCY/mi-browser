import { useLocalSearchParams, useRouter } from "expo-router";
import { useEffect } from "react";
import { View } from "react-native";

export default function Unmatched() {
  const router = useRouter();
  const params = useLocalSearchParams();

  useEffect(() => {
    router.replace("/");
  }, []);

  return <View style={{ flex: 1, backgroundColor: '#000' }} />;
}