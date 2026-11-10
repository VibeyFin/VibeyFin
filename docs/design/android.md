# Android

A Jetpack Compose app that follows Material 3. Use the `android` theme and `m3-*` type styles; build a `ColorScheme` (dark, neutral, no seed tint). **Mapping:** M3 `primary` = `action` (white) and `onPrimary` = `on-action`, so every filled button is white by default; `secondaryContainer` = `tonal`; `tertiary` = `spark` (copper) is used ONLY explicitly: `LinearProgressIndicator`, the scrubber, `Switch` (checked), the selected nav icon and label, the selected `FilterChip` (`spark-soft`). Never pass `primary` to those, or they turn white. Build `Typography` from the tokens and pass both to `MaterialTheme`. When Material 3 has a component for it, use the M3 component.

## Structure

- `Scaffold` per screen with a `TopAppBar` (Home: app name + profile avatar; tab roots: medium / large top app bar that collapses on scroll; detail: transparent bar over the backdrop).
- Main navigation: a floating pill bar over the content (`FloatingNavigationBar`) with Home, Search, Library, Downloads: `surfaceContainer`, a soft shadow, `Space.s3` above the system navigation, centred and only as wide as its tabs, with a 1dp `ink` 8% edge. The tab showing is its icon and label side by side in `spark` on the neutral `tonal` pill (selected = copper, as on iOS); the others are icons alone (56dp wide) in `ink-muted`, each named for TalkBack. Drawn here, not with M3's `NavigationBarItem`. The same bar on phones and tablets (no `NavigationRail`, no tabs at the top); the tabs' lists add `LocalBottomBarPadding` at the bottom so their last row clears it. As on iOS, a finger dragged along the bar moves the highlight to the tab under it, with a `SegmentTick` haptic for each; the page changes only when the finger lifts.
- Tab header: Home, Search, Library and Downloads share `TabHeader`: one line, the page name in `headlineSmall` at the left and the profile avatar at the right, no second line on any tab, so the title stays still when the tab changes. Downloads shows its storage in a card on the page (`surface-1`, "On this device" over "2.4 GB used", the bin as an icon), not in the header. The avatar is on every tab: a tap opens the profile menu (there is no double tap), and TalkBack gets Switch profile as a custom action.
- Navigation Compose with one back stack per destination; system back and **predictive back** (`android:enableOnBackInvokedCallback="true"`) on every screen, sheet and the player.
- Edge-to-edge (`enableEdgeToEdge()`); pad with `WindowInsets`, never fake status-bar strips. Dark always — no dynamic color.

## Components to use

