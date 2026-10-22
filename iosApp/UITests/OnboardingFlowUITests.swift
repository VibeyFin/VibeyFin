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
                let item = app.tabBars.buttons.matching(NSPredicate(format: "label ==[c] %@", tab)).firstMatch
                XCTAssertTrue(item.waitForExistence(timeout: 20), "\(tab) tab is missing after sign-in")
            }
        }
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

    private func button(_ label: String, contains: Bool = false) -> XCUIElement {
        let format = contains ? "label CONTAINS[c] %@" : "label ==[c] %@"
        return app.buttons.matching(NSPredicate(format: format, label)).firstMatch
    }

    private func text(_ label: String) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label ==[c] %@", label)).firstMatch
    }
}
