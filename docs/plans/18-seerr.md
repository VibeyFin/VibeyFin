# Plan 18: Seerr, connect, search and request (first part of roadmap Phase 8)

> **Status:** planned, not started. Work it later, in the phases below, stopping for review after each.

**Goal:** Connect a Seerr instance, find titles that aren't in the library from Search, request them, and
see their status. Discover rows, "My requests" and Phase 9's "Because you watched" rows come in later plans.

**Related:** issue #14 (Seerr results when a search finds nothing), `docs/ROADMAP.md` Phases 8 and 9,
plan 13 (search).

## Decisions

- **Native UI, no web view.** The app never loads Seerr's web pages (no WebView, `WKWebView` or Safari
  hand-off). It talks only to Seerr's JSON API and draws every Seerr screen itself in Compose and SwiftUI,
  with the same posters, rows, sheets and design tokens as the rest of the app.
- **Sign-in with the Jellyfin login, once.** `POST /api/v1/auth/jellyfin` with the profile's Jellyfin
  username and password returns a `connect.sid` session cookie. Only that cookie is kept, sealed by the
  existing `TokenVault`; the password is used once and never stored. The user is asked again only when
  Seerr answers 401, the same rule as Jellyfin tokens (CLAUDE.md "Sessions"). A Seerr API key was ruled
  out: it acts as the admin, so requests and quotas would not be per user.
- **One Seerr address per Jellyfin server**, keyed by server ID; each profile signs in to Seerr as itself.
  (The roadmap said "saved per profile"; this replaces that.)
- **Optional.** With no Seerr connected, nothing of Seerr shows except "Connect Seerr" in Settings, and a
  Seerr failure only affects Seerr's own parts (ROADMAP "Cross-cutting").
- **A Seerr 401 never touches Jellyfin.** It clears only the Seerr session; it does not go through
  `ConnectionManager.expireSession`.

## Not in this plan

Discover rows on Home, a "My requests" list, request approval for admins, 4K requests, recommendation rows
(Phase 9), Seerr notifications.

## Shape

```
data/seerr/   SeerrApi (Ktor, base <url>/api/v1, cookie header), DTOs from seerr-api.yml
data/db/      seerr_servers (serverId → url); account_settings gets a sealed seerrSession column
domain/       SeerrRepository: connect, signIn, search, title, request; Seerr connection StateFlow
ui/search/    SearchUiState gains a "Not in your library" group
ui/request/   RequestViewModel: title, status, season picker, send request
ui/settings/  "Seerr" row: connect, sign in, sign out, remove
```

## Steps

1. **Seerr client** (`shared/src/commonMain/.../data/seerr/`). A thin Ktor client written against
   `seerr-api.yml` (seerr-team/seerr), in the style of `data/api/JellyfinHttpClient.kt` and `ApiResult.kt`.
   Endpoints: `GET /status`, `POST /auth/jellyfin`, `GET /auth/me`, `GET /search?query=&page=`,
   `GET /movie/{id}`, `GET /tv/{id}`, `POST /request`. The cookie is sent as a header by us (no Ktor
   `HttpCookies` plugin), so the session lives in the vault and nowhere else. Reuse `RetryPolicy.kt` and
   `TlsFailure.kt`. DTOs stay in `data`.
2. **Storage.** One Room auto-migration: a `seerr_servers` table (`serverId` primary key, `url`) and a sealed
   `seerrSession` blob on the account row, sealed and discarded through `TokenVault` like the Jellyfin
   token. Export the schema and extend `MigrationTest`.
3. **`SeerrRepository` and connection state** (`domain/`). Built from `ConnectionManager.state`: for the
   connected server and account it exposes `Disconnected`, `NeedsSignIn` or `Ready`. A 401 moves to
   `NeedsSignIn` only. Inject the clock as elsewhere.
4. **Matching the two worlds.** Seerr results carry a TMDB id and `mediaInfo.status` (1 unknown,
   2 pending, 3 processing, 4 partially available, 5 available, 6 deleted). Ask Jellyfin for `ProviderIds`
   (add to the fields in `LibraryApi.kt`) so an available Seerr title opens the Jellyfin item page instead
   of the request page, and isn't listed twice.
5. **Search** (`ui/search/SearchViewModel.kt`). After the Jellyfin search answers, if Seerr is `Ready`,
   search Seerr with the same text and add a "Not in your library" group with titles whose TMDB id isn't in
   the Jellyfin results. This covers issue #14 (no Jellyfin results) and the wider section in plan 13. A
   Seerr failure leaves the Jellyfin groups as they are and shows a one-line note on that group.
6. **Request page** (`ui/request/RequestViewModel.kt`, new). Poster, title, year, overview and status
   (requested, pending approval, processing, partly available, available). "Request" is the screen's one
   aqua primary action. TV gets a season picker in a platform sheet; seasons already requested or available
   show but can't be picked. Errors say what happened: quota reached, already requested, needs approval,
   Seerr unreachable. Routes from Search on both apps (`@Serializable` route on Android, `NavigationStack`
   on iOS) and an iOS facade in `shared/src/iosMain/.../ios/` like `PlaybackModel.kt`.
7. **Settings.** A "Seerr" row in `ui/settings/SettingsPages.kt`: enter the address (checked with
   `GET /status`), sign in (password field, used once), a "Signed in as" line, sign out of Seerr, and remove
   Seerr from this server. Any profile can connect; Seerr decides what each user may do.
8. **Text.** Every label, status, error and accessibility text in `Strings`, errors in a `SeerrMessages`
   beside `LoadMessages`. Status shows in text and icon, never in aqua (CLAUDE.md design rules).
9. **Native screens.** Compose and SwiftUI: the Search group, the request page, the season picker and the
   Settings row. Screens only draw state and forward events.
10. **Test server and docs.** Add a pinned Seerr container to `scripts/test-server/start.sh`, linked to the
    test Jellyfin, with a script that completes Seerr's first-run setup against it. No Radarr or Sonarr:
    requests then stay pending or approved, which is enough to test. Document it in `docs/test-server.md`.
    Tick Phase 8 in `docs/ROADMAP.md` and fix its "Teal marks available" line (aqua never signals status).

## Phases

- **A.** Steps 1–4: shared client, storage, repository and matching, with tests.
- **B.** Step 5: search.
- **C.** Step 6: request page.
- **D.** Step 7: settings.
- **E.** Step 10: test server and docs.

Text (step 8) and native screens (step 9) are done inside each phase that needs them.

## Verification

- `./gradlew :shared:allTests`: Ktor `MockEngine` tests for `SeerrApi` (cookie sent, 401 mapping, DTOs
  parsed from fixture JSON taken from the spec); repository tests for the states (no address, needs sign-in,
  ready, 401 to needs sign-in without touching the Jellyfin session); `SearchViewModel` tests for merging
  and for a Seerr failure; `RequestViewModel` tests for a movie request, a season request and each error.
- `./gradlew :androidApp:assembleDebug`, then against the test server with Seerr: connect, sign in, search
  a title that isn't in the library, request it and see "Requested"; search one that is and get the
  Jellyfin item, not a duplicate.
- iOS: `scripts/ios-smoke.sh` with the UITest extended by the connect and search flow. Say plainly if it
  could not run.
