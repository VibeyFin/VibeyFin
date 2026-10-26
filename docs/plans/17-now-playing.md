# 17 · Now Playing in Control Center (and the Android media notification) Plan

**Goal:** While a title plays, the system's media card shows it with working controls: Control Center and the lock screen on iOS, the media notification on Android. Play, pause, skip and scrub on the card reach the player, and the card goes away when playback ends.

**Where it stands**
- **iOS:** `PlayerController.updateNowPlayingInfo` fills `MPNowPlayingInfoCenter` (title, subtitle, elapsed time, rate, duration). That is why the card already shows when you leave the app. There is **no `MPRemoteCommandCenter` handler**, so the card's buttons do nothing or are greyed, and there is no artwork. The info is also refreshed only on a few events, so the elapsed time on the card can drift.
- **Android:** `rememberPlayerSession` (`PlayerSession.kt`) builds a Media3 `MediaSession` and a `PlayerNotificationManager` notification. They live only while the player screen is in front or in a PiP window; leaving the app without PiP pauses the video and the notification goes. No service.

**Decision needed (product):** should playback ever continue with the app in the background and no PiP window? Today it does not, and the Phase 3 roadmap line says leaving pauses. Without it, Control Center can still show the card, but its Play brings the app's player back to the foreground; it cannot keep video going. The plan below assumes **no background playback** (the current rule) and makes the card correct and controllable while the app is in front, in PiP, or just left. Say so if you want background audio-style playback; that is a separate plan (iOS `audio` mode is already declared, but video would need the layer detached).

## Global constraints

- Same rules as plan 16: logic in `shared/`, no string literals in native code, no new dependency without asking (none needed: `MediaPlayer`, Media3 session are already in use).
- A kids profile and a signed-out profile behave as today. The card never shows another profile's title after a switch.
- Verify iOS on a device: the simulator shows a Now Playing card, but Control Center remote commands and the lock screen need a real iPhone. Say plainly what ran where.

## Tasks

### Task 1 (iOS): remote commands

**Files:** `iosApp/iosApp/Player/PlayerController.swift`, new `iosApp/iosApp/Player/PlayerRemoteCommands.swift`.

- Register `MPRemoteCommandCenter` handlers when a stream opens, and remove them in `tearDown()` (a stale handler would control a closed player):
  - `playCommand`, `pauseCommand`, `togglePlayPauseCommand` → `play()` / `pause()` / `togglePlayPause()`.
  - `skipBackwardCommand` / `skipForwardCommand` with `preferredIntervals` = the profile's seconds from `PlaybackUiState`; they call the same path as the player's own skip buttons, so the server hears a seek.
  - `changePlaybackPositionCommand` → `seek(to:)` through the same callback the scrubber uses.
  - Disable `nextTrackCommand` and `previousTrackCommand` (no queue).
- The handlers go through `PlayerEvents`, so progress reporting to the server is unchanged.
- **Test:** a small `PlayerRemoteCommandsTests` can only check registration and removal; the controls themselves are verified on a device.

### Task 2 (iOS): keep the card accurate

- Call `updateNowPlayingInfo()` on play, pause, seek, rate change and about once a second while playing, and set `MPNowPlayingInfoPropertyMediaType = .video`.
- Artwork: a Primary or Backdrop image for the item via `MPMediaItemArtwork`. `PlaybackUiState` does not carry an image URL yet; add `artworkUrl` to it from `jellyfin-mapping.md`'s image rule (shared, test in `PlaybackViewModelTest`), and load it with `URLSession` using the same auth header as the stream. A failed load leaves the card without artwork.
- Clear the info and the commands when the session ends (already done in `tearDown`; also on `finished`).

### Task 3 (iOS): the PiP / Control Center / in-app handoff

- With the profile's Picture in picture setting on, tapping the card's Play while the player is not in front must not start a second player. The remote commands act on the live `PlayerController` in `PlayerSession`, which is the only owner.
- Check on a device: Control Center during PiP, lock screen with the video playing in PiP, and the card after closing the PiP window.

### Task 4 (Android): the media notification

- **Under the "no background playback" rule:** the notification already shows while the player is in front or in PiP. Check, and fix only what is wrong: it carries the title, subtitle, artwork (same `artworkUrl`), play and pause, and it disappears when the screen goes. Add the artwork and make sure the notification's channel and permission are right on Android 13+ (it shares the `POST_NOTIFICATIONS` request that Notifications already asks for; do not ask for it a second time).
- **If background playback is wanted:** a `MediaSessionService` with the player moved into it, a foreground service type `mediaPlayback`, and the screen attaching its surface to a player it no longer owns. This is a large change and needs its own plan and your approval first.

### Task 5: docs and checklist

- `docs/design/player.md`: a Now Playing section (what the card shows and which commands work).
- `docs/ROADMAP.md`: tick when done.

## Review focus

- Remote commands never outlive the player that registered them.
- A seek from Control Center is reported to the server like one from the scrubber.
- No card shows after Stop, after a profile switch, or for a title that failed to load.
- Picture in picture off: leaving the app pauses and the card shows Paused, not Playing.
