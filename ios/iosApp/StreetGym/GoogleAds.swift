import AppTrackingTransparency
import GoogleMobileAds
import StreetGymApp
import UIKit
import UserMessagingPlatform

/// Free's ads (Android: monetization/NativeAds.kt and ads/WorkoutAdBreak.kt): Google's consent (User Messaging
/// Platform), Apple's tracking question, the native card and the full-screen ad of the Google Mobile Ads SDK. When each
/// may run is decided by the app (Kotlin, composeApp: data/AdRules.kt); Kotlin can't read these Swift frameworks, so they
/// are made here and handed over as `AdsBridge` (composeApp, iosMain, IosAds.kt). Every answer goes back on the main thread.
///
/// The units come from the Info.plist (`StreetGymNativeAdUnit`, `StreetGymInterstitialAdUnit`), which project.yml fills
/// from the build settings ADMOB_NATIVE_ID and ADMOB_INTERSTITIAL_ID: Google's published test units until the owner's
/// own are set there. Nothing about the athlete's training goes to Google.
final class GoogleAds: NSObject, AdsBridge, FullScreenContentDelegate {
    /// Google's test units (https://developers.google.com/admob/ios/test-ads).
    private static let testNative = "ca-app-pub-3940256099942544/3986624511"
    private static let testInterstitial = "ca-app-pub-3940256099942544/4411468910"

    private let nativeUnit = GoogleAds.unit("StreetGymNativeAdUnit", fallback: GoogleAds.testNative)
    private let interstitialUnit = GoogleAds.unit("StreetGymInterstitialAdUnit", fallback: GoogleAds.testInterstitial)
    private var listener: AdsBridgeListener?
    private var interstitial: InterstitialAd?
    private var activeObserver: NSObjectProtocol?

    func attach(listener: AdsBridgeListener) {
        self.listener = listener
    }

