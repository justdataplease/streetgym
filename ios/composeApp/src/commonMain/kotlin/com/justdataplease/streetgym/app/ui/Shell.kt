package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.back
import com.justdataplease.streetgym.app.resources.dashboard_title
import com.justdataplease.streetgym.app.resources.ic_brand_mark
import com.justdataplease.streetgym.app.resources.ic_nav_progress
import com.justdataplease.streetgym.app.resources.ic_nav_settings
import com.justdataplease.streetgym.app.resources.ic_nav_timer
import com.justdataplease.streetgym.app.resources.ic_nav_today
import com.justdataplease.streetgym.app.resources.ic_nav_training
import com.justdataplease.streetgym.app.resources.levels_title
import com.justdataplease.streetgym.app.resources.nav_levels
import com.justdataplease.streetgym.app.resources.nav_progress
import com.justdataplease.streetgym.app.resources.nav_settings
import com.justdataplease.streetgym.app.resources.nav_timer
import com.justdataplease.streetgym.app.resources.nav_today
import com.justdataplease.streetgym.app.resources.progress_title
import com.justdataplease.streetgym.app.resources.settings_title
import com.justdataplease.streetgym.app.resources.workout_overview
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Where the app can be. The five tabs are roots; a class opens over Today or Training. */
sealed interface Screen {
    data object Today : Screen
    data object Training : Screen
    data object Progress : Screen
    data object Timer : Screen
    data object Settings : Screen
    data class Class(val programId: String) : Screen

    val nested: Boolean get() = this is Class
}

private class Tab(val screen: Screen, val label: StringResource, val icon: DrawableResource)

private val TABS = listOf(
    Tab(Screen.Today, Res.string.nav_today, Res.drawable.ic_nav_today),
    Tab(Screen.Training, Res.string.nav_levels, Res.drawable.ic_nav_training),
    Tab(Screen.Progress, Res.string.nav_progress, Res.drawable.ic_nav_progress),
    Tab(Screen.Timer, Res.string.nav_timer, Res.drawable.ic_nav_timer),
    Tab(Screen.Settings, Res.string.nav_settings, Res.drawable.ic_nav_settings),
)

/**
 * The frame every screen sits in (android-streetgym, HomeActivity.buildShell): the page's grain behind everything, the
 * title bar, the screen, and the tab bar. [safeArea] keeps it clear of the Dynamic Island and the home indicator.
 */
@Composable
fun Shell(model: AppModel, safeArea: PaddingValues, startAt: Screen = Screen.Today) {
    val theme = Street.theme
    // The screens under the one showing; the first is always a tab.
    val stack = remember { mutableStateListOf(if (startAt.nested) Screen.Today else startAt).apply { if (startAt.nested) add(startAt) } }
    val screen = stack.last()
    fun openTab(tab: Screen) { stack.clear(); stack.add(tab) }
    fun open(next: Screen) { stack.add(next) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    Column(Modifier.fillMaxSize().pageBackground(theme).padding(safeArea)) {
        TitleBar(screen, onBack = ::back)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                Screen.Today -> TodayScreen(model, onOpenClass = { open(Screen.Class(it)) }, onOpenProgress = { openTab(Screen.Progress) })
                is Screen.Class -> ClassScreen(model, screen.programId, onDone = ::back)
                Screen.Training -> ToCome(stringResource(Res.string.levels_title))
                Screen.Progress -> ToCome(stringResource(Res.string.progress_title))
                Screen.Timer -> ToCome(stringResource(Res.string.nav_timer))
                Screen.Settings -> ToCome(stringResource(Res.string.settings_title))
            }
        }
        TabBar(selected = stack.first(), onSelect = ::openTab)
    }
}

@Composable
private fun screenTitle(screen: Screen): String = stringResource(when (screen) {
    Screen.Today -> Res.string.dashboard_title
    Screen.Training -> Res.string.levels_title
    is Screen.Class -> Res.string.workout_overview
    Screen.Progress -> Res.string.progress_title
    Screen.Settings -> Res.string.settings_title
    Screen.Timer -> Res.string.nav_timer
})

