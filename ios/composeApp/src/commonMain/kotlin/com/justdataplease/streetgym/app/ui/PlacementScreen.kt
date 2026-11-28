package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.StrengthResult
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.level_number
import com.justdataplease.streetgym.app.resources.placement_answer_all
import com.justdataplease.streetgym.app.resources.placement_assumes_label
import com.justdataplease.streetgym.app.resources.placement_better
import com.justdataplease.streetgym.app.resources.placement_better_label
import com.justdataplease.streetgym.app.resources.placement_calculate
import com.justdataplease.streetgym.app.resources.placement_confirm
import com.justdataplease.streetgym.app.resources.placement_how
import com.justdataplease.streetgym.app.resources.placement_kicker
import com.justdataplease.streetgym.app.resources.placement_less
import com.justdataplease.streetgym.app.resources.placement_level_day
import com.justdataplease.streetgym.app.resources.placement_more
import com.justdataplease.streetgym.app.resources.placement_more_pull_ups
import com.justdataplease.streetgym.app.resources.placement_more_push_ups
import com.justdataplease.streetgym.app.resources.placement_more_squats
import com.justdataplease.streetgym.app.resources.placement_no_credit
import com.justdataplease.streetgym.app.resources.placement_opens_label
import com.justdataplease.streetgym.app.resources.placement_prompt
import com.justdataplease.streetgym.app.resources.placement_pull_ups
import com.justdataplease.streetgym.app.resources.placement_pull_ups_short
import com.justdataplease.streetgym.app.resources.placement_push_ups
import com.justdataplease.streetgym.app.resources.placement_push_ups_short
import com.justdataplease.streetgym.app.resources.placement_scratch
import com.justdataplease.streetgym.app.resources.placement_skill_capped
import com.justdataplease.streetgym.app.resources.placement_skill_result
import com.justdataplease.streetgym.app.resources.placement_skill_test
import com.justdataplease.streetgym.app.resources.placement_skills_label
import com.justdataplease.streetgym.app.resources.placement_skills_prompt
import com.justdataplease.streetgym.app.resources.placement_skills_title
import com.justdataplease.streetgym.app.resources.placement_skip
import com.justdataplease.streetgym.app.resources.placement_squats
import com.justdataplease.streetgym.app.resources.placement_squats_short
import com.justdataplease.streetgym.app.resources.placement_start_line
import com.justdataplease.streetgym.app.resources.placement_title
import com.justdataplease.streetgym.app.resources.placement_your_start
import com.justdataplease.streetgym.app.resources.share_body
import com.justdataplease.streetgym.app.resources.share_switch
import com.justdataplease.streetgym.app.resources.tier_free
import com.justdataplease.streetgym.app.resources.tier_plus
import com.justdataplease.streetgym.app.resources.tier_pro
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.theme.blendColor
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Placement
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The phone's options for the counters' keyboard: on iPhone, digits with a return key, so Next and Done show as on
 * Android (its number pad has none); null elsewhere.
 */
val LocalCountKeys = staticCompositionLocalOf<PlatformImeOptions?> { null }

/** The three tests in the card's order, with the counter's label, its spoken name and the "N more" plural. */
private class TestWords(val short: StringResource, val title: StringResource, val more: PluralStringResource)

private fun Placement.Test.words(): TestWords = when (this) {
    Placement.Test.PULL_UPS -> TestWords(Res.string.placement_pull_ups_short, Res.string.placement_pull_ups, Res.plurals.placement_more_pull_ups)
    Placement.Test.PUSH_UPS -> TestWords(Res.string.placement_push_ups_short, Res.string.placement_push_ups, Res.plurals.placement_more_push_ups)
    Placement.Test.SQUATS -> TestWords(Res.string.placement_squats_short, Res.string.placement_squats, Res.plurals.placement_more_squats)
}

/** "Level 2" for a start on day 1, "Level 2, day 21" inside a level (Android: PlacementView.where). */
@Composable
private fun where(start: Placement.Start): String =
    if (start.day > 1) stringResource(Res.string.placement_level_day, start.level, start.day)
    else stringResource(Res.string.level_number, start.level)

@Composable
private fun tierName(tier: Tier): String = stringResource(when (tier) {
    Tier.FREE -> Res.string.tier_free
    Tier.PLUS -> Res.string.tier_plus
    Tier.PRO -> Res.string.tier_pro
})

