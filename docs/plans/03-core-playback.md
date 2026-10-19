# Plan 03: Roadmap Phase 0 exit and Phase 1, core playback

Goal: finish the roadmap's Phase 0 (log in, list libraries, fetch an item's metadata) and deliver
Phase 1 (play a movie and an episode, direct and transcoded, with position reported). Logic is
shared; every screen and the player are native on each platform (plan 02).

**Status (2026-10-19):** Phase A is done, which closes roadmap Phase 0. Phases B and C (roadmap
Phase 1, playback) are **not started**: the owner asked to stop after roadmap Phase 0. Nothing in
Phase A plays media or shows a Play button.

Phase 1 can't start without Phase 0's exit, and plan 01 stops short of it (its Phases 6 and 7, sign-in
and sessions, are unbuilt, and it lists library browsing as out of scope). So the order is:

1. Plan 01 Phase 6 (Who's watching and sign-in) and Phase 7 (sessions, Quick Connect, startup).
2. This plan's Phase A: libraries and items, which closes roadmap Phase 0.
3. This plan's Phases B and C: roadmap Phase 1.

## Roadmap mapping

| Roadmap item | Where |
|---|---|
| Phase 0: auth (password, Quick Connect), token, device id, client name | Plan 01 Phases 6 and 7 (done) |
| Phase 0: version and capability check | Plan 01 Phase 3 (done; rejects below 10.10, where the roadmap says warn or degrade) |
| Phase 0: API client with token, retries, error handling | Plan 01 Phase 7 (401) and this plan's Phase A (retries, typed errors) (done) |
| Phase 0 exit: list libraries, fetch an item's metadata | Phase A (done) |
| Phase 1: PlaybackInfo with a device profile, direct play vs HLS | Phase B (deferred) |
| Phase 1: player behind a platform boundary | Phase C (deferred) |
| Phase 1: progress beacon, `Stopped` on backgrounding and crash | Phase B (logic), Phase C (platform hooks) (deferred) |
| Phase 1: audio and subtitle selection from `MediaStreams` | Phase B (logic), Phase C (UI) (deferred) |

## Decisions

- **The player is native, driven by shared state.** The roadmap says the player sits behind
  `expect`/`actual`. With native UI per platform that boundary is simply the app module: Media3 in
  `androidApp`, AVPlayer in `iosApp`. The shared `PlaybackViewModel` decides what to play and owns
  the beacon; the native player renders `PlaybackUiState` (stream URL, headers, start position) and
  reports events back (`playing`, `paused`, `seeked`, `progress`, `ended`, `failed`, `stopped`).
  No shared player abstraction.
- **Track selection is a new negotiation.** Choosing an audio or subtitle track asks the server
  again with `AudioStreamIndex` / `SubtitleStreamIndex` and restarts at the current position.
  The server decides direct play, direct stream or transcode. Text subtitles are delivered as
  external files where the platform's profile says it can load them (Android); otherwise the
  server burns them in (iOS). One code path for both.
- **Stream auth.** Players send the `Authorization` header; the HLS URL the server returns already
  carries its own key. The shared code never builds URLs with the user's token.
- **Orphaned sessions.** A crash can't send `Stopped`. The beacon keeps a small pending record
  (item, session, last position) in storage and sends `Stopped` for it on the next start.
- **Browsing is generic.** Libraries, folders, series and seasons all open the same children list
  (`GET /Items?parentId=`), because real libraries nest (the demo's Movies are in folders).
  Playable items (movie, episode, video) open the detail page.
- Server minimum stays 10.10 (rejected below); whether to warn instead is the owner's call.

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

## Phase B: Playback negotiation, reporting and track selection (shared) — deferred, not started

- `POST /Items/{id}/PlaybackInfo` with a device profile built from per-platform capabilities.
  Choose the media source, decide direct play, direct stream or transcode, and build the stream URL.
- A reporter for `/Sessions/Playing`, `/Sessions/Playing/Progress` (every 10 s and on pause, resume
  and seek) and `/Sessions/Playing/Stopped`, with the pending-stop recovery described above.
- `PlaybackViewModel`: state for the player, events from it, track selection by renegotiation.
- Done when: MockEngine tests cover negotiation (direct, stream, transcode, no compatible stream),
  the beacon's lifecycle and recovery, and track selection.

## Phase C: Native players — deferred, not started

- Android: Media3 ExoPlayer with custom Compose controls, audio and subtitle pickers, and `Stopped`
  on stopping the activity.
- iOS: AVPlayer in SwiftUI with the same controls. Written without being able to run it.
- Done when: on an emulator against a real server a movie and an episode play (direct, and forced
  transcode), position is reported to the server, and switching audio or subtitles restarts at the
  same position. iOS is compile-checked and parsed only.

## Out of scope

Roadmap Phases 2 to 6: Continue Watching, Next Up, auto-advance, segments, trickplay, HDR,
search, My List, offline, parental controls. Picture-in-picture, casting, background audio.
