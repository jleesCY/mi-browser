/**
 * Design System - Separator Component
 * Unified separator/divider line
 */

import React from 'react';
import { View, ViewStyle } from 'react-native';
import { createCardStyles, StyleConfig } from '../styles';

export interface SeparatorProps {
    style?: ViewStyle;
    config: StyleConfig;
}

export const Separator: React.FC<SeparatorProps> = ({ style, config }) => {
    const styles = createCardStyles(config);

    return <View style={[styles.separator, style]} />;
};
