# Plan 01: Server connection, discovery, authentication, profiles

Goal: from a fresh install, find or add a Jellyfin server, pick a profile on a Netflix-style
"Who's watching?" screen, sign in once, and come back later straight into that profile.
Home can be a placeholder screen showing the signed-in user and server.

## Flow

App start:
- No saved servers → **Servers** screen.
- Saved server, last profile has a session, "always ask" off → straight to Home.
- Otherwise → **Who's watching?** for the last used server.

Screens: Servers (saved + discovered, "Add server") → Add server → Who's watching? →
password sheet (only when needed) → Home placeholder with a profile menu
(Switch profile, Switch server, Sign out).

## Endpoints

| Purpose | Request |
|---|---|
| Validate server, get id/name/version | `GET /System/Info/Public` (no auth) |
| Profile tiles | `GET /Users/Public` (no auth; hidden users don't appear) |
| Avatar | `GET /Users/{id}/Images/Primary?tag=…&maxWidth=…` |
| Sign in | `POST /Users/AuthenticateByName` `{ "Username", "Pw" }` → `AccessToken`, `User` |
| Quick Connect available? | `GET /QuickConnect/Enabled` |
| Quick Connect start / poll | `POST /QuickConnect/Initiate` → `Code`, `Secret`; `GET /QuickConnect/Connect?secret=…` until `Authenticated` |
| Quick Connect finish | `POST /Users/AuthenticateWithQuickConnect` `{ "Secret" }` |
| Session still valid? | `GET /Users/Me` (401 = expired) |
| Sign out (revoke) | `POST /Sessions/Logout` |

Verify field names against the server's OpenAPI spec (`/api-docs/openapi.json` on a 10.10+
server) before writing DTOs; JSON is PascalCase.

## Phase 1: Project scaffold

- Create the module layout from CLAUDE.md, version catalog, Koin setup, theme (palette from
  CLAUDE.md), empty navigation graph, Android and iOS entry points.
- Done when: `:androidApp:assembleDebug` builds, the iOS app launches in the simulator, and both
  show a placeholder screen in the VibeyFin theme.

## Phase 2: Storage

- Room KMP database: `servers`, `server_addresses`, `accounts` (encrypted token, nullable),
  `account_settings` (per profile, one column per option). Cascading deletes from server.
- `TokenVault` expect/actual: Android Keystore AES-256-GCM (blob = 12-byte IV + ciphertext);
  iOS Keychain. Unreadable token → treat as expired and clear it.
- `ServerRepository`, `AccountRepository`, `DevicePreferences` (DataStore: device ID, last server,
  last profile, always-ask toggle).
- Done when: common tests cover multi-address ordering, same server via two URLs = one row,
  expire keeps the profile but locks it, unreadable token clears, profile ordering by last use,
  cascade delete. An Android instrumented test covers Keystore round-trip and tamper rejection.

## Phase 3: Jellyfin API client and address resolution

- Ktor client factory with the `MediaBrowser` auth header, JSON config (ignore unknown keys),
  short connect timeout for probing (about 5 s).
- Address candidates from user input, e.g. `nas`, `nas:8096`, `192.168.1.20`, `https://jelly.example.com`:
  - With scheme: use as given.
  - Without scheme: try `http://` and `https://`.
  - Without port: also try 8096 for http and 8920 for https; keep the bare host (port 80/443) for reverse proxies.
  - Normalise: lower-case host, strip trailing slash and `/web` suffixes.
- Probe all candidates in parallel with `/System/Info/Public`. Score: reachable + valid
  `ProductName` ("Jellyfin Server") + version ≥ minimum (start at 10.10.0) + prefer https +
  faster response. Report clear issues: unreachable, TLS failed, not a Jellyfin server, version too old.
- Done when: MockEngine tests cover candidate generation for each input form, scoring and
  ordering, and each error case.

## Phase 4: Add server manually

- Add server screen: address field, "Connect" button, shows a server card (name, version,
  address that worked) or a specific error. Confirm saves via `ServerRepository`.
- Adding a server that's already saved (same server ID) merges the address instead of duplicating.
- Done when: adding a real server works on Android, and a wrong address shows a helpful message.

## Phase 5: LAN discovery

- Send UDP broadcast `who is JellyfinServer?` to `255.255.255.255:7359`; collect JSON replies
  (`Address`, `Id`, `Name`, `EndpointAddress`) for about 3 s, de-duplicate by `Id`, expose as a Flow.
- Android: hold a `WifiManager.MulticastLock` while discovering (androidMain); add
  `ACCESS_WIFI_STATE` and `CHANGE_WIFI_MULTICAST_STATE`. Check whether the targetSdk requires a
  local-network permission and handle it.
- iOS: broadcast needs `NSLocalNetworkUsageDescription` and the
  `com.apple.developer.networking.multicast` entitlement, which Apple must approve. Until then,
  discovery is disabled on iOS behind a capability flag and manual add is the path.
- Servers screen shows saved servers first, then discovered ones not already saved;
  tapping a discovered server runs the same validation as manual add.
- Done when: a server on the same Wi-Fi appears within about 3 s on an Android device.

## Phase 6: Who's watching? and sign-in

- Profile grid for the selected server: saved accounts merged with `/Users/Public`, keyed by user
  ID. Tile = avatar (or initial on a tinted square), name, lock icon when a password is needed.
  Extra tiles: "Other user" (username + password) and "Quick Connect" (if enabled).
- Tap logic: saved session → sign in instantly; user without a password → authenticate with an
  empty password; otherwise → password sheet. Save the token on success.
- Done when: all three tile paths sign in, and a wrong password shows an inline error without clearing the field.

## Phase 7: Sessions, switching, Quick Connect, startup routing

- `ConnectionManager`: activate a session (server address fallback through saved addresses),
  expose the current user, central 401 handling → `expire()` → Who's watching?.
- Home placeholder with a profile menu: Switch profile (no password for saved sessions),
  Switch server, Sign out (calls `/Sessions/Logout`, then clears the token locally).
- Quick Connect sheet: show the code, poll every 3 s, cancel button, timeout message.
- Startup routing as described in Flow; "Always ask who's watching" toggle in a settings stub.
- Done when: switching between two profiles needs no password, revoking the token on the server
  sends you back to a locked tile on the next request, and cold start lands on the right screen
  in all three cases.

## Out of scope for this plan

Library browsing, playback, Seerr, profile management on the server, PIN codes.
