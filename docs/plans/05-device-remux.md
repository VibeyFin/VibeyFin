# Plan 05: Transcoding on the device (iOS), with a per-profile choice of where

Goal: a server with little compute is never asked to re-encode video. On iOS, files AVPlayer can't open
(MKV, WebM, DTS or TrueHD audio) are converted **on the phone** into a stream AVPlayer can play, and each
profile can choose to have the **server** do that work instead. Android already plays most files
directly (ExoPlayer plus the ffmpeg audio decoder), so there the choice only decides whether the server
may re-encode video.

**Needs redesign (2026-10-20):** the owner scrapped the per-profile `allowTranscoding` flag (plan 07, Task 6c: no other Jellyfin client has such a setting). This plan's "On this device / On the server" choice was built on it, so its Decisions, Phase B and the questions below must be redone without a setting before any work starts.

**Status (2026-10-20):** planned, nothing built. This plan was written without a design interview: every
call marked *(my call)* is a guess for the owner to confirm or change. It follows the video-only
transcoding setting added in plan 03's follow-up work (`ProfileSettings.allowTranscoding`).

## Why, and what was ruled out

- Remuxing on the server (copy the video, convert the audio) is cheap, and the profile setting already
  allows it. What a small server can't do is re-encode video. But iOS depends on the server for every MKV
  today, and the server's answer for HEVC-in-MKV is a re-encode, because our HLS profile lists only H.264
  for iOS.
- **VLCKit and MPVKit were rejected.** VLCKit 3 can't send our `Authorization` header (a VideoLAN answer
  from 2016, unconfirmed since), VLCKit 4 has been an alpha since 2023, and the only SwiftPM package is a
  hobby wrapper around a binary one person built. MPVKit's own README calls it learning-grade. Both would
  also replace AVPlayer and lose AirPlay, picture-in-picture and HDR handling.
- **Re-encoding on the phone is not the plan.** It would decode and encode every frame for nothing. The
  work here is a *remux*: unpack the container, copy the video as it is, and convert only what AVPlayer
  can't play (the audio, in practice).

## Decisions

- **One setting, two places for the work *(my call)*.** The existing per-profile `allowTranscoding`
  column (default off) is kept and its meaning is renamed in the UI: **off = "On this device"** (the
  default), **on = "On the server"**. No migration: the stored values already mean that. The row becomes
  a two-option choice with help text; the strings say "transcoding" for both. The server is never asked
  to re-encode video when it is "On this device", on either platform.
- **The remux runs in AVPlayer's path, not around it *(my call)*.** AVPlayer stays the player (AirPlay,
  PiP, HDR, Liquid Glass controls). A small engine in `iosApp` reads the original file over HTTP with
  FFmpeg's demuxer, and serves an HLS stream with fragmented-MP4 segments on a loopback port that only
  this app can reach. AVPlayer opens that URL as it would any HLS stream. The shared code and
  `PlaybackViewModel` don't know it exists, except for one flag on the stream.
- **Video is copied, never decoded.** H.264 and HEVC (tagged `hvc1`) go through untouched. A video codec
  AVPlayer can't decode on this device (VP9, AV1 on an older chip, 10-bit HEVC on an old phone) is not
  handled on the device: it's an error that says to switch the profile to "On the server".
- **Audio is converted on the device only when AVPlayer can't play it.** AAC, AC-3, E-AC-3, ALAC and MP3
  are copied. DTS and TrueHD are decoded with FFmpeg's own decoders and encoded to AAC (and E-AC-3 where the
  channel layout needs it). This is light work for a CPU.
- **Segments are built on demand, keyed to the file's keyframes.** The playlist is a full VOD playlist
  from the container's duration and keyframe index (MKV Cues), so AVPlayer's scrubber and seeking work as
  they do for any HLS stream. A seek to a segment that isn't built yet repositions the demuxer there. A
  file with no keyframe index is scanned once at the start, and if that is too slow the file is refused
  with the "switch to the server" message.
- **Authentication stays in a header.** FFmpeg's `http` protocol takes a `headers` option, so the engine
  sends our `Authorization` header to the Jellyfin server itself. No token goes in a URL, and the
  loopback server serves only to this process (a random path prefix per playback).
- **Capabilities depend on the profile's choice.** `PlaybackCapabilitiesSource.current()` becomes
  `current(onDevice: Boolean)`. "On this device" on iOS adds `mkv` and `webm` (and the codecs above) to
  what the device can play, and marks them as playable after a local remux; "On the server" keeps today's
  list. Android ignores the argument for now.
- **FFmpeg comes from a build we control *(my call; Phase A decides)*.** Every iOS FFmpeg package I found is
  small or one person's hobby, and none confirmed it ships the MP4 and HLS muxers we need. The plan is to
  build FFmpeg from its own pinned release with a script checked into the repo: LGPL only, no GPL parts,
  dynamic framework so the LGPL relinking terms are met, and only the demuxers, muxers and audio codecs
  listed above. CLAUDE.md says to ask before adding a dependency, so Phase A ends in that question.
