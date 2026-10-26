# Plan 14: Downloads and offline mode (roadmap Phase 10)

**Status (2026-10-23):** Phases A (foundations, shared), B (Android download engine), C (download UI on Android) and D (offline mode, offline Home and Search, local playback, progress sync) built, tested and run on the emulator; E (iOS) not started. Each phase ends with a review stop.

## Context

The Downloads tab exists on both platforms but shows "Not available yet". No downloads code exists:
no tables, worker, service, notification permission, WorkManager, background URLSession or Download
button. There is also no connectivity monitor, no content cache, and offline playback progress is
dropped. This plan builds real downloads that keep going with the screen off or the app closed, and an
automatic offline mode with three tabs (Home, Search, Downloads) that all work from local data.
Watch progress made offline is stored and sent to the server once the device is online again.

"Phone off" is read as screen off, locked, or app swiped away. Nothing can download while the phone is
powered down. Queued downloads resume after a reboot (WorkManager persists them). On iOS, a user
force-quit cancels background transfers; that is an OS rule and goes in the docs.

First step of implementation: copy this plan to `docs/plans/14-downloads-and-offline.md`, fix the
stale Phase 10 lines in `docs/ROADMAP.md` ("the code is kept" is wrong), and work in the phases below.
Each phase ends with a build, tests and a summary, then stops for review (CLAUDE.md).

## Decisions (settled with the user)

- Android engine: WorkManager plus a foreground service (`dataSync`). This adds `androidx.work`;
  the user approved it. Resolve its latest stable version yourself and pin it in `libs.versions.toml`.
- Offline mode is automatic when there is no validated network. If the network is up but the server
  is unreachable, show a banner with "Use offline" instead of switching. Returning online is automatic.
- A manual "Offline mode" switch in Settings forces offline mode on, even with a working network
  (for example to save data or battery). It is a device setting in `DevicePreferences` (DataStore),
  like "Always ask who's watching", because it describes the device and not a profile. While it is on,
  the network monitor can't switch the app back online; turning it off returns to the automatic rule.
- Android first, iOS as the last phase. Say plainly what iOS code was only parsed.
- Downloads are Wi-Fi only by default, with a per-profile "Download over mobile data" setting.
- Quality: the original file only (`GET /Items/{id}/Download`). Jellyfin has no offline transcode.

## Phase A: foundations (shared)

- `NetworkMonitor` interface in `commonMain` (`StateFlow<NetworkState>`: none, wifi, metered), with
  `actual`s bound in `PlatformModule.android.kt` / `PlatformModule.ios.kt`. Android uses
  `ConnectivityManager.registerDefaultNetworkCallback` and checks `NET_CAPABILITY_VALIDATED`; add
  `ACCESS_NETWORK_STATE`. iOS uses `NWPathMonitor` (later phase).
- `ConnectivityState` / offline flag exposed from one place (extend `ConnectionManager` with an
  `isOffline` flow rather than adding a `ConnectionState` case, so a missing network is never treated as
  an expired token; only a 401 expires a session). `isOffline` = manual switch on, or no validated
  network. The manual switch is stored in `DevicePreferences` (`domain/DevicePreferences.kt`). Server-unreachable detection reuses
  `ApiError.Network` / `ConnectionIssue.Unreachable`.
- Room v7 (`VibeyFinDatabase.kt`, `AutoMigration(6, 7)`, new schema JSON, extend `MigrationTest`):
  - `downloads`: profile (`serverId`, `userId`), `itemId`, kind, state (queued, running, paused,
    waiting-for-wifi, failed, done), bytes done/total, file path, `mediaSourceId`, container, ETag,
    error, parent ids (series, season), `addedAt`. Foreign key to `accounts`, cascade.
  - `download_items`: the offline snapshot of a `MediaItem` (title, year, runtime, overview, genres,
    ratings, cast, series/season/episode numbers, video format, `MediaStreams`) plus local image paths,
    BlurHashes and subtitle files. Series and season rows are stored as parents so the show page and
    Downloads grouping work offline.
  - `pending_reports`: add kinds `progress` and `played`, keyed by `(profile, itemId, kind)` with
    latest-wins, a `watchedAt` timestamp and a played flag. Today's unique key uses `playSessionId`,
    which offline plays do not have. Reuse `PendingReportDao` / `PendingReports`.
