# Reference screens

Layouts for Home and the detail screens, the same on both platforms apart from the native chrome. These
are reference screens, not components: build each from the parts named here (hero, `action` buttons,
rows, `PosterCard`s, category buttons, category pages). Colours, type and controls come from `brand.md`, `ios.md` and
`android.md`; the rules from `guardrails.md`; the data from `jellyfin-mapping.md`.

## Home

The Home tab: the same content, rows and rules on both platforms, with native chrome on each.

- iOS: the tab header every tab shares (`TabHeader`: "Home" in `.title2` bold at the left, the avatar at the right, no logo), the category
  buttons under it, pinned there while the page scrolls (as the Library's chips are); the backdrop and rows scroll under the system tab bar (the safe area provides the bottom inset).
- Android: the tab header every tab shares (`TabHeader`: "Home" in `headlineSmall` at the left, the avatar at the right, no logo), the
  category buttons under it, pinned there while the page scrolls, rows scrolling behind the floating bar (their bottom padding leaves its space free).
- Same on both: the page name ("Home") at the left, the profile at the right, as on every tab; category buttons (pinned) → hero (optional, see **Home hero**) → Continue watching → Recently added in each video library → a row for every genre, biggest first (a profile can limit them or turn them off). There is no row of the libraries: the Library tab is where they are browsed. If the libraries can't be listed, one "Recently added" row stands in for theirs with the error and Try again. A row with nothing in it is not shown, with no gap; each row loads and fails on its own; a header says "See all" and opens the library or the genre. Copper only on progress and
  selection; Play is white. The button row scrolls with the content.

### Category buttons

The row at the top of Home, Netflix-style: **Shows**, **Movies**, **New**, **Categories**, in that
fixed order (configurable in Phase 7). Home itself is never filtered: every button opens something of its own.

**Provide:** the buttons (`id`, `label`, whether it opens a sheet), the open sheet with its entries, and handlers for
a tap and for picking an entry.

- One horizontally scrolling row; the next button always peeks. Nothing stays selected, and there is no clear (×).
- **Shows**, **Movies** and **New** open a category page at once, titled with their name.
- **Categories** is different, and looks it: it carries a chevron and opens a bottom sheet of genres, the same list
  component as the player's audio and subtitle pickers (a title and one row per genre; nothing is checked, because
  picking a genre such as "Action" opens a category page titled with its name). Each row ends with how many titles
  it holds, and a genre with none is left out. No search.
