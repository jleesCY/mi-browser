// What changes profiles (named "containers" in the code) across mi: switching profile, moving a tab into one (its
// WebView made again in that profile's storage, its pages kept), and making, changing and deleting profiles (a deleted
// one's tabs move to Default, and its own cookies and storage go).
package com.jlees.mibrowser.containers

import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.WebCmd
import com.jlees.mibrowser.logic.uniqueId

/** The profile you're in: the one the tab showing belongs to (null: Default). */
val BrowserViewModel.currentProfile: String? get() = tabs.current?.container

/** Profile [id]'s icon and colour (null: Default's). */
fun BrowserViewModel.lookOf(id: String?): Pair<ProfileIcon, ProfileColor> =
    containerOf(id, settings.containers)?.let { it.icon to it.color }
        ?: (settings.defaultProfileIcon to settings.defaultProfileColor)

/** Profile [id]'s name (null: Default). */
fun BrowserViewModel.nameOf(id: String?): String = profileName(containerOf(id, settings.containers))

/** Every profile, Default (null) first, in the order made. */
val BrowserViewModel.profileIds: List<String?> get() = listOf(null) + settings.containers.map { it.id }

/** Shows profile [id] (null: Default): its most recent tab, or a new tab if it has none. */
fun BrowserViewModel.switchProfile(id: String?) {
    if (id == currentProfile) return
    tabToShow(tabs.list, id)?.let { setActiveTab(it.id) } ?: addNewTab(profile = id)
}

/**
 * Tab [tabId] belongs to profile [containerId] (null: Default) from now on; its pages reload there, signed in as that
 * profile is (an incognito tab keeps its own private storage, so it just moves). Moving the tab you're on takes you along.
 */
fun BrowserViewModel.moveToContainer(tabId: String, containerId: String?) {
    val tab = tabs[tabId] ?: return
    if (tab.container == containerId) return
    tabs.setContainer(tabId, containerId)
    if (tab.keepsTraces) web.tryEmit(WebCmd.Rebuild(tabId))
}

/** A new profile shown by [icon] and [color], keeping its own sign-ins if [isolated]; returns it. */
fun BrowserViewModel.newContainer(icon: ProfileIcon, color: ProfileColor, isolated: Boolean): Container {
    val c = Container(uniqueId(), icon, color, isolated)
    updateSettings { copy(containers = containers + c) }
    return c
}

/** Profile [id] (null: Default) is shown by [icon] and [color] from now on. */
fun BrowserViewModel.setLook(id: String?, icon: ProfileIcon, color: ProfileColor) {
    if (id == null) updateSettings { copy(defaultProfileIcon = icon, defaultProfileColor = color) }
    else updateSettings { copy(containers = containers.map { if (it.id == id) it.copy(icon = icon, color = color) else it }) }
}

/**
 * Profile [id] keeps its own sign-ins and site data ([on]) or uses Default's. Its open pages reload in their new storage;
 * turned off, its own storage is deleted.
 */
fun BrowserViewModel.setIsolated(id: String, on: Boolean) {
    updateSettings { copy(containers = containers.map { if (it.id == id) it.copy(isolated = on) else it }) }
    tabs.list.filter { it.container == id && it.keepsTraces }.forEach { web.tryEmit(WebCmd.Rebuild(it.id)) }
    web.tryEmit(WebCmd.ReleaseContainers)
}

/** Profile [id] goes: its tabs move to Default (their pages reload), and its own cookies and site storage are deleted. */
fun BrowserViewModel.deleteContainer(id: String) {
    val inIt = tabs.list.filter { it.container == id }.map { it.id }
    updateSettings { copy(containers = containers.filterNot { it.id == id }) }
    inIt.forEach { moveToContainer(it, null) }
    history.moveProfile(id, null)
    web.tryEmit(WebCmd.ReleaseContainers)
}