    /// Google's consent, brought up to date, then its form where the region needs it. An update that fails keeps the
    /// answer given before (Android: "respecting cached consent").
    func askConsent() {
        ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters()) { [weak self] error in
            Task { @MainActor in
                if error == nil, let root = GoogleAds.topController() {
                    try? await ConsentForm.loadAndPresentIfRequired(from: root)
                }
                self?.consentEnded()
            }
        }
    }

    /// Apple's App Tracking Transparency question, asked once the app is active (it doesn't show otherwise). Ads show
    /// either way; without tracking they aren't personalised. The Simulator skips it: its automated tour can't answer a
    /// system question, so it is first seen on a real iPhone.
    func askTracking() {
        #if targetEnvironment(simulator)
        listener?.trackingEnded()
        #else
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else {
            listener?.trackingEnded()
            return
        }
        if UIApplication.shared.applicationState == .active {
            requestTracking()
        } else {
            activeObserver = NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification,
                                                                    object: nil, queue: .main) { [weak self] _ in
                guard let self else { return }
                if let observer = self.activeObserver {
                    NotificationCenter.default.removeObserver(observer)
                }
                self.activeObserver = nil
                self.requestTracking()
            }
        }
        #endif
    }

    func startSdk() {
        MobileAds.shared.start { [weak self] _ in
            DispatchQueue.main.async { self?.listener?.sdkStarted() }
        }
    }

    func makeCard(listener: AdCardListener) -> AdCardBridge {
        GoogleAdCard(unit: nativeUnit, listener: listener)
    }

    func loadFullScreen() {
        InterstitialAd.load(with: interstitialUnit, request: Request()) { [weak self] ad, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.interstitial = ad
                ad?.fullScreenContentDelegate = self
                self.listener?.fullScreenLoaded(ready: ad != nil)
            }
        }
    }

    func showFullScreen() -> Bool {
        guard let ad = interstitial, let root = GoogleAds.topController() else { return false }
        interstitial = nil
        ad.present(from: root)
        return true
    }

    func dropFullScreen() {
        interstitial = nil
    }

    /// Settings › About › Ad privacy: Google's privacy options form, when Google requires one where the athlete is.
    func showPrivacyOptions() {
        ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters()) { [weak self] _ in
            Task { @MainActor in
                guard ConsentInformation.shared.privacyOptionsRequirementStatus == .required,
                      let root = GoogleAds.topController() else {
                    self?.listener?.privacyEnded(result: 1)
                    return
                }
                do {
                    try await ConsentForm.presentPrivacyOptionsForm(from: root)
                    self?.listener?.privacyEnded(result: 0)
                } catch {
                    self?.listener?.privacyEnded(result: 2)
                }
            }
        }
    }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        listener?.fullScreenClosed()
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        listener?.fullScreenClosed()
    }

    private func consentEnded() {
        let info = ConsentInformation.shared
        listener?.consentEnded(canRequestAds: info.canRequestAds, privacyOptions: info.privacyOptionsRequirementStatus == .required)
    }

    private func requestTracking() {
        ATTrackingManager.requestTrackingAuthorization { [weak self] _ in
            DispatchQueue.main.async { self?.listener?.trackingEnded() }
        }
    }

    private static func unit(_ key: String, fallback: String) -> String {
        let value = (Bundle.main.object(forInfoDictionaryKey: key) as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
        return value.isEmpty ? fallback : value
    }

    /// The view controller on top, which Google's forms and the full-screen ad are shown from.
    static func topController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap { $0.windows }.first { $0.isKeyWindow } ?? scenes.first?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

/// One native card (Android: NativeAdSlot.render): the theme's surface with 24 pt corners and a 1 pt outline, the "Ad"
/// label with the advertiser, a 40 pt icon beside the headline, the body, 160 pt of media, the button, and AdChoices
/// in the top right corner. It loads one ad when told and tells the app once it is in; until then it takes no room.
final class GoogleAdCard: NSObject, AdCardBridge, NativeAdLoaderDelegate {
    private let unit: String
    private let listener: AdCardListener
    private let container = UIView()
    private let adView = NativeAdView()
    private let badge = UIView()
    private let badgeLabel = UILabel()
    private let advertiser = UILabel()
    private let icon = UIImageView()
    private let headline = UILabel()
    private let body = UILabel()
    private let media = MediaView()
    private let action = UILabel()
    private var loader: AdLoader?

    var view: UIView { container }

    init(unit: String, listener: AdCardListener) {
        self.unit = unit
        self.listener = listener
        super.init()
        build()
    }

    func setLook(surface: Int64, text: Int64, muted: Int64, accent: Int64, onAccent: Int64, outline: Int64) {
        adView.backgroundColor = GoogleAdCard.color(surface)
        adView.layer.borderColor = GoogleAdCard.color(outline).cgColor
        badge.layer.borderColor = GoogleAdCard.color(outline).cgColor
        badgeLabel.textColor = GoogleAdCard.color(text)
        headline.textColor = GoogleAdCard.color(text)
        advertiser.textColor = GoogleAdCard.color(muted)
        body.textColor = GoogleAdCard.color(muted)
        action.backgroundColor = GoogleAdCard.color(accent)
        action.textColor = GoogleAdCard.color(onAccent)
    }

    func loadAd() {
        guard loader == nil else { return }
        let options = NativeAdViewAdOptions()
        options.preferredAdChoicesPosition = .topRightCorner
        let loader = AdLoader(adUnitID: unit, rootViewController: GoogleAds.topController(), adTypes: [.native], options: [options])
        loader.delegate = self
        self.loader = loader
        loader.load(Request())
    }

    func fitHeight(width: Double) -> Double {
        guard adView.nativeAd != nil else { return 0 }
        let size = container.systemLayoutSizeFitting(CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                                                     withHorizontalFittingPriority: .required,
                                                     verticalFittingPriority: .fittingSizeLevel)
        return Double(size.height.rounded(.up))
    }

    func destroy() {
        loader = nil
        adView.nativeAd = nil
    }

    func adLoader(_ adLoader: AdLoader, didReceive nativeAd: NativeAd) {
        headline.text = nativeAd.headline
        body.text = nativeAd.body
        body.isHidden = (nativeAd.body ?? "").isEmpty
        advertiser.text = nativeAd.advertiser
        advertiser.isHidden = (nativeAd.advertiser ?? "").isEmpty
        icon.image = nativeAd.icon?.image
        icon.isHidden = nativeAd.icon == nil
        media.mediaContent = nativeAd.mediaContent
        action.text = nativeAd.callToAction
        action.isHidden = (nativeAd.callToAction ?? "").isEmpty
        // Only Google's own views take the taps; the card's spare room doesn't.
        action.isUserInteractionEnabled = false
        adView.nativeAd = nativeAd
        listener.cardChanged(loaded: true)
    }

    func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: Error) {
        listener.cardChanged(loaded: false)
    }

    private func build() {
        adView.translatesAutoresizingMaskIntoConstraints = false
        adView.layer.cornerRadius = 24
        adView.layer.borderWidth = 1
        adView.clipsToBounds = false
        container.addSubview(adView)

        badgeLabel.text = "Ad"
        badgeLabel.font = .boldSystemFont(ofSize: 12)
        badgeLabel.translatesAutoresizingMaskIntoConstraints = false
        badge.layer.cornerRadius = 5
        badge.layer.borderWidth = 1
        badge.addSubview(badgeLabel)
        advertiser.font = .systemFont(ofSize: 12)
        advertiser.numberOfLines = 0
        let attribution = UIStackView(arrangedSubviews: [badge, advertiser])
        attribution.axis = .horizontal
        attribution.alignment = .center
        attribution.spacing = 8
        // Room in the top right corner for Google's AdChoices.
        let attributionRow = UIStackView(arrangedSubviews: [attribution, UIView()])
        attributionRow.axis = .horizontal
        attributionRow.isLayoutMarginsRelativeArrangement = true
        attributionRow.layoutMargins = UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 32)

        icon.contentMode = .scaleAspectFit
        headline.font = .boldSystemFont(ofSize: 18)
        headline.numberOfLines = 0
        let titleRow = UIStackView(arrangedSubviews: [icon, headline])
        titleRow.axis = .horizontal
        titleRow.alignment = .center
        titleRow.spacing = 10

        body.font = .systemFont(ofSize: 14)
        body.numberOfLines = 0
        action.font = .systemFont(ofSize: 14)
        action.textAlignment = .center
        action.layer.cornerRadius = 16
        action.clipsToBounds = true

        let column = UIStackView(arrangedSubviews: [attributionRow, titleRow, body, media, action])
        column.axis = .vertical
        column.alignment = .fill
        column.setCustomSpacing(10, after: attributionRow)
        column.setCustomSpacing(8, after: titleRow)
        column.setCustomSpacing(12, after: body)
        column.setCustomSpacing(12, after: media)
        column.translatesAutoresizingMaskIntoConstraints = false
        adView.addSubview(column)

        NSLayoutConstraint.activate([
            adView.topAnchor.constraint(equalTo: container.topAnchor),
            adView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            adView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            adView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
            column.topAnchor.constraint(equalTo: adView.topAnchor, constant: 12),
            column.leadingAnchor.constraint(equalTo: adView.leadingAnchor, constant: 16),
            column.trailingAnchor.constraint(equalTo: adView.trailingAnchor, constant: -16),
            column.bottomAnchor.constraint(equalTo: adView.bottomAnchor, constant: -16),
            badgeLabel.topAnchor.constraint(equalTo: badge.topAnchor, constant: 2),
            badgeLabel.bottomAnchor.constraint(equalTo: badge.bottomAnchor, constant: -2),
            badgeLabel.leadingAnchor.constraint(equalTo: badge.leadingAnchor, constant: 6),
            badgeLabel.trailingAnchor.constraint(equalTo: badge.trailingAnchor, constant: -6),
            badge.widthAnchor.constraint(greaterThanOrEqualToConstant: 24),
            badge.heightAnchor.constraint(greaterThanOrEqualToConstant: 20),
            icon.widthAnchor.constraint(equalToConstant: 40),
            icon.heightAnchor.constraint(equalToConstant: 40),
            media.heightAnchor.constraint(equalToConstant: 160),
            action.heightAnchor.constraint(greaterThanOrEqualToConstant: 48),
        ])

        // Google's outlets hold their views weakly: they are in the card above first.
        adView.headlineView = headline
        adView.bodyView = body
        adView.advertiserView = advertiser
        adView.iconView = icon
        adView.mediaView = media
        adView.callToActionView = action
    }

    private static func color(_ argb: Int64) -> UIColor {
        UIColor(red: CGFloat((argb >> 16) & 0xFF) / 255, green: CGFloat((argb >> 8) & 0xFF) / 255,
                blue: CGFloat(argb & 0xFF) / 255, alpha: CGFloat((argb >> 24) & 0xFF) / 255)
    }
}
