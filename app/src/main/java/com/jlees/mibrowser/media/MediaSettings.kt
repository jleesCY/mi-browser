// Settings → Media: autoplay, picture-in-picture and background playback.
package com.jlees.mibrowser.media

import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SettingsSection
import com.jlees.mibrowser.ui.settings.SwitchItem

fun mediaSection(vm: BrowserViewModel) = SettingsSection("media", "Media", "play_arrow", "Autoplay, picture-in-picture, background playback", listOf(
    SettingsRow("Block Video Autoplay", "autoplay video sound media") {
        SwitchItem("block", "Block Video Autoplay", "Videos wait for a tap instead of playing on their own", vm.settings.blockAutoplay) { vm.updateSettings { copy(blockAutoplay = it) } }
    },
    SettingsRow("Picture-in-Picture", "pip floating video window fullscreen") {
        SwitchItem("layers", "Picture-in-Picture", "A fullscreen video keeps playing in a small window when you leave mi", vm.settings.pictureInPicture) { vm.updateSettings { copy(pictureInPicture = it) } }
    },
    SettingsRow("Background Playback", "audio music podcast notification lock screen") {
        SwitchItem("play_arrow", "Background Playback", "Audio and video keep playing when you leave mi, with controls in a notification", vm.settings.backgroundAudio) { vm.updateSettings { copy(backgroundAudio = it) } }
    },
))
