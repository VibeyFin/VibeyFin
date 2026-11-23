# iOS: adapting VibeyFin to iPhone Duo (foldable iPhone)

Researched 2026-11-19. Nothing here is implemented. `docs/` had no research folder or convention (flat guides plus `docs/design/`), so this note starts `docs/research/`.

Labels: **[Official]** Apple or the vendor says it. **[Derived]** computed by me from official numbers. **[Third-party]** a blog or news post, not Apple. **[Unconfirmed]** could not be verified.

## 1. Device facts

The name is right. "iPhone Duo" is a real, shipping device, and the pre-launch rumour names ("iPhone Fold", "iPhone Ultra", "iPhone Flip") were not used.

| Fact | Value | Source |
|---|---|---|
| Announced | 9 Sep 2026, "the first foldable iPhone" [Third-party summary of newsroom; the newsroom page confirms the product and dates below] | https://www.apple.com/newsroom/2026/09/apple-unveils-iphone-duo/ |
| Pre-order / on sale | Pre-orders 16 Oct 2026; available 23 Oct in 70+ countries; 28 more on 30 Oct. [Official] | https://www.apple.com/newsroom/2026/09/apple-unveils-iphone-duo/ |
| OS | Requires iOS 27.1 [Official] | same |
| Price | From $1,999 US [Official] | same |
| Inner display | 7.6 in (7.58 rectangular), 1878 x 2670 px, 430 ppi, aspect 313:445 (about 1:1.42) [Official] | https://support.apple.com/en-us/148588 |
| Outer display | 5.4 in (5.36), 1398 x 2034 px, 460 ppi, aspect 233:339 (about 1:1.45) [Official] | https://support.apple.com/en-us/148588 |
| Size | Open 164.6 x 117.8 mm; closed 84.1 x 117.8 mm [Official] | https://support.apple.com/en-us/148588 |
| Point sizes | Apple publishes no point size or scale factor. At 3x the outer display would be about 466 x 678 pt and the inner about 626 x 890 pt. [Derived, unconfirmed] Read the real values from the simulator and do not hard-code them. | https://support.apple.com/en-us/148588 (says no scale factor given) |
| Hinge | Magnet closure, nano-texture finish "minimizes crease visibility", partially open poses (book, tent, tabletop) [Official] | https://www.apple.com/newsroom/2026/09/apple-unveils-iphone-duo/ |
| Multitasking | First iPhone with Split View (two apps side by side) and multiple windows of one app (inner display only) [Official] | newsroom page above; https://developer.apple.com/videos/play/tech-talks/111464/ |

