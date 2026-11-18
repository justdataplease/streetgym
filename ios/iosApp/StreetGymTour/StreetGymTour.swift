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

        // Start workout opens the class the sequence offers on its first set, with its moving line man.
        start.tap()
        _ = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "set 1 of")).firstMatch.waitForExistence(timeout: 10)
        snap("02-workout")

        // The title bar's list icon shows the same class as a list of sets with their ticks.
        let list = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Switch to list view")).firstMatch
        guard list.waitForExistence(timeout: 10) else {
            XCTFail("The workout has no Switch to list view button")
            return
        }
        list.tap()
        _ = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "ready for the next set")).firstMatch.waitForExistence(timeout: 10)
        snap("03-list")

        // The list keeps the tab bar: Training shows the ten levels, and the first opens its classes.
        let training = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Training")).firstMatch
        guard training.waitForExistence(timeout: 10) else { return }
        training.tap()
        let level = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "of 100 complete")).firstMatch
        guard level.waitForExistence(timeout: 10) else { return }
        snap("04-training")
        level.tap()
        _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "level 1 workouts")).firstMatch.waitForExistence(timeout: 10)
        snap("05-level")
    }

    private func snap(_ name: String) {
        let picture = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        picture.name = name
        picture.lifetime = .keepAlways
        add(picture)
    }
}
