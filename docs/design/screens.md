# Reference screens

Layouts for Home and the detail screens, the same on both platforms apart from the native chrome. These
are reference screens, not components: build each from the parts named here (hero, `action` buttons,
rows, `PosterCard`s, category buttons, category pages). Colours, type and controls come from `brand.md`, `ios.md` and
`android.md`; the rules from `guardrails.md`; the data from `jellyfin-mapping.md`.

## Home

The Home tab: the same content, rows and rules on both platforms, with native chrome on each.

- iOS: the app logo with the large title "Home" next to it and the profile avatar at the right, the category buttons under them; the backdrop and rows
  scroll under the floating tab bar (the safe area provides the bottom inset).
- Android: top app bar with the app logo and "Home" next to it and the avatar at the right, the category buttons under it, rows scrolling above the
  `NavigationBar` (`Scaffold`'s inner padding).
- Same on both: the logo and the page name ("Home") at the left, the profile at the right; category buttons → hero (optional) → Continue watching → Recently added. Aqua only on Play and
  progress (plus native tinting on iOS). The button row scrolls with the content.

### Category buttons

The row at the top of Home, Netflix-style: **Shows**, **Movies**, **New**, **Studios**, **Categories**, in that
fixed order (configurable in Phase 7). Home itself is never filtered: every button opens something of its own.

**Provide:** the buttons (`id`, `label`, whether it opens a dropdown), the open dropdown with its entries, and
handlers for a tap and for picking an entry.

- One horizontally scrolling row; the next button always peeks. Nothing stays selected, and there is no clear (×).
- **Shows**, **Movies** and **New** open a category page at once, titled with their name.
- **Studios** and **Categories** are different, and look it: they carry a chevron and open a dropdown under the
  button, with a search field on top. Picking an entry (a studio, a genre such as "Action") opens a category page
  titled with its name.
- iOS: a horizontal `ScrollView` of capsule buttons, 36pt tall with a 44pt hit area, `surface-2`
  (`tertiarySystemBackground`) with `ink`; Studios and Categories add a trailing `chevron.down`. A capsule on the
  system background, never glass. The dropdown is a popover under the button (a sheet on compact widths) with a
  search field and a list of names.
- Android: a `LazyRow` of M3 `AssistChip`s, outlined in `line-strong` with `ink-muted`; Studios and Categories add a
  trailing dropdown arrow and open a `DropdownMenu` with a search field above a scrolling list.
- The dropdown: loading shows grey lines, an empty list says so, what was typed matching nothing says so, a failure
  offers Try again. Each list is fetched once per session.
- Both: aqua is not used in this row or the dropdowns. Accessibility: the row is a group named "Filter Home"; a
  button that opens a dropdown says "Studios, opens a list".

### Category page

The page a button or a dropdown entry opens.

- A new screen over Home with the bar kept (the tabs' own back stack): a back button, the **name as the title**
  ("Shows", "Movies", "New", "A24", "Action"), then a grid of posters, the same grid as a folder's list.
- **Shows** are series by name; **Movies** are movies by name; **New** is movies and shows added in the last 30
  days, newest first; a studio or a genre is its movies and shows by name.
- Posters open the item's page or a folder's list, like anywhere else. More load as the end comes into view.
- Loading is grey posters, an empty page says so, a failure offers Try again, a rejected session leaves to "Who's
  watching?".

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
