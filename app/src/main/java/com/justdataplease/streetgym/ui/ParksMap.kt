package com.justdataplease.streetgym.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.parks.Park
import com.justdataplease.streetgym.data.parks.Parks
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

/**
 * Parks near me as a map: OpenFreeMap tiles through MapLibre (as in JustMaps: no API key, no per-view cost),
 * the parks as clustered pins, the athlete's approximate location as a blue dot, and a card with directions for the
 * tapped park. One instance lives while the Parks map is on screen; the activity forwards its lifecycle.
 */
class ParksMap(
    private val context: Context,
    private val onLocate: () -> Unit,
    private val onDirections: (Park) -> Unit,
    me: Pair<Double, Double>?,
) {
    private var parks: List<Park> = emptyList()
    private var parksJson: String? = null
    private var me: Pair<Double, Double>? = me
    private var map: MapLibreMap? = null
    /** A camera move asked for before the map was ready (a location fix can arrive first). */
    private var pendingCamera: ((MapLibreMap) -> Unit)? = null
    private var style: Style? = null
    private var started = false
    private var resumed = false
    private var destroyed = false

    private val mapView: MapView
    private val card: LinearLayout
    private val locate: ImageView
    val view: FrameLayout

    init {
        MapLibre.getInstance(context.applicationContext)
        val start = me?.let { (lat, lng) -> CameraPosition.Builder().target(LatLng(lat, lng)).zoom(NEAR_ZOOM).build() }
            ?: CameraPosition.Builder().target(LatLng(30.0, 10.0)).zoom(1.2).build()
        mapView = MapView(context, MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true).logoEnabled(false).attributionEnabled(true).tiltGesturesEnabled(false).camera(start))
        mapView.onCreate(null)
        mapView.getMapAsync { ready ->
            map = ready
            pendingCamera?.invoke(ready)
            pendingCamera = null
            ready.setStyle(Style.Builder().fromUri(if (AppColors.darkMode) STYLE_DARK else STYLE_LIGHT)) { loaded ->
                style = loaded
                addLayers(loaded)
                showData()
            }
            ready.addOnMapClickListener { point -> onTap(ready, point) }
        }
        card = context.card().apply {
            visibility = View.GONE
            isClickable = true
            elevation = context.dp(4).toFloat()
        }
        locate = ImageView(context).apply {
            setImageResource(R.drawable.ic_my_location)
            contentDescription = context.getString(R.string.parks_my_location)
            val pad = context.dp(12)
            setPadding(pad, pad, pad, pad)
            background = roundedDrawable(AppColors.SURFACE, context.dp(24).toFloat(), AppColors.DIVIDER, context.dp(1))
            elevation = context.dp(4).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { onLocate() }
        }
        tintLocate()
        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(locate, LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply {
                gravity = Gravity.END
                bottomMargin = context.dp(12)
            })
            addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        view = FrameLayout(context).apply {
            clipToOutline = true
            background = roundedDrawable(AppColors.SURFACE, context.dp(24).toFloat(), AppColors.DIVIDER, context.dp(1))
            addView(mapView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM).apply { val m = context.dp(12); setMargins(m, m, m, m) })
        }
    }

    /** The parks and their GeoJSON ([geoJson] of the same list, built off the main thread). */
    fun showParks(parks: List<Park>, json: String) {
        if (json === parksJson) return
        this.parks = parks
        parksJson = json
        hideCard()
        showData()
    }

    /** The athlete's approximate location as a blue dot. */
    fun showMe(lat: Double, lng: Double) {
        me = lat to lng
        tintLocate()
        showData()
    }

    /** The athlete's location with the [nearest] parks in view (as far out as the nearest ones are). */
    fun showNearby(lat: Double, lng: Double, nearest: List<Park>) {
        showMe(lat, lng)
        camera { map ->
            val here = LatLng(lat, lng)
            val points = nearest.map { LatLng(it.lat, it.lng) }.filter { it != here }
            if (points.isEmpty()) map.animateCamera(CameraUpdateFactory.newLatLngZoom(here, NEAR_ZOOM), 700)
            else map.animateCamera(CameraUpdateFactory.newLatLngBounds(
                LatLngBounds.Builder().include(here).apply { points.forEach { include(it) } }.build(), context.dp(56)), 700)
        }
    }

    /** Fits the map to [matches] (a search); one match also opens its card. */
    fun focus(matches: List<Park>) = camera { map ->
        when {
            matches.isEmpty() -> Unit
            matches.size == 1 || matches.all { it.lat == matches[0].lat && it.lng == matches[0].lng } -> {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(matches[0].lat, matches[0].lng), PARK_ZOOM), 700)
                if (matches.size == 1) showCard(matches[0])
            }
            else -> {
                val bounds = LatLngBounds.Builder().apply { matches.forEach { include(LatLng(it.lat, it.lng)) } }.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, context.dp(48)), 700)
                hideCard()
            }
        }
    }

    fun start() { if (!started && !destroyed) { mapView.onStart(); started = true } }
    fun resume() { start(); if (!resumed && !destroyed) { mapView.onResume(); resumed = true } }
    fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
    fun stop() { pause(); if (started) { mapView.onStop(); started = false } }
    fun lowMemory() { if (!destroyed) mapView.onLowMemory() }
    fun destroy() {
        if (destroyed) return
        stop()
        mapView.onDestroy()
        destroyed = true
        map = null
        style = null
    }

    /** Runs [move] now, or once the map is ready; the latest request wins. */
    private fun camera(move: (MapLibreMap) -> Unit) {
        val ready = map
        if (ready != null) move(ready) else if (!destroyed) pendingCamera = move
    }

    private fun tintLocate() {
        locate.imageTintList = android.content.res.ColorStateList.valueOf(if (me != null) ME_BLUE else AppColors.TEXT_SECONDARY)
    }

    private fun addLayers(style: Style) {
        style.addSource(GeoJsonSource(SOURCE_PARKS, GeoJsonOptions().withCluster(true).withClusterMaxZoom(13).withClusterRadius(48)))
        style.addSource(GeoJsonSource(SOURCE_ME))
        style.addImage(PIN, pin())
        val clustered = Expression.has("point_count")
        style.addLayer(CircleLayer(LAYER_CLUSTERS, SOURCE_PARKS).withFilter(clustered).withProperties(
            circleColor(AppColors.PRIMARY),
            circleRadius(Expression.step(Expression.get("point_count"), Expression.literal(15f),
                Expression.stop(50, 19f), Expression.stop(500, 24f))),
            circleStrokeColor(Color.WHITE),
            circleStrokeWidth(2f),
        ))
        style.addLayer(SymbolLayer(LAYER_COUNTS, SOURCE_PARKS).withFilter(clustered).withProperties(
            // MapLibre abbreviates 10,000 and more as "25.744000k", so those are rounded here: "26k".
            textField(Expression.step(Expression.get("point_count"), Expression.toString(Expression.get("point_count_abbreviated")),
                Expression.stop(10000, Expression.concat(
                    Expression.toString(Expression.round(Expression.division(Expression.get("point_count"), Expression.literal(1000)))),
                    Expression.literal("k"),
                )))),
            textFont(arrayOf("Noto Sans Regular")),
            textSize(12f),
            textColor(AppColors.ON_ACCENT),
            textAllowOverlap(true),
            textIgnorePlacement(true),
        ))
        style.addLayer(SymbolLayer(LAYER_PARKS, SOURCE_PARKS).withFilter(Expression.not(clustered)).withProperties(
            iconImage(PIN),
            iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        ))
        style.addLayer(CircleLayer(LAYER_ME_HALO, SOURCE_ME).withProperties(
            circleRadius(18f), circleColor(ME_BLUE), circleOpacity(0.18f),
        ))
        style.addLayer(CircleLayer(LAYER_ME, SOURCE_ME).withProperties(
            circleRadius(7f), circleColor(ME_BLUE), circleStrokeColor(Color.WHITE), circleStrokeWidth(2.5f),
        ))
    }

    private fun showData() {
        val style = style ?: return
        parksJson?.let { json -> style.getSourceAs<GeoJsonSource>(SOURCE_PARKS)?.setGeoJson(json) }
        me?.let { (lat, lng) ->
            style.getSourceAs<GeoJsonSource>(SOURCE_ME)?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lng, lat)))
        }
    }

    private fun onTap(map: MapLibreMap, point: LatLng): Boolean {
        val screen = map.projection.toScreenLocation(point)
        val reach = context.dp(14).toFloat()
        val area = RectF(screen.x - reach, screen.y - reach, screen.x + reach, screen.y + reach)
        map.queryRenderedFeatures(area, LAYER_CLUSTERS).firstOrNull()?.let { cluster ->
            val source = style?.getSourceAs<GeoJsonSource>(SOURCE_PARKS) ?: return true
            val at = cluster.geometry() as? Point ?: return true
            val zoom = runCatching { source.getClusterExpansionZoom(cluster).toDouble() }.getOrDefault(map.cameraPosition.zoom + 2)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(at.latitude(), at.longitude()), zoom), 500)
            return true
        }
        val park = map.queryRenderedFeatures(area, LAYER_PARKS).firstOrNull()
            ?.getNumberProperty("i")?.toInt()?.let(parks::getOrNull)
        if (park != null) showCard(park) else hideCard()
        return park != null
    }

    private fun showCard(park: Park) {
        card.removeAllViews()
        card.addView(context.titleText(park.name, 16f))
        me?.let { (lat, lng) ->
            val km = Parks.distance(lat, lng, park.lat, park.lng)
            card.addWithMargins(context.bodyText(
                if (km < 1) context.getString(R.string.parks_m, (km * 1000).toInt()) else context.getString(R.string.parks_km, km), 13f),
                topDp = 4)
        }
        card.addWithMargins(context.primaryButton(context.getString(R.string.parks_directions_button)) { onDirections(park) }, topDp = 12)
        card.visibility = View.VISIBLE
    }

    private fun hideCard() { card.visibility = View.GONE }

    /** A map pin in the app's green with a white centre, its tip at the bottom. */
    private fun pin(): Bitmap {
        val w = context.dp(28)
        val h = context.dp(36)
        val stroke = context.dp(2).toFloat()
        val r = w / 2f - stroke
        val cx = w / 2f
        val cy = w / 2f
        val shape = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            val tail = Path().apply {
                moveTo(cx - r * 0.7f, cy + r * 0.7f)
                lineTo(cx, h - stroke)
                lineTo(cx + r * 0.7f, cy + r * 0.7f)
                close()
            }
            op(tail, Path.Op.UNION)
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = AppColors.PRIMARY
            canvas.drawPath(shape, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = Color.WHITE
            canvas.drawPath(shape, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, r * 0.38f, paint)
        }
    }

    companion object {
        private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
        private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
        private const val SOURCE_PARKS = "parks"
        private const val SOURCE_ME = "me"
        private const val LAYER_CLUSTERS = "parks-clusters"
        private const val LAYER_COUNTS = "parks-counts"
        private const val LAYER_PARKS = "parks-points"
        private const val LAYER_ME_HALO = "me-halo"
        private const val LAYER_ME = "me-dot"
        private const val PIN = "park-pin"
        private const val NEAR_ZOOM = 12.5
        private const val PARK_ZOOM = 15.0
        private val ME_BLUE = 0xFF1A73E8.toInt()
    }
}
