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

    private static var finishedDownloads = 0

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

            // On an iPad, Settings is two panes: the sidebar lists the pages and the first one with something to set is open.
            if UIDevice.current.userInterfaceIdiom == .pad {
                // Only the detail column has a nav-bar title of its own; the sidebar's row labels also hold values.
                let title = { (name: String) in self.app.navigationBars.staticTexts.matching(NSPredicate(format: "label ==[c] %@", name)).firstMatch }
                XCTAssertTrue(title("Quality").waitForExistence(timeout: 10), "Quality is not open in the detail column")
                // The sidebar rows are cells; fall back to buttons where the list exposes them that way.
                let sidebarRow = { (name: String) in
                    let cell = self.app.cells.matching(NSPredicate(format: "label BEGINSWITH[c] %@", name)).firstMatch
                    return cell.exists ? cell : self.app.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] %@", name)).firstMatch
                }
                let subtitles = sidebarRow("Subtitles")
                XCTAssertTrue(subtitles.waitForExistence(timeout: 5), "Subtitles is missing from the sidebar")
                subtitles.tap()
                XCTAssertTrue(title("Subtitles").waitForExistence(timeout: 5), "Subtitles did not open in the detail column")
                XCTAssertFalse(title("Quality").exists, "Quality is still showing next to Subtitles")
                // The sidebar's back button leaves Settings for Home.
                let back = app.buttons["settings.back"]
                XCTAssertTrue(back.waitForExistence(timeout: 5), "Back button is missing from the sidebar")
                back.tap()
                XCTAssertTrue(button(username).waitForExistence(timeout: 10), "Home did not come back")
            } else {
                // The root page links to the pages; a link's button is labelled with its title and its value.
                let quality = button("Quality", contains: true)
                XCTAssertTrue(quality.waitForExistence(timeout: 10), "Quality link is missing")
                quality.tap()
                // The whole option label: "Quality, 1080p ..." on the root is longer, so only the option matches.
                let p1080 = button("1080p \u{00B7} 10 Mbps")
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
                // Back to Home: Skipping, then the root.
                goBack()
                goBack()
            }
        }

        // Switching notifications on asks iOS for permission; the system alert is allowed. Ends on Home with the setting on.
        step("Notifications") {
            let isPad = UIDevice.current.userInterfaceIdiom == .pad
            addUIInterruptionMonitor(withDescription: "Notification permission") { alert in
                guard alert.buttons["Allow"].exists else { return false }
                alert.buttons["Allow"].tap()
                return true
            }

            let profile = button(username)
            XCTAssertTrue(profile.waitForExistence(timeout: 10), "Profile menu is missing")
            profile.tap()
            let settings = button("Settings")
            if !settings.waitForExistence(timeout: 5) { profile.tap() }
            XCTAssertTrue(settings.waitForExistence(timeout: 5), "Settings menu item is missing")
            settings.tap()

            // The sidebar row is a cell on an iPad, the root's link a button on a phone (the section header above it is only text).
            let cell = app.cells.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Notifications")).firstMatch
            let link = cell.waitForExistence(timeout: 5) ? cell : button("Notifications", contains: true)
            XCTAssertTrue(link.waitForExistence(timeout: 10), "Notifications link is missing")
            link.tap()

            let toggle = app.switches.matching(NSPredicate(format: "label CONTAINS[c] %@", "Notify me")).firstMatch
            XCTAssertTrue(toggle.waitForExistence(timeout: 10), "Notifications switch is missing")
            XCTAssertEqual(toggle.value as? String, "0", "Notifications are on before they were switched on")
            toggle.tap()
            // The system alert belongs to SpringBoard. Tap its Allow directly; the monitor above is only a fallback, as it is unreliable
            // for SpringBoard alerts. No alert means permission was decided before (a reused simulator).
            let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            let allow = springboard.alerts.buttons.matching(NSPredicate(format: "label ==[c] %@", "Allow")).firstMatch
            if allow.waitForExistence(timeout: 5) {
                allow.tap()
            } else {
                // Gives the monitor its chance: it only runs on the next interaction with the app.
                app.navigationBars.firstMatch.tap()
            }
            // Still on once the alert is answered: a refusal would have switched it back off.
            XCTAssertFalse(allow.waitForExistence(timeout: 2), "The notification permission alert is still up")
            let on = NSPredicate(format: "value == '1'")
            expectation(for: on, evaluatedWith: toggle)
            waitForExpectations(timeout: 10)
            sleep(1)
            XCTAssertEqual(toggle.value as? String, "1", "Notifications switched back off: permission was refused")
            // Which shows to notify about appears once the setting is on.
            XCTAssertTrue(button("Which shows", contains: true).waitForExistence(timeout: 5), "Which shows row is missing")

            if isPad {
                let back = app.buttons["settings.back"]
                XCTAssertTrue(back.waitForExistence(timeout: 5), "Back button is missing from the sidebar")
                back.tap()
            } else {
                goBack()
                goBack()
            }
            XCTAssertTrue(button(username).waitForExistence(timeout: 10), "Home did not come back")
        }

        // The kids user (scripts/test-server/seed-userdata.sh) sets a PIN; Settings then asks for it. Ends signed in as the first user again.
        step("Kids PIN") {
            let isPad = UIDevice.current.userInterfaceIdiom == .pad
            let kidsUser = "kids", kidsPassword = "vibeyfin-kids", pin = "1234"

            // Opens a profile menu item on Home.
            func menu(_ name: String, as user: String) {
                let profile = button(user)
                XCTAssertTrue(profile.waitForExistence(timeout: 10), "Profile menu is missing for \(user)")
                profile.tap()
                let item = button(name)
                if !item.waitForExistence(timeout: 5) { profile.tap() }
                XCTAssertTrue(item.waitForExistence(timeout: 5), "\(name) menu item is missing")
                item.tap()
            }
            func enter(_ digits: String) {
                for digit in digits {
                    let key = app.buttons["pin.key.\(digit)"]
                    XCTAssertTrue(key.waitForExistence(timeout: 5), "PIN key \(digit) is missing")
                    key.tap()
                }
            }
            func leaveSettings() {
                if isPad {
                    let back = app.buttons["settings.back"]
                    XCTAssertTrue(back.waitForExistence(timeout: 5), "Back button is missing from the sidebar")
                    back.tap()
                } else {
                    goBack()
                    goBack()
                }
            }
            func openKidsAndPin() {
                // The sidebar row is a cell on an iPad, the root's link a button on a phone.
                let cell = app.cells.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Kids profile")).firstMatch
                let link = cell.waitForExistence(timeout: 5) ? cell : button("Kids profile", contains: true)
                XCTAssertTrue(link.waitForExistence(timeout: 10), "Kids profile link is missing")
                link.tap()
            }

            // Sign in as the kids user.
            menu("Switch profile", as: username)
            XCTAssertTrue(button("Other user", contains: true).waitForExistence(timeout: 10), "Who's watching isn't showing")
            button("Other user", contains: true).tap()
            let user = app.textFields.firstMatch
            XCTAssertTrue(user.waitForExistence(timeout: 5), "Username field is missing")
            user.tap()
            user.typeText(kidsUser)
            let secret = app.secureTextFields.firstMatch
            secret.tap()
            secret.typeText(kidsPassword)
            button("Sign in").tap()

            // Set a PIN: typed twice.
            menu("Settings", as: kidsUser)
            openKidsAndPin()
            let setPin = button("Set PIN", contains: true)
            XCTAssertTrue(setPin.waitForExistence(timeout: 10), "Set PIN is missing")
            setPin.tap()
            XCTAssertTrue(text("Choose a PIN").waitForExistence(timeout: 5), "The PIN sheet didn't ask for a new PIN")
            enter(pin)
            XCTAssertTrue(text("Enter the PIN again").waitForExistence(timeout: 5), "The PIN sheet didn't ask for the PIN again")
            enter(pin)
            XCTAssertTrue(button("Remove PIN", contains: true).waitForExistence(timeout: 10), "The PIN wasn't set")
            leaveSettings()

            // A wrong PIN doesn't open Settings; the right one does.
            menu("Settings", as: kidsUser)
            XCTAssertTrue(text("Enter PIN").waitForExistence(timeout: 10), "Settings didn't ask for the PIN")
            enter("0000")
            let wrong = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "PIN is wrong")).firstMatch
            XCTAssertTrue(wrong.waitForExistence(timeout: 5), "A wrong PIN wasn't refused")
            XCTAssertFalse(button("Kids profile", contains: true).exists, "Settings opened on a wrong PIN")
            enter(pin)
            XCTAssertTrue(
                app.cells.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Kids profile")).firstMatch.waitForExistence(timeout: 10)
                    || button("Kids profile", contains: true).waitForExistence(timeout: 1),
                "Settings didn't open on the right PIN"
            )
            if isPad { leaveSettings() } else { goBack() }

            // Back to the first user: switching away from the kids profile asks for the PIN too.
            menu("Switch profile", as: kidsUser)
            XCTAssertTrue(text("Enter PIN").waitForExistence(timeout: 10), "Switch profile didn't ask for the PIN")
            enter(pin)
            // The tile reads "A, admin": the avatar's initial, then the name.
            let first = button(username, contains: true)
            XCTAssertTrue(first.waitForExistence(timeout: 10), "The first user's tile is missing")
            first.tap()
            // Home-only: the profile's tile on Who's watching is a button with the same label.
            let library = NSPredicate(format: "label ==[c] %@", "Library")
            let libraryTab = app.tabBars.buttons.matching(library).firstMatch.exists
                ? app.tabBars.buttons.matching(library).firstMatch
                : app.buttons.matching(library).firstMatch
            XCTAssertTrue(libraryTab.waitForExistence(timeout: 20), "Home didn't open for the first user")
        }

        step("Downloads tab opens on its empty state") {
            let match = NSPredicate(format: "label ==[c] %@", "Downloads")
            let tab = app.tabBars.buttons.matching(match).firstMatch.exists
                ? app.tabBars.buttons.matching(match).firstMatch
                : app.buttons.matching(match).firstMatch
            tab.tap()
            let empty = app.staticTexts.matching(NSPredicate(format: "label ==[c] %@", "Nothing downloaded yet")).firstMatch
            XCTAssertTrue(empty.waitForExistence(timeout: 10), "Downloads shows no empty state on a fresh install")
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
    /// An MKV with HEVC video: AVPlayer cannot open the container, so the download is remuxed to MP4 on the device.
    func testZDownloadMovie() throws {
        try downloadAndExpectFinished(search: "HDR", result: "HDR")
    }

    /// An MKV with DTS audio: the audio is converted to AAC on the device.
    func testZDownloadDtsAudio() throws {
        try downloadAndExpectFinished(search: "DTS Audio", result: "DTS Audio")
    }

    private func downloadAndExpectFinished(search query: String, result title: String) throws {
        let search = tabButton("Search")
        XCTAssertTrue(search.waitForExistence(timeout: 20), "Search tab is missing")
        search.tap()
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10), "Search field is missing")
        field.tap()
        field.typeText(query)
        let found = button(title, contains: true)
        XCTAssertTrue(found.waitForExistence(timeout: 20), "\(title) is missing from the results")
        found.tap()
        // "Download " with its space: the tab bar's "Downloads" also begins with the word.
        let download = app.buttons.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Download ")).firstMatch
        XCTAssertTrue(download.waitForExistence(timeout: 20), "Download button is missing")
        download.tap()
        tabButton("Downloads").tap()
        let row = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", title)).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 20), "Download row is missing")
        // The tests share one install, so an earlier test's finished download is on this list too: wait for one more.
        let finished = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH[c] %@", "Downloaded"))
        let expected = Self.finishedDownloads + 1
        let reached = NSPredicate(format: "count >= %d", expected)
        expectation(for: reached, evaluatedWith: finished)
        waitForExpectations(timeout: 120) { error in
            if error != nil { XCTFail("The download did not finish: \(self.visibleTexts())") }
        }
        Self.finishedDownloads = expected
    }

    /// The converted MP4 from `testZDownloadMovie` opens in the player, and plays: its position moves.
    func testZPlayDownloadedFile() throws {
        tabButton("Downloads").tap()
        let row = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "HDR")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 20), "The downloaded movie is not on the Downloads tab")
        row.tap()

        let position = app.descendants(matching: .any).matching(NSPredicate(format: "label ==[c] %@", "Playback position")).firstMatch
        if !position.waitForExistence(timeout: 30) {
            app.tap() // the controls hide a moment after the player opens
        }
        XCTAssertTrue(position.waitForExistence(timeout: 15), "The player did not open: \(visibleTexts())")
        let first = position.value as? String
        var moved = false
        for _ in 0..<10 where !moved {
            sleep(2)
            if !position.exists { app.tap() }
            moved = position.exists && (position.value as? String) != first
        }
        step("playing a downloaded file") {}
        XCTAssertTrue(moved, "The downloaded file did not play: position stayed \(first ?? "nil"). \(visibleTexts())")
    }

    /// The downloads' settings sit on the Settings root, next to the others: Offline mode for the device, mobile data and the storage limit for the profile.
    func testZSettingsHasDownloadSettings() throws {
        try XCTSkipIf(UIDevice.current.userInterfaceIdiom == .pad, "The iPad shows Settings as a sidebar; its rows are checked on the phone")
        let profile = button(username)
        XCTAssertTrue(profile.waitForExistence(timeout: 20), "Profile menu is missing")
        profile.tap()
        let settings = button("Settings")
        if !settings.waitForExistence(timeout: 5) { profile.tap() }
        XCTAssertTrue(settings.waitForExistence(timeout: 5), "Settings menu item is missing")
        settings.tap()
        XCTAssertTrue(button("Quality", contains: true).waitForExistence(timeout: 10), "Settings did not open")
        // The list draws only what is on screen, so scroll to each row.
        for row in ["Offline mode", "Download over mobile data", "Storage limit for downloads"] {
            for _ in 0..<8 where !switchOrButton(row).exists { app.swipeUp() }
            XCTAssertTrue(switchOrButton(row).exists, "\(row) is missing from Settings. On screen: \(visibleTexts())")
        }
    }

    private func switchOrButton(_ label: String) -> XCUIElement {
        let match = NSPredicate(format: "label CONTAINS[c] %@", label)
        let toggle = app.switches.matching(match).firstMatch
        return toggle.exists ? toggle : app.buttons.matching(match).firstMatch
    }

    private func tabButton(_ name: String) -> XCUIElement {
        let match = NSPredicate(format: "label ==[c] %@", name)
        let tab = app.tabBars.buttons.matching(match).firstMatch
        return tab.exists ? tab : app.buttons.matching(match).firstMatch
    }

    private func visibleTexts() -> String {
        app.staticTexts.allElementsBoundByIndex.map { $0.label }.joined(separator: " | ")
    }

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
