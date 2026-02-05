/**
 * Design System Styles
 * Reusable style generators and common patterns
 */

import { StyleSheet, ViewStyle } from 'react-native';
import { borderWidths, getScaledFontSize, getScaledSpacing, opacity, shadows, spacing, typography, withOpacity } from './tokens';

// ============================================
// TYPES
// ============================================

export interface Theme {
    bg: string;
    surface: string;
    glass: string;
    glassBorder: string;
    text: string;
    textSec: string;
    card: string;
    sheetHeader: string;
    inputBg: string;
    placeholder: string;
    isDark: boolean;
    fonts: {
        regular: string;
        semibold: string;
        bold: string;
        extrabold: string;
        light: string;
    };
}

export interface StyleConfig {
    theme: Theme;
    accentColor: string;
    cornerRadius: number;
    uiPadding: 'compact' | 'normal' | 'airy';
    fontScale: number;
}

// ============================================
// LAYOUT HELPERS
// ============================================

/**
 * Flexbox center alignment
 */
export const flexCenter: ViewStyle = {
    alignItems: 'center',
    justifyContent: 'center',
};

/**
 * Flexbox row layout
 */
export const flexRow: ViewStyle = {
    flexDirection: 'row',
    alignItems: 'center',
};

/**
 * Absolute fill container
 */
export const absoluteFill: ViewStyle = {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
};

// ============================================
// BUTTON STYLES
// ============================================

export const createButtonStyles = (config: StyleConfig) => {
    const { theme, accentColor, cornerRadius, uiPadding, fontScale } = config;

    return StyleSheet.create({
        // Primary button (filled with accent color)
        primary: {
            backgroundColor: accentColor,
            paddingHorizontal: getScaledSpacing(spacing.lg, uiPadding),
            paddingVertical: getScaledSpacing(spacing.sm, uiPadding),
            borderRadius: cornerRadius,
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: 44,
            ...shadows.sm,
        },
        primaryText: {
            color: '#FFFFFF',
            fontSize: getScaledFontSize(typography.sizes.base, fontScale),
            fontFamily: theme.fonts.semibold,
        },

        // Secondary button (outlined)
        secondary: {
            backgroundColor: 'transparent',
            paddingHorizontal: getScaledSpacing(spacing.lg, uiPadding),
            paddingVertical: getScaledSpacing(spacing.sm, uiPadding),
            borderRadius: cornerRadius,
            borderWidth: borderWidths.regular,
            borderColor: accentColor,
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: 44,
        },
        secondaryText: {
            color: accentColor,
            fontSize: getScaledFontSize(typography.sizes.base, fontScale),
            fontFamily: theme.fonts.semibold,
        },

        // Tertiary button (text only)
        tertiary: {
            backgroundColor: 'transparent',
            paddingHorizontal: getScaledSpacing(spacing.md, uiPadding),
            paddingVertical: getScaledSpacing(spacing.xs, uiPadding),
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: 44,
        },
        tertiaryText: {
            color: accentColor,
            fontSize: getScaledFontSize(typography.sizes.base, fontScale),
            fontFamily: theme.fonts.regular,
        },

        // Icon button
        icon: {
            width: 44,
            height: 44,
            borderRadius: cornerRadius,
            alignItems: 'center',
            justifyContent: 'center',
        },

        // Disabled state
        disabled: {
            opacity: opacity.disabled,
        },
    });
};

// ============================================
// INPUT STYLES
// ============================================

export const createInputStyles = (config: StyleConfig) => {
    const { theme, accentColor, cornerRadius, uiPadding, fontScale } = config;

    return StyleSheet.create({
        container: {
            backgroundColor: theme.inputBg,
            borderRadius: cornerRadius,
            paddingHorizontal: getScaledSpacing(spacing.md, uiPadding),
            paddingVertical: getScaledSpacing(spacing.sm, uiPadding),
            minHeight: 44,
        },
        containerFocused: {
            borderWidth: borderWidths.regular,
            borderColor: accentColor,
        },
        text: {
            fontSize: getScaledFontSize(typography.sizes.base, fontScale),
            fontFamily: theme.fonts.regular,
            color: theme.text,
        },
        placeholder: {
            color: theme.placeholder,
        },
    });
};

// ============================================
// CARD STYLES
// ============================================

