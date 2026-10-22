# iOS & iPadOS

A SwiftUI app that follows the Human Interface Guidelines and adopts Liquid Glass (iOS / iPadOS 26+). Use the `ios` theme and `ios-*` type styles. When the system has a component for it, use the system component — it gets glass, accessibility and future OS refinements for free.

## Structure

- `TabView` with `Tab`s Home, Library, Downloads and `Tab(role: .search)` for Search — iOS shows it as its own glass button. `.tabBarMinimizeBehavior(.onScrollDown)` on Home and Library.
- iPad: `.tabViewStyle(.sidebarAdaptable)` — a top tab bar or a glass sidebar; libraries can appear as sidebar items.
- One `NavigationStack` per tab. Tab roots (Library, Downloads) use large titles; Home draws its own header with the profile avatar; pushed screens (detail, season, See All) use inline titles over the backdrop.
- Mini player (while something plays in PiP or audio continues): `.tabViewBottomAccessory`.
- Force dark: `.preferredColorScheme(.dark)` at the root. App tint = `accent` (AccentColor in the asset catalog).

## Liquid Glass

Glass is the **navigation and controls layer** floating above content — never content itself.

- System-rendered, no code: tab bar, sidebar, navigation and toolbars, sheets, menus, context menus, alerts, the search field.
- Custom glass where the controls float over media: player buttons (`.buttonStyle(.glass)`, `.glassEffect(in: .circle)`) grouped in a `GlassEffectContainer`; the Continue watching play disc.
- **Never** glass on posters, rows, the hero card or text blocks. **Never** tint glass with aqua — the prominent Play button is the filled `.borderedProminent` capsule.
- Content scrolls edge to edge under the bars; let the safe area inset the scroll content instead of adding manual padding.
- Reduce Transparency and Increase Contrast restyle glass automatically — test with both on; our own `scrim`s don't change.

## Components to use

| Need | Use |
| --- | --- |
| Play / Resume | `Button` + `.buttonStyle(.borderedProminent)`, `.buttonBorderShape(.capsule)`, `.controlSize(.large)` |
| Secondary action | `.bordered`; tertiary `.borderless` (tinted) |
| Quick actions on a card | `.contextMenu { … } preview: { … }` |
| Episode row actions | `.swipeActions` (Mark as Watched, Download) |
| Audio / subtitles | `Menu` / `Picker` in a toolbar or a sheet with `.presentationDetents([.medium, .large])` |
| Season buttons (series detail) | Horizontal `ScrollView` of capsule buttons, selected filled `ink`; `ScrollViewReader` scrolls to the next-up season on open |
| Home category buttons (Shows, Movies, New, Categories) | Horizontal `ScrollView` of capsule buttons under the Home header: `surface-2` fill with `ink`, never selected; 36pt tall with a 44pt hit area. Not glass. Categories adds a trailing `chevron.down` and opens a `.sheet` with a plain list (medium and large detents), no search; the others open their page. A sheet rather than the player's `Menu`, because the list loads on demand and needs loading, empty and error states. A category page's Sort and Genre are capsules that open the same sheet |
| Share | `ShareLink` |
| Casting | `AVRoutePickerView` (AirPlay) |
| Player | Custom SwiftUI controls over `AVPlayerLayer` (Jellyfin trickplay and skip segments need it); PiP via `AVPictureInPictureController`; Now Playing via `MPNowPlayingInfoCenter` |
| Loading | `ProgressView` in the player only; skeletons elsewhere (`.redacted(reason: .placeholder)`) |

## Go

- Keep the interactive edge-swipe back working everywhere; rows start at the margin so they don't fight it.
- Use semantic colors and Dynamic Type styles (`Color(.secondaryLabel)`, `.font(.footnote)`) — not hex values or fixed sizes.
- Haptics: `.sensoryFeedback` on long-press and on scrub snapping to a chapter.
- Status-bar tap and tab re-tap scroll to top.
- Background downloads with a background `URLSession`; exclude media from iCloud backup.
- Test VoiceOver, Dynamic Type up to AX5, Reduce Motion, Reduce Transparency, Increase Contrast; iPhone SE-size, Pro Max, iPad in Split View.

## Don't

- Don't fake glass with blur materials where a system component exists, and don't stack glass on glass.
- Don't add a hamburger menu, FAB, Android-style snackbars or an in-app "Exit". A failed write that needs a word ("Couldn't mark this as watched") uses `failureBanner`: a non-modal capsule on `.regularMaterial` at the bottom with "Try again", dismissed after about six seconds.
- Don't use Material icons, `⋮` or `←` — SF Symbols, `…`, `‹`.
- Don't put Settings in a tab — it's under the profile avatar on Home.
- Don't leave the player rotated when returning to a portrait screen; hide the home indicator with the controls (`.persistentSystemOverlays(.hidden)`).
