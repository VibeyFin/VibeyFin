# Design review: living findings

A preview of UI/UX do's and don'ts, taken while the app is still being built. Source: a read of the
code on `develop` (d276170) against `docs/design/`. Nothing here was built, run or seen on a device.
Many items are **not built yet** rather than wrong; they are listed so they are not forgotten.

Update this file when the rest of the UI lands: re-check each item, set its status, and add what
new screens show. Status: `open`, `planned` (a phase or roadmap item covers it), `fixed`, `dropped`.

Last reviewed: 2026-10-21, against `develop` f295a4d plus the clean-up that fixed #7, #8, #9, #12, #13
and most of #15. Android was run on the emulator after it; the SwiftUI side was parsed, and
`Artwork.swift` type-checked against a stub, but never built or run (no Xcode).

## Do

- **Keep colours and type from tokens.** `VibeyFinTokens` and `Theme.swift` mirror `tokens.json` with a
  drift check; Android sets every `ColorScheme` role so Material's purple never shows.
- **Keep aqua for the primary action and progress only.**
- **Use the platform's own control where one exists.** iOS 26 uses real Liquid Glass (`.glass`,
  `GlassEffectContainer`) with a scrim disc before that; Android uses `OutlinedTextField`, `DropdownMenu`.
- **Keep copy in `Strings`.** Native screens hold no literals; iOS title-cases through `titleCased()`.
- **Keep touch targets honest:** 48dp to touch and 40dp to look at on Android (`ChipButton`); 44pt on iOS.

## Don't

- **Don't hand-copy values into screens.** Spacing goes through `Space`, button heights through `Size`
  (`button-android-lg` is the 56 dp one). A size that only one component uses stays a named constant in
  that file. *Fixed on Android; the avatar tints are now `avatar-1` to `avatar-6` in `tokens.json` and
  both drift checks.*
- **Don't add hues.** The six avatar tints add blue, purple, green, brown, pink and olive to a
  one-accent brand. They are tokenised now, not removed, so the Who's watching tiles look as before.
  Dropping them for `surface-3` with an initial is still an option (`dropped` vs `open`: your call).
- **Don't use spinners for loading.** Skeletons everywhere except the player.
- **Don't build a clickable without semantics.** *Fixed:* the Android profile button is a button named
  for the user, as on iOS.
- **Don't invent a third typeface.** *Fixed:* the iOS Quick Connect code is plain `.largeTitle` heavy.
- **Don't draw custom text-field chrome on iOS.** `FormField` is a hand-drawn rounded rect and stroke.
  See #11 for why it is still there.
- **Don't duplicate components.** *Fixed:* `ChipButton` is one component; the back button, the item "⋮"
  and the player's track button use it.
- **Don't mix icon sets.** See #15: everything on Android is Material Icons (filled), not Symbols Rounded.

## Findings

| # | Area | Finding | Severity | Status |
|---|---|---|---|---|
| 1 | Item | Not the specified hero. Spec: full-bleed backdrop under the status bar, Logo or `brand-display` title, scrim gradient, 3-line overview with "More". Built: an inset 16:9 image with 8dp corners in a column, `titleLarge` title. On Android `safeDrawingPadding()` on the parent blocks full-bleed. | High | open |
| 2 | Item | Play now says "Resume · 38m left" with a "start over" arrow (watch-status phase). Still missing: "Play S2:E4" for a series, and the secondary actions (My List, Like, Trailer, Download). | High | open (label done) |
| 3 | Home | Continue watching is built. Still no hero or tab bar, and Home shows a greeting ("Hi, demo") where the spec says app name + avatar. | High | planned |
| 4 | Tablets | No screen adapts. Only `screenMargin()` reads width (16 or 24dp). The Item backdrop stretches full width with unbounded text; no left-column hero. | High | open |
| 5 | Grids | Minimums are ad hoc: 150 Home, 130 Children, 120 Who's watching. Spec posters: 112, 140, 168. Left for the width-class work (#4): a poster minimum without a width class gives 3 columns of tiny library tiles on Home. | Medium | open |
| 6 | Loading | Spinners on Android (Home libraries, Item, Who's watching, Add server, Children) and iOS (`LoadingIndicator`, `SearchingLabel`, Who's watching). The Continue watching row already uses a skeleton. | Medium | planned (state audit, roadmap) |
| 7 | Tokens | Avatar tint hex outside `tokens.json`. | Medium | fixed |
| 8 | A11y | Android profile button has no role or label. | Medium | fixed |
| 9 | Android | "Libraries" (and "Continue watching") are `labelLarge` muted; spec says `titleLarge` for row headers. | Medium | fixed |
| 10 | Android | Each screen builds its own title row. Spec: `Scaffold` + `TopAppBar` (large, collapsing on tab roots, transparent over detail). | Medium | open |
| 11 | iOS | `FormField` is custom text-field chrome. Not changed: the stock `.roundedBorder` style has no edge, and `line-strong` says a text field's border must be seen at 3:1. Needs a decision: keep the border (and amend this note) or take the stock look. | Medium | open (decision) |
| 12 | Artwork | BlurHash placeholder and a `dur-base` fade-in, on both platforms (Android: Coil `placeholder` + `crossfade`; iOS: `AsyncImage` over the decoded hash). No fade with "Remove animations" / Reduce Motion. | Low | fixed |
| 13 | Player | Time left now starts with a true minus (U+2212). The design's `38m left` form is not used in the player, only on the Resume button and cards; the clock form is the scrubber's convention. | Low | fixed (minus); form: dropped |
| 14 | Player | Gestures, skip intro, speed, trickplay not built (Up next is). Check scrim contrast on bright video and the scrubber thumb (Android aqua M3 thumb, iOS thumbless bar that thickens). | Low | planned (Phase 3) |
| 15 | Code | Spacing literals, the 56 and 40 dp heights, the duplicated chip and `ink` vs `onBackground` in `ItemScreen` are fixed. Not fixed: the icon sets. Android uses `Icons.Filled.*` and four vector drawables from the same Material Icons set, while the doc says Material Symbols Rounded 400; changing it means vendoring about a dozen Symbols paths or adding `material-icons-extended` (a new dependency, so ask first). The simpler fix is to change the doc. | Low | open (decision) |

## Suggested order when work resumes

1. Item hero (sets the pattern for the rest).
2. Width-class layout (takes #5 with it).
3. Skeleton and state audit.

## To re-check once the rest is ready

- Home rows, hero and tab bar against `guardrails.md`.
- Detail screens at phone and tablet widths.
- Search, Library and Downloads (not yet reviewed).
- Player controls against the playback guardrails.
- Screenshots on a phone, a tablet, font scale 200% and with Reduce Motion / Remove animations.
