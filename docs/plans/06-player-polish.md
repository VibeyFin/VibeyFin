# Player polish (roadmap Phase 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver roadmap Phase 3 on Android and iOS: stats overlay, Skip Intro/Credits, trickplay, HDR, "are you still watching?", playback speed, picture-in-picture and format badges.

**Architecture:** Logic, state and every string live in `shared/` (new DTOs, repository calls, `PlaybackViewModel` state, `PlaybackDiagnostics`, a binge counter); each native player (Media3 in `androidApp`, AVPlayer in `iosApp`) draws that state and feeds events back. Phase 2's Next Up is the single stub, behind `NextUpSource`.

**Tech Stack:** Kotlin 2.4, Ktor + kotlinx.serialization, Room KMP (schema v3), Koin, Media3 1.11.1 (+ `media3-session`), Jetpack Compose, SwiftUI/AVKit, `kotlin.test` + Ktor `MockEngine`.

**Spec:** `docs/superpowers/specs/2026-10-20-player-polish-design.md` (read it with this plan). Project plans live in `docs/plans/` (CLAUDE.md), so this is plan 06, not under `docs/superpowers/plans/`.

## Global Constraints

- Phase order: **one phase at a time; build, test, summarise, stop for review** (CLAUDE.md). `./gradlew :shared:allTests` and `./gradlew :androidApp:assembleDebug` pass before a phase is done. iOS is built in Xcode and run on the simulator; say plainly what was only compiled.
- Branch: all work on `feature/player-polish` (the spec and plan commits live only there, not on `develop`). iOS build: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17' build`; run on the booted iPhone 17 simulator.
- Decided in the grill: stats overlay is always visible while its setting is on (no in-player toggle); skip button uses the secondary style and is offered for every segment type except `Unknown`; eight review stops (A to H).
- Commit format, one line, no body, no extra attribution lines: `task|fix|feat|chore: <short message>`.
- All user-facing text in `Strings` (`ui/common/Strings.kt`), sentence case; errors in `LoadMessages`. Native screens hold no literals. iOS title-cases through `titleCased()`.
- Colours only from tokens (`app/theme`, `Theme.swift`); aqua is the one primary action and progress, never status. No new hex values.
- Per-profile settings are columns on `AccountSettingsEntity`; device-wide stay in DataStore.
- 401 only through `ConnectionManager.expireSession`; `Stopped` still goes out after the screen is gone.
- Versions: resolve the latest stable `media3-session` yourself and pin it in `gradle/libs.versions.toml` next to Media3 1.11.1 (owner approved this one dependency; nothing else new).
- Next Up is the **only** stub. "Still watching" default **3**, configurable (Off, 2 to 5). Skip never auto-skips. Android has **no** in-app mini player (PiP + notification only).
- Schema: write version 3 here; **on rebase, renumber on top of Phase 2's migration** and regenerate the schema JSON.
- HDR: Android picks the surface **per item** (HDR items `SurfaceView` with a plain-fade or no exit; everything else keeps `TextureView` and plan 03's transition).

## Review Focus

- A server with no segments API or no segments for an item: no skip button, no error, playback unaffected.
- An item with no trickplay (or trickplay for a different media source): scrubbing shows the timestamp only.
- Entering PiP, then closing the window, then reopening the app: one `Stopped`, no duplicate session, position kept.
- Rotating, scrubbing and pause/resume while the stats overlay is up: no crash, fields missing on a platform show "–".
- The binge count survives a new `PlaybackViewModel` per episode and resets on any input or when the player is closed.

---

## File structure

| Area | Files |
|---|---|
| DTOs / API | `data/api/PlaybackDtos.kt` (stream fields), `data/api/ItemDtos.kt` (`mediaStreams`, `trickplay`), `data/api/LibraryApi.kt` (`DETAIL_FIELDS`), new `data/api/SegmentsApi.kt` |
| Domain | `domain/Playback.kt` (`VideoFormat`, plan fields), new `domain/MediaSegments.kt`, `domain/Trickplay.kt`, `domain/PlaybackDiagnostics.kt`, `domain/BingeCounter.kt`, `domain/NextUpSource.kt`, `domain/NowPlaying.kt`, `domain/ProfileSettings.kt`, `domain/PlaybackRepository.kt`, `domain/model/MediaItem.kt` (`videoFormat`) |
| DB | `data/db/Entities.kt`, `AccountDao.kt`, `VibeyFinDatabase.kt` (v3), `shared/schemas/.../3.json` |
| Presentation | `ui/playback/PlaybackViewModel.kt`, `ui/settings/SettingsViewModel.kt`, `ui/item/ItemFacts.kt`, `ui/common/Strings.kt`, DI in `di/` |
| iOS facades | `shared/src/iosMain/.../ios/PlaybackModel.kt`, `SettingsModel.kt` |
| Android | `app/player/*` (`PlayerScreen`, `PlayerControls`, new `PlayerOverlays.kt`, `PlayerPip.kt`, `PlayerSession.kt`), `res/layout/player_view_texture.xml` + new `player_view_surface.xml`, `AndroidManifest.xml`, `MainActivity.kt`, `settings/SettingsScreen.kt`, `item/ItemScreen.kt` |
| iOS | `PlayerController.swift`, `PlayerView.swift`, `PlayerControls.swift`, new `PlayerOverlays.swift`, `PlayerPictureInPicture.swift`, `MiniPlayerView.swift`, `SettingsView.swift`, `ItemView.swift`, `Info.plist` |
| Test infra | new `scripts/test-server/` + `docs/test-server.md`; `commonTest/testing/PlaybackJson.kt` |

---

## Phase A: Foundations (shared, no UI)

### Task 1: Local test server with chapters, segments, trickplay and HDR media

**Files:** Create `scripts/test-server/start.sh`, `scripts/test-server/make-media.sh`, `docs/test-server.md`.

**Interfaces:** Produces: a repeatable podman Jellyfin 12.2.0 on a documented port with a library holding an MKV/MP4 with chapter markers, a two-episode show (for Phase G), an HDR10 sample, trickplay generated, and media segments present.

- [ ] **Step 1:** `start.sh` runs the pinned Jellyfin image under podman with config/media volumes under a git-ignored directory, and prints the URL; `docs/test-server.md` records the first-run wizard values (admin user, library paths, enable trickplay generation on the library).
- [ ] **Step 2:** `make-media.sh` uses the container's ffmpeg to generate: a 3-minute video with chapters named "Intro" (0:10-0:40) and "Credits" (2:30-end); a second episode of the same show; an HDR10 file (`yuv420p10le`, bt2020/smpte2084 flags, HEVC). Builder decides whether the Chapter Segments Provider plugin is enough to produce `Intro`/`Outro` segments or Intro Skipper is needed; record the answer in the doc.
- [ ] **Step 3: Verify:** `curl -H "Authorization: MediaBrowser Token=<t>" $URL/MediaSegments/<itemId>` returns at least one `Intro` and one `Outro`; `GET /Items/<id>?fields=Trickplay` lists a trickplay entry; the HDR item's source stream reports `VideoRangeType` of `HDR10`.
- [ ] **Step 4: Commit:** `chore: add local test server scripts`

### Task 2: Stream, trickplay and segment data

**Files:** Modify `data/api/PlaybackDtos.kt`, `data/api/ItemDtos.kt`, `data/api/LibraryApi.kt`, `domain/Playback.kt`, `domain/PlaybackRepository.kt`, `domain/model/MediaItem.kt`, `domain/LibraryRepository.kt` (`toMediaItem`); Create `data/api/SegmentsApi.kt`, `domain/MediaSegments.kt`, `domain/Trickplay.kt`; Test `commonTest/.../domain/PlaybackRepositoryTest.kt`, `domain/TrickplayTest.kt`, `testing/PlaybackJson.kt` (video stream takes width, height, `VideoRangeType`).

**Interfaces:**
- Produces:
  - `MediaStreamDto` gains `width: Int?`, `height: Int?`, `videoRangeType: String?`, `bitRate: Int?`, `realFrameRate: Float?`, `bitDepth: Int?` (all `@SerialName` PascalCase, null defaults).
  - `BaseItemDto` gains `mediaStreams: List<MediaStreamDto> = emptyList()` and `trickplay: Map<String, Map<String, TrickplayInfoDto>> = emptyMap()` (media source id → width → info). `TrickplayInfoDto(width, height, tileWidth, tileHeight, thumbnailCount, interval)`. `DETAIL_FIELDS = "Overview,Genres,MediaStreams,Trickplay"`.
  - `enum class DynamicRange { Sdr, Hdr10, DolbyVision, Hlg }`; `data class VideoFormat(val codec: String?, val width: Int?, val height: Int?, val dynamicRange: DynamicRange, val frameRate: Float?, val bitrateKbps: Int?)`; `MediaItem.videoFormat: VideoFormat?`; `PlaybackPlan.videoFormat: VideoFormat?`.
  - `enum class SegmentType { Intro, Outro, Recap, Preview, Commercial, Unknown }`; `data class MediaSegment(val type: SegmentType, val startMs: Long, val endMs: Long)`; `suspend fun PlaybackRepository.segments(itemId: String): List<MediaSegment>` (empty on any failure, including a server that lacks the endpoint) via `JellyfinApi.mediaSegments(itemId)` = `GET /MediaSegments/{itemId}` (retry = true).
  - `data class TrickplayTrack(val mediaSourceId: String, val width: Int, val tileWidth: Int, val tileHeight: Int, val thumbWidth: Int, val thumbHeight: Int, val thumbnailCount: Int, val intervalMs: Long)` with `fun locate(positionMs: Long): TrickplayThumb` and `fun tilePath(itemId: String, tileIndex: Int): String` (`/Videos/{id}/Trickplay/{width}/{index}.jpg`); `data class TrickplayThumb(val tileIndex: Int, val column: Int, val row: Int)`; `suspend fun PlaybackRepository.trickplay(itemId: String, mediaSourceId: String): TrickplayTrack?` (picks the width closest to 320).
- [ ] **Step 1: Write failing tests:** `TrickplayTest.locates thumbnail inside the right tile` (interval 10 000 ms, 10x10 tiles: position 0 → tile 0, col 0, row 0; 125 000 → tile 1... compute from the formula and assert exact col/row; position past the end clamps to the last thumbnail); `PlaybackRepositoryTest.segments are mapped from ticks` (10 000 000 ticks = 1 s, unknown type string → `Unknown`); `segments are empty when the server answers 404`; `videoFormat reads VideoRangeType` (`HDR10` → `Hdr10`, `DOVI*` → `DolbyVision`, `HLG` → `Hlg`, anything else/absent → `Sdr`).
- [ ] **Step 2:** `./gradlew :shared:allTests` fails to compile or fails.
- [ ] **Step 3:** Implement the signatures above; mapping helpers are `internal`. Use the existing `get(path, params, retry)` pattern in `PlaybackApi.kt`.
- [ ] **Step 4:** `./gradlew :shared:allTests` passes.
- [ ] **Step 5: Commit:** `feat: read segments, trickplay and video format from the server`

### Task 3: Profile settings columns (schema v3) and Settings state

**Files:** Modify `data/db/Entities.kt`, `AccountDao.kt`, `VibeyFinDatabase.kt`, `domain/ProfileSettings.kt`, `ui/settings/SettingsViewModel.kt`, `ui/common/Strings.kt`, `shared/src/iosMain/.../ios/SettingsModel.kt`; Create `shared/schemas/.../3.json` (generated); Test `ui/settings/SettingsViewModelTest.kt`, `testing/TestApp.kt` if needed.

**Interfaces:**
- Produces: `AccountSettingsEntity.showPlaybackStats: Boolean` (default 0) and `.bingeThreshold: Int` (default 3, `0` = Off); `AutoMigration(from = 2, to = 3)`; `ProfileSettings.showPlaybackStats: Flow<Boolean?>`, `.bingeThreshold: Flow<Int?>`, `suspend fun setShowPlaybackStats(Boolean)`, `suspend fun setBingeThreshold(Int)` (accepts 0 or 2..5); `SettingsUiState` gains `showPlaybackStats: Boolean = false`, `bingeThreshold: Int = 3`, and `hasProfile` is derived from "any profile flow is non-null" (not from `allowTranscoding` alone); `SettingsViewModel.onShowPlaybackStatsChanged(Boolean)`, `onBingeThresholdChanged(Int)`; `Strings.playbackStatsTitle/Help`, `Strings.stillWatchingTitle/Help`, `Strings.stillWatchingOff`, `Strings.stillWatchingAfter(n: Int)`.
- [ ] **Step 1: Write failing tests:** `SettingsViewModelTest.defaults are stats off and three in a row`; `changing the threshold is stored per profile` (two signed-in profiles, one differs); `a threshold outside 0 or 2..5 is ignored`.
- [ ] **Step 2:** run, expect FAIL.
- [ ] **Step 3:** Add columns with `@ColumnInfo(defaultValue = ...)`, DAO `observe…`/`set…` queries mirroring `allowTranscoding`, bump to version 3, build once so Room writes `3.json`, extend `ProfileSettings` and `SettingsViewModel`, add the iOS `SettingsModel` delegates.
- [ ] **Step 4:** `./gradlew :shared:allTests` passes; the migration is covered by the existing schema test setup (if none exists for 1→2, add one for 2→3 that opens a v2 DB and checks the defaults).
- [ ] **Step 5: Commit:** `feat: add per-profile playback stats and still-watching settings`

### Task 4: The Next Up seam, binge counter and now-playing holder

**Files:** Create `domain/NextUpSource.kt`, `domain/BingeCounter.kt`, `domain/NowPlaying.kt`; Modify `di/` (register `single`s); Test `domain/BingeCounterTest.kt`, `domain/NowPlayingTest.kt`.

**Interfaces:**
- Produces:
  - `interface NextUpSource { val autoAdvance: StateFlow<Boolean>; val advanced: Flow<Unit>; suspend fun next(itemId: String): MediaItem? }` and `class NoNextUpSource : NextUpSource` (autoAdvance false, never advances, `next` null), registered in Koin as the `NextUpSource` binding. **Replaced by Phase 2's implementation on rebase.**
  - `class BingeCounter { val count: StateFlow<Int>; fun autoAdvanced(); fun interacted(); fun reset(); fun shouldAsk(threshold: Int): Boolean }` — in-memory, `single`, so it survives a new `PlaybackViewModel` per episode. `shouldAsk` is true when `threshold > 0 && count >= threshold`.
  - `data class NowPlayingState(val itemId: String, val title: String, val subtitle: String?, val playing: Boolean, val positionMs: Long)`; `class NowPlaying { val state: StateFlow<NowPlayingState?>; val restoreRequests: SharedFlow<String>; fun update(NowPlayingState); fun clear(); fun requestRestore() }`, `single`.
- [ ] **Step 1: Write failing tests:** `BingeCounterTest.asks after three auto advances`; `interaction resets the count`; `threshold 0 never asks`; `count survives being read from a second consumer`; `NowPlayingTest.clear removes state` and `requestRestore emits the current item id`.
- [ ] **Step 2:** run, expect FAIL. **Step 3:** implement. **Step 4:** `./gradlew :shared:allTests` passes.
- [ ] **Step 5: Commit:** `feat: add next-up seam, binge counter and now-playing state`

**Phase A done when:** `:shared:allTests` and `:androidApp:assembleDebug` pass; summarise and stop for review.

---

## Phase B: Stats overlay

### Task 5: `PlaybackDiagnostics` (shared)

**Files:** Create `domain/PlaybackDiagnostics.kt`; Modify `ui/playback/PlaybackViewModel.kt`, `shared/src/iosMain/.../ios/PlaybackModel.kt`, `ui/common/Strings.kt`, `di/`; Test `ui/playback/PlaybackViewModelTest.kt`, `domain/PlaybackDiagnosticsTest.kt`.

**Interfaces:**
- Produces:
  - `data class PlaybackDiagnostics(val method: PlayMethod?, val transcodeReasons: List<String>, val videoCodec: String?, val audioCodec: String?, val width: Int?, val height: Int?, val frameRate: Float?, val bitrateKbps: Int?, val droppedFrames: Int?, val throughputKbps: Int?, val bufferMs: Long?)`; `fun PlaybackDiagnostics.withLive(live: LiveDiagnostics)`; `data class LiveDiagnostics(val videoCodec: String?, val audioCodec: String?, val width: Int?, val height: Int?, val frameRate: Float?, val bitrateKbps: Int?, val droppedFrames: Int?, val throughputKbps: Int?, val bufferMs: Long?)`.
  - `PlaybackViewModel` constructor gains `profileSettings: ProfileSettings`; `PlaybackUiState` gains `diagnostics: PlaybackDiagnostics? = null` and `showDiagnostics: Boolean = false`; `fun onDiagnostics(live: LiveDiagnostics)`; transcode reasons come from the plan's `TranscodeReasons` URL parameter (reuse the parsing behind `reencodesVideo()`; expose it as `internal fun transcodeReasons(url: String): List<String>`).
  - `Strings.statsMethod(PlayMethod)`, `Strings.statsLabel…` for each row, `Strings.statsUnavailable = "–"`.
  - iOS: one-line delegates `PlaybackModel.diagnostics(live:)`.
- [ ] **Step 1: Write failing tests:** `diagnostics start from the plan` (direct play: method, codecs from the plan's `videoFormat`, no reasons); `a transcode lists the reasons from the stream URL` (URL `...&TranscodeReasons=VideoCodecNotSupported,AudioCodecNotSupported` → two reasons); `live values merge without losing the plan's`; `showDiagnostics follows the profile setting`; `missing values stay null`.
- [ ] **Step 2:** run, expect FAIL. **Step 3:** implement; update the Koin `viewModel` factory for the new parameter. **Step 4:** pass. 
- [ ] **Step 5: Commit:** `feat: add shared playback diagnostics`

### Task 6: Stats overlay on Android and iOS

**Files:** Android: `app/player/PlayerOverlays.kt` (new `StatsOverlay`), `PlayerScreen.kt` (an `AnalyticsListener` feeding `viewModel.onDiagnostics`), `settings/SettingsScreen.kt` (`SwitchRow` for stats). iOS: `PlayerOverlays.swift` (new `StatsOverlay`), `PlayerController.swift` (access-log observer → `LiveDiagnostics`), `SettingsView.swift`.

**Interfaces:** Consumes `PlaybackUiState.diagnostics/showDiagnostics`, `onDiagnostics`, `SettingsUiState.showPlaybackStats`, `onShowPlaybackStatsChanged`. Entry point (builder's call within the design docs): the overlay is shown while `showDiagnostics` is true, on a `scrim` panel top-start; the Settings row toggles it.

- [ ] **Step 1:** Android: register a Media3 `AnalyticsListener` (`onVideoInputFormatChanged`, `onDroppedVideoFrames`, `onBandwidthEstimate`, `onAudioInputFormatChanged`) and a 1 Hz buffer read of `player.totalBufferedDuration`, building a `LiveDiagnostics`; render `StatsOverlay(state.diagnostics)`.
- [ ] **Step 2:** iOS: observe `AVPlayerItemNewAccessLogEntry`, map `AVPlayerItemAccessLogEvent` (`indicatedBitrate`, `observedBitrate`, `numberOfDroppedVideoFrames`) and the item's `presentationSize`; fields AVFoundation cannot give stay nil.
- [ ] **Step 3: Verify (emulator and simulator, against the Task 1 server):** turn the setting on, play: overlay shows method, codec, resolution, bitrate; force a transcode (profile setting allows it) and see its reasons; rotate and pause with the overlay up.
- [ ] **Step 4: Commit:** `feat: show playback stats in the player` (split Android and iOS into two commits).

### Task 6c: Remove the transcoding setting (owner decision, added after the plan was written)

The per-profile `allowTranscoding` flag is scrapped everywhere: no other Jellyfin client has it. The server may always transcode (direct play, then direct stream, then transcode); `forceTranscode` on `negotiate` stays for the player's failure fallback.

**Files:** Modify `data/db/Entities.kt` (drop `AccountSettingsEntity.allowTranscoding`), `AccountDao.kt` (drop `observeAllowTranscoding`/`setAllowTranscoding`), `VibeyFinDatabase.kt` (the 2→3 `AutoMigration` takes a spec with `@DeleteColumn(tableName = "account_settings", columnName = "allowTranscoding")`), `shared/schemas/.../3.json` (regenerated), `domain/ProfileSettings.kt`, `domain/PlaybackRepository.kt` (no `transcodingAllowed()` gate; remove `PlaybackNegotiation.NeedsTranscoding` and `LoadMessages.TRANSCODING_NEEDED` if nothing else reaches them), `ui/settings/SettingsViewModel.kt`, `ui/common/Strings.kt`, `iosMain/.../SettingsModel.kt`, `androidApp/.../settings/SettingsScreen.kt`, `iosApp/iosApp/SettingsView.swift`; tests `SettingsViewModelTest`, `PlaybackRepositoryTest`, `PlaybackViewModelTest`, `testing/TestApp.kt` (`signedIn(allowTranscoding)` parameter).

**Interfaces:** Produces: `SettingsUiState` without `allowTranscoding`; `ProfileSettings` without `allowTranscoding`/`setAllowTranscoding`/`transcodingAllowed`; `hasProfile` stays true when a profile is signed in (derived from the remaining profile flows).

- [ ] **Step 1: Write failing tests:** `PlaybackRepositoryTest.transcodes when the device can't play the file` (no setting needed; a source with only a transcode path returns `Ready` with `PlayMethod.Transcode`); `SettingsViewModelTest.hasProfile is true for a signed-in profile` still passes without the flag; delete the tests that only covered the flag.
- [ ] **Step 2:** run, expect FAIL (or compile errors for removed symbols). **Step 3:** remove the flag from every layer listed above. **Step 4:** `./gradlew :shared:allTests :androidApp:assembleDebug` pass, and the iOS app builds in Xcode.
- [ ] **Step 5: Verify:** against the test server, a file the device can't direct play transcodes without any setting, on the emulator and the simulator.
- [ ] **Step 6: Commit:** `task: remove the transcoding setting`
- [ ] **Step 7:** Mark `docs/plans/05-device-remux.md` as needing a redesign (its "On this device / On the server" choice hung on this flag); update `docs/ROADMAP.md` if it mentions the flag.

**Phase B done when:** tests and both app builds pass, the verification above is recorded in this plan's "What was built"; stop for review.

---

## Phase C: Skip Intro and Credits

### Task 7: Segments in the player state (shared)

**Files:** Modify `ui/playback/PlaybackViewModel.kt`, `PlaybackModel.kt`, `ui/common/Strings.kt`; Test `PlaybackViewModelTest.kt`.

**Interfaces:**
- Produces: `PlaybackUiState.skipOffer: SkipOffer? = null` where `data class SkipOffer(val label: String, val toMs: Long)`; `fun onSkip()` sets `PlaybackUiState.pendingSeekMs: Long?` (the segment end), which the native player consumes and clears with `fun onSeekHandled()`; `Strings.skip(type: SegmentType): String` ("Skip intro", "Skip credits", "Skip recap", "Skip preview", "Skip ad"); segments are loaded once per item in `open()`.
- [ ] **Step 1: Write failing tests:** `offer appears inside an intro and stays for the whole segment`; `no offer outside any segment`; `outro offers "Skip credits"`; `skip sets a seek to the segment end`; `no segments means no offer and no error`; `a seek past the segment removes the offer`.
- [ ] **Step 2-4:** run FAIL → implement using `onPosition` to recompute the offer → PASS.
- [ ] **Step 5: Commit:** `feat: offer to skip intros and credits`

### Task 8: Skip button on both platforms

**Files:** Android `PlayerControls.kt`/`PlayerOverlays.kt` (button), `PlayerScreen.kt` (handle `pendingSeekMs` → `player.seekTo`, then `onSeekHandled()`); iOS `PlayerOverlays.swift`, `PlayerView.swift`.

- [ ] **Step 1:** A bottom-end button labelled from `skipOffer.label`, visible **even with the controls hidden** (`guardrails.md`), using the platform button style; not aqua (aqua is Play/Resume only), uses the secondary style.
- [ ] **Step 2: Verify:** the Task 1 media: button appears at 0:10, stays to 0:40 with controls hidden, tapping jumps to 0:40 and reports a seek to the server; Credits button at 2:30.
- [ ] **Step 3: Commit:** `feat: add skip button to the player` (one per platform).

**Phase C done when:** tests pass and both players verified; stop for review.

---

## Phase D: Trickplay

### Task 9: Trickplay state (shared)

**Files:** Modify `PlaybackViewModel.kt`, `PlaybackModel.kt`; Test `PlaybackViewModelTest.kt`.

**Interfaces:** Produces `PlaybackUiState.trickplay: TrickplayTrack?` loaded in `open()` for the chosen `mediaSourceId` (null if none); `PlaybackStream.headers` already carries the auth the native layer uses to fetch tiles.

- [ ] **Step 1: Write failing tests:** `trickplay is loaded for the played media source`; `no trickplay entry leaves it null`; `trickplay for another media source is ignored`.
- [ ] **Step 2-4:** FAIL → implement → PASS.
- [ ] **Step 5: Commit:** `feat: load trickplay for the playing item`

### Task 10: Scrub preview on both platforms

**Files:** Android `PlayerControls.kt` (`Scrubber`), new tile loader in `PlayerOverlays.kt` (Coil is already in use; fetch tiles with the stream's headers and crop by `locate()`); iOS `PlayerControls.swift` (`PlayerScrubber`), a small tile cache.

- [ ] **Step 1:** While dragging, show the cropped thumbnail above the thumb with the timestamp; with `trickplay == null` the timestamp alone. Cache decoded tiles per playback (builder's call), drop on close.
- [ ] **Step 2: Verify:** scrub across the generated file; the thumbnail changes and matches the position; an item without trickplay shows only the timestamp.
- [ ] **Step 3: Commit:** `feat: show trickplay previews while scrubbing` (one per platform).

**Phase D done when:** as above; stop for review.

---

## Phase E: Speed and format badges

### Task 11: Playback speed

**Files:** `PlaybackViewModel.kt` (`PlaybackUiState.speed: Float = 1f`, `fun onSelectSpeed(Float)`; speeds `0.5, 0.75, 1, 1.25, 1.5, 2`), `Strings.kt` (`Strings.speed`, `Strings.speedValue(Float)`), `PlaybackModel.kt`; Android `PlayerScreen.kt` (`player.setPlaybackSpeed`), a speed control in the top bar/sheet; iOS `PlayerController.swift` (`player.rate` when playing, remembering the chosen rate across pause), `PlayerControls.swift`.

- [ ] **Step 1: Write failing tests:** `selecting a speed updates state`; `speed resets to 1 for a new item`; `an unsupported speed is ignored`; `progress reports are unaffected` (assert the reporter calls are unchanged).
- [ ] **Step 2-4:** FAIL → implement → PASS.
- [ ] **Step 5: Verify** on both platforms: 2x plays faster, position still reported in media time, pause/resume keeps the speed (AVPlayer: setting `rate` starts playback, so apply it only while playing and re-apply on resume).
- [ ] **Step 6: Commit:** `feat: add playback speed`

### Task 12: HDR and format badge

**Files:** `ui/item/ItemFacts.kt` (new `MediaItem.formatBadge(): String?` → one badge, priority Dolby Vision, HDR10/HLG, then 4K, then 1080p…, per `guardrails.md`'s one-badge rule), `Strings.kt` (`Strings.badgeDolbyVision`, `badgeHdr10`, `badgeHlg`, `badge4k`, `badgeHd`), `ItemScreen.kt`/`ItemView.swift` (badge next to the facts line), player top bar badge from `PlaybackUiState.badge: String?` (from the plan's `videoFormat`); Test `ItemFactsTest.kt`.

- [ ] **Step 1: Write failing tests:** `Dolby Vision wins over resolution`; `4K SDR shows 4K`; `no video stream shows no badge`; `only one badge`.
- [ ] **Step 2-4:** FAIL → implement → PASS. **Step 5: Verify** on the detail page and the player with the HDR10 sample.
- [ ] **Step 6: Commit:** `feat: show format badge on item page and player`

**Phase E done when:** tests pass, both verified; stop for review.

---

## Phase F: Picture-in-picture, `Stopped`, mini-player groundwork

### Task 13: PiP signals and `Stopped` in the ViewModel (own commits)

**Files:** `PlaybackViewModel.kt`, `PlaybackModel.kt`, `NowPlaying` wiring; Test `PlaybackViewModelTest.kt`. **Keep this diff small: Phase 2 edits the same stop path.**

**Interfaces:** Produces `fun onEnteredPictureInPicture(positionMs: Long)`, `fun onLeftPictureInPicture(positionMs: Long, closed: Boolean)` (`closed` = window dismissed → `reporter.stop`; not closed = expanded back, nothing reported), `PlaybackUiState.inPictureInPicture: Boolean`; while in PiP `onBackgrounded` is ignored. `NowPlaying.update` is called on play/pause/position/close; `NowPlaying.clear()` on `onClosed`/`onEnded`/PiP closed.

- [ ] **Step 1: Write failing tests:** `entering PiP does not send Stopped`; `progress keeps flowing in PiP`; `closing the PiP window sends one Stopped`; `expanding from PiP sends nothing and keeps the session`; `backgrounding without PiP still sends Stopped`; `NowPlaying reflects play and pause and clears on close`.
- [ ] **Step 2-4:** FAIL → implement → PASS.
- [ ] **Step 5: Commit:** `feat: keep the session alive in picture-in-picture` (shared only).

### Task 14: Android PiP and media notification

**Files:** `AndroidManifest.xml` (`supportsPictureInPicture="true"` on `MainActivity`), `MainActivity.kt`, `app/player/PlayerPip.kt` (new), `PlayerScreen.kt` (`ON_PAUSE` skips `player.pause()` + `onBackgrounded` while in PiP), new `app/player/PlayerSession.kt` (Media3 `MediaSession` + notification), `gradle/libs.versions.toml` + `androidApp/build.gradle.kts` (`media3-session`, latest stable).

- [ ] **Step 1:** `setPictureInPictureParams` with `setAutoEnterEnabled(true)` (API 31+) and the video aspect ratio; below 31, enter from `onUserLeaveHint`. `onPictureInPictureModeChanged` calls `onEnteredPictureInPicture` / `onLeftPictureInPicture(closed = lifecycle is not RESUMED)`; hide controls in PiP.
- [ ] **Step 2:** `MediaSession` bound to the ExoPlayer for play/pause and the notification; released with the player.
- [ ] **Step 3: Verify (emulator):** Home enters PiP and keeps playing; the server session stays open; closing the PiP window sends exactly one `Stopped`; tapping it returns to the full player at the same position; notification play/pause works; with PiP unavailable, Home still stops as before.
- [ ] **Step 4: Commit:** `feat: add picture-in-picture and media notification on Android` and `chore: add media3-session` (dependency first).

### Task 15: iOS PiP and the prepared (unmounted) mini player

**Files:** `Info.plist` (`UIBackgroundModes` → `audio`), `PlayerPictureInPicture.swift` (new, `AVPictureInPictureController` on `PlayerLayerView`'s layer), `PlayerView.swift` (`onDisappear` must not tear down while PiP is active; `restoreUserInterfaceForPictureInPictureStop` re-presents the player), `PlayerController.swift`, new `MiniPlayerView.swift` (iOS 26+ only, title, play/pause, tap → `NowPlaying.requestRestore`), a `#Preview` for it.

- [ ] **Step 1:** Wire the PiP delegate callbacks to `onEnteredPictureInPicture` / `onLeftPictureInPicture`; `MPNowPlayingInfoCenter` gets title and position.
- [ ] **Step 2:** `MiniPlayerView` reads the shared `NowPlaying` state via the facade; it is **not mounted anywhere**.
- [ ] **Step 3: Verify (simulator):** PiP starts on leaving the app, stays alive, close sends one `Stopped`, tap restores; the mini player renders in its preview only. Report it as "built, not verified in place".
- [ ] **Step 4:** Add to the roadmap and this plan: the mini player is finished when the tab bar mounts it (`.tabViewBottomAccessory`) and it is checked on the simulator; whoever builds the tab bar owns that.
- [ ] **Step 5: Commit:** `feat: add picture-in-picture on iOS`; `feat: add mini player view (not mounted)`.
- Done (Phase F): the mini player (`MiniPlayerView`, `NowPlayingModel`) is **not finished** until the tab bar mounts it with `.tabViewBottomAccessory`, it is checked on the simulator, and play and pause reach the player (`NowPlaying` carries state and the restore request, no command yet). Whoever builds the tab bar owns that (also in `docs/ROADMAP.md`).

**Phase F done when:** tests pass, both verified as above; stop for review.

---

## Phase G: "Are you still watching?"

### Task 16: Binge prompt (shared)

**Files:** `PlaybackViewModel.kt` (constructor gains `nextUp: NextUpSource`, `binge: BingeCounter`), `PlaybackModel.kt`, `Strings.kt` (`Strings.stillWatchingPrompt`, `Strings.stillWatchingContinue`, `Strings.stillWatchingStop`); Test `PlaybackViewModelTest.kt` with a `FakeNextUpSource` that can emit `advanced`.

**Interfaces:** Produces `PlaybackUiState.stillWatching: Boolean`; `fun onUserInteraction()` (calls `binge.interacted()`; the native screens call it for touches, scrubs, buttons, and for PiP/notification/media-key controls); `fun onContinueWatching()` (resets, clears the prompt, player resumes); `fun onStopWatching()` (same as close). The ViewModel calls `binge.autoAdvanced()` when `nextUp.advanced` emits; when `binge.shouldAsk(threshold)` it sets `stillWatching = true` and the native player pauses. `onClosed` calls `binge.reset()`. **Rebase step:** Phase 2's countdown must check `BingeCounter.shouldAsk` before starting; until then the prompt shows when the up-next window opens (`Outro` start or the last 30 s) or at the next advance.

- [ ] **Step 1: Write failing tests:** `prompt after three auto advances`; `interaction resets the count`; `threshold 0 never prompts`; `threshold 2 prompts after two`; `a new view model for the next episode keeps the count`; `closing the player resets`; `continue clears the prompt and the count`.
- [ ] **Step 2-4:** FAIL → implement → PASS. **Step 5: Commit:** `feat: ask whether the viewer is still watching`

### Task 17: Prompt UI and the threshold setting

**Files:** Android `PlayerOverlays.kt`, `PlayerScreen.kt` (pause while `stillWatching`; forward `onUserInteraction()` from the tap layer, scrubber, buttons, `MediaSession` callbacks and PiP actions), `SettingsScreen.kt` (picker: Off, 2, 3, 4, 5); iOS `PlayerOverlays.swift`, `PlayerView.swift`, `SettingsView.swift`.

- [ ] **Step 1: Verify with the fake:** debug-build only, a hook that emits `advanced` N times (clearly marked, removed on rebase) shows the prompt after N, "Keep watching" resumes, interaction resets; the Settings picker changes N.
- [ ] **Step 2:** Mark the binge flow **unverified end to end** in the plan until Phase 2's real `NextUpSource` is in.
- [ ] **Step 3: Commit:** `feat: show still-watching prompt` (one per platform).

**Phase G done when:** tests pass and the fake-driven check above is recorded; stop for review.

**Done (Phase G), as built:** the prompt gates Phase 2's in-ViewModel countdown (no `NextUpSource`, no fake): a countdown that runs out counts as an auto advance, any input resets the count, and at the threshold the card is replaced by the prompt. Driven for real with the four-episode test show (`docs/test-server.md`, setting at After 2 episodes, about 8 minutes hands-off). iOS: verified (prompt at 2:31 of episode 3, video paused, "Keep watching" resumed and the card came back). Android: Settings picker verified; the prompt itself was **not** seen on the emulator (it lost its connection to the server mid-run); covered by the shared tests only.

---

## Phase H: HDR

### Task 18: Android surface per item; iOS check

**Files:** `res/layout/player_view_surface.xml` (new, `surface_type="surface_view"`), `res/layout/player_view_texture.xml` (unchanged), `PlayerScreen.kt` (inflate the layout by `PlaybackStream`'s `dynamicRange` — add `PlaybackStream.dynamicRange: DynamicRange` set from the plan; `Sdr` → texture, others → surface; HDR exit is a plain fade or none), iOS `PlayerController.swift` (no tone-mapping overrides; confirm `AVPlayerItem` passes HDR).

**Interfaces:** Consumes `PlaybackPlan.videoFormat.dynamicRange` (Task 2).

- [ ] **Step 1: Write failing test:** `the stream carries the dynamic range of the plan` (`PlaybackViewModelTest`).
- [ ] **Step 2-4:** FAIL → implement → PASS.
- [ ] **Step 5: Verify:** on the emulator, the HDR10 sample inflates the surface layout (log the choice), plays, and Back leaves without freezing; an SDR item still uses the texture layout and the old transition. HDR *output* is not checkable on either simulator and is reported as unverified; log the Android `Display.hdrCapabilities` and the iOS `AVPlayer.availableHDRModes` to show the platform's answer.
- [ ] **Step 6: Commit:** `feat: use a surface view for HDR playback on Android`

**Phase H done when:** tests pass, verified as above; stop for review.

---

## Closing tasks

- [ ] Update `docs/ROADMAP.md` (tick Phase 3 items; the mini player and HDR output notes), `docs/design/android.md`/`ios.md` only if a decision changed them (none planned), and this plan's status with "What was built / left out", following plans 03 and 05.
- [ ] Update CLAUDE.md's stack list with `media3-session`.

## Rebase checklist (after Phase 2 lands)

1. Rebase `develop`-based branch; **renumber the schema to version 4** (or the next free one) and regenerate its JSON; keep both migrations.
2. Replace `NoNextUpSource` with Phase 2's implementation in Koin; delete the debug-only `advanced` hook (Task 17).
3. Make Phase 2's auto-advance countdown check `BingeCounter.shouldAsk(threshold)` first; confirm their countdown is not in `PlaybackUiState`.
4. Reconcile the `PlaybackViewModel` stop path (Task 13) with Phase 2's pending-record write; re-run `PlaybackViewModelTest`.
5. Re-run the binge flow end to end on the Task 1 server and update the "unverified" notes.
