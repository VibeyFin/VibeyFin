# Plan 03: Roadmap Phase 0 exit and Phase 1, core playback

Goal: finish the roadmap's Phase 0 (log in, list libraries, fetch an item's metadata) and deliver
Phase 1 (play a movie and an episode, direct and transcoded, with position reported). Logic is
shared; every screen and the player are native on each platform (plan 02).

**Status (2026-10-20):** Phase A is done, which closes roadmap Phase 0. Phases B and C (roadmap
Phase 1) are done: negotiation, the progress beacon, a native player on both platforms, and audio
and subtitle selection, and the player's failures are told apart (a lost connection, a rejected token,
a stream the device can't play). Resume from the saved position is roadmap Phase 2; `Stopped` recovery
after a crash is **not built** and moves to the first item of the Phase 2 plan (see "What was left out"). Android is verified on an emulator against real
servers; iOS is written and type-checked, not built or run.

Phase 1 can't start without Phase 0's exit, and plan 01 stops short of it (its Phases 6 and 7, sign-in
and sessions, are unbuilt, and it lists library browsing as out of scope). So the order is:

1. Plan 01 Phase 6 (Who's watching and sign-in) and Phase 7 (sessions, Quick Connect, startup).
2. This plan's Phase A: libraries and items, which closes roadmap Phase 0.
3. This plan's Phases B and C: roadmap Phase 1.

## Roadmap mapping

| Roadmap item | Where |
|---|---|
| Phase 0: auth (password, Quick Connect), token, device id, client name | Plan 01 Phases 6 and 7 (done) |
| Phase 0: version and capability check | Plan 01 Phase 3 (done; rejects below 10.10, which the owner chose over warning or degrading) |
| Phase 0: API client with token, retries, error handling | Plan 01 Phase 7 (401) and this plan's Phase A (retries, typed errors) (done) |
| Phase 0 exit: list libraries, fetch an item's metadata | Phase A (done) |
| Phase 1: PlaybackInfo with a device profile, direct play vs HLS | Phase B (done) |
| Phase 1: player behind a platform boundary | Phase C (done) |
| Phase 1: progress beacon, `Stopped` on backgrounding and crash | Phase B (logic), Phase C (platform hooks) (done; crash recovery moved to the Phase 2 plan) |
| Phase 1: audio and subtitle selection from `MediaStreams` | Phase B (logic), Phase C (UI) (done) |

## Decisions

- **The player is native, driven by shared state.** The roadmap says the player sits behind
  `expect`/`actual`. With native UI per platform that boundary is simply the app module: Media3 in
  `androidApp`, AVPlayer in `iosApp`. The shared `PlaybackViewModel` decides what to play and owns
  the beacon; the native player renders `PlaybackUiState` (stream URL, headers, start position) and
  reports events back (`playing`, `paused`, `seeked`, `progress`, `ended`, `failed`, `stopped`).
  No shared player abstraction.
- **Track selection: the player where it can, the server where it must.** (Revised after testing
  against a real server; the first draft said every choice is a new negotiation.) For a direct play the
  server returns the same answer whatever track is asked for, so the player switches audio itself, by the
  track's position in the file. Subtitles the server sends as files (`DeliveryMethod: External`, one URL
  per text track, given up front) are all loaded with the video and switched in the player. Only two
  things need the server again, with `AudioStreamIndex`, `SubtitleStreamIndex` **and** the
  `MediaSourceId`, restarting at the current position: the audio of a stream the server prepares (HLS),
  and any subtitle it burns into the picture (`DeliveryMethod: Encode`: image-based subtitles, and every
  subtitle on a device that declares no subtitle formats, which is iOS). The server ignores the two
  indices unless the request names the media source.
- **Stream auth.** Players send the `Authorization` header; the HLS URL the server returns already
  carries its own key. The shared code never builds URLs with the user's token.
- **Orphaned sessions (built in plan 06, Phase A).** A crash can't send
  `Stopped`. The beacon will keep a small pending record (item, session, last position) in a Room table
  keyed by server and profile, because `Stopped` needs that profile's token, and send `Stopped` for it on
  the next start.
