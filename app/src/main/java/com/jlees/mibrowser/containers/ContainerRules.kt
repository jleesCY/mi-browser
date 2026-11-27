// Profiles (named "containers" in the code, so they aren't mistaken for WebView's own profiles): spaces shown by an
// icon and a colour, each with its own tabs, tab groups, history and recent searches, and, if asked for ("Keep Sign-ins Separate"),
// its own cookies, sign-ins and site storage (its own WebView profile, kept on the phone). The profile you're in is the
// one the current tab belongs to; the tab switcher shows only its tabs, and switches from the profiles at the start of its chip row.
// Default is the one you start in (no container). Bookmarks and settings stay shared. Profiles can be turned off
// (Settings › Tabs › Profiles), and then everything shows together. These are the decisions; the WebView profiles
// themselves are ContainerProfiles' job.
package com.jlees.mibrowser.containers

import com.jlees.mibrowser.data.TabItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A profile: its [icon] and [color] stand for it (its name is the icon's); [isolated]: its own cookies and storage (else Default's). */
@Serializable
data class Container(val id: String, val icon: ProfileIcon = ProfileIcon.WORK, val color: ProfileColor = ProfileColor.BLUE, val isolated: Boolean = false)

const val CONTAINER_PROFILE_PREFIX = "container-"

/** The WebView profile that keeps profile [id]'s cookies and storage, when it keeps its own. */
fun containerProfile(id: String) = CONTAINER_PROFILE_PREFIX + id

/** A profile's icon ([symbol]: its Material Symbol) and the name it gives the profile. */
@Serializable
enum class ProfileIcon(val symbol: String, val label: String) {
    @SerialName("web") WEB("public", "Web"),
    @SerialName("work") WORK("work", "Work"),
    @SerialName("home") HOME("home", "Home"),
    @SerialName("school") SCHOOL("school", "School"),
    @SerialName("shopping") SHOPPING("shopping_cart", "Shopping"),
    @SerialName("games") GAMES("sports_esports", "Games"),
    @SerialName("travel") TRAVEL("flight", "Travel"),
    @SerialName("personal") PERSONAL("favorite", "Personal"),
    @SerialName("music") MUSIC("music_note", "Music"),
    @SerialName("money") MONEY("payments", "Money"),
    @SerialName("pets") PETS("pets", "Pets"),
    @SerialName("favorites") FAVORITES("star", "Favorites"),
}

/** A profile's colour: the mi. accent swatches ([argb]), or the app's accent itself (null: Default's, at first). */
@Serializable
enum class ProfileColor(val argb: Long?, val label: String) {
    @SerialName("accent") ACCENT(null, "Accent"),
    @SerialName("blue") BLUE(0xFF3D9BFF, "Blue"),
    @SerialName("green") GREEN(0xFF34C759, "Green"),
    @SerialName("red") RED(0xFFFF3B30, "Red"),
    @SerialName("orange") ORANGE(0xFFFF9500, "Orange"),
    @SerialName("yellow") YELLOW(0xFFFFCC00, "Yellow"),
    @SerialName("purple") PURPLE(0xFFAF52DE, "Purple"),
    @SerialName("pink") PINK(0xFFFF2D55, "Pink"),
    @SerialName("mint") MINT(0xFF00C7BE, "Mint"),
    @SerialName("gray") GRAY(0xFF8E8E93, "Gray"),
}

/** A new profile's look: the first icon and colour no profile has yet (Default's Web and Accent kept for it). */
fun newProfileLook(containers: List<Container>): Pair<ProfileIcon, ProfileColor> =
    (ProfileIcon.entries.drop(1).firstOrNull { i -> containers.none { it.icon == i } } ?: ProfileIcon.WORK) to
        (ProfileColor.entries.drop(1).firstOrNull { c -> containers.none { it.color == c } } ?: ProfileColor.BLUE)

/** What a profile is called: Default, or its icon's name. */
fun profileName(c: Container?): String = c?.icon?.label ?: "Default"

/**
 * The profile a tab opened from [opener] (a link, a pop-up, a new tab from the switcher) goes in: the opener's, so you
 * stay in the profile you're in and a page's sign-in carries on. None (Default) for a profile that's gone. An incognito
 * tab belongs to a profile too, to be listed with its tabs, though it keeps its own private storage.
 */
fun inheritedContainer(opener: TabItem?, containers: List<Container>): String? =
    opener?.container?.takeIf { id -> containers.any { it.id == id } }

/**
 * The tabs the tab switcher shows while [profile] (null: Default) is the one you're in: its own, or (profiles turned
 * off, [enabled] false) every tab.
 */
fun profileTabs(tabs: List<TabItem>, profile: String?, enabled: Boolean = true): List<TabItem> =
    if (enabled) tabs.filter { it.container == profile } else tabs

/** The tab a switch to [profile] shows: its most recently used, or none (then a new tab starts it). */
fun tabToShow(tabs: List<TabItem>, profile: String?): TabItem? = profileTabs(tabs, profile).maxByOrNull { it.lastUsed }

/** A tab's profile, if it still exists (a deleted profile's tabs are in Default). */
fun containerOf(tab: TabItem?, containers: List<Container>): Container? =
    tab?.container?.let { id -> containers.firstOrNull { it.id == id } }

/** The profile whose own storage tab's container [id] browses in: only one that keeps sign-ins separate. */
fun isolatedProfile(id: String?, containers: List<Container>): String? =
    id?.takeIf { c -> containers.any { it.id == c && it.isolated } }

/**
 * The profiles' storage on the phone ([profileNames]) to delete: a deleted profile's, or one that no longer keeps its
 * sign-ins separate (its tabs browse with Default's now).
 */
fun orphanedProfiles(profileNames: Collection<String>, containers: List<Container>): List<String> =
    profileNames.filter { name -> name.startsWith(CONTAINER_PROFILE_PREFIX) && containers.none { it.isolated && containerProfile(it.id) == name } }
