# VibeyFin: Jellyfin client (Kotlin Multiplatform)

A Netflix-style Jellyfin client with Seerr request integration, visually inspired by Nuvio
(dark, artwork-forward). Phone and tablet first. Android ships first; iOS shares the logic.
The UI is native on each platform (Jetpack Compose, SwiftUI); only the logic is shared.
The app should become very configurable over time, so settings live per profile from day one.

## Targets and modules

- `shared/`: KMP library (`com.android.kotlin.multiplatform.library`). Logic only: data, domain,
  DI and the ViewModels with their UI state. No UI toolkit. Source sets: `commonMain`,
  `androidMain`, `iosMain`, `commonTest`, `androidHostTest`, `androidDeviceTest`.
- `androidApp/`: the native Android app: Jetpack Compose screens, navigation and theme (package
  `com.liamgleeson.vibeyfin.app`), plus Activity, manifest and icons. AGP 9 does not allow the KMP
  plugin and `com.android.application` in one module, which is why this is separate.
- `iosApp/`: the native iOS app in SwiftUI (Xcode project with synchronized folders). It reads
  shared state through the facades in `shared/src/iosMain/.../ios`.

Targets: `androidTarget`, `iosArm64`, `iosSimulatorArm64`.

## Stack

- Kotlin (latest stable 2.4.x).
- UI is native per platform: Jetpack Compose with AndroidX Material 3 on Android, SwiftUI (iOS 16
  and up) on iOS. Shared code has no UI toolkit.
- Navigation: AndroidX Navigation Compose with type-safe `@Serializable` routes on Android;
  `NavigationStack` on iOS.
- ViewModels: JetBrains multiplatform lifecycle-viewmodel, in `shared/`. Android uses them
  directly (`koinViewModel`); iOS reaches each one through a facade in `shared/src/iosMain` that
  takes typed callbacks and owns its own `ViewModelStore`.
- DI: Koin (multiplatform).
- Networking: Ktor client + kotlinx.serialization (OkHttp engine on Android, Darwin on iOS).
- UDP discovery: `ktor-network` sockets.
- Storage: Room KMP with the bundled SQLite driver; DataStore Preferences KMP for device-wide prefs.
- Images: Coil 3 on Android. On iOS, SwiftUI's `AsyncImage` or a loader chosen when images land (ask first).
- Playback: Media3 ExoPlayer on Android (`exoplayer`, `exoplayer-hls`, `ui`), AVPlayer on iOS. The
  player is native and driven by the shared `PlaybackViewModel`; there is no shared player abstraction.
- Tests: `kotlin.test`, `kotlinx-coroutines-test`, Ktor `MockEngine`.

**Versions:** always resolve the latest *stable* release of each dependency yourself before adding
it, and pin it in `gradle/libs.versions.toml`. Don't use versions from memory. Don't use alphas or
betas unless there is no stable release.

## Jellyfin: we talk to the REST API directly

The official Jellyfin Kotlin SDK only supports JVM/Android (its discovery and networking live in
`jvmCommonMain`), so it can't be used from `commonMain`. We write a thin Ktor-based client for the
endpoints we need. Reimplement behaviour from the SDK where useful; don't copy its code (LGPL).

Every request sends:
`Authorization: MediaBrowser Client="VibeyFin", Device="<device name>", DeviceId="<stable id>", Version="<app version>", Token="<token, when signed in>"`

Device ID: generate a UUID once per install and persist it.

## Product decisions (don't change without asking)

- **Sessions:** once a profile has signed in, never ask for its password again until the server
  rejects the token (HTTP 401). Only a 401 or an explicit "Sign out" clears a token. No timers.
  An expired profile keeps its tile in "Who's watching?" and shows as locked.
- **Passwords are never stored.** Only access tokens, encrypted at rest:
  Android Keystore AES-256-GCM (non-exportable key), iOS Keychain. Behind an `expect`/`actual`
  `TokenVault` interface in `commonMain`.
- **Servers are keyed by the server's own ID** (from `/System/Info/Public`), not by URL. One
  server reachable at LAN IP and a domain is one entry with several addresses; the last address
  that worked is tried first.
- **Jellyfin only** for now. Keep the server model open for other backends later, but don't build abstractions for them yet.
- "Always ask who's watching" is a device setting, default off (reopen the last profile).

