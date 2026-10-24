# Player

The player on both platforms. Behaviour rules are in `guardrails.md` (Playback); this file is the layout and look. The interactive mockups (controls, scrubbing, skip offer, up next, Settings → Playback) are the `PlayerControls`, `PlayerScreen` and `PlaybackSettings` components of the VibeyFin design-system artifact: https://claude.ai/artifact/HeWVw1EzAbYcvssURJ2rNc

## iOS (SwiftUI, AVPlayer)

Apple's scheme: **white on Liquid Glass, never aqua.** Aqua is only the scrubber's progress. iOS 26 uses `.buttonStyle(.glass)` / `.glassEffect()` in one `GlassEffectContainer`; before iOS 26 the same controls sit on a `scrim` disc. Symbols are `.primary`.

- **Top bar**, one baseline, 44pt cells, top-aligned:
  - Leading: a round glass `xmark`.
  - Title (`.headline`) and one meta line (`.footnote`, secondary): episode, then the format badge as plain text ("S2:E4 · Northbound · 4K · HDR10"). The badge is no longer a capsule.
  - Trailing: **one** glass capsule holding, in order, speed (`1×`, a menu), audio and subtitles (`captions.bubble`, `.fill` when a subtitle is on, a menu), picture in picture, AirPlay (`AVRoutePickerView`). No control floats on its own.
- **Transport**, centered: `gobackward.<n>`, play / pause, `goforward.<n>` as glass discs of 56 / 76 / 56pt at the default text size (they scale with Dynamic Type). `n` comes from Settings → Playback (default 10 back, 10 forward).
- **Bottom:** scrubber with aqua progress, no thumb, thickening to 10pt while dragged; elapsed left, remaining right (`-12:30`), tabular numerals; trickplay thumbnail and timestamp above the bar while dragging. The **buffered segment** is white at about 35% opacity between the aqua fill and the track, ending at the loaded position (`loadedTimeRanges`).
- **Stalled:** while the player waits for data a white `ProgressView` replaces the three transport buttons, centred; the top bar and scrubber stay.
- **Skip offer** ("Skip Intro", "Skip Recap", "Skip Credits"): a white glass capsule with a `forward.end` symbol, bottom-right above the scrubber. Shown for the whole segment, also with the controls hidden (then it is the only thing on screen, over a faint bottom scrim).
- **Up next:** takes the skip offer's corner. A glass card (`radius-ios-card`): the next episode's thumbnail with a seconds-left badge, "Up Next", the title, a **white filled** "Play Now" capsule and a glass "Watch Credits".

## Android (Compose, Media3)

M3 on the scrim, no glass. Top app bar: `←`, title and meta line, speed, audio and subtitles. Transport: three clickable 48 dp buttons, the two skip buttons a generic arrow glyph with the profile's seconds drawn as text over it (not Material `replay_10` `IconButton`s, which exist for a few values only), and play / pause on a `scrim-strong` disc, all `ink` on the scrim. Scrubber: M3 look with a bar handle and a buffered segment (white at about 35% opacity, up to `player.bufferedPosition`) between the aqua fill and the track. While stalled, a white `CircularProgressIndicator` replaces the three transport buttons. Skip offer: a tonal button (`accent-soft`). Up next: a `surface-2` card with a filled "Play now" and a tonal "Watch credits".

## Settings → Playback (per profile)

| Setting | Choices | Default |
| --- | --- | --- |
| Skip back | 5, 10, 15, 30 s | 10 s |
| Skip forward | 10, 15, 30, 45, 60 s | 10 s |
| Skip intros and recaps | on / off | off |
| Buffer ahead | Auto, 10, 20, 30, 40, 50, 60 s | 20 s |

Choices are the values SF Symbols has a `gobackward.<n>` / `goforward.<n>` glyph for, so button and setting agree. The values also drive the double-tap seek and the VoiceOver / TalkBack labels ("Back 10 seconds"). iOS: a `Picker` with `.pickerStyle(.menu)` per interval in the settings list, which is a custom stacked list in a scroll view rather than an inset grouped `Form`; Android: the settings `ChoiceRow` (a menu with the value at the right), like the other choice settings. Buffer ahead sits in its own Buffering group after the skip rows, with `Strings.bufferAheadHelp` as its help text: how much video the player loads ahead, a longer buffer riding out a slow connection at the cost of data and memory, Auto leaving it to the device's player. Auto is the platform player's own behaviour; a change applies from the next video.

## What changes in the code (for the plan)

- Shared: `ProfileSettings` (per profile) with skip back / forward and auto-skip, exposed on `PlaybackUiState`; `Strings` for the new labels.
- iOS: `PlayerView` top bar regrouped into the glass cluster, controls recoloured to `.primary`, transport gains the two skip buttons, skip offer and up next restyled.
- Android: transport gains the two skip buttons; top bar order as above.
- Both: segment skip already exists in the shared model; confirm it shows on the test server (an intro and outro segment fixture in `make-media.sh` if missing).