- **Subtitles are a later phase.** Text subtitles inside the MKV become WebVTT in the HLS playlist, which
  AVPlayer shows natively. Until then, picking a subtitle on a device-remuxed file shows the same
  "switch to the server" message the burned-in ones need.

## Phases

Each phase builds, passes its tests, and stops for review.

### Phase A: feasibility and the FFmpeg decision (throwaway spike)

Prove the idea on the simulator before changing the app.
- A command-line or test harness (not shipped) that opens an MKV with H.264 and DTS over HTTP with a
  custom header, writes an HLS playlist and fMP4 segments, and plays them in AVPlayer.
- Build FFmpeg for iOS and the simulator with a script; record the configure line, the binary size added
  to the app, and the licence set.
- Measure: time to first frame, a seek into an unbuilt segment, CPU and battery over a ten-minute play.
- Done when: a short report says whether this works, how big it is and how fast it seeks, and names the
  FFmpeg source. **Stop and ask the owner** before adding the dependency to the app.

### Phase B: the shared setting and capabilities

- `PlaybackCapabilities` gets `remuxContainers` (playable after a local remux) beside `containers`;
  `PlaybackCapabilitiesSource.current(onDevice)`.
- `negotiate` reads the profile's choice. For "On this device" it advertises the wider list and, when the
  server direct-plays a file whose container is in `remuxContainers` only, the plan says
  `remuxOnDevice = true` (a new field on `PlaybackPlan` and `PlaybackStream`).
- The Settings row on both platforms becomes the two-option choice. Strings, `Strings` in `shared/` first.
- Done when: `:shared:allTests` and both app builds pass; new tests cover the capabilities for each choice
  and `remuxOnDevice`; Android is unchanged in behaviour.

### Phase C: the remux engine (`iosApp`)

- `DeviceRemuxEngine`: opens the source, builds the keyframe index, produces the playlist and the
  segments, converts audio when needed, and serves them on loopback. One engine per playback, torn down
  with the player.
- Failure mapping to the existing `PlaybackFailure` (network, unauthorized, unsupported) so the shared
  retry logic is untouched. "Unsupported" here means the message that points to the server choice, not the
  server transcode fallback, which stays off while the profile says "On this device".
- Done when: unit tests cover the playlist and the index on small fixture files; a manual run plays an
  MKV with H.264 and DTS, an MKV with HEVC, and a file with no keyframe index.

### Phase D: wire it into the player

- `PlayerController.open` opens `stream.url` directly, or through the engine when `remuxOnDevice` is set.
  Audio track choice becomes the engine's job for these streams. Position reporting, seeking and the
  background and retry paths are unchanged.
- The AVPlayer seek tolerance and timing logs from the earlier work stay; add engine timings (segment
  ready, seek-to-ready).
- Done when: the iOS app plays a device-remuxed file end to end on the simulator, with progress reported to
  the server, seeks within the segment budget set in Phase A, and "Try again" working.

### Phase E: subtitles, polish and licences

- Text subtitles to WebVTT; the track menu works for remuxed files.
- Memory and cache limits (segments dropped behind the playhead), a battery check, a long-file check.
- An "Open-source licences" screen that names FFmpeg, its version, its licence and where to get the
  source, as the LGPL needs.
- Done when: verified against a real server with the owner's problem files; the plan's status, `ROADMAP.md`
  and CLAUDE.md are updated (the "AVPlayer on iOS" line gains "fed by an on-device remux for formats it
  can't open", and FFmpeg joins the stack list).

## Risks

- **Size and review.** FFmpeg adds several megabytes and the LGPL terms are real obligations. Phase A
  measures the first; Phase E covers the second.
- **HLS fMP4 details.** HEVC tagging, Dolby Vision, audio priming and discontinuities are where AVPlayer is
  picky. Phase A's report should list what it refused.
- **Seek speed on a remote file.** Each unbuilt segment is a ranged read over the network. If that is too
  slow on a poor link, the answer is the server choice, not more engineering here.
- **iOS only can be built here.** Xcode 27.1 is installed on this machine, so Phases A to D can be built
  and run on the simulator; a physical device is still needed for battery and AirPlay checks.

## Out of scope

- Re-encoding video on the device.
- Replacing AVPlayer, on either platform.
- Android remux (ExoPlayer already plays MKV; the ffmpeg audio decoder covers DTS and TrueHD).
- Downloads for offline playback.

## Questions for the owner

1. Is "On this device" the right default for every new profile, or only on iOS?
2. Should a device-remux failure ever fall back to the server by itself, or only by the user changing the
   setting? (My call: only the user, since the point is to protect the server.)
3. Are you happy for FFmpeg to be built from source by a script in this repo, rather than taking a
   third-party prebuilt package?
