# iOS playback

How the iOS player picks an engine and how it behaves. The Android player is ExoPlayer alone and has none of this.

## Two engines, one player

`PlayerController` and the controls talk to one `PlaybackEngine` (`Player/Engines/PlaybackEngine.swift`). `SwitchingEngine` holds two:

- **`AVPlayerEngine`**: Apple's player. First choice whenever it can play the file, because it costs the least CPU and battery.
- **`AetherPlaybackEngine`**: [AetherEngine](https://github.com/superuser404notfound/AetherEngine), pinned to an exact version in the Xcode project. It demuxes with FFmpeg and plays through AVPlayer on a loopback stream or decodes in software. It takes what AVPlayer cannot: MKV, WebM, AVI, TS, VP9, AV1, MPEG-2, VC-1, DTS, TrueHD and similar. It is built the first time a title needs it.

Everything else (controls, Now Playing, remote commands, PiP wiring, the position clock) is shared and engine-independent.

## How the engine is chosen

The choice is made per title, in shared code, with no setting:

1. `PlaybackCapabilitiesSource.candidates()` lists the engines in order, each with what it can play (`IosPlaybackCapabilities`: AVPlayer, then Aether).
2. `PlaybackRepository.negotiate` asks the server with each engine's capabilities. The first that gets a **direct play** wins. If none does, the first engine's answer stands: the server's stream (direct stream or transcode), played in AVPlayer.
3. The plan carries the engine (`PlaybackPlan.engine`); `SwitchingEngine.open` mounts that engine's picture.

The stats overlay shows the method, the engine and the codecs. Five taps on the title in the player toggle it. The item screen shows the same method under Play before playback (`PlayoutLine`, `docs/design` mockups).

## Subtitles

- **Text** (SRT, ASS, VTT): the app fetches the file from the server and paints it (`SubtitleOverlay`), on both engines.
- **Image** (PGS, DVD, DVB) on **Aether**: Aether decodes the bitmaps and the app paints them over the video (`ImageSubtitleOverlay`). The title stays a direct play.
- **Image on AVPlayer titles**: the server burns the subtitle in, which makes the stream a transcode.
- Choosing a subtitle stays in the player when the track is a fetched file, or when it is embedded and the title direct plays. Otherwise the stream is reopened with the burn-in.

## AirPlay and Picture in Picture

- The picture and sound go to the receiver as the engine's stream. Subtitles the app paints stay on the phone.
- **AirPlay with an image subtitle showing**: the title moves to the server's stream with the subtitle burned in (`PlayerController.burnInForAirPlay`).
- **AirPlay stall**: if the player's clock has not moved 12 s after AirPlay started, the stream counts as unsupported and the shared model falls back to the server's stream.
- **PiP**: video works on both engines. The app-painted subtitles are not in the PiP window, and there is no burn-in for PiP.
- Aether's own subtitle renditions (WebVTT from on-device OCR of bitmap tracks) over AirPlay and PiP were tried and dropped: they stay off after the AirPlay reload and the receiver caches empty segments. The app does not use them.

## Known limits

- Aether needs iOS 18, which is the app's floor.
- Aether opens an MKV with a very large attachment block (about 23 MB of fonts) slowly over a WAN address, because the demuxer reads the attachments first.
- If Aether cannot start, the title falls back to the server's stream through the normal failure path.
- CPU, battery and thermal numbers for the two engines have not been measured.

## Logs

`PlayerLog` writes to Console, subsystem `app.vibeyfin`, category `player`: the engine per title, Aether's route and errors, AirPlay changes and fallbacks.
