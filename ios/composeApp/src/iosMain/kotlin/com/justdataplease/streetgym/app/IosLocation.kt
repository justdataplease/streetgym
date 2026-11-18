package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.Fix
import com.justdataplease.streetgym.app.data.LocationPermission
import com.justdataplease.streetgym.app.data.Parks
import com.justdataplease.streetgym.app.data.PhoneLocation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.darwin.NSObject

/**
 * The athlete's position for Parks (Android: ACCESS_COARSE_LOCATION and LocationManager), through Core Location: asked
 * for only while the app is in use, to a kilometre (Info.plist also asks for an approximate position by default), never
 * sent anywhere. A fresh position is one request, not a stream.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosLocation : PhoneLocation {
    private val manager: CLLocationManager by lazy {
        CLLocationManager().apply {
            desiredAccuracy = kCLLocationAccuracyKilometer
            delegate = locationDelegate
        }
    }

    /** The athlete's answer is awaited by this. */
    private var asking: ((Boolean) -> Unit)? = null

    /** A fresh position is awaited by these. */
    private val waiting = mutableListOf<(Fix?) -> Unit>()

    override fun permission(): LocationPermission = when (manager.authorizationStatus) {
        kCLAuthorizationStatusNotDetermined -> LocationPermission.NOT_ASKED
        kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> LocationPermission.ALLOWED
        else -> LocationPermission.DENIED
    }

    override fun ask(result: (Boolean) -> Unit) {
        asking = result
        manager.requestWhenInUseAuthorization()
    }

    override fun last(): Fix? = manager.location?.let(::fix)

    /** Core Location says when it can't find one ([locationManager] didFailWithError), so a request is always tried. */
    override fun available(): Boolean = true

    override fun current(result: (Fix?) -> Unit) {
        waiting += result
        manager.requestLocation()
    }

    override fun openDirections(name: String, lat: Double, lng: Double) {
        val url = NSURL.URLWithString(Parks.mapsLink(name, lat, lng)) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    fun answered() {
        val result = asking ?: return
        if (manager.authorizationStatus == kCLAuthorizationStatusNotDetermined) return
        asking = null
        result(permission() == LocationPermission.ALLOWED)
    }

    fun found(fix: Fix?) {
        val all = waiting.toList()
        waiting.clear()
        all.forEach { it(fix) }
    }

    fun fix(location: CLLocation): Fix = location.coordinate.useContents {
        Fix(latitude, longitude, (location.timestamp.timeIntervalSince1970 * 1_000.0).toLong())
    }
}

/** Core Location's answers, on the main thread where the manager was made; held here, as the manager keeps it weakly. */
private val locationDelegate = LocationDelegate()

@OptIn(ExperimentalForeignApi::class)
private class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        IosLocation.found((didUpdateLocations.lastOrNull() as? CLLocation)?.let(IosLocation::fix))
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        IosLocation.found(null)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        IosLocation.answered()
    }
}
