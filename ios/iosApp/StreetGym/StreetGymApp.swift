import SwiftUI
import StreetGymApp

/// The iPhone app's entry point. Every screen is drawn by the Kotlin framework (composeApp); Swift holds only what
/// Apple requires to be native: this entry point, the Parks map (MapLibre, ParksMapLibre.swift), and later purchases
/// (StoreKit) and ads.
@main
struct StreetGymIosApp: App {
    init() {
        // The Timer's alerts and their Stop, ready before the app has finished starting: a tap on an alert that opens
        // the app is heard too.
        MainViewControllerKt.startAlerts()
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
        MainViewControllerKt.MainViewController(maps: MapLibreParks(), restored: screens, saver: BindingSaver(binding: $screens))
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
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