/**
 * Today's first-launch screen, the strength test (android-streetgym, ui/PlacementView.kt placementCard, design B):
 * under a dark header asking how strong the athlete is today, three big counters take one set each of pull-ups,
 * push-ups and squats; Calculate puts the start they give on a strip of tape, with what it rests on and one confirm.
 * "Already hold skills?" folds out the level tests the counts pass. The answers stay while the app runs
 * ([AppModel.placementForm]); any start, skipping or "Start from scratch" goes through [AppModel.startPlacement], then
 * [onPlaced]. It closes with the progress-sharing note and its switch.
 */
@Composable
fun ColumnScope.PlacementCard(model: AppModel, onPlaced: () -> Unit) {
    val palette = Street.colors
    val form = model.placementForm
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var scrollTick by remember { mutableIntStateOf(0) }
    fun place(start: Placement.Start) {
        model.startPlacement(start)
        onPlaced()
    }
    fun calculate() {
        if (!form.calculate()) return
        // A picked level test the counts no longer pass is dropped, as on Android.
        model.placementResult()?.let { form.skill = it.skill }
        keyboard?.hide()
        focus.clearFocus()
        scrollTick++
    }

    StreetCard(fill = palette.primaryDark, outlined = false, padding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 20.dp)) {
        BodyText(stringResource(Res.string.placement_kicker), 12f, color = palette.heroKicker)
        TitleText(stringResource(Res.string.placement_title), 30f, Modifier.padding(top = 6.dp), color = palette.heroText)
        BodyText(stringResource(Res.string.placement_prompt), 14f, Modifier.padding(top = 8.dp),
            color = blendColor(palette.heroText, palette.primaryDark, 0.22f))
    }
    val requesters = remember { List(Placement.Test.entries.size) { FocusRequester() } }
    Row(Modifier.padding(top = 18.dp).fillMaxWidth()) {
        Placement.Test.entries.forEachIndexed { index, test ->
            val words = test.words()
            val last = index == Placement.Test.entries.lastIndex
            CountTile(
                label = stringResource(words.short),
                description = stringResource(words.title),
                value = form.counts[index],
                last = last,
                focus = requesters[index],
                onNext = { requesters.getOrNull(index + 1)?.requestFocus() },
                onDone = ::calculate,
                modifier = Modifier.weight(1f).padding(start = if (index > 0) 10.dp else 0.dp),
            ) { count ->
                // The first count typed starts the test for Google Analytics (Android: PlacementView's tutorial_begin).
                if (count >= 0 && form.counts.all { it < 0 }) model.googleAnalytics.tutorialBegin()
                form.set(index, count)
            }
        }
    }
    BodyText(stringResource(Res.string.placement_how), 12.5f, Modifier.padding(start = 2.dp, top = 10.dp))

    val result = if (form.shown) model.placementResult() else null
    if (result == null) {
        val ready = form.ready
        PrimaryButton(stringResource(Res.string.placement_calculate), Modifier.padding(top = 18.dp).alpha(if (ready) 1f else 0.45f),
            enabled = ready, onClick = ::calculate)
        if (!ready) BodyText(stringResource(Res.string.placement_answer_all), 13f, Modifier.fillMaxWidth().padding(top = 8.dp),
            align = TextAlign.Center)
        LinkButton(stringResource(Res.string.placement_skip), Modifier.padding(top = 6.dp)) { place(StrengthResult.SCRATCH) }
    } else {
        PlacementResult(model, result, scrollTick, onStart = ::place)
    }

    // Last, below the start: what the app keeps to improve the program, with its switch.
    ShareProgressNote(model, Modifier.padding(top = 22.dp))
}

