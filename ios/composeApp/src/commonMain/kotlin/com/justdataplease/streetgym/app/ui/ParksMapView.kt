package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.justdataplease.streetgym.app.data.Park
import com.justdataplease.streetgym.app.data.ParksMap
import com.justdataplease.streetgym.app.data.ParksMapListener
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Makes and draws the Parks map: MapLibre on iPhone (iosMain over the Swift in iosApp), the [StandInMaps] dots on the
 * desktop. [create] returns null where no map can start, and Parks shows its list instead (Android: parksMapFailed).
 */
interface ParksMapHost {
    /** A new map: [interactive] false is Free's still example, with no gestures. */
    fun create(interactive: Boolean, listener: ParksMapListener): ParksMap?

    /** Draws [map] over [modifier]'s place; the same map can be shown again after leaving the screen. */
    @Composable
    fun Show(map: ParksMap, modifier: Modifier)
}

/** The maps of the app: the stand-in until the phone gives its own; null, the list only. */
val LocalParksMaps = staticCompositionLocalOf<ParksMapHost?> { StandInMaps }

/** The map's colours as Android's ParksMap draws them: the athlete's blue dot. */
internal val ME_BLUE = Color(0xFF1A73E8)

/** The camera at a city's zoom and at one park's (Android: NEAR_ZOOM, PARK_ZOOM), and the world before a position. */
internal const val NEAR_ZOOM = 12.5
internal const val PARK_ZOOM = 15.0

/**
 * A plain map for the desktop window, the screenshots and tests: no tiles, the parks as dots in the theme's colour on a
 * plain ground, the athlete as Android's blue dot, and a camera that frames them as MapLibre's would. A tap on a dot
 * reports its park.
 */
object StandInMaps : ParksMapHost {
    override fun create(interactive: Boolean, listener: ParksMapListener): ParksMap = DotsMap(interactive, listener)

    @Composable
    override fun Show(map: ParksMap, modifier: Modifier) {
        (map as? DotsMap)?.Draw(modifier)
    }
}

/** What [StandInMaps] draws; it keeps every call, for tests. */
class DotsMap(val interactive: Boolean, private val listener: ParksMapListener) : ParksMap {
    var parks by mutableStateOf<List<Park>>(emptyList())
        private set
    var me by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    var dark by mutableStateOf(false)
        private set
    private var pin by mutableStateOf(0xFF121212)
    /** Where the camera is: a centre and zoom, or points to fit with a margin in dp. */
    var camera by mutableStateOf<Camera>(Camera.At(30.0, 10.0, 1.2))
        private set

    sealed interface Camera {
        data class At(val lat: Double, val lng: Double, val zoom: Double) : Camera
        data class Fit(val points: List<Pair<Double, Double>>, val margin: Float) : Camera
    }

    // Each park's place on the world square, 0 to 1, made once per list.
    private var projected: Pair<List<Park>, FloatArray>? = null

    override fun showParks(parks: List<Park>, geoJson: String) {
        this.parks = parks
    }

    override fun showMe(lat: Double, lng: Double) {
        me = lat to lng
    }

    override fun centre(lat: Double, lng: Double) {
        camera = Camera.At(lat, lng, NEAR_ZOOM)
    }

    override fun showNearby(lat: Double, lng: Double, nearest: List<Park>) {
        showMe(lat, lng)
        val points = nearest.map { it.lat to it.lng }.filter { it != lat to lng }
        camera = if (points.isEmpty()) Camera.At(lat, lng, NEAR_ZOOM) else Camera.Fit(listOf(lat to lng) + points, 56f)
    }

    override fun focus(matches: List<Park>) {
        camera = when {
            matches.isEmpty() -> camera
            matches.size == 1 || matches.all { it.lat == matches[0].lat && it.lng == matches[0].lng } ->
                Camera.At(matches[0].lat, matches[0].lng, PARK_ZOOM)
            else -> Camera.Fit(matches.map { it.lat to it.lng }, 48f)
        }
    }

    override fun setLook(dark: Boolean, pin: Long, count: Long) {
        this.dark = dark
        this.pin = pin
    }

