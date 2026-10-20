# UX guardrails

Hard rules per screen, the same on iOS and Android — they describe behavior, not looks. **Must** and **never** are non-negotiable in v1; anything else is a default you may change with a reason. Copy examples are in sentence case; iOS shows them in title case (see **Voice**).

## Home

- **Must** order rows: hero (optional, max one) → Continue watching (Next up is part of it, not a row of its own) → Recently added (one row per library) → collections / genres.
- **Must** show Continue watching above the fold on a 6.1" phone, even with the hero present.
- **Must** hide empty rows. Never show a row that says "No items" on Home.
- Each row holds at most 20 items, then a "See all" action in its header.
- At most 12 rows on Home. More belongs in Library.
- Hero: one item per app session (recently added or a favorite) that has both a Backdrop and a Logo image. **Never** an auto-rotating carousel.
- Pull to refresh reloads Continue watching first.

## Cards

- Poster tap → detail. Continue-watching tap → **resume playback directly**; its info button → detail.
- Long-press → quick actions: Play / Resume, Mark as watched, Add to My List, Download. Same actions on both; iOS presents a context menu, Android a bottom sheet (see **iOS & iPadOS**, **Android**).
- Progress bar only while in progress (between the server's resume thresholds). Watched items get the check badge, no bar.
- **Never** more than one badge per card. Priority: New episode > 4K / HDR > rating.

## Detail

- Opens on a full-width backdrop hero; title (Logo image, else `brand-display`), facts, overview and actions sit over the image, always on `scrim`. Cast and More like this scroll below it.
- Exactly one primary button, and its label says what happens: "Play", "Resume · 38m left" (the time left; a round icon-only "start over" arrow sits beside it as the secondary button, only when there is something to resume), "Play S2 E4".
- Secondary actions (icon over label) after it: My List, Like, Trailer (only when the item has one), Download — max four. Mark as watched lives in the overflow menu.
- Series: a row of season buttons (about four visible, scrolling sideways) and the selected season's episode list below. **Must** open on the season of the episode Play / Resume starts, with that episode marked; picking a season swaps the list in place.
- Synopsis 3 lines + "More". Technical info (video, audio, subtitles, file) in a collapsed section at the bottom.
- Trailers play in the player, never inline with sound on the detail screen.

## Playback

- Phones open the player full-screen landscape; rotation lock respected on exit. Tablets keep the current orientation.
- Controls auto-hide after 3s without input; a tap toggles them. Double-tap the left / right third seeks ∓10s with a visible "10s" label.
- Scrubbing shows the trickplay thumbnail above the thumb with the timestamp; no trickplay data → timestamp only.
- Skip buttons ("Skip intro", "Skip recap") appear at segment start and stay for the whole segment, also when controls are hidden. **Never** auto-skip unless the user turned it on.
- Up next: at the Outro segment (or the last 30s without one) show the next-episode card with a 10s countdown and "Watch credits". After 3 auto-played episodes without input, pause and ask "Still watching?".
- Remember audio and subtitle language per series; fall back to the user's Jellyfin preferences.
- **Must** report progress to the server on start, every 10s, on pause, seek and stop — and on app backgrounding.
- **Never** start playback with sound from a non-player screen.

## Navigation

- Four tabs: Home, Search, Library, Downloads. Profile avatar top-right on Home switches Jellyfin user.
- Re-tapping the active tab scrolls to top, then pops to the tab's root.
- Each tab keeps its own back stack; switching tabs never loses scroll position.

## Search

- Results after a 300ms debounce, posters in a 3-column grid; recent searches when the field is empty.
- Group by type (Movies, Shows, Episodes, People) once there are results in more than one.

## Sign-in and profiles

- Server discovery on the local network first, manual URL second. Offer Quick Connect alongside username / password.
- Multiple users on the server → profile picker on launch, remembered per device.

## Offline and errors

- Server unreachable: keep the last Home in cache and show a banner with "Try again"; Downloads stays fully usable.
- Failed actions retry silently (the same retries as a read), then say so with an action. Android: a `Snackbar`. iOS has no snackbar, so a small banner on the system material at the bottom, with the same "Try again" action, gone after a few seconds. **Never** a modal for a non-blocking error.

## Accessibility

- Card accessibility label: "{title}, {year}, {n}% watched" (or "watched"). Rows are announced as lists with item counts.
- Every pair in the color tokens holds the contrast its usage note names on both platforms; artwork text always sits on `scrim`.
- Captions / subtitles styling follows the OS caption settings when the user hasn't chosen one.

## Never

- **Never** use red as an accent or a Netflix-like wordmark — VibeyFin borrows patterns, not brand.
- **Never** gate playback behind a detail page from Continue watching.
- **Never** infinite-scroll Home.
- **Never** block the UI on image loading; layout is final before artwork arrives.