/** The start on tape, what it rests on, the confirm and the skills step (Android: placementCard renderResult). */
@Composable
private fun PlacementResult(model: AppModel, result: StrengthResult, scrollTick: Int, onStart: (Placement.Start) -> Unit) {
    val palette = Street.colors
    val form = model.placementForm
    val start = result.start
    val wanted = result.wanted
    val here = where(start)
    @Composable fun levelName(number: Int): String =
        model.level(number)?.let { displayName(it) } ?: stringResource(Res.string.level_number, number)

    StartTape(stringResource(Res.string.placement_start_line, here, levelName(start.level)), Modifier.padding(top = 22.dp))

    // What the start rests on, in one card of labelled parts.
    val parts = buildList {
        if (result.skill > 0) {
            val suggests = stringResource(Res.string.placement_skill_result, result.skill, wanted.level, levelName(wanted.level))
            add(Res.string.placement_skills_label to if (start == wanted) suggests else suggests + " " +
                stringResource(Res.string.placement_skill_capped, wanted.level, tierName(LevelAccess.tierFor(wanted.level)), start.level))
        }
        result.opening.takeIf { it.isNotEmpty() }?.let { movements ->
            add(Res.string.placement_opens_label to movements.joinToString(" · ") { it.replaceFirstChar(Char::uppercase) })
        }
        result.assumed?.let { exam -> add(Res.string.placement_assumes_label to exam.tests.map { examTarget(it) }.joinToString("\n")) }
        result.better?.let { better ->
            val more = pluralStringResource(better.test.words().more, better.more, better.more)
            add(Res.string.placement_better_label to stringResource(Res.string.placement_better, more, where(better.start)))
        }
    }
    if (parts.isNotEmpty() || !result.fromScratch) {
        StreetCard(Modifier.padding(top = 14.dp), padding = PaddingValues(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 14.dp)) {
            parts.forEachIndexed { index, (label, value) ->
                BasicText(
                    stringResource(label).uppercase(),
                    Modifier.padding(top = if (index == 0) 0.dp else 12.dp),
                    style = TextStyle(color = palette.textSecondary, fontSize = 11.sp, fontFamily = Street.type.label, letterSpacing = 0.12.em),
                )
                BodyText(value, 14f, Modifier.padding(top = 2.dp), color = palette.text)
            }
            if (!result.fromScratch) BodyText(stringResource(Res.string.placement_no_credit), 12f,
                Modifier.padding(top = if (parts.isEmpty()) 0.dp else 12.dp))
        }
    }

    // Calculate brings the result in below the fold of a phone: show it, with a little room under the confirm.
    val bring = remember { BringIntoViewRequester() }
    var confirmSize by remember { mutableStateOf(IntSize.Zero) }
    val room = with(LocalDensity.current) { 64.dp.toPx() }
    LaunchedEffect(scrollTick) {
        if (scrollTick > 0) bring.bringIntoView(Rect(0f, 0f, confirmSize.width.toFloat(), confirmSize.height + room))
    }
    PrimaryButton(stringResource(Res.string.placement_confirm, here),
        Modifier.padding(top = 20.dp).onSizeChanged { confirmSize = it }.bringIntoViewRequester(bring)) { onStart(start) }
    if (!result.fromScratch) LinkButton(stringResource(Res.string.placement_scratch), Modifier.padding(top = 6.dp)) { onStart(StrengthResult.SCRATCH) }

    if (result.tests.isNotEmpty()) {
        // The skills step, folded under one line so the start stays by the counters; one tap picks a test, a second clears it.
        var open by remember { mutableStateOf(form.skill > 0) }
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TitleText(stringResource(Res.string.placement_skills_title), 16f, Modifier.weight(1f))
            BodyText(if (open) "−" else "+", 24f, Modifier.clearAndSetSemantics {}, color = palette.text)
        }
        if (open) {
            BodyText(stringResource(Res.string.placement_skills_prompt), 13f, Modifier.padding(top = 2.dp))
            result.tests.forEach { exam -> SkillTestRow(exam, chosen = form.skill == exam.levelNumber) {
                form.skill = if (form.skill == exam.levelNumber) 0 else exam.levelNumber
            } }
        }
    }
}

/** One level test of the skills step: its name and targets, framed in the theme's colour when picked. */
@Composable
private fun SkillTestRow(exam: TrainingProgram, chosen: Boolean, onClick: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val name = stringResource(Res.string.placement_skill_test, exam.levelNumber)
    Column(
        Modifier.padding(top = 8.dp).fillMaxWidth()
            .streetBackground(theme, if (chosen) palette.infoSurface else palette.surface, 14.dp,
                if (chosen) palette.primary else palette.border, if (chosen) 2.dp else 1.dp)
            .selectable(chosen, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = name }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        TitleText(name, 14f)
        BodyText(exam.tests.map { examTarget(it) }.joinToString(", "), 12f, Modifier.padding(top = 2.dp))
    }
}

/**
 * The start on a strip of the theme's tape, a little askew in the poster themes: "Your start" over [line] ("Level 2 ·
 * Bar Strength") (Android: PlacementView.startTape).
 */
@Composable
private fun StartTape(line: String, modifier: Modifier = Modifier) {
    val theme = Street.theme
    val palette = theme.palette
    val ink = palette.contentOn(palette.accent)
    Column(
        modifier.fillMaxWidth()
            .graphicsLayer { if (theme != StreetTheme.NIGHT_PARK) rotationZ = -1.2f }
            .streetBackground(theme, palette.accent, 14.dp, palette.border, theme.shape.borderDp.dp)
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 12.dp),
    ) {
        BasicText(stringResource(Res.string.placement_your_start).uppercase(),
            style = TextStyle(color = ink, fontSize = 11.sp, fontFamily = Street.type.label, letterSpacing = 0.14.em))
        TitleText(line, 22f, Modifier.padding(top = 2.dp), color = ink)
    }
}

