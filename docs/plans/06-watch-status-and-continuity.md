# Plan 06: Roadmap Phase 2, watch status and continuity

Goal: remember what the user watched. Resume where they stopped (on any device), show Continue watching on Home,
let them mark things watched, and move on to the next episode by itself. Logic is shared; every screen is native.

**Status (2026-10-20):** approved. All five phases (A to E) are done; this is for review. Each phase stops for review.

Paths: `C/` = `shared/src/commonMain/kotlin/com/liamgleeson/vibeyfin`, `T/` = `shared/src/commonTest/...` (same package),
`A/` = `androidApp/src/main/kotlin/com/liamgleeson/vibeyfin/app`, `I/` = `iosApp/iosApp`, `X/` = `shared/src/iosMain/.../ios`.

## Context

Phase 1 (core playback) merged to `develop` as PR #3, so the app can play but forgets everything: it always starts at 0,
shows no Continue watching, can't mark anything watched, and a crash leaves the server showing the item as playing.
Phase 2 closes that and is the second half of the MVP (log in, play, **resume across devices**). Exit: finish an episode on one
device, resume it on another.

Six roadmap items, built as five reviewable phases (CLAUDE.md: finish one, build, test, summarise, **stop for review**).
First step after approval: cut `feature/watch-status` from `develop` (not `main`), and commit this plan as
`docs/plans/06-watch-status-and-continuity.md` (`task: add plan 06 for watch status`).

| Phase | Roadmap item |
|---|---|
| A | `Stopped` after a crash or killed process (first item, per roadmap and plan 03) |
| B | Continue Watching row (Resume + Next Up), with the refresh mechanism it needs |
| C | Resume on open, "Resume from 41:12", "Start over", 5 s rewind |
| D | Mark played/unplayed, optimistic user-data writes |
| E | Auto-advance with countdown |

## Decided with you / already decided

- **Countdown (you chose):** card with a 10 s countdown and "Watch credits" appears in the last 30 s (`guardrails.md`). At zero
  the next episode starts, about 20 s early, and the finished episode is reported at its end position so the server marks it
  watched. "Watch credits" plays on to the end with no second countdown. Phase 3 only swaps the trigger for the Outro segment.
- **Card to details (you chose):** tap plays at once; long-press shows "Details" and "Mark as watched" (iOS context menu,
  Android bottom sheet). No info button.
- **Resume button (roadmap, your "yes" on Q10):** aqua "Resume from 41:12" with "Start over" beside it. This overrides the
  design docs' "Resume · 38m left" (`guardrails.md:25`, `screens.md:30`); fix those docs in Phase C.
- Server position is the source of truth; no per-device positions. Auto-advance is on by default.

## My calls (change any of these)

1. **One table for both uses.** `pending_reports` (Room v2 to v3, `AutoMigration(2,3)`, new `schemas/.../3.json`): `id`,
   `serverId`+`userId` (FK to `accounts`, cascade), `kind` (only `stopped` now; Phase 10 adds played marks and progress),
   `itemId`, `mediaSourceId`, `playSessionId`, `positionTicks`, `failed`, `updatedAt` (injected `Clock`). Unique on
   (profile, `playSessionId`, `kind`). Roadmap Phase 10 asks for exactly this reuse.
2. **A record older than 24 h is dropped unsent.** A late `Stopped` carries a position and Jellyfin saves it, so one from last
   week would overwrite a newer position watched on another device.
3. **`Stopped` is always sent as the profile that started it.** New `ConnectionManager.asProfile(serverId, userId) { api -> }`
   (`C/domain/ConnectionManager.kt`) reuses `AccountRepository.sessionToken` and `ServerConnector.firstAnswer`, returning
   `Answered(result)`, `NoSession` (locked: drop) or `Unreachable` (keep). A 401 goes through the same expire path as
   `expireSession`, generalised to a stored profile, so tokens stay owned by `ConnectionManager`. This also fixes today's
   hole: `PlaybackReporter.call` drops `Stopped` silently after a profile switch or expiry.
