/**
 * Design System - Card Component
 * Unified card container with consistent styling
 */

import React from 'react';
import { View, ViewStyle } from 'react-native';
import { createCardStyles, StyleConfig } from '../styles';

export interface CardProps {
    children: React.ReactNode;
    variant?: 'container' | 'row';
    style?: ViewStyle;
    config: StyleConfig;
}

export const Card: React.FC<CardProps> = ({
    children,
    variant = 'container',
    style,
    config,
}) => {
    const styles = createCardStyles(config);

    const cardStyle = [
        variant === 'container' && styles.container,
        variant === 'row' && styles.row,
        style,
    ];

    return <View style={cardStyle}>{children}</View>;
};
