# Phone pass

Everything that has only been tried on the emulator, or only compiled, and so needs a look on a real phone. The debug build with all of Phase 4 and the tidy-up pass is installed on the phone; for smoothness use the `benchmark` build (`./gradlew :androidApp:installBenchmark`), because the debug build is slow on purpose.

Tick a box when it works. If something doesn't, see **What to send** at the end.

**Read this first.** The phone is signed into the real NAS. Anything marked **(changes watch state)** moves real progress, watched marks or My List on that server: do those on a title you don't mind changing, or on the throwaway test server. Nothing below has been done on the phone by the assistant.

## 1. Home

- [ ] Opening the app shows the hero's grey card straight away, in the card's exact shape, and the rows below don't slide in underneath it.
- [ ] The hero is a rounded card with the title's logo, a line of what it is and its genres, **Play** (aqua) beside **My List**. It never shows something you have already watched.
- [ ] Tap the hero picture: the title's page opens. **Play** on a movie starts it; on a show it starts the next episode **(changes watch state)**.
- [ ] My List on the hero changes only its icon (heart outline to filled heart): the picture, logo and Play button do not flicker or fade **(changes My List)**.
- [ ] Settings → "Featured titles on Home" to Off: the hero and its grey card are gone, with no gap. Back to 1 title: it comes back.
- [ ] Home with many genres: one row per genre, biggest first, rows appear a few at a time and nothing jumps. Settings → "Genre rows on Home" at Top 5 / 10 / 20 / All / None changes the rows to match.
- [ ] Shows, Movies, New and Categories open their pages; Categories opens a sheet of genres with counts, and a pick opens that genre's page.

## 2. Opening a title (detail page)

- [ ] Tap a poster: the screen dims at once, a skeleton shaped like the page shows, then the whole page fades up. No spinner, no piece-by-piece build-up, no poster flying across.
- [ ] Same for a show, an episode and a collection. The back button returns to the same place in the list.
- [ ] A movie shows My List, Trailer (only if it has one) and Watched under Play. Download is the fourth action (icon over label); see the Downloads section below.
- [ ] Watched ticks and un-ticks at once **(changes watch state)**.
- [ ] On a show: the season row opens on the right season, episode cards have all their text on the still, and a long press on an episode offers **View episode** and the watched mark.
- [ ] Cast: a tap opens the person's page (a round portrait or initials, name, born, biography with More/Less, Movies and Shows rows).
- [ ] Phone scroll: the picture stays pinned and darkens as the page scrolls over it, with no flicker or stutter.

## 3. Trailers

- [ ] On a title that has a trailer (the Trailer button shows): tap it. It opens **straight in YouTube** (or the system viewer), not in a pop-up inside the app.
- [ ] Long-press a poster of a title **with** a trailer: the menu opens at once with Details, My List, and a Trailer row that appears without the other rows moving.
- [ ] Long-press a poster of a title **without** a trailer: no Trailer row ever flashes up and disappears.
- [ ] Long-press a **collection**: the menu has Details and My List only, with no Trailer row or grey line at any point.
- [ ] Details opens the page; My List adds or removes and the row's text changes **(changes My List)**.

## 4. Library, Search and menus

- [ ] Library tab: posters three across, the title count under the name. Pick Movies / Shows / Collections / Sort / Genre: the old list dims and the count stays put until the new list is in, with no bounce.
- [ ] Year offers decades (2020s … Before 1980). People opens a sheet with a search field; typing a name finds actors and directors, and picking one lists their titles.
- [ ] A combination that matches nothing says "Nothing matches these filters…" and **Clear** puts everything back.
- [ ] Hold your finger on a row in the subtitle picker, the poster menu and a dropdown: the row turns accent-coloured and does not shrink or move.
- [ ] Picked rows in every sheet are a soft pill, not a tick. Chips are filled with no outline.
- [ ] Search tab: the keyboard opens at once. Typing shows results grouped under Movies, Shows, Episodes, People and Collections; the filter buttons narrow them; a person opens their page; a collection opens its page.
- [ ] Long-press a result in Search: the same menu as on a poster.
- [ ] Recent searches appear when the field is empty (ten at most), tap runs one again, the cross removes one, "Clear" removes all.
- [ ] Profile avatar → **Sign out**, then sign back in: the recent searches of that profile are gone. Just switching profile or being logged out by the server keeps them.

