package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.Board
import com.justdataplease.streetgym.app.data.BoardEntry
import com.justdataplease.streetgym.app.data.Boards
import com.justdataplease.streetgym.app.data.Countries
import com.justdataplease.streetgym.app.data.NameReport
import com.justdataplease.streetgym.app.data.Standing
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.account_pick_name
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.leaderboard_all
import com.justdataplease.streetgym.app.resources.leaderboard_block
import com.justdataplease.streetgym.app.resources.leaderboard_blocked_note
import com.justdataplease.streetgym.app.resources.leaderboard_empty
import com.justdataplease.streetgym.app.resources.leaderboard_hidden
import com.justdataplease.streetgym.app.resources.leaderboard_how
import com.justdataplease.streetgym.app.resources.leaderboard_intro
import com.justdataplease.streetgym.app.resources.leaderboard_loading
import com.justdataplease.streetgym.app.resources.leaderboard_month
import com.justdataplease.streetgym.app.resources.leaderboard_offline
import com.justdataplease.streetgym.app.resources.leaderboard_rank
import com.justdataplease.streetgym.app.resources.leaderboard_report
import com.justdataplease.streetgym.app.resources.leaderboard_report_body
import com.justdataplease.streetgym.app.resources.leaderboard_report_message
import com.justdataplease.streetgym.app.resources.contact_address
import com.justdataplease.streetgym.app.resources.leaderboard_report_no_mail
import com.justdataplease.streetgym.app.resources.leaderboard_report_subject
import com.justdataplease.streetgym.app.resources.leaderboard_sign_in
import com.justdataplease.streetgym.app.resources.leaderboard_unavailable
import com.justdataplease.streetgym.app.resources.leaderboard_unblock
import com.justdataplease.streetgym.app.resources.leaderboard_updated
import com.justdataplease.streetgym.app.resources.leaderboard_world
import com.justdataplease.streetgym.app.resources.leaderboard_you_none
import com.justdataplease.streetgym.app.resources.leaderboard_you_rank
import com.justdataplease.streetgym.app.resources.leaderboard_you_top
import com.justdataplease.streetgym.app.theme.Street
import org.jetbrains.compose.resources.stringResource

/** The leaderboard's two switches, kept while the app runs (Android: boardMonth, boardInCountry); all time first. */
@Stable
class LeaderboardState {
    var month by mutableStateOf(false)
    var inCountry by mutableStateOf(false)
}

/**
 * Progress › Leaderboard on Plus and Pro (android-streetgym, HomeActivity.renderLeaderboard): the intro, All time / This
 * month, World / the athlete's country (the profile's, else the phone's region), the athlete's own line, the top 100
 * with the athlete's row highlighted, "1,234 players · updated …", and the line on how points are earned. A board is one
 * cached document, read at most every six hours. On iPhone a tap on another athlete's row offers to report or block the
 * name ([onReport], Apple 1.2); blocked names are left out on this phone. A build without Firebase says so.
 */
@Composable
fun ColumnScope.LeaderboardSection(model: AppModel, state: LeaderboardState, onReport: (BoardEntry, String) -> Unit) {
    val cloud = model.cloud
    BodyText(stringResource(Res.string.leaderboard_intro), 14f, Modifier.padding(top = 12.dp))
    if (!cloud.available) {
        StreetCard(Modifier.padding(top = 12.dp)) { BodyText(stringResource(Res.string.leaderboard_unavailable)) }
        return
    }
    val uid = cloud.user?.uid?.takeIf { model.tier.account }
    val profile = uid?.let { cloud.profile() }
    val country = profile?.country ?: model.services.region?.takeIf(Countries::valid)
    SegmentedSwitch(listOf(stringResource(Res.string.leaderboard_all), stringResource(Res.string.leaderboard_month)),
        if (state.month) 1 else 0, Modifier.padding(top = 12.dp)) { state.month = it == 1 }
    if (country != null) SegmentedSwitch(listOf(stringResource(Res.string.leaderboard_world), "${Countries.flag(country)} ${Countries.name(country)}"),
        if (state.inCountry) 1 else 0, Modifier.padding(top = 10.dp)) { state.inCountry = it == 1 }
    val id = Boards.id(state.month, country?.takeIf { state.inCountry })
    var board by remember(id) { mutableStateOf(cloud.boards.cached(id)?.first) }
    var loading by remember(id) { mutableStateOf(true) }
    LaunchedEffect(id) {
        cloud.boards.board(id) { read ->
            board = read
            loading = false
        }
    }
    val score = if (uid != null && profile != null) model.pointsTotal()?.let { if (state.month) it.month else it.all } ?: 0 else 0
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        BoardHolder(model, board, loading, uid, profile?.username, profile?.visible, score) { entry -> onReport(entry, id) }
    }
    BodyText(stringResource(Res.string.leaderboard_how), 12f, Modifier.padding(top = 14.dp))
}