4. **Staleness fix.** The reporter's `stop()` is fire-and-forget, so a screen that reloads on return can read the old position.
   New singleton `WatchChanges` (`SharedFlow<Unit>`): the reporter fires it after each `Stopped` attempt, Phase D after each
   write. Home and the item page reload on it. Plus pull-to-refresh and a refresh on returning to the foreground if the last
   load is over 60 s old (`Clock`), which is what makes "resume on another device" show up.
5. **Resume lookup failure blocks playback.** Starting at 0 on a failed item read would later report a small position and
   overwrite the real one, so the player shows the load message and "Try again". Costs one extra request before playback
   (sequential, simple).
6. **Next episode comes from `GET /Shows/{seriesId}/Episodes?startItemId=&limit=2`, not `/Shows/NextUp`.** NextUp may still
   return the episode being watched, because `Stopped` hasn't reached the server yet.
7. **Routes:** `/UserItems/Resume` and `/UserPlayedItems/{id}` (exist from 10.9, our minimum is 10.10) instead of the roadmap's
   older `/Users/{id}/...`. Verified against a real server before any code; fall back if absent. `/Shows/NextUp` with
   `enableResumable=false`.
8. **Retries:** played writes use the existing `RetryPolicy.Default` (3 tries, 400/800 ms). The roadmap says "two tries"; fix its
   wording. Use `send(..., retry = true)`; `post<B,T>` can't retry.
9. **Auto-advance** is a `PlaybackViewModel` constructor parameter (default true), not a settings column; Phase 7 wires it to
   the profile setting.
10. **Continue card:** 16:9 (`Thumb`, else `Backdrop`, else `Primary`), title = series name for an episode, subtitle (time left when started: "S2 E5 · 38m left", "38m left" for a movie; the episode and title for Next Up),
    aqua progress bar on in-progress cards only, label `"{title}, {year}, {n}% watched"`. Row cap 20, hidden when empty. No
    "See all" (nothing to open yet). The ContinueCard design page was never copied, so record what we build in `docs/design/`.
11. **Failed-write feedback:** Android `Snackbar` with "Try again"; iOS has no equivalent (`ios.md:50` bans snackbars), so a
    small non-modal bottom banner with the same action, written into `ios.md`.
12. **New row gets a static skeleton** (no shimmer). Libraries keep their spinner until the state audit before Phase 4.
13. Watched is an icon, not a word: a check on a watched poster's top-right corner (Android, iOS) and a check-circle beside the item page's facts. (Moved up from Phase 4 at your request.)

## Phase A: Crash-safe `Stopped` — done

- `C/data/db`: `PendingReportEntity`, `PendingReportDao`, version 3. `C/domain/PendingReports` (DAO + `Clock`, create/touch/remove,
  list). `C/domain/PlaybackRecovery`: `send(record)` returns Sent/Dropped/Retry, and `recoverOnStart()` goes through the records,
  drops stale ones, keeps the record on `Unreachable`, `Network` or 5xx, deletes it on any other answer.
- `C/domain/PlaybackReporter.kt`: at the first `Playing` capture the profile from `connection.state` and insert the record;
  on every Progress send touch the position first; on `Stop` send through `PlaybackRecovery` (keeps the one immediate retry), delete
  only on a definitive answer, then `WatchChanges.changed()`. Constructor gains the new collaborators; update the Koin factory
  (`C/di/ApiModule.kt:56`), `TestApp`, and the reporter tests.
