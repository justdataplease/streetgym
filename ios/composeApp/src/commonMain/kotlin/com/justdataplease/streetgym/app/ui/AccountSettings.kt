package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AccountStatus
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.CloudAccount
import com.justdataplease.streetgym.app.data.Countries
import com.justdataplease.streetgym.app.data.Profile
import com.justdataplease.streetgym.app.data.ProfileProblem
import com.justdataplease.streetgym.app.data.SignInProvider
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.account_choose_profile
import com.justdataplease.streetgym.app.resources.account_delete
import com.justdataplease.streetgym.app.resources.account_delete_message
import com.justdataplease.streetgym.app.resources.account_delete_message_apple
import com.justdataplease.streetgym.app.resources.account_delete_title
import com.justdataplease.streetgym.app.resources.account_edit_profile
import com.justdataplease.streetgym.app.resources.account_intro
import com.justdataplease.streetgym.app.resources.account_intro_ios
import com.justdataplease.streetgym.app.resources.account_intro_plus
import com.justdataplease.streetgym.app.resources.account_intro_plus_ios
import com.justdataplease.streetgym.app.resources.account_not_synced
import com.justdataplease.streetgym.app.resources.account_plus_note
import com.justdataplease.streetgym.app.resources.account_sign_in
import com.justdataplease.streetgym.app.resources.account_sign_in_apple
import com.justdataplease.streetgym.app.resources.account_sign_in_failed
import com.justdataplease.streetgym.app.resources.account_sign_out
import com.justdataplease.streetgym.app.resources.account_sign_out_note
import com.justdataplease.streetgym.app.resources.account_sync_failed
import com.justdataplease.streetgym.app.resources.account_sync_now
import com.justdataplease.streetgym.app.resources.account_synced
import com.justdataplease.streetgym.app.resources.account_visible
import com.justdataplease.streetgym.app.resources.account_working
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.resources.delete
import com.justdataplease.streetgym.app.resources.profile_apple
import com.justdataplease.streetgym.app.resources.profile_apple_signed
import com.justdataplease.streetgym.app.resources.profile_board_note
import com.justdataplease.streetgym.app.resources.profile_country
import com.justdataplease.streetgym.app.resources.profile_delete
import com.justdataplease.streetgym.app.resources.profile_delete_message
import com.justdataplease.streetgym.app.resources.profile_delete_title
import com.justdataplease.streetgym.app.resources.profile_failed
import com.justdataplease.streetgym.app.resources.profile_google
import com.justdataplease.streetgym.app.resources.profile_intro
import com.justdataplease.streetgym.app.resources.profile_invalid
import com.justdataplease.streetgym.app.resources.profile_keep_note
import com.justdataplease.streetgym.app.resources.profile_offline
import com.justdataplease.streetgym.app.resources.profile_save
import com.justdataplease.streetgym.app.resources.profile_taken
import com.justdataplease.streetgym.app.resources.profile_title
import com.justdataplease.streetgym.app.resources.profile_username
import com.justdataplease.streetgym.app.theme.Street
import org.jetbrains.compose.resources.stringResource

/*
 * Settings › Profile (android-streetgym, ui/AccountSettingsView.kt): a username for anyone, no sign-in needed; Connect
 * Google, and on iPhone Sign in with Apple beside it (Apple 4.8), to keep it with Pro's cloud copy; once connected the
 * account, Sync now on Pro, Sign out and Delete account. Shown only in a build with Firebase, as on Android.
 */

/**
 * The Sign in with Apple button (Apple's own on iPhone: iosMain's ASAuthorizationAppleIDButton, drawn by UIKit); this
 * stand-in draws Apple's black button with its words where UIKit can't (the desktop). [dark]: the white one, for Night Park.
 */
fun interface AppleButton {
    @Composable
    fun Draw(dark: Boolean, modifier: Modifier, onClick: () -> Unit)
}

/** The desktop's and the previews' Sign in with Apple: Apple's button drawn in Compose, in its black or white style. */
object AppleButtonStandIn : AppleButton {
    @Composable
    override fun Draw(dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val label = stringResource(Res.string.account_sign_in_apple)
        Box(
            modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(8.dp)).background(if (dark) Color.White else Color.Black)
                .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(label, style = TextStyle(color = if (dark) Color.Black else Color.White, fontSize = 19.sp,
                fontFamily = Street.type.bold, textAlign = TextAlign.Center))
        }
    }
}

val LocalAppleButton = staticCompositionLocalOf<AppleButton> { AppleButtonStandIn }