- API: add `MediaSources` (file size, container) to `DETAIL_FIELDS`
  (`LibraryApi.kt`); read the download permission (`Policy.EnableContentDownloading`) in `UserDtos.kt`;
  add a streaming `GET /Items/{id}/Download` call with Range support and its own long timeouts, not
  `Timeouts.Api` (30 s). Verify the user-data endpoint (`POST /UserItems/{id}/UserData`) against the
  test server.
- Test server: add to `scripts/test-server/make-media.sh`, `list-fixtures.sh` and
  `docs/test-server.md`: a large multi-minute fixture for resume and background tests, a movie with
  external subtitles and artwork, and a user without download permission.
- Strings first (`Strings.kt`, `LoadMessages`): all labels, states, errors and offline banner text.

## Phase B: Android download engine

- `commonMain`: `DownloadRepository` (queue, state `Flow`s, enqueue movie/episode/season/series,
  pause, resume, cancel, remove, storage totals) and a `Downloader` interface so the platform does
  the transfer. Repositories take the injected `Clock`.
- `androidMain` / `androidApp`: a `CoroutineWorker` per queue run, with `setForeground` and a
  `dataSync` foreground service type (manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
  `POST_NOTIFICATIONS`, requested when the first download starts). A visible progress notification is
  required (`docs/design/android.md:43`). Constraints: network connected, unmetered unless the
  mobile-data setting is on. Writes go to a `.part` file in app-private storage and are resumed with
  `Range`, then renamed on completion. Handle the foreground-service time limit by stopping and
  rescheduling.
- The worker runs in a cold process, where `ConnectionManager.state` is empty. It must use
  `ConnectionManager.asProfile(serverId, userId)` or `AccountRepository.sessionToken` plus the server
  addresses (the `PlaybackRecovery.send` pattern), never `connection.state`. A 401 locks the profile via
  the existing `asProfile` path and pauses its downloads.
- After the video: save artwork (poster, backdrop, logo, landscape, show poster, cast at the existing
  widths in `LibraryRepository`), external subtitles from `DeliveryUrl` (text formats only; PGS cannot
  work offline and the UI says so), and the metadata snapshot. A free-space check before enqueuing, and
  a clear failure when there is not enough space.
- Re-schedule on app start and on boot so nothing is stranded.

### What Phase B found on the emulator (Android 17, targetSdk 36)

Checked with `big-download` behind `scripts/test-server/throttle-proxy.py`, the app on the emulator, driven by the debug-only
`DebugDownloadReceiver` (`androidApp/src/debug`) because the Downloads screens come in phase C.

- **Works:** with the screen off and the app in the background the download carries on; so it does in forced deep Doze
  (`dumpsys deviceidle force-idle`) while the foreground service runs; after `kill -9` of the process, after a package update
  (force-stop) and a later app start, it resumes with a `Range` request, and the finished file's MD5 equals the server's.
- **A foreground service can only be started while the app is visible.** Started by the Download tap it is allowed (`isForeground=true`,
  type `dataSync`, our notification). A worker that starts later in the background (the network came back, a retry) is refused
  (`Background started FGS: Disallowed`, also for expedited work); it still runs as a plain job but with no notification and
  with job time limits (an expedited job here: about 3 minutes, then ordinary slices that resume from the offset).
