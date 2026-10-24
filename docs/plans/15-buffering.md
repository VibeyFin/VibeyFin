# 15 · Buffering

Show how far the stream is loaded, and let each profile choose how far ahead the player loads.

## Scope

- **Scrubber buffered segment** on both platforms: a lighter bar between the played part (aqua) and the track, up to the loaded position. Spinner on stall already exists.
- **Setting "Buffer ahead"** in Settings → Playback, per profile: Auto, 10, 20, 30, 40, 50, 60 seconds. Default 20 seconds. Auto means the platform player's own behaviour.

## Design

- `account_settings.bufferAheadSeconds` (Room 7 to 8 auto-migration, default 20). `ProfileSettings.bufferAheadSeconds` and `BUFFER_AHEAD_CHOICES = [0, 10..60]`, 0 is Auto. Invalid values are ignored on write and read as the default.
- `PlaybackUiState.bufferAheadSeconds` carries it to the players; `SettingsViewModel` and `SettingsModel` expose the setter.
- Android: `DefaultLoadControl` with `setBufferDurationsMs(min = max = N s, playback 2.5 s, rebuffer 5 s)`; Auto uses the default. Built when the player is created, so a change applies from the next video. The scrubber reads `player.bufferedPosition`.
- iOS: `AVPlayerItem.preferredForwardBufferDuration = N` (0 is Auto); set on each new item. The scrubber reads the end of the `loadedTimeRanges` range holding the current time.
- Both players poll the buffered position with the position they already poll, and the scrubber draws it.

## Tasks

1. Shared: column, migration, settings, UI state, strings, tests. (started, uncommitted)
2. Android: load control, buffered position, scrubber track, settings row.
3. iOS: forward buffer, buffered position, scrubber, settings row, UI test step.
4. Docs: `player.md`, `android.md`, `ios.md`.

Tasks 2 and 3 run in parallel after 1.
