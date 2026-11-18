// Settings › Media: autoplay, picture-in-picture and background playback.
package com.jlees.mibrowser.ui.settings

import com.jlees.mibrowser.BrowserViewModel

fun mediaSection(vm: BrowserViewModel) = SettingsSection("media", "Media", "play_arrow", "How audio and video behave", listOf(
    SettingsRow("Block Video Autoplay", "autoplay video sound media") {
        SwitchItem("block", "Block Video Autoplay", "Videos wait for a tap instead of playing on their own", vm.settings.blockAutoplay) { vm.updateSettings { copy(blockAutoplay = it) } }
    },
    SettingsRow("Picture-in-Picture", "pip floating video window fullscreen") {
        SwitchItem("layers", "Picture-in-Picture", "Fullscreen videos continue playing in picture-in-picture when you leave mi", vm.settings.pictureInPicture) { vm.updateSettings { copy(pictureInPicture = it) } }
    },
    SettingsRow("Background Playback", "audio music podcast notification lock screen") {
        SwitchItem("play_arrow", "Background Playback", "Audio and video continue playing when you leave mi", vm.settings.backgroundAudio) { vm.updateSettings { copy(backgroundAudio = it) } }
    },
))