export const createCardStyles = (config: StyleConfig) => {
    const { theme, cornerRadius, uiPadding } = config;

    return StyleSheet.create({
        container: {
            backgroundColor: theme.surface,
            borderRadius: cornerRadius,
            padding: getScaledSpacing(spacing.md, uiPadding),
            ...shadows.sm,
        },
        row: {
            backgroundColor: theme.surface,
            paddingHorizontal: getScaledSpacing(spacing.md, uiPadding),
            paddingVertical: getScaledSpacing(spacing.sm, uiPadding),
            minHeight: 44,
        },
        separator: {
            height: StyleSheet.hairlineWidth,
            backgroundColor: withOpacity(theme.text, opacity.subtle),
        },
    });
};

// ============================================
// TEXT STYLES
// ============================================

export const createTextStyles = (config: StyleConfig) => {
    const { theme, fontScale } = config;

    return StyleSheet.create({
        // Headings
        h1: {
            fontSize: getScaledFontSize(typography.sizes.xxxl, fontScale),
            fontFamily: theme.fonts.extrabold,
            color: theme.text,
        },
        h2: {
            fontSize: getScaledFontSize(typography.sizes.xxl, fontScale),
            fontFamily: theme.fonts.bold,
            color: theme.text,
        },
        h3: {
            fontSize: getScaledFontSize(typography.sizes.xl, fontScale),
            fontFamily: theme.fonts.bold,
            color: theme.text,
        },
        h4: {
            fontSize: getScaledFontSize(typography.sizes.lg, fontScale),
            fontFamily: theme.fonts.semibold,
            color: theme.text,
        },

        // Body text
        body: {
            fontSize: getScaledFontSize(typography.sizes.base, fontScale),
            fontFamily: theme.fonts.regular,
            color: theme.text,
        },
        bodyBold: {
            fontSize: getScaledFontSize(typography.sizes.base, fontScale),
            fontFamily: theme.fonts.bold,
            color: theme.text,
        },

        // Small text
        small: {
            fontSize: getScaledFontSize(typography.sizes.sm, fontScale),
            fontFamily: theme.fonts.regular,
            color: theme.text,
        },
        smallBold: {
            fontSize: getScaledFontSize(typography.sizes.sm, fontScale),
            fontFamily: theme.fonts.semibold,
            color: theme.text,
        },

        // Caption
        caption: {
            fontSize: getScaledFontSize(typography.sizes.xs, fontScale),
            fontFamily: theme.fonts.regular,
            color: theme.textSec,
        },

        // Secondary text color
        secondary: {
            color: theme.textSec,
        },
    });
};

// ============================================
// MODAL/OVERLAY STYLES
// ============================================

export const createModalStyles = (config: StyleConfig) => {
    const { theme, cornerRadius, uiPadding } = config;

    return StyleSheet.create({
        backdrop: {
            ...absoluteFill,
            backgroundColor: withOpacity('#000000', 0.5),
        },
        container: {
            backgroundColor: theme.bg,
            borderTopLeftRadius: cornerRadius,
            borderTopRightRadius: cornerRadius,
            ...shadows.xl,
        },
        header: {
            backgroundColor: theme.sheetHeader,
            paddingHorizontal: getScaledSpacing(spacing.lg, uiPadding),
            paddingVertical: getScaledSpacing(spacing.md, uiPadding),
            borderTopLeftRadius: cornerRadius,
            borderTopRightRadius: cornerRadius,
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            minHeight: 56,
        },
        content: {
            padding: getScaledSpacing(spacing.lg, uiPadding),
        },
    });
};

// ============================================
// SWIPEABLE ACTION STYLES
// ============================================

export const createSwipeActionStyles = (config: StyleConfig) => {
    const { cornerRadius } = config;

    return StyleSheet.create({
        deleteAction: {
            backgroundColor: '#FF3B30',
            justifyContent: 'center',
            alignItems: 'center',
            width: 80,
            borderRadius: cornerRadius / 2,
        },
        editAction: {
            backgroundColor: config.accentColor,
            justifyContent: 'center',
            alignItems: 'center',
            width: 80,
            borderRadius: cornerRadius / 2,
        },
    });
};

// ============================================
// COMMON HELPERS
// ============================================

/**
 * Create separator line style
 */
export const createSeparator = (theme: Theme): ViewStyle => ({
    height: StyleSheet.hairlineWidth,
    backgroundColor: withOpacity(theme.text, opacity.subtle),
});

/**
 * Create glass effect style
 */
export const createGlassEffect = (theme: Theme, cornerRadius: number): ViewStyle => ({
    backgroundColor: theme.glass,
    borderWidth: borderWidths.thin,
    borderColor: theme.glassBorder,
    borderRadius: cornerRadius,
    ...shadows.md,
});
