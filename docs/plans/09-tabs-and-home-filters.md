# Plan 09: Tabs and Home category buttons (first part of roadmap Phase 4)

Goal: Home, Library and Downloads as three tabs with their own back stacks, and the Home category
buttons (Shows, Movies, New, Studios, Categories) that filter Home in place, on Android and iOS.

**Status (2026-10-22):** Phases A and B built on `feature/tabs-and-home-filters` (A run on the Android emulator, iOS only parsed; B is `shared/` with tests); C and D not started. The state audit (skeletons) is done on
`feature/state-audit`; this plan builds on it. Calls marked *(my call)* are guesses for the owner to confirm.

**Scope.** Only the first two Phase 4 items that were reworked: the tab bar and the category buttons. Home
rows (Latest, genres, My Media), the detail pages, search, person pages, My List and the rest of Phase 4
get their own plans, one per group, so each is reviewed on its own.

**Specs:** `docs/ROADMAP.md` Phase 4, `docs/design/screens.md` ("Home", "Category buttons", "Home with a
filter", "Chrome"), `guardrails.md`, `jellyfin-mapping.md` ("Home category buttons").

## Global constraints

- One phase at a time: build, test, summarise, stop for review (CLAUDE.md). `./gradlew :shared:allTests`
  (here `:shared:testAndroidHostTest`, no Xcode) and `./gradlew :androidApp:assembleDebug` pass before a
  phase is done. Say plainly what iOS code was only parsed.
- Branch: `feature/tabs-and-home-filters` off `develop`, after `feature/state-audit` is merged.
- Commit format, one line, no body: `task|fix|feat|chore: <short message>`.
- All text in `Strings` (sentence case; iOS title-cases with `titleCased()`). Colours only from tokens;
  aqua is never used on the buttons, the sheets or the tab bar's selection beyond native tinting on iOS.
- Filter state is never stored: Home opens unfiltered on every launch.
- 401 only through `ConnectionManager.expireSession`; every new call goes through `LibraryRepository`'s
  `call` helper.
- No new dependency. Navigation Compose already supports a back stack per tab (`saveState`/`restoreState`).

## Decisions

1. **Tabs are shell, not content.** `Home`, `Library`, `Downloads` are routes inside one tab shell; Settings
   stays under the avatar and opens over the shell. Detail screens (`Item`, `Children`, `Player`) open from
   whichever tab is current and belong to that tab's back stack. *(my call: the player stays above the shell,
   full screen, outside any tab.)*
2. **Library and Downloads** show a "Not available yet" page (`Strings.notAvailableYet`) with the five states
   not applying; they are placeholders, nothing else is built.
3. **Re-tapping the current tab** pops it to its root, and a second tap on Home's root scrolls to the top
   *(my call, platform convention)*.
4. **iOS mini player:** on iOS 26+ it moves to the tab bar's bottom accessory (`tabViewBottomAccessory`); on
   iOS 16 to 25 it keeps today's `MiniPlayerView` placement above the tab bar. Behind `#available`, no custom
   fallback for the glass.
5. **One filter at a time** as a sealed `HomeFilter` in `shared/`: `Shows`, `Movies`, `New`,
   `Studio(id, name)`, `Genre(id, name)`. `HomeViewModel` holds the active filter and exposes it in
   `HomeUiState`; screens only draw it and send events (`onFilter`, `onClearFilter`, `onPickStudio`,
   `onPickGenre`).
6. **Sheets load lazily** when opened (`GET /Studios`, `GET /Genres`), cached for the session of the Home
   screen; loading is a skeleton list, failure shows `LoadError` with Retry, empty says so.
7. **New** uses the server's creation-date filter if the OpenAPI spec has one; otherwise the client keeps
   items with `DateCreated` in the last 30 days from a `DateCreated`-descending `GET /Items` page. The clock
   is injected (`kotlin.time.Clock`), as CLAUDE.md requires. *(Check the spec in Phase B before choosing.)*
