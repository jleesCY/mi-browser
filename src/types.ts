export interface HistoryItem { 
    id: string; 
    url: string; 
    title: string; 
    timestamp: number; 
}
export interface TabItem { 
    id: string; 
    url: string | null; 
    title: string; 
    showLogo: boolean; 
    loading?: boolean;
    canGoBack?: boolean;
    canGoForward?: boolean;
    isIncognito?: boolean; 
}