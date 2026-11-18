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

        // A fresh install opens on the strength test, once the program has been read from the app's files: its picture,
        // then the athlete skips it the way a user can, and Today shows the first class.
        let skip = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Skip and start at Level 1")).firstMatch
        if skip.waitForExistence(timeout: 60) {
            snap("01-strength-test")
            if !tapHittable(app, label: "Skip and start at Level 1") {
                app.swipeUp()
                _ = tapHittable(app, label: "Skip and start at Level 1")
            }
        }

        // Today, with the park on its brand card.
        let start = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", "start workout")).firstMatch
        let loaded = start.waitForExistence(timeout: 60)
        snap("02-today")
        XCTAssertTrue(loaded, "Today did not show its Start workout button")
        guard loaded else { return }

        // Start workout opens the class the sequence offers on its first set, with its moving line man.
        if !start.isHittable { app.swipeUp() }
        start.tap()
        _ = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "set 1 of")).firstMatch.waitForExistence(timeout: 10)
        snap("03-workout")

        // The title bar's list icon shows the same class as a list of sets with their ticks.
        let list = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Switch to list view")).firstMatch
        guard list.waitForExistence(timeout: 10) else {
            XCTFail("The workout has no Switch to list view button")
            return
        }
        list.tap()
        _ = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "ready for the next set")).firstMatch.waitForExistence(timeout: 10)
        snap("04-list")

        // The list keeps the tab bar: Training shows the ten levels, and the first opens its classes.
        guard tapHittable(app, label: "Training") else { return }
        let level = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "of 100 complete")).firstMatch
        guard level.waitForExistence(timeout: 10) else { return }
        snap("05-training")
        level.tap()
        _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "level 1 workouts")).firstMatch.waitForExistence(timeout: 10)
        snap("06-level")

        // Training › My workouts: the saved and own days, with Create a workout.
        guard tapHittable(app, label: "Training") else { return }
        if tapHittable(app, label: "My workouts") {
            _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "saved and custom days")).firstMatch.waitForExistence(timeout: 10)
            snap("07-my-workouts")
        }

        // Progress opens on Skills: the points and the ladders of medals.
        guard tapHittable(app, label: "Progress") else { return }
        _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "points. Double-tap")).firstMatch.waitForExistence(timeout: 10)
        snap("08-skills")

        // History: this month's calendar.
        guard tapHittable(app, label: "History") else { return }
        _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "this month")).firstMatch.waitForExistence(timeout: 10)
        snap("09-history")

        // Settings: the themes, the workout's choices, the skill and equipment switches.
        guard tapHittable(app, label: "Settings") else { return }
        _ = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Concrete & Tape")).firstMatch.waitForExistence(timeout: 10)
        snap("10-settings")
    }

    /// Taps the first element labelled [label] that can be tapped: a tab's label can also be a title or a word on screen.
    private func tapHittable(_ app: XCUIApplication, label: String) -> Bool {
        // Whatever the case: the tab bar writes its words in capitals ("TRAINING").
        let matches = app.descendants(matching: .any).matching(NSPredicate(format: "label ==[c] %@", label))
        guard matches.firstMatch.waitForExistence(timeout: 10) else { return false }
        for index in 0..<matches.count {
            let element = matches.element(boundBy: index)
            if element.isHittable {
                element.tap()
                return true
            }
        }
        return false
    }

    private func snap(_ name: String) {
        let picture = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        picture.name = name
        picture.lifetime = .keepAlways
        add(picture)
    }
}
