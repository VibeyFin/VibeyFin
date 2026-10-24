import XCTest

/// Smoke test for the first-run flow: launch, add a server, pick "Other user", sign in, land on the tabs.
///
/// It expects a fresh install (no saved servers) and the local test server from `docs/test-server.md`.
/// `scripts/ios-smoke.sh` takes care of both. Override the defaults with `TEST_RUNNER_`-prefixed
/// variables, for example `TEST_RUNNER_VIBEYFIN_TEST_SERVER=192.168.1.20:8096`.
///
/// Labels are matched case-insensitively because iOS shows button and title text in title case.
final class OnboardingFlowUITests: XCTestCase {
    private var app: XCUIApplication!

    private var server: String { Self.setting("VIBEYFIN_TEST_SERVER", default: "localhost:8196") }
    private var username: String { Self.setting("VIBEYFIN_TEST_USER", default: "admin") }
    // Throwaway password of the disposable local Jellyfin in docs/test-server.md.
    private var password: String { Self.setting("VIBEYFIN_TEST_PASSWORD", default: "vibeyfin-test") }

    private static func setting(_ key: String, default value: String) -> String {
        ProcessInfo.processInfo.environment[key] ?? value
    }

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        // The first launch asks for local network access (server discovery). Allow it whenever it shows.
        addUIInterruptionMonitor(withDescription: "Local network permission") { alert in
            for label in ["Allow", "OK"] where alert.buttons[label].exists {
                alert.buttons[label].tap()
                return true
            }
            return false
        }
        app.launch()
    }

    func testOnboardingFlow() {
        step("Servers screen shows the app name and the main action") {
            XCTAssertTrue(text("VibeyFin").waitForExistence(timeout: 15), "App name is missing")
            let addServer = button("Add server")
            XCTAssertTrue(addServer.waitForExistence(timeout: 5), "Add server button is missing")
            XCTAssertTrue(addServer.isHittable, "Add server button can't be tapped")
        }

        step("Add a server by address") {
            button("Add server").tap()
            XCTAssertTrue(text("Add a server").waitForExistence(timeout: 5), "Add a server title is missing")
            XCTAssertTrue(button("Connect").exists, "Connect button is missing")

            XCTAssertTrue(text("Server address").exists, "Server address label is missing")
            // The field has no accessibility label of its own, and it is the only one on this screen.
            let address = app.textFields.firstMatch
            XCTAssertTrue(address.waitForExistence(timeout: 5), "Server address field is missing")
            address.tap()
            address.typeText(server)
            button("Connect").tap()

            XCTAssertTrue(text("Found").waitForExistence(timeout: 30), "Server wasn't found at \(server)")
            // The same action is labelled "Add server" once the server answered.
            let confirm = button("Add server")
            XCTAssertTrue(confirm.waitForExistence(timeout: 5), "Add server confirm button is missing")
            confirm.tap()
        }

        step("Saved server opens Who's watching") {
            // The server card is the only button that shows the Jellyfin version.
            let card = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", "Jellyfin")).firstMatch
            XCTAssertTrue(card.waitForExistence(timeout: 10), "Saved server isn't listed")
            card.tap()
            XCTAssertTrue(
                app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "watching")).firstMatch
                    .waitForExistence(timeout: 10),
                "Who's watching isn't showing"
            )
            XCTAssertTrue(button("Quick Connect", contains: true).exists, "Quick Connect tile is missing")
            XCTAssertTrue(button("Other user", contains: true).exists, "Other user tile is missing")
        }

        step("Sign in as another user") {
            button("Other user", contains: true).tap()
            XCTAssertTrue(text("Sign in as another user").waitForExistence(timeout: 5), "Sign in sheet is missing")

            let user = app.textFields.firstMatch
            XCTAssertTrue(text("Username").exists, "Username label is missing")
            XCTAssertTrue(user.waitForExistence(timeout: 5), "Username field is missing")
            user.tap()
            user.typeText(username)

            let secret = app.secureTextFields.firstMatch
            XCTAssertTrue(secret.exists, "Password field is missing")
            secret.tap()
            secret.typeText(password)

            button("Sign in").tap()
        }

        step("Signed in: Home, Library and Downloads tabs show") {
            for tab in ["Home", "Library", "Downloads"] {
                // The bar is at the bottom on iPhone and at the top on iPad, where it may not be a `tabBar`.
                let match = NSPredicate(format: "label ==[c] %@", tab)
                let item = app.tabBars.buttons.matching(match).firstMatch.exists
                    ? app.tabBars.buttons.matching(match).firstMatch
                    : app.buttons.matching(match).firstMatch
                XCTAssertTrue(item.waitForExistence(timeout: 20), "\(tab) tab is missing after sign-in")
            }
        }

        step("Settings pages") {
            // Settings is in the profile menu on Home; its button is labelled with the user's name.
            let profile = button(username)
            XCTAssertTrue(profile.waitForExistence(timeout: 10), "Profile menu is missing")
            profile.tap()
            let settings = button("Settings")
            // Home is still loading right after sign-in and can swallow the first tap: open the menu once more.
            if !settings.waitForExistence(timeout: 5) { profile.tap() }
            XCTAssertTrue(settings.waitForExistence(timeout: 5), "Settings menu item is missing")
            settings.tap()

            // The root page links to the pages; a link's button is labelled with its title and its value.
            let quality = button("Quality", contains: true)
            XCTAssertTrue(quality.waitForExistence(timeout: 10), "Quality link is missing")
            quality.tap()
            let p1080 = button("1080p", contains: true)
            XCTAssertTrue(p1080.waitForExistence(timeout: 5), "1080p choice is missing")
            p1080.tap()
            goBack()

            // The root shows the new choice as the link's value.
            let shows1080 = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@ AND label CONTAINS %@", "Quality", "1080p")).firstMatch
            XCTAssertTrue(shows1080.waitForExistence(timeout: 5), "Quality doesn't show 1080p")

            let skipping = button("Skipping", contains: true)
            XCTAssertTrue(skipping.waitForExistence(timeout: 5), "Skipping link is missing")
            skipping.tap()

            // A menu picker's button is labelled with its title and the chosen value.
            let skipBack = button("Skip Back", contains: true)
            XCTAssertTrue(skipBack.waitForExistence(timeout: 10), "Skip Back row is missing")
            XCTAssertTrue(button("Skip Forward", contains: true).exists, "Skip Forward row is missing")
            skipBack.tap()
            let fifteen = button("15 s")
            XCTAssertTrue(fifteen.waitForExistence(timeout: 5), "15 s choice is missing")
            fifteen.tap()
            // The menu is still closing: wait for the row to show 15 rather than reading its label at once.
            let shows15 = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@ AND label CONTAINS %@", "Skip Back", "15")).firstMatch
            XCTAssertTrue(shows15.waitForExistence(timeout: 5), "Skip Back doesn't show 15")

            // Buffer ahead is a menu like Skip Back. It defaults to 20 s.
            let bufferAhead = button("Buffer Ahead", contains: true)
            XCTAssertTrue(bufferAhead.waitForExistence(timeout: 5), "Buffer ahead row is missing")
            let shows20 = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@ AND label CONTAINS %@", "Buffer Ahead", "20")).firstMatch
            XCTAssertTrue(shows20.waitForExistence(timeout: 5), "Buffer ahead doesn't show its 20 s default")
            bufferAhead.tap()
            let thirty = button("30 s")
            XCTAssertTrue(thirty.waitForExistence(timeout: 5), "30 s choice is missing")
            thirty.tap()
            let shows30 = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@ AND label CONTAINS %@", "Buffer Ahead", "30")).firstMatch
            XCTAssertTrue(shows30.waitForExistence(timeout: 5), "Buffer ahead doesn't show 30")
        }
    }

    /// A series lists its episodes down the page on a phone and sideways on a tablet, and tapping an episode's words opens its page.
    /// Runs after the onboarding test (alphabetical order), which leaves a signed-in profile behind.
    func testSeriesEpisodes() throws {
        let match = NSPredicate(format: "label ==[c] %@", "Search")
        let tab = app.tabBars.buttons.matching(match).firstMatch
        let search = tab.waitForExistence(timeout: 20) ? tab : app.buttons.matching(match).firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 20), "Search tab is missing")

        step("Search finds Saga Show") {
            search.tap()
            let field = app.searchFields.firstMatch
            XCTAssertTrue(field.waitForExistence(timeout: 10), "Search field is missing")
            field.tap()
            field.typeText("Saga Show")
            let result = button("Saga Show", contains: true)
            XCTAssertTrue(result.waitForExistence(timeout: 20), "Saga Show is missing from the results")
            result.tap()
        }

        step("Episodes are laid out for this width") {
            let first = episode("S1 E1")
            let second = episode("S1 E2")
            XCTAssertTrue(first.waitForExistence(timeout: 30), "Episode 1 is missing")
            if UIDevice.current.userInterfaceIdiom == .phone {
                // The rows stack, so the second sits below the first at the same left edge.
                for _ in 0..<4 where !second.exists || !second.isHittable { app.swipeUp() }
                XCTAssertTrue(second.waitForExistence(timeout: 10), "Episode 2 is missing")
                let dump = app.buttons.allElementsBoundByIndex.map { "\($0.label.prefix(40)) @\($0.frame)" }.joined(separator: "\n")
                XCTAssertEqual(first.frame.minX, second.frame.minX, accuracy: 1, "Phone episodes are not in one column:\n\(dump)")
                XCTAssertGreaterThan(second.frame.minY, first.frame.minY, "Episode 2 is not below episode 1")
            } else {
                XCTAssertTrue(second.waitForExistence(timeout: 10), "Episode 2 is missing")
                XCTAssertGreaterThan(second.frame.minX, first.frame.minX, "Tablet episodes are not side by side")
            }
        }

        step("Tapping an episode's words opens its page") {
            let first = episode("S1 E1")
            XCTAssertTrue(first.exists)
            sleep(1) // the page is still settling from the swipe
            first.tap()
            if !button("Play").waitForExistence(timeout: 5) { first.tap() }
            // An episode's page says plain "Play"; the series page below it says "Resume S1 E2 from ...".
            XCTAssertTrue(button("Play").waitForExistence(timeout: 20), "The episode page did not open")
        }
    }

    /// The player's controls stay up while the speed menu is open, and hide a quiet moment after it closes.
    /// Runs after the onboarding test (alphabetical order), which leaves a signed-in profile behind.
    func testPlayerControlsStayWhileMenuOpen() throws {
        let match = NSPredicate(format: "label ==[c] %@", "Search")
        let tab = app.tabBars.buttons.matching(match).firstMatch
        let search = tab.waitForExistence(timeout: 20) ? tab : app.buttons.matching(match).firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 20), "Search tab is missing")
        search.tap()
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10), "Search field is missing")
        field.tap()
        field.typeText("Test Show")
        let result = button("Test Show", contains: true)
        XCTAssertTrue(result.waitForExistence(timeout: 20), "Test Show is missing from the results")
        result.tap()
        let play = app.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Play")).firstMatch
        XCTAssertTrue(play.waitForExistence(timeout: 20), "Play is missing")
        play.tap()

        // The speed button is labelled "Speed, 1x"; it only exists while the controls show.
        let speed = button("Speed", contains: true)
        XCTAssertTrue(speed.waitForExistence(timeout: 30), "The player controls didn't show")

        speed.tap()
        let choice = app.buttons.matching(NSPredicate(format: "label ==[c] %@", "1.5x")).firstMatch
        XCTAssertTrue(choice.waitForExistence(timeout: 5), "The speed menu didn't open")
        // Well past the 3 s the controls normally wait.
        sleep(6)
        XCTAssertTrue(choice.exists, "The speed menu closed by itself")
        XCTAssertTrue(speed.exists, "The controls hid while the menu was open")

        choice.tap()
        sleep(1)
        XCTAssertTrue(button("Speed", contains: true).exists, "The controls hid at once when the menu closed")
        // And the quiet moment starts again after it closed.
        let gone = NSPredicate(format: "exists == false")
        expectation(for: gone, evaluatedWith: button("Speed", contains: true))
        waitForExpectations(timeout: 8)
    }

    // MARK: - Helpers

    /// Runs `body` as a named step and attaches a screenshot at the end, so a report shows each screen.
    private func step(_ name: String, _ body: () -> Void) {
        XCTContext.runActivity(named: name) { activity in
            body()
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.name = name
            shot.lifetime = .keepAlways
            activity.add(shot)
        }
    }

    /// The navigation bar's back chevron (its label is the previous page's title, or empty).
    private func goBack() {
        let back = app.navigationBars.buttons.element(boundBy: 0)
        XCTAssertTrue(back.waitForExistence(timeout: 5), "Back button is missing")
        back.tap()
    }

    private func button(_ label: String, contains: Bool = false) -> XCUIElement {
        let format = contains ? "label CONTAINS[c] %@" : "label ==[c] %@"
        return app.buttons.matching(NSPredicate(format: format, label)).firstMatch
    }

    /// An episode's words (its tag first): not the hero's "Play S1 E1", which also contains the tag.
    private func episode(_ tag: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] %@", tag)).firstMatch
    }

    private func text(_ label: String) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label ==[c] %@", label)).firstMatch
    }
}
