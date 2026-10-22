# Plan 10: Home rows (second part of roadmap Phase 4)

Goal: Home shows the rows the design asks for, on Android and iOS: Continue watching (done), Recently added per
library, a few genre rows, and My media, each with its own loading, error and hidden-when-empty states and a "See
all" action in its header.

**Status (2026-10-22):** Phases A and B built (A with tests; B run on the Android emulator against the test server); C (iOS) is written and was only parsed (no Xcode). Calls marked *(my call)* are guesses for the owner to confirm.

**Specs:** `docs/ROADMAP.md` Phase 4 "Home rows", `docs/design/guardrails.md` "Home", `jellyfin-mapping.md` "Home
rows", `screens.md` "Home".

## Global constraints

- One phase at a time: build, test, summarise, stop for review (CLAUDE.md). `./gradlew :shared:allTests` (here
  `:shared:testAndroidHostTest`, no Xcode) and `./gradlew :androidApp:assembleDebug` pass before a phase is done. Say
  plainly what iOS code was only parsed.
- Branch: `feature/home-rows`, off `feature/tabs-and-home-filters`.
- Commit format, one line, no body: `task|fix|feat|chore: <short message>`.
- All text in `Strings`; colours only from tokens; aqua is never on a row or its header.
- 401 only through `ConnectionManager.expireSession`; every call goes through a repository's `call` helper.
- No new dependency.

## Decisions

1. **Row order** (`guardrails.md`): category buttons → Continue watching → Recently added, one row per library →
   genre rows → My media. The hero joins above Continue watching later in the phase.
2. **Recently added** is `GET /Items/Latest?parentId={library}`, 20 items, posters, episodes grouped by series (the
   server's `groupItems`). One row per library that holds videos: collection type movies, tvshows, homevideos,
   musicvideos, boxsets, or none (mixed). Music, books, photos, playlists and live TV are left out. Titled "Recently
   added in {library}".
3. **Genre rows** *(my call)*: the three genres with the most titles (movies and shows together, from
   `GET /Genres` counts, the list the Categories sheet already loads), each `GET /Items?genreIds=` by community rating,
   best first, 20 items. Until Phase 7 lets the user choose rows this is the default.
4. **My media** is the libraries as 16:9 tiles in one row at the end, titled "My media" (was "Libraries" in a grid).
   Every library, not only video ones.
5. **"See all"** in a header: Recently added opens that library's list (the folder list that already exists); a genre
   row opens that genre's category page. Continue watching and My media have none.
6. **Cap:** at most 12 rows on Home (`guardrails.md`), counting Continue watching and My media; genre rows give way first,
   then the oldest libraries' Recently added.
7. **States:** a row loads on its own; a skeleton of posters while loading, its own message with Try again when it
   fails (the other rows are untouched), and nothing at all when it came back empty. A library or genre that is empty
   shows no row.
8. **Refresh:** pull to refresh reloads Continue watching first, then the other rows. A new profile or address
   reloads everything; Home itself never keeps old rows from another profile.
9. **Rows appear top to bottom** *(added after review)*: the rows still load at the same time, but each is shown as grey
   posters until every row above it has finished, Continue watching first, and My media comes in last. A row that
   turns out empty leaves only once it is reached, so nothing below it jumps. Rows that arrive or leave slide and fade.
10. **A row keeps one height** *(added after review)*: a poster's caption always takes two lines in a row (one in My media),
    and the grey posters reserve the same, so a long title scrolling into view never pushes the rows below it down.
    A row's header is one line.
11. **Row source of truth in `shared/`:** `HomeUiState.rows`, a list of `MediaRow` (id, title, items, loading, message,
   see-all target). The screens only draw rows and forward taps; the same row type will take Seerr rows in Phase 8 and
   the user's own rows in Phase 7.

## Phase A: Shared rows

- `JellyfinApi.latestItems`, `BaseItemDto.collectionType`, `MediaItem.collectionType`.
- `LibraryRepository.latest(libraryId)` and genre rows through `filteredPage`.
- `HomeViewModel`: build the rows after the libraries and genres load, load each independently, retry one row, refresh,
  the cap, the order. Strings.
- Tests with `MockEngine`: the order, hidden empty rows, one row failing while others load, retry of one row, the cap,
  a new session starting clean, a 401.
- Done when `:shared:testAndroidHostTest` passes.

## Phase B: Android

- Row sections (header with "See all", poster row, skeleton, error), My media as a row; remove the Libraries grid.
- Run on the emulator against the test server; add what the test server needs (a few movies already exist).

## Phase C: iOS

- The same rows in SwiftUI, from `Strings.shared`. Only parsed here (no Xcode).

## Review focus

- A library with nothing in it, a server with one library, a server with no genres: no empty rows, no crash.
- One row failing: the rest of Home is untouched and the failed row offers Try again.
- Pull to refresh, switching profile, and coming back from the player: rows are current, no duplicates.
- Rows keep their order while they load (nothing jumps when a row turns up).

## Out of scope

Choosing, ordering or hiding rows (Phase 7), My List and the hero (later in Phase 4), Library and Downloads content.
