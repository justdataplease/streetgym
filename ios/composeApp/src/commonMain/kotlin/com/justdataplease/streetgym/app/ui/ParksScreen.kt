package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.NearbyPark
import com.justdataplease.streetgym.app.data.Park
import com.justdataplease.streetgym.app.data.Parks
import com.justdataplease.streetgym.app.data.ParksMap
import com.justdataplease.streetgym.app.data.ParksMapListener
import com.justdataplease.streetgym.app.data.ParksNote
import com.justdataplease.streetgym.app.data.ParksState
import com.justdataplease.streetgym.app.data.Paywall
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.ic_my_location
import com.justdataplease.streetgym.app.resources.parks_credit
import com.justdataplease.streetgym.app.resources.parks_directions
import com.justdataplease.streetgym.app.resources.parks_directions_button
import com.justdataplease.streetgym.app.resources.parks_example
import com.justdataplease.streetgym.app.resources.parks_intro
import com.justdataplease.streetgym.app.resources.parks_km
import com.justdataplease.streetgym.app.resources.parks_loading
import com.justdataplease.streetgym.app.resources.parks_locating
import com.justdataplease.streetgym.app.resources.parks_location_denied
import com.justdataplease.streetgym.app.resources.parks_m
import com.justdataplease.streetgym.app.resources.parks_matches
import com.justdataplease.streetgym.app.resources.parks_my_location
import com.justdataplease.streetgym.app.resources.parks_near_me
import com.justdataplease.streetgym.app.resources.parks_nearest
import com.justdataplease.streetgym.app.resources.parks_no_location
import com.justdataplease.streetgym.app.resources.parks_none
import com.justdataplease.streetgym.app.resources.parks_privacy
import com.justdataplease.streetgym.app.resources.parks_search
import com.justdataplease.streetgym.app.resources.parks_search_hint
import com.justdataplease.streetgym.app.resources.parks_teaser
import com.justdataplease.streetgym.app.resources.parks_view_list
import com.justdataplease.streetgym.app.resources.parks_view_map
import com.justdataplease.streetgym.app.resources.tier_plus
import com.justdataplease.streetgym.app.theme.Street
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The height of Free's example map (Android: EXAMPLE_MAP_DP). */
private val EXAMPLE_MAP = 340.dp

/**
 * Training › Parks (android-streetgym, HomeActivity.renderParks and renderParksExample): Free sees a still map of a
 * dozen parks in Athens under a layer with Plus and Unlock; Plus and Pro find the parks near them, or by city or park
 * name, on a map that fills the screen (the default) or as a list, chosen with Map / List and kept. The parks ship
 * with the app and the location never leaves the phone. [tabs] draws Training's switch at the top.
 */
@Composable
fun ParksScreen(model: AppModel, tabs: @Composable (Modifier) -> Unit, onPaywall: (Paywall) -> Unit, onNote: (String) -> Unit) {
    val parks = model.parks
    val host = LocalParksMaps.current
    ParksNotes(parks, onNote)
    if (!model.tier.parks) {
        Page {
            tabs(Modifier.padding(bottom = 14.dp))
            ParksExample(parks, host) { onPaywall(Paywall.Parks) }
            EndAd(model)
        }
        return
    }
    LaunchedEffect(parks) { parks.load() }
    val all = parks.all
    if (all == null) {
        Page {
            tabs(Modifier.padding(bottom = 14.dp))
            BodyText(stringResource(Res.string.parks_loading))
            EndAd(model)
        }
        return
    }
    val map = if (parks.showList || parks.mapFailed) null else rememberParksMap(parks, host)
    if (map != null) {
        LaunchedEffect(map, all) {
            map.showParks(all, parks.geoJson.orEmpty())
            parks.mapOpened()
        }
        Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp)) {
            tabs(Modifier.padding(bottom = 14.dp))
            ParksControls(parks)
            ParksMapFrame(parks, map, host!!, Modifier.padding(top = 10.dp).weight(1f).fillMaxWidth())
            // One line, so the map keeps the height; the map's ⓘ button carries the OpenFreeMap and OpenStreetMap credit.
            BodyText(stringResource(Res.string.parks_privacy), 12f, Modifier.padding(top = 6.dp))
        }
        return
    }
    Page {
        tabs(Modifier.padding(bottom = 14.dp))
        ParksControls(parks)
        BodyText(stringResource(Res.string.parks_intro, pointsText(all.size)), modifier = Modifier.padding(top = 10.dp))
        PrimaryButton(stringResource(Res.string.parks_near_me), Modifier.padding(top = 12.dp)) { parks.requestNearMe() }
        parks.results?.let { results -> ParksList(parks, results) }
        BodyText(stringResource(Res.string.parks_credit), 12f, Modifier.padding(top = 16.dp))
        // Not on the map, which fills the screen (Android: renderLevels' endAd unless the map shows).
        EndAd(model)
    }
}