- **Player failures.** The native player maps what it gets to one of three kinds and calls
  `PlaybackViewModel.onFailed(kind)`; no platform error code reaches `shared/`. `Network` (a lost
  connection, a timeout, a 5xx) tries the same stream, direct or transcode, twice, after 1 s and then 2 s,
  from the current position, with the spinner showing and the server told it is paused; when the tries
  run out it is `LoadMessages.PLAYBACK_NETWORK` with "Try again", never a transcode, which would need the
  same connection. Playing 5 s past where it failed gives the tries back (the player reports "playing" the
  moment it opens a stream, so that can't be the signal). `Unauthorized` (a 401 on the stream) asks the
  server again once from the same position: the player fetches the stream itself and so bypasses the usual
  401 handling, but the new `PlaybackInfo` call goes through it, so a dead token ends the session in the
  one place that does that and a stale stream key gets a fresh address. A second rejection is
  `PLAYBACK_FAILED`. `Unsupported`, which is also everything the player can't place (every other HTTP
  status included), falls back to one transcode and then shows `PLAYBACK_FAILED`. "Try again" starts
  afresh, except after a lost connection on a stream that needed the transcode: that says nothing about
  what the device can play, so it goes straight back to the transcode instead of repeating a direct play
  that is known to fail.
- **Browsing is generic.** Libraries, folders, series and seasons all open the same children list
  (`GET /Items?parentId=`), because real libraries nest (the demo's Movies are in folders).
  Playable items (movie, episode, video) open the detail page.
- Server minimum stays 10.10: a server below it is rejected, not warned about (owner's decision).

## Phase A: Libraries and items (closes roadmap Phase 0) — done

- Retries for idempotent reads only: a read that gets no answer or a 5xx is tried up to three times
  with a doubling wait (400 ms, then 800 ms). Sign-in and anything that changes state is never
  repeated, and a 401, 403 or 404 comes straight back. Typed `ApiError`s were already in place; every
  load failure now maps to one worded message shared by both platforms.
- `GET /UserViews` for the libraries, `GET /Items?parentId=` for a page of what is inside a library,
  folder, series or season (sorted by name, 60 at a time, the next page loads as the end comes into
  view), `GET /Items?ids=` for one item's metadata.
- Home lists the libraries. Opening a folder, series or season shows its list; opening anything else
  shows its page: picture, title, year, runtime, rating, genres and overview, and for an episode
  the show and "S1 E5". **No Play or Resume button**: that belongs to Phase C.
- A 401 from any of these calls ends the session and returns to "Who's watching?" like everywhere
  else; a failed load says what happened and offers "Try again".
- Verified: shared tests (retries, repository, ViewModels), and on an Android emulator against the
  demo server: Home lists the libraries, Movies, a folder, a movie's page, Shows, a series, a season
  and an episode all open, back unwinds one screen at a time, and an offline load shows the message
  and recovers on "Try again". Not verified on a device: an ended session in the middle of browsing
  (the demo's account can't revoke its own token; covered by tests) and paging through a list longer
  than a screen (the demo has none; the load-more chain was checked with a page size of 1). iOS is
  compiled to klibs and its Swift parsed and type-checked against a stub of the shared API, not run.

## Phase B: Playback negotiation, reporting and track selection (shared) — done, basic (no track selection)

- `POST /Items/{id}/PlaybackInfo` with a device profile built from per-platform capabilities.
  Choose the media source, decide direct play, direct stream or transcode, and build the stream URL.
- A reporter for `/Sessions/Playing`, `/Sessions/Playing/Progress` (every 10 s and on pause, resume
  and seek) and `/Sessions/Playing/Stopped`, with the pending-stop recovery described above.
- `PlaybackViewModel`: state for the player, events from it, track selection by renegotiation.
- Done when: MockEngine tests cover negotiation (direct, stream, transcode, no compatible stream),
  the beacon's lifecycle and recovery, and track selection.

## Phase C: Native players — done, basic

- Android: Media3 ExoPlayer with custom Compose controls, audio and subtitle pickers, and `Stopped`
  on stopping the activity.
- iOS: AVPlayer in SwiftUI with the same controls. Written without being able to run it.
- Done when: on an emulator against a real server a movie and an episode play (direct, and forced
  transcode), position is reported to the server, and switching audio or subtitles restarts at the
  same position. iOS is compile-checked and parsed only.

## What was built (2026-10-20)

- **Shared** (`data/api/Playback*.kt`, `domain/Playback*.kt`, `ui/playback/`): `PlaybackRepository.negotiate`
  sends `PlaybackInfo` with a device profile built from per-platform `PlaybackCapabilities` (Android asks
  `MediaCodecList` and counts HEVC/VP9/AV1 only with a hardware decoder; iOS is a fixed list). It takes
  direct play if the server offers it, else the server's `TranscodingUrl`; `forceTranscode` turns direct
  play and direct stream off. `PlaybackReporter` queues `Start`, `Progress` (every 10 s while playing;
  at once on pause, resume and seek) and `Stopped` in order on an application-level scope, so `Stopped`
  still goes out after the screen is gone; a failed `Stopped` is tried once more. `PlaybackViewModel`
  hands the native player a `PlaybackStream` and takes its events. Failures are handled by kind (see
  "Player failures" above): Media3's error codes are mapped in `PlayerFailures.kt`, AVFoundation's (HTTP
  status from the item's error log, then `NSURLError` codes down the underlying errors) in
  `PlayerFailure.swift`.
- **Audio and subtitles** (see the decision above): `PlaybackViewModel.onSelectAudio` and
  `onSelectSubtitle` decide in-player or renegotiate (`PlaybackStream.audioInPlayer`, `subtitleFiles`,
  `PlaybackPlan.subtitleBurnedIn`); the picker state is `PlaybackUiState.audioTracks` and
  `subtitleTracks` ("Off" first), labelled with the server's own names. A switch inside one play session is
  reported at once with the new indices; Jellyfin stores them and offers them again next time (seen on the
  local server: the next play started on the Spanish audio and subtitles chosen before). Android:
  bottom sheet from a button in the top bar, `TrackSheet` (Audio and Subtitles side by side, each scrolling on its own), selection through Media3 track overrides
  (`PlayerTracks.kt`). iOS: a `Menu` with two `Picker`s, audio through `AVMediaSelectionGroup`.
- **Leaving the player on Android** (`PlayerScreen.kt`, `res/layout/player_view_texture.xml`): the video is
  drawn on a `TextureView`, not Media3's default `SurfaceView`, because a `SurfaceView` lives in its own
  layer, ignores the app's slide and fade, and hung frozen on screen. With a `TextureView` the app's own pop
  transition carries the video away and nothing has to be hidden or waited for. On Back the controls go
  at once, the player is stopped (so its decoder is released during the slide, keeping the last frame up)
  and the screen is popped immediately; the orientation and system bars are put back once the screen is
  gone, with the orientation request made off the main thread, because it blocked the main thread for
  400+ ms on the emulator and re-lays out everything. Measured on the emulator: pop 45-85 ms after the tap
  (an earlier version waited 0.7-1 s), finished and item page resumed about 0.6 s after (was 1.1-1.7 s);
  `player.release()` 3-50 ms (was ~400 ms). The cost: the item page shows in landscape layout until the
  turn completes (about 1.5 s on the emulator, expected to be far less on a phone), and a `TextureView`
  cannot pass HDR through, so roadmap Phase 3's HDR passthrough means going back to a `SurfaceView` (the
  layout file is the one place to change) and handling this exit differently.
- **Android**: Media3 1.11.1 (exoplayer, exoplayer-hls, ui; not in CLAUDE.md's stack list, named here),
  Compose controls over `PlayerView`, landscape lock on phones, system bars hidden, `MainActivity` handles
  configuration changes so rotation doesn't restart the player. The player pauses and reports `Stopped` on
  `ON_PAUSE`, not `ON_STOP`: on the emulator `ON_STOP` came ~4 s after Home, by which time the video
  surface was gone and the decoder failed.
- **iOS**: `PlaybackModel` facade; `PlayerController` (AVPlayer), `PlayerSurface`, `PlayerControls` (Liquid
  Glass behind `#available(iOS 26)`), `PlayerView`, `OrientationLock` plus an `AppDelegate` for the
  landscape lock. Presented as a full-screen cover from the item page.
- **Server contract** checked on the Jellyfin 12.2.0 demo server: `PlaybackInfo` with this profile (direct
  play offered; with direct play off, an HLS transcode with its own `ApiKey`), direct play with header
  auth (206), the HLS playlist (200), and `Playing`, `Progress` and `Stopped` (204, resume position
  saved for an item over the server's 5-minute minimum).
- **Verified on the Android emulator against that server**: direct play of an MP4; a transcode of an
  MPEG-4 Part 2 film; pause, seek and resume each reported with the right position and paused flag; Home
  ends the session on the server within a second and playing again starts a new one; Back and reaching the
  end both report `Stopped` and return to the item page.
- **Verified for audio and subtitles on the emulator against a local Jellyfin 12.2.0** (podman,
  media made with the container's ffmpeg: an MKV and an MP4 with two audio tracks and two subtitle
  tracks): audio switched live in a direct play (the player's selected track and the server's session
  both change, no new negotiation); text subtitles shown, switched between languages and still
  shown after seeking back; a device with no subtitle formats gets the subtitle burned into an HLS
  transcode, turning it off returns to direct play at the same position, and changing audio on that
  transcode renegotiates and continues from the same position.
- **Player failures.** `PlaybackViewModelTest` covers a retry from the same position (direct and transcode),
  the tries running out into `PLAYBACK_NETWORK` with "Try again" and no transcode, the tries coming back after
  5 s of playing (and not after the player merely reporting ready), "Try again" keeping a needed transcode
  after a lost connection and starting afresh after any other failure, a rejected token asking again from the
  same position, a dead token ending the session through the usual handling, and a second rejection ending as
  an error. The Media3 and AVFoundation mappings are not unit tested.
- **Verified on the Android emulator (2026-10-20), against the demo server and a local Jellyfin 12.2.0:**
  with the network cut and a seek past the buffer, a direct play (MP4) and an HLS transcode each show the
  spinner and no error, then, after three ExoPlayer errors (the failure and our two tries, about 9 s),
  `PLAYBACK_NETWORK` with "Try again"; with the network back, "Try again" resumes near the same position and
  plays on. A drop of about 7 s that ends during the tries recovers with no error shown (one ExoPlayer error
  logged); one of 1.5 s never reaches us, because ExoPlayer's own retries absorb it. A proxy that answers
  401 to the stream request only (the token stays valid): four 401s from the player, then exactly one new
  `PlaybackInfo`, then the second refusal ends as `PLAYBACK_FAILED` with no loop, no transcode and no exit;
  lifting the 401 and "Try again" resumed at the same position. Not isolated: revoking the token as admin
  and seeking ended the session within 4 s, but the seek's own progress report is rejected through the usual
  handling, so that run says nothing about the stream's 401. A local HEVC file direct-played on the
  emulator, whose goldfish decoder counts as hardware: this says nothing about a phone's.
- **Bugs the tests and the emulator found**: `Json` doesn't encode defaults, so fields such as
  `Protocol` and `Type` were silently dropped (the server would have read an HLS video profile as a
  progressive audio one); the `ON_STOP` timing above; the server silently ignoring track indices
  without a `MediaSourceId` (only a real server could show it); Media3's `setOverrideForType` not
  re-enabling a text type that was disabled; the video `SurfaceView` hanging, frozen, over the app's
  back transition, and the first fix for that (hide the video, wait for the rotation, then pop) adding
  about a second of blank screen.

## What was left out

- Resume from the saved position: playback always starts at the beginning (roadmap Phase 2).
- `Stopped` after a crash or a killed process (the pending-record recovery in the decisions above):
  roadmap Phase 2, first item (done in plan 06, Phase A). The same record covers a `Stopped` that can't go out offline: after a
  lost connection the server kept showing the item as playing at its last reported position until its own
  timeout.
- The error panel over the video has no scrim: white text on bright footage (the snow scene in the test
  film) is hard to read, which the design guardrail "text over artwork sits on `scrim`" rules out. It also
  shows the Pause icon while the error is up.
- Double-tap seek, ±10 s buttons, Cast, AirPlay, picture-in-picture.
- Subtitles on iOS are always burned in (a transcode). Delivering them as HLS WebVTT, which AVPlayer shows
  natively, would avoid that and is the next step if it matters.
- Side-loaded subtitles after a seek: Media3 re-delivers only cues that start after the seek point, so a
  single very long cue spanning it is missed. Real subtitles have short cues.
- iOS: not built or run. Unverified: `AVURLAssetHTTPHeaderFieldsKey` on HLS segment requests, the
  orientation lock, event ordering, the failure mapping (`errorLog()` HTTP status, `NSURLError` codes),
  how the glass and the track menu look, whether `AVMediaSelectionGroup`
  lists audio in the file's order, and the dismiss animation (it leaves landscape only after the cover is
  gone). Android: emulator only, no real device, no hardware HEVC path.

## Out of scope

Roadmap Phases 2 to 6: Continue Watching, Next Up, auto-advance, segments, trickplay, HDR,
search, My List, offline, parental controls. Picture-in-picture, casting, background audio.
