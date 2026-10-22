# VibeyFin

A Netflix-style [Jellyfin](https://jellyfin.org) client for phones and tablets, built with Kotlin
Multiplatform. Dark and artwork-forward, visually inspired by Nuvio, with
[Seerr](https://docs.seerr.dev) requests and recommendations planned for later.

The logic is shared between Android and iOS; the UI is native on each (Jetpack Compose on Android,
SwiftUI on iOS). Android ships first.

## Status

The roadmap's Phases 0 to 3 are done: you can connect to a server, sign in, browse libraries, play
movies and episodes, and pick up where you left off on any device. Phase 4 (the navigation bar, richer
Home, search) is next; there is no Seerr yet. See the [roadmap](docs/ROADMAP.md).

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
  and a movie or episode opens a page with its picture, facts, genres, overview and a format badge
  (HDR, codec, resolution). BlurHash placeholders hold the space while artwork loads.
- **Playback:** direct play with a transcode fallback, audio and subtitle choice, resume from the
  server's position, auto-advance into the next episode, and a progress report to the server (also
  sent after a crash). A lost connection is retried and then explained.
- **Continue Watching:** one row for what's in progress and what's next, plus mark played or unplayed.
- **Player extras:** skip intro and credits from the server's media segments, scrub previews
  (trickplay), playback speed, HDR output, picture-in-picture (Android; iOS written but never seen
  running), an optional stats overlay and "Are you still watching?" after a few episodes in a row.
  Both are per-profile settings.

Not built yet: the navigation bar and search, downloads, Seerr. Android has been run on an emulator
against a local test server (see [`docs/test-server.md`](docs/test-server.md)); no real device pass for
HDR yet. **The iOS app has been built and driven on the simulator for Home, playback speed, the format
badge and the still-watching prompt, but not for picture-in-picture, the stats overlay, skip or scrub
previews**, so expect fixes when those are first seen running. The iOS mini
player is built but not mounted, because the app has no tab bar yet.

## Project layout

| Module | What it is |
|---|---|
| `shared/` | The Kotlin Multiplatform library: API client, storage, repositories, connection and session handling, and the ViewModels with their UI state. Logic only, no UI toolkit. |
| `androidApp/` | The Android app: Compose screens, navigation, theme, Activity and manifest. |
| `iosApp/` | The iOS app: SwiftUI screens, reading shared state through the Kotlin facades in `shared/src/iosMain`. |
| `docs/` | The [roadmap](docs/ROADMAP.md), the phase [plans](docs/plans), the [design system](docs/design/brand.md) and the [test server](docs/test-server.md). |
| `scripts/` | Design drift and title-case checks, and a disposable local Jellyfin for testing (`scripts/test-server/`). |

Targets are `androidTarget`, `iosArm64` and `iosSimulatorArm64`. The Android application ID and iOS bundle ID are `app.vibeyfin`. Android needs API 26 or newer
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
(`./gradlew :shared:embedAndSignAppleFrameworkForXcode`). To run on a device, put `TEAM_ID=<your team>` in
`iosApp/Configuration/Local.xcconfig` (git-ignored); the simulator doesn't need it.

## Tests

```sh
./gradlew :shared:testAndroidHostTest          # the shared tests, on the JVM (what CI runs)
./gradlew :shared:connectedAndroidDeviceTest   # the platform tests (Keystore vault, discovery, wiring) on a device or emulator
```

The shared tests use real repositories over an in-memory database and a fake Jellyfin network
(Ktor `MockEngine`). The iOS side can't run tests without Xcode; check that the shared code still
compiles for it with:

```sh
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
```

## CI and releases

- **Pull requests** run `.github/workflows/pr.yml`: the shared and Android unit tests and a debug build,
  on Ubuntu. (`allTests` also needs the iOS targets, which only run on macOS.)
- **Releases** are made by pushing a tag: `git tag v0.2.0 && git push origin v0.2.0`.
  `.github/workflows/release.yml` runs the tests, builds a signed release APK and publishes it as a
  GitHub release. A tag with a suffix (`v0.2.0-beta.1`) is a pre-release. The version name comes from
  the tag and the version code from the run number.
- Signing needs four repository secrets: `RELEASE_KEYSTORE_BASE64` (the keystore, base64-encoded),
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. Without the keystore
  the release build is unsigned, so local `assembleRelease` output won't install. Keep the keystore and
  its passwords backed up: losing them means updates can't install over existing installs.
- A release APK and a debug build share one package name but are signed with different keys, so
  installing one over the other fails until the first is uninstalled.

## Built with

Kotlin, Jetpack Compose and Material 3, SwiftUI, Media3 ExoPlayer and AVPlayer, Ktor, kotlinx.serialization, Koin, Room (KMP) with
the bundled SQLite driver, DataStore, Coil, and the multiplatform lifecycle ViewModel. Versions are
pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

The official Jellyfin Kotlin SDK is JVM-only, so the app talks to the Jellyfin REST API directly
through a thin Ktor client.

## Where to read more

- [`docs/ROADMAP.md`](docs/ROADMAP.md): what is done and what comes next, including Seerr
  requests and recommendations (Phases 8 and 9).
- [`docs/plans/`](docs/plans): how each stage was planned and what was verified.
- [`docs/design/`](docs/design/brand.md): the design system, per platform.
- [`CLAUDE.md`](CLAUDE.md): the project's conventions, product decisions and architecture rules.
