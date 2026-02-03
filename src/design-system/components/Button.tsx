/**
 * Design System - Button Component
 * Unified button with consistent styling across the app
 */

import React from 'react';
import { ActivityIndicator, Text, TextStyle, TouchableOpacity, ViewStyle } from 'react-native';
import { createButtonStyles, StyleConfig } from '../styles';

export interface ButtonProps {
    title: string;
    onPress: () => void;
    variant?: 'primary' | 'secondary' | 'tertiary';
    disabled?: boolean;
    loading?: boolean;
    style?: ViewStyle;
    textStyle?: TextStyle;
    config: StyleConfig;
}

export const Button: React.FC<ButtonProps> = ({
    title,
    onPress,
    variant = 'primary',
    disabled = false,
    loading = false,
    style,
    textStyle,
    config,
}) => {
    const styles = createButtonStyles(config);

    const buttonStyle = [
        variant === 'primary' && styles.primary,
        variant === 'secondary' && styles.secondary,
        variant === 'tertiary' && styles.tertiary,
        (disabled || loading) && styles.disabled,
        style,
    ];

    const textStyleFinal = [
        variant === 'primary' && styles.primaryText,
        variant === 'secondary' && styles.secondaryText,
        variant === 'tertiary' && styles.tertiaryText,
        textStyle,
    ];

    return (
        <TouchableOpacity
            onPress={onPress}
            disabled={disabled || loading}
            style={buttonStyle}
            activeOpacity={0.7}
        >
            {loading ? (
                <ActivityIndicator
                    color={variant === 'primary' ? '#FFFFFF' : config.accentColor}
                />
            ) : (
                <Text style={textStyleFinal}>{title}</Text>
            )}
        </TouchableOpacity>
    );
};
