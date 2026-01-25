export interface HistoryItem {
  id: string;
  url: string;
  title: string;
  timestamp: number;
}

export interface TabItem {
  id: string;
  url: string | null;
  requestedUrl?: string | null;
  initialUrl?: string | null;
  title: string;
  showLogo: boolean;
  loading?: boolean;
  canGoBack?: boolean;
  canGoForward?: boolean;
  hasLoadedOnce?: boolean;
  desktopMode?: boolean;
  readerMode?: boolean;
  previewImage?: string;
  showPreview?: boolean;
  historyStack?: string[];
  currentIndex?: number;
  isCustomTitle?: boolean;
}

export interface BookmarkItem {
  id: string;
  type: "bookmark";
  url: string;
  title: string;
  parentId: string | null;
  order: number;
}

export interface BookmarkFolder {
  id: string;
  type: "folder";
  title: string;
  parentId: string | null;
  order: number;
}

export type BookmarkNode = BookmarkItem | BookmarkFolder;