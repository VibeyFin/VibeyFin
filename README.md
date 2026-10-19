# VibeyFin

A Netflix-style [Jellyfin](https://jellyfin.org) client for phones and tablets, built with Kotlin
Multiplatform. Dark and artwork-forward, visually inspired by Nuvio, with
[Seerr](https://docs.seerr.dev) requests and recommendations planned for later.

The logic is shared between Android and iOS; the UI is native on each (Jetpack Compose on Android,
SwiftUI on iOS). Android ships first.

## Status

The roadmap's Phase 0 is done: you can connect to a server, sign in, list its libraries and read an
item's metadata. **There is no playback yet**, and no Seerr; see the [roadmap](docs/ROADMAP.md).

What works today:

- **Servers:** add a server by address, or find one on your network (UDP discovery). A server is
  saved once however many addresses reach it, and the one that last worked is tried first. Servers
  below Jellyfin 10.10 are rejected.
- **Who's watching?** A profile grid per server. Sign in with a password or Quick Connect; a locked
  tile means its session ended and it needs signing in again.
- **Sessions:** sign in once. A profile keeps its session until the server rejects its token or you
  sign out, and the app reopens straight into the last profile unless you turn on "Always ask who's
  watching". Passwords are never stored; tokens are encrypted at rest (Android Keystore, iOS
  Keychain).
- **Libraries:** Home lists the profile's libraries. Folders, series and seasons open a paged grid,
  and a movie or episode opens a page with its picture, facts, genres and overview.

Not built yet: playback, Continue Watching, search, downloads, Seerr. Android has been run against a
real server; **the iOS app has only been compiled and type-checked, never built in Xcode**, so expect
fixes on its first build.

## Project layout

| Module | What it is |
|---|---|
| `shared/` | The Kotlin Multiplatform library: API client, storage, repositories, connection and session handling, and the ViewModels with their UI state. Logic only, no UI toolkit. |
| `androidApp/` | The Android app: Compose screens, navigation, theme, Activity and manifest. |
| `iosApp/` | The iOS app: SwiftUI screens, reading shared state through the Kotlin facades in `shared/src/iosMain`. |
| `docs/` | The [roadmap](docs/ROADMAP.md) and the phase [plans](docs/plans). |

Targets are `androidTarget`, `iosArm64` and `iosSimulatorArm64`. Android needs API 26 or newer
(compile SDK 37, target SDK 36); iOS needs 16 or newer.

## Requirements

- JDK 17 or newer (Android Studio's bundled JDK works).
- Android Studio with the Android SDK, to build and run the Android app.
- Xcode on a Mac, to build and run the iOS app.
- A Jellyfin 10.10+ server. The public demo at `https://demo.jellyfin.org/stable` works for trying
  the app.

## Build and run

Android:

```sh
./gradlew :androidApp:assembleDebug     # build
./gradlew :androidApp:installDebug      # install on the connected device or emulator
```

iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run. Xcode builds the shared framework itself
(`./gradlew :shared:embedAndSignAppleFrameworkForXcode`). To run on a device, set `TEAM_ID` in
`iosApp/Configuration/Config.xcconfig`; the simulator doesn't need it.

## Tests

```sh
./gradlew :shared:testAndroidHostTest          # the shared tests, on the JVM
./gradlew :shared:connectedAndroidDeviceTest   # the platform tests (Keystore vault, discovery, wiring) on a device or emulator
```

The shared tests use real repositories over an in-memory database and a fake Jellyfin network
(Ktor `MockEngine`). The iOS side can't run tests without Xcode; check that the shared code still
compiles for it with:

```sh
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
```

## Built with

Kotlin, Jetpack Compose and Material 3, SwiftUI, Ktor, kotlinx.serialization, Koin, Room (KMP) with
the bundled SQLite driver, DataStore, Coil, and the multiplatform lifecycle ViewModel. Versions are
pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

The official Jellyfin Kotlin SDK is JVM-only, so the app talks to the Jellyfin REST API directly
through a thin Ktor client.

## Where to read more

- [`docs/ROADMAP.md`](docs/ROADMAP.md): what is done and what comes next, including Seerr
  requests and recommendations (Phases 7 and 8).
- [`docs/plans/`](docs/plans): how each stage was planned and what was verified.
- [`CLAUDE.md`](CLAUDE.md): the project's conventions, product decisions and architecture rules.
