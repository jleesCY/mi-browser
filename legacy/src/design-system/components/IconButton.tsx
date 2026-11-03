/**
 * Design System - Icon Button Component
 * Unified icon button with consistent touch targets
 */

import { Ionicons } from '@expo/vector-icons';
import React from 'react';
import { TouchableOpacity, ViewStyle } from 'react-native';
import { createButtonStyles, StyleConfig } from '../styles';
import { iconSizes } from '../tokens';

export interface IconButtonProps {
    icon: keyof typeof Ionicons.glyphMap;
    onPress: () => void;
    color?: string;
    size?: keyof typeof iconSizes;
    disabled?: boolean;
    style?: ViewStyle;
    config: StyleConfig;
}

export const IconButton: React.FC<IconButtonProps> = ({
    icon,
    onPress,
    color,
    size = 'md',
    disabled = false,
    style,
    config,
}) => {
    const styles = createButtonStyles(config);
    const iconColor = color || config.theme.text;

    return (
        <TouchableOpacity
            onPress={onPress}
            disabled={disabled}
            style={[styles.icon, disabled && styles.disabled, style]}
            activeOpacity={0.7}
        >
            <Ionicons name={icon} size={iconSizes[size]} color={iconColor} />
        </TouchableOpacity>
    );
};
