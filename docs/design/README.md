# Design reference

Mirror of the "VibeyFin" design system artifact, https://claude.ai/artifact/MPryZWZRno4kWcKtxQZdgA
(last aligned 2026-11-10, artifact version `1794346910-262c`). **The artifact wins**: read it before a UI
change, and when the two differ, change the files here to match it. `design/tokens.json` is the token source
of truth for the code.

- `brand.md`: the four colour roles, the logo, type, the rules the system enforces and the allowed platform drift
- `ios.md`, `android.md`: native UI rules per platform
- `screens.md`, `player.md`: reference layouts for Home (with the category buttons), the detail, series detail and tablet
  detail screens, and Settings (phone and tablet; the design system's `SettingsPhone` and `SettingsTablet`)
- `devices.md`: the device targets (phone, tablet, TV planned), the width classes and how the mockups are named
- `guardrails.md`: screen behaviour both apps must share
- `jellyfin-mapping.md`: which server data drives which UI
- `review.md`: living design review of the built UI against these docs, with each finding's status

## Where the two differ on purpose

- **The artifact's SKIE / KMP-NativeCoroutines notes** are not followed here: iOS reaches the shared
  ViewModels through the facades in `shared/src/iosMain`.
- **`review.md`** is repo-only: a historical snapshot of the built UI against these docs.
- **Mockups** (`components/` in the artifact) are the interactive previews; the repo holds their rules in
  `screens.md`, `player.md` and the platform docs, not the previews.
- **`screens.md`, `player.md` and `jellyfin-mapping.md`** hold repo-only detail beside the artifact's text, so
  only their brand and colour lines follow the artifact.
- **Logos** are in `design/logos/`, copied from the artifact's `assets/Logos/`.
