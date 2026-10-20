# Plan 06: Roadmap Phase 2, watch status and continuity

Goal: remember what the user watched. Resume where they stopped (on any device), show Continue watching on Home,
let them mark things watched, and move on to the next episode by itself. Logic is shared; every screen is native.

**Status (2026-10-20):** planned and approved, nothing built. Phases A to E each stop for review.

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
10. **Continue card:** 16:9 (`Thumb`, else `Backdrop`, else `Primary`), title = series name for an episode, subtitle "S2 E5",
    aqua progress bar on in-progress cards only, label `"{title}, {year}, {n}% watched"`. Row cap 20, hidden when empty. No
    "See all" (nothing to open yet). The ContinueCard design page was never copied, so record what we build in `docs/design/`.
11. **Failed-write feedback:** Android `Snackbar` with "Try again"; iOS has no equivalent (`ios.md:50` bans snackbars), so a
    small non-modal bottom banner with the same action, written into `ios.md`.
12. **New row gets a static skeleton** (no shimmer). Libraries keep their spinner until the state audit before Phase 4.
13. Item facts show "Watched" for played items. Poster check badges stay in Phase 4.

## Phase A: Crash-safe `Stopped`

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

## Phase B: Continue watching row

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

## Phase C: Resume on open and Start over

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

## Phase D: Mark watched/unwatched, optimistic writes

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

## Phase E: Auto-advance

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

## Left out

Pull-to-refresh beyond Home; "See all"; poster check badges and long-press on library lists (Phase 4); series Play/Resume (Phase 4);
"Still watching?" (Phase 3, the advance counter is its seam); the auto-advance toggle (Phase 7); a Home cache and offline banner;
disk queue for played marks (Phase 10); the phone pass still owed from Phase 1.

## Risks

- A late `Stopped` overwriting a newer position: bounded by the 24 h rule only; an item-level check was judged too heavy.
- Whether Jellyfin accepts `Stopped` for an unknown session or a different token is unverified until Phase A's check.
- The reporter now touches Room on every progress tick (about every 10 s); its tests are real-time and must stay so.
- iOS is compile-checked and parsed only (no Xcode on this Mac), so the Home restructure, banner and overlay are unrun.
- The phone is signed into your real NAS and Phase 2 now writes watch state (including marking things watched). Verify on the emulator
  and a local podman Jellyfin only; the demo server is shared and public.

## Verification (every phase)

`./gradlew :shared:testAndroidHostTest` (stand-in for `allTests`, which can't link iOS here), `:shared:compileKotlinIosSimulatorArm64
:shared:compileTestKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`, `:androidApp:assembleDebug`, `:androidApp:testDebugUnitTest` (token
drift), and `scripts/check-title-case.sh` / `scripts/check-swift-tokens.sh` when Swift changes. Then the emulator check named in each phase,
reading the server's side with `GET /Sessions` and the item's `UserData`. Small commits, one line, `task|fix|feat|chore:`.