    /** A tap on the pin of park [index], as a finger would (tests). */
    internal fun tapPark(index: Int) = listener.parkTapped(index)

    @Composable
    internal fun Draw(modifier: Modifier) {
        val ground = if (dark) Color(0xFF1E2125) else Color(0xFFEDEAE4)
        val pinColor = Color(pin)
        val parks = parks
        val me = me
        val camera = camera
        Canvas(
            modifier.clipToBounds().then(if (!interactive) Modifier else Modifier.pointerInput(parks, camera) {
                detectTapGestures { tap ->
                    val view = view(size.width.toFloat(), size.height.toFloat(), density, camera)
                    val reach = 14f * density
                    var best = -1
                    var bestDistance = reach
                    val world = world(parks)
                    for (index in parks.indices) {
                        val x = view.x(world[index * 2])
                        val y = view.y(world[index * 2 + 1])
                        val d = sqrt((x - tap.x) * (x - tap.x) + (y - tap.y) * (y - tap.y))
                        if (d <= bestDistance) {
                            best = index
                            bestDistance = d
                        }
                    }
                    if (best >= 0) listener.parkTapped(best) else listener.mapTapped()
                }
            }),
        ) {
            drawRect(ground)
            val view = view(size.width, size.height, density, camera)
            val world = world(parks)
            val radius = 6f * density
            for (index in parks.indices) {
                val x = view.x(world[index * 2])
                val y = view.y(world[index * 2 + 1])
                if (x < -radius || y < -radius || x > size.width + radius || y > size.height + radius) continue
                drawCircle(pinColor, radius, Offset(x, y))
                drawCircle(Color.White, radius, Offset(x, y), style = Stroke(2f * density))
            }
            me?.let { (lat, lng) ->
                val at = Offset(view.x(mercatorX(lng)), view.y(mercatorY(lat)))
                drawCircle(ME_BLUE.copy(alpha = 0.18f), 18f * density, at)
                drawCircle(ME_BLUE, 7f * density, at)
                drawCircle(Color.White, 7f * density, at, style = Stroke(2.5f * density))
            }
        }
    }

    private fun world(parks: List<Park>): FloatArray {
        projected?.takeIf { it.first === parks }?.let { return it.second }
        val points = FloatArray(parks.size * 2)
        parks.forEachIndexed { index, park ->
            points[index * 2] = mercatorX(park.lng)
            points[index * 2 + 1] = mercatorY(park.lat)
        }
        projected = parks to points
        return points
    }

    /** The world square (0 to 1) on a view [width] by [height] pixels: its scale and where its corner is. */
    private class View(val scale: Float, val left: Float, val top: Float) {
        fun x(world: Float): Float = (world - left) * scale
        fun y(world: Float): Float = (world - top) * scale
    }

    private fun view(width: Float, height: Float, density: Float, camera: Camera): View = when (camera) {
        // MapLibre's world is 512 points wide at zoom 0.
        is Camera.At -> {
            val scale = (512.0 * 2.0.pow(camera.zoom) * density).toFloat()
            View(scale, mercatorX(camera.lng) - width / 2f / scale, mercatorY(camera.lat) - height / 2f / scale)
        }
        is Camera.Fit -> {
            val xs = camera.points.map { mercatorX(it.second) }
            val ys = camera.points.map { mercatorY(it.first) }
            val margin = camera.margin * density
            val spanX = max(xs.max() - xs.min(), 1e-9f)
            val spanY = max(ys.max() - ys.min(), 1e-9f)
            val scale = min((width - 2 * margin).coerceAtLeast(1f) / spanX, (height - 2 * margin).coerceAtLeast(1f) / spanY)
            View(scale, (xs.min() + xs.max()) / 2f - width / 2f / scale, (ys.min() + ys.max()) / 2f - height / 2f / scale)
        }
    }
}

private fun mercatorX(lng: Double): Float = ((lng + 180.0) / 360.0).toFloat()

private fun mercatorY(lat: Double): Float {
    val clamped = lat.coerceIn(-85.0511, 85.0511) * PI / 180.0
    return (0.5 - ln(tan(PI / 4 + clamped / 2)) / (2 * PI)).toFloat()
}