- **WorkManager stops running work the moment its network constraint is lost** (`Constraints not met: Cancelling work`). So work asked
  for now has no network constraint (`DownloadNetwork.None`) and the runner waits itself (up to 30 minutes in the worker, for a network,
  for Wi-Fi, or for the server) with the foreground service up; only after that does it end and ask for work with a network constraint.
  With this, dropping Wi-Fi and bringing it back resumed within seconds in the same process and the same service.
- **Not covered, and what to do about it:** a force-stop (the user's "Force stop", or an update) cancels all jobs until the app is
  next opened (`resumePending` runs then); that is Android's rule. For a start from the background that must run long, the platform
  answer is a user-initiated data transfer job (API 34+, scheduled from the tap), which WorkManager 2.12 does not offer. Recommended
  as a later step if the weaker background-start behaviour matters in daily use.

## Phase C: download UI (Android) and shared ViewModels

- `DownloadsViewModel` (list grouped by show, sizes, per-item progress, pause/resume/cancel/remove,
  storage summary, "remove all") and a download state in `ItemViewModel` (`ItemUiState`) plus the
  `TileMenuViewModel` entry. Season and series downloads show a confirmation with the episode count
  and total size, with an "unwatched only" option.
- Android UI: the Download button in `ItemHero.kt` `ActionButtons` (fourth action, where the comment
  at lines 204-245 sits), with progress and the filled "Downloaded" check in `ink` (never aqua); per
  episode entry or trailing icon in `ItemSections.kt` (`EpisodeRow`, `EpisodeCard`); season action on the
  `Children` screen; series action in the hero. Replace `NotAvailableYet()` in `VibeyFinNavHost.kt`
  (Downloads route) with the real screen. Add a Downloads section to `docs/design/screens.md`.
- Settings per profile (`AccountSettingsEntity`, v7): mobile data, optional auto-clean of watched
  downloads (off by default), storage limit.
- Settings: an "Offline mode" switch (device-wide, `SettingsViewModel` + the Android settings screen,
  help text "Use only your downloads, even when you're online"). It must stay reachable while offline,
  so it also appears in the offline banner as "Go online". Turning it on while a download runs does not
  cancel the download; downloads keep their own network rules.

### What Phase C did differently

- **No poster-menu entry.** `TileMenuViewModel` has no Download item: the long-press sheet sits over Home, where the count-and-size question
  has no room. Downloading starts from the item page, an episode's menu and a season's list.
- **No auto-clean setting yet.** The column exists (v7), but nothing would act on it before offline playback records what was watched, so it
  is not shown (a switch that does nothing is worse than none). It comes in Phase D.
- **The "Offline mode" switch is in Settings and stored, but takes effect in Phase D** (the three-tab shell, offline Home and Search). The
  banner with "Go online" comes with it.
- **Every removal asks first**, with what it frees (what is on the device now, not the file's full size).
- A tap on a row in the Downloads tab opens the item's page; playing a download without the server is Phase D.
- Downloads are keyed to the profile that is signed in; a profile switch shows that profile's.

## Phase D: offline mode, search, playback and sync

- Offline shell: when offline, `MainNavigationBar.kt` (and later `TabShell.swift`) show only Home,
  Search and Downloads; a banner says "You're offline". Library is hidden. Lost or regained network
  switches without restarting the app. The last profile opens as today (`resume` already works with
  the server down). Profile switching and sign-in stay blocked offline with the existing message.
- Offline Home: a Continue Watching row built from local positions (downloaded items in progress, and
  the next downloaded episode), plus Downloaded movies and shows. No hero, no genre rows.
- Offline Search: `SearchViewModel` queries local `download_items` (title, show name, episode name,
  cast), reusing `SearchResult` and `asMediaItem()`; the debounce, history and grouping stay.
- Local playback: `PlaybackViewModel.open()` gets a local branch that builds the `PlaybackStream`
  from the file (`file://`, no headers) and skips `negotiate`, `reporter.begin`, segments, trickplay and
  the server lookup for the resume position. The Android player needs a `DefaultDataSource.Factory`
  (it uses `DefaultHttpDataSource` today, `PlayerScreen.kt`). Subtitles come from the saved files.
  Next episode comes from the downloaded episodes. Network failures in the retry ladder do not apply.
- Offline progress: the reporter writes position and played state to `pending_reports`
  (`progress` / `played`) with a `watchedAt`. On sync, read the server's user data first and only
  overwrite if the offline watch is newer than the server's last played time (server stays the source of
  truth). Send with the user-data endpoint. This does not use the 24 h discard in `PlaybackRecovery`,
  which is for crash-time `Stopped` only. Sync triggers: network regained while the app runs, app
  start, and a WorkManager job constrained to a connected network so it also runs with the app
  closed. Call `WatchChanges.changed()` afterwards.
- Tests: the offline flag and mode switch, offline search, progress queue and conflict rule, queue
  state machine, resume after process death, migration v6 to v7, wiring in `AppModuleTest` and
  `PlatformWiringTest`.

### What Phase D did

- **Offline** is `OfflineMode.isOffline` (no validated network, or the Settings switch). A banner under the status bar says which: "You're offline" with nothing to press, "Offline mode" with **Go online**, or, with a network but a server that does not answer (`ServerReachability`: two misses in a row), "Can't reach the server" with **Use offline**, which only switches Offline mode on when the user taps it. The bar then shows Home, Search and Downloads; Library goes (and the app moves to Home if it was there).
- **Offline Home** is its own screen and ViewModel (`OfflineHomeViewModel`): Continue watching (started items, then the next downloaded episode of shows being watched), a Movies row and a Shows row, all from the device. A movie or a card plays at once; a show opens the Downloads tab. Its profile menu has only Settings. **Offline Search** is the same `SearchViewModel` reading `OfflineLibrary.search` (title, show and cast; the buttons are All, Movies, Episodes); a result plays at once.
- **Anything downloaded plays from its file, online or not** (`PlaybackViewModel` + `OfflineLibrary.playable`): no negotiation, no scrub preview, no segments. Online, the server's own record of where the user stopped wins (waiting up to 3 s for it); otherwise the device's. A file that will not play is an error, never a transcode. Subtitle files that were downloaded are offered; embedded or image subtitles are not (known limit). After an episode, the next downloaded one plays. The Downloads tab plays a finished row.
- **Progress** is saved on the device while playing (`LocalPlaybackReporter`: every 10 s and on pause, seek and stop), with Jellyfin's rules (under 5 % keeps no place, from 90 % it is watched). `OfflineSync` sends it whenever the server can be reached: at the stop, at app start, whenever a connection comes back, and from a WorkManager job (`ProgressSyncWorker`) that runs even with the app closed. A part-watched item sets the position and the date it was watched; a finished one is marked watched the ordinary way. **A record is dropped if the server's `LastPlayedDate` is newer** (watched elsewhere since): the server is the source of truth. Once online, `OfflineSync` also brings the server's watch state back into the downloaded items that have nothing waiting.
- **Time left:** a running download's speed is kept in memory (`DownloadSpeeds`, smoothed), so the notification and the Downloads tab say "20 MB of 283 MB · 8m left". Each episode on a show's page has its own download button, and **Remove all downloads** is a trash icon.
- **Not done:** the poster-menu Download entry, the "remove once watched" setting (the data is there), embedded subtitles, and offline pages for a movie or show (an offline tap plays, or opens the Downloads tab, instead of a page that would need the server).

## Phase E: iOS

- Swift downloader on `URLSessionConfiguration.background` (Ktor Darwin cannot do background
  transfers), with `handleEventsForBackgroundURLSession` in `AppDelegate`; the `Authorization` header is
  set per task. Exclude media from iCloud backup. A `NetworkMonitor` actual with `NWPathMonitor`.
- iOS facades in `shared/src/iosMain/.../ios` (`DownloadsModel`, download state on `ItemModel`),
  SwiftUI Downloads screen, buttons in `ItemHeroView.swift` and `ItemSectionsView.swift`, offline
  `TabShell.swift`, local `AVURLAsset` playback. Title case through `titleCased()`; no string literals.
- Extend the XCUITest in `iosApp/UITests/` for the new screens and run `scripts/ios-smoke.sh`. Say
  plainly if Xcode, the simulator or podman is missing and the code was only compiled to klibs or parsed.

### What Phase E did (iOS: compiled to klibs and parsed only)

No Xcode on this Mac, so none of this was built into an app, run on a simulator or tested on a device. What was checked:
`:shared:compileKotlinIosSimulatorArm64` and `:shared:compileKotlinIosArm64` succeed against the real Foundation and Network
bindings, and every Swift file passes `swiftc -parse` (syntax only: types, SwiftUI and the Shared framework were never
resolved). `scripts/ios-smoke.sh` and the XCUITest did not run.

- Transfer: `ExternalVideoTransfer` (commonMain) lets the platform move the video itself. `IosBackgroundTransfer` uses a background
  `NSURLSession` (`app.vibeyfin.downloads`); its delegate moves the finished file to `video.part`, resume data goes to `video.resume`,
  and `AppDelegate.application(_:handleEventsForBackgroundURLSession:)` hands iOS's wake-up to it. The runner (artwork, subtitles, the
  snapshot) still runs in the app process through `IosDownloadScheduler`, which re-runs when the network returns.
