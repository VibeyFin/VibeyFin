# Plan 03: Roadmap Phase 0 exit and Phase 1, core playback

Goal: finish the roadmap's Phase 0 (log in, list libraries, fetch an item's metadata) and deliver
Phase 1 (play a movie and an episode, direct and transcoded, with position reported). Logic is
shared; every screen and the player are native on each platform (plan 02).

Phase 1 can't start without Phase 0's exit, and plan 01 stops short of it (its Phases 6 and 7, sign-in
and sessions, are unbuilt, and it lists library browsing as out of scope). So the order is:

1. Plan 01 Phase 6 (Who's watching and sign-in) and Phase 7 (sessions, Quick Connect, startup).
2. This plan's Phase A: libraries and items, which closes roadmap Phase 0.
3. This plan's Phases B and C: roadmap Phase 1.

## Roadmap mapping

| Roadmap item | Where |
|---|---|
| Phase 0: auth (password, Quick Connect), token, device id, client name | Plan 01 Phases 6 and 7 |
| Phase 0: version and capability check | Done in plan 01 Phase 3 (rejects below 10.10) |
| Phase 0: API client with token, retries, error handling | Plan 01 Phase 7 (401) and this plan's Phase A (retries, typed errors) |
| Phase 0 exit: list libraries, fetch an item's metadata | Phase A |
| Phase 1: PlaybackInfo with a device profile, direct play vs HLS | Phase B |
| Phase 1: player behind a platform boundary | Phase C |
| Phase 1: progress beacon, `Stopped` on backgrounding and crash | Phase B (logic), Phase C (platform hooks) |
| Phase 1: audio and subtitle selection from `MediaStreams` | Phase B (logic), Phase C (UI) |

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

## Phase A: Libraries and items (closes roadmap Phase 0)

- Retries for idempotent calls (not for sign-in or beacons that are not safe to repeat), typed API
  errors, `GET /UserViews`, `GET /Items?parentId=`, `GET /Items?ids=` for one item's metadata.
- Home lists libraries; a children screen lists what's inside; an item page shows metadata and a
  Play or Resume button (the button is wired in Phase C).
- Done when: on Android, signed in to a real server, Home lists the libraries, a folder opens, a
  movie's page shows its title, year, runtime, genres and overview, and tests cover the repository.

## Phase B: Playback negotiation, reporting and track selection (shared)

- `POST /Items/{id}/PlaybackInfo` with a device profile built from per-platform capabilities.
  Choose the media source, decide direct play, direct stream or transcode, and build the stream URL.
- A reporter for `/Sessions/Playing`, `/Sessions/Playing/Progress` (every 10 s and on pause, resume
  and seek) and `/Sessions/Playing/Stopped`, with the pending-stop recovery described above.
- `PlaybackViewModel`: state for the player, events from it, track selection by renegotiation.
- Done when: MockEngine tests cover negotiation (direct, stream, transcode, no compatible stream),
  the beacon's lifecycle and recovery, and track selection.

## Phase C: Native players

- Android: Media3 ExoPlayer with custom Compose controls, audio and subtitle pickers, and `Stopped`
  on stopping the activity.
- iOS: AVPlayer in SwiftUI with the same controls. Written without being able to run it.
- Done when: on an emulator against a real server a movie and an episode play (direct, and forced
  transcode), position is reported to the server, and switching audio or subtitles restarts at the
  same position. iOS is compile-checked and parsed only.

## Out of scope

Roadmap Phases 2 to 6: Continue Watching, Next Up, auto-advance, segments, trickplay, HDR,
search, My List, offline, parental controls. Picture-in-picture, casting, background audio.
