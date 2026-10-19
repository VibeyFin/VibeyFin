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
  anywhere else, except for an explicit sign-out.
- Platform code only behind `expect`/`actual` or interfaces injected by Koin: token vault,
  Wi-Fi multicast lock (Android), device name.
- Time: inject a clock (`kotlin.time.Clock`) so repositories are testable.

## Design language

Dark, blue-ink background; amber is reserved for the single primary action (Play, Sign in);
teal only for availability. Artwork carries the colour everywhere else. Heavy, tight type for
titles. Netflix patterns: "Who's watching?" profile grid, hero billboard, rows of posters.

Native, not identical: the rules above hold on both platforms, implemented natively (`app/theme`
in `androidApp`, `Theme.swift` in `iosApp`); each platform otherwise follows its own idioms.

Palette: Ink `#10121A`, Surface `#1A1D29`, SurfaceHigh `#262A3A`, Text `#ECEAE4`,
TextMuted `#8E92A3`, Marquee (amber) `#E8B04B`, OnMarquee `#1B1405`, Available (teal) `#5CC8B0`.

Copy: sentence case, plain verbs, errors say what happened and how to fix it.

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
