import FirebaseAnalytics
import Foundation
import StreetGymApp

/// Google Analytics 4 through Firebase Analytics (Android: analytics/GoogleAnalytics.kt FirebaseAnalyticsSink, new in
/// 2.15.0). The app (Kotlin, composeApp: data/GoogleAnalytics.kt and data/AnalyticsConsent.kt) decides what is logged,
/// when collection runs and the consent, as Android does; Swift only passes it on, as Kotlin can't read Firebase's Swift
/// API. Handed over as `NativeAnalytics` (composeApp, iosMain, IosAnalytics.kt).
///
/// Nothing is collected before the app decides: the Info.plist (project.yml) starts with collection off, every consent
/// type denied and automatic screen reporting off, as Android's manifest. Without the owner's GoogleService-Info.plist
/// `configured()` is false and the app never calls the rest.
///
/// Firebase's class is written `FirebaseAnalytics.Analytics` in full: the app's framework has a class of its own named
/// Analytics (progress analytics, data/Analytics.kt).
final class FirebaseAnalyticsSink: NSObject, NativeAnalytics {
    /// Asking configures Firebase (FirebaseSetup, FirebaseAccount.swift), before Google's consent SDK starts, as Google's
    /// consent mode asks ("Initialize Firebase before starting the UMP SDK").
    func configured() -> Bool {
        FirebaseSetup.configured
    }

    func setConsent(adStorage: Bool, adUserData: Bool, adPersonalization: Bool, analyticsStorage: Bool) {
        func status(_ granted: Bool) -> FirebaseAnalytics.ConsentStatus { granted ? .granted : .denied }
        FirebaseAnalytics.Analytics.setConsent([
            .adStorage: status(adStorage),
            .adUserData: status(adUserData),
            .adPersonalization: status(adPersonalization),
            .analyticsStorage: status(analyticsStorage),
        ])
    }

    func setCollectionEnabled(enabled: Bool) {
        FirebaseAnalytics.Analytics.setAnalyticsCollectionEnabled(enabled)
    }

    /// Text as it is; whole numbers as Android's putLong (Kotlin's Long comes as an NSNumber), a double as putDouble.
    func logEvent(name: String, parameters: [String: Any]) {
        let values = parameters.mapValues { value -> Any in
            switch value {
            case let text as String:
                return text
            case let number as NSNumber:
                let type = String(cString: number.objCType)
                return type == "d" || type == "f" ? NSNumber(value: number.doubleValue) : NSNumber(value: number.int64Value)
            default:
                return String(describing: value)
            }
        }
        FirebaseAnalytics.Analytics.logEvent(name, parameters: values)
    }

    func setUserProperty(name: String, value: String?) {
        FirebaseAnalytics.Analytics.setUserProperty(value, forName: name)
    }
}
