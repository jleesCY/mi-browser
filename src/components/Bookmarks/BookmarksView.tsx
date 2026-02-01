import React, { useState, useMemo, useEffect, useRef } from 'react';
import { View, Text, TextInput, TouchableOpacity, Keyboard, Modal, BackHandler, Animated, ScrollView } from 'react-native';
import { Ionicons } from "@expo/vector-icons";
import { SortableGrid } from '../Tabs/SortableGrid';
import { BookmarkNode, BookmarkItem, BookmarkFolder } from '../../types';
import { BookmarkRow } from './BookmarkRow';
import { SCREEN_WIDTH, SNAP_DEFAULT } from '../../constants';

interface BookmarksViewProps {
  bookmarks: BookmarkNode[];
  activeUrl: string | null;
  activeTitle: string | null;
  theme: any;
  accentColor: string;
  cornerRadius: number;
  fontScale: number;
  uiPadding: string;
  onPressItem: (item: BookmarkItem) => void;
  onAddBookmark: (title: string, url: string, parentId: string | null) => void;
  onAddFolder: (title: string, parentId: string | null) => void;
  onDeleteBookmark: (id: string) => void;
  onUpdateBookmark: (id: string, updates: Partial<BookmarkNode>) => void;
  onMoveBookmark: (id: string, newParentId: string | null) => void;
  onReorderBookmarks: (parentId: string | null, from: number, to: number) => void;
  onFocusSearch: () => void;
  autoAdd?: boolean;
  onAutoAddHandled?: () => void;
  overlayHeightAnim: Animated.Value;
  showIcons?: boolean;
}