/**
 * The profile's card (Android: AccountSettingsView.render). [onEdit] opens the profile for a user id; [onDeleteProfile]
 * and [onDeleteAccount] ask first.
 */
@Composable
fun ProfileCard(model: AppModel, onEdit: (String) -> Unit, onDeleteProfile: () -> Unit, onDeleteAccount: () -> Unit, modifier: Modifier = Modifier) {
    val cloud = model.cloud
    val palette = Street.colors
    val tier = model.tier
    val user = cloud.user
    val profile = cloud.profile()
    val account = cloud.signedIn()
    val providers = cloud.account.providers
    StreetCard(modifier) {
        if (user == null || profile == null) {
            BodyText(stringResource(Res.string.profile_intro), 14f)
            PrimaryButton(stringResource(Res.string.account_choose_profile), Modifier.padding(top = 12.dp)) { cloud.pickUsername(onEdit) }
        } else {
            TitleText("${Countries.flag(profile.country)} ${profile.username}", 18f)
            BodyText(Countries.name(profile.country), 13f, Modifier.padding(top = 2.dp))
            SettingSwitch(stringResource(Res.string.account_visible), profile.visible, Modifier.padding(top = 6.dp)) { cloud.setVisible(it) }
            SecondaryButton(stringResource(Res.string.account_edit_profile), Modifier.padding(top = 4.dp)) { onEdit(user.uid) }
        }
        if (!tier.account) BodyText(stringResource(Res.string.profile_board_note), 12f, Modifier.padding(top = 8.dp))

        if (account == null) {
            if (providers.isNotEmpty()) {
                // Android names Google alone; with Apple beside it the iPhone names both.
                val intro = when {
                    SignInProvider.APPLE !in providers -> if (tier.cloud) Res.string.account_intro else Res.string.account_intro_plus
                    else -> if (tier.cloud) Res.string.account_intro_ios else Res.string.account_intro_plus_ios
                }
                BodyText(stringResource(intro), 14f, Modifier.padding(top = 18.dp))
                if (SignInProvider.APPLE in providers) {
                    LocalAppleButton.current.Draw(Street.theme.dark, Modifier.padding(top = 10.dp)) { cloud.connect(SignInProvider.APPLE) }
                }
                if (SignInProvider.GOOGLE in providers) {
                    SecondaryButton(stringResource(Res.string.account_sign_in), Modifier.padding(top = 10.dp)) { cloud.connect(SignInProvider.GOOGLE) }
                }
            } else if (profile != null) BodyText(stringResource(Res.string.profile_keep_note), 12f, Modifier.padding(top = 12.dp))
            StatusLine(cloud.status, Modifier.padding(top = 8.dp))
            if (user != null && profile != null) SecondaryButton(stringResource(Res.string.profile_delete), Modifier.padding(top = 8.dp), onClick = onDeleteProfile)
            return@StreetCard
        }

        val who = when {
            account.provider == SignInProvider.APPLE && account.email.isNullOrBlank() -> stringResource(Res.string.profile_apple_signed)
            account.provider == SignInProvider.APPLE -> stringResource(Res.string.profile_apple, account.email.orEmpty())
            else -> stringResource(Res.string.profile_google, account.email.orEmpty())
        }
        BodyText(who, 14f, Modifier.padding(top = 18.dp))
        // Free and Plus keep their progress on the phone; Pro's syncs.
        if (tier.cloud) {
            cloud.revision
            val last = cloud.store.lastSyncAt
            val status = cloud.status
            if (status != null) StatusLine(status, Modifier.padding(top = 8.dp))
            else BodyText(if (last > 0) stringResource(Res.string.account_synced, historyDate(last, model.zone))
                else stringResource(Res.string.account_not_synced), 13f, Modifier.padding(top = 8.dp))
            SecondaryButton(stringResource(Res.string.account_sync_now), Modifier.padding(top = 8.dp)) { cloud.syncNow() }
        } else BodyText(stringResource(Res.string.account_plus_note), 13f, Modifier.padding(top = 8.dp))
        SecondaryButton(stringResource(Res.string.account_sign_out), Modifier.padding(top = 8.dp)) { cloud.signOut() }
        BodyText(stringResource(Res.string.account_sign_out_note), 12f, Modifier.padding(top = 4.dp))
        SecondaryButton(stringResource(Res.string.account_delete), Modifier.padding(top = 12.dp), onClick = onDeleteAccount)
    }
}