/** Parks' notes in Android's words (its Toasts), shown by the shell. */
@Composable
private fun ParksNotes(parks: ParksState, onNote: (String) -> Unit) {
    val locating = stringResource(Res.string.parks_locating)
    val none = stringResource(Res.string.parks_no_location)
    val denied = stringResource(Res.string.parks_location_denied)
    val noMatch = stringResource(Res.string.parks_none)
    val note by rememberUpdatedState(onNote)
    DisposableEffect(parks) {
        parks.notes = { kind ->
            note(when (kind) {
                ParksNote.LOCATING -> locating
                ParksNote.NO_LOCATION -> none
                ParksNote.DENIED -> denied
                ParksNote.NO_MATCH -> noMatch
            })
        }
        onDispose { parks.notes = {} }
    }
}

/** The map once made, kept while the app runs; null where none can start, which shows the list from then on. */
@Composable
private fun rememberParksMap(parks: ParksState, host: ParksMapHost?): ParksMap? {
    parks.map?.let { return it }
    val made = remember(parks, host) {
        host?.create(true, object : ParksMapListener {
            override fun parkTapped(index: Int) {
                parks.card = parks.all?.getOrNull(index)
            }

            override fun mapTapped() {
                parks.card = null
            }
        })?.also { map ->
            parks.map = map
            // The map opens on the athlete when the position is known (Android: ParksMap's start camera).
            parks.origin?.let { (lat, lng) -> map.centre(lat, lng) }
        }
    }
    if (made == null) LaunchedEffect(Unit) { parks.mapFailed = true }
    return made
}

/** Map / List, then the search: "City or park name" and Search (Android: renderParks' switch and search row). */
@Composable
private fun ParksControls(parks: ParksState) {
    val focus = LocalFocusManager.current
    SegmentedSwitch(
        listOf(stringResource(Res.string.parks_view_map), stringResource(Res.string.parks_view_list)),
        if (parks.showList || parks.mapFailed) 1 else 0, Modifier.padding(bottom = 6.dp),
    ) { parks.showList = it == 1 }
    var field by remember { mutableStateOf(TextFieldValue(parks.typed, TextRange(parks.typed.length))) }
    fun run() {
        focus.clearFocus()
        parks.search(field.text.trim())
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        EntryField(field, stringResource(Res.string.parks_search_hint), Modifier.weight(1f), imeAction = ImeAction.Search, onAction = ::run) {
            field = it
            parks.typed = it.text
        }
        SecondaryButton(stringResource(Res.string.parks_search), wrap = true, onClick = ::run)
    }
}

/**
 * The map with its card and location button over it (Android: ParksMap's view): the button is grey until the athlete
 * is located, then blue; a tapped park's card has its name, distance and Directions. The frame is the card's outline.
 */
@Composable
private fun ParksMapFrame(parks: ParksState, map: ParksMap, host: ParksMapHost, modifier: Modifier) {
    val theme = Street.theme
    val palette = theme.palette
    LaunchedEffect(map, theme) { map.setLook(theme.dark, palette.primary.argb(), palette.onAccent.argb()) }
    Box(modifier.drawWithContent {
        drawContent()
        streetShape(theme, androidx.compose.ui.graphics.Color.Transparent, 24.dp.toPx(), palette.divider, 1.dp.toPx())
    }) {
        host.Show(map, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
            val located = parks.origin != null
            val locate = stringResource(Res.string.parks_my_location)
            Box(
                Modifier.align(Alignment.End).padding(bottom = 12.dp).size(48.dp)
                    .streetBackground(theme, palette.surface, 24.dp, palette.divider, 1.dp)
                    .clickable(role = Role.Button) { parks.requestNearMe() }
                    .semantics { contentDescription = locate },
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(Res.drawable.ic_my_location), contentDescription = null,
                    colorFilter = ColorFilter.tint(if (located) ME_BLUE else palette.textSecondary), modifier = Modifier.size(24.dp))
            }
            parks.card?.let { park ->
                StreetCard {
                    TitleText(park.name, 16f)
                    parks.kilometresTo(park)?.let { BodyText(distanceText(it), 13f, Modifier.padding(top = 4.dp)) }
                    PrimaryButton(stringResource(Res.string.parks_directions_button), Modifier.padding(top = 12.dp)) { parks.openPark(park) }
                }
            }
        }
    }
}

