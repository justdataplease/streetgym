import Foundation
import StreetGymApp
import WidgetKit

/// The home-screen widget's snapshot, written where the widget reads it (Android: NextClassWidget.update). The app
/// (Kotlin, data/Widget.kt) decides what the widget says and when; this writes the files into the App Group's folder
/// and asks WidgetKit to draw the widget again. Handed over as `NativeWidget` (composeApp, iosMain, IosWidget.kt).
///
/// Without the App Group (a build without signing, as the Simulator's CI build, or before the owner sets the group up)
/// there is no folder: nothing is written and the widget shows its empty card.
final class WidgetBridge: NSObject, NativeWidget {
    /// The App Group the app and the widget share (project.yml, both targets' entitlements).
    static let group = "group.com.justdataplease.streetgym"
    static let snapshotFile = "snapshot.json"

    func save(snapshot: String, figure: Data?, fontFile: String?, font: Data?) {
        guard let folder = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: WidgetBridge.group) else { return }
        do {
            if let figure = figure {
                try figure.write(to: folder.appendingPathComponent("figure.png"), options: .atomic)
            }
            if let fontFile = fontFile, let font = font {
                // Only a plain file name: the face's own, as the app names it.
                try font.write(to: folder.appendingPathComponent((fontFile as NSString).lastPathComponent), options: .atomic)
            }
            try Data(snapshot.utf8).write(to: folder.appendingPathComponent(WidgetBridge.snapshotFile), options: .atomic)
        } catch {
            return
        }
        WidgetCenter.shared.reloadAllTimelines()
    }
}
