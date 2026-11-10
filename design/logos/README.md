# Logos

The Fin Play logo as SVG, for everything that can't use the `Logo` component: app icons, splash and launch screens, store listings, the repo README, the website, email.

| File | Use |
|---|---|
| `logo-mark-dark.svg` / `logo-mark-light.svg` | The mark with its progress bar. 32 px and up. |
| `logo-mark-small-dark.svg` / `logo-mark-small-light.svg` | The mark alone, no bar. Under 32 px: favicon, notification icon, unread dot. |
| `logo-lockup-dark.svg` / `logo-lockup-light.svg` | Mark and wordmark side by side. Headers, the web, the README. |
| `logo-stacked-dark.svg` / `logo-stacked-light.svg` | Mark above the wordmark. Splash, Connect, store art. |

- **Tones:** `dark` is for dark backgrounds (white mark, `#e07a45` bar, `#3a3a3c` track). `light` is for light backgrounds (black mark, deep copper `#c8622e`, `#d1d1d6` track).
- **Fixed colours:** the files carry their own colours, the same on both platforms. They are not themed.
- **Wordmark:** Archivo at width 75%, weight 800, tracking -0.01em, converted to outlines, so no font needs to be installed. Written "VibeyFin". The bar is drawn at 62%; the `Logo` component's `progress` prop is the only place it moves.
- **Source of truth:** the `Logo` component draws the same mark path (`M27 17C50 19 69 31 81 48L27 69C34 54 34 32 27 17Z`, stroke 8) and bar (62 x 8 at 23, 81). Change the mark in both places or in neither.
- Clear space, minimum sizes and the other rules are in `brand.md` and the Logo component's README.
