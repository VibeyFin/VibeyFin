# Right-to-left readiness

No right-to-left language ships yet. Before the first one (Arabic, Hebrew) is added to `languages.json`, fix these. Found by an audit of the code, not yet verified on a device; check each on both platforms.

## Must fix

- **Time left:** `timeLeft` in `Strings.kt` puts a minus sign before the clock; in a right-to-left line it can land after the digits. Wrap the time in left-to-right isolates (U+2066 … U+2069) or set the text direction to LTR on the player's time labels (`PlayerControls.kt`, `PlayerProgressBar.swift`).
- **Player zones and their indicators:** swipe zones are physical (left and right of the screen) and the copy says so, which stays. The indicators must follow the zones, not the layout direction:
  - Android: `PlayerGestures.kt` volume pill and `SeekIndicator.kt` use `CenterEnd`/`CenterStart`, which mirror; use physical alignment.
  - iOS: `SeekingOverlay` (seek total) and `PlayerSwipe.swift` `SwipeLevelIndicator` order an `HStack` with a `Spacer`, which flips; use an explicit physical frame alignment.
- **Hero pager (iOS):** `HomeHeroPager.swift` reads the page from `contentOffset.x / width`, which is inverted in a right-to-left collection view; set `semanticContentAttribute = .forceLeftToRight` or map the index.
- **Hero scrims (Android):** `ItemHero.kt` and `CollectionHeader.kt` use a physical `horizontalGradient` that is dark on the left; reverse the stops under `LayoutDirection.Rtl` so text stays on the dark side.

## Cosmetic

- Every `" · "` / `"  ·  "` join (`serverMeta`, `ItemFacts`, `ContinueCard`, `DownloadUi`, `TileMenuViewModel` and others) can drift next to digits; give the separator a translatable key and isolate left-to-right runs. Several joins live outside `Strings`, against the layering rule.
- `PlayerTransport.swift` `backward.end`/`forward.end` symbols and iOS chevrons mirror in right-to-left; check they still point the right way.

## Already fine

`android:supportsRtl="true"`, `Icons.AutoMirrored` chevrons, `.leading`/`.trailing` in SwiftUI, the tab bar, poster rows, and the Android pager's index math.

Tell translators that "left" and "right" in `skip_help` and `swipe_gestures_help` mean the physical sides of the screen.