- `C/di/Koin.kt` `initKoin`: after `startKoin`, launch `recoverOnStart()` on `ApplicationScope` (both apps already call
  `initKoin`; `AppModuleTest` and `PlatformWiringTest` use `koinApplication {}` so they don't fire it).
- Tests (`T/domain`, same real-time style as `PlaybackReporterTest`): record written and removed; failed `Stopped` keeps it; other
  profile's `Stopped` carries that profile's token (`RecordedRequest.authorization`); locked profile drops; 401 drops and locks that
  profile; unreachable keeps; over 24 h drops with no request; recovery on start; cascade delete with the profile
  (`database.count`). Cancel scopes before `app.close()` (see `TestApp.close()`).
- Done when: `:shared:testAndroidHostTest` and `:androidApp:assembleDebug` pass. On the emulator against a local Jellyfin (recipe in
  `phase-1-playback-decisions`): play an item over 5 min, `adb shell am force-stop`, `GET /Sessions` still shows it playing,
  relaunch, the session ends and the resume position is saved. Install over the existing v2 build to prove the migration. Also
  note whether the server accepts a `Stopped` under a different token.

**Built (Phase A, 2026-10-20).**
- `pending_reports` (Room v3, `AutoMigration(2,3)`, `schemas/.../3.json`) with `PendingReportDao`; `PendingReports` (save, remove, list,
  discard older than an age) and `PendingStop`; `PlaybackRecovery.send` / `recoverOnStart` (Sent, Dropped, Retry); `WatchChanges`;
  `ConnectionManager.asProfile` (`ProfileCall`: Answered, NoSession, Unreachable). `initKoin` launches `recoverOnStart()` on
  `ApplicationScope`.
- `PlaybackReporter` keeps the started session in memory as a `PendingStop` and saves it when playback starts, before every progress report
  and at the stop; `Stopped` goes through `PlaybackRecovery` (one immediate second try, then the record stays for the next start) and
  `WatchChanges.changed()` fires after it. The session is named by the profile that started it, so a profile switch or an ended session
  no longer drops `Stopped`. `TestApp.reporter(scope, interval)` builds a reporter wired like the app's.
- Tests: 12 in `PlaybackRecoveryTest` (sent and forgotten; other profile's token while another is signed in; locked profile; 401 locking only
  that profile; 401 of the signed-in profile ending its session; no connection and 5xx keep the record; 404 drops it; over 24 h dropped
  unsent; oldest first; saving twice is one row; deleting the server cascades) and 5 in `PlaybackReporterTest`. `:shared:testAndroidHostTest`
  (421 tests), the iOS klib compile checks, `:androidApp:assembleDebug` and `:androidApp:testDebugUnitTest` pass.
- **Verified on the Android emulator against a local Jellyfin 12.2.0** (podman, a 6.5 min H.264 file): installing over the existing v2
  database migrated it to v3 with both saved profiles intact; playing, then `am force-stop` mid-playback, left the server showing the item
  as playing and a `pending_reports` row at the last reported position (16.17 s in one run); relaunching ended the session on the server
  within 6 s and removed the row. Not seen by this run: the record rescuing a position, because Jellyfin already saves the position from
  each progress report (48.2 s was on the server before the kill). What the crash leaves behind is the "playing" session, and that is what
  recovery clears.
- **What the server taught us.** A `Stopped` from another token and device is accepted (204) and its position is saved, but it does not clear
  the "now playing" entry of the session that sent `Start`: only the same device and token do. Recovery sends as the same profile with the
  same `DeviceId`, so it clears the right session; the 24 h rule matters because a late `Stopped` does overwrite the saved position.
  Jellyfin keeps no resume position under 5% of the runtime (19.5 s of a 6.5 min file), so a test that kills the app at 16 s saves nothing.
- Routes checked on 12.2.0: `GET /UserItems/Resume`, `POST`/`DELETE /UserPlayedItems/{id}` (200, and marking played clears the position),
  the old `/Users/{id}/...` forms and `GET /Shows/NextUp` all answer. **Not checked on a 10.10 server** (the floor): none was available. If
  Phase B finds the new routes missing there, fall back to the old ones.
- The test server (`jf-test` in podman, media in the session scratchpad) is stopped, not removed, for the next phases.

## Phase B: Continue watching row — done

- API (`C/data/api/LibraryApi.kt` pattern, `ItemDtos.kt`): `UserItemDataDto` (position, played) and `SeriesId` on `BaseItemDto`;
  `resumeItems` (`mediaTypes=Video`, `limit=20`) and `nextUp` as `get(..., retry = true)`.
- Domain: `MediaItem` gains defaulted `seriesId`, `runtimeMs`, `resumePositionMs`, `played`, `landscapeUrl` and a computed `progress`
  (defaults keep `ItemFactsTest`'s positional constructor valid). New `WatchRepository.continueWatching()`: in-progress first (server
  order), then Next Up minus ids already present and minus any series with an item in progress, capped at 20. One list failing returns
  the other; both failing returns the failure. New `WatchChanges` singleton. Register in `ApiModule`/`TestApp`/`AppModuleTest`.
- `C/ui/home/HomeViewModel.kt`: `continueRow` state (loading only on first load, cards, message, refreshing), reload on connection
  change, `WatchChanges`, `onRefresh()`, `onForeground()`, `onRetryContinue()`; session end through the existing `exit`. Card state is
  UI-ready (`id`, `title`, `subtitle`, `imageUrl`, `progress`, `description`). Strings: "Continue watching" and card label.
- Android: `A/components/ContinueCard.kt` (reuse `Artwork`, `Size.thumbWidth`, `progressTrack`, `accent`), a skeleton row, and
  `A/home/HomeScreen.kt` restructured so the row scrolls with the grid (one `LazyVerticalGrid`, row as a full-span item, as
  `ChildrenScreen.kt:150` does), `PullToRefreshBox`, `ON_RESUME` hook. Tap goes straight to `navigate(Player(id))` via a new callback
  in `VibeyFinNavHost.kt`.
- iOS: `ContinueCard.swift` and row (`.redacted` skeleton) in `I/HomeView.swift` inside the ScrollView above the grid, `.refreshable`,
  `scenePhase` hook, tap presents `PlayerView` as a `fullScreenCover(item:)` on Home (`I/RootView.swift` has no player route).
  Facade `X/HomeModel.kt` and `HomeObservable.swift` gain `onRefresh`, `onForeground`, `onRetryContinue`.
- Tests: API (`LibraryApiTest` style, extend `itemJson` with UserData/SeriesId), repository merge rules, `HomeViewModelTest` (partial
  and total failure, empty hides, reload on `WatchChanges`, 60 s foreground throttle with `FakeClock`).
- Done when: tests and `assembleDebug` pass; on the emulator the row shows an in-progress item with its bar, one Next Up card per series,
  and a second client (curl, own `DeviceId`) changing a position shows up after pull-to-refresh. iOS compile-checked only.

**Built (Phase B, 2026-10-20).**
- Shared: `UserItemDataDto` and `SeriesId` on `BaseItemDto`; `resumeItems` and `nextUp` (`data/api/WatchApi.kt`, retried reads);
  `MediaItem` gained `seriesId`, `runtimeMs`, `resumePositionMs`, `played`, `landscapeUrl` and `progress`; `WatchRepository.continueWatching()`
  with `mergeContinueWatching`; `HomeViewModel` now has `continueRow` (`ContinueRow`, `ContinueCard` with an `accessibilityLabel`),
  `refreshing`, `onRefresh`, `onForeground` (60 s throttle, `Clock`), `onRetryContinue`, and reloads on `WatchChanges`. A reload that fails
  leaves the cards as they were; only the first load or "Try again" shows a message.
- Android: `ContinueCard` and a static skeleton, Home as one `LazyVerticalGrid` (row as a full-span item that bleeds to the screen edge),
  `PullToRefreshBox` with a neutral indicator, `ON_RESUME` hook, tap goes straight to `Player(id)`.
- iOS: `ContinueCard.swift`, `HomeView` restructured (row above the libraries in one `ScrollView`, `.refreshable`, `scenePhase` hook,
  `fullScreenCover(item:)` player), facade and observable gained the three calls, `Size.thumbWidth` added to `Theme.swift` and to the token
  drift check. **Parsed with `swiftc -parse` only; not type-checked or run.**
- Tests: shared host tests 452 pass (new: `WatchApiTest`, `WatchRepositoryTest`, mapping tests, 12 Home tests), the timing-sensitive ones
  rerun five times green; iOS klib compile, `assembleDebug`, Android unit tests and both Swift scripts pass.
- **Verified on the emulator against local Jellyfin 12.2.0** (Movies, plus two generated shows): the row listed Beta E01 (33%), Long Test
  (12%), then Alpha E02 as Next Up with no bar and "S1 E2"; Alpha E01 (watched) and Beta E02 (Beta has an item in progress) were left out. A
  second client (`DeviceId` `second-device`) starting Alpha E02 at 2:10 showed up first at 36% after a pull to refresh. Tapping the card
  played at once, and going back refreshed the row by itself (5%, because playback still starts at 0 until Phase C, so that session
  overwrote the 2:10).
- Not done: no 10.10 server was available for the new routes; "Try again" and the offline row were covered by tests, not on the emulator.

## Phase C: Resume on open and Start over — done

- `PlaybackViewModel` (`C/ui/playback/PlaybackViewModel.kt:174-177`): replace `loadTitle(); open(0)` with a start step that reads the
  item once (title and position), then `open(startAtMs)`: 0 if `startOver` or played, else `max(0, position - 5 s)`. Seed
  `lastPositionMs` so retries and fallbacks keep it. Constructor takes `startOver`; Koin binding and `AppModuleTest` follow.
  Item failure shows `LoadMessages.forError` with "Try again" (call 5). The players already honour `PlaybackStream.startPositionMs`,
  and Android already reports `Start` at that position.
- Route/args: Android `Player(itemId, startOver = false)`; iOS `PlayerView(itemId:startOver:)` to `PlaybackObservable` to
  `PlaybackModel(itemId, startOver)`.
- `ItemViewModel` exposes `playLabel` ("Play" or "Resume from 41:12", from `Strings.clock`) and `canStartOver`, and reloads silently on
  `WatchChanges`. Android `ItemScreen.kt:119-142` and iOS `ItemView.swift:82`: aqua button beside a secondary "Start over".
- Docs: `screens.md`, `guardrails.md` (label, "S2 E5" not "S2:E4"), roadmap wording.
- Tests: `PlaybackViewModelTest` (starts at position minus 5 s; clamps at 0; `startOver`; played item starts at 0; item failure; retry keeps
  position), `ItemViewModelTest` labels, `StringsTest`.
- Done when: tests pass; on the emulator, play 2 min of a long item, back out, the item page says "Resume from 2:00" without a manual
  refresh, Resume starts at 1:55, Start over starts at 0, and the Continue card resumes directly.

**Built (Phase C, 2026-10-20).**
- `PlaybackViewModel` reads the item first (`start()`), then opens at `max(0, position - 5 s)` (`RESUME_REWIND_MS`), or at 0 for `startOver`
  or a watched item; `lastPositionMs` is seeded so retries and fallbacks keep it. A failed item read shows the load message with "Try again"
  and negotiates nothing (the old test "a missing title doesn't stop playback" was replaced by this). `ItemUiState` has `playLabel`
  ("Play" / "Resume from 41:12") and `canStartOver`; `ItemViewModel` reloads quietly on `WatchChanges`. New strings `resumeFrom`, `startOver`.
- Android: `Player(itemId, startOver)` route; the item page has the aqua button with the grey "Start over" beside it (3:2). iOS: the same
  through `PlaybackModel(itemId, startOver)`, `PlayerView(itemId:startOver:)` and `ItemView` (parsed only, not type-checked or run).
- Design docs fixed: "Resume from 41:12" (not "Resume · 38m left") and "S2 E4" (not "S2:E4"), in `guardrails.md`, `brand.md`, `screens.md`.
- Tests: 461 shared tests pass (new: start position, clamp at 0, start over, watched, item failure and retry, retry keeps position, item labels
  and quiet reload). Sweep (iOS klib compile, `assembleDebug`, Android unit tests, both Swift scripts) green.
- **Verified on the emulator** (local Jellyfin, 6.5 min film saved at 48.2 s): a Continue watching tap played with the video's burned-in
  clock at 43.8 s; the item page read "Resume from 0:51" with "Start over"; "Start over" began at 0, and on going back the page had changed to
  "Play" by itself (the server keeps no position under 5%). Not run: "Resume" from the item page itself (same code path as the card).

## Phase D: Mark watched/unwatched, optimistic writes — done

- API: `POST`/`DELETE /UserPlayedItems/{id}` through `send(..., retry = true)`; the 401 reaches `expireSession` with no extra code.
  `WatchRepository.setPlayed`, then `WatchChanges.changed()` on success.
- `ItemViewModel.onTogglePlayed()`: flip `played` and clear the position at once; on failure revert and set an action message
  (`LoadMessages.MARK_WATCHED_FAILED`/`..UNWATCHED..`, with retry); skip the message when `exit` is set; ignore taps while a write is in
  flight. `HomeViewModel.onTogglePlayed(cardId)`: remove the card at once, restore it at its index on failure.
- UI: Android overflow `DropdownMenu` on the item page and a `ModalBottomSheet` on card long-press ("Details", "Mark as watched"),
  `Snackbar`. iOS toolbar `Menu`, `.contextMenu`, bottom banner. Strings: "Mark as watched", "Mark as unwatched", "Details", "Watched".
- Tests: optimistic flip and revert, retry count on 5xx, 401 ends the session with no message, card removal and restore, double tap.
- Done when: tests pass; on the emulator a card disappears at once, comes back with a snackbar when the server is cut, and the played state
  matches `GET /Users/{id}/Items/{id}` afterwards.

**Built (Phase D, 2026-10-20).**
- Shared: `setPlayed` (`POST`/`DELETE /UserPlayedItems/{id}?userId=`, `execute(..., retry = true)`, body not read); `WatchRepository.setPlayed`
  (fires `WatchChanges` on success; the repository now takes `WatchChanges`); `ItemViewModel.onTogglePlayed` flips `played`, clears the
  position and adds "Watched" to the facts at once, takes it back on failure, ignores taps while a write is in flight, and exposes
  `markLabel`, `actionMessage`, `onRetryAction`, `onDismissMessage`; `HomeViewModel.onMarkWatched(cardId)` removes the card at once and puts it
  back at its place on failure, with the same message and retry. A 401 sets no message (the session ends). New strings and two
  `LoadMessages`.
- Android: a "⋮" chip top-right on the item page (`Mark as watched` / `Mark as unwatched`), a bottom sheet on a card's long press (the card's
  title, `Details`, `Mark as watched`), and `ActionSnackbar` ("Try again", action in plain text, not aqua). The Continue row scrolls back to its
  start when its first card changes (otherwise a restored card sat half off-screen to the left).
