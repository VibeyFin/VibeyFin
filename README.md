# VibeyFin

VibeyFin is a next-generation [Jellyfin](https://jellyfin.org) client with a Netflix-like feel,
built for
speed on every platform: native SwiftUI on iOS and iPadOS, Jetpack Compose with Material 3 on
Android, over a
Kotlin Multiplatform core. Dark and artwork-forward, with [Seerr](https://docs.seerr.dev) requests
and
recommendations planned for later, in native screens. Android ships first.

## Status

The roadmap's Phases 0 to 4, Phase 6 and Phase 10 (offline) are done (Phase 5, a recommendation
backend, is deferred): connect
to a server, sign in, browse, search, play, pick up where you left off on any device, and tune
playback and
profiles in Settings. Phase 6 still owes Now Playing on the lock screen and a baseline profile. Next
is
customisation (Phase 7); Seerr is on the roadmap but not started. See
the [roadmap](docs/ROADMAP.md).

What works today:

- **Servers:** add a server by address, or find one on your network (UDP discovery). A server is
  saved once however many addresses reach it, and the one that last worked is tried first. Servers
  below Jellyfin 10.10 are rejected.
- **Who's watching?** A profile grid per server. Sign in with a password or Quick Connect; a locked
  tile means its session ended and it needs signing in again. A profile can have a 4-digit PIN that
  guards opening it, Settings and switching profile.
- **Sessions:** sign in once. A profile keeps its session until the server rejects its token or you
  sign out, and the app reopens straight into the last profile unless you turn on "Always ask who's
  watching". Passwords are never stored; tokens are encrypted at rest (Android Keystore, iOS
  Keychain).
- **Navigation:** Home, Search, Library and Downloads, each with its own back stack. Android has a
  floating
  bar at the bottom on phones and tablets; iOS has the system tab bar, at the top on iPad.
- **Home:** an optional featured title, category buttons (Shows, Movies, New, Categories), Continue
  watching with Next up, Recently added per library, My List, a row per genre and My media.
- **Browsing:** a Library tab with filters (Movies, Shows, Collections, sort, genre, decade,
  people),
  detail pages for movies and series (cast, seasons and episodes, similar titles, special features,
  trailers, collections), person and collection pages, and a long-press menu on any poster.
- **Search:** one search over movies, shows, episodes, people and collections, with filter buttons
  and a
  per-profile history.
- **Playback:** direct play with a transcode fallback, audio and subtitle choice, resume from the
  server's position, auto-advance into the next episode, and a progress report to the server (also
  sent after a crash). A lost connection is retried and then explained.
- **Player extras:** skip intro and credits from the server's media segments (or skip them
  automatically), scrub previews (trickplay), skip buttons and double-tap seek that add up taps in a
  row,
  playback speed, HDR output, picture-in-picture, an optional stats overlay and "Are you still
  watching?" after a few episodes in a row.
- **Downloads and offline:** download a movie, an episode, a season or a whole show per profile,
  play it
  with no connection, and manage the space in the Downloads tab. When the server can't be reached
  the
  app opens on what is downloaded, or you can switch Offline mode on.
- **Settings (per profile):** quality cap, subtitle style, skip lengths, auto-skip, buffer ahead,
  still
  watching, picture-in-picture, Home hero and genre rows, download rules, Kids profile (shows the
  server's parental
  limits), PIN, and new-title notifications (off by default; WorkManager on Android, background
  refresh
  on iOS). Tablets get a two-pane Settings.

Not built yet: customisation of Home rows and tabs, Seerr, TV apps. Translations are in
progress, see [Translating](#translating). Android has been run on an emulator against a local test server (see
[`docs/test-server.md`](docs/test-server.md)); there has been no real-device pass yet,
so real HDR output is unverified. **The iOS app builds and its unit tests pass on the simulator, but
most screens have not been walked through by hand**,
and picture-in-picture, Control Center and notifications have not been seen running on iOS. Expect
fixes
when they are.

## Project layout

| Module        | What it is                                                                                                                                                               |
|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `shared/`     | The Kotlin Multiplatform library: API client, storage, repositories, connection and session handling, and the ViewModels with their UI state. Logic only, no UI toolkit. |
| `androidApp/` | The Android app: Compose screens, navigation, theme, Activity and manifest.                                                                                              |
| `iosApp/`     | The iOS app: SwiftUI screens, reading shared state through the Kotlin facades in `shared/src/iosMain`.                                                                   |
| `docs/`       | The [roadmap](docs/ROADMAP.md), the [design system](docs/design/brand.md) and the [test server](docs/test-server.md).                                                    |
| `scripts/`    | Design drift and title-case checks, and a disposable local Jellyfin for testing (`scripts/test-server/`).                                                                |

Targets are `androidTarget`, `iosArm64` and `iosSimulatorArm64`. The Android application ID and iOS
bundle ID are `app.vibeyfin`. Android needs API 26 or newer (compile SDK 37, target SDK 36); iOS
needs 16 or newer.

## Requirements