/**
 * One test's counter (Android: PlacementView.countTile): its [label] over a big number the athlete can type (up to
 * three digits) or step with − and + under a rule. [description] names it to VoiceOver ("Strict pull-ups"), and the
 * steps say which they move ("One rep more, Strict pull-ups"). [value] -1 is empty. The keyboard's Next moves on to the
 * next counter ([onNext]); on the [last] one its Done runs [onDone].
 */
@Composable
private fun CountTile(
    label: String,
    description: String,
    value: Int,
    last: Boolean,
    focus: FocusRequester,
    onNext: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val theme = Street.theme
    val palette = theme.palette
    val fonts = theme.fonts
    val border = theme.shape.borderDp.coerceAtLeast(1).dp
    // The count kept on the form while Today redraws; the field only ever changes it.
    var field by remember { mutableStateOf(TextFieldValue(if (value >= 0) value.toString() else "")) }
    fun step(delta: Int) {
        val next = ((field.text.toIntOrNull() ?: 0) + delta).coerceIn(0, 999)
        field = TextFieldValue(next.toString(), TextRange(next.toString().length))
        onChange(next)
    }
    val style = TextStyle(color = palette.text, fontSize = 40.sp, fontFamily = Street.type.numeral, textAlign = TextAlign.Center)
    Column(
        modifier.streetBackground(theme, palette.surface, 18.dp, palette.border, border).padding(start = 4.dp, top = 12.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(label.uppercase(), Modifier.fillMaxWidth().clearAndSetSemantics {}, style = TextStyle(color = palette.text,
            fontSize = 11.sp, fontFamily = Street.type.label, letterSpacing = 0.12.em, textAlign = TextAlign.Center), maxLines = 1)
        BasicTextField(
            value = field,
            onValueChange = { next ->
                if (next.text.length <= 3 && next.text.all(Char::isDigit)) {
                    field = next
                    onChange(next.text.toIntOrNull() ?: -1)
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = description }
                .fontPadding(40.sp, fonts.displayTop, fonts.displayBottom).padding(top = 2.dp, bottom = 6.dp),
            textStyle = style,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
                platformImeOptions = LocalCountKeys.current,
            ),
            keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
            cursorBrush = SolidColor(palette.primary),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (field.text.isEmpty()) BasicText("–", Modifier.fillMaxWidth(), style = style.copy(color = palette.divider))
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(border).background(palette.border))
        Row(Modifier.fillMaxWidth()) {
            StepButton("−", "${stringResource(Res.string.placement_less)}, $description", Modifier.weight(1f)) { step(-1) }
            StepButton("+", "${stringResource(Res.string.placement_more)}, $description", Modifier.weight(1f)) { step(1) }
        }
    }
}

@Composable
private fun StepButton(sign: String, spoken: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(sign, style = TextStyle(color = Street.colors.text, fontSize = 22.sp, fontFamily = Street.type.bold, textAlign = TextAlign.Center))
    }
}

/**
 * The progress-sharing note (Android: ui/ShareProgressView.kt shareProgressNote): one line on what the app keeps to
 * improve the program, its first words in bold, beside a switch that is on until switched off; it closes the strength
 * test under a rule. The uploads come with the account (round 13); the choice is kept now.
 */
@Composable
fun ShareProgressNote(model: AppModel, modifier: Modifier = Modifier) {
    val palette = Street.colors
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(palette.divider))
        ShareProgressRow(model, Modifier.padding(top = 12.dp))
    }
}

/**
 * The sharing line and its switch (Android: shareProgressNote's row): switched off, what this install shared is
 * deleted and Google Analytics stops ([AppModel.shareProgress]); [onChanged] follows.
 */
@Composable
fun ShareProgressRow(model: AppModel, modifier: Modifier = Modifier, onChanged: (Boolean) -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Android's <b> over the body face: a synthesized bold of Albert Sans Regular, as on Android.
        BodyText(boldMarkup(stringResource(Res.string.share_body), SpanStyle(fontWeight = FontWeight.Bold)), 12.5f, Modifier.weight(1f))
        SwitchToggle(model.shareProgress, stringResource(Res.string.share_switch), Modifier.padding(start = 14.dp)) {
            model.shareProgress = it
            onChanged(it)
        }
    }
}