- iOS: a horizontal `ScrollView` of capsule buttons, 36pt tall with a 44pt hit area, `surface-2`
  (`tertiarySystemBackground`) with `ink`; Categories adds a trailing `chevron.down`. A capsule on the
  system background, never glass. The list is a `.sheet` with a plain list (the player's tracks use a `Menu`, but this list loads on demand and needs loading, empty and error states).
- Android: a `LazyRow` of M3 `AssistChip`s, filled `surface-2` with `ink`, no outline; Categories adds a
  trailing dropdown arrow and opens a `ModalBottomSheet` with the player's `OptionList`.
- The sheet: loading shows grey lines, an empty list says so, a failure offers Try again. The list is fetched once
  per session.
- Both: the selected chip is `spark-soft`; nothing else in this row or the sheet is copper. Accessibility: the row is a group named "Filter Home"; the
  button that opens the sheet says "Categories, opens a list".

### Category page

The page a button or the sheet opens.

- A new screen over Home with the bar kept (the tabs' own back stack): a back button, the **name as the title**
  ("Shows", "Movies", "New", "Action"), then a grid of posters, the same grid as a folder's list.
- **Shows** are series by name; **Movies** are movies by name; **New** is movies and shows by when they were
  added to the server, newest first; a genre is its movies and shows by name.
- **Sort and Genre** sit under the title as two buttons (a genre's own page has no Genre button). Each opens
  the player's picker sheet (a title, one row per choice; the one in use is a soft `spark-soft` pill, not a tick). Sort: Name (A to Z), Recently
  added (newest first; New starts here) and Rating (best first); Genre: All genres and each genre with how many titles
  it holds on this page: movies only on Movies, shows only on Shows, both on New; a genre with none of the page's kind
  is left out. A button that changes the page shows filled (`spark-soft`), so it is clear what is applied. Choosing
  reloads the list from the top. The buttons stay put while the list scrolls. These pages have no Year button, no year
  subtitles and no year strip (the Library tab does: see **Library**).
- Back from an item's page returns to the same place in the list.
- Posters open the item's page or a folder's list, like anywhere else. More load as the end comes into view.
- Loading is grey posters, an empty page says so, a failure offers Try again, a rejected session leaves to "Who's
  watching?".

## Home hero

An optional card between the category buttons and Continue watching: one title, or a carousel of up to five, picked once per app session.

- **Which:** picked at random from recently added and My List, only titles with both a `Backdrop` and a `Logo` image, and never one the
  user has watched, never one twice; as many as "Featured titles on Home" says (Off, 1 to 5; 1 by default). Fewer qualify: only those.
  Nothing qualifies, or the setting is Off: no hero, no gap.
- **Carousel (2 or more):** the cards page sideways, full width, no peek. Page dots sit centred under the card, outside the artwork
  (iOS `UIPageControl`, Android a row of dots, current `ink`, others `ink-subtle`). Every 8 seconds it moves to the next item, and after
  the last comes the first; swipes go round the same way. A swipe, a tap on the dots or a long press stops it until Home is left, and coming back to Home starts it again. Android also pauses it while a
  finger rests on the card; iOS does not, so a drag that starts on the card still scrolls the page. It never moves under Reduce Motion / Remove animations or with
  VoiceOver / TalkBack on. Each page reads "Featured, 2 of 3: <title>, opens its page".
- **Card:** a rounded card (`radius-artwork`), 4:3 on a phone and 2.6:1 from 600dp. The `Backdrop` fills it under a `scrim` that rises
  from the bottom; over it, centred: the `Logo`, a line of what it is (a movie or a show) and its genres.
- **Buttons:** Play (the one white button) beside My List (a heart), 40dp tall, acting on the item showing. Play on a movie starts it; on a show it starts the next
  episode, else the first episode of the first season that is not Specials, else opens the page. Tapping the picture opens the page; a long press opens
  the poster menu (Details, Trailer, My List), the same sheet as a poster's.
- **Loading:** Home waits until it has read the profile's hero setting, so the skeleton is in the first frame and never slides in; it
  is the card's exact shape, with the dots row's height under it when the setting is 2 or more. The hero's own slot does not animate its placement when the rows below load.

## Library

The Library tab: every movie and show in the library as a poster grid (three across on a phone), under the tab header
("Library" and the profile; no count, so the header is the same height as on the other tabs).

- **Buttons** under the title, one scrolling row of filled capsules: Movies, Shows, Collections, Sort, Genre, Year, People and Clear
  (Clear only when something is applied). Movies and Shows narrow the type; Collections lists the boxsets instead. A button that
  changes the page shows filled `spark-soft`.
- **Year** picks a decade (2020s, 2010s, 2000s, 1990s, 1980s, and Older). **People** opens a sheet with a search field: type a name, pick
  an actor or director, and the grid lists their titles.
- **Changing a filter** keeps the old list on screen, dimmed, until the new one arrives. Back from an item returns to the same place.
- States as a category page: grey posters while loading, an empty page says so, a failure offers Try again.

## Search

The Search tab: the tab header ("Search" and the profile), the field under it, then results from `/Search/Hints` after a 300 ms debounce.

- **Empty field:** the profile's recent searches (ten at most, kept on this device), each tapping to search again, and a way to clear them.
- **Filters:** a row of filled capsules under the field: All, Movies, Shows, Episodes, People, Collections. Results are grouped under
  those headings when more than one kind has a match.
- **Results:** a poster opens the title, an episode its episode page, a person their page, a collection its page. A long press on a
  poster opens the poster menu (see **Poster menu**).
- **States:** grey rows for the first search; a newer search dims the older answer; no match says so with a hint; a failure says why with
  Try again.

## Person page

A cast member's page, opened from the cast row or a search result: a round portrait (their initials on a `surface-2` disc without one),
the name in `brand-display`, "Born 1999 · Place", the biography (four lines with More / Less, or "No biography on the server."), then
Movies and Shows in the library as poster rows, newest first. A person with nothing in the library says so.

On a tablet (Android from 600dp, iPad at regular width) the page uses the width: the portrait and the details sit side by side, the
details start-aligned and no wider than 720, and Movies and Shows are each a heading over a grid of posters at least 168 wide that
fills the width. A folded device's cover screen and Split View at compact width keep the phone layout. Mockup: **PersonTablet**.

## Collection page

A boxset opens its own page: a header with the collection's picture and name (the text fades in with the picture, with no jump), then
Movies and Shows rows. On a tablet the header is wide (21:9) with the details in a left column. As on a title's page, the picture stays
put while the page scrolls: the text goes up over it, it darkens, and the rows below cover it.

## Continue watching menu

A long press or the "…" on a Continue watching card opens its options: a bottom sheet on Android, a native context menu (and the same `Menu` behind the "…") on iOS. Rows, in order: **Details**, **Mark as watched**, **Remove from Continue watching** (a card in progress only; a Next Up card has no such row) and **Download** (when the server lets the user). Mockup: `ContinueCardMenu`.

- **Remove** is for what the user gave up on. It has the plain row style, asks nothing, and the card leaves the row as the menu closes; the last card takes the row with it. Once saved, "Removed from Continue watching" shows with **Undo** for a few seconds, which puts the card back where it was with its progress. A removal that fails puts the card back and says so, with **Try again**. Mark as watched is unchanged. Kids profiles have it too; offline the card has no menu.

## Poster menu

A long press on any poster, in a row, a grid or search results, or on a Home hero card, opens a short menu: a bottom sheet on Android, a native context menu on iOS:

- **Details** opens the title's page, **Trailer** (only a movie or a show can have one, and only when it does: the server's own plays in the app, one on the web opens in
  YouTube), **Download** (when the server lets the user: a tap starts one, a show asks first; with one the row says where it is and opens Pause or Resume and Remove) and
  **My List** (a heart, filled when it is on the list; adds or removes, and says which).
- Android: the rows are there when the sheet opens. Lists do not carry trailers, so the sheet looks the title up; a grey line the height of a row holds the Trailer row's place while the
  server answers, and a title with no trailer never shows a Trailer row that then goes away. The lookup never undoes a My List change made in the meantime.
- A held row lights up `spark-soft` and does not shrink. A change that could not be saved is taken back and the sheet says so in `danger`.
- **iOS:** a native `.contextMenu` (the same as Continue watching and the episodes), so a finger can press, slide to a row and let go; the system draws it, lifts the poster and plays
  the haptic. Rows, in order: Details, Trailer, My List, Download (with a download: Pause or Resume and Remove), each with an SF Symbol and in title case. No header. SwiftUI fixes the
  rows as the finger lands, so they come from what the poster knows (My List from the item, Download from the user's permission) and a greyed "Checking for trailer…" row (`film`, disabled)
  holds the Trailer's place while the server is asked; the menu updates by itself, the row turning into Trailer or going. A My List change that could not be saved is taken back and an
  alert says so. The Download questions (how many episodes, delete it?) are sheets and dialogs over the tabs. On the Home hero the carousel stops once the menu is up, so it does not
  slide away as the menu closes.
- **Android:** a `ModalBottomSheet` on `surface-container-low`, opened to its full height; the title alone in `titleMedium`, 56 dp rows, the Download menu an M3 dropdown.

## Detail

The movie detail screen: a full-width cinematic backdrop hero with the details and actions layered over
it, then cast, similar items and technical info.

**Provide:** the item's `Backdrop` and `Logo` images (plus `ImageBlurHashes`), name, production year,
official rating, runtime, genres, overview, `UserData` (playback position, `IsFavorite`,
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
- Play / Resume is the one white `action` button on the screen, large (iOS
  `.borderedProminent` capsule `.controlSize(.large)`, Android filled 56dp). The label says what happens:
  "Play", "Resume · 38m left" (with the play icon). It is full width until there is something to resume;
  then a round icon-only "start over" button (a circular arrow, the grey secondary button) sits beside it.
- Secondary actions as `action` buttons (icon over label): My List (Favorites, a heart), Trailer (only when
  the item has a local or remote trailer), Watched (a tick) and Download (only when the server lets the user;
  see **Downloads**). There is no Like and no overflow menu (Likes were cut from the roadmap: Jellyfin's like flag is
  barely used, and My List covers it).
- Tablets and landscape use **Detail on tablets** below.

### Bar

- iOS: no opaque bar. Glass back button, an inline title over the backdrop, Share and `…` as glass
  buttons. The title sits on the top scrim band.
- Android: a floating back button over the backdrop on the same scrim band; no app bar.

### Opening

The page does not build itself in front of the user. The screen dims at once, a skeleton with the page's exact layout shows (never a
spinner), and when the data is in the finished page fades up whole. On a phone the picture is pinned and darkens as the page
scrolls over it (fully dark after about half the picture has gone by).

### Below the hero

- Cast: a row of people from `People` (round `Primary` image, name, role); a missing image is a
  `surface-2` disc with initials.
- More like this: a row of posters from `/Items/{id}/Similar`, without watched titles when the profile hides them (**Watched titles in rows**).
- Search for subtitles: on a movie's or an episode's page, a button inside opened Technical info, directly under the **Subtitles** tracks, because that is the list a new subtitle joins. A title with no subtitle tracks keeps the Subtitles label with only this button. It and **Delete subtitles** are compact capsules side by side (`surface-2` on Android, the system's bordered capsule on iOS), and Delete wraps under Search when they don't fit. Held, a button lights up where it is (Android `MenuPressIndication`, iOS's own highlight) and doesn't shrink or move, unlike the app's other buttons. Never on a show or a season or in the long-press menu. It shows only when the user's `Policy.EnableSubtitleManagement` is on (being an admin doesn't count), never on a kids profile or offline, and not when the title plays from a download on this device. It opens the same sheet as the player: see **Search for subtitles** in `player.md`. Mockup: `SubtitleSearch`.
- Delete subtitles (issue #84): a button with a trash icon in `danger`, beside Search for subtitles, on the same titles, when the title has at least one external subtitle file. It shows only for an administrator (`Policy.IsAdministrator`, the only users the server lets delete one; subtitle management doesn't count), never on a kids profile or offline, and not when the title plays from a download. It opens a native sheet: "Delete subtitles", "Subtitles built into the video can’t be deleted, so they aren’t listed.", then the external tracks, each with a trash icon in `danger`. A track is named by its language and kind only ("English · Forced", "French"; the codec and "External" say nothing here), with its file name under it ("Film.en.forced.srt"), so two files in one language can be told apart. A screen reader says "Delete the French subtitles, Film.fr.srt". Tracks inside the video file are never listed. Choosing one closes the sheet and asks, in an alert: "Delete the French subtitles?", "They’re deleted from the server for everyone and can’t be brought back.", **Delete** (destructive) and **Cancel**. **Delete** takes the track out of Technical info at once, so it can't be chosen twice while the server answers; the page then reads the title again and "French subtitles deleted" goes by. The row goes when no file is left. A failure puts the track back and says so with **Try again**; a 403 says the account can't delete subtitles and hides the row; a track already gone is shown gone. Not in the player. Mockup: `SubtitleDelete`.
- Technical info (video, audio, subtitles, file) collapsed at the bottom. Plain text in brackets right after the **Video** codec, not a badge, says how Play will deliver the title (`3840×2160 · HEVC (Direct play) · 24 fps · 8.2 Mbps`: Direct play, Direct stream or Transcode, a transcode with its reason under the row). When only the audio has to be converted, `(Transcode)` goes after the codec of the playing **Audio** track instead, and when both do, on both. A title with no such line, like a show, has a row saying only that. Nothing is said when it plays from a download or the server can't say. **Audio** and **Subtitles** list every track, one per line, by the server's own name with ` · ` between the parts ("English · Dolby Digital · 5.1 · Default", "English · SUBRIP · External"); a line is left out when the file has no such track.

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
  in `spark-soft` / `on-spark-soft`. Copper stays reserved for progress and selection; Play / Resume is white.
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

**Provide:** the episode's `Primary` still (else the show's), `ParentIndexNumber` and `IndexNumber`, title, synopsis, runtime,
rating, air date, and its `UserData` (played, resume position).

- **Phone (compact width):** a row in a list down the page. A 148dp 1.6:1 picture on the left plays it (a check at its top once
  watched, a copper progress bar along its bottom edge when part watched, a brighter outline on the episode Play starts). On the
  right, the "S1 E4" tag, the title, a line with how long it is (or how much is left), its rating and air date, and two lines of
  synopsis; those open the episode's page.
- **Regular width:** a card in a row that scrolls sideways, 380dp wide, 1.6:1, **all of its text on the still**: at
  the bottom, over a scrim, the "S1 E4" tag, the title, two lines of synopsis, and a line with how long it is (or how much is
  left), its rating and the day it aired. A check sits at the top once watched, a copper progress bar runs along the bottom edge
  when it is part watched, and the episode Play starts has a brighter outline.
- On a card, a tap on the picture plays it and a tap on the words opens its page. A long press opens a menu on both platforms: **View episode** (its own page) and the watched mark.
- Each episode has its own **download button**: at the end of the row on a phone, at the bottom-right of the still on a card, under the watched check (top-right); the facts line stops short of it so the release date is never covered. It is the
  arrow with nothing downloaded (a tap starts it), then the state badge (a tap opens pause, resume and remove). The long-press menu holds the
  same actions.
- No swipe actions on either platform.
- Downloaded is a phone with a check in `ink` (Android: Material `mobile_check`; iOS: `checkmark.rectangle.portrait`), never the check-disc, which is the watched mark.

## Downloads

The Download action, the Downloads tab and the questions around them. Everything is in `ink`: copper never signals status, and a
download's progress is not playback progress.

- **Download button** (fourth secondary action, icon over label). One picture for the state, drawn from the shared `DownloadBadge`:
  the download arrow (nothing downloaded), a ring that fills (coming in; grey and still while it waits for Wi-Fi or a connection),
  pause bars (paused), a warning (failed), a phone with a check (on the device), never the check-disc, which is the watched mark. The label under it is "Download", "Downloading",
  "Waiting", "Paused", "Failed", "Downloaded", and on a show "N downloaded". Android draws the ring by hand, not with M3's spinner,
  which is for the player only.
- **Tapping it:** on a movie or an episode with no download it starts one (and asks, once, for the notification permission on Android
  13+). With a download, and on a show, it opens a menu: pause, resume, remove; for a show "Download Season N" (the selected one),
  "Download all episodes" and "Remove N downloads".
- **Season and show confirm first:** "Download 13 episodes?" with "They take about 965 KB." and, when some are watched, "Only episodes
  I haven't watched". A season's own list page has a **Download season** button with "3 of 8 downloaded".
- **Removing one movie or episode is immediate:** Remove in its menu deletes at once, with no second question. Removing more than one (a show, a season, **Remove all downloads**) always asks: "Remove 13 downloads?" with "This frees 283 MB. You can download them again later." iOS shows it as a centred alert (a confirmation dialog is a popover on iPad); Android as an `AlertDialog`. (what is on the device now).
- **Downloads tab:** the tab header, then a card: "On this device" over "1.4 GB used", and a bin icon with no words
  (**Remove all downloads**, asks first; the screen reader names it); **Movies** (poster, title, status line) and **Shows**
  (a card with the show's poster, "13 episodes · 965 KB" and a chevron that opens its episodes in place, a menu with go to the show and
  remove all of it). A row's status line is "Downloaded · 850 MB", "Downloading · 120 MB of 850 MB" with a thin ink progress bar,
  "Waiting for Wi-Fi", "Waiting for a connection", "Waiting for the server", "Paused · …", or "Download failed" with the reason under it. A running download adds the time left ("· 8m left"); the notification says the same ("20 MB of 283 MB · 8m left").
  A row's menu is pause or resume, and remove; a tap opens the item's page. Empty: "Nothing downloaded yet".
- **Swipe to remove (Downloads tab only):** a row swipes left to a red Remove (iOS: native `swipeActions`, a short "Remove" with a bin, VoiceOver gets the full words; Android: Material `SwipeToDismissBox` with a `danger` bin). A movie or an episode goes at once, with a full swipe on iOS; a show settles back and asks first, with no full swipe on iOS. The row's menu stays the way to do it without a gesture. The episode lists on the item pages still have no swipe actions.
- **Settings:** "Offline mode" (device), "Download over mobile data" and "Storage limit for downloads" (profile), and **Delete downloaded files** in `danger` text (profile). It asks first (iOS confirmation dialog, Android `AlertDialog`) and deletes the profile's whole folder, leftover metadata of unfinished downloads and files no download lists included. Other profiles keep theirs.
- **Where the files are:** per server and per profile (`<server>/<profile>/<item>`). iOS: the app's Documents folder, shown in the Files app under On My iPad, VibeyFin, out of iCloud and backups. Android: the app's own folder on shared storage (`Android/data/<app>/files/Downloads`), which a file manager can open. They go with their owner: signing out deletes the profile's, removing a server deletes the server's, uninstalling deletes all (so Android does not use the shared Movies folder, which outlives the app).

## Offline

- **Going offline:** said once, as a toast: "You're offline. Showing what you've downloaded." (no network) or "Offline mode is on. Showing what you've downloaded." (the switch in Settings). Android: the system `Toast`. iOS has none: a capsule on `.regularMaterial` above the tab bar for about 3.5 s, announced to VoiceOver, taking no taps. No strip across the page; Settings switches Offline mode off.
- **Banner:** only "Can't reach the server" with **Use offline**: a strip under the status bar, `surface-3`, title and one line. It stays until the server answers or Use offline is pressed.
- **Tabs:** Home, Search, Downloads. **Home** has Continue watching, then Movies and Shows rows from the device; a movie or a card plays at once and a show opens Downloads. **Search** searches downloads (All, Movies, Episodes) and a result plays. **Downloads** plays a finished row.
- Offline nothing opens a page that needs the server; Library is not there.

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

- iPad keeps the main navigation at the top; Android tablets use the same floating bar at the bottom as phones, centred and as wide as its tabs.
- iPad: the floating glass tab bar stays at the top center (system-rendered with `.sidebarAdaptable`), with the profile avatar in the same row at the right and no second header row;
  glass back button left, Share and `…` right.
- Android: the four destinations sit in the top app bar, centered (icon + label, the active one in an
  `spark-soft` pill), over the backdrop with back on the left. No navigation
  rail and no bottom bar at these widths.

### Below the hero

- Rows at the larger margins (`space-6`) with posters at 168: Cast, More like this, technical info.
  Series: season buttons (more of them visible, same behavior) and the episode list, which can split into
  two columns at expanded width.
- Portrait iPad keeps this layout when the width is regular; split-screen at compact width falls back to
  the phone layout.

## Seerr

Only once the profile is signed in to Seerr, never on a kids profile. The reference screens are in the design system's
"Screens · Seerr" group: `SeerrSettingsPhone`, `SeerrSearchPhone`, `SeerrRequestPhone`, `SeerrRequestSheet`, `HomeRequestsPhone`,
`HomeDiscoverPhone`.

- **Search:** "Not in your library" is the last group, with a "Not in library" filter chip. When the library has nothing, a line says so above it.
- **Home:** My requests right after Continue watching; Trending, Popular movies, Popular shows and Coming soon after the
  library's rows and before the genres. Each loads on its own and is hidden when empty or failing.
- **Title page:** the detail page's hero and layout. Status line in ink with its icon (danger for a failure). One white `action` button:
  Request, Request seasons, Request again; a quiet disabled one when there is nothing to do. Trailer, Request in 4K and Cancel
  request beside it. Seasons, Cast (not tappable), More like this.
- **Poster menu and season picker:** a long press on a Seerr poster anywhere. A movie's Request sends at once; a show's opens the
  picker (All missing seasons on, Specials off, Standard / 4K when allowed). What a request came to: a snackbar on Android, the
  banner capsule on iOS, never a dialog.
- **Settings › Seerr:** the address field and Connect; then the address, version, "Signed in as …", requests left, My requests
  and Disconnect (this profile only). The password sheet only when Quick Connect is off on the Jellyfin server.

## Settings

Settings sits under the profile avatar on Home, never in a tab. The same pages, rows and rules on both
platforms, in each platform's own list style (`ios.md`, `android.md`). The design system's `SettingsPhone`
and `SettingsTablet` are the mockups; rules are in `guardrails.md` (Settings).

### Root (phone)

A profile card, then groups. Per-profile groups come first and the one device-wide group after them, so
the scope is clear from position and from the footers.

- **Profile card** (tappable, chevron): opens the Profile page.
- **Playback**: Quality, Audio and subtitles, Skipping and autoplay. There is no Buffering row here: Buffer ahead lives
  on Skipping and autoplay.
- **Home**: Featured title, Genre rows, Show watched titles in rows (a switch, on by default; see **Watched titles in rows**).
- **Notifications**: opens its page (below).
- **Kids and PIN** (the group): one row, **Kids profile**, which opens the Kids and PIN page (below). It stays on a kids profile too, since turning kids mode off asks for the PIN on that page.
- **This device**: Always ask who's watching.
- **Advanced**: Playback stats overlay, About.

A choice that is not a page of its own (Genre rows and the intervals) is a menu in its row: a `Picker` with
`.pickerStyle(.menu)` on iOS, a `ChoiceRow` with a `DropdownMenu` on Android. It never opens a sub-page.
A link row opens a sub-page.

### Watched titles in rows

"Show watched titles in rows" is per profile and on by default. Off, Home's Recently added rows and genre rows ask the server for
unwatched titles only (`isPlayed=false` on `/Items/Latest`, `Filters=IsUnplayed` on `/Items`), so each row still fills to its 20.
A show is watched only once every episode is (the server's own rule). More like this leaves them out too: `/Items/{id}/Similar`
has no such filter, so three times as many are asked for and the watched ones dropped on the device. Continue watching, My List,
the hero (which never features a watched title anyway) and the library and category pages are not affected. On, Recently added
still follows the profile's own Jellyfin preference "Hide watched content from latest media" (`HidePlayedInLatest`, on by
default): the API has no way to ask Latest for watched titles past it, and the help text says so. A row left empty is hidden, as
any empty row: Home's genre list is still picked from all genres, so a genre with nothing unwatched left drops out and Home shows
one genre row fewer, with no empty state. More like this asks for 36 to keep 12, since `/Similar` can't be paged: a profile that
has watched most of the similar titles gets a shorter row, or none, without a message. Changing the switch while Home is open
asks for those rows again.

### Profile

Avatar, name and server; **Switch profile** (to "Who's watching?") and **Quick Connect** (below); a read-only Server group (name and
addresses); **Sign out** in `danger` text with a confirmation. Sign out clears the token, deletes the profile's
downloads from the device and keeps the profile's tile in "Who's watching?", locked (the explicit sign-out the session rules allow). Name and avatar
are edited in Jellyfin, not here. A kids profile has no Sign out _(planned with kids mode)_.

**Quick Connect** (mockup **QuickConnectApprovePhone**) is reached from the profile menu (after the PIN to open Settings when that is on) and from the Profile page, under Switch profile. Its page: "On the other device, choose Quick Connect. Type the code it shows here to sign it in as Alex.", a Code field (number pad, digits only, six) and **Approve** (the white `action` button, the page's one primary action; "Approving…" while busy). Approved says so under the field with a check in the text colour and clears it; a code the server never gave out, Quick Connect off on the server and an unreachable server are said under the field, which keeps the code. Not on a kids profile.

### Quality

A per-profile cap on what the server sends: **Maximum** by default (the original, direct play when possible),
then 4K 40, 1080p 10, 720p 4 and 480p 1.5 Mbps. It is the one choice shown as a checklist: the rows sit inline in
their own section, the section title once, the footer (help text) below, and the chosen row is a soft
`spark-soft` pill with no tick, on both platforms. There is no quality menu in the player.

### Skipping and autoplay

- Skip back 5, 10, 15 or 30 s; Skip forward 10, 15, 30, 45 or 60 s (the values SF Symbols has glyphs for).
- "Auto skip intros and recaps", off by default (the segment buttons show either way).
- "Still watching after": Off or 2 to 5 episodes, default 3.
- **Buffer ahead** in its own group: Auto, 10 to 60 s, default 20 s, applied from the next video
  (`Strings.bufferAheadHelp`).

Each is a menu row.

### Subtitle style

At the end of the Audio and subtitles page, under the languages, headed "Subtitle style" (there is no separate
Subtitles page). A live preview over artwork, then "Use system caption style" on by default; turning it off enables size, text
colour and background (none, shadow, box), and "Reset to defaults" sits at the bottom. Android applies the
style to the player. iOS burns subtitles into the picture today, so its page shows the controls disabled
with the note "Applies to text subtitles only; iOS burns subtitles in for now". Applying a style on iOS needs
a spike on a simulator and is an open item.

### Notifications

"Notify me about new episodes and movies", off by default, per profile. While it is on, a "Which shows" menu: "Shows I
follow and new movies" (default; shows in Continue watching or My List) or "Everything new in the library". The
footer says Jellyfin sends no push, so the app checks in the background and there is a delay. Switching it on asks
for notification permission; a refusal switches it back off and the footer says notifications are blocked and where
to allow them. Hidden for a kids profile, which never notifies. What a notification looks like and what a tap does:
`android.md`, `ios.md` and `guardrails.md`.

### Kids and PIN

Opened from the **Kids profile** row in the root's Kids and PIN group.
"Kids profile" is a local flag and the PIN (4 digits, a salted hash, never the Jellyfin password) a household lock,
independent of each other. The rating cap, blocked tags and hours are read from the Jellyfin user's policy and
shown read-only.

### Settings on tablets

At regular width (iPad regular, Android from the 600dp tablet breakpoint) Settings is two panes: the root on
the left, the chosen page on the right.

- The selected root row follows the row the user tapped, not the page that opened.
- The detail column is capped at about 640 and centred; it never stretches across the screen.
- Opening Settings selects the first page that has something to set (Quality); the selection survives rotation.
- iPad: `NavigationSplitView`, the root as an inset grouped sidebar list with the system selection, the page in
  the detail column with its own title; the back chevron is in the sidebar only.
- Android: a plain two-column layout at the existing 600dp breakpoint (not `material3-adaptive`), the selected
  row in an `spark-soft` pill.
- At compact width (Split View, a folded device) it collapses to the phone's push navigation.

## Sign-in

From first launch to Home, and back out. The mockups are **SignInFlow** (the map), **ServersPhone**, **AddServerPhone**,
**WhoIsWatchingPhone**, **SignInSheetPhone**, **QuickConnectPhone** and **SignInTablet**; their READMEs hold the details.

- **Headers:** the app's header row on every page (`TabHeader`: the title at the left, a round button in the profile's place;
  on Who's watching? that is the server button, with Switch server and Server details; on a tab the profile's first letter in a
  circle on the profile's tint, the same as its tile; Jellyfin profile pictures aren't shown), at the tab header's size (iOS `.title2`
  bold, Android `headlineSmall`). Pushed pages add the platform's back button above it; sub-pages match Settings; sheets match
  the app's sheets.
- **First launch:** the wordmark, "Connect to your Jellyfin server to start watching." and **Add server** (the white `action` button). Android searches
  the network and lists what answers; iOS can't search (no multicast entitlement) and says where to find the address instead.
- **Servers:** a card per saved server (name, the address that worked last, version, how many addresses and profiles), a menu with
  **Server details** (its addresses, add or remove one) and **Remove server** (asks first; signs out its profiles on this device and
  deletes their downloads). Android adds "On your network". **Add server** at the foot is the secondary button with a leading +
  (Android: `tonal` / `on-tonal`; iOS: the grey bordered button).
- **Add a server:** a labelled field with help under it, focused on open; Go on the keyboard or Connect connects and puts the keyboard away; Connect → Found (or "you already have this server") →
  **Add server**, which goes straight to that server's Who's watching?. A failure is said under the field, which keeps the text.
- **Who's watching?:** the server's name over the title, a tile per profile (lock badge when it needs a password or a PIN), then
  **Quick Connect** (only when the server allows it) and **Other user**. Grey tiles while loading; the tapped tile shows a spinner;
  a 401 lands here with a note naming the profile; an unreachable server says which addresses were tried. Once a tile's profile
  is signed in, its picture lifts out, moves to the middle and grows to 1.8× over about half a second while everything else fades,
  then moves up into the round button at the top right, shrinking to its size and rounding into its circle (about 0.4 s), and Home
  fades in with the profile button in that place (as Netflix does). Straight to Home with Reduce Motion / Remove animations, and after Other user or
  Quick Connect, which have no tile.
- **Password sheets:** focused on open, show/hide, autofill and paste allowed, the error under the field, **Use Quick Connect
  instead** when it is on. The password is never stored.
- **Quick Connect:** steps, the code in two groups with copy, "Waiting for approval · code expires in 4:32", then Approved →
  Home. Expired: **Get a new code**. Off: **Sign in with password**. The polling resumes when the app comes back to the front.
- **Tablets:** Who's watching? centred with 140 tiles; sheets as an iPad form sheet or Android's centred bottom sheet; forms in one
  480-wide column.
