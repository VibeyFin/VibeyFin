# Devices

What VibeyFin is designed for, how the mockups are named and grouped, and what is still missing per device. TV is planned but not designed yet; this page is where it slots in.

## Device targets

| Device | iOS family | Android family | Input | Width class | Status |
| --- | --- | --- | --- | --- | --- |
| Phone | **iOS** — iPhone (SwiftUI) | **Android mobile** (Compose M3) | Touch | compact | Designed |
| Tablet | **iPadOS** — iPad (SwiftUI) | **Android tablet**, foldables open (Compose M3) | Touch, pointer, keyboard | regular / medium–expanded | Designed: Home, Detail, Player, Settings, Search |
| TV | tvOS — Apple TV | Google TV / Android TV | Remote, focus | 10-foot | Planned, not designed |

The device follows the **width class**, not the hardware: an iPad in Split View at compact width, or a folded phone, uses the phone layout; an unfolded foldable uses the tablet layout.

## How the mockups are organized

- **Screens** are grouped per feature — **Screens · Home**, **Screens · Detail**, **Screens · Player**, **Screens · Settings** — and named `<Feature>[<Variant>]<Device>`: `HomePhone`, `CategoryPagePhone`, `HomeTablet`, `DetailMoviePhone`, `DetailSeriesPhone`, `DetailTablet`, `PlayerPhone`, `PlayerTablet`, `SettingsPhone`, `SettingsTablet`. A new device is a new card in the same group: `HomeTablet`, later `HomeTV`.
- Each screen card holds one device for both platform families; the page's theme switch picks which one you see — **iOS / iPadOS** shows the iPhone or iPad mockup, **Android** the Android mobile or tablet one. The header names the device first ("iPadOS · iPad · SwiftUI", "Android tablet · Compose M3"); the card's subtitle starts with the device class.
- **Components · …** (Actions, Cards, Layout, Navigation, Playback, Status) are the building blocks. They are device-agnostic and previewed at phone size; when a component changes per device, its README says how (sizes, layout), and a screen card shows it in place.
- In previews, `Platform` takes `device` (`phone`, `tablet`; `tv` reserved) and adds `data-device` and a `.vf-device-<device>` class, so device-specific styles hook in without new components.

## Coverage

| Feature | Phone (iOS · Android mobile) | Tablet (iPadOS · Android tablet) | TV |
| --- | --- | --- | --- |
| Home | `HomePhone`, `CategoryPagePhone` | `HomeTablet` | Planned |
| Detail | `DetailMoviePhone`, `DetailSeriesPhone` | `DetailTablet` (movie; series follows the same layout) | Planned |
| Player | `PlayerPhone` | `PlayerTablet` | Planned |
| Settings | `SettingsPhone` | `SettingsTablet` (two panes) | Planned |
| Search | `SearchResultsPhone` | `SearchResultsTablet` | Planned |
| Library, Downloads, Sign-in | Not yet | Not yet | Planned (no Downloads on TV) |

## Tablet rules (today)

- Margins `space-5` on Android tablet and 20 on iPad; posters 140 (medium) / 168 (expanded).
- Navigation: iPad uses the system tab bar at the top (`.sidebarAdaptable`); Android tablet uses the same floating bottom bar as phones, centred (see **Android**).
- The player opens landscape on tablets as on phones (see **guardrails.md**, Playback); rows show more cards, never bigger artwork ratios.

## When TV is added

Design it as its own device, not a stretched tablet. Each TV screen is a new card in its feature group (`HomeTV`, `DetailTV`, `PlayerTV`), and a `tv` column fills in the table above.

- **Focus, not touch.** Everything reachable with the remote's directional input; one clearly focused item at all times (tvOS focus engine with the system's lift and parallax on posters; Android `androidx.tv` Material components with focus scale and border). No long-press or swipe-only actions: quick actions come from a focused Options / Menu button.
- **Safe area.** Keep content inside the TV safe area (tvOS: 60pt top and bottom, 80pt left and right on the 1920 × 1080 canvas; Android TV: 48dp left and right, 27dp top and bottom).
- **Navigation.** tvOS: the system top tab bar. Google TV: a navigation drawer or top tabs. No Downloads destination.
- **Type and spacing** come from each TV platform's own text styles, which are larger than phone sizes; tokens get TV entries rather than scaled phone values.
- **Themes.** The `ios` and `android` themes are reused unless the TV platforms' native colors differ; then add `tvos` / `androidtv` themes with the same role names.
- **Shared.** The Kotlin Multiplatform core, UX guardrails, artwork rules and voice apply unchanged; guardrails get TV-specific notes where input differs (no double-tap seek; the remote's clickpad scrubs).