/**
 * The list's results (Android: renderParks' list): "Nearest to you" or "Matches for “x”", "No park matches." or how to
 * get directions, then a card per park with how far it is; a tap opens the phone's maps app at it.
 */
@Composable
private fun ColumnScope.ParksList(parks: ParksState, results: List<NearbyPark>) {
    SectionLabel(if (parks.query.isBlank()) stringResource(Res.string.parks_nearest) else stringResource(Res.string.parks_matches, parks.query),
        Modifier.padding(top = 14.dp))
    if (results.isEmpty()) BodyText(stringResource(Res.string.parks_none), 14f)
    else BodyText(stringResource(Res.string.parks_directions), 13f, Modifier.padding(bottom = 8.dp))
    results.forEach { nearby ->
        val distance = nearby.kilometres.takeIf { it >= 0 }?.let { distanceText(it) }
        StreetCard(
            Modifier.padding(bottom = 8.dp).semantics(mergeDescendants = true) {},
            onClick = { parks.openPark(nearby.park) },
        ) {
            TitleText(nearby.park.name, 16f)
            distance?.let { BodyText(it, 13f, Modifier.padding(top = 4.dp)) }
        }
    }
}

/** "350 m away" under a kilometre, else "2.4 km away" (Android: parks_m and parks_km's %.1f). */
@Composable
fun distanceText(kilometres: Double): String =
    if (kilometres < 1) stringResource(Res.string.parks_m, (kilometres * 1000).toInt())
    else stringResource(Res.string.parks_km, oneDecimal(kilometres))

/**
 * Free's Parks (Android: renderParksExample): the teaser, "Example: parks in Athens", and a still map of
 * [Parks.EXAMPLE] under a layer with Plus and Unlock; where no map can start, five of them as cards.
 */
@Composable
private fun ColumnScope.ParksExample(parks: ParksState, host: ParksMapHost?, onOffer: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    BodyText(stringResource(Res.string.parks_teaser), modifier = Modifier.padding(bottom = 10.dp))
    SectionLabel(stringResource(Res.string.parks_example))
    val map = parks.example ?: remember(parks, host) {
        host?.create(false, object : ParksMapListener {
            override fun parkTapped(index: Int) = onOffer()
            override fun mapTapped() = onOffer()
        })?.also { parks.example = it }
    }
    if (map != null) LaunchedEffect(map, theme) {
        map.setLook(theme.dark, palette.primary.argb(), palette.onAccent.argb())
        map.showParks(Parks.EXAMPLE, Parks.geoJson(Parks.EXAMPLE))
        map.focus(Parks.EXAMPLE)
    }
    LockedLayer(stringResource(Res.string.tier_plus), Modifier.padding(top = 6.dp), onOffer = onOffer) {
        if (map != null && host != null) Box(Modifier.fillMaxWidth().height(EXAMPLE_MAP).drawWithContent {
            drawContent()
            streetShape(theme, androidx.compose.ui.graphics.Color.Transparent, 24.dp.toPx(), palette.divider, 1.dp.toPx())
        }) { host.Show(map, Modifier.fillMaxSize()) }
        else Column(Modifier.fillMaxWidth()) {
            Parks.EXAMPLE.take(5).forEach { park -> StreetCard(Modifier.padding(bottom = 8.dp)) { TitleText(park.name, 16f) } }
        }
    }
}

/** A colour as 0xAARRGGBB, for the map. */
private fun androidx.compose.ui.graphics.Color.argb(): Long =
    ((alpha * 255f + 0.5f).toLong() shl 24) or ((red * 255f + 0.5f).toLong() shl 16) or
        ((green * 255f + 0.5f).toLong() shl 8) or (blue * 255f + 0.5f).toLong()