@Composable
private fun BoardHolder(
    model: AppModel,
    board: Board?,
    loading: Boolean,
    uid: String?,
    username: String?,
    visible: Boolean?,
    score: Int,
    onReport: (BoardEntry) -> Unit,
) {
    val theme = Street.theme
    val palette = theme.palette
    val cloud = model.cloud
    if (board == null) {
        BodyText(stringResource(if (loading) Res.string.leaderboard_loading else Res.string.leaderboard_offline), 14f)
        return
    }
    StreetCard(Modifier.padding(bottom = 10.dp)) {
        when {
            uid == null -> BodyText(stringResource(Res.string.leaderboard_sign_in), 14f)
            username == null -> BodyText(stringResource(Res.string.account_pick_name), 14f)
            visible == false -> BodyText(stringResource(Res.string.leaderboard_hidden), 14f)
            else -> TitleText(when (val standing = Boards.standing(board, username, score)) {
                is Standing.Rank -> stringResource(Res.string.leaderboard_you_rank, standing.rank, pointsText(score))
                is Standing.TopPercent -> stringResource(Res.string.leaderboard_you_top, standing.percent, pointsText(score))
                null -> stringResource(Res.string.leaderboard_you_none)
            }, 16f)
        }
    }
    if (board.entries.isEmpty()) {
        BodyText(stringResource(Res.string.leaderboard_empty), 14f)
        return
    }
    cloud.revision
    val blocked = cloud.blocked.all()
    board.entries.forEachIndexed { index, entry ->
        val mine = username != null && entry.username == username
        // A name blocked on this phone is left out; the others keep their places.
        if (!mine && entry.username.lowercase() in blocked) return@forEachIndexed
        val rank = stringResource(Res.string.leaderboard_rank, index + 1)
        val name = "${Countries.flag(entry.country)} ${entry.username}"
        val points = pointsText(entry.score)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp).streetBackground(theme, if (mine) palette.infoSurface else palette.surface, 14.dp)
                .then(if (mine) Modifier else Modifier.clickable(role = Role.Button) { onReport(entry) })
                .semantics(mergeDescendants = true) { contentDescription = "$rank, ${entry.username}, $points" }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BodyText(rank, 14f, Modifier.width(48.dp))
            BasicText(name, Modifier.weight(1f), style = TextStyle(color = palette.text, fontSize = 15.sp,
                fontFamily = if (mine) Street.type.bold else Street.type.body), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TitleText(points, 15f)
        }
    }
    if (blocked.isNotEmpty()) {
        BodyText(stringResource(Res.string.leaderboard_blocked_note), 12f, Modifier.padding(top = 8.dp))
        TextAction(stringResource(Res.string.leaderboard_unblock), Modifier.padding(top = 2.dp)) { cloud.unblockAll() }
    }
    if (board.updatedAt > 0) BodyText(stringResource(Res.string.leaderboard_updated, pointsText(board.players), historyDate(board.updatedAt, model.zone)),
        12f, Modifier.padding(top = 8.dp))
}

/**
 * Another athlete's name: Report (a message to the app's contact address, from the athlete's mail app) or Block (left
 * out on this phone), or Cancel. [onReport] gets the mail link, and the note for a phone with no mail app.
 */
@Composable
fun ReportNameDialog(entry: BoardEntry, board: String, onBlock: () -> Unit, onReport: (link: String, noMail: String) -> Unit, onClose: () -> Unit) {
    val subject = stringResource(Res.string.leaderboard_report_subject)
    val body = stringResource(Res.string.leaderboard_report_body, entry.username, board)
    val address = stringResource(Res.string.contact_address)
    val noMail = stringResource(Res.string.leaderboard_report_no_mail, address)
    StreetDialog(
        title = "${Countries.flag(entry.country)} ${entry.username}",
        message = stringResource(Res.string.leaderboard_report_message),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.leaderboard_block), onBlock),
        dismiss = DialogAction(stringResource(Res.string.leaderboard_report)) { onReport(NameReport.mailto(address, subject, body), noMail) },
        neutral = DialogAction(stringResource(Res.string.cancel), onClose),
    )
}
