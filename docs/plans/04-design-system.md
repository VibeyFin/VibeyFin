# Plan 04: Integrate the VibeyFin design system (native per platform)

Goal: replace the current ink/amber/teal look with the new design system, each platform
following its own conventions: SwiftUI with the Human Interface Guidelines and Liquid Glass on iOS,
Jetpack Compose with Material 3 on Android. Both share the brand (electric aqua, dark only, artwork
treatment, one display face) and every rule in the UX guardrails. This is the base: tokens, theme,
the custom components and the restyle of every screen that exists today. New screens (Home rows,
Detail, Player, Search, Downloads) come with their features.

Source: the "VibeyFin" design system artifact (https://claude.ai/artifact/HeWVw1EzAbYcvssURJ2rNc),
version `1792434133-0ab2` (2026-10-19), the split iOS / Android revision. Files used:
`project/tokens.json` (v2), `README.md`, `ios.md`, `android.md`, `guardrails.md`,
`jellyfin-mapping.md`, `components/*/README.md`. Re-read the artifact if it has changed.

This revision supersedes my first draft, which assumed a shared Compose UI. The split design
already matches CLAUDE.md's "native UI, shared logic", so the earlier conflicts about shared UI,
icons, the OLED theme and the Figtree font are gone.

## What the design says, in one table

| | iOS / iPadOS (SwiftUI) | Android (Compose M3) |
|---|---|---|
| Colours | Apple dark semantic colours (`systemBackground`, `label`, `secondaryLabel`, ...); only brand values are ours | M3 dark `ColorScheme` from the aqua seed, values in tokens, `primary` = aqua |
| Type | SF Pro Dynamic Type text styles, no fixed sizes | M3 `Typography`, Roboto |
| Brand face | Bricolage Grotesque, only for the wordmark, hero title without a Logo image, onboarding | same |
| Shape | artwork radius 8 continuous; capsules for buttons | M3 shapes; artwork radius 8 |
| Chrome | `TabView` with Liquid Glass on iOS 26+, `NavigationStack` per tab | `Scaffold`, `NavigationBar`, predictive back |
| Accent | AccentColor `#3fe0d0` tints the tab, links, borderless buttons; Play is `.borderedProminent` capsule | `primary` `#3fe0d0`; Play is filled `Button` |
| Text casing | Title case ("Continue Watching") | Sentence case ("Continue watching") |
| Icons | SF Symbols | Material Symbols Rounded |

## Where the design and CLAUDE.md still disagree (sign-off needed before Phase 1)

| # | Conflict | Recommendation |
|---|---|---|
| 1 | **Palette.** New: aqua `#3fe0d0`, true-black iOS, aqua-tinted Android neutrals. CLAUDE.md: blue-ink `#10121A`, amber, teal "availability". | Adopt the new palette; rewrite CLAUDE.md "Design language". Teal availability goes away (aqua never signals status; downloaded uses `ink` plus the filled icon). Revisit when Seerr lands. |
| 2 | **Casing.** iOS wants title case, Android sentence case. CLAUDE.md: sentence case, all text in shared `Strings`. | Keep `Strings` in sentence case. On iOS add one Swift helper `Text.titleCased()` (Apple title-case rules) applied only to button, tab and title labels; body text and errors stay as is. No literals in Swift. The alternative, parallel iOS strings, doubles every string. |
| 3 | **Typography.** CLAUDE.md: "heavy, tight type for titles". Design: system fonts, brand face only for the wordmark and hero fallback. | Follow the design. Remove the heavy-title helpers (`Type.kt`, `Text.vibeTitle`). |
| 4 | **Bridging.** The design's architecture section suggests SKIE or KMP-NativeCoroutines. CLAUDE.md: our own typed facades, no new dependencies. | Keep the facades. Do not add either library. |
| 5 | **Token generation.** The design says generate Swift and Kotlin from `tokens.json`, never hand-copy. | Hand-write the two small token files, commit `design/tokens.json`, and fail a check when they drift (Phases 1 and 4). A generator is not worth a build step for about 25 colours. Say if you want the generator anyway. |
| 6 | **iOS deployment target.** CLAUDE.md: iOS 16+. Liquid Glass and `Tab(role: .search)` need iOS 26 / 18. | Keep 16+. Gate glass and the new tab API with `#available`; below that use the classic `TabView` with the same tint. |

Also left alone: launcher and app icons stay amber until a separate icon task.

## Mapping onto this repo

| Design | Android (`androidApp/.../app/theme`, `.../app/components`) | iOS (`iosApp/iosApp`) |
|---|---|---|
| Tokens | `VibeyFinTokens` (explicit M3 role values, extras via `LocalVibeyFinExtras`) | `Theme.swift`: brand values, spacing, radii; semantic colours referenced by name |
| Brand font | `res/font` Bricolage Grotesque 800 | `iosApp/Fonts`, `UIAppFonts` in `Info.plist` |
| Custom components | Compose files in `app/components` | SwiftUI views, one file per component group |
| Copy | `Strings` in `shared/` | same, plus `titleCased()` |

## Phase 1: Source of truth and docs

- Add `design/tokens.json` (verbatim) and `docs/design/` with the README, ios, android, guardrails
  and jellyfin-mapping pages, plus a short `docs/design/README.md` naming the artifact URL, version
  and copy date.
- Rewrite CLAUDE.md "Design language" (palette, aqua for the one primary action, dark only, native
  per platform, brand face rules, casing rule from conflict 2) and the Stack icon line. Leave the
  architecture rules untouched. Point at `docs/design/`.
- The drift test moves to Phase 2: it needs the Kotlin constants, which don't exist until then.
- Done when: `design/tokens.json` parses, `docs/design/` holds the pages, CLAUDE.md describes the
  new design language and contradicts nothing else in the file. Commit:
  `task: add design tokens and design docs`.

## Phase 2: Android theme

- Font: Bricolage Grotesque ExtraBold static TTF from Google Fonts into `res/font`, with the OFL
  licence next to it.
- Replace `Color.kt` with `VibeyFinTokens`: the `android` column of `tokens.json` for every colour,
  plus `space1..7`, radii (`radiusArtwork` 8, `m3` shapes), sizes (`touchAndroid`, `buttonAndroid`,
  `posterW`, `thumbW`) and `durHero`, `durBase`.
- `Theme.kt`: a dark `ColorScheme` with every role set explicitly (bg to `background` and `surface`,
  surface-1/2/3 to `surfaceContainerLow/Container/High` and `Highest`, line to `outlineVariant`,
  line-strong to `outline`, ink to `onSurface`, ink-muted to `onSurfaceVariant`, accent to `primary`,
  on-accent to `onPrimary`, accent-soft and on-accent-soft to `secondaryContainer` and
  `onSecondaryContainer`, danger to `error`). Values with no M3 role (`ink-subtle`, `scrim`,
  `scrim-strong`, `progress-track`, `focus`) go through `LocalVibeyFinExtras`.
- `Type.kt`: standard M3 `Typography`, with the design's exact sizes for the eight `m3-*` styles;
  drop the heavy-title overrides. Add `BrandDisplay` (`brand-display` 40/44, weight 800, -0.02em).
- Update `res/values/colors.xml` and `themes.xml` (window, status and navigation bar) to `bg`
  `#0e1514`.
- Drift test in `androidApp/src/test` (write it first, watch it fail): parse `design/tokens.json`
  with `kotlinx.serialization.json` (already an `androidApp` dependency) and assert every
  `android`-theme colour, spacing, radius and size token equals its Kotlin constant. It needs
  `testImplementation(libs.kotlin.test)` and JUnit 4 on the unit-test classpath; ask before adding
  anything beyond `kotlin-test` and its JUnit variant.
- Done when: `./gradlew :androidApp:assembleDebug` and the drift test pass (and the test fails after
  changing one hex value, then passes when reverted), and on an emulator no
  amber or blue-ink remains. Commit: `feat: apply design tokens to the Android theme`.

## Phase 3: Android components and screens

Use M3 components wherever the design says so; build only what M3 lacks, in `app/components`:

- Buttons: filled `Button` for Play/Sign in (one per screen, 56dp on hero and detail), 
  `FilledTonalButton` secondary, `TextButton` ghost. No custom button.
- Custom, reading `MaterialTheme` plus `LocalVibeyFinExtras`: `PosterCard` (2:3, radius 8, one
  badge top-left, check top-right, progress on the bottom edge), `Row` (`m3-title-large` header,
  optional "See all", next card peeks at least 24dp), `Badge`, `Progress`, `EpisodeItem`,
  `ArtworkPlaceholder` (`surface-2` tile with the title, replacing `Artwork.kt`'s placeholder).
- Text fields: M3 `OutlinedTextField`/`TextField` with `line-strong` borders; replace `Fields.kt`
  styling.
- Restyle `servers`, `addserver`, `whoswatching`, `startup`, `home`, `children`, `item` and
  `settings`. Settings stays reachable from Home's profile avatar, not a tab.
- New text (accessibility labels, "Downloaded") goes into `Strings` first. Card label
  "{title}, {year}, {n}% watched" via a `Strings` function.
- Done when: `./gradlew :shared:allTests :androidApp:assembleDebug` pass; on an emulator at 200%
  font scale nothing clips, each screen has at most one filled aqua button, TalkBack reads a card
  as its label. Commit per component group, `feat: ...`.

## Phase 4: iOS theme

- Font: same Bricolage Grotesque file into `iosApp/iosApp/Fonts` (synchronized folder, no project
  edit), listed in `UIAppFonts`; confirm the PostScript name before use.
- `Assets.xcassets`: add `AccentColor` (`#3fe0d0`) and set the app to dark. Update
  `LaunchBackground.colorset` to black.
- `Theme.swift`: remove `Palette`. Add `Brand` (`accent`, `onAccent`, `accentSoft`, `onAccentSoft`,
  `scrim`, `scrimStrong`, `progressTrack`), `Space`, `Radius`, and `Font.brandDisplay`.
  Everything else uses system semantic colours and Dynamic Type styles (`.headline`, `.footnote`,
  ...), never hex or fixed sizes. Root view sets `.preferredColorScheme(.dark)`.
- Add `Text.titleCased()` (conflict 2) in `Theme.swift`.
- `scripts/check-swift-tokens.sh`: compare the brand hex values in `Theme.swift` and the
  `AccentColor` asset with `design/tokens.json`'s `ios` column. Fails on drift.
- Verification limit (as plan 02): no Xcode here. Only `swiftc -parse` and the script run.
- Done when: all Swift files parse and the script passes (and fails on a deliberate edit).
  Commit: `feat: apply design tokens to the iOS theme`.

## Phase 5: iOS components and screens

- System components first: `.borderedProminent` capsule `.controlSize(.large)` for Play and Sign
  in, `.bordered` secondary, `.borderless` ghost, `Menu`, sheets with detents. Delete
  `PrimaryButtonStyle` and `OutlineButtonStyle` from `Components.swift`.
- Custom SwiftUI: `PosterCard`, `PosterRow`, `Badge`, `ProgressBar`, `EpisodeItem`, placeholder
  tile. Glass never on content; do not fake glass with blur.
- Restyle every existing view; the `Card` in `Components.swift` becomes a `surface-1`
  (`secondarySystemBackground`) container. Content runs under the bars (safe area handles insets).
- SF Symbols with `.fill` for selected and toggled states; `‹`, `…` glyph conventions via system.
- Done when: all Swift files parse and the script passes. Not built or run on a simulator. Commit
  per component group.

## Out of scope (deferred to feature plans)

- Four-tab shell (`TabView` + Liquid Glass on iOS 26, `NavigationBar`/`NavigationRail` on Android):
  Search, Library and Downloads have no screens. Build it with the first of them.
- Hero, `ContinueCard`, Continue watching and Next up rows (roadmap Phase 2 data).
- `PlayerControls`, skip buttons, trickplay, mini player (plan 03 Phases B and C, deferred).
- Long-press quick actions (context menu / bottom sheet), swipe actions, haptics, casting, PiP.
- BlurHash image loading (needs the Coil / iOS loader decision CLAUDE.md says to ask about).
- App icon recolour.

## Risks

- Android neutrals are generated from the seed, so M3 components I haven't set explicitly (for
  example `surfaceTint`, `inverseSurface`) could show off-palette tones. Set every role and check
  dialogs, menus and the date picker once.
- Contrast: spot check `ink-muted` on `surface-3` and `on-accent-soft` on `accent-soft` when the
  Android screens first render.
- iOS cannot be built here, so Phase 5 stays unverified until someone builds it in Xcode.
