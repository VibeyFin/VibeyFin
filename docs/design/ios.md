# iOS & iPadOS

A SwiftUI app that follows the Human Interface Guidelines and adopts Liquid Glass (iOS / iPadOS 26+). Use the `ios` theme and `ios-*` type styles. When the system has a component for it, use the system component — it gets glass, accessibility and future OS refinements for free.

## Structure

- `TabView` with `Tab`s Home, Library, Downloads and `Tab(role: .search)` for Search — iOS shows it as its own glass button. `.tabBarMinimizeBehavior(.onScrollDown)` on Home and Library.
- iPad: `.tabViewStyle(.sidebarAdaptable)` — a top tab bar or a glass sidebar; libraries can appear as sidebar items.
- One `NavigationStack` per tab. The four tab roots hide the navigation bar and draw one `TabHeader`: the page name in `.title2` bold at the left and the profile avatar at the right, so the title keeps its size and place when the tab changes. Search puts the system `UISearchBar` under it (`.searchable` needs the navigation bar). Pushed screens (detail, season, See All) use inline titles over the backdrop.
- iPad with the tab bar at the top (regular width): the system puts that bar in the navigation bar's row, so the four roots keep the navigation bar (empty, compact `.editor` role, no background, and no scroll-edge effect so no root gets a grey band behind it) and the profile avatar takes its trailing slot, with the system's glass capsule behind it switched off on iOS 26 (`tabRootBar`). No title row of ours and no overlay: hiding the bar and drawing over it left the content under the tab bar after a page was opened and closed. An iPad in a narrow window, with the bar at the bottom, keeps the `TabHeader` row.
- The profile avatar is on every tab. A tap opens the system menu as the finger touches down (a `UIButton` with its menu as the primary action; SwiftUI's `Menu` waited for the finger to lift) (Switch profile, Switch server, Settings, Sign out; no Sign out on a kids profile; Settings only offline). There is no double tap on any platform. The tab bar stays the system's: floating Liquid Glass on iOS 26, and its own swipe.
- No in-app mini player: while PiP plays, the player steps aside and the system window is the only mini view. PiP is a per-profile setting.
- Force dark: `.preferredColorScheme(.dark)` at the root. App tint = `accent` (AccentColor in the asset catalog).

## Liquid Glass

Glass is the **navigation and controls layer** floating above content — never content itself.

- System-rendered, no code: tab bar, sidebar, navigation and toolbars, sheets, menus, context menus, alerts, the search field.
- Custom glass where the controls float over media: player buttons (`.buttonStyle(.glass)`, `.glassEffect(in: .circle)`) grouped in a `GlassEffectContainer`; the Continue watching play disc.
- **Player controls are white, never aqua.** Symbols are `.primary` on `.glass`, grouped in one `GlassEffectContainer`; the top-right controls share one glass capsule, not separate floating buttons. Aqua is only the scrubber's progress; the buffered part is white at about 35%.
- **Never** glass on posters, rows, the hero card or text blocks. **Never** tint glass with aqua — the prominent Play button is the filled `.borderedProminent` capsule.
- Content scrolls edge to edge under the bars; let the safe area inset the scroll content instead of adding manual padding.
- Reduce Transparency and Increase Contrast restyle glass automatically — test with both on; our own `scrim`s don't change.

## Components to use

| Need | Use |
| --- | --- |
| Play / Resume | `Button` + `.buttonStyle(.borderedProminent)`, `.buttonBorderShape(.capsule)`, `.controlSize(.large)` |
| Secondary action | `.bordered`; tertiary `.borderless` (tinted) |
| Quick actions on a poster or the hero | A native `.sheet`, not `.contextMenu`: the Trailer row arrives after the server answers and the Download row shows live state, and a menu is static once open (see **Poster menu** in `screens.md`) |
| Episode card actions | `.contextMenu` on the card (View episode, the watched mark); no swipe actions |
| Audio / subtitles | `Menu` / `Picker` in a toolbar or a sheet with `.presentationDetents([.medium, .large])` |
| Season buttons (series detail) | Horizontal `ScrollView` of capsule buttons, selected filled `ink`; `ScrollViewReader` scrolls to the next-up season on open |
| Home hero (optional) | One rounded card, or a paged `TabView` (`.page(indexDisplayMode: .never)`) of them (2 to 5) with a wrapped `UIPageControl` under it, the system's own dots, which VoiceOver adjusts. Not glass. Auto-advance as `guardrails.md` says (see `screens.md` **Home hero**) |
| Home category buttons (Shows, Movies, New, Categories) | Horizontal `ScrollView` of capsule buttons under the Home header: `surface-2` fill with `ink`, never selected; 36pt tall with a 44pt hit area. Not glass. Categories adds a trailing `chevron.down` and opens a `.sheet` with a plain list (medium and large detents), no search; the others open their page. A sheet rather than the player's `Menu`, because the list loads on demand and needs loading, empty and error states. A category page's Sort and Genre are capsules that open the same sheet |
| Share | `ShareLink` |
| Casting | `AVRoutePickerView` (AirPlay) |
| Player skip buttons | `gobackward.<n>` / `goforward.<n>` SF Symbols, `n` from the profile's skip settings (back 10, forward 10 by default) |
| Skip feedback | `SeekFlash`: a "10s" capsule on the scrim by the side the video moved, holding the total of skips in a row; shown for buttons and double-taps alike |
| Settings rows | inset grouped list, small-caps section headers, footers carrying the help text; `Toggle` tinted aqua; a choice is a `Picker` with `.pickerStyle(.menu)`, never a sub-page; a link is a `NavigationLink` row |
| Quality setting | the one checklist: inline rows in its own section (title once, footer below), the chosen row an `accent-soft` pill with no tick |
| Settings on iPad | at regular width `NavigationSplitView`: the root as the sidebar (system selection following the tapped row), the page in the detail column capped at about 640 and centred; the back chevron is in the sidebar only. At compact width it is the phone's `NavigationStack` |
| Skip intervals setting | a `Picker` with `.pickerStyle(.menu)` (Skip back 5 to 30 s, Skip forward 10 to 60 s), on the Skipping and autoplay page |
| Buffer ahead setting | the same `Picker` row (Auto, 10 to 60 s, default 20 s), in its own group on Skipping and autoplay. Player: `AVPlayerItem.preferredForwardBufferDuration`; the scrubber's buffered segment is the end of the `loadedTimeRanges` range holding the current time |
| Player | Custom SwiftUI controls over `AVPlayerLayer` (Jellyfin trickplay and skip segments need it); PiP via `AVPictureInPictureController`; Now Playing via `MPNowPlayingInfoCenter`; while AirPlay is active the PiP button is hidden and a "Playing on <device>" pill shows (`player.md`) |
| Loading | `ProgressView` in the player only; skeletons elsewhere (`.redacted(reason: .placeholder)`) |

## Go

- Keep the interactive edge-swipe back working everywhere; rows start at the margin so they don't fight it.
- Use semantic colors and Dynamic Type styles (`Color(.secondaryLabel)`, `.font(.footnote)`) — not hex values or fixed sizes.
- Haptics: `.sensoryFeedback` on long-press and on scrub snapping to a chapter.
- Status-bar tap and tab re-tap scroll to top.
- Background downloads with a background `URLSession`; exclude media from iCloud backup.
- New-title notifications (the Notifications setting, per profile, off by default): local notifications through `UNUserNotificationCenter`, one per new movie or episode, text from `Strings`, the profile's name only when several profiles on the device get notifications. A show's episodes share a `threadIdentifier` (per server and show), so iOS stacks them; new movies share one stack per server. Permission is asked when Notifications is switched on; a refusal switches it back off. The check is a `BGAppRefreshTask`, best effort: iOS decides when, or whether, it runs, and the page says to expect a delay. At most once per title. Tapping opens the title on Home only for the profile signed in now (see guardrails: never a sign-in, never past a PIN).
- Test VoiceOver, Dynamic Type up to AX5, Reduce Motion, Reduce Transparency, Increase Contrast; iPhone SE-size, Pro Max, iPad in Split View.

## Don't

- Don't fake glass with blur materials where a system component exists, and don't stack glass on glass.
- Don't add a hamburger menu, FAB, Android-style snackbars or an in-app "Exit". A failed write that needs a word ("Couldn't mark this as watched") uses `failureBanner`: a non-modal capsule on `.regularMaterial` at the bottom with "Try again", dismissed after about six seconds.
- Don't use Material icons, `⋮` or `←` — SF Symbols, `…`, `‹`.
- Don't put Settings in a tab — it's under the profile avatar on Home.
- Don't leave the player rotated when returning to a portrait screen; hide the home indicator with the controls (`.persistentSystemOverlays(.hidden)`).
