package com.justdataplease.streetgym.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.justdataplease.streetgym.app.data.ParksMap
import com.justdataplease.streetgym.app.data.ParksMapListener
import com.justdataplease.streetgym.app.ui.ParksMapHost
import platform.UIKit.UIView

/**
 * What Swift gives the app for Training › Parks: MapLibre maps (iosApp/StreetGym/ParksMapLibre.swift), as Kotlin can't
 * read MapLibre's Objective-C framework on Windows. Swift sees this as the protocol `NativeParksMaps`.
 */
interface NativeParksMaps {
    /** A new map: [interactive] false is Free's still example; [listener] hears taps on its pins. */
    fun makeMap(interactive: Boolean, listener: ParksMapListener): NativeParksMap
}

/** A MapLibre map made in Swift: what Parks asks of a map ([ParksMap]), and its view. */
interface NativeParksMap : ParksMap {
    val view: UIView
}

/**
 * Parks' maps on iPhone: Swift's MapLibre maps, drawn by Compose through UIKitView. The map's own view stays under
 * Compose, so the location button and the park's card are drawn over it, and it takes its gestures at once (it fills
 * the screen and doesn't scroll with the page). The same view is shown again on coming back to Parks.
 */
internal class IosParksMaps(private val native: NativeParksMaps) : ParksMapHost {
    /** Free's still example sits in a scrolling page: the page keeps its scroll, and the layer over it the taps. */
    private val still = mutableListOf<ParksMap>()

    override fun create(interactive: Boolean, listener: ParksMapListener): ParksMap? =
        runCatching { native.makeMap(interactive, listener) }.getOrNull()?.also { if (!interactive) still += it }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Show(map: ParksMap, modifier: Modifier) {
        val view = (map as? NativeParksMap)?.view ?: return
        val example = still.any { it === map }
        UIKitView(
            factory = { view },
            modifier = modifier,
            properties = UIKitInteropProperties(
                interactionMode = if (example) UIKitInteropInteractionMode.Cooperative() else UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = !example,
            ),
        )
    }
}
