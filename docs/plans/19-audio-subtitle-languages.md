# 19 · Audio and subtitle languages (issue #28) Implementation Plan

**Goal:** A profile picks its preferred audio language, subtitle language and when subtitles show, in
Settings → Playback → **Audio and subtitles**, so a video starts with the right tracks without opening the
player's track menu.

**Decisions (confirmed 2026-10-27):**
- **Stored on the Jellyfin account**, not on the device: the user's `UserConfiguration`
  (`AudioLanguagePreference`, `SubtitleLanguagePreference`, `SubtitleMode`, `PlayDefaultAudioTrack`,
  `RememberAudioSelections`, `RememberSubtitleSelections`), read with `GET /Users/Me` and written with
  `POST /Users/Configuration`. The server then picks `DefaultAudioStreamIndex` / `DefaultSubtitleStreamIndex`
  itself, which `MediaSourceDto.tracks()` already honours, so the player needs **no track-matching logic** and
  every Jellyfin client shares the choice. No Room column, no migration.
- **A new "Audio and subtitles" page** under Playback; the existing Subtitles (caption style) page stays.

**Mockups:** `SettingsPhone` (pages `Audio and subtitles`, `Audio language`, `When subtitles show`, the
can't-reach-server state, and the new root row) and `SettingsTablet` (split view) in the shared mockups artifact
(https://claude.ai/code/artifact/9a3ea814-fd54-4106-b2ab-71115ddc3c7b), version 10.

**Branch / PR:** `feature/28-audio-subtitle-languages` off `develop`, one PR into `develop`. Commits
`task|fix|feat|chore: <short message>`, one line.

## Review focus

- **`POST /Users/Configuration` replaces the whole configuration.** Read-modify-write, keeping every field we
  don't model (ordered views, grouped folders, `DisplayMissingEpisodes`, and so on). A write must never reset
  another client's settings. Round-trip the configuration as a `JsonObject` and only set our keys.
- A non-admin user may write their own configuration (test server: the Kids user). If the server refuses (403),
  the page says so and keeps the old values.
- Offline or unreachable server: the rows show the last values read, dimmed and inert, with a footer. Nothing
  queues writes for later.
- A choice made in the player still wins for that video (unchanged `PlaybackViewModel` behaviour).
- A video without the preferred language plays the server's default tracks; playback never fails over this.

## Behaviour

| Row | Values | Writes |
| --- | --- | --- |
| Audio language | "Any language", or a language | `AudioLanguagePreference` = ISO 639-2 code or `""`; `PlayDefaultAudioTrack` = `true` for Any, `false` for a language (otherwise the server prefers the file's default-flagged track over the language) |
| Subtitle language | "Any language", or a language | `SubtitleLanguagePreference` = code or `""` |
| When subtitles show | Default, Always, Only forced, Smart, Off | `SubtitleMode` = `Default`, `Always`, `OnlyForced`, `Smart`, `None` |
| Remember what I pick in the player | toggle | both `RememberAudioSelections` and `RememberSubtitleSelections` |

- The languages come from `GET /Localization/Cultures` (`ThreeLetterISOLanguageName`, `DisplayName`), sorted by
  name, de-duplicated by code. Their list opens as the picker sheet the filters and the player's tracks use, with a
  search field and "Any language" first; a pick saves it and closes the sheet. A stored code
  that isn't in the list still shows (its code as its label) so a value set by another client isn't lost.
- Root row help sums it up, e.g. "German · English subtitles, always", or "Server defaults" when nothing is set.
- Changes apply from the next video; the player asks PlaybackInfo afresh each time.
- Downloads follow the preferences too, including ones changed after the download. The device keeps a copy of the
  preferences with the profile (`account_settings.languagePrefs`, database version 14), refreshed whenever they are read
  or saved and whenever a download is planned. A download saves each track's language and its default and forced flags,
  and offline playback picks the tracks from the copy with the server's own rules (`chooseTracks`, after Jellyfin's
  `MediaStreamSelector`). Only subtitle files come along with a download, so only they can be picked. A download saved
  before this, or a profile whose preferences were never read, keeps the tracks the server picked at download time.
  Checked with the `download-languages` fixture.
- To verify on the test server: what the server does with an empty `SubtitleLanguagePreference` (the ticket's
  "Same as audio"). If it falls back to the audio language, label the empty option "Same as audio" instead of "Any language".
  Also check that forced subtitles in the audio's language show in Off mode, as the ticket says. Update the mockup to match what the server does.

## Tasks

### 1. API and DTOs (`shared/data/api`)
- `UserDtos.kt`: `UserDto.configuration: JsonObject?` (`@SerialName("Configuration")`) plus a small typed
  reader `UserLanguagePrefs` (the six fields above, with Jellyfin's defaults when absent).
- `JellyfinApi`: `cultures(): ApiResult<List<CultureDto>>` (`GET /Localization/Cultures`, retry-safe) and
  `updateUserConfiguration(userId, config: JsonObject): ApiResult<Unit>` (`POST /Users/Configuration?userId=`;
  10.10 is our minimum and has it).
- Tests (`MockEngine`): the configuration is posted whole with only our keys changed; 401 goes through the
  usual `UnauthorizedEvents`; 403 is a failure, not a session expiry.

### 2. Domain: `LanguagePreferences` (`shared/domain`, Koin single)
- `load(): ApiResult<LanguageSettings>` (calls `me()` and `cultures()` in parallel; cultures failing degrades to codes as labels).
- `setAudioLanguage(code?)`, `setSubtitleLanguage(code?)`, `setSubtitleMode(mode)`, `setRemember(Boolean)`:
  each re-reads `me()` just before writing (read-modify-write against the latest server copy) and returns the new
  `LanguageSettings`.
- `SubtitleMode` enum mapped to and from the server's strings; an unknown string reads as `Default`.
- Runs only as the connected profile through `ConnectionManager`; holds no token.
- Tests (`LanguagePreferencesTest`, `TestApp`): read defaults; each setter writes the right keys; unknown fields
  survive a write; `PlayDefaultAudioTrack` follows the audio choice; unknown stored code kept; offline → failure.

### 3. Settings tree and ViewModel (`shared/ui/settings`)
- `SettingsPageId.AudioSubtitles`, `AudioLanguage`, `SubtitleLanguage`; `SettingKey.AudioSubtitles`,
  `AudioLanguage`, `SubtitleLanguage`, `SubtitleMode`, `RememberTracks`.
- Languages are strings, `ChoiceItem` is `Int`-valued: the builder indexes the sorted culture list (0 = Any) and
  the ViewModel maps the index back to a code. No new item type.
- The language pages are pages of the tree with a `search` and a `sheet` flag on `SettingsPage`, filtered by the
  ViewModel (`onSearch(page, query)`), so both platforms get search from one place. `sheet` makes a link open the page
  as a picker sheet over the linking page instead of pushing it; a pick sets the one-shot `closePage`, which closes it.
- `LanguageState` (Loading / Failed(error) / Loaded) like `PolicyState`; loaded when the page opens. While not
  Loaded, the rows show the last values and `enabled = false`, and the footer says why (`LoadMessages`).
- Optimistic update with rollback on failure, plus a one-shot error message.
- Tests in `SettingsViewModelTest` / `SettingsPagesTest`: the root summary, visibility (hidden with no profile),
  disabled state offline, search filtering, the index-to-code mapping, rollback.

### 4. Strings
All titles, option labels, footers, the root summary builder, search placeholder and error text go in
`Strings` (sentence case), with the mode explanations from the mockup.

### 5. Android (`androidApp/.../settings`)
- Existing renderers draw the new rows. A link to a `sheet` page opens `PickerSheet` (the filters' sheet, with
  `PickerSearch`) built from the page's choice. The `SubtitleMode` row is the existing `ChoiceRow` menu.

### 6. iOS (`iosApp/iosApp/Settings`)
- Same: a link to a `sheet` page opens `PickerSheetView` with its search field; the mode is the existing menu
  `Picker`. Facade gets `search` and `pageClosed`. Labels through `titleCased()`.

### 7. Test server fixture and verification
- `make-media.sh`: a **"Languages"** movie, MKV, two audio tracks (`eng` default-flagged, `deu`) and three
  text subtitles (`eng`, `deu`, and a forced `eng`), each saying its language on screen. Add it to the
  catalogue in `docs/test-server.md`.
- Check against the test server as admin and as the Kids user: German audio with English subtitles (Always)
  starts in German with English subtitles; a fixture without German plays its default tracks; the empty
  subtitle-language and Off-mode questions above.
- Extend the iOS UITest: Settings → Audio and subtitles opens and shows its rows.

### 8. Done
- `./gradlew :shared:allTests`, `./gradlew :androidApp:assembleDebug`, `scripts/ios-smoke.sh` (scheme
  `iosAppUITests`); report what could not run.
- Tick the "default audio and subtitle language" part of Phase 7 "Player preferences" in `docs/ROADMAP.md`.
