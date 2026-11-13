// The inline answer above the Pill's field, and its switch in Settings › Pill › While Typing.
package com.jlees.mibrowser.answers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.ui.common.LocalHaptics
import com.jlees.mibrowser.ui.common.LocalUi
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.jlees.mibrowser.ui.common.Txt
import com.jlees.mibrowser.ui.common.copyText
import com.jlees.mibrowser.ui.common.touchable
import com.jlees.mibrowser.ui.settings.SettingsRow
import com.jlees.mibrowser.ui.settings.SwitchItem

/** The label on a copied answer: Paste and Go doesn't offer it back (CopiedLink). */
const val ANSWER_CLIP = "mi answer"

/** Words answers this long or shorter show large ("Heads"); longer ones at reading size. */
private const val BIG_ANSWER = 18

/** What was asked, small, above the answer; tap to copy the answer (a tick says it's copied). Sits against the field, below any other rows. */
@Composable
fun AnswerRow(a: InlineAnswer) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val haptics = LocalHaptics.current
    // a number (with any unit) is always large and bold, one line, shrinking to fit rather than being cut off
    val numeric = a.answer.firstOrNull()?.let { it.isDigit() || it == '-' } == true
    val big = a.answer.length <= BIG_ANSWER
    Column(
        Modifier.fillMaxWidth().touchable {
            copyText(ctx, a.copy, ANSWER_CLIP)
            haptics?.tick() // copied: felt, not a toast over the keyboard
        }.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
    ) {
        Txt(a.question, 14f, ui.cs.onSurfaceVariant, ui.regular, maxLines = 2)
        if (numeric) BasicText(
            a.answer, Modifier.padding(top = 2.dp).fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.extrabold),
            autoSize = TextAutoSize.StepBased((17 * ui.fontScale).sp, (32 * ui.fontScale).sp, 1.sp),
        )
        else Txt(a.answer, if (big) 32f else 17f, ui.cs.onSurface, if (big) ui.extrabold else ui.semibold, Modifier.padding(top = 2.dp), maxLines = if (big) 1 else 5)
    }
}

/** Settings › Pill › While Typing › Inline Answers. */
fun inlineAnswersRow(vm: BrowserViewModel) = SettingsRow("Inline Answers", "calculator math unit conversion convert time date coin dice answer") {
    SwitchItem("calculate", "Inline Answers", "Math, unit conversions, the time and more, answered above the Pill as you type", vm.settings.inlineAnswers) {
        vm.updateSettings { copy(inlineAnswers = it) }
    }
}
