VibeyFin is a dark, artwork-first client for a personal Jellyfin library. **One brand, two native apps:** the iOS / iPadOS app is SwiftUI and follows Apple's Human Interface Guidelines (Liquid Glass included); the Android app is Jetpack Compose and follows Material 3. They share a Kotlin Multiplatform core — Jellyfin API, repositories, playback reporting, downloads, and the state each screen renders — never UI code.

Further sections: **UX guardrails** (behavior both apps must share), **iOS & iPadOS** and **Android** (each platform's native UI rules), **Jellyfin data → UI** (which API field drives which pixel). Every component preview shows both platforms side by side; the two color themes *are* the two platforms.

## Principles

1. **Artwork leads, chrome recedes.** Screens are mostly posters and backdrops on `bg`. No decorative panels, gradients or borders around content.
2. **Resume beats discover.** Continue watching sits above the fold, and tapping it plays — no detour.
3. **Native first.** Each app uses its platform's own components, type, navigation, motion and icons. If the platform has a control for it, use that control; build custom only where Jellyfin needs it (posters, rows, the player).
4. **One brand across both.** What stays identical: the name, electric aqua, dark-only, artwork ratios and treatment, the brand display face, voice, and every rule in **UX guardrails**.
5. **Calm motion.** Native transitions only; nothing bounces for effect, spins or auto-rotates.

## What is shared vs native

| Shared (brand + behavior) | Native per platform |
| --- | --- |
| Name, wordmark, `brand-display` face | UI framework (SwiftUI / Compose M3) |
| `accent` aqua, dark-only, `scrim` over artwork | Surfaces, text and line colors (Apple semantic colors / M3 scheme) |
| Artwork ratios, `radius-artwork`, BlurHash placeholders | Type scale (SF Pro Dynamic Type / M3 Roboto) |
| Screen structure, row order, guardrails, copy content | Buttons, sheets, menus, navigation, tab bar, gestures |
| Kotlin Multiplatform core and its state | Icons (SF Symbols / Material Symbols), motion, haptics |

## Voice

- Same words on both; casing follows the platform. iOS: title case for buttons, tabs and titles ("Continue Watching", "Mark as Watched", "Skip Intro"). Android: sentence case everywhere ("Continue watching", "Mark as watched", "Skip intro").
- Buttons are verbs, specific when we know more: "Play", "Resume", "Play S2:E4".
- Time and episode shorthand: `1h 52m`, `38m left`, `S2:E4`; tabular numerals for clocks.
- Use metadata exactly as Jellyfin returns it. No emoji, no exclamation marks. Errors say what happened and what to do: "Can't reach your server. Check your connection, then try again."

## Color

Dark only, on both platforms (force dark appearance; ignore the system light mode). The two themes in the tokens are the two platforms; each token's note names the native API to use — on device, use that API, not the hex.

- Screens on `bg`; lists and sheet bodies on `surface-1`; fields, menus and missing-artwork tiles on `surface-2`. iOS reads Apple's dark semantic colors (`systemBackground` is true black); Android reads the M3 dark scheme generated from the aqua seed (slightly aqua-tinted neutrals).
- Text: `ink`, `ink-muted` for metadata and synopsis, `ink-subtle` only on `bg` / `surface-1`.
- `accent` — electric aqua — fills the one primary action per screen (Play / Resume) and playback progress. iOS: set it as the app's AccentColor, so the system tints tab selection, links and borderless buttons natively. Android: it is `primary`; tonal surfaces use `accent-soft` / `on-accent-soft` (secondaryContainer).
- Over artwork or video, text and controls sit on `scrim` (synopsis on `scrim-strong`) — identical on both.
- Downloaded state is `positive` (= `ink`) with the filled icon; `danger` (systemRed / M3 error) only for errors, always with a word or icon. Aqua never signals status.
- No dynamic theming: no Material You wallpaper colors, no artwork-derived UI colors (only the BlurHash placeholder).

## Type

System fonts for UI on each platform; one brand face.

- `brand-display` (Bricolage Grotesque, bundled) only for the hero title fallback when an item has no Logo image, the wordmark and onboarding.
- iOS: SF Pro via Dynamic Type text styles (`ios-*` styles) — always the semantic style (`.headline`, `.footnote`), never fixed sizes, so text scales.
- Android: the M3 type scale (`m3-*` styles) with Roboto via `MaterialTheme.typography`; respect font scale (non-linear on Android 14+).
- Mapping of roles: row header `ios-title3` / `m3-title-large`; card title `ios-subheadline` / `m3-title-small`; metadata `ios-footnote` / `m3-body-small`; button `ios-headline` / `m3-label-large`.

## Space and layout

- 4-unit grid `space-1` … `space-7` (pt / dp). Phone margins 16 on both; iPad 20; Android medium width 24.
- Rows scroll edge to edge, first card at the margin, a next card always peeks. Rows `space-5` apart.
- Width classes drive layout on both (SwiftUI size classes / Compose `WindowSizeClass`): posters 112 → 140 → 168.
- Hit targets: `touch-ios` 44pt, `touch-android` 48dp.

## Artwork

- Ratios: poster 2:3, thumbnail and backdrop 16:9, phone hero 4:5 crop. `radius-artwork` (8) on both (continuous on iOS).
- BlurHash color block → image fades in. Missing image: `surface-2` tile with the title. Never a broken-image icon.
- Request images at rendered size × density. One badge per card, top-left; watched check top-right; progress on the bottom edge.

## Iconography

- iOS: SF Symbols, matching the text weight; `.fill` for selected tabs and toggled states.
- Android: Material Symbols Rounded, 24dp, weight 400; filled for the active destination.
- Same meaning per action on both (play, add, download, more…), never the other platform's shape: `‹` vs `←`, `…` vs `⋮`, AirPlay vs Cast, square-and-arrow vs share-nodes. The icons in previews are simplified stand-ins.

## Architecture

- `shared/` (Kotlin Multiplatform): Ktor + Jellyfin API, repositories, caching, playback session and progress reporting, download queue, and one state holder per screen exposing `StateFlow`s.
- `iosApp/` (SwiftUI): consumes the shared state holders (via SKIE or KMP-NativeCoroutines for Flows and suspend functions), owns navigation (`TabView` + `NavigationStack`), AVPlayer playback.
- `androidApp/` (Jetpack Compose + Material 3): consumes the same state holders, Navigation Compose, Media3 playback.
- Tokens: generate a Swift asset catalog / `Color` extension and a Kotlin M3 `ColorScheme` + `Typography` from `tokens.json` — never hand-copy values.
