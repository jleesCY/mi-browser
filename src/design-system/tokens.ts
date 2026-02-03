/**
 * Design System Tokens
 * Central source of truth for all design values
 */

import { TextStyle } from 'react-native';

// ============================================
// SPACING SYSTEM (4px base unit)
// ============================================
export const spacing = {
    xxs: 4,
    xs: 8,
    sm: 12,
    md: 16,
    lg: 20,
    xl: 24,
    xxl: 32,
    xxxl: 40,
    xxxxl: 48,
} as const;

// ============================================
// TYPOGRAPHY SCALE
// ============================================
export const typography = {
    sizes: {
        xs: 12,
        sm: 14,
        base: 16,
        lg: 18,
        xl: 20,
        xxl: 24,
        xxxl: 32,
        xxxxl: 40,
        logo: 60,
    },
    weights: {
        regular: '400' as TextStyle['fontWeight'],
        semibold: '600' as TextStyle['fontWeight'],
        bold: '700' as TextStyle['fontWeight'],
        extrabold: '800' as TextStyle['fontWeight'],
    },
    families: {
        regular: 'Nunito_400Regular',
        semibold: 'Nunito_600SemiBold',
        bold: 'Nunito_700Bold',
        extrabold: 'Nunito_800ExtraBold',
    },
} as const;

// ============================================
// ICON SIZES
// ============================================
export const iconSizes = {
    xs: 16,
    sm: 20,
    md: 24,
    lg: 28,
    xl: 32,
} as const;

// ============================================
// TOUCH TARGETS
// ============================================
export const touchTargets = {
    minimum: 44,
    comfortable: 48,
    large: 56,
} as const;

// ============================================
// BORDER WIDTHS
// ============================================
export const borderWidths = {
    thin: 1,
    regular: 2,
    thick: 3,
} as const;

// ============================================
// OPACITY LEVELS
// ============================================
export const opacity = {
    disabled: 0.4,
    muted: 0.6,
    subtle: 0.1,
    medium: 0.2,
    strong: 0.3,
} as const;

// ============================================
// ANIMATION TIMINGS
// ============================================
export const animations = {
    fastest: 100,
    fast: 150,
    normal: 200,
    slow: 300,
    slowest: 400,
} as const;

// ============================================
// SHADOW SYSTEM (Elevation levels)
// ============================================
export const shadows = {
    none: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 0 },
        shadowOpacity: 0,
        shadowRadius: 0,
        elevation: 0,
    },
    sm: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 1 },
        shadowOpacity: 0.1,
        shadowRadius: 2,
        elevation: 2,
    },
    md: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.15,
        shadowRadius: 4,
        elevation: 4,
    },
    lg: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.2,
        shadowRadius: 8,
        elevation: 8,
    },
    xl: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 8 },
        shadowOpacity: 0.25,
        shadowRadius: 16,
        elevation: 12,
    },
} as const;

// ============================================
// Z-INDEX LAYERS
// ============================================
export const zIndex = {
    base: 0,
    dropdown: 10,
    overlay: 50,
    modal: 100,
    popover: 200,
    toast: 300,
    tooltip: 400,
} as const;

// ============================================
// UI PADDING MULTIPLIERS
// ============================================
export const paddingMultipliers = {
    compact: 0.75,
    normal: 1,
    airy: 1.25,
} as const;

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Get scaled spacing value based on UI padding setting
 */
export const getScaledSpacing = (
    value: number,
    paddingMode: 'compact' | 'normal' | 'airy'
): number => {
    return Math.round(value * paddingMultipliers[paddingMode]);
};

/**
 * Get scaled font size based on font scale setting
 */
export const getScaledFontSize = (
    baseSize: number,
    fontScale: number
): number => {
    return baseSize * fontScale;
};

/**
 * Create alpha color from hex
 */
export const withOpacity = (color: string, opacity: number): string => {
    // Ensure opacity is between 0 and 1
    const clampedOpacity = Math.max(0, Math.min(1, opacity));

    // Convert opacity to hex (00-FF)
    const alpha = Math.round(clampedOpacity * 255)
        .toString(16)
        .padStart(2, '0');

    return `${color}${alpha}`;
};
