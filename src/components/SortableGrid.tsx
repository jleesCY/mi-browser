import React, { useState, useEffect } from 'react';
import { Dimensions, ViewStyle, ScrollView } from 'react-native';
import { GestureDetector, Gesture } from 'react-native-gesture-handler';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  useAnimatedReaction,
  runOnJS,
  withTiming,
  SharedValue,
} from 'react-native-reanimated';

const SCREEN_WIDTH = Dimensions.get('window').width;

interface SortableGridProps<T> {
  data: T[];
  keyExtractor: (item: T) => string;
  renderItem: (info: { item: T; index: number; isActive: boolean }) => React.ReactNode;
  itemHeight: number;
  itemWidth: number;
  numColumns: number;
  onReorder: (from: number, to: number) => void;
  contentContainerStyle?: ViewStyle;
  headerComponent?: React.ReactNode;
  onScroll?: (event: any) => void;
  gridPaddingTop?: number;
  gridPaddingSide?: number;
}

function SortableItem<T>({
  id,
  index,
  item,
  renderItem,
  positions,
  scrollY,
  itemHeight,
  itemWidth,
  numColumns,
  onReorder,
  totalItems,
  headerHeight,
  gridPaddingTop = 0,
  gridPaddingSide = 0
}: {
  id: string;
  index: number;
  item: T;
  renderItem: any;
  positions: SharedValue<any>;
  scrollY: SharedValue<number>;
  itemHeight: number;
  itemWidth: number;
  numColumns: number;
  onReorder: (from: number, to: number) => void;
  totalItems: number;
  headerHeight: number;
  gridPaddingTop: number;
  gridPaddingSide: number;
}) {
    const isGestureActive = useSharedValue(false);
    const initialOrder = useSharedValue(index);
    
    // Initialize position with padding offsets
    const position = useSharedValue({
        x: (index % numColumns) * itemWidth + gridPaddingSide,
        y: Math.floor(index / numColumns) * itemHeight + headerHeight + gridPaddingTop
    });
    const translateX = useSharedValue(0);
    const translateY = useSharedValue(0);
  
    // Snappy config: High stiffness, critical damping, no overshoot
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
              const newRow = Math.floor(newOrder / numColumns);
              const newCol = newOrder % numColumns;
              position.value = withSpring({
                  x: newCol * itemWidth + gridPaddingSide,
                  y: newRow * itemHeight + headerHeight + gridPaddingTop
              }, springConfig);
          }
      },
      [numColumns, itemWidth, itemHeight, headerHeight, gridPaddingTop, gridPaddingSide]
    );
  
    const pan = Gesture.Pan()
      .activateAfterLongPress(200)
      .onStart(() => {
        isGestureActive.value = true;
        initialOrder.value = positions.value[id];
      })
      .onUpdate((e) => {
        translateX.value = e.translationX;
        translateY.value = e.translationY;
  
        // Use the CAPTURED initial order for the drag source calculation
        // This prevents the feedback loop where updating 'positions' shifts the calculation reference
        const startOrder = initialOrder.value;
        
        const startX = (startOrder % numColumns) * itemWidth + gridPaddingSide;
        const absoluteX = startX + e.translationX;
        
        const startY = Math.floor(startOrder / numColumns) * itemHeight + headerHeight + gridPaddingTop;
        const absoluteY = startY + e.translationY;
  
        // Calculate column: remove side padding, divide by width
        const relativeX = absoluteX - gridPaddingSide;
        const col = Math.min(Math.max(Math.round(relativeX / itemWidth), 0), numColumns - 1);
        
        // Calculate row: remove top padding AND header height
        const relativeY = absoluteY - headerHeight - gridPaddingTop;
        const row = Math.max(Math.round(relativeY / itemHeight), 0);
        
        const newOrder = row * numColumns + col;
        const safeOrder = Math.min(Math.max(newOrder, 0), totalItems - 1);
  
        const currentOrder = positions.value[id]; // Use live value for check, but not for coord calc
  
        if (safeOrder !== currentOrder) {
          const newPositions = { ...positions.value };
          for (const key in newPositions) {
              const val = newPositions[key];
              // We want to shift items that are BETWEEN the current position and the new target
              // Note: We use 'currentOrder' (where we are logically now) vs 'safeOrder' (target)
              // But visually, the user perceives dragging from 'startOrder' to 'safeOrder'
              // Actually, the shuffle logic needs to be robust. 
              // Standard approach: if we move from A to B, we shift everything in between.
              
              if (currentOrder < safeOrder) {
                  // Moving forward/down
                  // Shift items > current and <= target BACK (-1)
                  if (val > currentOrder && val <= safeOrder) {
                      newPositions[key] = val - 1;
                  }
              } else {
                  // Moving backward/up
                  // Shift items >= target and < current FORWARD (+1)
                  if (val >= safeOrder && val < currentOrder) {
                      newPositions[key] = val + 1;
                  }
              }
          }
          newPositions[id] = safeOrder;
          positions.value = newPositions;
        }
      })
      .onEnd(() => {
        const toIndex = positions.value[id];
        
        if (index !== toIndex) {
          runOnJS(onReorder)(index, toIndex);
        }
        
        // Calculate final target position (where the slot is)
        const newRow = Math.floor(toIndex / numColumns);
        const newCol = toIndex % numColumns;
        const targetX = newCol * itemWidth + gridPaddingSide;
        const targetY = newRow * itemHeight + headerHeight + gridPaddingTop;
  
        // Calculate current visual position (where the finger is)
        // Note: position.value is currently the START slot of the drag (since we didn't update it during active drag)
        const currentVisualX = position.value.x + translateX.value;
        const currentVisualY = position.value.y + translateY.value;
  
        // 1. Shift 'base' position to the new target immediately
        position.value = { x: targetX, y: targetY };
  
        // 2. Adjust translation so the item visually stays at the finger location
        translateX.value = currentVisualX - targetX;
        translateY.value = currentVisualY - targetY;
  
        // 3. Animate translation to 0 (slide to slot)
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
        { translateX: position.value.x + translateX.value },
        { translateY: position.value.y + translateY.value },
        { scale: withSpring(isGestureActive.value ? 1.02 : 1, springConfig) } // Reduced scale slightly
      ],
      ...(isGestureActive.value ? {
        shadowColor: "#000",
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.2,
        shadowRadius: 4,
        elevation: 5
      } : {})
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

export const SortableGrid = React.forwardRef<ScrollView, SortableGridProps<any>>(({
  data,
  keyExtractor,
  renderItem,
  itemHeight,
  itemWidth,
  numColumns,
  onReorder,
  contentContainerStyle,
  headerComponent,
  onScroll,
  gridPaddingTop = 0,
  gridPaddingSide = 0
}, ref) => {
  // scrollY is unused by children currently, so we can omit it or pass a dummy if needed by interface?
  // SortableItem expects scrollY prop but doesn't use it. We can pass a dummy shared value or remove it from SortableItem props.
  // For now, let's keep the shared value to satisfy the prop but not update it on scroll to save performance/avoid worklet issues with standard ScrollView.
  const scrollY = useSharedValue(0);
  
  // Header height logic
  const [headerHeight, setHeaderHeight] = useState(0);

  // Map of ID -> Order Index
  const positions = useSharedValue<Record<string, number>>({});

  // Initialize positions when data changes length or keys
  useEffect(() => {
    const newPositions: Record<string, number> = {};
    data.forEach((item, index) => {
      newPositions[keyExtractor(item)] = index;
    });
    positions.value = newPositions;
  }, [data, keyExtractor]); // Dependency on data ensures sync

  const containerHeight = Math.ceil(data.length / numColumns) * itemHeight + headerHeight + gridPaddingTop * 2;
  const isHeaderReady = !headerComponent || headerHeight > 0;

  return (
    <ScrollView
      ref={ref}
      style={{ flex: 1 }}
      contentContainerStyle={[contentContainerStyle, { height: containerHeight }]}
      keyboardShouldPersistTaps="handled"
      onScroll={onScroll}
      scrollEventThrottle={16}
    >
        <HeaderWrapper 
            onLayout={(e: any) => setHeaderHeight(e.nativeEvent.layout.height)}
            style={{ zIndex: 0 }}
        >
            {headerComponent}
        </HeaderWrapper>

      {isHeaderReady && data.map((item, index) => (
        <SortableItem
          key={keyExtractor(item)}
          id={keyExtractor(item)}
          index={index}
          item={item}
          renderItem={renderItem}
          positions={positions}
          scrollY={scrollY}
          itemHeight={itemHeight}
          itemWidth={itemWidth}
          numColumns={numColumns}
          onReorder={onReorder}
          totalItems={data.length}
          headerHeight={headerHeight}
          gridPaddingTop={gridPaddingTop}
          gridPaddingSide={gridPaddingSide}
        />
      ))}
    </ScrollView>
  );
});

// Wrapper to allow ViewStyle as a component for simplicity in the layout measurement
const HeaderWrapper = ({ children, onLayout, style }: any) => (
    <Animated.View onLayout={onLayout} style={style}>{children}</Animated.View>
);