- JDK 17 or newer (Android Studio's bundled JDK works).
- Android Studio with the Android SDK, to build and run the Android app.
- Xcode on a Mac, to build and run the iOS app.
- A Jellyfin 10.10+ server (development is tested against Jellyfin 12). The public demo at `https://demo.jellyfin.org/stable` works for trying
  the app.

## Build and run

Android:

```sh
./gradlew :androidApp:assembleDebug     # build
./gradlew :androidApp:installDebug      # install on the connected device or emulator
```

iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run. Xcode builds the shared framework itself
(`./gradlew :shared:embedAndSignAppleFrameworkForXcode`). To run on a device, put
`TEAM_ID=<your team>` in
`iosApp/Configuration/Local.xcconfig` (git-ignored); the simulator doesn't need it.

## Tests

```sh
./gradlew :shared:testAndroidHostTest          # the shared tests, on the JVM (what CI runs)
./gradlew :shared:connectedAndroidDeviceTest   # the platform tests (Keystore vault, discovery, wiring) on a device or emulator
```

The shared tests use real repositories over an in-memory database and a fake Jellyfin network (Ktor
`MockEngine`). The iOS side can't run tests without Xcode; check that the shared code still
compiles for it with:

```sh
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
```

With Xcode and a simulator, `./gradlew :shared:iosSimulatorArm64Test` runs the shared tests on iOS
and
`xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 18 Pro'`
runs the Swift unit tests in `iosApp/Tests/`. `./gradlew :shared:allTests` runs the shared tests on
every
target, which also needs Xcode.

## CI and releases

- **Pull requests** run `.github/workflows/pr.yml`: the shared and Android unit tests and a debug
  build,
  on Ubuntu. (`allTests` also needs the iOS targets, which only run on macOS.)
- **Releases** are made by pushing a tag: `git tag v0.2.0 && git push origin v0.2.0`.
  `.github/workflows/release.yml` runs the tests, builds a signed release APK and publishes it as a
  GitHub release. A tag with a suffix (`v0.2.0-beta.1`) is a pre-release. The version name comes
  from
  the tag and the version code from the run number.
- Signing needs four repository secrets: `RELEASE_KEYSTORE_BASE64` (the keystore, base64-encoded),
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. Without the keystore
  the release build is unsigned, so local `assembleRelease` output won't install. Keep the keystore
  and
  its passwords backed up: losing them means updates can't install over existing installs.
- A release APK and a debug build share one package name but are signed with different keys, so
  installing one over the other fails until the first is uninstalled.

## Translating

[![Translation status](https://translate.lore-le.ch/widget/vibeyfin/multi-auto.svg)](https://translate.lore-le.ch/engage/vibeyfin/)

VibeyFin is translated on [Weblate](https://translate.lore-le.ch/workspaces/0e11631b-4809-477f-ab13-52e3dff0e7b6/).
You don't need to know Kotlin, Swift or Git: sign in, pick your language and start translating. One
set of strings feeds both the Android and the iOS app. Missing a language? Ask for it in an issue.

The text comes in sets. Today there are two: Common (what every app shows) and Mobile (downloads and
offline, for phones and tablets), and a TV set may follow. On Weblate each set is its own component, so
you translate shared text once and pick the component you want to work on. Nothing else changes for you.

Guidelines:

- Keep `{{placeholders}}` such as `{{name}}` and `{{count}}` exactly as written. Move them where your
  language needs them, but never rename or drop one.
- Keys ending in `_one` and `_other` are plural forms. Add the forms your language uses (`_few`,
  `_many` and so on).
- Leave product names alone: VibeyFin, Jellyfin, Seerr, Quick Connect.
- Follow the glossary on Weblate (My List, Who's watching?, Continue watching, Home, Downloads) so
  terms stay consistent.
- Stay under the length limit shown for tab, button and chip labels.
- Write in sentence case with plain verbs; errors say what happened and how to fix it.

Weblate opens a pull request with your work and a maintainer reviews and merges it. Please don't edit
`shared/strings/<set>/*.json` by hand, except `en.json`, the source text. The full workflow, including
adding a language and changing English text, is in [`docs/translating.md`](docs/translating.md).

By contributing a translation you agree that it is released under the same license as the
project's source code, [MPL-2.0](LICENSE).

## Built with

Kotlin, Jetpack Compose and Material 3, SwiftUI, Media3 ExoPlayer and AVPlayer, Ktor,
kotlinx.serialization, Koin, Room (KMP) with
the bundled SQLite driver, DataStore, Coil, and the multiplatform lifecycle ViewModel. Versions are
pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

The official Jellyfin Kotlin SDK is JVM-only, so the app talks to the Jellyfin REST API directly
through a thin Ktor client.

## License

VibeyFin is licensed under the [Mozilla Public License 2.0](LICENSE). The VibeyFin name and logo are
not covered by it.

The Android build bundles Jellyfin's FFmpeg audio decoder (`org.jellyfin.media3:media3-ffmpeg-decoder`),
which is GPL-3.0, so an Android APK that includes it is distributed under the GPL-3.0. MPL-2.0 is
compatible with that. The iOS build uses AetherEngine (LGPL-3.0 with an App Store exception) and
dynamically linked FFmpeg (LGPL-2.1+). All third-party components and their licences are listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Where to read more

- [`docs/ROADMAP.md`](docs/ROADMAP.md): what is done and what comes next, including Seerr
  requests and recommendations (Phases 8 and 9).
- [`docs/design/`](docs/design/brand.md): the design system, per platform.
- [`CLAUDE.md`](CLAUDE.md): the project's conventions, product decisions and architecture rules.