## 5. Screens that need a bigger screen (unfold the phone)

- [ ] Unfolded: the detail page shows the wide backdrop with the details in a left column; collections have the wide header.
- [ ] Unfolded: Search and the person page stay a readable width, centred, instead of stretching edge to edge. The long-press menu is a centred sheet.
- [ ] Fold and unfold while on a detail page and on Search: nothing is lost (the typed text, the scroll place).

## 6. Accessibility (not seen visually on any device yet)

- [ ] System font size at the largest: Home, Search, Library, a detail page, the person page and the poster menu still read without cut-off text or buttons off the screen.
- [ ] TalkBack on: posters read "Title, year"; a long-press poster says it can show options; chips and menu rows read as buttons; the person's name is a heading; the biography says it expands.
- [ ] The search field is announced as "Search", not just as an empty box.

## 7. Playback (owed since Phase 1) **(changes watch state)**

Do this on a title you don't mind changing, or on the local test server.

- [ ] HEVC plays on the phone's hardware decoder.
- [ ] Pressing Play: the player fades in with a slight grow, not an instant cut.
- [ ] Starting a title: the player is full screen, landscape, including the camera cut-out area; leaving it brings the page back without a long wait in landscape.
- [ ] Turn Wi-Fi off for about ten seconds mid-playback: it retries by itself, then offers "Try again" and does not switch to a transcode on its own.
- [ ] Resume: leave halfway, come back: Resume says the time left, and "start over" is the small round arrow beside it.
- [ ] Skip intro / recap buttons appear when the server has the segments, and stay for the whole segment.
- [ ] Up next: near the end the next-episode card counts down from ten and "Play now" starts it at once.
- [ ] Scrubbing shows the preview picture above the thumb.

## 8. Smoothness (use the `benchmark` build)

- [ ] Home scrolls without hitching with a big library and many genre rows.
- [ ] Opening and closing detail pages repeatedly feels the same each time.
- [ ] Library with a filter change doesn't flash or jump.

## What to send if something fails

- What you did, in a sentence, and what you expected.
- A screen recording if it's about motion (opening a page, scrolling, the hero loading); a screenshot if it's about layout.
- Whether it was the `debug` or `benchmark` build, whether the phone was folded or unfolded, and the font size setting.
- If the app closed: `adb -s R3GL9028R6N logcat -d -b crash` straight after (adb is at `~/Library/Android/sdk/platform-tools/adb`).
- Never send a token, password or server address; the log above doesn't contain them, but check before pasting.

## Not on this list, on purpose

- **iOS:** nothing here applies until the app is built in Xcode. The Xcode pass is its own task: see the roadmap's "iOS" notes. Known differences to look at then: the shared-element zoom is switched off to match Android, and every Swift file is parsed but never built.
- **Downloads (plan 14, phase C):** on a movie, Download starts at once and asks once for the notification permission; the ring fills, then a check, label "Downloaded". A show's Download opens a menu (season / all episodes); the confirmation shows count and size. The Downloads tab lists Movies and Shows, with progress, pause, resume and remove (confirmed). Turn the screen off and lock the phone mid-download and check it finishes. Use a small file on the real NAS only with the owner's say-so.
- **Offline (plan 14, phase D):** download an episode, switch on flight mode: the banner says "You're offline", the bar has three tabs, Home shows it under Continue watching, Search finds it, and it plays. Watch part of it, switch the network on: within moments the server shows the position (or the watched mark). Settings → Offline mode forces the same with a network; the banner's Go online ends it.
