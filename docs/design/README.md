# Design reference

Mirror of the "VibeyFin" design system artifact, https://claude.ai/artifact/L3iSAKqvpmXtUtRy2qBGAe
(last aligned 2026-10-31, artifact version `1793456393-8ee3`). `design/tokens.json` is the token source of
truth for the drift checks.

**Keep the repo and the artifact aligned.** On every change, update both in the same piece of work: the
docs here and the artifact's matching files (`brand.md` is its `README.md`, `screens.md` and `player.md` are
sections of it, the mockups are its `components/`). Read the artifact before starting a UI change, and
update it in place when you are done; say in the summary if it could not be read or updated. A change that
touches only one of the two leaves them drifted.

- `brand.md`: principles, shared vs native, voice, colour, type, space, artwork, icons
- `ios.md`, `android.md`: native UI rules per platform
- `screens.md`, `player.md`: reference layouts for Home (with the category buttons), the detail, series detail and tablet
  detail screens, and Settings (phone and tablet; the design system's `SettingsPhone` and `SettingsTablet`)
- `devices.md`: the device targets (phone, tablet, TV planned), the width classes and how the mockups are named
- `guardrails.md`: screen behaviour both apps must share
- `jellyfin-mapping.md`: which server data drives which UI
- `review.md`: living design review of the built UI against these docs, with each finding's status

## Where the two differ on purpose

- **`brand.md` Architecture** and the artifact's SKIE / KMP-NativeCoroutines notes are not followed here
  (the project hand-writes `Theme.swift` and `app/theme` and checks them against `tokens.json`).
- **`review.md`** is repo-only: a historical snapshot of the built UI against these docs.
- **Mockups** (`components/` in the artifact) are the interactive previews; the repo holds their rules in
  `screens.md`, `player.md` and the platform docs, not the previews.
