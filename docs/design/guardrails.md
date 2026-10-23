# UX guardrails

Hard rules per screen, the same on iOS and Android — they describe behavior, not looks. **Must** and **never** are non-negotiable in v1; anything else is a default you may change with a reason. Copy examples are in sentence case; iOS shows them in title case (see **Voice**).

## Home

- **Must** order rows: hero (optional, max one) → Continue watching (Next up is part of it, not a row of its own) → Recently added (one row per library) → collections / genres.
- **Must** show the category buttons under the header, above the hero, as one scrolling row in this order: Shows, Movies, New, Categories. Home is never filtered. Shows, Movies and New open a category page titled with their name; Categories carries a chevron and opens a sheet of genres (the player's picker component, no search), and the genre picked opens a page titled with its name. Aqua is never used on the buttons.
- **Must** show Continue watching above the fold on a 6.1" phone, even with the hero present.
- **Must** hide empty rows. Never show a row that says "No items" on Home.
- Each row holds at most 20 items, then a "See all" action in its header.
- Home has no fixed cap on rows: every genre the server lists gets one (biggest first), unless the profile limits them in Settings ("Genre rows on Home": all, top 20, 10, 5 or 3, or none). A new profile starts at the top 5; a profile from before the setting existed keeps all. Rows load a few at a time and show from top to bottom.
- Hero: one item per app session, picked at random from recently added and My List, that has both a Backdrop and a Logo image and has **not** been watched. A rounded card (4:3 on a phone, 2.6:1 from 600dp) with Play beside My List; a profile setting turns it off. **Never** an auto-rotating carousel. Its skeleton is in the first frame, and Home waits for the setting before drawing, so nothing slides in.
- Pull to refresh reloads Continue watching first.

## Cards

- Poster tap → detail. Continue-watching picture tap → **resume playback directly**; its title and its info button → detail.
- Long-press on a poster → a bottom sheet (both platforms) with Details, Trailer (only when the title has one) and My List. The rows are there at once; a title with no trailer never shows a Trailer row that then disappears. It works the same in search results. Only a movie or a show is ever looked up for a trailer: a collection, season or episode shows no Trailer row and no grey line, and nothing is asked of the server. Episodes (a row on a phone, a card on a tablet) play from the picture and open the episode's page from the words, and have their own long-press menu: View episode and the watched mark.
- A row of a menu or sheet that is held (a subtitle picker, the poster menu, a dropdown) lights up in the accent colour; it does not shrink or move. Buttons and cards elsewhere still dip slightly when pressed.
- Pressing Play opens the player with a short fade and a slight grow from the page behind it, never an instant cut.
- Progress bar only while in progress (between the server's resume thresholds). Watched items get the check badge, no bar.
- **Never** more than one badge per card. Priority: New episode > 4K / HDR > rating.

## Detail

- Opens on a full-width backdrop hero; title (Logo image, else `brand-display`), facts, overview and actions sit over the image, always on `scrim`. Cast and More like this scroll below it.
- Exactly one primary button, and its label says what happens: "Play", "Resume · 38m left" (the time left; a round icon-only "start over" arrow sits beside it as the secondary button, only when there is something to resume), "Play S2 E4".
- Secondary actions (icon over label) after it: My List (a heart), Trailer (only when the item has one) and Watched (a tick); Download joins them when offline playback lands (hidden until then, since a button that does nothing is worse than none). Max four. There is no Like and no overflow menu.
- Series: a row of season buttons (about four visible, scrolling sideways) and the selected season's episode list below. **Must** open on the season of the episode Play / Resume starts, with that episode marked; picking a season swaps the list in place.
- Synopsis 3 lines + "More". Technical info (video, audio, subtitles, file) in a collapsed section at the bottom.
- A trailer the server holds plays in the player; one on the web opens straight in YouTube (or the system's viewer for another site). **Never** inline with sound on the detail screen.
- Opening a detail page: the screen dims at once, a skeleton shaped like the page shows, and the finished page fades up whole. **Never** a spinner, and never a page that builds itself piece by piece in front of the user.

## Playback

- Phones open the player full-screen landscape; rotation lock respected on exit. Tablets keep the current orientation.
- Controls auto-hide after 3s without input; a tap toggles them. The transport has three buttons: skip back, play / pause, skip forward. Defaults are 10s back and 10s forward; both are set per profile in Settings → Playback (back 5, 10, 15, 30; forward 10, 15, 30, 45, 60). Double-tap the left / right third seeks the same amounts with a visible "10s" label.
- The top bar holds only: close or back, the title with one meta line (episode, then the format badge as text), and the controls cluster (speed, audio and subtitles, picture in picture, AirPlay or Cast). Nothing else. Playback controls are never aqua: iOS is white on Liquid Glass, Android is `ink` on the scrim. Full spec in `player.md`.
- Scrubbing shows the trickplay thumbnail above the thumb with the timestamp; no trickplay data → timestamp only.
- Skip buttons ("Skip intro", "Skip recap") appear at segment start and stay for the whole segment, also when controls are hidden. **Never** auto-skip unless the user turned it on (Settings → Playback → Skip intros and recaps, off by default). Skip Credits shows at the Outro segment and gives way to up next when there is a next episode.
- Up next: at the Outro segment (or the last 30s without one) show the next-episode card with a 10s countdown, "Play now" and "Watch credits". At zero the next episode starts (about 20s early, the finished one counted as watched); "Watch credits" dismisses the card for that episode, which plays out and closes with no second countdown. A video under three minutes only counts down once it has ended. After 3 auto-played episodes without input, pause and ask "Still watching?".
- Remember audio and subtitle language per series; fall back to the user's Jellyfin preferences.
- **Must** report progress to the server on start, every 10s, on pause, seek and stop — and on app backgrounding.
- **Never** start playback with sound from a non-player screen.

## Navigation

- Four tabs: Home, Search, Library, Downloads. Profile avatar top-right on Home switches Jellyfin user.
- Re-tapping the active tab scrolls to top, then pops to the tab's root.
- Each tab keeps its own back stack; switching tabs never loses scroll position.

## Search

- Results after a 300ms debounce, posters in a 3-column grid; recent searches when the field is empty.
- Group by type (Movies, Shows, Episodes, People, Collections) once there are results in more than one; filter buttons (All, Movies, Shows, Episodes, People, Collections) narrow them. A person opens their own page, a collection its own page.
- Recent searches are kept per profile on the device, at most ten. Signing out clears that profile's list; an expired session keeps it, because the profile is coming back.

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
- **Never** gate playback behind a detail page from Continue watching: the picture always plays directly.
- **Never** infinite-scroll Home.
- **Never** block the UI on image loading; layout is final before artwork arrives.
