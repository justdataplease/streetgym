package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Where the back swipe must start: this close to the screen's left edge, as iPhone's own. */
private val EDGE = 24.dp

/** How far right it must go, mostly sideways, before the screen goes back. */
private val DISTANCE = 64.dp

/**
 * iPhone's back swipe (Android: the system back gesture, HomeActivity.navigateBack): on a nested screen, a drag that
 * starts at the left edge and goes right goes back. It looks at the touches before the screen does, and once it has gone
 * back it keeps the rest of that drag from the screen, so nothing under the finger scrolls or taps.
 */
fun Modifier.backSwipe(enabled: Boolean, onBack: () -> Unit): Modifier = if (!enabled) this else composed {
    val back by rememberUpdatedState(onBack)
    pointerInput(Unit) {
        val edge = EDGE.toPx()
        val distance = DISTANCE.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x > edge) return@awaitEachGesture
            var moved = Offset.Zero
            var done = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (done) change.consume()
                    break
                }
                if (done) {
                    change.consume()
                    continue
                }
                moved += change.positionChange()
                if (moved.y > distance || -moved.y > distance) break
                if (moved.x > distance && moved.x > abs(moved.y) * 2) {
                    change.consume()
                    done = true
                    back()
                }
            }
        }
    }
}
