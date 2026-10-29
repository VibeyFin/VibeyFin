# Plan 20: Android tab shell: one header, a floating bar, the profile everywhere

> **Status:** Phases 1 and 2 done, reviewed on a phone against the test server (iOS only compiled to klibs and parsed: Xcode not installed). Phase 3 not started.

**Goal:** The four tabs on Android look like one app. Each has the same header (the page name, the same size and place, and the
profile at the right), and one floating bar at the bottom replaces the phone's `NavigationBar` and the tablet's tabs at the top.

**Related:** issue #42. Android first; in review iOS took the same headers, profile button and storage card, and keeps the system tab bar.
Mockups: the `AndroidTabShell` card in the shared mockups artifact.

## Decisions

- **One header for the four tab roots** (`TabHeader`): the page name in `headlineMedium` at the left, the profile button at the
  right, `screenMargin()` at the sides and `Space.s3` above and below. Home, Search, Library and Downloads all use it, so the title
  keeps its size and place when the tab changes. Pages opened from a tab (an item, a folder, a list) keep their own headers.
- **No logo on Home.** The header says "Home" like the other tabs, online and offline.
- **Search has a title** ("Search", `Strings.tabSearch`) above the field.
- **The profile button is on every tab**, drawn by the shell rather than by Home, so it behaves the same everywhere. One tap opens
  the menu as today (Switch profile, Switch server, Settings, Sign out; no Sign out on a kids profile). A double tap goes straight
  to Switch profile, through the PIN when the profile has one. TalkBack gets "Switch profile" as a custom action, since a double
  tap is TalkBack's own activate gesture. Offline the menu has Settings only, as today, and a double tap does nothing: there is no
  server to sign in to.
- **The menu's logic moves out of Home** into a shared `ProfileMenuViewModel` (user name, kids profile, the PIN sheet, the exit).
  `HomeViewModel` keeps its methods for iOS, both reaching one `ProfileActions` helper, so the rules live in one place.
- **Floating bar.** A pill (`CircleShape`) detached from the edges: `Space.s4` from the sides, `Space.s3` above the system
  navigation inset, `surfaceContainer` with a soft shadow, at most 480dp wide and centred (a tablet, a phone in landscape). The four
  tabs, Library hidden offline. M3's `NavigationBarItem` (labels always shown, the `accent-soft` indicator), never aqua.
  Content scrolls behind it; every tab root adds the bar's height to its bottom padding (`LocalBottomBarPadding`), so the last row
  is reachable. Hidden on every page that is not a tab root's stack (the player, Settings), as today. Swiping sideways on the bar
  still changes tab.
- **No more tablet tabs at the top.** `TabletTabs`, `TABLET_TABS_HEIGHT` and the top offset go. `android.md` and `screens.md`
  change with it; iPad keeps its top bar.
- **No hide-on-scroll** for now: a bar that moves under the thumb is easy to miss, and the ticket has it as optional.

## Changed in review (phase 2)

- Headers are one line, `headlineSmall`, `Space.s2` above and below; no title count on Library, no storage line on Downloads.
- Downloads shows its storage in a card on the page ("On this device" over "2.4 GB used") with the bin as an icon.
- The bar is only as wide as its tabs; the tab showing has its label beside its icon in `ink` on a faint tint, the others are
  icons alone. A finger dragged along it moves the highlight, and the page changes when it lifts.
- Going offline is a toast on both platforms (Android's `Toast`, an iOS capsule); "Can't reach the server" stays a banner.
- The detail page's actions sit in four places of one width, edge to edge under Play; fewer keep the same places (both platforms).
- **iOS follows the headers** (asked in review): the four tabs share one `TabHeader` with the profile (Home without its logo, Search
  with its title over a `UISearchBar`, Library without its count), the profile model is one `ProfileMenuViewModel` for the shell
  (`ProfileMenuModel` facade) opening the same `Menu` Home had, with no double tap (it slowed every tap), and Downloads has the storage card.
  The tab bar stays the system's, which floats on iOS 26.

## Not in this plan

A custom iOS tab bar, a new tab, a redesign of the player or of the detail pages, light theme or dynamic colour.

## Phases

### Phase 1: shared profile menu

1. **`ProfileActions`** (`ui/common`): the switch profile, switch server and sign-out rules taken from `HomeViewModel`, with the
   PIN gate and the exit, run in the caller's scope.
2. **`ProfileMenuViewModel`** (`ui/profile`): `ProfileMenuUiState(userName, kidsProfile, pinPrompt, exit)` (offline comes from the shell, which already knows it), and
   `onSwitchProfile`, `onSwitchServer`, `onSignOut`, the PIN callbacks. Registered in Koin.
3. **`HomeViewModel`** reaches the same `ProfileActions`; its public API does not change, so iOS builds as before.
4. **Strings:** none new. The button is named for the person as today, and the TalkBack action uses `Strings.switchProfile`.
5. **Tests:** `ProfileMenuViewModelTest`: switch profile with and without a PIN, sign out (not for a kids profile), the
   offline state, the session ending. `HomeViewModelTest` keeps passing unchanged.

Done when `./gradlew :shared:allTests` and `:androidApp:assembleDebug` pass and the iOS smoke test passes.

### Phase 2: Android shell

1. **`TabHeader`** (`components`) and **`ProfileButton`** (`navigation`, the menu, the double tap and the PIN sheet), the button
   provided to the tab roots through a `CompositionLocal` so none of them takes the profile's callbacks.
2. **`FloatingNavigationBar`** replaces `MainNavigationBar` and `TabletTabs`, drawn over the content in the shell;
   `LocalBottomBarPadding` for the tab roots' lists and grids.
3. **Screens:** Home and offline Home lose the logo and their own profile menus; Search gets the title; Library (the root of
   `ChildrenScreen`) and Downloads take `TabHeader`.
4. **Docs:** `android.md`, `screens.md` (Home, Search, the tablet note).
5. **Verified** on a phone and a tablet emulator against the test server: switching tabs keeps the title still, the last row
   clears the bar in every tab, gesture and 3-button navigation, a double tap with a PIN profile.

### Phase 3: consistency pass

1. The screens touched in phase 2 lose their hard-coded dp and colours for `VibeyFinTokens` and the type scale.
2. One-off copies of a shared component (chips, menus, empty and error states) are replaced with the component.
3. Before and after screenshots of each tab, phone and tablet, on the issue.
