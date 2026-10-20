# Reference screens

Layouts for the detail screens, the same on both platforms apart from the native chrome. These are
reference screens, not components: build each from the parts named here (hero, `action` buttons, rows,
`PosterCard`s). Colours, type and controls come from `brand.md`, `ios.md` and `android.md`; the rules
from `guardrails.md`; the data from `jellyfin-mapping.md`.

## Detail

The movie detail screen: a full-width cinematic backdrop hero with the details and actions layered over
it, then cast, similar items and technical info.

**Provide:** the item's `Backdrop` and `Logo` images (plus `ImageBlurHashes`), name, production year,
official rating, runtime, genres, overview, `UserData` (playback position, `IsFavorite`, `Likes`,
`Played`), `People`, trailers (`LocalTrailerCount` / `RemoteTrailers`) and `GET /Items/{id}/Similar`.

### Hero

- Artwork: full-bleed, 2:3 on phones, so the image fills most of the first screen and runs under the
  status bar and the bar. Source: the item's `Backdrop`, center-cropped (posters usually carry a baked-in
  title that clashes with the Logo). BlurHash color until it loads.
- Scrim: a `scrim` band at the top for the bar, clear through the middle so the artwork reads, then a
  gradient that is light behind the Logo and reaches `scrim` before the facts line, `scrim-strong` behind
  the overview, and ends in `bg`. Text over the image **always** sits on `scrim`; the Logo image may sit
  higher on the lighter part.
- Layered over the image: the `Logo` image as the title, centered and large (the `brand-display` title,
  centered, when the item has no Logo) → facts line centered under it (year · rating · runtime · genres,
  `ink-muted`) → overview (3 lines + "More", left-aligned) → Play / Resume → secondary actions.
- Play / Resume is the one aqua `primary` button on the screen, large (iOS
  `.borderedProminent` capsule `.controlSize(.large)`, Android filled 56dp). The label says what happens:
  "Play", "Resume · 38m left" (with the play icon). It is full width until there is something to resume;
  then a round icon-only "start over" button (a circular arrow, the grey secondary button) sits beside it.
- Secondary actions as `action` buttons (icon over label), max four: My List (Favorites), Like (thumbs,
  `UserData.Likes`), Trailer (only when the item has a local or remote trailer), Download. Mark as watched
  moves to the overflow menu (`…` / `⋮`).
- Tablets and landscape use **Detail on tablets** below.

### Bar

- iOS: no opaque bar. Glass back button, an inline title over the backdrop, Share and `…` as glass
  buttons. The title sits on the top scrim band.
- Android: transparent `TopAppBar` over the backdrop (back arrow, Cast, `⋮`) on the same scrim band; it
  fills with the surface color once the hero scrolls away.

### Below the hero

- Cast: a row of people from `People` (round `Primary` image, name, role); a missing image is a
  `surface-2` disc with initials.
- More like this: a row of posters from `/Items/{id}/Similar`.
- Technical info (video, audio, subtitles, file) collapsed at the bottom.

## Series detail

The same backdrop hero as **Detail**, then a row of season buttons and the selected season's episodes.
The hero, bar and secondary actions follow **Detail**.

**Provide:** the series item (as Detail), `GET /Shows/{id}/Seasons`,
`GET /Shows/{id}/Episodes?seasonId=` for the selected season, and the next-up episode
(`GET /Shows/NextUp?seriesId=`).

### Hero

- Same layout as Detail. A series without a Logo image shows the `brand-display` title.
- Play / Resume starts the next-up episode and says so: "Play S2 E4", "Resume S2 E4 from 12:30".

### Seasons

- A horizontal row of season buttons under the hero: about four visible at a time on a phone, the next
  one peeking; more seasons scroll sideways. Labels are the season names Jellyfin returns ("Season 1",
  "Specials").
- The selected season is marked. iOS: filled `ink` capsule with `bg` text. Android: selected `FilterChip`
  in `accent-soft` / `on-accent-soft`. Aqua stays reserved for Play / Resume.
- On open the row starts on the season of the episode Play / Resume will start, scrolled so that button
  is visible, and that episode is marked current in the list.
- Tapping another season swaps the episode list in place: no navigation, no sheet, the hero stays where
  it is. Keep the previous list visible until the new one loads.
- Accessibility: the row is a tab list (each season a tab, the list its panel), announced with the season
  count.

### Episodes

- Episode rows for the selected season (see **Episode row**). The next-up episode is current.

## Episode row

One episode in a season list.

**Provide:** `index`, `title`, `runtime`, `synopsis`, `color`, optional `progress` + `remaining`,
`watched`, `downloaded`, `current`. Thumbnail, number and title, runtime, watched / time left, download
and a 2-line synopsis.

- iOS: a `List` / `LazyVStack` row; download button tinted aqua; swipe actions (Mark as Watched,
  Download) on the row.
- Android: M3 `ListItem`-style row; download as `IconButton` (onSurfaceVariant); long-press for actions,
  no swipe actions.
- Downloaded is a filled check-disc in `positive` (`ink`) on both.

## Detail on tablets

Movie and series detail at tablet widths (iPad, Android medium / expanded): a full-bleed landscape
backdrop with the details in a left column over it, then the same rows as on the phone. It is the
**Detail** layout for regular width (SwiftUI `horizontalSizeClass == .regular`, Compose
`WindowWidthSizeClass` Medium / Expanded); content, actions and data are the same as **Detail** and
**Series detail**.

### Hero

- Artwork: the item's `Backdrop` at its own 16:9, full width, under the bar (capped at 720pt / dp tall).
  BlurHash color until it loads.
- Scrim: from the left edge (`scrim-strong` → `scrim` → clear by about 60% of the width), a band at the
  top for the bar, and a fade into `bg` at the bottom. The right side of the artwork stays clear.
- Left column, about 46% of the width (max 520), left-aligned, at the bottom of the backdrop: Logo image
  (else `brand-display`) → facts line → overview (3 lines + "More") → Play / Resume (large, not full
  width) → the four `action` buttons.
- The Logo and text scale up with the larger type styles; the facts line and overview never run into the
  clear right side.

### Chrome

- Both platforms keep the main navigation at the top on tablets.
- iPad: the floating glass tab bar stays at the top center (system-rendered with `.sidebarAdaptable`);
  glass back button left, Share and `…` right.
- Android: the four destinations sit in the top app bar, centered (icon + label, the active one in an
  `accent-soft` pill), over the backdrop with back on the left and Cast, `⋮` on the right. No navigation
  rail and no bottom bar at these widths.

### Below the hero

- Rows at the larger margins (`space-6`) with posters at 168: Cast, More like this, technical info.
  Series: season buttons (more of them visible, same behavior) and the episode list, which can split into
  two columns at expanded width.
- Portrait iPad keeps this layout when the width is regular; split-screen at compact width falls back to
  the phone layout.