@Composable
private fun TitleBar(screen: Screen, onBack: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val title = screenTitle(screen)
    Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (screen.nested) {
            val back = stringResource(Res.string.back)
            Box(Modifier.size(56.dp).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = back },
                contentAlignment = Alignment.Center) {
                BasicText("‹", style = TextStyle(color = palette.text, fontSize = 38.sp, fontFamily = Street.type.body))
            }
        } else {
            Image(painterResource(Res.drawable.ic_brand_mark), contentDescription = null, colorFilter = ColorFilter.tint(palette.mark),
                modifier = Modifier.size(56.dp).padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 12.dp))
        }
        val fontSize = if (theme == StreetTheme.WHEATPASTE) 20.sp else 25.sp
        BasicText(
            text = if (theme.shape.upperTitles) title.uppercase() else title,
            modifier = Modifier.weight(1f).displayPadding(fontSize),
            style = TextStyle(color = palette.text, fontSize = fontSize, fontFamily = Street.type.display),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The colour of a tab's icon and label. */
private fun tabColor(theme: StreetTheme, selected: Boolean): Color = when (theme) {
    StreetTheme.CONCRETE -> if (selected) theme.palette.border else Color(0xFFBDBAB3)
    StreetTheme.WHEATPASTE -> if (selected) theme.palette.text else theme.palette.textSecondary
    StreetTheme.NIGHT_PARK -> if (selected) theme.palette.primary else theme.palette.textSecondary
}

@Composable
private fun TabBar(selected: Screen, onSelect: (Screen) -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val bar = when (theme) {
        StreetTheme.CONCRETE -> Modifier.streetBackground(theme, palette.border, 0.dp)
        // Paper with an ink rule along the top.
        StreetTheme.WHEATPASTE -> Modifier.drawBehind {
            drawRect(palette.background)
            drawRect(palette.border, size = Size(size.width, 3.dp.toPx()))
        }
        StreetTheme.NIGHT_PARK -> Modifier.streetBackground(theme, palette.surface, 24.dp)
    }
    Row(
        Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().height(74.dp).then(bar).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEach { tab ->
            val on = tab.screen == selected
            val color = tabColor(theme, on)
            val label = stringResource(tab.label)
            val item = when {
                !on -> Modifier
                theme == StreetTheme.CONCRETE -> Modifier.streetBackground(theme, palette.accent, 0.dp)
                // A rule in the theme's orange along the top of the selected tab.
                theme == StreetTheme.NIGHT_PARK -> Modifier.drawBehind { drawRect(palette.primary, size = Size(size.width, 3.dp.toPx())) }
                else -> Modifier
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 3.dp).height(58.dp).then(item)
                    .clickable(role = Role.Tab) { onSelect(tab.screen) }
                    .semantics(mergeDescendants = true) { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(painterResource(tab.icon), contentDescription = null, colorFilter = ColorFilter.tint(color), modifier = Modifier.size(22.dp))
                // Wheatpaste marks the selected label with a highlighter swipe.
                val swipe = on && theme == StreetTheme.WHEATPASTE
                BasicText(
                    text = label.uppercase(),
                    modifier = Modifier.padding(top = 4.dp)
                        .then(if (swipe) Modifier.streetBackground(theme, palette.spot, 0.dp).padding(horizontal = 4.dp) else Modifier),
                    style = TextStyle(color = color, fontSize = 10.sp, letterSpacing = 0.06.em, textAlign = TextAlign.Center,
                        fontFamily = if (theme == StreetTheme.WHEATPASTE) Street.type.label else Street.type.bold),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A screen's content: one scrolling column with the page's margins. */
@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
        content = content,
    )
}

/** A tab whose screen is not built on iPhone yet. */
@Composable
private fun ToCome(title: String) {
    Page {
        StreetCard {
            TitleText(title, 22f)
            BodyText("Not on iPhone yet.", modifier = Modifier.padding(top = 8.dp))
        }
    }
}
