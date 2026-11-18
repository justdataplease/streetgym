import XCTest

/// Opens the app in the Simulator and keeps a picture of each screen it reaches. CI exports the pictures from the
/// test result, and they are reviewed like the Android emulator shots.
final class StreetGymTour: XCTestCase {
    override func setUp() {
        continueAfterFailure = true
    }

    func testTour() {
        let app = XCUIApplication()
        app.launch()

        // Today, once the program has been read from the app's files.
        let start = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", "start workout")).firstMatch
        let loaded = start.waitForExistence(timeout: 60)
        snap("01-today")
        XCTAssertTrue(loaded, "Today did not show its Start workout button")
        guard loaded else { return }

        // Start workout opens the class the sequence offers on its first set.
        start.tap()
        _ = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "set 1 of")).firstMatch.waitForExistence(timeout: 10)
        snap("02-workout")
    }

    private func snap(_ name: String) {
        let picture = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        picture.name = name
        picture.lifetime = .keepAlways
        add(picture)
    }
}
