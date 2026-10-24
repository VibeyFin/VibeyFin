# Accounts and polish (roadmap Phase 6): design

Status: draft for the owner's review, 2026-10-24. Calls marked *(my call)* are mine, not the owner's. Mockups: the `SettingsScreens` page of the VibeyFin design system artifact (https://claude.ai/artifact/HeWVw1EzAbYcvssURJ2rNc).

## Intent

Phase 6 makes the app livable for a household that shares devices. Today Settings is one flat list that grew a row at a time, there is no way to keep a child out of Settings or other people's profiles, the app never tells you a new episode arrived, and streaming quality is a hard-coded 60 Mbps. The phase gives Settings a real structure that later phases can extend, adds a kids profile with a PIN, adds background new-episode notifications, and makes quality and subtitle style settings.

## Scope

Four tracks, each on **both Android and iOS**, built and reviewed as separate sub-phases in this order (A unblocks Phase 7; B and C are independent after A, so after A they are built in parallel where the code allows, each reviewed on its own).

### A. Settings, redesigned (first)

1. **Settings as data.** The shared module exposes a tree: `SettingsPage(title, sections[])` → `SettingsSection(title?, footer?, items[])` → `SettingItem` of type Toggle, Choice, Slider, Link(page), Action, Info or Custom(key). Every item has a key, title, help text, scope (Profile or Device), a visibility rule and a default. Each platform has one renderer per type (SwiftUI list rows, M3 list rows); a new setting is one entry plus its column, no new screen code. Titles and help text live in `Strings` as today. `SettingsViewModel` builds the tree from `ProfileSettings` and `DevicePreferences`; iOS reads it through the existing `SettingsModel` facade.
2. **Root and sub-pages as in the mockups.** Profile card; Playback (Quality, Subtitles, Skipping and autoplay); Home (Featured title, Genre rows); Notifications; Kids and PIN; This device (Always ask who's watching); Advanced (Playback stats overlay, About). Existing settings keep their columns and defaults; "Skip intros and recaps" is renamed **Auto skip intros and recaps** (`Strings.autoSkipSegments`). Skip back, skip forward, auto skip, "Still watching after" and Buffer ahead (in its own Buffering group) move to the merged "Skipping and autoplay" page; there is no separate root Buffering row. The stats overlay moves to Advanced.
3. **Tablet layout** (design system `SettingsTablet`): at tablet width Settings is two panes, the grouped root on the left and the chosen page on the right (iPad `NavigationSplitView`, Android a two-column layout at the existing 600dp breakpoint), the detail column capped at about 640 and centred, Quality selected on open, collapsing to the phone's push navigation at compact width.
3. **Profile page** behind the profile card: avatar, name, server; Switch profile (to "Who's watching?"); read-only server name and addresses; Sign out with a confirmation (the explicit sign-out the session rules allow: clears the token, the tile stays and shows locked). No Sign out on a kids profile.
4. **Quality.** A per-profile maximum: Maximum (default, today's behaviour), 4K 40 Mbps, 1080p 10, 720p 4, 480p 1.5. It replaces the constant `MAX_STREAMING_BITRATE` in `PlaybackCapabilities` and `PlaybackRepository`, so both the device profile and the PlaybackInfo request read it. Not remembered per device or network.
5. **Subtitle style.** "Use system caption style" (default on, `guardrails.md`); when off: size (S, M, L, XL), text colour (white, yellow, light blue) and background (none, shadow, box) with a live preview and Reset to defaults.

### B. Kids profile and PIN

1. **Kids profile**: a per-profile local flag. It gives a simpler Home (no Advanced settings, no Sign out, no Notifications). It is separate from the PIN: a profile can have either, both or neither. Any profile may switch it on from its own Settings, with no gate until a PIN is set. The rating limits are **read from the Jellyfin user's policy** (`GET /Users/Me`, `Policy.MaxParentalRating`, `BlockedTags`, `AccessSchedules`) and shown read-only; the server already enforces them on every item query, so the app does not build its own rating filter.
2. **PIN**: 4 digits, optional, per profile. Stored as a salted hash, never the Jellyfin password. A **household lock, not security** (the mockup footer says so). It is its own per-profile lock, independent of kids mode, with three switches on the PIN page: **Ask for PIN to open this profile** (a lock badge on the tile in "Who's watching?"), **to open Settings**, and **to switch profile**. Turning kids mode off, changing the PIN and removing it always ask for the PIN once one is set (not switchable). Five wrong attempts lock the gate for 30 seconds *(my call)*.
   - **Whose PIN, and when.** "Open this profile" checks the **target** profile's PIN, when its tile is tapped in the picker. At launch the app never picks such a profile for the user: the picker stays up. "Open Settings" and "Switch profile" check the **current** profile's PIN; switching includes opening "Who's watching?" from inside the app and the picker at launch: if the last profile has "switch profile" on, the picker asks for its PIN first, even with "Always ask who's watching" on *(my call)*.
   - **Forgotten PIN.** The PIN prompt has "Forgot PIN?", which asks for that Jellyfin user's password and checks it with a normal sign-in (never stored). On success the PIN is removed and the kids flag stays. A child who does not know the password cannot get through; the Sign out row stays hidden on kids profiles *(my call)*.
3. **Policy is shown, not edited.** Jellyfin has no per-profile sub-accounts; an admin changes ratings, tags and hours in Jellyfin. It is fetched live when the Kids and PIN page opens (not cached) and has the five states, so a failed load says so. If the server sets no rating limit and no blocked tags, the page says "No limits are set on your Jellyfin server" and still lets the owner turn kids mode on *(my call)*.
4. **Kids profiles hide** Advanced, Sign out and the Notifications page *(my call)*.

### C. New-title notifications

1. Off by default, a per-profile setting covering **new episodes and new movies**. Scope: "Shows I follow and new movies" (default; shows in Continue watching or My List) or "Everything new in the library" *(my call on the wording)*. The setting and page are called **Notifications**.
2. Jellyfin has no push, so the app **polls in the background**: `GET /Shows/NextUp` and `GET /Items/Latest` for the profile, compares with the ids it has already notified about (a small Room table, keyed by server and profile), and posts a local notification per new episode (grouped per show) and per new movie. Tapping one opens its detail page.
3. Android: WorkManager periodic work (about every 4 hours) and the notification permission, asked when the setting is switched on. iOS: `BGAppRefreshTask` and `UNUserNotificationCenter`, best effort (iOS decides when it runs). The setting says to expect a delay.
4. Background work runs as the profile through `ConnectionManager.asProfile`; a 401 locks that profile the usual way and stops its polling. Nothing polls for a signed-out profile, and a kids profile never notifies *(my call)*.
5. **Dependency:** WorkManager (`androidx.work`), approved. `BGTaskScheduler` and `UserNotifications` are system frameworks, no dependency.

### D. Carry-overs (separate, do not gate A to C)

- Mount the iOS mini player (`MiniPlayerView`) in `TabShell` with `.tabViewBottomAccessory` and check it on the simulator, including play and pause back to the player (Phase 3's open item).
- Baseline profile for first-scroll speed (Phase 4 moved it here). Macrobenchmark and the baseline-profile Gradle plugin are **approved**; this item goes last so it cannot hold up A to C, and is measured on the `benchmark` build.
- Owed from Phase 4: the phone pass (`docs/checklists/phone-pass.md`) and the iOS Xcode pass. These are the owner's device checks and a build-and-fix task; list them in the sub-phase summaries, do not pretend they happened.

## Non-goals

- Phase 7's work: settings search, "Reset this section", copy and export settings, custom Home rows, theming, DisplayPreferences sync. The tree is shaped so they can follow, none is built.
- A client-side rating filter, content scheduling of our own, per-title blocks.
- Real security: no promise that a PIN stops a determined user (it can be bypassed by reinstalling or by signing in elsewhere).
- Seerr, downloads, remote push through our own server.
- Editing a Jellyfin user's name, avatar, policy or password.
- Changing the quality of a video while it plays (see the open question on the player's quality menu).

## Anti-goals

- A PIN that looks like security and is sold as such.
- Storing a password anywhere, or silently signing a profile out (CLAUDE.md: only a 401 or an explicit Sign out clears a token).
- Settings rows drifting back into hand-laid screens with per-row spacing.
- Notification spam or battery drain: one grouped notification per show, work that stops when disabled, nothing for a locked profile.
- Hand-copied strings or hex values (CLAUDE.md).

## Constraints

- All CLAUDE.md rules apply: logic and strings in `shared/`, native screens draw state, one `ConnectionManager`, 401 only through `expireSession`, Room auto-migration for every new column, **one schema version per sub-phase** (A is 9, B is 10, C is 11; *my call*, so each sub-phase ships on its own), tokens in the vault.
- New settings follow plan 15's pattern: column with `defaultValue`, `ProfileSettings` flow and setter with validated choices, tests (`ProfileSettingsTest`, `SettingsViewModelTest`, `MigrationTest`).
- Design docs are updated with the work: `docs/design/` (`screens.md` or a new settings section, `android.md`, `ios.md`, `guardrails.md`, `player.md`), kept in step with the design system artifact.
- Test server: a kids user with a `MaxParentalRating` and some rated items, and a series that gains an episode, are added to `make-media.sh` and `docs/test-server.md` before the features that need them are called done.
- `scripts/ios-smoke.sh` runs for anything touching iOS or shared code the iOS app uses; the UITest is extended for Settings navigation and the kids PIN flow. Say plainly what could not be run.
- Phases of the working agreement: one sub-phase finished, built, tested and summarised, then stop for review. Commits `task|fix|feat|chore: <message>`, one line, no trailer, and a clean history (see Delivery).

## Decided by the owner

Redesigned Settings for both platforms, extensible for future setting types; quality (a maximum and a default) is a setting; the PIN is for households that share devices, not real security, and is tied to the kids profile; notifications, the carry-overs and the rest of the proposed scope; "Auto skip intros and recaps", "Kids and PIN" and "PIN" as the names in the mockups.

## Decided in review (2026-10-24)

- "Play above the rating limit" is dropped from the PIN page: the server hides such titles, so there is nothing to unlock.
- No quality menu in the player this phase; the Quality page does not promise one.
- No mobile-data quality cap this phase.
- Any profile may switch kids mode on from its own Settings, with no gate until a PIN is set.
- iOS subtitle style: if only text tracks AVPlayer renders can be styled, the page shows its controls with the note "Applies to text subtitles only".
- WorkManager (`androidx.work`), Macrobenchmark and the baseline-profile plugin are approved.
- The PIN is independent of kids mode (the earlier "coupled" remark was corrected).
- Notifications also cover new movies; the setting and page are named "Notifications".

## Delivery

- **One branch, one PR** (`feature/phase-6-accounts-polish` into `develop`) for the whole of Phase 6. A, B, C and D are built and reviewed in order inside it; the working agreement's stop for review after each sub-phase still applies, but they are not separate PRs. B and C build in parallel after A.
- **A clean history.** Small commits, each building and passing its tests, with the one-line `task|fix|feat|chore:` messages and no trailer. No "wip", "fix typo" or "address review" commits: fold fixups into the commit they belong to (`git commit --fixup` and an autosquash rebase) before the PR, so the history reads as the story of the change. Nothing is force-pushed to a shared branch without asking.
- Parallel subagents may use temporary git worktrees under `.claude/worktrees`, merged back into the feature branch in a tidy way. **Clean them all up at the end** (remove each worktree and its branch, prune), and say so in the final report.

## Left to the builder

- Where the PIN hash lives (a Room column on `account_settings` or an entry in the `TokenVault`), the hashing parameters, and the exact lockout timings.
- Notification polling interval, channel and group names, and the table that remembers what was notified.
- Android's subtitle styling surface (`CaptionStyleCompat` on the player's `SubtitleView`).
- **Risk to investigate first:** iOS burns subtitles into the picture today (Phase 1), and burned-in text cannot be styled by the client. Subtitle style on iOS may only be possible for text tracks AVPlayer renders itself (`AVTextStyleRule`). A short spike decides what the iOS style options can honestly do; if they cannot apply, the iOS page says so rather than showing dead controls.
- The renderer details of the Choice row (decided: always a menu; Quality, marked `asPage`, is inline checklist rows in its section) and the kids profile's simplified Home.

## Exit

Open Settings on both platforms and find every setting under a clear group; change the quality cap and see a stream request respect it; set subtitle style where the platform allows it; mark a profile as kids with a PIN, and fail to open Settings or switch profile without it; turn on new-episode notifications, add an episode on the test server and get one notification that opens the episode; the iOS mini player appears on iOS 26.