/** The status line: "Connecting…", "Couldn't reach the server…", "Couldn't connect…", "Couldn't sync…". */
@Composable
private fun StatusLine(status: AccountStatus?, modifier: Modifier) {
    val text = when (status) {
        null -> return
        AccountStatus.WORKING -> Res.string.account_working
        AccountStatus.OFFLINE -> Res.string.profile_offline
        AccountStatus.SIGN_IN_FAILED -> Res.string.account_sign_in_failed
        AccountStatus.SYNC_FAILED -> Res.string.account_sync_failed
    }
    BodyText(stringResource(text), 13f, modifier)
}

/**
 * "Your leaderboard profile" (Android: editProfile): the username (3–20 letters, digits, _ or .) and the country, the
 * phone's region first picked; Save checks the name, then claims it ("That username is taken.", "Couldn't save…").
 * The countries open in a list of their own, as Android's spinner drops its list down.
 */
@Composable
fun ProfileDialog(cloud: CloudAccount, uid: String, region: String?, onClose: () -> Unit) {
    val previous = remember(uid) { cloud.store.profile(uid) }
    var name by remember { mutableStateOf(TextFieldValue(previous?.username.orEmpty())) }
    val countries = remember { Countries.all() }
    var country by remember { mutableStateOf(previous?.country ?: region?.takeIf(Countries::valid) ?: countries.first().first) }
    var error by remember { mutableStateOf<ProfileProblem?>(null) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val palette = Street.colors
    StreetDialog(
        title = stringResource(Res.string.profile_title),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.profile_save)) {
            if (saving) return@DialogAction
            val profile = Profile(name.text.trim(), country, previous?.visible ?: true)
            saving = true
            cloud.saveProfile(uid, profile) { problem ->
                saving = false
                if (problem == null) onClose() else error = problem
            }
        },
        dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 20.dp)) {
            EntryField(
                name, stringResource(Res.string.profile_username), maxLength = 40, focusFirst = previous == null,
                error = when (error) {
                    ProfileProblem.INVALID -> stringResource(Res.string.profile_invalid)
                    ProfileProblem.TAKEN -> stringResource(Res.string.profile_taken)
                    ProfileProblem.FAILED -> stringResource(Res.string.profile_failed)
                    null -> null
                },
            ) {
                name = it
                error = null
            }
            BodyText(stringResource(Res.string.profile_country), 13f, Modifier.padding(top = 12.dp))
            val label = "${Countries.flag(country)} ${Countries.name(country)}"
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { picking = true }
                    .semantics { contentDescription = label }.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(label, Modifier.weight(1f), style = TextStyle(color = palette.text, fontSize = 18.sp, fontFamily = Street.type.body), maxLines = 1)
                BodyText("▾", 18f)
            }
        }
    }
    if (picking) CountryDialog(countries, country, onPick = { country = it; picking = false }) { picking = false }
}

/** Every country with its flag, the picked one ticked, scrolled to it. */
@Composable
private fun CountryDialog(countries: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val start = countries.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    StreetDialog(
        title = stringResource(Res.string.profile_country),
        onDismiss = onClose,
        confirm = DialogAction(stringResource(Res.string.cancel), onClose),
    ) {
        LazyColumn(Modifier.fillMaxWidth().height(420.dp), state = list) {
            itemsIndexed(countries) { _, (code, name) ->
                ChoiceRow("${Countries.flag(code)} $name", code == selected) { onPick(code) }
            }
        }
    }
}

/** "Delete your profile?" (Android: confirmDeleteProfile). */
@Composable
fun DeleteProfileDialog(onClose: () -> Unit, onDelete: () -> Unit) = StreetDialog(
    title = stringResource(Res.string.profile_delete_title),
    message = stringResource(Res.string.profile_delete_message),
    onDismiss = onClose,
    confirm = DialogAction(stringResource(Res.string.delete), onDelete),
    dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
)

/** "Delete your account?" (Android: confirmDelete), naming Apple for an account signed in with Apple. */
@Composable
fun DeleteAccountDialog(apple: Boolean, onClose: () -> Unit, onDelete: () -> Unit) = StreetDialog(
    title = stringResource(Res.string.account_delete_title),
    message = stringResource(if (apple) Res.string.account_delete_message_apple else Res.string.account_delete_message),
    onDismiss = onClose,
    confirm = DialogAction(stringResource(Res.string.delete), onDelete),
    dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
)

/**
 * Settings › Your data: the sharing note and its switch in a card (Android: shareProgressNote inSettings); switched on,
 * the install's record goes up at once (Android: syncAnalytics(force = true)).
 */
@Composable
fun ShareProgressCard(model: AppModel, modifier: Modifier = Modifier) {
    StreetCard(modifier) {
        ShareProgressRow(model) { on -> if (on) model.syncAnalytics(force = true) }
    }
}
