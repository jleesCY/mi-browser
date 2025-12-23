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
    // New transient state for Multi-WebView support
    loading?: boolean;
    canGoBack?: boolean;
    canGoForward?: boolean;
}