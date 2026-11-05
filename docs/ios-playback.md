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

## Downloads

A download is kept exactly as the server sent it (`GET /Items/{id}/Download`); nothing is converted on the device. When it is played, `OfflineLibrary.playable` makes the plan with the same rule as streaming, without the server: the first engine whose lists include the file's container, its video codec and every audio codec (AVPlayer, then Aether), and whose limits the video is within: the same level and bit depth limits streaming sends to the server (`VIDEO_LIMITS`). The download saves the video's level and bit depth for this. A download saved before that has none, and is judged by its codec alone. A file no engine lists goes to the last engine, Aether, which tries it.

- A download is refused before it is fetched when no engine lists its video codec (`IosPlayableVideo`, `DownloadFailure.Unplayable`).
- Offline there is no server to fall back to: a file the chosen engine cannot open ends in the playback error.
- Only subtitles that came as separate files are offered offline. Embedded and image subtitles are not, even on Aether.

### Progress and notifications outside the app

- **System progress (iOS 26 and up).** When a queue starts with the app in front, `DownloadSystemProgress` submits one `BGContinuedProcessingTask` (`<bundle ID>.download.queue`, listed exactly in `Info.plist`; registering a `.*` wildcard returned false, so the exact identifier is used). It does not carry the transfer: the background URLSession does. The task keeps the app running to report the download in hand (`IosDownloadScheduler.progress`, worded by `DownloadStatusTracker`) to the system: the title is "Downloading <title>", the second line "1.23 GB of 4.50 GB", and the bar follows the whole queue (finished videos plus the one in hand, the rest estimated at the average size, failed ones left out), so it only moves forward: a bar that fell back to zero with the next video got the task ended about 30 seconds later. It is never filled while the queue runs, and the two counts are written in the order that never reads as full on the way (`DownloadProgressUnits.apply`), because a full `Progress` makes the system end the task (later writes then log "Couldn't find ... in the list of started activities"); it is filled only when the queue ends. Writes to the task are held to one a second (`ProgressUpdateThrottle`, a new title or waiting text goes at once): the system ended tasks that were written to with every chunk. The system also ends the task on its own after a minute or two, and its expiry handler cannot tell that from the user's Cancel. Both only close the card: the transfers belong to the background URLSession and go on (pause them from the Downloads tab), and a new task is asked for while the app is in front. Below iOS 26 there is no bar.
- **Notifications (all versions).** `DownloadRunner` tells `DownloadAlerts` once when the queue is empty (per profile, with what finished) and once per failed download. The title is "Downloaded <title>" for one, "Downloaded N episodes for <show>" for several episodes of one show, and "Downloaded N videos" otherwise (`downloadedTitle`). Both kinds are local notifications posted by `DownloadAlertPoster` while the app is not in front, including in the wake the system gives the app when a background transfer ends. They follow the profile's "Download notifications" setting (on by default). The permission is asked once, at the first download, for a profile that has the setting on. A queue that the user paused part of reports nothing.
- **Not covered.** A force-quit app: the system cancels its background transfers, so nothing finishes and nothing is posted.
- **Not yet seen on a device.** That the bar keeps updating with the app suspended or the screen locked, and what the system does on expiry, are the spike in issue #31. The expiry handler cannot tell Cancel from the system ending the task, so neither pauses anything: the card closes and the downloads go on.

## Subtitles

- **Text** (SRT, ASS, VTT): the app fetches the file from the server and paints it (`SubtitleOverlay`), on both engines. Aether is told to skip an MKV's attached fonts (`skipsContainerAttachments`), which the app never uses and which made large anime remuxes open slowly.
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
- If Aether cannot start, the title falls back to the server's stream through the normal failure path.
- CPU, battery and thermal numbers for the two engines have not been measured.

## Logs

`PlayerLog` writes to Console, subsystem `app.vibeyfin`, category `player`: the engine per title, Aether's route and errors, AirPlay changes and fallbacks.
