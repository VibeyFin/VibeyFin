# Plan 02: Native UI per platform, shared logic

Goal: the UI is native on each platform (Jetpack Compose on Android, SwiftUI on iOS) and only the
logic is shared. Today `shared/` holds both. This plan runs before Phase 6 of plan 01, so
"Who's watching?" and sign-in are built once, natively.

## What stays shared, what goes native

Stays in `shared/`: data, domain, DI, and the ViewModels (`ServersViewModel`, `AddServerViewModel`)
with their UI state types. They own the behaviour; screens only draw state and forward events. The
wording of errors (`ConnectionIssue.message()`) stays shared so both platforms say the same thing.

Moves out of `shared/`: screens, components, theme and navigation, plus the Compose Multiplatform,
Material 3 and navigation-compose dependencies.

| Concern | Android (`androidApp`) | iOS (`iosApp`) |
|---|---|---|
| UI | Jetpack Compose, AndroidX Material 3 | SwiftUI, iOS 16 and up |
| Navigation | AndroidX Navigation Compose, type-safe routes | `NavigationStack` with a route enum |
| ViewModel | Shared `ViewModel` via `koinViewModel()` | Shared `ViewModel` behind an iOS facade |
| Theme | Palette tokens from CLAUDE.md in Kotlin | The same tokens in Swift |

No pixel parity is the goal. Each platform follows its own idioms as long as the design language
in CLAUDE.md holds: dark ink, amber for the one primary action, teal for availability only, heavy
tight titles.

## How SwiftUI reads shared state

No new dependency. `iosMain` exposes one facade per screen, `ServersModel` and `AddServerModel`.
A facade owns its own `ViewModelStore` (so `viewModelScope` is cancelled on `close()`), takes
plain typed callbacks (`observe { state in ... }`) and forwards the screen's events. Swift wraps
each facade in an `ObservableObject`. Callbacks arrive on the main thread, and state types are
the shared Kotlin classes, so nothing is cast from `Any`.

## Verification limits

This Mac has Xcode's command-line tools only, so nothing for iOS can be built, linked or run here.
What can be checked: the Kotlin side compiles to iOS klibs, and the Swift files parse with
`swiftc -parse`. Whether the Swift type-checks against the generated `Shared` framework and how it
looks stay unverified until someone builds it in Xcode. Every iOS step says so.

## Phase 1: Native Android UI

- Add AndroidX Compose (BOM), Material 3, Navigation Compose, lifecycle and Koin Compose to
  `androidApp`, at their latest stable versions.
- Port the theme, text field style, Servers screen, Add server screen and navigation host into
  `androidApp`, using the shared ViewModels. The Compose Multiplatform UI in `shared/` stays until
  Phase 3.
- Done when: `:androidApp:assembleDebug` builds and, on an emulator, the empty state, adding by
  address, a wrong address, a discovered server and merging an already saved server all work as
  before.

## Phase 2: iOS SwiftUI

- Add the `ServersModel` and `AddServerModel` facades and Koin start-up to `shared/src/iosMain`.
- Write the SwiftUI app in `iosApp/iosApp`: theme, Servers view, Add server view, navigation, and
  an `ObservableObject` per facade. Remove the Compose host (`ContentView` wrapping
  `MainViewController`). The Xcode project uses synchronized folders, so new files need no project
  edits.
- Done when: the Kotlin iOS targets compile, every Swift file parses, and each Kotlin API the Swift
  calls is checked against its Kotlin declaration. Not run on a simulator or device.

## Phase 3: Shared becomes logic only

- Delete the Compose screens, components, theme, navigation host, `App.kt` and `MainViewController`
  from `shared/`. Drop the Compose Multiplatform and Kotlin Compose plugins and the Compose,
  navigation-compose, lifecycle-compose and koin-compose dependencies; keep
  `lifecycle-viewmodel` and add `koin-core-viewmodel` explicitly.
- Update CLAUDE.md: module description, stack, architecture layers and design language per
  platform.
- Done when: shared tests pass, `:androidApp:assembleDebug` builds, the iOS klibs compile, and
  `shared/` has no Compose dependency.

## Out of scope

Redesigning screens, new features, localisation, and iOS discovery (still blocked on Apple's
multicast entitlement).
