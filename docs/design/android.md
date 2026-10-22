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
| Home category buttons (Shows, Movies, New, Categories) | `LazyRow` of M3 `AssistChip`s under the top app bar, never selected. Categories carries a trailing dropdown arrow and opens a `DropdownMenu` anchored to the chip, with a search field above a scrolling list; the others open their page |
| Audio / subtitles | `ModalBottomSheet` |
| Season buttons (series detail) | `LazyRow` of `FilterChip`s, selected = `accent-soft`; `rememberLazyListState` scrolled to the next-up season on open |
| Featured row (optional) | `HorizontalMultiBrowseCarousel` — user-scrolled, never auto-advancing |
| Messages | `Snackbar` with an action ("Try again" for a failed write); the action is plain text, not aqua |
| Casting | Google Cast button + MediaRouter |
| Player | Compose controls over Media3 `PlayerSurface`; `MediaSession` notification; PiP with `setAutoEnterEnabled` on Android 12+ |
| Loading | `LinearProgressIndicator` / skeletons; `CircularProgressIndicator` only in the player |

## Go

- Ripple and state layers for press feedback; `HapticFeedbackType.LongPress` on long-press.
- Tonal elevation (surfaceContainer steps), not shadows.
- Downloads with `WorkManager` + a foreground-service notification showing progress; request notification permission when the first download starts.
- Survive rotation and process death: player position and screen state restored.
- Test TalkBack, font scale 200%, "Remove animations", a compact phone, a foldable / tablet.

## Don't

- Don't use iOS shapes or glyphs: no `‹` chevrons, `…` overflow, action sheets, or glass / blur chrome.
- Don't use Material You dynamic color or a light theme.
- Don't use swipe-to-act on list rows for primary actions — long-press and `⋮` instead.
- Don't intercept back to show "Press again to exit".
- Don't run downloads without a visible notification.
