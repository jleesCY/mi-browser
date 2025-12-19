import { Ionicons } from '@expo/vector-icons';
import React from 'react';
import { LayoutAnimation, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

interface SettingsSectionProps {
    title: string;
    children: React.ReactNode;
    isExpanded: boolean;
    onToggle: () => void;
    theme: any;
    fontScale: number;
    // If true, we force expansion (used during search)
    forceExpand?: boolean;
    // If false, the entire section is hidden (no search matches)
    isVisible?: boolean;
}

const SettingsSection = ({ 
    title, 
    children, 
    isExpanded, 
    onToggle, 
    theme, 
    fontScale,
    forceExpand = false,
    isVisible = true 
}: SettingsSectionProps) => {
    
    if (!isVisible) return null;

    const showContent = isExpanded || forceExpand;

    return (
        <View style={[styles.container, { borderBottomColor: theme.bg }]}>
            <TouchableOpacity 
                onPress={() => {
                    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
                    onToggle();
                }} 
                style={styles.header}
                disabled={forceExpand} // Disable toggling if search is forcing it open
            >
                <Text style={[styles.headerText, { color: theme.textSec, fontFamily: 'Nunito_700Bold', fontSize: 14 * fontScale }]}>
                    {title}
                </Text>
                {!forceExpand && (
                    <Ionicons 
                        name={showContent ? "chevron-up" : "chevron-down"} 
                        size={16} 
                        color={theme.textSec} 
                    />
                )}
            </TouchableOpacity>
            
            {showContent && (
                <View style={styles.content}>
                    {children}
                </View>
            )}
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        marginBottom: 10,
    },
    header: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingVertical: 10,
        paddingHorizontal: 5,
        marginBottom: 5
    },
    headerText: {
        textTransform: 'uppercase',
        letterSpacing: 1
    },
    content: {
        overflow: 'hidden'
    }
});

export default SettingsSection;