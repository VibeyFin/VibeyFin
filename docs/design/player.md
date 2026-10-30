# Player

The player on both platforms. Behaviour rules are in `guardrails.md` (Playback); this file is the layout and look. The interactive mockups (controls, scrubbing, skip offer, up next, Settings → Playback) are the `PlayerControls` and `PlayerScreen` components of the VibeyFin design-system artifact (the Settings screens are `SettingsPhone` and `SettingsTablet`, see `screens.md`): https://claude.ai/artifact/HeWVw1EzAbYcvssURJ2rNc

## iOS (SwiftUI, AVPlayer)

Apple's scheme: **white on Liquid Glass, never aqua.** Aqua is only the scrubber's progress. iOS 26 uses `.buttonStyle(.glass)` / `.glassEffect()` in one `GlassEffectContainer`; before iOS 26 the same controls sit on a `scrim` disc. Symbols are `.primary`.

- **Top bar**, one baseline, 44pt cells, top-aligned:
  - Leading: a round glass `xmark`.
  - Title (`.headline`) and one meta line (`.footnote`, secondary): episode, then the format badge as plain text ("S2:E4 · Northbound · 4K · HDR10"). The badge is no longer a capsule.
  - Trailing: **one** glass capsule holding, in order, speed (`1×`, a menu), audio and subtitles (`captions.bubble`, `.fill` when a subtitle is on, a menu), picture in picture, AirPlay (`AVRoutePickerView`). No control floats on its own.
