import SwiftUI
import StreetGymApp

/// The iPhone app's entry point. Every screen is drawn by the Kotlin framework (composeApp); Swift holds only what
/// Apple requires to be native: this entry point now, purchases (StoreKit) and ads later.
@main
struct StreetGymIosApp: App {
    var body: some Scene {
        WindowGroup {
            // Edge to edge: the screens keep clear of the Dynamic Island and the home indicator themselves.
            ComposeScreen().ignoresSafeArea()
        }
    }
}

private struct ComposeScreen: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
