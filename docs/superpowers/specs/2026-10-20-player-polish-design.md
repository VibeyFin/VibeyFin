# Player polish (roadmap Phase 3): design

Status: draft for the owner's review, 2026-10-20. Calls marked *(my call)* are mine, not the owner's.

## Intent

Roadmap Phase 3 turns the working player (plan 03) into one that feels finished: scrubbing shows
previews, intros and credits can be skipped, HDR is not flattened, the app can keep playing in a
small window, and the player can explain what it is doing. The roadmap's exit: *skip-intro and
scrub previews working end to end.*

This phase runs in parallel with Phase 2 (watch status and continuity), which a colleague builds on
another branch. This branch is rebased onto Phase 2 when that is ready. Everything here is designed
so that rebase is mechanical.

## Scope

Eight items, **each on both Android and iOS**, verified on the Android emulator and the iOS
simulator, in this order (the roadmap's, with the stats overlay first because it makes the rest
measurable):

1. **Playback stats overlay.** Off by default, behind a per-profile setting. A shared
   `PlaybackDiagnostics` state, filled by Media3's analytics listener (Android) and the player item's
   access log (iOS). Shows: direct play, direct stream or transcode, and the transcode reasons from the
   stream URL (`reencodesVideo()` in `PlaybackRepository` already parses them), codecs, resolution,
   frame rate, bitrate, dropped frames, network throughput, buffer health.
2. **Skip Intro and Credits** from Media Segments (`GET /MediaSegments/{itemId}`), all segment types.
   A skip button appears at the segment's start and stays for the whole segment, with the controls
   hidden too (`guardrails.md`). Never auto-skip in this phase; auto-skip is a Phase 7 setting.
3. **Trickplay.** Scrub-preview thumbnails from the item's `Trickplay` map and
   `/Videos/{id}/Trickplay/{width}/{n}.jpg` tiles. The preview sits above the thumb with the
   timestamp; with no trickplay, the timestamp alone (`guardrails.md`).
4. **HDR and Dolby Vision passthrough** where the platform supports it. Android: back to a
   `SurfaceView`, which means redoing the exit transition that plan 03 built around `TextureView`.
   iOS: AVPlayer passes HDR through already; the work is to not defeat it and to confirm it.
5. **"Are you still watching?"** After **N** episodes auto-advanced in a row with no touch on the
   player, the next countdown becomes a prompt and playback pauses. Any touch resets the count.
   **N defaults to 3 and is configurable** per profile (a Settings row with Off, 2 to 5). The count lives
   in `PlaybackViewModel` and is never stored.
6. **Playback speed.** Media3 `setPlaybackSpeed`, AVPlayer `rate`. Chosen from the player; not
   remembered between plays *(my call, the roadmap does not say)*. Progress reports are unchanged.
7. **Picture-in-picture**, on both platforms (see "Picture-in-picture and Stopped").
8. **HDR and format badge** on the detail page and in the player, from `MediaStreams`
   (`VideoRangeType`, codec, resolution). One badge, as `guardrails.md` allows.

## Non-goals

- Auto-advance, Next Up, Continue Watching, resume, crash recovery: Phase 2.
- Auto-skip of segments, a seek-step setting, gestures: Phase 7.
- Cast and AirPlay routing UI. Background audio.
- An Android in-app mini player. **Decided: Android stops at PiP plus the `MediaSession` notification**;
  `android.md` stays as it is.
- The iOS tab bar itself (see the mini player below).
- Verifying real HDR output. Neither simulator outputs real HDR.

## Anti-goals

- A player that loses its place: PiP, rotation or leaving must never lose the position or leave the
  server showing a stale session.
- A Phase 3 that makes the Phase 2 rebase painful: no rewrites of code Phase 2 owns.
- Hand-copied strings or hex values (CLAUDE.md): all text in `Strings`, all colour from tokens.

## Constraints

- All CLAUDE.md rules apply: logic and strings in `shared/`, native screens draw state, one
  `ConnectionManager`, 401 only through `expireSession`, no new dependency without asking.
- **Dependency to ask about before adding:** `androidx.media3:media3-session` for the notification
  (not in the stack list, though Media3 is already named in plan 03).
- Work in phases, one reviewed at a time, as in the working agreement; `:shared:allTests` and
  `:androidApp:assembleDebug` pass before a phase is done; iOS is built with Xcode and run on the
  simulator, and anything not run says so.
- Commits: `task|fix|feat|chore: <message>`, one line.

## The Phase 2 seam *(only Next Up is stubbed)*

"Are you still watching?" needs to know what plays next and whether it advances on its own. That is
Phase 2's auto-advance. This phase adds a small shared interface for exactly that, for example
`NextUpSource` (what follows this item, if anything, and whether auto-advance is on), with a **fake
implementation registered in Koin** that reports "nothing next". The binge counter is built and unit
tested against a test double that does advance. On rebase the fake is replaced by Phase 2's real one
and nothing else changes. The binge behaviour is marked **unverified end to end** until then.

Nothing else is stubbed. `Stopped` handling is in scope (below).

## Picture-in-picture and Stopped

Today `onBackgrounded` ends the session (`reporter.stop`). Entering PiP is not backgrounding: the
beacon keeps running, and `Stopped` goes out only when the PiP window is closed or playback ends.

- Android: `supportsPictureInPicture`, auto-enter on 12+ (`setAutoEnterEnabled`), the `ON_PAUSE`
  handler skips pause and `onBackgrounded` while in PiP, and `onPictureInPictureModeChanged` decides
  between "expanded again" and "closed". A `MediaSession` notification with play and pause.