- **AirPlay active** (`AVPlayer.isExternalPlaybackActive`): the picture is on the other screen, so the phone or iPad keeps the same controls over a darker backdrop and shows a status pill above the transport: the `airplay` glyph and "Playing on <device>", white on glass (a `scrim-strong` disc before iOS 26), never aqua. The AirPlay button shows pressed. **The PiP button is hidden** while AirPlay is active, because PiP does not apply to external playback; it returns when AirPlay stops. The route button opens the system picker to switch device or stop. The receiver's own UI (Apple TV, TV player) is the system's; the app can't style it. Switching audio or subtitles keeps the position (issue #47).
- **Transport**, centered: `gobackward.<n>`, play / pause, `goforward.<n>` as glass discs of 56 / 76 / 56pt at the default text size (they scale with Dynamic Type). `n` comes from Settings → Playback (default 10 back, 10 forward).
- **Bottom:** scrubber with aqua progress, no thumb, thickening to 10pt while dragged; elapsed left, remaining right (`-12:30`), tabular numerals; trickplay thumbnail and timestamp above the bar while dragging. The **buffered segment** is white at about 35% opacity between the aqua fill and the track, ending at the loaded position (`loadedTimeRanges`).
- **Stalled:** while the player waits for data a white `ProgressView` replaces the three transport buttons, centred; the top bar and scrubber stay.
- **Skip total:** after a skip, a "10s" capsule on the `scrim` (`.title3`, semibold, tabular numerals) by the side the video moved, holding the running total of skips in a row (`skipRun`); it fades in and out and is hidden from VoiceOver.
- **Skip offer** ("Skip Intro", "Skip Recap", "Skip Credits"): a white glass capsule with a `forward.end` symbol, bottom-right above the scrubber. Shown for the whole segment, also with the controls hidden (then it is the only thing on screen, over a faint bottom scrim).
- **Next episode:** for an episode with a next one only (never a movie or the last episode), a glass `forward.end.fill` button (`PlayerIconButton`) at the end of the scrubber's row, right of the bar and the times. It plays the next episode now, like Play Now on the up next card, gives way to that card while it shows, and fades out while scrubbing. VoiceOver: "Next episode", the episode's label and title as the hint. Mockup: `PlayerNextEpisode`.
- **Swipe for volume and brightness** (issue #40, as on Android): a vertical swipe on the right half sets the volume, on the left half the brightness; up raises, a full sweep is 60% of the height, drags starting within 40 pt of the top or bottom or 24 pt of the sides (Notification Centre, Control Centre, the home indicator) or going sideways first are ignored. iOS has no call that sets the volume, so the swipe moves a hidden `MPVolumeView`'s slider; that view being on screen also hides the system volume HUD, so the hardware buttons show the same capsule while the player is up. iOS has no per-window brightness: the screen's brightness from before the first swipe comes back when the player closes or the app leaves the foreground. The indicator is a Liquid Glass capsule (52 × 160 pt, a `scrim` capsule before iOS 26) on the swiped side, filled from the bottom in white like Control Center, with `speaker.slash.fill` / `speaker.wave.1…3.fill` or `sun.min.fill` / `sun.max.fill` at its foot, black once the fill covers it. Never aqua. It fades out 1 s after the finger lifts; VoiceOver hears the level when it does. Off with the same per-profile switch as Android.
- **Up next:** takes the skip offer's corner. A glass card (`radius-ios-card`): the next episode's thumbnail with a seconds-left badge, "Up Next", the title, a **white filled** "Play Now" capsule and a glass "Watch Credits".

## Android (Compose, Media3)

M3 on the scrim, no glass. Top app bar: `←`, title and meta line, speed, audio and subtitles. Transport: three clickable 48 dp buttons, the two skip buttons a generic arrow glyph with the profile's seconds drawn as text over it (not Material `replay_10` `IconButton`s, which exist for a few values only), and play / pause on a `scrim-strong` disc, all `ink` on the scrim. A press turns a skip arrow about 30° the way it points and springs it back. After any skip, the side the video moved shows Android's own double-tap seek feedback (`SeekRipple.kt`): a light half circle (`ink` at 12%) curving in from that edge over about 38% of the width, a ripple for each tap, three chevrons lighting up in turn in the skip direction and the running total ("30s") under them. It takes no touches and is hidden from TalkBack. Scrubber: M3 look with a bar handle and a buffered segment (white at about 35% opacity, up to `player.bufferedPosition`) between the aqua fill and the track. While stalled, a white `CircularProgressIndicator` replaces the three transport buttons. Skip offer: a tonal button (`accent-soft`). Up next: a `surface-2` card with a filled "Play now" and a tonal "Watch credits".

**Next episode:** the same rules as on iOS, as the player's round chip (`ChipButton`: 48 dp touch, 40 dp `scrim` disc, Material `skip_next` in `ink`) at the end of the scrubber's row. TalkBack: "Next episode, S1 E2 · Northbound". The player looks the next episode up whether or not autoplay is on.

**Swipe for volume and brightness (Android, issue #40):** a vertical swipe on the right half sets the media volume (`STREAM_MUSIC`, without the system panel), on the left half this window's brightness (`screenBrightness`, never the system setting; back to `BRIGHTNESS_OVERRIDE_NONE` when the player closes). Up raises; a full sweep is 60% of the player's height. Swipes starting in the system gesture insets are ignored; taps, double taps and the hardware keys work as before, and a swipe neither shows nor hides the controls. While swiping, a vertical pill (44 × 152 dp, `scrim-strong` with the `progress-track`, an `ink` fill from the bottom, never aqua) sits `space-6` in from the swiped edge with `volume_off` / `volume_down` / `volume_up` or `brightness_low` / `brightness_high` at its foot (`bg` once the fill covers it); it fades out 1 s after the finger lifts and TalkBack hears "Volume 60%". Off with the profile's "Swipe for volume and brightness" switch (on by default). Mockup: `PlayerGestures` in the design-system artifact.

**Cast (Android, planned, issue #48):** the top app bar's Cast button shows only when a device is found, and shows pressed while connected. It opens an M3 dialog on `surface-2`: "Cast to", the devices (cast glyph, name, type and state, a check on the connected one) and a "Stop casting" tonal button while connected. While casting, the player shows the same status pill as on iOS, "Casting to <device>", over the transport and scrubber on a darker backdrop. The mockups are `PlayerControls`, `PlayerPhone` and `PlayerTablet` in the design-system artifact.

## Settings → Playback (per profile)

| Setting | Choices | Default |
| --- | --- | --- |
| Skip back | 5, 10, 15, 30 s | 10 s |
| Skip forward | 10, 15, 30, 45, 60 s | 10 s |
| Skip intros and recaps | on / off | off |
| Buffer ahead | Auto, 10, 20, 30, 40, 50, 60 s | 20 s |
| Still watching after | Off, 2 to 5 episodes | 3 |
| Quality | Maximum, 4K 40, 1080p 10, 720p 4, 480p 1.5 Mbps | Maximum |
| Swipe for volume and brightness | on / off | on |
| Subtitle style | system style, or size, text colour, background | system style |

Skip back and forward, auto skip, still watching and Buffer ahead are on the **Skipping and autoplay** page; Quality and Audio and subtitles (languages, then subtitle style) are their own pages under Playback. Quality caps the bitrate the server may send (`jellyfin-mapping.md`) and applies from the next stream; the player has no quality menu. Subtitle style follows the OS caption style until the user turns that off; Android applies the chosen size, colour and background to the subtitle view, iOS burns subtitles in today so its controls are disabled with a note.

Choices are the values SF Symbols has a `gobackward.<n>` / `goforward.<n>` glyph for, so button and setting agree. The values also drive the double-tap seek and the VoiceOver / TalkBack labels ("Back 10 seconds"). iOS: a `Picker` with `.pickerStyle(.menu)` per interval in the settings list; Android: the settings `ChoiceRow` (a menu with the value at the right), like the other choice settings. Buffer ahead sits in its own group on Skipping and autoplay, after the skip rows, with `Strings.bufferAheadHelp` as its help text: how much video the player loads ahead, a longer buffer riding out a slow connection at the cost of data and memory, Auto leaving it to the device's player. Auto is the platform player's own behaviour; a change applies from the next video.

## What changes in the code (for the plan)

- Shared: `ProfileSettings` (per profile) with skip back / forward and auto-skip, exposed on `PlaybackUiState`; `Strings` for the new labels. `PlaybackUiState.skipRun` (`SkipRun`: side, how far it moved, taps) adds up skips in a row and clears after `SKIP_RUN_WINDOW` (1 s).
- iOS: `PlayerView` top bar regrouped into the glass cluster, controls recoloured to `.primary`, transport gains the two skip buttons, skip offer and up next restyled.
- Android: transport gains the two skip buttons; top bar order as above.
- Both: segment skip already exists in the shared model; confirm it shows on the test server (an intro and outro segment fixture in `make-media.sh` if missing).