| Need | Use |
| --- | --- |
| Play / Resume | `Button` (filled, `action` = white); 56dp on hero and detail |
| Secondary action | `FilledTonalButton`; tertiary `TextButton`; icons `IconButton` |
| Quick actions on a card | Long-press → `ModalBottomSheet` with the actions as list items |
| Overflow | `⋮` `IconButton` → `DropdownMenu` |
| Home category buttons (Shows, Movies, New, Categories) | `LazyRow` of M3 `AssistChip`s (filled, no outline) under the top app bar, never selected. Categories carries a trailing dropdown arrow and opens a `ModalBottomSheet` with the player's `OptionList`, no search; the others open their page |
| Audio / subtitles | `ModalBottomSheet` |
| Season buttons (series detail) | `LazyRow` of `FilterChip`s, selected = `spark-soft`; `rememberLazyListState` scrolled to the next-up season on open |
| Home hero (optional) | One rounded card, or a `HorizontalPager` of them (2 to 5) with a row of dots under it: current `ink`, others `ink-subtle` (M3 has no pager indicator). Auto-advance as `guardrails.md` says (see `screens.md` **Home hero**) |
| Messages | `Snackbar` with an action ("Try again" for a failed write); the action is plain text, not copper |
| Casting (planned) | Google Cast button + MediaRouter, shown only when a device is found, pressed while connected; the device list is an M3 dialog on `surface-2` with "Stop casting"; the player shows a "Casting to <device>" status pill (`player.md`) |
| Player skip buttons | clickable 48 dp buttons with a generic arrow glyph and the seconds as text over it (seconds from the profile's skip settings; 10 back, 10 forward by default), play / pause between them. A press turns the arrow about 30° its way and springs it back |
| Skip feedback | `SeekRipple`: Android's double-tap seek look, a half circle on the side the video moved with a ripple per tap, three running chevrons and the total skipped so far ("30s"); shown for buttons and double-taps alike |
| Settings rows | Toggle: a `Switch` row. Choice: a `ChoiceRow`, a menu (`DropdownMenu`) with the value at the right, never a sub-page. Link: a row with a chevron to a sub-page. Rows sit in rounded `surfaceContainerLow` cards with a leading tonal icon circle and group titles in `ink-muted` text |
| Quality setting | the one checklist: inline rows in its own section (title once, footer below), the chosen row an `spark-soft` pill with no tick |
| Settings on tablets | from the 600dp tablet breakpoint a plain two-column layout (root left, page right, detail capped at about 640 and centred), the selected root row an `spark-soft` pill that follows the tapped row. Not `material3-adaptive`. Below it, push navigation |
| Skip intervals setting | the settings `ChoiceRow` (Skip back 5 to 30 s, Skip forward 10 to 60 s), on the Skipping and autoplay page |
| Buffer ahead setting | the same `ChoiceRow` (Auto, 10 to 60 s, default 20 s), in its own group on Skipping and autoplay. Player: `DefaultLoadControl.setBufferDurationsMs`; the scrubber's buffered segment reads `player.bufferedPosition`, white at 40% (`overlay-strong`) under the copper fill |
| Player | Compose controls over Media3 `PlayerSurface`; `MediaSession` notification; PiP with `setAutoEnterEnabled` on Android 12+ |
| Loading | `LinearProgressIndicator` / skeletons; `CircularProgressIndicator` only in the player |

## Go

- Ripple and state layers for press feedback; `HapticFeedbackType.LongPress` on long-press.
- Tonal elevation (surfaceContainer steps), not shadows.
- Downloads with `WorkManager` + a foreground-service notification showing progress; request notification permission when the first download starts.
- Survive rotation and process death: player position and screen state restored.
- New-title notifications (the Notifications setting, per profile, off by default):
  - Channel "New episodes and movies" (`Strings.addedTitlesChannel`), default importance. Small icon `ic_logo`, monochrome, and no accent colour: copper never marks status.
  - One notification per new movie. Episodes are grouped per show (one group per profile and show) under a summary "New episodes of {show}"; every check that posts an episode posts the show's summary again under the same tag, so later episodes join the same group and only the episodes alert. The profile's name is added only when several profiles on the device get notifications.
  - The permission (Android 13 and up) is asked when Notifications is switched on, never earlier. A refusal switches the setting back off and the page says notifications are blocked and where to allow them.
  - A `WorkManager` periodic check about every 4 hours with a network, best effort (Android decides when it runs). It runs while any profile has Notifications on and stops when none does, and it skips the check while notifications can't be shown. At most once per title: a title is marked before it is posted and a failed run is not retried.
  - Tapping opens the title inside the tab on screen (Home from the player or Settings), and only for a tap the app posted (a saved profile with Notifications on, a title it was notified about). It never signs a profile in or bypasses a PIN: for another profile it goes to "Who's watching?", unless the signed-in profile's PIN guards switching, then nothing happens. Reopening the app from Recents does not open the title again.
- Test TalkBack, font scale 200%, "Remove animations", a compact phone, a foldable / tablet.

## Don't

- Don't use iOS shapes or glyphs: no `‹` chevrons, `…` overflow, action sheets, or glass / blur chrome.
- Don't use Material You dynamic color or a light theme.
- Don't use swipe-to-act on list rows for primary actions — long-press and `⋮` instead.
- Don't intercept back to show "Press again to exit".
- Don't run downloads without a visible notification.