- iOS (parsed only): toolbar `Menu` on the item page, `.contextMenu` on a card, and `failureBanner` (a non-modal capsule on `.regularMaterial`).
- Tests: 478 shared tests pass (new: the endpoint, the repository signal, optimistic flip and revert on the item and on Home, retry, 401
  without a message, a second tap ignored, dismissing). The fakes behave like the real server (after a write the item reads back changed).
- **Verified on the emulator** (local Jellyfin): long press showed the sheet; with the server stopped, "Mark as watched" removed the card at
  once and, when the save failed, brought it back at the front with "Couldn't mark this as watched… Try again"; with the server up the card
  went for good and Beta's Next Up took its place ("Beta Show S1 E2"); the item page's "⋮" marked a film watched ("2020 · 7 min · Watched",
  server `played: true`). Not tried on the emulator: "Try again" from the snackbar (covered by tests), and the iOS banner and menus.
- Rig note: after restarting the podman container the app's pooled connection kept getting 503s until the app was restarted. It was not seen
  against a real server; if it ever is, look at the HTTP client's connection reuse.

## Phase E: Auto-advance — done

- API/repo: `episodesFrom(seriesId, startItemId)`, `WatchRepository.nextEpisode(item)` (null for a movie, no series, or the last episode).
- `PlaybackViewModel`: after open, prefetch the next episode in the background. `PlaybackUiState.upNext` (id, label, title, image,
  `secondsLeft`). `onPosition` starts the countdown when `runtime - position <= 30 s`, runtime is at least 3 min, and it isn't dismissed;
  `onEnded` starts it from 10 when the window never fired. Injected tick (`Duration`, tests use 1 ms) like `retryDelays`. `onPlayNow()`,
  `onWatchCredits()`. Advance: stop the job, `reporter.stop(runtimeMs)`, make `itemId` a `var`, reset choice, plan, position and dismissal,
  then reuse the Phase C start step for the next item. The native players already reopen on a new `PlaybackStream.id`, so neither
  player is rebuilt. `onBackgrounded` and `onClosed` cancel the countdown; the card returns with a fresh one if the window is still open.
