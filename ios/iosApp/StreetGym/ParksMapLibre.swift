import MapLibre
import StreetGymApp
import UIKit

/// Training › Parks' maps (Android: ui/ParksMap.kt): OpenFreeMap's tiles through MapLibre, with no key and no cost per
/// view, the parks as clustered pins in the theme's colour, the athlete as a blue dot. The app (Kotlin) draws the
/// location button and the tapped park's card over it. Kotlin can't read MapLibre's framework on Windows, so the map is
/// made here and handed over as `NativeParksMaps` (composeApp, iosMain, IosParksMaps.kt).
final class MapLibreParks: NSObject, NativeParksMaps {
    func makeMap(interactive: Bool, listener: ParksMapListener) -> NativeParksMap {
        ParksMapLibre(interactive: interactive, listener: listener)
    }
}

final class ParksMapLibre: NSObject, NativeParksMap, MLNMapViewDelegate {
    private static let styleLight = URL(string: "https://tiles.openfreemap.org/styles/liberty")!
    private static let styleDark = URL(string: "https://tiles.openfreemap.org/styles/dark")!
    private static let nearZoom = 12.5
    private static let parkZoom = 15.0
    private static let meBlue = UIColor(red: 0x1A / 255.0, green: 0x73 / 255.0, blue: 0xE8 / 255.0, alpha: 1)

    private let mapView: MLNMapView
    private let listener: ParksMapListener
    private var parksJson: String?
    private var me: CLLocationCoordinate2D?
    private var dark = false
    private var pinColor = UIColor.black
    private var countColor = UIColor.white

    var view: UIView { mapView }

    init(interactive: Bool, listener: ParksMapListener) {
        self.listener = listener
        mapView = MLNMapView(frame: .zero, styleURL: ParksMapLibre.styleLight)
        super.init()
        mapView.delegate = self
        // Android hides the logo and keeps the ⓘ credit; no tilting.
        mapView.logoView.isHidden = true
        mapView.isPitchEnabled = false
        mapView.setCenter(CLLocationCoordinate2D(latitude: 30, longitude: 10), zoomLevel: 1.2, animated: false)
        if interactive {
            // After a double tap fails, so zooming in by double tap still works.
            let tap = UITapGestureRecognizer(target: self, action: #selector(tapped(_:)))
            for recognizer in mapView.gestureRecognizers ?? [] {
                if let double = recognizer as? UITapGestureRecognizer, double.numberOfTapsRequired == 2 {
                    tap.require(toFail: double)
                }
            }
            mapView.addGestureRecognizer(tap)
        } else {
            // Free's still example: the layer over it takes every tap.
            mapView.isUserInteractionEnabled = false
        }
    }

    func showParks(parks: [Park], geoJson: String) {
        if geoJson == parksJson { return }
        parksJson = geoJson
        showData()
    }

    func showMe(lat: Double, lng: Double) {
        me = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        showData()
    }

    func centre(lat: Double, lng: Double) {
        mapView.setCenter(CLLocationCoordinate2D(latitude: lat, longitude: lng), zoomLevel: ParksMapLibre.nearZoom, animated: false)
    }

    func showNearby(lat: Double, lng: Double, nearest: [Park]) {
        showMe(lat: lat, lng: lng)
        let here = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        let points = nearest.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }
            .filter { $0.latitude != lat || $0.longitude != lng }
        if points.isEmpty {
            mapView.setCenter(here, zoomLevel: ParksMapLibre.nearZoom, animated: true)
        } else {
            fit([here] + points, padding: 56)
        }
    }