- Other actuals: `IosNetworkMonitor` (NWPathMonitor), `IosDownloadFiles` (Application Support, excluded from backup).
- Facades: `DownloadsModel`, `OfflineHomeModel`, `AppBannerModel`, `OfflineModeModel`, `DownloadBridge`, plus download methods on
  `ItemModel`, `ChildrenModel` and `SettingsModel`.
- SwiftUI: `Downloads/` (list, badge, buttons, confirm sheet, offline Home), the Download action and per-episode buttons on the
  item page, "Download season" on a season's list, Offline mode / mobile data / storage limit in Settings, the banner, three tabs
  offline, and offline search results that play.
- UITest: the Downloads tab's empty state is checked after sign-in.

Not done: iOS syncs offline progress only while the app runs (no BGTask), so with the app closed it waits for the next launch;
a 401 on a background download marks that download as needing sign-in but does not lock the profile; a force-quit cancels
background transfers (an iOS rule). Also open on both platforms: a Download entry in the poster menu, the "remove once watched"
setting, embedded and image subtitles offline, and offline movie and show pages.

## Verification

- `./gradlew :shared:allTests` (Android host tests stand in for it on this Mac) and
  `./gradlew :androidApp:assembleDebug` at the end of each phase.
- Against the local test server (`scripts/test-server/`, `http://10.0.2.2:8196` from the emulator):
  1. Download an episode, a season and a series on the emulator. Check files, artwork and subtitles.
  2. Start the large fixture, turn the screen off and lock the device, leave it for some minutes, and
     confirm it finishes (`adb shell dumpsys deviceidle` to force Doze). Then repeat after
     `am kill` / swipe-away and after an emulator reboot (it must resume with a Range request).
  3. Switch to flight mode: the app goes offline, shows three tabs, Home, Search and Downloads
     work, and a downloaded episode plays. Watch part of it, finish another.
  3b. With the network on, switch "Offline mode" on in Settings: the app drops to three tabs at once,
     and stays offline after a restart and when the network changes. Switch it off: the app is online again.
  4. Turn the network on: the position and played mark reach the server (check the server UI or API),
     and a newer position set elsewhere is not overwritten.
  5. The no-permission user sees no Download button. A 401 pauses downloads and locks the profile.
- The phone pass (`docs/checklists/phone-pass.md`) gains a downloads section. The user's phone is
  signed into their real NAS, so do not play or download there without asking.