- Android `PlayerScreen.kt`: a Compose `UpNextCard` in the existing `Box`, gated on `!leaving`, on `scrim`, clear of the controls, closing
  the track sheet on a new stream. Never touch orientation or `window.restore()` during a swap (TextureView exit transition, plan 03).
  iOS `PlayerView.swift`: overlay in the `ZStack` using `PlayerGlassGroup`; `finished` must not be set while counting. Facade gains
  `onPlayNow`, `onWatchCredits`.
- Known edge: after a swap, Back on Android lands on the item page of the first episode (the route arg is unchanged); it reloads through
  `WatchChanges`, so it is merely a different page than expected, not a stale one.
- Tests (`PlaybackViewModelTest` pattern): window starts the countdown; zero advances with `Stopped(end)` for the old session and `Start`
  for the new; "Watch credits" ends at `finished`; short item counts down at the end; movie, last episode, `autoAdvance = false` and a
  failed next lookup all just finish; background and close cancel.
- Done when: tests pass; on the emulator two episodes of a local series advance on their own, the server shows the first as watched, and
  Cancel and Back mid-countdown behave.

**Built (Phase E, 2026-10-20).**
- Shared: `episodesFrom` (`GET /Shows/{seriesId}/Episodes?startItemId=&limit=2`) and `WatchRepository.nextEpisode` (null for a movie, no show,
  the last episode, or when the server doesn't start at the episode asked for). `PlaybackViewModel` took `watch`, `autoAdvance` (default true)
  and `countdownStep`; it looks the next episode up in the background, shows `PlaybackUiState.upNext` (`UpNext`: id, "S1 E2", title, picture,
  seconds left) from 30 s before the end for videos of 3 min or more (a shorter one counts down after it ends), counts 10 s, then plays the next
  episode in place: `reporter.stop(runtime)`, per-item state reset, `begin(next)`. `onPlayNow`, `onWatchCredits` (dismiss for this episode; if the
  video had ended, the screen closes), a seek back out of the window or a background, close, retry or give-up takes the card away. Strings:
  "Up next", "Play now", "Watch credits", "Starts in N".
- Android: `UpNextCard` (picture, "S1 E2 · title", countdown, aqua "Play now", "Watch credits") over the video, gated on `!leaving`, and the track
  sheet closes on a new stream. iOS (parsed only): `UpNextCard.swift` in the player's `ZStack`, facade and observable `onPlayNow`/`onWatchCredits`.
- Tests: 500 shared tests pass (new: 13 in `PlaybackViewModelTest` for the window, the zero, play now, watch credits, short videos, a video that ends
  during the count, movies, the last episode, auto-advance off, a failed lookup, background, close, seeking out; 5 in `WatchRepositoryTest`; the
  endpoint); the playback tests were rerun five times green.
- **Verified on the emulator** (local Jellyfin, two 6 min shows): jumping Alpha E01 near its end, the card appeared ("Up next · S1 E3 · …",
  "Starts in 8", aqua "Play now", "Watch credits"); left alone, E01 gave way to E02 about 20 s before its end and E02 to E03 the same way,
  E01 and E02 each recorded as watched (`Played: true`) by the `Stopped` at their end. On Beta, "Watch credits" removed the card, E01
  played to the end, the server recorded it watched, the screen closed and E02 was not started.
- Not run: "Play now" on the emulator (covered by a test), the iOS card, and a swap on a transcode.

## Left out

Pull-to-refresh beyond Home; "See all"; poster check badges and long-press on library lists (Phase 4); series Play/Resume (Phase 4);
"Still watching?" (Phase 3, the advance counter is its seam); the auto-advance toggle (Phase 7); a Home cache and offline banner;
disk queue for played marks (Phase 10); the phone pass still owed from Phase 1.

## Risks

- A late `Stopped` overwriting a newer position: bounded by the 24 h rule only; an item-level check was judged too heavy.
- The new routes (`/UserItems/Resume`, `/UserPlayedItems`) are verified on Jellyfin 12.2.0 only, not on the 10.10 floor.
- The reporter now touches Room on every progress tick (about every 10 s); its tests are real-time and must stay so.
- iOS is compile-checked and parsed only (no Xcode on this Mac), so the Home restructure, banner and overlay are unrun.
- The phone is signed into your real NAS and Phase 2 now writes watch state (including marking things watched). Verify on the emulator
  and a local podman Jellyfin only; the demo server is shared and public.

## Verification (every phase)

`./gradlew :shared:testAndroidHostTest` (stand-in for `allTests`, which can't link iOS here), `:shared:compileKotlinIosSimulatorArm64
:shared:compileTestKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`, `:androidApp:assembleDebug`, `:androidApp:testDebugUnitTest` (token
drift), and `scripts/check-title-case.sh` / `scripts/check-swift-tokens.sh` when Swift changes. Then the emulator check named in each phase,
reading the server's side with `GET /Sessions` and the item's `UserData`. Small commits, one line, `task|fix|feat|chore:`.

## Change after Phase D review (2026-10-20)

You asked for the Resume button to say the time left and for "Start over" to be an icon. Done on both platforms: the primary button reads
"Resume · 38m left" (`Strings.resumeTimeLeft`, "1h 52m" from an hour; "Resume" alone if the length is unknown), with its play icon; "Start
over" is a round icon-only circular-arrow button with the words as its accessibility label. The menus got leading icons (info, check, clear).
This replaces "Resume from 41:12" above, and the design docs and roadmap now say so.

Also at your request: a watched item shows a check icon, not the word "Watched": on its poster in every list (`WatchedBadge`, Android and iOS, on
the scrim, VoiceOver reads "…, Watched" on the tile) and beside the facts on the item page. `ItemUiState.watched` replaced the "Watched" fact.
Checked on the emulator: the film's poster in Movies shows the badge and its page shows the check-circle after "2020 · 7 min".

Continue watching cards for an episode now read "S2 E5  ·  Episode title" under the show's name (the screen-reader label includes it too).

Continue watching card, redone from your reference: a play disc centred on the picture, the aqua bar on its bottom edge, the title with its line
under it only 2 dp apart, and a "…" button (Android `IconButton`, iOS `Menu`) beside the title that opens the same actions as a long press.
In-progress cards say what is left ("S2 E5 · 38m left", "5m left" for a movie); Next Up cards keep "S2 E5 · Episode title". The screen-reader label
is back to "{title}, {episode or year}, {n}% watched". The skeleton uses the same spacing. Checked on the emulator.

Motion, at your request. The next-episode card grows in from 92% and fades (260 ms) and goes the same way (180 ms), like a screen opening
(Android `AnimatedVisibility` keeping the last card while it fades out; iOS a scale-and-opacity transition, plain opacity under Reduce Motion).
Presses: on Android the theme's indication is now `PressScaleIndication` (the system ripple plus a spring-back shrink to 95% under the finger), so
every Material button, card, chip and plain `clickable` gets it with no per-screen code; only what is drawn scales, not the touch area. On iOS the
plain-styled card, tile and row buttons use `PressableButtonStyle` (the same shrink, none under Reduce Motion); the system bordered and glass
buttons keep their own highlight. Checked on the emulator: a held press on the Movies tile shrinks it with its rounded corners kept. Not seen: the
card's animation (too quick to catch with screenshots) and anything on iOS.
