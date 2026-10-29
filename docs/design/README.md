# Design reference

Copied from the "VibeyFin" design system artifact, https://claude.ai/artifact/HeWVw1EzAbYcvssURJ2rNc
(version `1792497333-649f`, copied 2026-10-20; the Home filter chips were added to both on 2026-10-20, artifact
version `1792521935-239c`). `design/tokens.json` is the token source of truth.

- `brand.md`: principles, shared vs native, voice, colour, type, space, artwork, icons
- `ios.md`, `android.md`: native UI rules per platform
- `screens.md`: reference layouts for Home (with the category buttons), the detail, series detail and tablet
  detail screens, and Settings (phone and tablet; the design system's `SettingsPhone` and `SettingsTablet`)
- `devices.md`: the device targets (phone, tablet, TV planned), the width classes and how the mockups are named
- `guardrails.md`: screen behaviour both apps must share
- `jellyfin-mapping.md`: which server data drives which UI
- `review.md`: living design review of the built UI against these docs, with each finding's status

The artifact's own architecture notes (SKIE, KMP-NativeCoroutines, generated token files) are not
followed here.

## Where this copy differs from the artifact

When re-syncing, keep these; the artifact does not have them yet.

- **Continue watching holds Next up.** One row, not two (`guardrails.md` Home, `jellyfin-mapping.md`
  Home rows). The artifact still lists Next up as its own row and refreshes it separately.
- **BlurHash wash and opt-in looping motion** (`brand.md` principle 5 and the colour rules). The artifact
  allows neither.
- **`screens.md`** is the artifact's `DetailScreen`, `SeriesDetailScreen`, `EpisodeItem` and
  `DetailScreenTablet` component pages, merged into one file. The other component pages are not copied.
- **Settings.** `SettingsPhone` and `SettingsTablet` (summarised in `screens.md`) differ in what was built:
  a choice that is not marked as a page is always a menu (the design system's "up to five options" rule is
  dropped), only Quality is a checklist, inline in its own section with a soft pill and no tick; there is no
  root Buffering row (Buffer ahead is on Skipping and
  autoplay); the Android tablet layout is a plain two-column layout at 600dp, not `ListDetailPaneScaffold`; the
  iOS subtitle style rows show disabled while subtitles are burned in.
