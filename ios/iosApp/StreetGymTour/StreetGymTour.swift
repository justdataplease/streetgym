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
        // The park takes the top of the screen, so Start workout is further down: Today is there when its brand card
        // is (the Mac run of 18 Nov 2026 failed here looking for the button without scrolling).
        let today = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "we choose the workout")).firstMatch
        let loaded = today.waitForExistence(timeout: 60)
        snap("02-today")
        XCTAssertTrue(loaded, "Today did not show its brand card")
        guard loaded else { return }

        // Start workout opens the class the sequence offers on its first set, with its moving line man.
        let start = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "start workout")).firstMatch
        var swipes = 0
        while !(start.exists && start.isHittable) && swipes < 4 {
            app.swipeUp()
            swipes += 1
        }
        guard start.exists, start.isHittable else { return }
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
        dump("05-training", app)

        // Which screen stops the pictures? (18 Nov 2026: after Training the app went on but the screen stayed.)
        // Settings draws no figure; a level's page draws a moving tile per class.
        if tapHittable(app, label: "Settings") { snap("05b-settings-before-level") }
        _ = tapHittable(app, label: "Training")
        snap("05c-training-again")
        level.tap()
        _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "level 1 workouts")).firstMatch.waitForExistence(timeout: 10)
        snap("06-level")
        dump("06-level", app)

        // Training › My workouts: the saved and own days, with Create a workout.
        guard tapHittable(app, label: "Training") else { return }
        if tapHittable(app, label: "My workouts") {
            _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "saved and custom days")).firstMatch.waitForExistence(timeout: 10)
            snap("07-my-workouts")
        }

        // Training › Library: the ready workouts with their filters. (Parks is left out: its map would ask for the
        // location, a system dialog, and needs the network.)
        if tapHittable(app, label: "Library") {
            _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "ready workouts for days off")).firstMatch.waitForExistence(timeout: 10)
            snap("08-library")
        }

        // Progress opens on Skills: the points and the ladders of medals.
        guard tapHittable(app, label: "Progress") else { return }
        _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "points. Double-tap")).firstMatch.waitForExistence(timeout: 10)
        snap("09-skills")
        dump("09-skills", app)

        // History: this month's calendar.
        guard tapHittable(app, label: "History") else { return }
        _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "this month")).firstMatch.waitForExistence(timeout: 10)
        snap("10-history")

        // The Timer tab: the timer's minute and its presets. Nothing here asks for alerts until Enable alerts is tapped.
        if tapHittable(app, label: "Timer") {
            _ = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS[c] %@", "tap the time to change it")).firstMatch.waitForExistence(timeout: 10)
            snap("11-timer")
        }

        // Settings: the themes, the workout's choices, the skill and equipment switches.
        guard tapHittable(app, label: "Settings") else { return }
        _ = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Concrete & Tape")).firstMatch.waitForExistence(timeout: 10)
        snap("12-settings")
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

    /// Every label the app says is on screen goes into the log, between TOUR-SCREEN lines: the Mac run of 18 Nov 2026
    /// photographed Training six times while these steps reported success.
    private func dump(_ name: String, _ app: XCUIApplication) {
        print("TOUR-SCREEN-BEGIN " + name)
        print(app.debugDescription)
        print("TOUR-SCREEN-END " + name)
    }

    private func snap(_ name: String) {
        // A moment for the frame to be drawn before the picture.
        Thread.sleep(forTimeInterval: 1.5)
        let picture = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        picture.name = name
        picture.lifetime = .keepAlways
        add(picture)
    }
}
