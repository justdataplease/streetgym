package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AdLook
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.remove_ads
import com.justdataplease.streetgym.app.theme.Street
import org.jetbrains.compose.resources.stringResource

/*
 * Ads on Free (android-streetgym, README "Ads and privacy"; HomeActivity.endAd, ManagedNativePlacement and the title
 * bar's Remove ads): a native card closing every page and inside a few, never on Plus or Pro.
 */

/** The theme's colours for a native card (Android: ManagedNativePlacement's AdPalette); its button's words read on the action colour. */
@Composable
fun adLook(): AdLook {
    val palette = Street.colors
    return AdLook(palette.surface, palette.text, palette.textSecondary, palette.action, palette.contentOn(palette.action), palette.divider)
}

/**
 * One native ad card on Free (Android: ManagedNativePlacement): [top] and [bottom] room around it. On a workout's page
 * ([training]) it waits for consent; any other page asks for it first (Google's form where needed, then Apple's
 * tracking question). Gone at once when Plus or Pro is bought, or consent is withdrawn.
 */
@Composable
fun AdCard(model: AppModel, modifier: Modifier = Modifier, top: Dp = 0.dp, bottom: Dp = 0.dp, training: Boolean = false) {
    if (model.plans.adFree) return
    val consent = model.adConsent
    if (!training) LaunchedEffect(consent) { consent.gather() }
    if (!consent.canRequest || !consent.started) return
    model.ads.NativeCard(adLook(), modifier.fillMaxWidth().padding(top = top, bottom = bottom))
}

/** The card closing a page on Free (Android: endAd, 16 dp above and 8 below). */
@Composable
fun EndAd(model: AppModel, modifier: Modifier = Modifier, training: Boolean = false) =
    AdCard(model, modifier, top = 16.dp, bottom = 8.dp, training = training)

/**
 * "♥ REMOVE ADS" beside the title on Free, on every screen but a workout (Android: toolbarRemoveAds): the heart in its
 * red, the words in the label face, a pill in the theme's style; it opens the Remove ads offer.
 */
@Composable
fun RemoveAdsButton(onClick: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val words = stringResource(Res.string.remove_ads)
    Box(
        Modifier.height(40.dp).streetBackground(theme, palette.surface, 20.dp, palette.border, 2.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = words }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = palette.heart)) { append("♥") }
                append("  ")
                append(words.uppercase())
            },
            style = TextStyle(color = palette.text, fontSize = 12.sp, fontFamily = Street.type.label, letterSpacing = 0.06.em),
            maxLines = 1,
        )
    }
}
