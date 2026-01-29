import React, { useEffect } from 'react';
import { ViewStyle, View } from 'react-native';
import { GestureDetector, Gesture } from 'react-native-gesture-handler';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  useAnimatedReaction,
  runOnJS,
  SharedValue,
} from 'react-native-reanimated';

interface HorizontalSortableListProps<T> {
  data: T[];
  keyExtractor: (item: T) => string;
  renderItem: (info: { item: T; index: number; isActive: boolean }) => React.ReactNode;
  itemWidth: number;
  itemHeight: number;
  onReorder: (from: number, to: number) => void;
  onDragStart?: () => void;
  onDragEnd?: (id: string, absoluteX: number, absoluteY: number) => void;
  contentContainerStyle?: ViewStyle;
}

function SortableItem<T>({
  id,
  index,
  item,
  renderItem,
  positions,
  itemWidth,
  itemHeight,
  onReorder,
  onDragStart,
  onDragEnd,
  totalItems,
}: {
  id: string;
  index: number;
  item: T;
  renderItem: any;
  positions: SharedValue<any>;
  itemWidth: number;
  itemHeight: number;
  onReorder: (from: number, to: number) => void;
  onDragStart?: () => void;
  onDragEnd?: (id: string, absoluteX: number, absoluteY: number) => void;
  totalItems: number;
}) {
    const isGestureActive = useSharedValue(false);
    const initialOrder = useSharedValue(index);
    const positionX = useSharedValue(index * itemWidth);
    const translateX = useSharedValue(0);
    const translateY = useSharedValue(0);

    const springConfig = {
        damping: 50,
        stiffness: 500,
        mass: 1,
        overshootClamping: true,
        restDisplacementThreshold: 0.01,
        restSpeedThreshold: 0.01
    };

    useAnimatedReaction(
      () => positions.value[id],
      (newOrder) => {
          if (!isGestureActive.value && newOrder !== undefined) {
              positionX.value = withSpring(newOrder * itemWidth, springConfig);
          }
      },
      [itemWidth]
    );

    // Pan Gesture for Dragging (Reordering)
    // Activates quickly to allow dragging
    const pan = Gesture.Pan()
      .activateAfterLongPress(150)
      .onStart(() => {
        isGestureActive.value = true;
        initialOrder.value = positions.value[id];
        if (onDragStart) runOnJS(onDragStart)();
      })
      .onUpdate((e) => {
        translateX.value = e.translationX;
        translateY.value = e.translationY;

        const startOrder = initialOrder.value;
        const startX = startOrder * itemWidth;
        const absoluteX = startX + e.translationX;
        
        // Calculate target column
        const col = Math.round(absoluteX / itemWidth);
        const safeOrder = Math.min(Math.max(col, 0), totalItems - 1);

        const currentOrder = positions.value[id];

        if (safeOrder !== currentOrder) {
          const newPositions = { ...positions.value };
          for (const key in newPositions) {
              const val = newPositions[key];
              if (currentOrder < safeOrder) {
                  if (val > currentOrder && val <= safeOrder) {
                      newPositions[key] = val - 1;
                  }
              } else {
                  if (val >= safeOrder && val < currentOrder) {
                      newPositions[key] = val + 1;
                  }
              }
          }
          newPositions[id] = safeOrder;
          positions.value = newPositions;
        }
      })
      .onEnd((e) => {
        const toIndex = positions.value[id];
        
        if (index !== toIndex) {
          runOnJS(onReorder)(index, toIndex);
        }

        if (onDragEnd) {
            runOnJS(onDragEnd)(id, e.absoluteX, e.absoluteY);
        }
        
        const targetX = toIndex * itemWidth;
        const currentVisualX = positionX.value + translateX.value;
        
        // Snap logic
        positionX.value = targetX;
        translateX.value = currentVisualX - targetX;
        translateX.value = withSpring(0, springConfig);
        translateY.value = withSpring(0, springConfig);
        
        isGestureActive.value = false;
      });

    const animatedStyle = useAnimatedStyle(() => {
      return {
        position: 'absolute',
        top: 0,
        left: 0,
        width: itemWidth,
        height: itemHeight,
        zIndex: isGestureActive.value ? 100 : 1,
        transform: [
          { translateX: positionX.value + translateX.value },
          { translateY: translateY.value },
          { scale: withSpring(isGestureActive.value ? 1.1 : 1, springConfig) }
        ],
      };
    });

    return (
      <GestureDetector gesture={pan}>
        <Animated.View style={animatedStyle}>
          {renderItem({ item, index, isActive: false })}
        </Animated.View>
      </GestureDetector>
    );
}

export const HorizontalSortableList = <T,>({
  data,
  keyExtractor,
  renderItem,
  itemWidth,
  itemHeight,
  onReorder,
  onDragStart,
  onDragEnd,
  contentContainerStyle,
}: HorizontalSortableListProps<T>) => {
  const positions = useSharedValue<Record<string, number>>({});

  useEffect(() => {
    const newPositions: Record<string, number> = {};
    data.forEach((item, index) => {
      newPositions[keyExtractor(item)] = index;
    });
    positions.value = newPositions;
  }, [data, keyExtractor]);

  const containerWidth = data.length * itemWidth;

  return (
    <View style={[contentContainerStyle, { width: containerWidth, height: itemHeight, position: 'relative' }]}>
      {data.map((item, index) => (
        <SortableItem
          key={keyExtractor(item)}
          id={keyExtractor(item)}
          index={index}
          item={item}
          renderItem={renderItem}
          positions={positions}
          itemWidth={itemWidth}
          itemHeight={itemHeight}
          onReorder={onReorder}
          onDragStart={onDragStart}
          onDragEnd={onDragEnd}
          totalItems={data.length}
        />
      ))}
    </View>
  );
};
