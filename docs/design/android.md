# Android

A Jetpack Compose app that follows Material 3. Use the `android` theme and `m3-*` type styles; build a `ColorScheme` (dark, from the aqua seed, `primary` pinned to the brand aqua) and `Typography` from the tokens and pass them to `MaterialTheme`. When Material 3 has a component for it, use the M3 component.

## Structure

- `Scaffold` per screen with a `TopAppBar` (Home: app name + profile avatar; tab roots: medium / large top app bar that collapses on scroll; detail: transparent bar over the backdrop).
- `NavigationBar` with Home, Search, Library, Downloads — labels always visible, pill indicator in `accent-soft`. Medium / expanded widths (tablets): the same four destinations move to the top, centered in the top app bar (custom row over the content, active item in an `accent-soft` pill) — no `NavigationRail`, matching the iPad's top tab bar.
- Navigation Compose with one back stack per destination; system back and **predictive back** (`android:enableOnBackInvokedCallback="true"`) on every screen, sheet and the player.
- Edge-to-edge (`enableEdgeToEdge()`); pad with `WindowInsets`, never fake status-bar strips. Dark always — no dynamic color.

## Components to use

| Need | Use |
| --- | --- |
| Play / Resume | `Button` (filled, `primary`); 56dp on hero and detail |
| Secondary action | `FilledTonalButton`; tertiary `TextButton`; icons `IconButton` |
| Quick actions on a card | Long-press → `ModalBottomSheet` with the actions as list items |
| Overflow | `⋮` `IconButton` → `DropdownMenu` |
| Home category buttons (Shows, Movies, New, Categories) | `LazyRow` of M3 `AssistChip`s (filled, no outline) under the top app bar, never selected. Categories carries a trailing dropdown arrow and opens a `ModalBottomSheet` with the player's `OptionList`, no search; the others open their page |
| Audio / subtitles | `ModalBottomSheet` |
| Season buttons (series detail) | `LazyRow` of `FilterChip`s, selected = `accent-soft`; `rememberLazyListState` scrolled to the next-up season on open |
| Home hero (optional) | A single rounded card, never a carousel or auto-advancing (see `screens.md` **Home hero**) |
| Messages | `Snackbar` with an action ("Try again" for a failed write); the action is plain text, not aqua |
| Casting | Google Cast button + MediaRouter |
| Player skip buttons | clickable 48 dp buttons with a generic arrow glyph and the seconds as text over it (seconds from the profile's skip settings; 10 back, 10 forward by default), play / pause between them. A press turns the arrow about 30° its way and springs it back |
| Skip feedback | `SeekRipple`: Android's double-tap seek look, a half circle on the side the video moved with a ripple per tap, three running chevrons and the total skipped so far ("30s"); shown for buttons and double-taps alike |
| Settings rows | Toggle: a `Switch` row. Choice: a `ChoiceRow`, a menu (`DropdownMenu`) with the value at the right, never a sub-page. Link: a row with a chevron to a sub-page. Rows sit in rounded `surfaceContainerLow` cards with a leading tonal icon circle and group titles in aqua text |
| Quality setting | the one checklist: inline rows in its own section (title once, footer below), the chosen row an `accent-soft` pill with no tick |
| Settings on tablets | from the 600dp tablet breakpoint a plain two-column layout (root left, page right, detail capped at about 640 and centred), the selected root row an `accent-soft` pill that follows the tapped row. Not `material3-adaptive`. Below it, push navigation |
| Skip intervals setting | the settings `ChoiceRow` (Skip back 5 to 30 s, Skip forward 10 to 60 s), on the Skipping and autoplay page |
| Buffer ahead setting | the same `ChoiceRow` (Auto, 10 to 60 s, default 20 s), in its own group on Skipping and autoplay. Player: `DefaultLoadControl.setBufferDurationsMs`; the scrubber's buffered segment reads `player.bufferedPosition`, white at about 35% under the aqua fill |
| Player | Compose controls over Media3 `PlayerSurface`; `MediaSession` notification; PiP with `setAutoEnterEnabled` on Android 12+ |
| Loading | `LinearProgressIndicator` / skeletons; `CircularProgressIndicator` only in the player |

## Go

- Ripple and state layers for press feedback; `HapticFeedbackType.LongPress` on long-press.
- Tonal elevation (surfaceContainer steps), not shadows.
- Downloads with `WorkManager` + a foreground-service notification showing progress; request notification permission when the first download starts.
- Survive rotation and process death: player position and screen state restored.
- New-title notifications (the Notifications setting, per profile, off by default):
  - Channel "New episodes and movies" (`Strings.addedTitlesChannel`), default importance. Small icon `ic_logo`, monochrome, and no accent colour: aqua never marks status.
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
