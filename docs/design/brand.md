# Brand language

One brand, two native apps. **The brand wins over the platform.** A platform may drift only where the OS requires it (the list at the bottom); everywhere else iOS and Android look and behave the same.

## The four roles

| Role | Token | Means | Use it for | Never |
| --- | --- | --- | --- | --- |
| Action | `action` / `on-action` | "Do this" | The one filled button per screen: Play, Resume, Connect, Request. White, black label. | Copper |
| Spark | `spark` | "This is on / here / how far" | Progress and scrubber, a selected tab / chip / season / destination, a switch that is on, the logo, the New badge | Buttons, links, tappable icons, errors, status, large fills, text on `surface-3` |
| Tonal | `tonal` / `on-tonal` | "Secondary" | Secondary buttons, the Android nav pill, the selected tablet nav item. Neutral. | Any tint |
| Ink | `ink`, `ink-muted` | everything else | Text, links, ghost buttons, action icons (My List, Like, Trailer, Download), section labels | |

Rule of thumb: **if you can tap it to do something, it is white or ink. Copper only tells you a state.**

## Same on both platforms

| Thing | iOS | Android |
| --- | --- | --- |
| Main button | white `action` capsule | white `action` pill |
| Progress | copper on `surface-3` (3pt) | copper on `surface-3` (4dp) |
| Selected chip / season | `spark-soft` / `on-spark-soft` | `spark-soft` / `on-spark-soft` |
| Selected nav item | copper icon and label on glass | copper icon and label on `tonal` |
| Switch on | copper track | copper track |
| Ghost, link, action icons | `ink` | `ink` |
| New badge | `spark-soft` | `spark-soft` |
| Error | `danger` + icon + word | `danger` + icon + word |
| Downloaded | `ink`, filled icon, the word | `ink`, filled icon, the word |
| Section labels | `ink-muted` | `ink-muted` |

Implementation: iOS AccentColor = `ink`; copper is applied with `.tint(Spark)` where listed. Android M3 `primary` = `action`; copper is `tertiary`, used only explicitly. Neither platform may let the system tint decide a colour.

## Name

Always **VibeyFin**. Never "vibeyfin", "Vibeyfin" or "Vibey Fin". The wordmark appears on the splash screen, connect screen, onboarding and About. Tab headers show the page name ("Home") on both platforms, not the wordmark.

## Logo: Fin Play

A rounded, fin-shaped play triangle with a copper progress bar below it. The bar is the app's own progress bar, so the logo can move: it fills on launch, connecting and buffering, and stays static under Reduce Motion.

- Variants: mark, horizontal lockup, stacked lockup, reversed, on light (deep copper #C8622E), mono light, mono dark.
- The mark uses fixed colours (white, #3a3a3c track, #e07a45 fill), never theme tokens, so it is identical on both platforms.
- Sizes: 24 px minimum (favicon and unread dot only), 32 px minimum inside the UI. The track is dropped below 32 px.
- Clear space: at least half the fin height on every side.
- Flat solid colours only: no gradients, transparency, shadows or extra strokes.
- Deep copper is for the logo on light backgrounds, never for text.
- Android: adaptive icon with a monochrome themed-icon layer; the notification icon is white only.

```svg
<path d="M27 17 C 50 19 69 31 81 48 L 27 69 C 34 54 34 32 27 17 Z" fill="#fff" stroke="#fff" stroke-width="8" stroke-linejoin="round"/>
<rect x="23" y="81" width="62" height="8" rx="4" fill="#3a3a3c"/>
<rect x="23" y="81" width="38" height="8" rx="4" fill="#e07a45"/>
```

Use the `Logo` component in the UI, and the SVG files in Assets → Logos (`assets/Logos/`) everywhere else (app icons, store art, the web); don't copy the path or redraw it. See **SplashPhone** and **ConnectPhone**.

## Type

UI text stays native: SF Pro with Dynamic Type on iOS, Roboto and the M3 scale on Android. The brand face is **Archivo Condensed** (variable Archivo at width 75%, weight 800, tracking -0.01em) for the wordmark, hero title fallback, onboarding, the movie-logo stand-in and generated poster titles. Never body text or controls.

## Rules the system enforces

- Text 4.5:1 on its ground. `ink-subtle` and (iOS) `danger` text only on `bg` and `surface-1`.
- Progress bars on a plain surface use `surface-3` (copper 3.8:1 iOS, 4.8:1 Android); bars over artwork use `progress-track` (18% white).
- Every colour in a component comes from a token. Fixed white over artwork is `on-media`; white pills over glass are `overlay-weak`, `overlay`, `overlay-strong`.
- Small controls keep their visual size but reach the platform tap target (44pt iOS, 48dp Android) through an invisible hit area.
- Badge text is 12px or larger. iOS tab labels stay 10pt (the system size).
- `prefers-reduced-motion`: no transitions or looping animation; the logo bar is static.

## Allowed platform drift (OS dos and don'ts only)

| Drift | Why |
| --- | --- |
| iOS Liquid Glass bars and overlay buttons, Android opaque surfaces | Apple's material, M3 has none |
| iOS Title Case, Android sentence case | Platform writing conventions |
| SF Pro vs Roboto, Dynamic Type vs the M3 scale | System fonts |
| Button height 50pt vs 56dp (hero) and 40dp, hit area 44pt vs 48dp | Platform minimums |
| Progress 3pt vs 4dp | Native component sizes |
| iOS chips and seasons are capsules, Android uses 8dp rounded rectangles | M3 chip shape |
| iOS 10pt tab labels | System tab bar |
| Back, share and menu glyph sets | SF Symbols vs Material Symbols |

Anything not in this table is a bug.