8. **Continue watching under a filter:** use server-side filters on `/UserItems/Resume` and `/Shows/NextUp`
   if the spec supports them; otherwise filter the already fetched cards on the client by type, genre and
   studio (for an episode, its series' genres and studios). *(Check the spec in Phase B.)*
9. **The hero** (when Home has one) is picked again from the filtered items, once per filter change. Until
   the hero exists, a filter only changes the rows.

## Phase A: Tab shell

- `shared/`: `Strings` for the three tab labels, "Not available yet" and the accessibility name of the bar.
- Android: a `TabShell` with an M3 `NavigationBar` (a `NavigationRail`-style top placement at tablet widths is
  *(my call)* left to the width-class work, noted in the review), one `NavHost` per tab so each keeps its back
  stack; `Home` becomes the first tab's root; the bar hides on `Player`, Who's watching and Servers.
- iOS: a `TabView` with a `NavigationStack` and its own path per tab; `RootView` keeps the pre-sign-in stack.
  The mini player placement as in Decision 4.
- Tests: route and tab-state logic that lives in `shared/` (none expected beyond Strings); a Compose or UI
  test is out of scope (needs a framework the owner has not approved).
- Done when: switching tabs keeps each one's position; back from a detail screen returns to the tab it came
  from; re-tapping pops to root; the bar is absent in the player. Shown on the emulator; iOS parsed only.

## Phase B: Home filters in `shared/`

- Check the OpenAPI spec (the server's `/api-docs/openapi.json`) for: a creation-date filter on `GET /Items`,
  filters on `/UserItems/Resume` and `/Shows/NextUp`, and `studioIds` on `/Items`. Record the answers in
  `docs/design/jellyfin-mapping.md`.
- `JellyfinApi`: `studios()`, `genres()` and the filter parameters on the items call, with DTOs.
- `LibraryRepository`: `studios()`, `genres()`, `filteredItems(filter, ...)`; `HomeViewModel`: the active
  filter, the sheet state, the refetch on change that keeps old content until the new has loaded.
- Tests with `MockEngine`: each filter builds the right query; New keeps exactly the last 30 days with a fixed
  clock; a 401 goes through `expireSession`; a failed sheet load keeps the active filter; clearing restores
  the unfiltered rows; empty rows are dropped.
- Done when: `:shared:testAndroidHostTest` passes with these.

## Phase C: Android buttons and sheets

- The button row (`LazyRow` of `FilterChip`s per `android.md`), a `ModalBottomSheet` with the list for Studios
  and Categories, selected state, the picked name in the button, the clear (×), scroll-into-view, and the
  skeleton, empty and error states for the sheets. Strings for all of it.
- Done when: on the emulator against the test server, each of the five buttons filters Home, one at a time,
  rows left empty hide, and the sheets show loading, empty and error. Add fixtures for studios and genres to
  `scripts/test-server/` and list them in `docs/test-server.md` (plan 08's convention).

## Phase D: iOS buttons and sheets

- The capsule row, a `.sheet` with detents for Studios and Categories, `ScrollViewReader` to the active
  button, the same states as Android, reading `Strings.shared`.
- Done when: the iOS code compiles to klibs and parses; **not built or run** without Xcode, and said so.
  The owner runs it in Xcode, including the mini player on iOS 26.

## Review focus

- A server with no studios or no genres: the sheet says so, nothing crashes.
- Switching filters quickly: the last tap wins, no stale response replaces newer content.
- A 401 while a sheet is loading: one route to "Who's watching?".
- Rotating or switching tabs with a filter active: the filter stays until the app is relaunched.
- Tab back stacks: open an item from Home, switch to Library and back, the item is still there.

## Out of scope

Configuring the button order or hiding buttons (Phase 7), Library and Downloads content, search, the other
Phase 4 items, and tablet top-placed navigation beyond what `screens.md` "Chrome" already requires.
