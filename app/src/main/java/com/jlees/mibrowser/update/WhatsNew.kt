// What's New: after every update, mi shows that version's release notes, read from its GitHub release (the same
// notes the release page shows). New installs see the Gesture Walkthrough instead, and nothing is "new" to them yet.
// The rules (when to show, how the notes' markdown becomes blocks) are pure functions below; WhatsNew holds the state.
package com.jlees.mibrowser.update

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.BuildConfig
import com.jlees.mibrowser.ui.ModalFrame
import com.jlees.mibrowser.ui.common.DialogAction
import com.jlees.mibrowser.ui.common.DialogCard
import com.jlees.mibrowser.ui.common.LocalUi
import com.jlees.mibrowser.ui.common.Txt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL

/** What to do at launch about What's New. */
enum class WhatsNewStep { SHOW, RECORD, NONE }

/**
 * [lastSeen]: the version code whose notes were last shown (0: none yet). An update shows the new version's notes; a
 * new install ([walkthroughSeen] still false at its first start) only records its version. Someone updating from
 * before What's New existed has seen the walkthrough, so they get the notes too.
 */
fun whatsNewStep(lastSeen: Int, current: Int, walkthroughSeen: Boolean): WhatsNewStep = when {
    lastSeen >= current -> WhatsNewStep.NONE
    lastSeen == 0 && !walkthroughSeen -> WhatsNewStep.RECORD
    else -> WhatsNewStep.SHOW
}

sealed interface NoteBlock {
    data class Heading(val text: String) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
    data class Bullet(val text: String) : NoteBlock
}

/** A release's notes: its tagline (from the "# mi. X.Y.Z: tagline" title) and the rest, in blocks. */
data class ReleaseNotes(val version: String, val tagline: String?, val blocks: List<NoteBlock>)

private val LINK = Regex("""\[([^\]]+)]\(([^)]+)\)""")

/**
 * The release notes' markdown as blocks: "#" is the title (its tagline kept), "##"/"###" headings, "-"/"*" bullets,
 * and other lines paragraphs (lines in a row join into one). Links keep their text only. Lines for developers go: a
 * line that's only a link ("All changes since…") and the APK's path. **Bold** stays marked for the dialog to draw.
 */
fun parseReleaseNotes(version: String, markdown: String): ReleaseNotes {
    var tagline: String? = null
    val blocks = mutableListOf<NoteBlock>()
    val para = StringBuilder()
    fun endPara() { if (para.isNotBlank()) blocks += NoteBlock.Paragraph(para.toString().trim()); para.clear() }
    for (raw in markdown.lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> endPara()
            LINK.matches(line) || line.contains(".apk") -> endPara()
            line.startsWith("# ") -> { endPara(); tagline = line.removePrefix("# ").substringAfter(": ", "").ifBlank { null } }
            line.startsWith("#") -> { endPara(); blocks += NoteBlock.Heading(clean(line.trimStart('#'))) }
            line.startsWith("- ") || line.startsWith("* ") -> { endPara(); blocks += NoteBlock.Bullet(clean(line.drop(2))) }
            else -> para.append(clean(line)).append(' ')
        }
    }
    endPara()
    return ReleaseNotes(version, tagline, blocks)
}

private fun clean(s: String) = LINK.replace(s.trim()) { it.groupValues[1] }

/** Asks GitHub for the release tagged "v[version]": its notes, null when there's no such release (or no notes). */
internal suspend fun fetchReleaseNotes(version: String): ReleaseNotes? = withContext(Dispatchers.IO) {
    val conn = (URL("https://api.github.com/repos/jleescy/mi-browser/releases/tags/v$version").openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000; readTimeout = 10_000
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    try {
        val body = JSONObject(conn.inputStream.use { it.readBytes().decodeToString() }).optString("body")
        body.takeIf { it.isNotBlank() }?.let { parseReleaseNotes(version, it) }
    } catch (_: FileNotFoundException) { null } // 404: this version has no release
}

/** The notes to show after an update; held by BrowserViewModel, checked once at launch. */
class WhatsNew(private val vm: BrowserViewModel) {
    var notes by mutableStateOf<ReleaseNotes?>(null)
        private set

    fun checkAtLaunch() {
        val current = BuildConfig.VERSION_CODE
        when (whatsNewStep(vm.settings.lastSeenVersion, current, vm.settings.walkthroughSeen)) {
            WhatsNewStep.NONE -> {}
            WhatsNewStep.RECORD -> record(current)
            WhatsNewStep.SHOW -> vm.viewModelScope.launch {
                // offline: try again next launch; no release for this version: nothing to show, ever
                val result = runCatching { fetchReleaseNotes(BuildConfig.VERSION_NAME.removeSuffix("-dev")) }
                if (result.isFailure) return@launch
                val found = result.getOrNull()
                if (found == null || found.blocks.isEmpty()) record(current) else notes = found
            }
        }
    }

    /** Got It: these notes are seen. */
    fun dismiss() { notes = null; record(BuildConfig.VERSION_CODE) }

    private fun record(version: Int) = vm.updateSettings { copy(lastSeenVersion = version) }
}

/** The What's New dialog: the version's tagline, then its notes, in mi's dialog card. */
@Composable
fun WhatsNewDialog(vm: BrowserViewModel) {
    val ui = LocalUi.current
    val shown = vm.whatsNew.notes
    var last by remember { mutableStateOf(shown) }
    if (shown != null) last = shown
    val n = last ?: return
    ModalFrame(shown != null, onDismiss = { vm.whatsNew.dismiss() }) {
        DialogCard("What's New in ${n.version}", actions = { DialogAction("Got It") { vm.whatsNew.dismiss() } }) {
            n.tagline?.let { Txt(it.replaceFirstChar(Char::uppercase), 16f, ui.cs.onSurface, ui.semibold, Modifier.padding(bottom = 12.dp), lineHeight = 22f) }
            n.blocks.forEach { b ->
                when (b) {
                    is NoteBlock.Heading -> Txt(b.text, 15f, ui.cs.onSurface, ui.bold, Modifier.padding(top = 14.dp, bottom = 4.dp))
                    is NoteBlock.Paragraph -> NoteText(b.text, Modifier.padding(bottom = 6.dp))
                    is NoteBlock.Bullet -> Row(Modifier.padding(vertical = 3.dp)) {
                        Txt("•", 14f, ui.cs.primary, ui.bold, Modifier.width(16.dp))
                        NoteText(b.text, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** A line of the notes, its **bold** parts in bold. */
@Composable
private fun NoteText(text: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val styled = buildAnnotatedString {
        text.split("**").forEachIndexed { i, part -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.W600, color = ui.cs.onSurface)) { append(part) } else append(part) }
    }
    androidx.compose.foundation.text.BasicText(
        styled, modifier,
        style = androidx.compose.ui.text.TextStyle(color = ui.cs.onSurfaceVariant, fontSize = (14 * ui.fontScale).sp, lineHeight = (20 * ui.fontScale).sp, fontFamily = ui.font),
    )
}
