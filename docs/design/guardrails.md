# UX guardrails

Hard rules per screen, the same on iOS and Android — they describe behavior, not looks. **Must** and **never** are non-negotiable in v1; anything else is a default you may change with a reason. Copy examples are in sentence case; iOS shows them in title case (see **Voice**).

## Home

- **Must** order rows: hero (optional, up to five items in one carousel) → Continue watching (Next up is part of it, not a row of its own) → Recently added (one row per library) → collections / genres.
- **Must** show the category buttons under the header, above the hero, as one scrolling row in this order: Shows, Movies, New, Categories. Home is never filtered. Shows, Movies and New open a category page titled with their name; Categories carries a chevron and opens a sheet of genres (the player's picker component, no search), and the genre picked opens a page titled with its name. Copper is never used on the buttons.
- **Must** show Continue watching above the fold on a 6.1" phone, even with the hero present.
- **Must** hide empty rows. Never show a row that says "No items" on Home.
- Each row holds at most 20 items, then a "See all" action in its header.
- Home has no fixed cap on rows: every genre the server lists gets one (biggest first), unless the profile limits them in Settings ("Genre rows on Home": all, top 20, 10, 5 or 3, or none). A new profile starts at the top 5; a profile from before the setting existed keeps all. Rows load a few at a time and show from top to bottom.
- Hero: as many items as the profile's setting ("Featured titles on Home": Off, 1 to 5; 1 by default), picked once per app session at random from recently added and My List, each with both a Backdrop and a Logo image and **not** watched, never one twice. Fewer qualify: only those show. A rounded card (4:3 on a phone, 2.6:1 from 600dp) with Play beside My List, acting on the item showing. Two or more are a swipeable carousel with page dots under the card (never copper). It advances by itself every 8 seconds and goes round: after the last comes the first, and swipes go round the same way. A swipe or a long press stops it for good, a touch pauses it, it runs only while Home is on screen, and it **never** moves under Reduce Motion / Remove animations or with a screen reader on. Its skeleton is in the first frame, and Home waits for the setting before drawing, so nothing slides in.
- Pull to refresh reloads Continue watching first.

## Cards

- Poster tap → detail. Continue-watching picture tap → **resume playback directly**; its title and its info button → detail.
- Long-press on a poster, or on a Home hero card → a menu (a bottom sheet on Android, a native `.contextMenu` on iOS so the finger can slide to a row; see **Poster menu** in `screens.md`) with Details, Trailer (only when the title has one), My List and Download (on iOS in the order Details, Trailer, My List, Download; Android keeps Details, Trailer, Download, My List). The rows are there when the menu opens; a title with no trailer never shows a Trailer row that then disappears (on iOS a greyed "Checking for trailer…" row holds its place while the server is asked). It works the same in search results. Only a movie or a show is ever looked up for a trailer: a collection, season or episode shows no Trailer row and no grey line, and nothing is asked of the server. Episodes (a row on a phone, a card on a tablet) play from the picture and open the episode's page from the words, and have their own long-press menu: View episode and the watched mark.
- Long press or "…" on a Continue watching card → its menu: Details, Mark as watched, Remove from Continue watching (a card in progress only) and Download (when the server lets the user). Remove takes the card out at once and offers Undo; if the server refuses, the card comes back with a message. **Never** a confirmation and never `danger`: it takes away no history. Offline the card has no menu.
- A row of a menu or sheet that is held (a subtitle picker, the poster menu, a dropdown) lights up in `spark-soft`; it does not shrink or move. Buttons and cards elsewhere still dip slightly when pressed.
- Pressing Play opens the player with a short fade and a slight grow from the page behind it, never an instant cut.
- Progress bar only while in progress (between the server's resume thresholds). Watched items get the check badge, no bar.
- **Never** more than one badge per card. Priority: New episode > 4K / HDR > rating.

## Detail

- Opens on a full-width backdrop hero; title (Logo image, else `brand-display`), facts, overview and actions sit over the image, always on `scrim`. Cast and More like this scroll below it.
- Exactly one primary button, and its label says what happens: "Play", "Resume · 38m left" (the time left; a round icon-only "start over" arrow sits beside it as the secondary button, only when there is something to resume), "Play S2 E4".
- Secondary actions (icon over label) after it: My List (a heart), Trailer (only when the item has one) and Watched (a tick); Download is the fourth, shown only when the server lets the user download. Max four. There is no Like and no overflow menu. They sit in four places of one width (the widest label's), the first at Play's left edge and the fourth ending at its right edge, evenly spread; with fewer (an episode has no trailer) they keep those places and the last stay empty. Phones and tablets alike.
- Series: a row of season buttons (about four visible, scrolling sideways) and the selected season's episode list below. **Must** open on the season of the episode Play / Resume starts, with that episode marked; picking a season swaps the list in place.
- Synopsis 3 lines + "More". Technical info (video, audio, subtitles, file) in a collapsed section at the bottom.
- A trailer the server holds plays in the player; one on the web opens straight in YouTube (or the system's viewer for another site). **Never** inline with sound on the detail screen.
- Opening a detail page: the screen dims at once, a skeleton shaped like the page shows, and the finished page fades up whole. **Never** a spinner, and never a page that builds itself piece by piece in front of the user.
- **A skeleton is the real view with placeholder data:** the same composable or SwiftUI view the content uses, fed `Placeholders` and drawn as grey shapes, so its size can't drift from the real one. **Never** a second hand-built skeleton layout. Image shapes (poster, backdrop, episode still) read `MediaAspect`; a row draws `PlaceholderCount.forRow` (the visible width plus one peeking tile, the real count when known) and a grid fills the screen height.

## Playback

- **iPhone is portrait only outside the player:** every other screen stays upright however the phone is held (iPad turns freely). The player is the one place that asks for landscape. Android phones are not locked.
- The player opens full-screen landscape on phones and tablets alike; the orientation from before is put back on exit, and the user's rotation lock is left alone. (Where the system refuses the request, the player stays in the orientation it is held in: an iPad whose app is windowed (iPadOS 26) or in Split View or Slide Over, and an Android 16 tablet, which can ignore orientation requests.)
- **Android, where the screen has to turn** (a phone or a foldable's cover screen held upright): opening and closing are one movement, like YouTube, using the system's own rotation. The old screen and the new one rotate together while cross-fading, with no separate grow or shrink step and no pause; the page or the picture comes straight in during the turn. The player and the page swap with no transition of the app's own, which would fight the rotation. Where the screen doesn't turn (an inner foldable screen or tablet that ignores the request, a split window, a phone already held sideways) there is no wait and the usual fade and grow apply.
- Controls auto-hide after 3s without input; a tap toggles them. The transport has three buttons: skip back, play / pause, skip forward. Previous and Next episode sit in the top bar for an episode (dimmed on the first and last). Defaults are 10s back and 10s forward; both are set per profile in Settings → Playback → Skipping and autoplay (back 5, 10, 15, 30; forward 10, 15, 30, 45, 60), where Buffer ahead is set too, in its own group (Auto, 10, 20, 30, 40, 50, 60 s; default 20 s). Double-tap the left / right third seeks those amounts, and while the total shows each single tap there adds another. The total shows on that side as "+30 >" / "< −30" with only the scrubber left on screen; skips in a row to the same side add up, and the total clears 1s after the last skip or when the other side is tapped. Seeking never toggles the controls: a run begun by double taps hides them until it ends, one begun on a skip button keeps them up, and either way taps on the sides and the buttons add to the same total. The total counts how far the video moved, so a skip that hits the start or the end counts less than its step. The shared `PlaybackUiState.skipRun` owns the count; both apps only draw it.
- The top bar holds only: close or back, the title with one meta line (episode, then the format badge as text), and the controls cluster (speed, audio and subtitles, picture in picture, AirPlay or Cast). Nothing else. Playback controls are never copper: iOS is white on Liquid Glass, Android is `ink` on the scrim. Full spec in `player.md`.
- The scrubber draws how far the stream is loaded: a white segment at 40% opacity (`overlay-strong`) between the copper played part and the track, ending at the loaded position, never before the played part. While the player stalls to load, a spinner replaces the transport buttons; controls stay up.
- Scrubbing shows the trickplay thumbnail above the thumb with the timestamp; no trickplay data → timestamp only.
- Skip buttons ("Skip intro", "Skip recap") appear at segment start and stay for the whole segment, also when controls are hidden. **Never** auto-skip unless the user turned it on (Settings → Playback → Skipping and autoplay → Auto skip intros and recaps, off by default). Skip Credits shows at the Outro segment and gives way to up next when there is a next episode.
- Up next: at the Outro segment (or the last 30s without one) show the next-episode card with a 10s countdown that stands still while the video is paused, and equal-size "Play now" and "Watch credits" buttons. For a series the card replaces Skip Credits. At zero the next episode starts (about 20s early, the finished one counted as watched); "Watch credits" dismisses the card for that episode, which plays out and closes with no second countdown. A video under three minutes only counts down once it has ended. After 3 auto-played episodes without input, pause and ask "Still watching?".
- Remember audio and subtitle language per series; fall back to the user's Jellyfin preferences.
- **Engine (iOS):** AVPlayer or Aether, chosen per title in the shared model, never by the user and never named beside Play. AVPlayer first; if the server would not direct play it, Aether; if neither can, the server's stream in AVPlayer. Android has the one engine, ExoPlayer.
- **Must** report progress to the server on start, every 10s, on pause, seek and stop — and on app backgrounding.
- **Never** start playback with sound from a non-player screen.

## Settings

- **Where:** under the profile avatar on Home, never a tab. Per-profile groups come first, the one device-wide group ("This device") after them. Settings are stored per profile; only "Always ask who's watching" is device-wide.
- **Playback group:** Quality, Audio and subtitles (languages and subtitle style on one page), Skipping and autoplay. No root Buffering row; Buffer ahead is on Skipping and autoplay in its own group.
- **Choices:** a choice is a menu in its row (iOS `Picker(.menu)`, Android dropdown) and never opens a sub-page. Only Quality is a checklist, shown inline in its own section.
- **Quality:** a per-profile cap, Maximum by default (the original, direct play when possible), then 4K 40, 1080p 10, 720p 4, 480p 1.5 Mbps. It applies to the next stream. No quality menu in the player and no per-network cap.
- **Subtitle style** follows the OS caption style unless the user turned "Use system caption style" off and chose one. iOS burns subtitles in today, so its style controls are disabled with a note until that is solved.
- **Sign out** is on the Profile page, asks for confirmation (which says the profile's downloads are deleted), clears the token, deletes the profile's downloads from the device and keeps the profile's tile locked in "Who's watching?". An expired session (401) deletes nothing: that profile is coming back. It runs to completion even if the user leaves the screen (it is not tied to the screen's lifetime). It is the only way to clear a token besides a 401. Never on a kids profile.
- **Tablets:** two panes at regular width; the selected root row follows the tapped row; Quality is selected on open.
- **PIN** (Settings, Kids and PIN group, **Kids profile** row): 4 digits, optional, per profile, a salted hash, never the Jellyfin password. A household lock, not security, and never presented as security. It is independent of the kids flag. Turning kids mode off, changing the PIN and removing it always ask for the PIN. "Forgot PIN?" checks the Jellyfin password with a normal sign-in and removes the PIN.
- **Developer (debug builds only):** the last group of device Settings, with a "Show skeletons" switch that holds every loading screen on its skeleton. Release builds don't have it. A dev flag's label is a literal in code, not a `Strings` key (precedent: `PseudoLocale.language`).

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
- Android 17 and up: the system asks for local network access when the app opens, before anything connects. The app's screens wait for the answer, because a connection made earlier stays failed after Allow. Refusing still opens the app, with the "Can't reach the server" banner for a server on the local network.
- iOS has no network search until VibeyFin holds Apple's multicast entitlement; its first screen asks for the address and never shows an empty "On your network".
- Quick Connect shows only when the server says it is on (`/QuickConnect/Enabled`), on the grid and in the password sheets. It signs in whoever approves the code. Closing its sheet stops the polling; coming back to the app resumes it.
- Password fields allow paste, password managers and show/hide. The password is sent once and dropped; only the token is kept.
- A sign-in error is said under its field, in `danger` with an icon, the text kept. A session that ended (401) is a note in `ink-muted`, not an error.
- A saved address that now answers as a different server (another server ID from `/System/Info/Public`) is skipped as if it didn't answer, and a 401 from it never locks a profile.
- Removing a server asks first, then signs out its profiles on this device (their tokens and downloads go); nothing changes on the server. A server's last address can't be removed on its own.
- Multiple users on the server → profile picker on launch, remembered per device.
- A tapped notification never signs a profile in and never bypasses a PIN: it opens its title only for the profile signed in now. For another profile the app either does nothing (iOS) or goes to "Who's watching?" (Android, only when the signed-in profile's PIN doesn't guard switching), where that profile is picked the usual way.

## Offline and errors

- **Downloads are kept exactly as the server sent them**: nothing is converted on the device, so there is no "Converting" state. A video no engine on the device can play is refused before it downloads, with "This video is in a format this device can't play, so it can't be downloaded here." A download that fails for another reason says "The download failed. Remove it and try again." (not a connection problem). Offline there is no server to fall back to: a file the chosen engine can't open ends in the playback error. Only subtitles that came as separate files are offered offline.
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
