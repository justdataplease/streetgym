import SwiftUI
import StreetGymApp

/// The iPhone app's entry point. Every screen is drawn by the Kotlin framework (composeApp); Swift holds only what
/// Apple requires to be native: this entry point, the Parks map (MapLibre, ParksMapLibre.swift), Plus and Pro
/// (StoreKit 2, StoreKitStore.swift) and Free's ads (Google's, GoogleAds.swift).
@main
struct StreetGymIosApp: App {
    /// One store and one set of ads for the app's life: the store hears the App Store's transactions from its start.
    static let store = StoreKitStore()
    static let ads = GoogleAds()

    init() {
        // The Timer's alerts and their Stop, ready before the app has finished starting: a tap on an alert that opens
        // the app is heard too.
        // (A Kotlin function outside a class is named after its file: IosTiming.kt gives IosTimingKt.)
        IosTimingKt.startAlerts()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

/// Where the app is, kept by iOS with the window: it comes back after the phone closed the app in the background, and
/// is gone after the athlete closes it, as Android's saved state.
private struct RootView: View {
    @SceneStorage("streetgym.screens") private var screens = ""

    var body: some View {
        // Edge to edge: the screens keep clear of the Dynamic Island and the home indicator themselves.
        ComposeScreen(screens: $screens).ignoresSafeArea()
    }
}

private struct ComposeScreen: UIViewControllerRepresentable {
    @Binding var screens: String

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(maps: MapLibreParks(), restored: screens, saver: BindingSaver(binding: $screens),
                                                store: StreetGymIosApp.store, ads: StreetGymIosApp.ads, debug: ComposeScreen.debug)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    /// A debug build: its developer settings offer Simulate tier (Android: BuildConfig.DEBUG).
    #if DEBUG
    static let debug = true
    #else
    static let debug = false
    #endif
}

/// Hands Kotlin's text for where the app is to the scene storage.
private final class BindingSaver: NSObject, SceneSaver {
    private let binding: Binding<String>

    init(binding: Binding<String>) {
        self.binding = binding
    }

    func save(state: String) {
        binding.wrappedValue = state
    }
}