Size classes [Official, https://developer.apple.com/videos/play/tech-talks/111461/]:

| Display / pose | Horizontal | Vertical |
|---|---|---|
| Outer, portrait | Compact | Regular |
| Outer, landscape | Compact | Compact |
| Inner | Regular | Regular |

The inner display does not honour supported interface orientations (same source). A forum thread shows the behaviour is inconsistent with `UIRequiresFullScreen` and Info.plist combinations: https://developer.apple.com/forums/thread/848598 [Third-party report, 0 replies].

## 2. Official guidelines and docs (read these first)

All [Official], developer.apple.com.

1. Prepare your app for iPhone Duo (Tech Talk 111461). https://developer.apple.com/videos/play/tech-talks/111461/
   - Apps run unmodified. iOS 27 SDK: app extends left of the status bar on the inner display. iOS 27.1 SDK: full edge, and system bars lay out vertically.
   - Use size classes, not interface orientation or idiom. Do not use `UIScreen.main` (ambiguous with two displays, "will be deprecated"); use the environment, trait collection or window-scene bounds.
   - Safe areas and layout margins are asymmetric (vertical bars can sit left or right). Foreground controls inside the safe area, full-bleed artwork may go past it (`.ignoresSafeArea()`). Never assume equal opposite insets.
   - `NavigationSplitView` and `TabView` adapt automatically. Opt-in sidebar on the inner display: `.defaultTabBarPlacement(.sidebar)`.
   - `ConcentricRectangle` for shapes that fit the new corners.
   - New in iOS 27.1: `ReservedRegion` (SwiftUI), `UIViewReservedRegion` (UIKit).
   - Test Split View on both sides in Device Hub.
2. Design for iPhone Duo (Tech Talk 111466). https://developer.apple.com/videos/play/tech-talks/111466/ Target compact and regular width classes, not breakpoints. Video in PiP can pin to the top. A half-folded device makes video fill half the screen and the app resizes vertically live. System sheets, alerts, menus and toolbars avoid the fold for free. Scrollable content need not avoid the fold.
3. Raise the bar with iPhone Duo (Tech Talk 111462). https://developer.apple.com/videos/play/tech-talks/111462/ Navigation, toolbar and tab bars move to the side. Use system bars only (custom bars are not considered). Symbol-only items suit vertical bars. APIs: `axisBehavior`, `toolbarVerticalEdge`, `toolbarVerticalCompressionBehavior`, `visibilityPriority`, `ToolbarOverflowMenu`, opt out with `.toolbarVerticalBehavior(.disabled)`.
4. Strike a pose with adaptive layouts (Tech Talk 111463). https://developer.apple.com/videos/play/tech-talks/111463/ Reserved regions (division = fold, occlusion = FaceTime camera) via `GeometryProxy.reservedRegions(kind:)`. Displacement pattern. `ArrangementView` with `.split` and `.overlay` styles (iOS 27.1+). Do not put `NavigationSplitView` inside an `ArrangementView`, or an `ArrangementView` inside a `List` or `ScrollView`.
5. Leverage multiple displays and scenes (Tech Talk 111464). https://developer.apple.com/videos/play/tech-talks/111464/ `onHingeChange` / `UIHingeInteraction` for effects (not layout). New windows are inner display only. Use `UIWindowSceneActivationAction`. Scene accessories (not relevant to VibeyFin).
6. Modernize your UIKit app, WWDC26 session 278. https://developer.apple.com/videos/play/wwdc2026/278 Flexible layout and the resize mode in Device Hub and Previews.
7. Technology overview "Preparing your app for iPhone Duo". https://developer.apple.com/documentation/technologyoverviews/preparing-your-app-for-iphone-duo [Could not fetch the body, only the title. Read it in a browser.]
8. HIG "Designing for iPhone Duo". https://developer.apple.com/design/human-interface-guidelines/designing-for-iphone-duo [Same: body not retrievable here. Read it in a browser.]
9. Xcode 27.1 release notes. https://developer.apple.com/documentation/xcode-release-notes/xcode-27_1-release-notes.md Preview canvas gets a Display group to preview a device's alternative display. Simulator limits: StandBy and most extensions unavailable in the Duo runtime.

Gaps: I found no Apple page on AVKit or `AVPlayerViewController` full-screen behaviour on Duo. Talks 111466 and 111464 have no player API guidance. The Mux post (https://www.mux.com/blog/what-the-iphone-duo-means-for-web-development, Third-party) only says web custom fullscreen is limited on a folded Duo. VibeyFin plays through its own player UI over AVPlayer and orientation-locks to landscape (`iosApp/iosApp/App/OrientationLock.swift`), so the player needs hands-on testing; there is no documentation to lean on.

## 3. Tooling and skills

- Simulator: `xcrun simctl list devicetypes` on this Mac (Xcode 27.2, build 27B52) already lists "iPhone Duo", and a shut-down "iPhone Duo" simulator exists. Create or boot it in Device Hub (Xcode 27.1 or later). On-screen controls open, close, rotate and fold. [Official: Tech Talk 111461; local check]
- Xcode Previews: resize mode and the new Display override group (Xcode 27.1 notes, above).
- Apple's bundled agent skills in Xcode 27: `swiftui-specialist`, `swiftui-whats-new-27`, `uikit-app-modernization` and others, exported with `xcrun agent skills export --output-dir <dir>`. [Third-party list: https://dev.to/arshtechpro/wwdc-2026-xcode-27-ships-with-apples-own-agent-skills-what-they-are-and-how-to-use-them-3g2] Tech Talk 111461 says the modernization skill is renamed "App Resizability" in Xcode 27.1 and now supports SwiftUI and Duo. [Official]. I could not verify the exact exported names; run the export and check. `xcrun agent` is not a command here (`xcrun agent skills --help` failed), so the export route needs Xcode's own UI or a newer command form.
- Installed Claude skills (`~/.claude/plugins/cache/indie-apple-stack/apple-skills/.../skills/`): none mention foldables or Duo (grep found nothing). Closest:
  - `ios/ipad-patterns` (size classes, Split View, NavigationSplitView widths) and `ios/ui-review`, `ios/navigation-patterns`, `ios/accessibility-audit`, `ios/run-simulator`.
  - `swiftui/layout` (Layout protocol vs GeometryReader, lazy stacks). Useful for the "avoid GeometryReader" part. Note it is general, not Duo-aware.
  - `design/liquid-glass`, `testing`, `performance/swiftui-debugging`.
  They help with the groundwork (size-class refactor), not the Duo-specific APIs (reserved regions, ArrangementView, vertical bars). Plan to feed the Tech Talk transcripts or Apple's exported skills in as the Duo knowledge.
- Android analogue tooling: Jetpack WindowManager and Compose Material 3 Adaptive. https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware

## 4. How others adapted

- Netflix [Official, vendor]: redesigned home screen for the big inner display, player controls that adapt to full screen, book, tent and tabletop poses ("stay within reach at all times"), hinge-angle-responsive glow on controls, Clips browsed on the outer display then continued on the inner. https://about.netflix.com/en/news/mobile-experience-that-moves-with-you . Apple's newsroom also names Netflix, Zoom and Slack as already adopted.
- Infuse (closest to VibeyFin) [Official, App Store release notes]: 8.5.10 "Added support for iPhone Duo", later "Improved support for iPhone Duo". https://apps.apple.com/app/id1136220934 . No detail on what changed.
- Plex [Third-party]: a user request on 30 Oct 2026 says the app is not yet optimised (no Split View, poor fold transitions). No official Plex announcement found. https://forums.plex.tv/t/iphone-duo-optimisation/945326
- Swiftfin, YouTube, Spotify, Disney+: no Duo support statement found. [Unconfirmed]
- Android precedent [Official, Google]: size classes plus `WindowInfoTracker` / `FoldingFeature` (state FLAT or HALF_OPENED, orientation, occlusion, `isSeparating`), tabletop posture for video (video above the fold, controls below), book posture, avoid controls near the hinge, `currentWindowAdaptiveInfoV2()` for window size class. https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware . I found no Netflix Pixel Fold or Galaxy Fold source, so I do not claim one. The Apple model is the same idea: size classes for layout, fold regions for avoidance, posture for the player.

## 5. VibeyFin: what exists and what is affected

Current state (grep of `iosApp/iosApp`):
- Deployment target is iOS 18.0 (`iosApp/iosApp.xcodeproj/project.pbxproj`). Duo APIs are iOS 27.1, so they need `#available(iOS 27.1, *)`.
- `horizontalSizeClass` is already used in about 15 views (Home hero and carousel, Item, Library children and collection header, Seerr rows and title, Person, Downloads, Settings split view, `Theme.swift` margins, `TabHeader`, `Skeletons`, Who's watching). Regular width gives wider margins (`Space.regularMargin`).
- Good: `TabHeader.swift` branches on `userInterfaceIdiom == .pad && sizeClass == .regular` (lines 101 and 262). On Duo the inner display is regular width with the phone idiom, so it takes the phone path. This is the main idiom-based assumption to remove.
- `OrientationLock.swift` returns portrait for the phone idiom and forces landscape for the player. On the inner display, orientations are not honoured as listed, so the "iPhone stays portrait" rule and the player landscape lock behave differently there.
- `RootView.swift:97` uses `UIScreen.main.bounds`, which Apple says to stop using.
- `GeometryReader` sits in many places (Item, Seerr, Home `ContinueCard`, Skeletons, Library header, Player). Each must tolerate asymmetric insets and a resizing window.
- `SettingsSplitView` is the one `NavigationSplitView`.
- No `ViewThatFits` beyond `TechnicalActionButtons`, no `ReservedRegion`, no hinge handling, no Duo mention in `docs/design/ios.md` or `docs/design/guardrails.md` (ios.md covers iPad only).

Likely affected screens:
| Screen | Why |
|---|---|
| Player (`Player/`) | Landscape lock, controls placement, PiP and half-fold resizing, tabletop and book posture. Highest risk and no Apple player guidance. |
| Home hero and rows (`Home/`) | Hero aspect and row counts at about 626 pt wide with regular width. Check `sizeClass`-only logic. |
| Item detail (`Item/`) | Hero, episode list, many `GeometryReader`s. A regular-width split (artwork plus details) is likely. |
| Library grid (`Library/`) | Column count by width, not idiom. |
| Tab bar and `TabHeader` | Vertical bar on the outer display, optional sidebar on the inner display, idiom checks. |
| Who's watching and onboarding | Centred grid at regular width. |
| Seerr screens, Person, Downloads, Settings | Same size-class review. |
| Sheets and menus | System ones adapt; custom overlays (`PlayerOverlays`) do not. |

## 5b. Simulator walkthrough findings (2026-11-19)

Unmodified debug build on the iPhone Duo simulator (iOS 27.2, Xcode 27.2), driven by hand on both displays. Simulator only, no physical device. Each screen was walked with a person tapping and swiping and a screenshot taken of each display (`xcrun simctl io <udid> screenshot --display=1` is the outer display, `--display=3` the inner). Nothing in this section was fixed yet.

What the simulator shows on both displays: system chrome sits in a reserved strip on one edge (clock, Wi-Fi, the vertical tab bar pill on the right, the back chevron). Page content is inset from it, artwork and horizontal rows are not handled consistently.

| # | Finding | Where | Duo-specific |
|---|---|---|---|
| 1 | The reserved strip and the vertical tab bar are handled inconsistently from screen to screen. | Outer and inner | Yes |
| 2 | Hero artwork and backgrounds stop at the strip, leaving a black stripe (Home, Item detail, Library). Horizontal rows ignore the inset and scroll under the pill; they still end clear of it, so nothing is stuck behind it. Likely cause: `liftRoom()` in `Components/TileContextMenu.swift:60-69` uses `.scrollClipDisabled()`. The inner Item detail bleeds its artwork correctly and is the model to follow. | Mostly outer | Yes |
| 3 | A pushed list page keeps the system nav bar (back chevron) and adds `.padding(.top, Space.s5)`, leaving a gap above the title (`Library/FilteredListView.swift:33-37`). Same on iPhone 18 Pro. | All iPhones | No, separate bug |
| 5 | The system back chevron sits in the right strip, away from the left-aligned title. | Outer | Yes |
| 7 | Horizontal chip rows run under the clock and Wi-Fi and collide with them (Library, Search; "Sort: Name", "People" are overlapped). Fine on the inner display, where the chips fit. | Outer | Yes |
| 8 | Search People grid: two columns of very large circles, cells not top-aligned (the right column sits lower). | All iPhones | No, separate bug |
| 9 | Player controls (scrubber, time labels, 10 s and play buttons) are laid out in the safe-area box, not centred on the picture. The outer display has a large left inset, so the controls look shifted right; the inner display has a large right inset, so they look shifted left. Everything stays tappable. The video itself fills the width correctly. | Both | Yes |
| 10 | Inner Home: the pill covers part of the fourth Continue Watching card, and the hero stops at the strip. | Inner | Yes |
| 11 | The Play button says "Play S1 E0" while Specials (S0) is selected. May be Jellyfin data. | Item detail | No, to check |
| 12 | Who's watching on the inner display is a single top-left row of four tiles with the lower 60% of the screen empty. Polish, not a bug. | Inner | Yes |
| 13 | Half-folded, held in landscape (book pose, vertical crease at the middle): the app shows the same wide inner layout as when fully open, with no resize or reflow. Rows and cards cross the crease (one Continue Watching card ends at it, a My List poster straddles it). Apple says scrollable content need not avoid the fold, so this is fine; the Home hero was checked too: its artwork crosses the crease, which is fine, while the title, genre line and the Play and My List buttons all sit in the left half and clear it (My List ends about 70 px short of the crease in the Simulator window). Player (book pose, controls up): the video and scrubber cross the crease, which is acceptable, but the centred transport buttons are laid out in the safe-area box (finding 9), so the play button's right edge sits on the crease (play centred about 34 px left of it in the Simulator window) and the 10 s buttons flank it. Apple says to keep controls away from the fold (Tech Talk 111463, reserved regions). The player does not resize or reflow for the half-fold; Apple's description of video filling half the screen was not seen here, and may apply to tabletop (horizontal crease) only. `simctl` screenshots do not draw the crease, only the Simulator window does. | Inner, book pose | Yes, to finish checking |
| 14 | Half-folded, held in portrait (tabletop pose, horizontal crease): the app shows its normal phone layout with the horizontal bottom tab bar, nothing resized. The crease falls between the hero and the Continue Watching row, with the section title on it. Hero buttons clear the crease. The player in this pose is not checked yet. | Inner, tabletop pose | Yes, to finish checking |

Checked and fine: the Home hero and rows on the outer display (apart from the strip), the Library and Shows grids (consistent inset), the inner Item detail page, the search field, the server button on Who's watching (clears the strip), the player starting and playing on both displays. Not checked: the outer-display Who's watching, Seerr screens, Person, Downloads, Settings, Split View, fold and unfold during playback, tabletop and book poses, Dynamic Type and VoiceOver.

The wide layouts already work well on the inner display because the views use `horizontalSizeClass` (Home hero is a wide banner, rows show four cards, the Item detail is two-sided). The work is mostly the strip and the inset rule, not a layout rewrite.

Open research item (requested): find Apple's recommended way to handle top-bar buttons and actions on the Duo. VibeyFin draws its own header on tab roots (`TabHeader`: title, profile avatar, filter and sort chips) and uses the system back chevron on pushed pages, and the vertical system bar puts the chevron and clock in a strip away from the title. Start with Tech Talk 111462 (vertical toolbars, symbol-only items, `axisBehavior`, `toolbarVerticalEdge`, `toolbarVerticalCompressionBehavior`, `visibilityPriority`, `ToolbarOverflowMenu`, `.toolbarVerticalBehavior(.disabled)`) and the HIG "Designing for iPhone Duo" page, which this note could not fetch. Questions to answer: should the avatar and the sort and filter actions become system toolbar items, should custom headers move to a different edge, how should a back action and a title relate in a vertical bar, and does Apple say anything about chip rows under the clock.

## 5c. Evidence for the inset rule (2026-11-19)

Checks the proposed single inset rule (a) to (f) against Apple primary sources only (developer.apple.com). Method note: pages were read through a fetch tool that returns a model-made extract, not raw bytes, so quotes are as returned and could carry small wording drift; re-read the linked page before quoting in a ticket. Talk transcripts have chapter starts, not per-sentence times, so "t=" is the chapter start. Items marked [paraphrase] were not given verbatim.

Sources:
- HIG Designing for iPhone Duo: https://developer.apple.com/design/human-interface-guidelines/designing-for-iphone-duo (read through https://developer.apple.com/tutorials/data/design/human-interface-guidelines/designing-for-iphone-duo.json)
- Technology overview: https://developer.apple.com/documentation/technologyoverviews/preparing-your-app-for-iphone-duo
- Tech Talk 111461: https://developer.apple.com/videos/play/tech-talks/111461/ , 111462: .../111462/ , 111463: .../111463/ , 111466: .../111466/
- HIG Layout: https://developer.apple.com/design/human-interface-guidelines/layout
- SwiftUI: [scrollClipDisabled](https://developer.apple.com/documentation/swiftui/view/scrollclipdisabled(_:)), [contentMargins](https://developer.apple.com/documentation/swiftui/view/contentmargins(_:for:)), [ignoresSafeArea](https://developer.apple.com/documentation/swiftui/view/ignoressafearea(_:edges:)), [safeAreaPadding](https://developer.apple.com/documentation/swiftui/view/safeareapadding(_:_:)), [SafeAreaRegions](https://developer.apple.com/documentation/swiftui/safearearegions), [ReservedRegion](https://developer.apple.com/documentation/swiftui/reservedregion), [backgroundExtensionEffect](https://developer.apple.com/documentation/swiftui/view/backgroundextensioneffect())
- Not retrievable: WWDC26 session 278 (the page was not read; nothing here relies on it), Xcode 27.1 release notes (not re-read for this section), and the pages `GeometryProxy.reservedRegions`, `ToolbarOverflowMenu`, `toolbarVerticalBehavior`, `safeAreaInset` as separate pages (the first returned 404 at the guessed URL; the others were covered only through the talks and the overview). HIG Toolbars and Tab bars pages were not read.

| Rule | Verdict | Evidence |
|---|---|---|
| (a) Artwork and backgrounds bleed to the edge under the strip | SUPPORTED | 111461 t=7:07 "Allow background elements such as full-bleed artwork to fill the available space, extending behind toolbars and sidebars. For SwiftUI, use ignoresSafeArea." HIG Duo (Vertical controls): "You can also combine both approaches, letting a background image or header span the full width while scrollable content stays inset." Overview: "If your view has a hero or background image, extend it under a vertical bar using backgroundExtensionEffect() in SwiftUI." HIG Layout: "For full-screen background content, be sure to extend it underneath sidebars, toolbars, and tab bars to fit the entire screen or window." |
| (b) Text, buttons and page content stay inside the safe area | SUPPORTED | 111461 t=6:06 "make sure to place foreground elements like interactive controls within the safe area. For SwiftUI, your content is placed within the safe area by default." HIG Duo: "Use safe areas to make sure controls don't cover your content." 111466 t=6:33 [paraphrase]: on the outer display most content needs an offset so it is not hidden behind the controls; aligning to safe area insets gives it. |
| (c) Rows start at the margin, scroll under the strip, end clear of it, via system APIs | PARTIALLY | Supported: use system APIs, not fixed widths ("steer clear of fixed widths or anything tied to a specific display", HIG Duo); layout margins are asymmetric so content can get "closer to vertical buttons and the status bar, while preserving the margin on the opposite side" (111461 t=7:30). `contentMargins` doc: a horizontal ScrollView "will automatically inset its content by the safe area plus an additional 20 points". `safeAreaPadding` doc: "add a fixed amount of space to the safe area a view sees" (example is a horizontal ScrollView). 111466 t=6:33 [paraphrase]: put every interactive element inside the scrollable area so nothing is covered. NOT found: any Apple text that says a horizontal row may scroll under the vertical strip or the clock, or that its items may be partly covered by it. The only "partly obscured" text is about the fold with an overlay arrangement (111463 t=14:39: "Because you can scroll the readable content above the overlay, it's okay if it's partially obscured at times"), which is not the strip. |
| (d) Default stays safe, bleeding is opt-in | SUPPORTED | 111461 t=6:06 (quote above, "by default"). ignoresSafeArea doc: "By default, the SwiftUI layout system sizes and positions views to avoid certain safe areas ... To extend your content into these regions, you can ignore safe areas on specific edges by applying this modifier." |
| (e) Do not replace system tab, nav and toolbar with custom bars | SUPPORTED | 111462 t=2:00 (code comment on the UIKit sample) "content from custom bars (UINavigationBar, UITabBar, UIToolbar) won't be considered"; overview: set toolbar items "instead of creating a custom bar for your view based on UIToolbar, UINavigationBar, or UITabBar"; HIG Duo: "In general, don't override the default bar placement." Caveat: 111461 t=8:08 says `ReservedRegion` "is a great option for building your own custom bars or when implementing edge-to-edge UI", so custom UI is allowed if it handles regions itself; Apple prefers system bars. |
| (f) Top-bar buttons follow Apple's handling for vertical bars | PARTIALLY | Apple does give a recipe for toolbar items, but only for items inside system bars. See notes. Apple has no text on custom in-content headers or chip rows. |

### Notes

What Apple says about content under the strip versus scrolling under bars:
- 111461 t=0:30: built with the iOS 27.1 SDK "your app extends to the edge of the screen. Standard navigation and toolbar buttons now lay out vertically under the status bar." t=6:06: "Navigation bars, toolbars, and tab bars lay out outside the safe area. They automatically avoid system UI like the status bar and hardware features like the camera." and "Horizontal bars provide top and bottom insets, while vertical bars provide leading and trailing insets."
- 111462 t=10:07: "Just like horizontal bars, a vertical bar doesn't have a scroll-edge effect by default. However, it does have a background when the reduced transparency accessibility setting is enabled. Make sure your custom view content stays legible regardless." That is the only Apple text about content next to a vertical bar, and it is about legibility of custom views in the bar, not scrolling rows.
- HIG Layout (Extending backgrounds): the "scroll edge effect" is the system way to separate controls from scrolling content under bars. Apple does not say it applies to vertical bars.
- Conclusion: Apple sanctions bleeding backgrounds and header images under bars, and an inset foreground. It does not document scrolling foreground rows under the vertical strip. Rule (c) is an extrapolation from "scrolling content is inset by safe area plus margin" and from the iPad and sidebar pattern, not an Apple instruction. Treat "may scroll under the strip" as our design choice that still needs a check on the simulator (the last item must clear the strip, which it does today per 5b #2).

Documented contract of the APIs:
- `scrollClipDisabled`: "By default, a scroll view clips its content to its bounds, but you can disable that behavior by using this modifier." The doc's only use is shadows. It says nothing about safe areas or bars. So it is the documented way to let content draw outside the scroll view's frame; whether that overlaps the strip is a consequence, not documented intent. Consistent with the 5b hypothesis for `liftRoom()`, still untested. Apple also advises: "you typically still want the scroll view to clip at some point" and to add `clipShape`.
- `contentMargins(_, for: .scrollContent)`: insets the scrollable content and leaves the scroll indicators alone; the margin is added on top of the safe area ("the safe area plus an additional 20 points"). It is the right tool for the start and end margin of a row; the docs do not say how it behaves when the scroll view itself ignores the safe area.
- `safeAreaPadding`: adds a fixed amount to the safe area the view sees; the doc example is a horizontal ScrollView.
- `ignoresSafeArea`: default region is `.all`. `SafeAreaRegions.container` = "The safe area defined by the device and containers within the user interface, including elements such as top and bottom bars"; `.keyboard` = the keyboard extent; `.all` = "All safe area regions". The docs do not mention vertical bars or the Duo strip here.

Why a view with `ignoresSafeArea` could still stop at the strip: Apple documents no cause. Candidates, none confirmed by Apple text: (1) the modifier is applied with `edges:` limited to vertical edges (the default is all edges, so check each hero's call), or sits inside a container that proposes a smaller size; (2) the view is in a ScrollView or List whose content is laid out inside the safe area, so the bleed has to be applied to the background layer, not the scrolled content; (3) the strip is a bar region and `.container` covers it, so a hero that works on one screen and not on another differs in where the modifier sits. `ReservedRegion` is not a cause: it is for occlusion (cameras, Dynamic Island) and division (fold), its page does not mention safe areas, and framework views handle it for you. 5b notes that the inner Item detail bleeds correctly, so the working screen's structure is the best reference.

Top-bar buttons and actions with vertical bars (f):
- Use system toolbar items in a `NavigationStack` or `NavigationSplitView`; the overview says to do this "instead of creating a custom bar".
- Order: "Reserve the top of the vertical axis for primary navigation controls, like Back or Close, followed by prominent actions, like Done." Back is added automatically; for a custom one use `cancellationAction`; for prominent actions use `topBarPinnedTrailing` (111462 t=5:00 and 5:24).
- Vertical bars suit symbols: "Unlike horizontal bars, where items have a fixed height and allow flexible item width, vertical ones allow flexible item height and have a fixed width. This makes vertical bars better suited for symbol-only item representations." (t=5:56). Give each item a title and a symbol; "If your item has a title and doesn't have an icon, the system doesn't present it vertically", and a custom view is not shown vertically unless you set `axisBehavior(.verticalPreferred)` and it fits the fixed bar width (overview; 111462 t=8:00 and t=10:07).
- HIG Duo: "Keep text-based buttons to a minimum. Labels that include text stay in a horizontal bar." This means text chips ("Sort: Name", "People") stay horizontal by Apple's rule, so they would not move to the strip.
- Overflow: `visibilityPriority` orders what collapses; `ToolbarOverflowMenu` for extra actions; "Reserve the ellipsis symbol for overflow".
- Proximity rule (HIG Duo): "Locate controls near the content they affect. When controls belong to a content area other than the one along the trailing edge, keep them with that area rather than moving them to the side." This supports leaving sort and filter chips in the page, next to the list they filter, and moving only app-level actions (profile avatar) to the toolbar.
- `toolbarVerticalEdge` (environment) tells a custom view whether a vertical bar is present and on which side; `.toolbarVerticalBehavior(.disabled)` opts a sheet out (t=14:21).
- Not found: Apple text on a custom page header (title row plus avatar plus chips) drawn inside content, how a back chevron should relate to a left-aligned title in a vertical bar, or chip rows running under the clock. 111466 [paraphrase] only says a full-width header or background image can sit behind inset scrollable content.

SDK behaviour of this project (Xcode 27.2, iphoneos SDK 27.2, deployment target iOS 18.0 from `project.pbxproj`):
- Apple (111461 t=0:30): iOS 27 SDK builds extend only "to the left of the status bar area on the inner display"; iOS 27.1 SDK builds "extend to the edge of the screen" and put "standard navigation and toolbar buttons" vertically under the status bar. Overview: "Build your app with the latest version of Xcode to use all of the available screen space on iPhone Duo. When you build with Xcode 26 and earlier, your app doesn't extend under the status bar and camera." (The overview names Xcode 26; the talk names the iOS 27 and 27.1 SDKs. The two statements do not line up exactly; treat the talk as the finer one.)
- Our build uses SDK 27.2, which is later than 27.1, so it should have the full-edge behaviour, and the simulator walkthrough in 5b agrees (chrome in a reserved strip, bars outside the safe area). The deployment target does not change this; the SDK does. `ReservedRegion` and other 27.1 APIs still need `#available(iOS 27.1, *)`.

### Unresolved

1. Does Apple say anything on a horizontal row scrolling under the vertical strip or clock? Not found in any source read. Rule (c)'s "may scroll under the strip" has no Apple backing; decide it as a design choice and test it.
2. Why do heroes with `ignoresSafeArea` stop at the strip? No documented cause. Needs an experiment: check the `edges:` argument and container of each hero, compare with the inner Item detail that works, and try `backgroundExtensionEffect()` (iOS 26+) as the Apple-named alternative.
3. How should a custom in-content header (`TabHeader`: title, avatar, chips) coexist with system vertical bars? Apple's answer is "use system toolbar items"; moving the avatar to a toolbar item with a symbol and title is the documented path, but the title row and chips have no guidance, and `TabHeader` is not a custom bar in the UIKit sense.
4. Does `contentMargins(.scrollContent)` or `safeAreaPadding` behave correctly when the ScrollView ignores the safe area? The docs only show the case where the safe area is respected.
5. WWDC26 session 278, the Xcode 27.1 release notes, HIG Toolbars and Tab bars, and the standalone `reservedRegions`, `ToolbarOverflowMenu` and `toolbarVerticalBehavior` pages were not read for this section.
6. All quotes came through an extract tool, not raw page bytes; verify exact wording before quoting externally.

## 5d. Decisions on the open research points (product owner, 2026-11-19)

| Open point | Decision |
|---|---|
| Rows scrolling under the strip or clock (no Apple text) | Accepted as our design choice, as long as the last item stays tappable. The walkthrough showed it is. |
| Why the outer hero stops at the strip | The implementor investigates. |
| `scrollClipDisabled()` in `liftRoom()` as the cause | Plausible; the implementor confirms. |
| `contentMargins` or `safeAreaPadding` with an ignoring scroll view | The implementor tests it. |
| Sources not read (HIG Duo and overview, WWDC26 278, Xcode 27.1 notes, HIG Toolbars and Tab bars, `reservedRegions` reference, Tech Talk 111466) | The implementor runs a research agent for these before starting. |
| Top-bar buttons and actions | Move the profile avatar into the system bar, on the side that suits the orientation (right or left). Sort and filter chips stay in the page, which matches the HIG hint that controls sit near the content they affect. The implementor checks the exact placement APIs. |
| Player on the fold (no AVKit guidance, half-fold behaviour in tabletop) | Decided after implementation, from hands-on testing. |
| Remaining items (device facts, orientation handling, other apps, Apple agent skills) | Accepted as listed; no further research now. |

## 6. Recommended approach

1. Verify first: done for the main screens (section 5b). Still to do: Split View, fold and unfold during playback, tabletop and book poses, the screens in "Not checked", and real point sizes from the simulator.
2. Fix assumptions, no new APIs yet: replace idiom checks with size classes (`TabHeader`), drop `UIScreen.main`, review `OrientationLock` for the inner display, test asymmetric insets and every `GeometryReader`.
3. Layout by size class, not device: Home, Item and Library use compact versus regular on width. The same code then serves iPad, Split View, Stage Manager and Duo.
4. Navigation: keep system `TabView` and `NavigationStack` and system toolbars (do not replace them with custom bars, or vertical-bar support is lost). Decide on `.defaultTabBarPlacement(.sidebar)` for the inner display. `TabHeader` draws its own title row, so check how it behaves with vertical bars.
5. Player: test landscape lock, controls and PiP on both displays, and while folding or unfolding mid-playback. Consider a tabletop layout (video top, controls bottom) behind `#available(iOS 27.1, *)` using reserved regions, after a design decision. Android's tabletop precedent exists; Infuse and Netflix have done a version of this.
6. Add a mockup to the design system artifact (CLAUDE.md requires it) and a Duo section to `docs/design/ios.md`, with both displays shown.
7. Tests: size-class and layout helpers get XCTest cases in `iosApp/Tests/`. No UI tests per project rules, so behaviour decisions (for example the number of columns) move into shared code or small Swift helpers. Run `xcodebuild test ... -destination 'platform=iOS Simulator,name=iPhone 18 Pro'` plus a manual Duo pass.
8. Scope the 27.1-only APIs (`ArrangementView`, `ReservedRegion`) as a separate follow-up. They are optional polish; the base work above is what Apple says is enough for a good result.

Android: not in scope here, but Android has no `WindowSizeClass` or `FoldingFeature` use today (grep found none in `androidApp`). Worth a sibling ticket for Galaxy Fold and Pixel Fold users if parity matters.

## 7. Draft GitHub issue

Title: `Adapt the iOS app to iPhone Duo (inner and outer displays, Split View, player)`

Body:

```
## Context
iPhone Duo (Apple's first foldable iPhone, on sale since 23 Oct 2026, iOS 27.1) has an outer 5.4 in display (compact width, regular height) and an inner 7.6 in display (regular width and height, orientation not honoured), plus Split View, a fold, and book / tent / tabletop poses. Infuse and Netflix have shipped Duo support. VibeyFin runs on it unmodified but was designed for phone (compact) and iPad (regular).

Research and sources: docs/research/ios-foldable-iphone.md

## Problem
Found by walking the unmodified build on the iPhone Duo simulator (docs/research/ios-foldable-iphone.md, section 5b):
- The system strip (clock, Wi-Fi, vertical tab bar, back chevron) is handled inconsistently. Hero artwork and backgrounds stop at the strip and leave a black stripe, especially on the outer display; horizontal rows ignore the inset and run under the pill (likely `scrollClipDisabled()` in `liftRoom()`).
- Horizontal chip rows on the outer display collide with the clock and Wi-Fi (Library, Search).
- Player controls are centred in the safe-area box, not on the picture, so they look shifted on both displays.
- On the inner display Home and Library keep the stripe, the pill covers part of the fourth Continue Watching card, and Who's watching is a top-left row with most of the screen empty.
- Idiom checks (`userInterfaceIdiom == .pad`) in `TabHeader.swift` (lines 101 and 262), `UIScreen.main` in `RootView.swift:97`, and `OrientationLock` assume a phone stays compact and portrait.
- Not yet checked: Split View, fold and unfold during playback, tabletop and book poses, outer Who's watching, Seerr, Person, Downloads, Settings.

## Scope
1. One inset rule for the strip, implemented once and used by every screen, not fixed per screen: artwork and backgrounds bleed to the screen edge (no black stripe); text, buttons and page content stay clear of the strip; horizontal rows start at the page margin, may scroll under the strip, and end clear of it. Use the system safe-area and content-margin APIs, not fixed padding. The inner Item detail is the working example. Shape: a small set of shared pieces in `App/Theme.swift` next to `screenMargin()` (which 56 call sites already use), namely a bleed modifier for artwork and backgrounds and one horizontal-row component that the 15 hand-built `ScrollView(.horizontal)` sites (Home, Search, Item, Seerr, `MediaRowView`, skeletons) move onto. Screens then stop making their own inset decisions. Default stays safe: content clears the strip unless a view opts in to bleed.
2. Chip rows and headers on the outer display must not sit under the clock and Wi-Fi.
3. Player controls: pick one rule (symmetric insets using the larger side, or centring the transport buttons on the screen) and apply it on both displays. Recommended: symmetric.
4. Top-bar buttons and actions: move the profile avatar into the system bar, on the right or left depending on the orientation; keep the sort and filter chips in the page; the back chevron stays the system one. Check the exact placement APIs against Apple's guidance (Tech Talk 111462, HIG "Designing for iPhone Duo") and record the result in docs/design/ios.md.
4b. Before starting, the implementor runs a research agent on the sources this note could not read: the HIG Duo page and the technology overview, WWDC26 session 278, the Xcode 27.1 release notes, HIG Toolbars and Tab bars, the `reservedRegions` reference and Tech Talk 111466. The implementor also finds out why the outer hero stops at the strip and tests `contentMargins` / `safeAreaPadding` with an ignoring scroll view. Rows scrolling under the strip is accepted as long as the last item stays tappable.
5. Replace idiom and `UIScreen.main` assumptions with size classes and scene geometry.
6. Check the remaining screens (Who's watching on both displays, Seerr, Person, Downloads, Settings) and fix what breaks.
7. Keep system tab, navigation and toolbar components; verify vertical bars; decide on sidebar placement and on centring the Who's watching grid for the inner display.
8. Player: also verify landscape lock, PiP, and fold or unfold during playback on both displays.
9. Add the mockup to the design system artifact and a Duo section to docs/design/ios.md.
10. Phase 2, planned in: a fold-aware player (iOS 27.1+, behind `#available`). Book pose: the transport buttons and any fixed control stay off the crease (reserved regions, not a fixed offset). Tabletop pose: video in the upper half, controls and details in the lower half. Optional extra: a soft light reflection of the picture on the lower half in the tabletop pose, which could follow the fold angle (`onHingeChange` / `UIHingeInteraction`, Tech Talk 111464, for effects not layout). Open questions: whether the frames can be sampled for both engines (AVPlayer and AetherEngine), the battery and thermal cost, and whether it stays off for HDR and Dolby Vision, which a tinted overlay could misrepresent. Needs a design decision and a mockup first. Precedent: Netflix (pose-aware controls) and Android's tabletop guidance (video above the fold, controls below); Apple's sources are Tech Talks 111463 and 111466 (`ReservedRegion`, `ArrangementView`).
11. Out of scope: other hinge effects, Android foldables (separate ticket), and the non-Duo bugs below.

Separate small bugs found on the way (file as their own issues): the extra top gap on pushed list pages (`FilteredListView.swift:33-37`), the oversized and misaligned People grid in Search, and "Play S1 E0" while Specials is selected (may be Jellyfin data).

## Acceptance criteria
- [ ] Every screen in Scope 1, 2 and 6 is checked on the outer display (portrait, landscape) and inner display (portrait, landscape), with screenshots in the PR.
- [ ] No black stripe beside artwork or backgrounds; no text, control or row item is clipped by or overlaps the clock, Wi-Fi, camera, vertical bars or rounded corners.
- [ ] Horizontal rows scroll under the strip and the last item ends clear of it.
- [ ] Player controls are visually balanced on both displays, and the chosen rule is documented.
- [ ] The handling of top-bar buttons and actions follows Apple's guidance, with the source named in docs/design/ios.md.
- [ ] Phase 2: in the book pose no player control sits on the crease; in the tabletop pose the video is in the upper half and the controls in the lower half; folding and unfolding during playback keeps playing. Mockup for both poses added first.
- [ ] Layout decisions use size classes or measured width; no `userInterfaceIdiom` check decides layout for the phone idiom, and no `UIScreen.main` remains in iosApp.
- [ ] The app works in Split View (either side), resizes live without losing state (playback position, scroll position, navigation), and survives fold and unfold.
- [ ] Player: starts, plays, shows controls and exits on both displays; folding or unfolding during playback does not stop it; PiP works; controls stay reachable in tabletop pose.
- [ ] Tab bar and toolbars still use system components; behaviour with vertical bars verified.
- [ ] VoiceOver and Dynamic Type spot-checked on the inner display.
- [ ] Any iOS 27.1 API sits behind `#available(iOS 27.1, *)`; the iOS 18 deployment target is unchanged.
- [ ] Mockup added to the design system artifact; docs/design/ios.md updated.
- [ ] `./gradlew :shared:iosSimulatorArm64Test` and `xcodebuild test` (iosAppTests) pass; `swiftlint lint --strict` clean. New Swift helpers have XCTest cases.
- [ ] PR description states plainly whether verification was in the simulator only (no physical Duo).
```

## 8. Open questions
- Exact point sizes and scale factor (read from the simulator).
- AVKit and custom-player behaviour on the fold (no Apple text found).
- Whether the Duo tab bar and vertical-bar behaviour clashes with `TabHeader` and `tabRootBar`. The walkthrough shows the back chevron and clock in a right-hand strip, away from the title; Apple's recommended handling of top-bar buttons and actions is still to be researched (see section 5b).
- Whether `.contentMargins` or `.safeAreaPadding` is the right fix for the rows, and whether `scrollClipDisabled()` is the only cause. The cause in 5b is a reading of the code, not yet tested.
- HIG and the technology overview bodies could not be retrieved; read them before starting.
- Unverified: some news sources disagree on small details (for example the announcement date); the Apple newsroom and support pages above are the authority.

## 5e. Gaps closed (2026-11-19)

Read for section 5c's "Unresolved" list. Primary Apple sources only (developer.apple.com). Pages came through a fetch tool that returns an extract, so quotes are as returned and could carry small wording drift; for Tech Talks the transcripts have chapter starts only, so "t=" is the chapter start. Tags: [Official] Apple text, [Derived] my inference from Apple text, [Unconfirmed] no Apple text found.

### What was read now

- HIG "Designing for iPhone Duo" (body) via `tutorials/data/design/human-interface-guidelines/designing-for-iphone-duo.json`.
- Technology overview "Preparing your app for iPhone Duo" (body) via `tutorials/data/documentation/technologyoverviews/preparing-your-app-for-iphone-duo.json`.
- Tech Talk 111466 "Design for iPhone Duo" (full transcript), 111462 (toolbar excerpts and code samples), 111461 (safe area chapters).
- Xcode 27.1 release notes (whole page; short).
- HIG Toolbars and Tab bars (searched; no Duo text).
- API pages: `GeometryProxy.reservedRegions(kind:options:layoutDirectionBehavior:)`, `ReservedRegion`, `toolbarVerticalBehavior(_:)`, `toolbarVerticalEdge`, `ToolbarItemPlacement.topBarPinnedTrailing`, `ToolbarContent.axisBehavior(_:)`, `ignoresSafeArea(_:edges:)`, `contentMargins(_:for:)`, `safeAreaPadding(_:_:)`, `AVPlayerViewController`.

Not read: WWDC26 session 278 is "Modernize your UIKit app". Its page was fetched, but the extract held no iPhone Duo, vertical-bar or `ignoresSafeArea` text (only navigation-bar minimization, scroll edge effects, "remove references to screen bounds"). It is UIKit-focused and was read through an extract, so a full read could still hold something; nothing in it was needed. I did not read the AVKit page past its first 100,000 characters. The Xcode 27.1 notes have nothing on safe areas or bars (see below).

### Q1. Where does a profile/account avatar button go on Duo

No Apple source mentions a profile, account or avatar button. The HIG Duo, HIG Toolbars, HIG Tab bars, the overview and Talks 111462 and 111466 were all searched for it. [Official: absence] What Apple does say that applies:

- Position in a vertical bar. "Reserve the top of the vertical axis for primary navigation controls, like Back or Close, followed by prominent actions, like Done." (HIG Duo; overview; 111462 t=4:29.) The avatar is neither, so by Apple's order it belongs after Back and the prominent actions, in the remaining items: "Keep remaining toolbar items in their original groupings; the system provides a vertical space between items from the top and bottom bars." [Official]
- Placement API. `topBarPinnedTrailing` is for "prominent navigation items such as a Done button" (overview), "For prominent actions, place them using topBarPinnedTrailing placement" (111462 t=4:29). API page: "A placement that pins the item to the trailing edge of the toolbar. Pinned items only move to the overflow menu when search is active and there isn't enough room." It is available from iOS 27.0, not 27.1. [Official] A custom Back or Close uses `cancellationAction`. [Official]
- Leading vs trailing. In a vertical bar the side is chosen by the system, not by the item: "Because controls on the vertical axis stay aligned with the hardware, they hold the same position relative to the camera on the outer display, and stay on the same side in right-to-left languages" (HIG Duo), and `toolbarVerticalEdge` is "read-only; the system determines the edge based on locale and device" (`HorizontalEdge?`, nil where no vertical bar is used). On the outer display the controls are on the right ("move to the right side", 111466 t=0:28); on the inner display in Split View "the left app has controls on the left". So "leading or trailing" for an avatar is not ours to pick in the vertical case; we pick only the placement (`topBarLeading`, `topBarTrailing`, `topBarPinnedTrailing`, `primaryAction`) and the system lays it out. [Official, Derived] HIG Toolbars (generic, not Duo): the trailing edge holds "important items that need to remain available, buttons that open nearby inspectors, an optional search field, and the More menu", and the leading edge holds back and sidebar controls "followed by the view title". An avatar fits neither listed role; `topBarTrailing` is the usual iOS home for it [Derived, not stated by Apple].
- Make it eligible for the vertical bar. Give the item an icon and a title; "The system uses an icon for an item it presents vertically"; "If your item has a title and doesn't have an icon, the system doesn't present it vertically"; "If your item uses a custom view rather than a title or icon, the system doesn't present it vertically" (overview). Apple's one sample for a custom profile-style view (111462 t=8:08) is `ToolbarItem { ProfileView() }.axisBehavior(.verticalPreferred)`. It names `ProfileView` but gives no placement and no prose says where a profile goes; do not read it as Apple guidance on side. [Official]
- Text avatars or wide items stay horizontal: "Toolbar items that are too wide for the space on the right, like a text button or a segmented control, should stay put in the nav bar" (111466 t=5:07). A cart button with a dollar amount "is better to keep in the horizontal bar" (111462 t=9:00). `axisBehavior(.horizontalOnly)` forces it. [Official]
- Overflow. `visibilityPriority` orders collapse; "controls that convey important status, such as items with badges, should remain visible for longer" (111462 t=13:10). Items collapse "from bottom to top by default" (HIG Duo). [Official]
- Proximity rule (HIG Duo), already in 5c: "Locate controls near the content they affect." Supports keeping sort and filter chips in the page and moving only the app-level avatar to the bar. [Official]

Net for the 5d decision: moving the avatar to a system toolbar item with an icon plus title is the supported route. Which `ToolbarItemPlacement` is undocumented for avatars; `topBarTrailing` is the default expectation, `topBarPinnedTrailing` if it must never go to overflow. Verify in the Duo simulator, outer display and inner Split View. [Unconfirmed on the exact placement]

Does a pushed page title relate to the Back chevron in a vertical bar? Still no Apple text (5c Unresolved #3 remains for custom headers). One related fact: sheets on the outer display get vertical bars by default and can opt out with `toolbarVerticalBehavior(.disabled)`, "This works well for sheets with only a single toolbar button" (111466 t=8:36). [Official]

### Q2. Text on horizontal scroll rows next to the vertical strip

None. 111466 says "The transcript doesn't address horizontal rows directly" (my extract note, and no row text in the chapter list). What exists:
- "build your layouts with layout margins and horizontal safe area insets, and steer clear of fixed widths or anything tied to a specific display" (HIG Duo). [Official]
- "most content needs an offset so it isn't hidden behind the controls. If your app aligns to horizontal safe area insets, that offset happens automatically." and "a full-width background image or header with scrollable foreground content that's inset. Just to make sure every interactive element lives inside that scrollable area so nothing gets covered up." (111466 t=6:33.) [Official]
- "Scrollable content doesn't need to avoid this region" (111466 t=9:28), but that is the fold, not the strip.
- Nothing says a row may scroll under the strip or that items may be partly covered. So 5d's "design choice" stands and 5c Unresolved #1 is closed as "not documented". [Official: absence]

### Q3. `contentMargins` / `safeAreaPadding` when the ScrollView ignores the safe area

Not documented. What the pages say:
- `contentMargins`: "the scroll view will automatically inset its content by the safe area plus an additional 20 points" (example is a horizontal ScrollView). [Official]
- `safeAreaPadding`: "add a fixed amount of space to the safe area a view sees" (example is a horizontal ScrollView). The page has no note about `ignoresSafeArea`. [Official]
- Derived: a scroll view that ignores a safe edge sees no inset there, so `contentMargins` adds only its own fixed length on that edge and the content's start/end margin would not clear the strip. `safeAreaPadding` on the same view adds to the safe area that view sees, so it would give the fixed length too, not the strip width. To clear the strip you must either not ignore the safe area on the scroll view's leading and trailing edges (bleed only the artwork layer) or read the strip from `toolbarVerticalEdge` plus insets yourself. Both claims [Unconfirmed]; this needs the planned experiment.
- The 111461 talk gives no scroll view, content margin or `safeAreaPadding` text. It also says "layout margins are also asymmetric. This lets your foreground content get closer to vertical buttons and the status bar, while preserving the margin on the opposite side" (t=7:30). [Official]

### Q4. Why `ignoresSafeArea(edges: .top)` cannot reach the vertical strip

- Apple text: "Horizontal bars provide top and bottom insets, while vertical bars provide leading and trailing insets." (111461 t=6:06.) And `ignoresSafeArea` `edges`: "The set of edges to expand. Any edges that you don't include in this set remain unchanged. The set includes all edges by default." [Official]
- Derived: the strip is a leading or trailing inset, so `.top` (or `.vertical`) never expands into it. Only `.horizontal`, `.leading`/`.trailing`, or the default `.all` does. Apple has no sentence that says this about the strip; it follows from the two statements. A hero that uses `edges: .top` (or `.vertical`) stops at the strip, and one with `.all` or horizontal edges should reach it, which matches 5b's note that the inner Item detail bleeds. [Derived] This replaces candidate (1) in 5c as the first thing to check in each hero's call.
- 111461 t=7:07 shows only `.ignoresSafeArea()` for full-bleed artwork and "The transcript does not discuss specific edges". The overview also names `backgroundExtensionEffect()` for hero images under a vertical bar. [Official]
- `ReservedRegion` pages never mention safe areas; regions have `frame`, `margins`, `isActive`, kind `occlusion` or `division`. 111461 t=8:08: ReservedRegion "lets your custom UI use as much of the available screen space as possible without colliding with system-provided UI... a great option for building your own custom bars or when implementing edge-to-edge UI." [Official] So it is the Apple route if `TabHeader` stays custom; nothing says it is required.

### Q5. Player and AVKit

- `toolbarVerticalBehavior(_:)`: "Disable the vertical bar only for UIs that are better served by horizontal bars — such as a fullscreen video player with toolbar controls, or a non-scrolling layout like a calculator." Treat it as stable; do not toggle per state; use `toolbarVisibility(_:for:)` to hide bars. "The behavior is resolved by the window or presentation"; a `NavigationStack` uses the top-most view, a `TabView` the selected view, a `NavigationSplitView` the trailing-most column. iOS 27.1+. [Official] This is the only Apple text about a player and bars. It only matters if VibeyFin's player uses system toolbar items; its controls are custom (finding 9 in 5b), so this does not apply directly.
- 111466 t=0:28: PiP video "can now pin it to the top of the screen... Your current app resizes vertically", and half-folded "the video extends to fill half of the screen. So apps adjust vertically". Also tabletop: "Media can sit at the top, while tappable controls live on a stable base at the bottom... be sure it has all the same controls and general hierarchy... You don't want to tie functionality to a specific pose." [Official]
- `AVPlayerViewController` page: no Duo, fold, reserved-region or safe-area text (first 100,000 characters). It has `entersFullScreenWhenPlaybackBegins` and `unobscuredContentGuide` ("an area that fixed-position playback controls don't obscure when visible"), neither Duo-specific. [Official: absence]
- Overview: AVKit is mentioned only for camera capture. [Official]
- Controls near the fold: `ReservedRegion` `division` says "Arrange your content into separate sections on each side of this area"; the HIG says controls are nudged by system components, and "Use these components whenever possible to get the same fold avoidance behavior". [Official] A custom player must use `reservedRegions(kind: .division)` to do the same [Derived].

### Reserved-region API reference (for the implementor)

- `GeometryProxy.reservedRegions(kind: ReservedRegion.Kind, options: ReservedRegion.QueryOptions = [], layoutDirectionBehavior: LayoutDirectionBehavior = .mirrors) -> [ReservedRegion]`. iOS 27.1+. Returns "The reserved regions that overlap the view, with frames in the view's coordinate space", measured from the top-leading corner. By default only active regions; pass `.includeInactive` for the rest. Frames are mirrored for right-to-left unless `.fixed`. `ReservedRegion.frame` "already includes the region's margins". Kinds: `occlusion` ("Dynamic Island, a camera, or window controls... Place content outside this area") and `division` (fold). [Official]
- `toolbarVerticalEdge: HorizontalEdge?` environment: "the system's preferred edge for the vertical bar in the current context, regardless of whether a vertical bar is currently visible", nil where none is used. iOS 27.1+. [Official]
- `ToolbarContent.axisBehavior(_:)` with `ToolbarItemAxisBehavior` (`.horizontalOnly`, `.verticalPreferred` shown). iOS 27.1+. [Official]
- `topBarPinnedTrailing`: iOS 27.0+, so it needs `#available(iOS 27.0, *)` while our target is iOS 18; the 27.1 APIs need `#available(iOS 27.1, *)`. [Derived from the availability lines]

### Xcode 27.1 release notes

Short page: Previews get a Display group in the canvas overrides picker; Simulator known issues are StandBy unavailable, most app extensions unavailable in the Duo runtime, and a `CameraCaptureAccessory` oddity; Mac Catalyst compile errors with 27.1 APIs. Nothing on safe areas, bars or SwiftUI toolbar behavior. [Official]

### Items still open after this pass

1. Exact avatar placement (`topBarTrailing` vs `topBarPinnedTrailing`) and its vertical position: no Apple text; test on both displays and in Split View.
2. Custom header title row and Back chevron relationship, chip rows under the clock: no Apple text.
3. Q3 and Q4 are derived from API text, not stated by Apple; the experiment in 5d still applies.
4. WWDC26 278 was read only through an extract and has no Duo content there.
