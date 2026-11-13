// What the Pill shows while you type in it: rows inside the Pill, above the field (the Pill's darker area grows up to
// fit them, like a sheet's ⋮ menu).
// An inline answer to what's typed (answers/) sits against the field. The rows run upward above it, the first closest:
// matching search shortcuts while one is typed, quick actions (Paste and go), then recent searches and
// your search engine's suggestions (each one switched in Settings › Pill › While Typing).
package com.jlees.mibrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.search.CopiedLink
import com.jlees.mibrowser.search.shortcutMatches
import com.jlees.mibrowser.search.shortcutName
import com.jlees.mibrowser.answers.AnswerRow
import com.jlees.mibrowser.answers.InlineAnswer
import com.jlees.mibrowser.answers.engineAnswer
import com.jlees.mibrowser.answers.keepsAnswer
import com.jlees.mibrowser.answers.inlineAnswer
import com.jlees.mibrowser.ui.common.*

/** The rows above the Pill's field, at most [maxHeightDp] tall (they scroll past that); nothing when there are none. */
@Composable
fun PillSuggestions(vm: BrowserViewModel, maxHeightDp: Float) {
    val ui = LocalUi.current
    val focusManager = LocalFocusManager.current
    val ctx = LocalContext.current
    val filter = vm.inputUrl
    val activeUrl = vm.activeUrl
    val notTyping = filter.isEmpty() || (activeUrl != null && (filter == activeUrl || filter == displayHost(activeUrl)))
    // Paste and go: a link copied in the last few minutes, offered before anything is typed (see CopiedLink)
    val copied = remember(vm.isInputFocused) { if (vm.isInputFocused) CopiedLink.peek(ctx) else null }?.takeIf { notTyping && vm.settings.quickActions }
    // incognito doesn't show (or add to) your normal recent searches
    val recents = if (!vm.settings.recentSearches || vm.tabs.current?.keepsTraces == false) emptyList()
        else vm.history.recents.filter { notTyping || it.title.contains(filter, ignoreCase = true) }
    // engine suggestions repeating a past search are dropped
    val searched = recents.map { it.title.lowercase() }.toSet()
    // the engine sometimes answers in its suggestions ("= 3.10686 miles", from Google): an answer, not a search
    val suggestions = vm.suggestions.items.filter { it.lowercase() !in searched && !it.startsWith("=") }
    // an inline answer to what's typed (math, units, the time…): pinned against the field, below the rows. mi's own
    // (worked out on the device) first, else the engine's
    val answers = vm.settings.inlineAnswers && !notTyping
    val own = remember(filter, answers) { if (answers) inlineAnswer(filter) else null }
    val fresh = own ?: if (answers) engineAnswer(filter, vm.suggestions.items, vm.suggestions.query) else null
    // between keystrokes that have no answer of their own ("2^" on the way to "2^10"), the last one stays put rather
    // than the Pill shrinking and growing back
    // (a plain holder, not state: remembering it mustn't cause another pass)
    val kept = remember { arrayOfNulls<Any>(2) } // [0] the text it answered, [1] the answer
    val answer = fresh ?: (kept[1] as InlineAnswer?)?.takeIf { answers && keepsAnswer(kept[0] as String, filter) }
    if (fresh != null) { kept[0] = filter; kept[1] = fresh } else if (answer == null) kept.fill(null)
    // typing a search shortcut ("!w"): the shortcuts that match, closest to the field (engine suggestions give way)
    val shortcuts = if (vm.settings.shortcutSuggestions) shortcutMatches(filter, vm.bangs) else emptyList()
    val engineRows = if (shortcuts.isEmpty()) suggestions else emptyList()
    val rows = copied != null || shortcuts.isNotEmpty() || recents.isNotEmpty() || engineRows.isNotEmpty()
    if (!rows && answer == null) return

    Column(Modifier.fillMaxWidth().heightIn(max = maxHeightDp.dp).padding(horizontal = 10.dp)) {
        // reverseLayout: the first item sits at the bottom, against the field, and the list grows (and scrolls) upward
        if (rows) LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), reverseLayout = true, contentPadding = PaddingValues(top = 10.dp)) {
            if (copied != null) item(key = "paste") {
                Row(
                    Modifier.animateItem().fillMaxWidth().touchable {
                        val link = CopiedLink.read(ctx) ?: return@touchable
                        focusManager.clearFocus(); vm.submitInput(link)
                    }.padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym("content_paste", 20f, ui.cs.primary, Modifier.padding(end = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("Paste and go", 15f, ui.cs.onSurface, ui.semibold, maxLines = 1)
                        copied.text?.let { Txt(it, 12f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 1) }
                    }
                }
            }
            // tap a shortcut: it's put in the Pill ("!w "), ready for the search
            items(shortcuts, key = { "b_${it.first}" }) { (key, url) ->
                Row(
                    Modifier.animateItem().fillMaxWidth().touchable { vm.inputUrl = "!$key "; vm.ui.tryEmit(com.jlees.mibrowser.UiEvent.FocusInput) }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // the keys' pills share a width, so the site names line up
                    Box(Modifier.padding(end = 12.dp).widthIn(min = 56.dp).background(ui.cs.primaryContainer, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
                        Txt("!$key", 14f, ui.cs.onPrimaryContainer, ui.bold, maxLines = 1)
                    }
                    Txt(shortcutName(key, url), 15f, ui.cs.onSurface, ui.semibold, Modifier.weight(1f), maxLines = 1)
                }
            }
            items(recents, key = { it.id }) { item ->
                Row(
                    Modifier.animateItem().fillMaxWidth().touchable { vm.inputUrl = item.title; vm.ui.tryEmit(com.jlees.mibrowser.UiEvent.FocusInput) }
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym("history", 20f, ui.cs.onSurfaceVariant, Modifier.padding(end = 14.dp))
                    MatchText(item.title.ifEmpty { "Untitled" }, if (notTyping) "" else filter, Modifier.weight(1f))
                    // the ✕ stays small; the area that takes the tap is a full 48dp square around it
                    Box(Modifier.size(48.dp).clip(CircleShape).touchable { vm.history.removeSearch(item.id) }, contentAlignment = Alignment.Center) {
                        Sym("close", 16f, ui.cs.onSurfaceVariant, label = "Remove search")
                    }
                }
            }
            // search-engine suggestions: tap to search
            items(engineRows, key = { "s_$it" }) { s ->
                Row(
                    Modifier.animateItem().fillMaxWidth().touchable { focusManager.clearFocus(); vm.submitInput(s) }.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym("search", 20f, ui.cs.onSurfaceVariant, Modifier.padding(end = 14.dp))
                    MatchText(s, filter, Modifier.weight(1f))
                    // ↖ puts the suggestion in the Pill without searching, to keep typing from it ("weather" → "weather tomorrow")
                    Box(Modifier.size(48.dp).clip(CircleShape).touchable { vm.inputUrl = s; vm.ui.tryEmit(com.jlees.mibrowser.UiEvent.FocusInput) }, contentAlignment = Alignment.Center) {
                        Sym("arrow_upward", 18f, ui.cs.onSurfaceVariant, Modifier.graphicsLayer { rotationZ = -45f }, label = "Fill in \u201c$s\u201d")
                    }
                }
            }
        }
        if (answer != null) {
            // a faint line where the rows above end and the answer begins
            if (rows) Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(1.dp).background(ui.cs.outlineVariant.copy(alpha = 0.5f)))
            AnswerRow(answer)
        }
    }
}

/** A row's text with the part that matches what's typed in bold, the rest regular (like a search engine's list). */
@Composable
private fun MatchText(text: String, typed: String, modifier: Modifier) {
    val ui = LocalUi.current
    val q = typed.trim()
    val at = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    val shown = buildAnnotatedString {
        if (at < 0) append(text)
        else {
            append(text.substring(0, at))
            withStyle(SpanStyle(fontWeight = ui.extrabold)) { append(text.substring(at, at + q.length)) }
            append(text.substring(at + q.length))
        }
    }
    BasicText(
        shown, modifier, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = ui.cs.onSurface, fontSize = (15 * ui.fontScale).sp, fontFamily = ui.font, fontWeight = ui.regular),
    )
}