## Architecture rules

- Layers: `data` (API, DB, vault) → `domain` (repositories, use cases where they earn it) →
  presentation (one shared ViewModel per feature, exposing a `StateFlow` of UI state) → native
  screens. Screens only draw state and forward events; behaviour lives in the ViewModel. UI never
  sees DTOs or Room entities.
- All user-facing text lives in `shared/`: titles, labels, buttons, help text, empty states and
  accessibility descriptions in `Strings` (`ui/common/Strings.kt`), errors in `LoadMessages`,
  `SignInMessages` and `ConnectionIssueMessage`. Native screens hold no string literals; they read
  `Strings` (`Strings.shared` in Swift). Add the text there first, then use it from both apps.
- One `ConnectionManager` owns the current server, profile and authenticated HTTP client as a
  `StateFlow`. Features read from it; nothing else holds tokens.
- A 401 from any authenticated call goes through one place, `ConnectionManager.expireSession`, which
  calls `expire()` and routes to "Who's watching?". Don't call `AccountRepository.expire` from
  anywhere else, except for an explicit sign-out. `ConnectionManager.asProfile` runs work as a saved
  profile that isn't signed in (a pending `Stopped`) and locks that profile on a 401 the same way,
  without moving the signed-in state.
- Platform code only behind `expect`/`actual` or interfaces injected by Koin: token vault,
  Wi-Fi multicast lock (Android), device name.
- Time: inject a clock (`kotlin.time.Clock`) so repositories are testable.

## Design language

The design system lives in `docs/design/` (start with `brand.md`) and `design/tokens.json`. It is
dark only, artwork-forward, and native on each platform: Apple's Human Interface Guidelines with
Liquid Glass on iOS 26+ (`ios.md`), Material 3 on Android (`android.md`). Behaviour rules that both
apps share are in `guardrails.md`; which Jellyfin field drives which UI is in `jellyfin-mapping.md`.

- One brand across both: electric aqua `#3fe0d0` fills the single primary action per screen (Play,
  Sign in) and playback progress, and nothing else. Aqua never signals status; downloaded uses `ink`
  with the filled icon. No teal, no amber, no dynamic colour, no light theme.
- Colours: iOS uses Apple's dark semantic colours (not hex) plus the brand values; Android builds an
  explicit M3 dark `ColorScheme` from the `android` column of `tokens.json`. Text over artwork sits
  on `scrim`.
- Type: system fonts (SF Pro Dynamic Type styles on iOS, the M3 type scale on Android). Only
  `brand-display` (Bricolage Grotesque) is bundled, for the wordmark, the hero title without a Logo
  image and onboarding.
- Use the platform's own component when it has one (buttons, sheets, menus, tab bar, text fields);
  build custom only for posters, rows, the hero and the player. Never fake Liquid Glass.
- Copy: `Strings` is written in sentence case, plain verbs; errors say what happened and how to fix
  it. iOS shows button, tab and title labels in title case through one helper, `titleCased()`;
  Android uses the text as is.
- Token values are hand-written in `app/theme` (Android) and `Theme.swift` (iOS). A drift check
  against `design/tokens.json` fails when they differ. Never hand-copy a hex value into a screen.
- Netflix patterns stay: "Who's watching?" profile grid, hero billboard, rows of posters.

## Working agreement

- Work in the phases of the current plan in `docs/plans/`. Finish one phase, make sure it builds
  and its tests pass, summarise what changed, then stop for review before the next phase.
- Run `./gradlew :shared:allTests` (or the equivalent) and `./gradlew :androidApp:assembleDebug`
  before calling a phase done.
- iOS can only be built with Xcode. Say plainly when iOS code was only compiled to klibs or
  parsed, not built or run.
- Small commits. Don't commit secrets.
- Commit message format, always: `task|fix|feat|chore: <short message>`. One line, no body. Use `feat`
  for new behaviour, `fix` for a bug fix, `chore` for upkeep (build, dependencies, config), `task`
  for everything else (refactors, docs).
- Ask before adding a dependency that isn't in the stack above.

## Reference material

`reference/reel-android/` (if present) is an earlier Android-only prototype. Use it for UI ideas,
the colour system and the step-1 storage design (Room entities, session rules, tests). Port into
KMP; don't copy Android-only APIs into `commonMain`.