    func focus(matches: [Park]) {
        guard let first = matches.first else { return }
        if matches.count == 1 || matches.allSatisfy({ $0.lat == first.lat && $0.lng == first.lng }) {
            mapView.setCenter(CLLocationCoordinate2D(latitude: first.lat, longitude: first.lng), zoomLevel: ParksMapLibre.parkZoom, animated: true)
        } else {
            fit(matches.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lng) }, padding: 48)
        }
    }

    func setLook(dark: Bool, pin: Int64, count: Int64) {
        pinColor = ParksMapLibre.color(pin)
        countColor = ParksMapLibre.color(count)
        if self.dark != dark {
            self.dark = dark
            mapView.styleURL = dark ? ParksMapLibre.styleDark : ParksMapLibre.styleLight
        } else if let style = mapView.style {
            addLayers(style)
        }
    }

    /// Each style (the first, and the other one after a change of theme) gets the parks' and the athlete's layers.
    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        addLayers(style)
    }

    private func addLayers(_ style: MLNStyle) {
        for id in ["me-dot", "me-halo", "parks-points", "parks-counts", "parks-clusters"] {
            if let layer = style.layer(withIdentifier: id) { style.removeLayer(layer) }
        }
        for id in ["parks", "me"] {
            if let source = style.source(withIdentifier: id) { style.removeSource(source) }
        }
        let parks = MLNShapeSource(identifier: "parks", shape: nil,
                                   options: [.clustered: true, .maximumZoomLevelForClustering: 13, .clusterRadius: 48])
        style.addSource(parks)
        let mine = MLNShapeSource(identifier: "me", shape: nil, options: nil)
        style.addSource(mine)
        style.setImage(pinImage(), forName: "park-pin")

        let clusters = MLNCircleStyleLayer(identifier: "parks-clusters", source: parks)
        clusters.predicate = NSPredicate(format: "cluster == YES")
        clusters.circleColor = NSExpression(forConstantValue: pinColor)
        clusters.circleRadius = NSExpression(mglJSONObject: ["step", ["get", "point_count"], 15, 50, 19, 500, 24])
        clusters.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
        clusters.circleStrokeWidth = NSExpression(forConstantValue: 2)
        style.addLayer(clusters)

        // MapLibre abbreviates 10,000 and more as "25.744000k", so those are rounded here: "26k" (as on Android).
        let counts = MLNSymbolStyleLayer(identifier: "parks-counts", source: parks)
        counts.predicate = NSPredicate(format: "cluster == YES")
        counts.text = NSExpression(mglJSONObject: [
            "step", ["get", "point_count"], ["to-string", ["get", "point_count_abbreviated"]],
            10000, ["concat", ["to-string", ["round", ["/", ["get", "point_count"], 1000]]], "k"],
        ])
        counts.textFontNames = NSExpression(forConstantValue: ["Noto Sans Regular"])
        counts.textFontSize = NSExpression(forConstantValue: 12)
        counts.textColor = NSExpression(forConstantValue: countColor)
        counts.textAllowsOverlap = NSExpression(forConstantValue: true)
        counts.textIgnoresPlacement = NSExpression(forConstantValue: true)
        style.addLayer(counts)

        let points = MLNSymbolStyleLayer(identifier: "parks-points", source: parks)
        points.predicate = NSPredicate(format: "cluster != YES")
        points.iconImageName = NSExpression(forConstantValue: "park-pin")
        points.iconAnchor = NSExpression(forConstantValue: "bottom")
        points.iconAllowsOverlap = NSExpression(forConstantValue: true)
        points.iconIgnoresPlacement = NSExpression(forConstantValue: true)
        style.addLayer(points)

        let halo = MLNCircleStyleLayer(identifier: "me-halo", source: mine)
        halo.circleRadius = NSExpression(forConstantValue: 18)
        halo.circleColor = NSExpression(forConstantValue: ParksMapLibre.meBlue)
        halo.circleOpacity = NSExpression(forConstantValue: 0.18)
        style.addLayer(halo)
        let dot = MLNCircleStyleLayer(identifier: "me-dot", source: mine)
        dot.circleRadius = NSExpression(forConstantValue: 7)
        dot.circleColor = NSExpression(forConstantValue: ParksMapLibre.meBlue)
        dot.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
        dot.circleStrokeWidth = NSExpression(forConstantValue: 2.5)
        style.addLayer(dot)
        showData()
    }

    private func showData() {
        guard let style = mapView.style else { return }
        if let json = parksJson, let source = style.source(withIdentifier: "parks") as? MLNShapeSource,
           let data = json.data(using: .utf8) {
            source.shape = try? MLNShape(data: data, encoding: String.Encoding.utf8.rawValue)
        }
        if let me, let source = style.source(withIdentifier: "me") as? MLNShapeSource {
            let point = MLNPointFeature()
            point.coordinate = me
            source.shape = point
        }
    }

    private func fit(_ coordinates: [CLLocationCoordinate2D], padding: CGFloat) {
        var south = coordinates[0].latitude, north = south, west = coordinates[0].longitude, east = west
        for coordinate in coordinates {
            south = min(south, coordinate.latitude)
            north = max(north, coordinate.latitude)
            west = min(west, coordinate.longitude)
            east = max(east, coordinate.longitude)
        }
        let bounds = MLNCoordinateBounds(sw: CLLocationCoordinate2D(latitude: south, longitude: west),
                                         ne: CLLocationCoordinate2D(latitude: north, longitude: east))
        let inset = UIEdgeInsets(top: padding, left: padding, bottom: padding, right: padding)
        mapView.setVisibleCoordinateBounds(bounds, edgePadding: inset, animated: true, completionHandler: nil)
    }

    /// A cluster zooms in to where it splits; a pin opens its park's card; anywhere else closes the card.
    @objc private func tapped(_ sender: UITapGestureRecognizer) {
        guard sender.state == .ended else { return }
        let point = sender.location(in: mapView)
        let area = CGRect(x: point.x - 14, y: point.y - 14, width: 28, height: 28)
        if let cluster = mapView.visibleFeatures(in: area, styleLayerIdentifiers: ["parks-clusters"]).first as? MLNPointFeatureCluster {
            let source = mapView.style?.source(withIdentifier: "parks") as? MLNShapeSource
            let zoom = source?.zoomLevel(forExpanding: cluster) ?? -1
            mapView.setCenter(cluster.coordinate, zoomLevel: zoom >= 0 ? zoom : mapView.zoomLevel + 2, animated: true)
            return
        }
        if let park = mapView.visibleFeatures(in: area, styleLayerIdentifiers: ["parks-points"]).first,
           let index = park.attribute(forKey: "i") as? NSNumber {
            listener.parkTapped(index: index.int32Value)
        } else {
            listener.mapTapped()
        }
    }

    /// A map pin in the theme's colour with a white rim and centre, its tip at the bottom (Android: ParksMap.pin).
    private func pinImage() -> UIImage {
        let width: CGFloat = 28, height: CGFloat = 36, stroke: CGFloat = 2
        let radius = width / 2 - stroke
        let centre = CGPoint(x: width / 2, y: width / 2)
        let color = pinColor
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height)).image { _ in
            // The circle from its lower left round to its lower right, then down to the tip: one outline.
            let shape = UIBezierPath(arcCenter: centre, radius: radius, startAngle: .pi * 0.75, endAngle: .pi * 2.25, clockwise: true)
            shape.addLine(to: CGPoint(x: centre.x, y: height - stroke))
            shape.close()
            color.setFill()
            shape.fill()
            UIColor.white.setStroke()
            shape.lineWidth = stroke
            shape.stroke()
            UIColor.white.setFill()
            UIBezierPath(arcCenter: centre, radius: radius * 0.38, startAngle: 0, endAngle: .pi * 2, clockwise: true).fill()
        }
    }

    private static func color(_ argb: Int64) -> UIColor {
        UIColor(red: CGFloat((argb >> 16) & 0xFF) / 255, green: CGFloat((argb >> 8) & 0xFF) / 255,
                blue: CGFloat(argb & 0xFF) / 255, alpha: CGFloat((argb >> 24) & 0xFF) / 255)
    }
}