- iOS: `AVPictureInPictureController` on the player layer, the audio background mode in `Info.plist`,
  the full-screen cover must not tear the player down while PiP is active, and tapping the PiP window
  restores the full player.
- Shared: a way to tell the ViewModel "now in PiP" versus "backgrounded" versus "closed", so the
  reporter logic stays in one place. This touches `PlaybackViewModel` and the call sites of
  `reporter.stop`, **in their own commit(s)**, because Phase 2's crash-recovery record hooks the same
  place.

## The iOS mini player: prepared now, tested later

`docs/design/ios.md` puts the iOS 26 mini player in `.tabViewBottomAccessory`. The iOS app has **no
`TabView` yet** (Home, Library and Downloads tabs are unbuilt), so it can't be shown or fully tested
in this phase. **Decided: prepare it now, test it fully when the tab bar exists.** Prepared means:

- Shared: `PlaybackViewModel` state (or a small shared "now playing" holder) that a mini player can
  read: item, title, position, playing or paused, and an intent to restore the full player. Unit
  tested in `shared/`.
- iOS: a mini player view (title, play and pause, tap to restore) built against that state, shown in
  a SwiftUI preview and wired to the PiP restore path, **not mounted** in any tab bar. Below iOS 26 it
  doesn't exist (PiP only), as `ios.md` says.
- A line in the roadmap and in the plan: the mini player is not done until it is mounted in the tab
  bar and checked on the simulator; whoever builds the tab bar owns that. Until then it is reported
  as **built, not verified in place**.

## Per-profile settings and the schema

New per-profile settings (stats overlay on or off, "still watching" count) are new columns on
`AccountSettingsEntity` (`account_settings`), which the code comments already say is the pattern. That
is schema version 3 with `AutoMigration(2, 3)` and a new `3.json`. Phase 2's pending-stop table also
wants version 3. **Decided: I write mine as the next migration on this branch, and renumber it on top
of theirs on rebase, regenerating the schema file.** `SettingsViewModel` derives `hasProfile` from
`allowTranscoding != null`; with more settings that derivation changes.

## Data the player needs (found in the code)

`MediaStreamDto` lacks `Width`, `Height`, `VideoRangeType`, `VideoRange`, `BitRate`,
`RealFrameRate`, `BitDepth`; `BaseItemDto` lacks `MediaStreams`, `MediaSources` and `Trickplay`;
the detail fetch field list is `Overview,Genres` (`LibraryApi.kt`). There is no segments or trickplay
API yet. New calls follow the `PlaybackApi.kt` extension-function pattern. Each new
`PlaybackViewModel` entry point gets a one-line delegate in the iOS `PlaybackModel` facade.

## Test setup

The local Jellyfin 12.2.0 under podman is **undocumented in the repo**. **Decided: extend it with
generated test files**, and check the setup in as a script and a short doc so it is repeatable:
files with chapter markers and media segments (via the Chapter Segments Provider where it applies),
trickplay enabled and generated for the library, and an HDR10 sample (for the badge and surface
choice, not for output). Whether Intro Skipper itself is needed to get segment types beyond chapters
is left to the builder.

## What is left to the builder

- Where each item sits in the player UI within `android.md` and `ios.md`: the stats overlay's entry
  point (a long press or a chip), the speed picker, the skip button's shape.
- Exact `PlaybackDiagnostics` fields and units.
- How the shared "PiP, backgrounded, closed" signal is named and shaped.
- Phase breakdown and order of the plan, within the order above.
- Whether trickplay tiles are cached, and how.

## Settled after the builder check

- **Skip button rule already exists** (`docs/design/guardrails.md`): "Skip intro" and "Skip recap"
  appear at the segment start and stay for the whole segment, also with the controls hidden, and are
  never auto-skipped unless the user turned that on. No design-doc change needed.
- **What counts as input for the "still watching" reset** *(my call)*: any touch, scrub or button on the
  player screen, plus play, pause and seek from the PiP window, the media notification and hardware
  media keys. Native screens forward one `onUserInteraction()`; iOS gets a one-line delegate in
  `PlaybackModel`.
- **iOS PiP and the audio background mode** *(my call)*: the `audio` background mode is required for
  PiP and is not a promise of background audio. Backgrounding the app without PiP still pauses and
  stops the session as today. Minimum iOS stays 16; PiP on the player layer works there.
- **Stats missing on one platform** *(my call)*: `PlaybackDiagnostics` fields are nullable; a field a
  platform can't supply is shown as "–". Parity is not required per field, and the plan lists which
  fields each platform fills.

## Decided by the owner after the builder check

- **Android surface per item:** HDR items use `SurfaceView` with a simpler exit (a plain fade, or none);
  everything else keeps `TextureView` and plan 03's transition.
- **"Still watching" ownership:** this branch owns the prompt and the count and only consumes Phase 2's
  "advanced automatically" signal. `NextUpSource` is: the next item if any, an "auto-advance is on"
  flag, and that signal. Phase 2's countdown and pause logic stay theirs. **To confirm with the
  colleague** that their countdown does not live in the same `PlaybackViewModel` state.
- **`androidx.media3:media3-session`** may be added for the Android notification (pinned to the
  latest stable, in `gradle/libs.versions.toml`, alongside the existing Media3 1.11.1).

## My calls for the owner to check

- Playback speed is not remembered between plays.
- "Still watching" is Off, 2, 3, 4 or 5 in Settings; Off is allowed.
- The stats overlay is a profile setting, not a device one.
- The mini player's shared state lives on the playback ViewModel or a small holder next to it.
- Test media includes an HDR10 sample.