export const BookmarksView: React.FC<BookmarksViewProps> = ({
  bookmarks,
  activeUrl,
  activeTitle,
  theme,
  accentColor,
  cornerRadius,
  fontScale,
  uiPadding,
  onPressItem,
  onAddBookmark,
  onAddFolder,
  onDeleteBookmark,
  onUpdateBookmark,
  onMoveBookmark,
  onReorderBookmarks,
  onFocusSearch,
  autoAdd,
  onAutoAddHandled,
  overlayHeightAnim,
  showIcons = true
}) => {
  useEffect(() => {
    console.log("=== BOOKMARKS VIEW OPENED ===");
    console.log(`Total Bookmarks: ${bookmarks.length}`);
    console.log("Bookmarks Data:", JSON.stringify(bookmarks.map(b => ({
        id: b.id,
        title: b.title,
        type: b.type,
        url: b.type === 'bookmark' ? (b as any).url : undefined,
        parentId: b.parentId
    })), null, 2));
  }, []);

  const [currentFolderId, setCurrentFolderId] = useState<string | null>(null);
  const [folderStack, setFolderStack] = useState<{id: string, title: string}[]>([]);
  const [searchText, setSearchText] = useState("");
  const [isKeyboardVisible, setIsKeyboardVisible] = useState(false);
  const [showScrollTop, setShowScrollTop] = useState(false);
  
  const searchInputRef = useRef<TextInput>(null);
  const titleInputRef = useRef<TextInput>(null);
  const urlInputRef = useRef<TextInput>(null);
  const scrollViewRef = useRef<ScrollView>(null);
  
  // Modal State
  const [modalVisible, setModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'add_bookmark' | 'add_folder' | 'edit'>('add_bookmark');
  const [editingItem, setEditingItem] = useState<BookmarkNode | null>(null);
  const [inputTitle, setInputTitle] = useState("");
  const [inputUrl, setInputUrl] = useState("");
  const [modalFolderId, setModalFolderId] = useState<string | null>(null);
  const [showFolderPicker, setShowFolderPicker] = useState(false);

  useEffect(() => {
    const showSub = Keyboard.addListener("keyboardDidShow", () => setIsKeyboardVisible(true));
    const hideSub = Keyboard.addListener("keyboardDidHide", () => setIsKeyboardVisible(false));
    return () => {
      showSub.remove();
      hideSub.remove();
    };
  }, []);

  useEffect(() => {
      if (autoAdd && onAutoAddHandled) {
          openAddBookmarkModal();
          onAutoAddHandled();
      }
  }, [autoAdd]);

  const getHierarchyPath = () => {
      const path = ["Bookmarks", ...folderStack.map(f => f.title)];
      return "/" + path.join("/");
  };

  // Helper to get folder name by ID
  const getFolderName = (id: string | null) => {
      if (!id) return "Bookmarks";
      const folder = bookmarks.find(b => b.id === id);
      return folder ? folder.title : "Unknown";
  };

  // Filter & Sort
  const visibleBookmarks = useMemo(() => {
    let filtered = bookmarks;
    
    if (searchText.trim() !== "") {
      // Flatten search
      return filtered.filter(b => 
          b.title.toLowerCase().includes(searchText.toLowerCase()) || 
          (b.type === 'bookmark' && b.url.toLowerCase().includes(searchText.toLowerCase()))
      );
    } else {
      // Hierarchy
      return filtered
        .filter(b => b.parentId === currentFolderId)
        .sort((a, b) => a.order - b.order);
    }
  }, [bookmarks, currentFolderId, searchText]);

  // Back handling within folder structure
  useEffect(() => {
    const onBackPress = () => {
      if (modalVisible) {
          if (showFolderPicker) {
              setShowFolderPicker(false);
              return true;
          }
          setModalVisible(false);
          return true;
      }
      if (searchText !== "") {
          setSearchText("");
          return true;
      }
      if (folderStack.length > 0) {
        handleGoBack();
        return true;
      }
      return false; // Let parent handle it (close overlay)
    };
    
    const sub = BackHandler.addEventListener('hardwareBackPress', onBackPress);
    return () => sub.remove();
  }, [folderStack, searchText, modalVisible, showFolderPicker]);

  const handleGoBack = () => {
      const newStack = [...folderStack];
      newStack.pop();
      setFolderStack(newStack);
      setCurrentFolderId(newStack.length > 0 ? newStack[newStack.length - 1].id : null);
  };

  const handleEnterFolder = (folder: BookmarkFolder) => {
      setFolderStack([...folderStack, { id: folder.id, title: folder.title }]);
      setCurrentFolderId(folder.id);
  };

  const openAddBookmarkModal = () => {
      setModalMode('add_bookmark');
      setInputTitle(activeTitle || "");
      setInputUrl(activeUrl || "");
      setModalFolderId(currentFolderId);
      setShowFolderPicker(false);
      setModalVisible(true);
  };

  const openAddFolderModal = () => {
      setModalMode('add_folder');
      setInputTitle("New Folder");
      setInputUrl("");
      setModalFolderId(currentFolderId);
      setShowFolderPicker(false);
      setModalVisible(true);
  };

  const openEditModal = (item: BookmarkNode) => {
      setModalMode('edit');
      setEditingItem(item);
      setInputTitle(item.title);
      setInputUrl(item.type === 'bookmark' ? item.url : "");
      setModalFolderId(item.parentId);
      setShowFolderPicker(false);
      setModalVisible(true);
  };

  const handleSave = () => {
      if (modalMode === 'add_bookmark') {
          onAddBookmark(inputTitle, inputUrl, modalFolderId);
      } else if (modalMode === 'add_folder') {
          onAddFolder(inputTitle, modalFolderId);
      } else if (modalMode === 'edit' && editingItem) {
          const updates: any = { title: inputTitle };
          if (editingItem.type === 'bookmark') updates.url = inputUrl;
          
          // Only update parent if changed (and handle move logic if needed by hook)
          if (modalFolderId !== editingItem.parentId) {
              onMoveBookmark(editingItem.id, modalFolderId);
          }
          
          onUpdateBookmark(editingItem.id, updates);
      }
      setModalVisible(false);
  };

  // Helper to build full path for a folder
  const buildFolderPath = (folderId: string | null, allBookmarks: BookmarkNode[]): string => {
      if (!folderId) return "Bookmarks (Root)";
      
      const path: string[] = [];
      let currentId = folderId;
      
      // Safety break counter
      let depth = 0;
      while (currentId && depth < 10) {
          const folder = allBookmarks.find(b => b.id === currentId);
          if (folder) {
              path.unshift(folder.title);
              currentId = folder.parentId;
          } else {
              break;
          }
          depth++;
      }
      
      return path.join(" / ");
  };

  // Get available folders for picker (prevent cycles) with hierarchy
  const availableFolderOptions = useMemo(() => {
      const allFolders = bookmarks.filter(b => b.type === 'folder');
      let candidates = allFolders;
      
      if (modalMode === 'edit' && editingItem?.type === 'folder') {
          // Exclude self and children to prevent cycles
          const excludeIds = new Set<string>();
          const findChildren = (targetId: string) => {
            excludeIds.add(targetId);
            const children = bookmarks.filter(b => b.parentId === targetId);
            children.forEach(c => findChildren(c.id));
          };
          findChildren(editingItem.id);
          
          candidates = allFolders.filter(f => !excludeIds.has(f.id));
      }
      
      // Map to include full hierarchy path
      // We also include the Root option manually in the final list, so just process candidates here
      return candidates.map(f => ({
          ...f,
          hierarchyTitle: buildFolderPath(f.id, bookmarks)
      })).sort((a, b) => a.hierarchyTitle.localeCompare(b.hierarchyTitle));
      
  }, [bookmarks, modalMode, editingItem]);

  // Dimensions
  const rowHeightItem = 60 * fontScale;
  const rowMargin = 10;
  const rowTotalHeight = rowHeightItem + rowMargin;
  const slotWidth = SCREEN_WIDTH - 40;

  const rowTheme = {
    ...theme,
    surface: theme.card,
    bg: theme.card,
  };

  return (
    <View style={{ flex: 1 }}>
      {/* HEADER */}
      <View style={{ 
          paddingHorizontal: 20, 
          paddingTop: 10,
          zIndex: 10
      }}>
        <View style={{
            flexDirection: 'row',
            alignItems: 'center',
            marginBottom: 10
        }}>
           {folderStack.length > 0 && (
               <TouchableOpacity onPress={handleGoBack} style={{ marginRight: 10 }}>
                   <Ionicons name="arrow-back" size={24} color={theme.text} />
               </TouchableOpacity>
           )}
           <Text style={{ 
               color: theme.text, 
               fontFamily: "Nunito_700Bold", 
               fontSize: 20 * fontScale 
           }}>
               {searchText ? "Search Results" : getHierarchyPath()}
           </Text>
        </View>

        <View
            style={{
                marginBottom: 10,
                backgroundColor: theme.card,
                borderRadius: cornerRadius,
                flexDirection: "row",
                alignItems: "center",
                paddingHorizontal: 15,
                height: 50,
            }}
            >
            <Ionicons
                name="search"
                size={20}
                color={theme.textSec}
                style={{ marginRight: 10 }}
            />
            <TextInput
                ref={searchInputRef}
                style={{
                    flex: 1,
                    color: theme.text,
                    fontFamily: "Nunito_600SemiBold",
                    fontSize: 16,
                }}
                placeholder="Search Bookmarks..."
                placeholderTextColor={theme.textSec}
                value={searchText}
                onFocus={onFocusSearch}
                onChangeText={(text) => setSearchText(text)}
            />
             {searchText !== "" && (
                <TouchableOpacity onPress={() => {
                    setSearchText("");
                    searchInputRef.current?.focus();
                }}>
                <Ionicons name="close-circle" size={20} color={theme.textSec} />
                </TouchableOpacity>
            )}
        </View>
      </View>

      {/* LIST */}
      <SortableGrid
        ref={scrollViewRef}
        data={visibleBookmarks}
        keyExtractor={(item) => item.id}
        numColumns={1}
        itemHeight={rowTotalHeight}
        itemWidth={slotWidth}
        gridPaddingTop={10}
        gridPaddingSide={20}
        contentContainerStyle={{ paddingBottom: 100, paddingHorizontal: 20 }}
        onScroll={(e) => {
            const offsetY = e.nativeEvent.contentOffset.y;
            if (offsetY > 100 && !showScrollTop) {
                setShowScrollTop(true);
            } else if (offsetY <= 100 && showScrollTop) {
                setShowScrollTop(false);
            }
        }}
        onReorder={(from, to) => {
            if (searchText === "") {
                onReorderBookmarks(currentFolderId, from, to);
            }
        }}
        renderItem={({ item, isActive }) => (
            <BookmarkRow
                item={item}
                theme={rowTheme}
                accent={accentColor}
                radius={cornerRadius}
                height={rowHeightItem}
                margin={rowMargin}
                fontScale={fontScale}
                showIcon={showIcons}
                onPress={() => {
                    if (item.type === 'folder') {
                        handleEnterFolder(item);
                    } else {
                        onPressItem(item);
                    }
                }}
                onDelete={() => onDeleteBookmark(item.id)}
                onRename={() => openEditModal(item)}
            />
        )}
      />

      {/* FABs */}
      {searchText === "" && (
          <Animated.View 
            pointerEvents={isKeyboardVisible ? "none" : "auto"}
            style={{
                position: 'absolute',
                bottom: overlayHeightAnim.interpolate({
                    inputRange: [0, SNAP_DEFAULT],
                    outputRange: [-100, 20],
                    extrapolate: 'clamp'
                }),
                right: 20,
                alignItems: 'flex-end', // Stack vertically, align to right
                opacity: isKeyboardVisible ? 0 : 1
            }}
          >
             {showScrollTop && (
                <TouchableOpacity
                    style={{
                    width: 40,
                    height: 40,
                    borderRadius: 20,
                    backgroundColor: theme.card,
                    justifyContent: 'center',
                    alignItems: 'center',
                    marginBottom: 15,
                    marginRight: 8, // Center over rightmost FAB
                    shadowColor: "#000",
                    shadowOffset: { width: 0, height: 2 },
                    shadowOpacity: 0.25,
                    shadowRadius: 3.84,
                    elevation: 5,
                    borderWidth: 1,
                    borderColor: theme.bg
                    }}
                    onPress={() => scrollViewRef.current?.scrollTo({ y: 0, animated: true })}
                >
                    <Ionicons name="arrow-up" size={24} color={theme.text} />
                </TouchableOpacity>
             )}

             <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <TouchableOpacity
                    style={{
                        width: 56,
                        height: 56,
                        borderRadius: 28,
                        backgroundColor: theme.card,
                        justifyContent: 'center',
                        alignItems: 'center',
                        marginRight: 15,
                        shadowColor: "#000",
                        shadowOffset: { width: 0, height: 4 },
                        shadowOpacity: 0.3,
                        shadowRadius: 4.65,
                        elevation: 8,
                    }}
                    onPress={openAddFolderModal}
                >
                    <Ionicons name="folder-outline" size={28} color={theme.text} />
                </TouchableOpacity>

                <TouchableOpacity
                    style={{
                        width: 56,
                        height: 56,
                        borderRadius: 28,
                        backgroundColor: accentColor,
                        justifyContent: 'center',
                        alignItems: 'center',
                        shadowColor: "#000",
                        shadowOffset: { width: 0, height: 4 },
                        shadowOpacity: 0.3,
                        shadowRadius: 4.65,
                        elevation: 8,
                    }}
                    onPress={openAddBookmarkModal}
                >
                    <Ionicons name="add" size={32} color="#fff" />
                </TouchableOpacity>
             </View>
          </Animated.View>
      )}

      {/* MODAL */}
      <Modal
        visible={modalVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setModalVisible(false)}
      >
          <View style={{
              flex: 1,
              backgroundColor: 'rgba(0,0,0,0.5)',
              justifyContent: 'center',
              alignItems: 'center',
              padding: 20
          }}>
              <View style={{
                  width: '100%',
                  backgroundColor: theme.surface,
                  borderRadius: cornerRadius,
                  padding: 20,
                  elevation: 5
              }}>
                  <Text style={{
                      color: theme.text,
                      fontFamily: "Nunito_700Bold",
                      fontSize: 18,
                      marginBottom: 15
                  }}>
                      {modalMode === 'add_bookmark' ? "Add Bookmark" : 
                       modalMode === 'add_folder' ? "New Folder" : "Edit Item"}
                  </Text>

                  <Text style={{color: theme.textSec, marginBottom: 5}}>Title</Text>
                  <View style={{
                      backgroundColor: theme.inputBg,
                      borderRadius: 8,
                      marginBottom: 15,
                      flexDirection: 'row',
                      alignItems: 'center',
                      paddingRight: 10
                  }}>
                      <TextInput
                          ref={titleInputRef}
                          style={{
                              flex: 1,
                              color: theme.text,
                              padding: 10,
                          }}
                          value={inputTitle}
                          onChangeText={setInputTitle}
                          autoFocus={!showFolderPicker}
                      />
                      {inputTitle.length > 0 && (
                          <TouchableOpacity onPress={() => {
                              setInputTitle("");
                              titleInputRef.current?.focus();
                          }}>
                              <Ionicons name="close-circle" size={20} color={theme.textSec} />
                          </TouchableOpacity>
                      )}
                  </View>

                  {(modalMode === 'add_bookmark' || (modalMode === 'edit' && editingItem?.type === 'bookmark')) && (
                      <>
                        <Text style={{color: theme.textSec, marginBottom: 5}}>URL</Text>
                        <View style={{
                            backgroundColor: theme.inputBg,
                            borderRadius: 8,
                            marginBottom: 15,
                            flexDirection: 'row',
                            alignItems: 'center',
                            paddingRight: 10
                        }}>
                            <TextInput
                                ref={urlInputRef}
                                style={{
                                    flex: 1,
                                    color: theme.text,
                                    padding: 10,
                                }}
                                value={inputUrl}
                                onChangeText={setInputUrl}
                                autoCapitalize="none"
                            />
                            {inputUrl.length > 0 && (
                                <TouchableOpacity onPress={() => {
                                    setInputUrl("");
                                    urlInputRef.current?.focus();
                                }}>
                                    <Ionicons name="close-circle" size={20} color={theme.textSec} />
                                </TouchableOpacity>
                            )}
                        </View>
                      </>
                  )}
                  
                  <Text style={{color: theme.textSec, marginBottom: 5}}>Location</Text>
                  <TouchableOpacity
                    onPress={() => setShowFolderPicker(true)}
                    style={{
                        backgroundColor: theme.inputBg,
                        padding: 10,
                        borderRadius: 8,
                        marginBottom: 15,
                        flexDirection: 'row',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                    }}
                  >
                      <Text style={{ color: theme.text, fontFamily: 'Nunito_600SemiBold' }}>
                          {getFolderName(modalFolderId)}
                      </Text>
                      <Ionicons name="chevron-forward" size={20} color={theme.textSec} />
                  </TouchableOpacity>
                  
                  {/* FOLDER PICKER OVERLAY */}
                  {showFolderPicker && (
                      <View style={{
                          position: 'absolute',
                          top: 0, left: 0, right: 0, bottom: 0,
                          backgroundColor: theme.surface,
                          borderRadius: cornerRadius,
                          zIndex: 10,
                          padding: 20
                      }}>
                           <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 15 }}>
                               <TouchableOpacity onPress={() => setShowFolderPicker(false)} style={{ marginRight: 10 }}>
                                   <Ionicons name="arrow-back" size={24} color={theme.text} />
                               </TouchableOpacity>
                               <Text style={{ color: theme.text, fontFamily: "Nunito_700Bold", fontSize: 18 }}>
                                   Select Location
                               </Text>
                           </View>
                           <SortableGrid
                               data={[
                                   { id: "", title: "Bookmarks (Root)", hierarchyTitle: "Bookmarks (Root)", type: 'folder' }, 
                                   ...availableFolderOptions
                               ]}
                               keyExtractor={(item: any) => item.id || "root"}
                               numColumns={1}
                               itemHeight={70} // Increased height for multiline
                               itemWidth={SCREEN_WIDTH - 80} // Approx modal width
                               gridPaddingTop={0}
                               gridPaddingSide={0}
                               onReorder={() => {}} // No reordering here
                               renderItem={({ item }: any) => (
                                   <TouchableOpacity
                                     onPress={() => {
                                         setModalFolderId(item.id === "" ? null : item.id);
                                         setShowFolderPicker(false);
                                     }}
                                     style={{
                                         height: 70, // Match itemHeight
                                         flexDirection: 'row',
                                         alignItems: 'center',
                                         borderBottomWidth: 1,
                                         borderBottomColor: theme.bg,
                                         paddingVertical: 5
                                     }}
                                   >
                                       <Ionicons 
                                            name={item.id === "" ? "home-outline" : "folder-outline"} 
                                            size={20} 
                                            color={theme.textSec} 
                                            style={{ marginRight: 10 }}
                                       />
                                       <Text style={{ 
                                           flex: 1, // Allow wrapping
                                           color: modalFolderId === (item.id === "" ? null : item.id) ? accentColor : theme.text,
                                           fontFamily: "Nunito_600SemiBold",
                                           fontSize: 14,
                                           paddingRight: 10
                                       }} numberOfLines={3} ellipsizeMode="tail">
                                           {item.hierarchyTitle || item.title}
                                       </Text>
                                       {modalFolderId === (item.id === "" ? null : item.id) && (
                                           <Ionicons name="checkmark" size={20} color={accentColor} style={{ marginLeft: 'auto' }} />
                                       )}
                                   </TouchableOpacity>
                               )}
                           />
                      </View>
                  )}

                  <View style={{ flexDirection: 'row', justifyContent: 'flex-end', marginTop: 10 }}>
                      <TouchableOpacity 
                        onPress={() => setModalVisible(false)}
                        style={{ padding: 10, marginRight: 10 }}
                      >
                          <Text style={{ color: theme.textSec, fontFamily: "Nunito_600SemiBold" }}>Cancel</Text>
                      </TouchableOpacity>
                      <TouchableOpacity 
                        onPress={handleSave}
                        style={{ 
                            padding: 10, 
                            backgroundColor: accentColor, 
                            borderRadius: 8 
                        }}
                      >
                          <Text style={{ color: '#fff', fontFamily: "Nunito_700Bold" }}>Save</Text>
                      </TouchableOpacity>
                  </View>
              </View>
          </View>
      </Modal>

    </View>
  );
};
