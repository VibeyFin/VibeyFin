# Plan 20: iOS player controls, AirPlay hold-open and flicker

> **Status:** Phase 1 done (checked on an iPhone and an iPad; the smoke test has one unrelated failure, `testZZDownloadFromTileMenu`).

**Goal:** On iOS and iPadOS the player's controls stay up while the AirPlay picker is open (then hide after the usual 3 s), and
they no longer flicker while shown.

**Related:** issue #35, plan 14 (player refinement, where the hold-open logic for the menus was built).
No mockup: nothing the user sees changes except the two bugs going away.

## Decisions

- **AirPlay reports through the existing path.** `AirPlayButton` gets `onOpenChange` and `onTouch`, like the speed and track menus.
  A Coordinator is the `AVRoutePickerViewDelegate`: `routePickerViewWillBeginPresentingRoutes` calls `onOpenChange(true)`,
  `routePickerViewDidEndPresentingRoutes` calls `onOpenChange(false)`. `PlayerView` passes `menuOpenChanged` and the same touch
  closure the menus use. `MenuOpenProbe` is not involved: the picker is not a context menu.
- **Safety net.** If `didEnd` never arrives, a new touch on the video or a scene-phase change clears `menuOpen`, so the controls
  can't stay stuck open.
- **Confirm the flicker before fixing it.** A temporary `Self._printChanges()` in `PlayerView` and the controls, run on the
  simulator against the test server, shows whether `controller.position` or `model.$state` drives the re-render and how often.
  It is removed before commit. The fixes below apply only to what it confirms.
- **If the tick is a cause:** the ticking position moves out of `PlayerController`'s main `@Published` surface into a small
  `PlayerClock` `ObservableObject`. Only the scrubber and the two time labels observe it; the rest of the controls, the AirPlay
  button and the probe no longer depend on it. `currentMilliseconds` stays as it is.
- **If `model.$state` is a cause:** `PlaybackObservable` skips an emission equal to the last one, or `onReceive` reacts only to the
  fields it uses. iOS layer only; the shared `PlaybackViewModel` is not touched.
- **`MenuOpenProbe` stays unchanged** unless the measurement shows `AirPlayButton` or the probe being recreated on those passes.
  Then the probe restarts only when its view enters or leaves a window.
- **Tests.** The AirPlay picker is system UI and can't be driven in the simulator, so it gets no UITest assertion; the summary says
  so. A player-controls step goes into `OnboardingFlowUITests` only if the player is reachable from the existing test-server
  fixtures (case-insensitive labels). The hold-open logic gets a unit test only if it can be pulled out cleanly.

## Not in this plan

Changing the 3 s delay, restyling the controls, the Android player, an AirPlay route list of our own.

## Phase 1: fix both

1. **Measure.** Add `_printChanges()`, play an item from the test server, note what re-renders and how often. Report the finding
   before fixing.
2. **AirPlay.** Delegate and Coordinator in `AirPlayButton.swift`; wire `onOpenChange` and `onTouch` in `PlayerView.swift`;
   add the safety net.
3. **Flicker.** Apply only the fixes from step 1 (`PlayerClock`, state de-duplication, probe).
4. **Remove** the `_printChanges()` calls.

## Verification

- `./gradlew :shared:allTests` and `./gradlew :androidApp:assembleDebug` (shared code is untouched, so these should be unchanged).
- `scripts/ios-smoke.sh` with the shared `iosAppUITests` scheme, when Xcode, a simulator and podman are available; otherwise say
  which tool was missing.
- **On a real iPhone and iPad (you):** AirPlay needs a real device and a real receiver.
  1. Play a video, tap it to show the controls, tap the AirPlay button and leave the picker open for 10 s: the controls stay.
  2. Close the picker without choosing: the controls hide about 3 s later.
  3. Open it again and pick a receiver: the controls stay until the picker closes, then hide after 3 s.
  4. Leave the controls up for 30 s with no touch: they hide at 3 s as before, and while visible nothing flickers
     (try it while playing, paused, and with the scrubber and the speed menu used).
  5. Repeat the speed and audio/subtitle menus to check they still hold the controls open.
