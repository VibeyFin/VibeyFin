# Plan 11: Detail pages (third part of roadmap Phase 4)

Goal: a movie, an episode and a series each open on a cinematic page: a full-bleed backdrop hero with the Logo as the
title, the facts, the overview and one aqua Play / Resume button over it, then the cast and "More like this"; a series
adds a row of season buttons and the selected season's episodes. Android and iOS.

**Status (2026-10-22):** Phases A and B built (A with tests; B run on the Android emulator against the test server, including a simulated unfolded-foldable screen); C (iOS) is written and was only parsed (no Xcode). Calls marked *(my call)* are guesses for the owner to confirm.

**Specs:** `docs/ROADMAP.md` Phase 4 "Detail page" and "Series detail page", `docs/design/screens.md` "Detail",
"Series detail", "Episode row", `guardrails.md`, `jellyfin-mapping.md` "Detail".

## Global constraints

- One phase at a time: build, test, summarise, stop for review (CLAUDE.md). `./gradlew :shared:allTests` (here
  `:shared:testAndroidHostTest`, no Xcode) and `./gradlew :androidApp:assembleDebug` pass before a phase is done. Say
  plainly what iOS code was only parsed.
- Branch: `feature/detail-pages`, off `feature/home-rows`.
- Commit format, one line, no body: `task|fix|feat|chore: <short message>`.
- All text in `Strings`; colours only from tokens; aqua is the Play / Resume button and playback progress, nothing else.
- 401 only through `ConnectionManager.expireSession`; every call through a repository's `call` helper.
- No new dependency. The five states on every part of the page (the page, the cast, similar, the seasons, the episodes).

## Decisions

1. **One screen for movie, episode and series**, as today (`ItemRoute`), so every poster, card and "See all" that opens an
   item keeps working. A series adds its seasons and episodes below the hero.
2. **Hero** (`screens.md`): the item's `Backdrop`, centre-cropped to 2:3 on phones, under the status bar; the `scrim`
   gradient so text always sits on `scrim`; the `Logo` image as the title, else the `brand-display` title; the facts line
   centred under it; the overview, 3 lines with "More"; Play / Resume full width, with a round "start over" beside it
   once there is something to resume. Mark as watched stays in the overflow menu.
3. **Actions under Play** *(added after review)*: My List (the server's favourite, optimistic, reverts with a message on failure),
   Trailer (only when there is one: a trailer the server holds plays in the app, one on the web opens in the system viewer) and
   Download (does nothing until offline lands). Like, Share and Cast stay out.
4. **Cast** is `People` of type Actor and Guest star, round `Primary` image, name, role; a missing image is a grey disc with
   initials. A cast member does nothing when tapped until the Person page item lands *(my call)*, and is drawn as plain
   content, not a button.
5. **More like this** is `GET /Items/{id}/Similar` (12), the same poster row as Home's rows (same component, one
   height), hidden when empty.
6. **Series Play / Resume** starts the next-up episode (`GET /Shows/NextUp?seriesId=&enableResumable=true`), else the
   first episode of the first real season; the label says which: "Play S2 E4", "Resume S2 E4 from 12:30" *(wording is mine)*.
   The player is opened for that episode.
7. **Seasons** are `GET /Shows/{id}/Seasons`, one button each in the server's order (Specials included), about four
   visible and the next peeking; it opens on the season of the episode Play will start, scrolled to it. Tapping another
   swaps the list in place and keeps the old list on screen until the new one loads. A series with one season still shows
   the row *(my call: it is what Jellyfin's own client does)*.
8. **Episodes** are `GET /Shows/{id}/Episodes?seasonId=`: a thumbnail, "1. Title", runtime, a watched check or the time
   left, and two lines of synopsis; the next-up episode is marked current. Tapping one plays it. Long-press has the
   watched toggle *(my call)*. Download is not shown (offline is a later phase).
9. **Technical info** (video, audio, subtitles, file) collapsed at the bottom *(my call: only when the item has streams)*.
10. **Tablets and unfolded foldables** (width from 600dp, `BoxWithConstraints`, no new dependency; `horizontalSizeClass == .regular` on iOS) get the tablet layout of `screens.md`: the backdrop at its own 16:9, the details in a left column about 46% wide (max 520) over a scrim from the left, Play not full width, larger margins and posters (168), the cast at 112, and a season's episodes in two columns from 840dp. This was added after the owner saw the phone layout stretched on a Z Fold. The main navigation stays at the bottom for now (the top placement is the width-class work in the design review).
15. **Four actions, a collection page** *(added after review)*: the actions are four equal columns, My List (a plus, a tick once
    on the list), Trailer, Watched (a tick, filled once watched; it replaces the overflow menu) and Download. A collection opens on
    its own header (picture, title, "N titles", overview) with its movies and shows under sub-headings; on a wide screen the header
    is a 21:9 picture with the text in a column on the left. Collections are always tall posters.
12. **Reaching the show and collections** *(added after review)*: an episode's or season's page and a season's list lead back to the
    series by its name; a movie or series shows the collections it is in (`GET /Items/{id}/Collections`) as a poster row.
13. **Season buttons** always read "Season N" ("Specials" for 0). The overflow and long-press menus use the app's rounded menu.
14. **Tablet backdrop and episodes** *(added after review)*: the backdrop fills the screen and stays put while the page scrolls over
    it, darkening to nearly the page colour by 70% of a screen; a season's episodes are a horizontal row of cards on every screen (260dp on a phone, 320dp on a tablet).
11. **Facts line** adds the number of seasons for a series ("3 seasons") instead of a runtime.

## Phase A: Shared

- DTOs and models: `Logo` image, `People`, season count; `MediaItem.logoUrl`, `MediaItem.cast`, `CastMember`.
- `LibraryRepository`: `similar`, `seasons`, `episodes`; `WatchRepository`: `nextUp(seriesId)`.
- `ItemViewModel`: the cast, the similar row, the series' seasons, the selected season, its episodes, the play target
  and its label; events for choosing a season and for retrying each part.
- Tests with `MockEngine`: every call's query, the play target and label cases (movie, episode, series never started,
  half-watched, all watched), the season opening on the next-up episode, switching seasons keeping the old list, each
  part failing alone, a 401.

## Phase B: Android

- The hero, the cast and similar rows, the season row and the episode rows, the technical info; run on the emulator
  against the test server (add a show with a few seasons and a Logo/Backdrop to the fixtures if needed).

## Phase C: iOS

- The same in SwiftUI from `Strings.shared`. Only parsed (no Xcode).

## Review focus

- No Logo, no Backdrop, no overview, no cast, no similar: the page still looks right.
- A series with Specials, one season, or an episode with no thumbnail.
- Play on a half-watched episode, on a finished series, and from an episode page: the right episode, the right label.
- Switching seasons quickly: the last tap wins, no old list replaces a newer one.
- Coming back from the player: the Resume label and the episode list's marks are current.

## Out of scope

Like, Share and Cast buttons; the Person page; special features; shared-element
transitions and the ambient background (their own items).
