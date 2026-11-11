# Local test server

A disposable Jellyfin under podman for testing skip intro, trickplay, HDR badges and "still
watching". Scripts live in `scripts/test-server/`; server state and generated media go to
`scripts/test-server/data/` (git-ignored).

```sh
scripts/test-server/start.sh   # idempotent: generates media, starts, completes the wizard, adds libraries
```

- URL: `http://localhost:8196` (from the Android emulator use `http://10.0.2.2:8196`)
- Container: `vibeyfin-jellyfin`, image `docker.io/jellyfin/jellyfin:12` (the floating major tag: any 12.x
  build, so a fresh pull can move to a newer one; it was 12.2.0 when this was written)
- User `admin`, password `vibeyfin-test` (throwaway value for this local server only)
- Stop with `podman stop vibeyfin-jellyfin`. Reset with `podman rm -f vibeyfin-jellyfin && rm -rf scripts/test-server/data/config scripts/test-server/data/cache`.
- A Seerr linked to it starts as well, at `http://localhost:5155` ("Seerr" below).

## What `start.sh` sets up

The first-run wizard is done through the Startup API (culture en-US, the admin user above, remote
access on). Three libraries. Shows and Movies have trickplay extraction on (`EnableTrickplayImageExtraction`
and `ExtractTrickplayImagesDuringLibraryScan`, set through the library options API); Failures has it off,
so its items have no trickplay:

| Library | Path | Content |
| --- | --- | --- |
| Shows | `/media/shows` | "Test Show" S01E01 to S01E04 (3 min each, 640x360 H.264; four so "Are you still watching?" can be reached with the setting at After 2 episodes: two start by themselves, and the prompt replaces the countdown in the third because a fourth follows; about 8 minutes hands-off) with chapters `Scene 1` 0:00, `Intro` 0:10-0:40, `Episode` 0:40, `Credits` 2:30-end |
| Movies | `/media/movies` | "HDR Sample (2026)": 20 s HEVC 10-bit, bt2020 / smpte2084 with mastering metadata |
| Failures | `/media/failures` | 20 s clips, each with one thing a player may refuse (see the catalogue); no chapters, so no segments; no trickplay. `uhd-load` is 10 minutes long; `dv-p8` is added by hand |

`make-media.sh` creates these with the ffmpeg inside the Jellyfin image (no host ffmpeg). Jellyfin
names the first episode "Episode 1" and the HDR movie "HDR" because there is no online metadata
match; the file names are what the scripts own.

## Search for subtitles

The test server has **no subtitle provider**: the OpenSubtitles plugin needs an account and has a daily limit, so it isn't installed and CI fakes the provider with `MockEngine`. What it can check: `EnableSubtitleManagement` (off by default on the `admin` user) hides or shows the entry, and the search returns `[]`, which is the "nothing found" state. Finding results and adding a real file is checked by hand against a server with a provider. `plain` has no subtitles, for the list with only "Off" and the search row.

## Fixture catalogue

Every file `make-media.sh` creates, by stable name. Tests and manual checks refer to a fixture by
this name; renaming or changing one is a reviewed change. All paths are under
`scripts/test-server/data/media/`.

| Fixture | File | What it is | Needed by |
| --- | --- | --- | --- |
| `show-e01` | `shows/Test Show/Season 01/Test Show S01E01.mkv` | 3 min, 640x360 H.264 (`testsrc2`), 440 Hz AAC, chapters `Scene 1` / `Intro` 0:10-0:40 / `Episode` / `Credits` 2:30-end | Skip intro and credits segments, trickplay, chapters, resume, next episode |
| `show-e02` | `shows/Test Show/Season 01/Test Show S01E02.mkv` | Same as `show-e01`, pattern `smptebars` | Next episode, watch status |
| `show-e03` | `shows/Test Show/Season 01/Test Show S01E03.mkv` | Same as `show-e01`, pattern `rgbtestsrc` | "Are you still watching?" (the prompt appears here with the setting at After 2 episodes) |
| `show-e04` | `shows/Test Show/Season 01/Test Show S01E04.mkv` | Same as `show-e01`, pattern `smptehdbars` | Makes the prompt in `show-e03` appear: the prompt needs a next episode |
| `movie-hdr` | `movies/HDR Sample (2026)/HDR Sample (2026).mkv` | 20 s, 1280x720 HEVC 10-bit, bt2020 / smpte2084, mastering metadata, 330 Hz AAC; trickplay on (Movies library), no chapters so no segments | HDR10 badge, HDR to SDR handling in negotiation |
| `plain` | `failures/Plain Clip (2026)/Plain Clip (2026).mkv` | 20 s, 640x360 H.264 (`testsrc2`), 440 Hz AAC, no chapters, no subtitles | The baseline for the failure fixtures; "no segments, no trickplay": no skip button, no scrub thumbnails |
| `dts-audio` | `failures/DTS Audio (2026)/DTS Audio (2026).mkv` | Same as `plain`, with DTS audio instead of AAC | An audio codec a device may not decode: negotiation |
| `mkv-vp9-opus` | `failures/MKV VP9 Opus (2026)/MKV VP9 Opus (2026).mkv` | Same as `plain`, VP9 video and Opus audio in MKV | A file AVPlayer cannot open: negotiation |
| `av1-ac3` | `failures/AV1 AC3 (2026)/AV1 AC3 (2026).mkv` | Same as `plain`, AV1 10-bit (SVT-AV1, `yuv420p10le`) video and AC-3 audio in MKV, like the web rips many libraries hold | iOS download: kept as sent and played in Aether, which decodes AV1 in software where the device has no hardware decoder |
| `subs-text` | `failures/Text Subtitles (2026)/Text Subtitles (2026).mkv` | Same as `plain`, plus two SubRip tracks (`eng` "English subtitle", `deu` "Deutscher Untertitel"), shown 1 s to 15 s | Text subtitle tracks and the picker |
| `languages` | `failures/Languages (2026)/Languages (2026).mkv` | Same as `plain`, but two AAC audio tracks (`eng` "English" 440 Hz, flagged default; `ger` "Deutsch" 880 Hz) and three SubRip tracks (`eng` "English subtitle", `ger` "Deutscher Untertitel", and `eng` "Forced English subtitle" flagged forced), shown 1 s to 15 s | Preferred audio and subtitle languages (issue #28): which tracks a video starts with. Also the detail page's Technical info, whose Audio and Subtitles lines list these tracks |
| `subs-mov-text` | `failures/MovText Subtitles (2026)/MovText Subtitles (2026).mp4` | Same as `plain` in MP4, plus one `mov_text` track (`eng`, "English subtitle" text, 1 s to 15 s) | A text subtitle codec the server has no subtitle profile for: the app must paint it once, not also get it burned in |
| `subs-webvtt` | `failures/WebVTT Subtitles (2026)/WebVTT Subtitles (2026).mkv` | Same as `plain`, plus one `webvtt` track (`eng`, 1 s to 15 s) in Matroska | The same, for WebVTT |
| `subs-image` | `failures/Image Subtitles (2026)/Image Subtitles (2026).mkv` | Same as `plain`, plus one PGS track (a white bar, 1 s to 15 s, hand-written because ffmpeg cannot encode PGS) | Image subtitles: burned in by a transcode |
| `truncated` | `failures/Truncated (2026)/Truncated (2026).mp4` | An MP4 cut in half: no moov atom, so no streams and no duration. Jellyfin still lists it | A file that cannot play at all: the error path |
| `truehd-hevc` | `failures/TrueHD HEVC (2026)/TrueHD HEVC (2026).mkv` | 20 s, 640x360 HEVC 10-bit (`libx265`, `yuv420p10le`), TrueHD audio (5.1 if the encoder accepts six channels, else stereo), 440 Hz, in MKV | Direct play of a lossless audio codec that AVPlayer and many Android devices cannot decode: the audio must not go silent |
| `mpeg2-ts` | `failures/MPEG2 TS (2026)/MPEG2 TS (2026).ts` | 20 s, 640x360 MPEG-2 video (`mpeg2video`) and MP2 audio 440 Hz, in an MPEG transport stream | A container and video codec many devices have no decoder for (AVPlayer has none for MPEG-2 video): the fallback path, and an old container read by its extension |
| `avi-divx` | `failures/AVI DivX (2026)/AVI DivX (2026).avi` | 20 s, 640x360 MPEG-4 part 2 video with the DivX tag (`mpeg4`, `-vtag DIVX`) and MP3 audio 440 Hz, in AVI | An AVI container and a codec most mobile hardware does not decode: the fallback path |
| `subs-ass` | `failures/ASS Subtitles (2026)/ASS Subtitles (2026).mkv` | Same as `plain`, plus one ASS track with three styled lines shown 1 s to 19 s: bold (`eng`), coloured yellow, and positioned at the top left with an override tag | Styled subtitles: the track is listed, and its styles or its plain text show, but nothing disappears |
| `uhd-load` | `failures/UHD Load (2026)/UHD Load (2026).mkv` | 10 minutes (600 s), 3840x2160 HEVC 10-bit with HDR10 flags (bt2020 / smpte2084, mastering metadata, as `movie-hdr`), 330 Hz AAC, `testsrc2` at 24 fps. Large, and slow to make: the 4K encode is CPU-bound and runs for a long time on a laptop, so it is made once | The 10-minute battery and thermal test: sustained 4K HDR10 direct play with no transcode. Not for quick checks |

`dv-p8` (Dolby Vision Profile 8) is not generated: ffmpeg cannot encode Dolby Vision, and no free sample exists. Drop a licensed
Profile 8 sample into `failures/DV Profile 8 (2026)/` by hand, named `DV Profile 8 (2026).mkv`. `make-media.sh`
never writes or removes that folder. Profile 8 files usually carry an HDR10 or HLG base layer, so a device without Dolby
Vision should play that base layer and not fail.

Expected app behaviour for the failure fixtures (what to look for when playing them; these follow the
negotiation rules in plan 03 and have not all been confirmed in the app yet):

- `plain`: plays directly, with no skip button and no thumbnails while scrubbing.
- `dts-audio`: direct stream or transcode of the audio if the device cannot decode DTS; never silent playback.
- `mkv-vp9-opus`: direct play where the player supports MKV, VP9 and Opus (Android); on iOS it plays in Aether, also from a download.
- `subs-text`: both tracks are in the picker with their languages; picking one shows the text.
- `languages`: with no language preference it starts on English audio (the default flag) and no subtitles. With
  Audio language German and Subtitle language English (Always) it starts on the 880 Hz German track with "English subtitle".
- `subs-image`: the track is in the picker; picking it needs a transcode that burns it in.
- `truncated`: the player error screen with "Try again", not a crash and not an endless spinner.
- `truehd-hevc`: the video plays directly; the audio plays, or is transcoded, but is never silent.
- `mpeg2-ts`, `avi-divx`: play through a fallback transcode where the device cannot decode the codec, not an error screen.
- `subs-ass`: the track is in the picker; the styled lines show their styles, or their text plainly, and none disappears.
- `uhd-load`: plays at 4K without a transcode on a device that decodes HEVC 10-bit, for the full 10 minutes without stalling.

## Adding a fixture

1. Add it to `make-media.sh`, skipping the file if it already exists (so `start.sh` stays idempotent).
   Generate it with the ffmpeg in the Jellyfin image and pick values you can assert exactly: known
   duration, known chapter times, one distinguishing pattern or tone.
2. Put it under a library path `start.sh` already adds (`shows`, `movies` or `failures`), or add the library in
   `start.sh`. Name files the way Jellyfin parses them (`Name SxxEyy.mkv`, `Name (Year).mkv`).
3. Add a row to the catalogue above: stable name, path, what it contains, and the feature that needs
   it. Update the library table and the header comment of `make-media.sh` too, and add its expected
   properties to `FIXTURES` in `list-fixtures.sh` (the two must agree).
4. Run `scripts/test-server/start.sh` against a fresh `data/` and check the item in Jellyfin with the
   commands under "Checking it", or `scripts/test-server/list-fixtures.sh`.
5. If the fixture exists for a failure case, say which behaviour the app should show for it.

## Media segments: a plugin is needed

Jellyfin 12.2.0 ships **no segment provider** (the plugin list is only metadata providers), so a
fresh server returns no segments. The **Chapter Segments Provider** plugin (5.0.0.0, from the
default "Jellyfin Stable" repository) is enough: it turns chapter names into segments, and Intro
Skipper is not needed for this. `start.sh` installs it through the package API
(`POST /Packages/Installed/Chapter%20Segments%20Provider`, then restarts the container), and runs
the "Media Segment Scan" task. Result for the episodes:

```
Intro  10.0 s - 40.0 s
Outro  150.0 s - 180.02 s
```

Caveat: its name matching is a regex, so a chapter called "Opening" is also treated as an intro.
That is why the first chapter is named `Scene 1`. Names must be real words the plugin knows (`Intro`,
`Credits`); other segment types (Recap, Preview, Commercial) are not produced by chapters.

## Fixtures for the Home category buttons

`make-media.sh` also writes three 5 s movies with a `movie.nfo` each (Jellyfin reads it as local metadata, no online
lookup) and a `tvshow.nfo` for Test Show. After adding them to an existing server run a library refresh
(`POST /Library/Refresh`).

| Title | Year | Genres |
| --- | --- | --- |
| Neon Night (2026) | 2026 | Drama, Thriller |
| HDR Sample (2026) | 2026 | none |
| Quiet Harbor (2025) | 2025 | Drama |
| Test Show | 2024 | Drama |
| Old Classic (1999) | 1999 | Science Fiction |

Filler 01 to 24 (one clip copied, years 2003 to 2026, genres Drama, Thriller, Action and Comedy in turn) fill a page
so it scrolls, has many years to jump between, and the genre counts differ.

So Categories lists Action (6), Comedy (6), Drama (9), Science Fiction (1) and Thriller (7), and "New" lists them
newest added first. (The
`.nfo` files also set studios A24 and Neon; the app has no studio filter now.)

## Page fixtures: series, artwork, people, collection, user data

`make-media.sh` also generates data for the pages the app has (series, detail, Home hero, My List, Continue
watching). Clips are 3 s, 320x180 (one clip copied, so cheap); artwork is one ffmpeg frame (poster 400x600,
backdrop 1280x720, a transparent `logo.png` with two white bars); metadata comes from `.nfo` files with
`<lockdata>true</lockdata>`, so Jellyfin does not look anything up online (without it a name such as "Neon Night"
can match a real title and pull in its artwork). Existing fixtures are unchanged. After adding them to an existing
server run a library refresh (`POST /Library/Refresh`), then the seed script below.

| Fixture | Path under `media/` | What it is | Needed by |
| --- | --- | --- | --- |
| `saga-show` | `shows/Saga Show/` | Series with poster, backdrop, logo, `season01-poster.jpg`, genres Drama and Science Fiction, plot, two cast members. Seasons: Specials (S00E01, S00E02), Season 1 (E01 to E08), Season 2 (E01 to E03) | Series page, season picker, scrolling episode list on phones, Specials, Home hero (backdrop plus logo) |
| `saga-s01` episodes | `Season 01/Saga Show S01E0n.mkv` | "Chapter n". Synopsis on 1, 2 and 4 only; a still (`-thumb.jpg`) on 1, 2 and 5 only; the rest have neither | Episode rows with and without synopsis or still |
| `saga-s00`, `saga-s02` | `Season 00/`, `Season 02/` | "Special 1", "Special 2"; "Return 1" to "Return 3", all with a synopsis, no still | Specials season, a second season |
| `solo-season` | `shows/Solo Season/` | Series with one season ("Only 1", "Only 2"), poster only, no backdrop, no logo | Single-season series (no season picker), Home hero must skip it |
| `starlit-crown` | `movies/Starlit Crown (2026)/` | Poster, backdrop, logo, tagline, plot, PG-13, rating 7.8, genres Drama, Science Fiction and Thriller, director Cleo Marsh, cast Ada Vance, Ben Okoro, Dara Quinn, Eli Park | Movie page with people and genres, Home hero |
| `starlit-crown-2` | `movies/Starlit Crown II (2027)/` | Same artwork and metadata pattern as `starlit-crown` | Second member of the collection |
| `bare-item` | `movies/Bare Item (2026)/` | No poster, backdrop, logo, cast, plot or genres | The no-artwork placeholder. Jellyfin may still make a `Primary` image from a video frame; there is never a backdrop or logo |

Home hero candidates (backdrop and logo): Saga Show, Starlit Crown, Starlit Crown II. Not Solo Season, not Bare Item.

### User data and the collection: `seed-userdata.sh`

Watched state, resume positions, favourites and the box set are stored per user by the server, not in the media
folder, so `scripts/test-server/seed-userdata.sh` sets them through the API as `admin` (it waits, bounded, for the
scan to find the fixtures). It is idempotent: each run sets the same state, nothing toggles, and it also clears the
state on the page fixtures it does not list. It does not touch Test Show or other older fixtures.

```sh
scripts/test-server/start.sh          # generates media, starts, scans (first time)
scripts/test-server/make-media.sh     # only the media; then POST /Library/Refresh on a running server
scripts/test-server/seed-userdata.sh  # user data and the box set; run again any time to reset it
```

| Data | Items |
| --- | --- |
| Box set "Crown Collection" | Starlit Crown, Starlit Crown II |
| Watched | Saga Show S01E01, Bare Item |
| Partly watched (Continue watching) | Saga Show S01E02 at 1.2 s of 3 s (40 %), Neon Night at 2 s of 5 s |
| My List (favourites) | Saga Show, Neon Night, Starlit Crown, Solo Season |

"Show watched titles in rows" (issue #83) needs no new media. Off, Bare Item (a watched movie) leaves Recently added in Movies and
More like this, and Saga Show (one episode of thirteen watched) stays: a show is watched only once every episode is. `/Items/Latest`
already leaves watched titles out for `admin` without `isPlayed`, because the user's `HidePlayedInLatest` is on (Jellyfin's default).

Remove from Continue watching (issue #32) needs no new media. Neon Night is a started movie. Saga Show S01E02 is an episode in
progress: with its position at 0 the server names it again in Next Up, and it must stay gone. For a Next Up card, which has no
Remove, mark `show-e01` watched (`POST /UserPlayedItems/{id}`). `seed-userdata.sh` resets the Saga Show
and Neon Night data, not Test Show's.

## Kids user and rated fixtures

For the Phase 6 profile limits and the Settings PIN. `make-media.sh` writes three more 5 s movies (the Quiet Harbor clip
copied, `.nfo` with `<mpaa>` and `lockdata`); `seed-userdata.sh` creates the kids user and sets its policy through the API
(`POST /Users/New`, then `POST /Users/{id}/Policy`; idempotent, the policy is set again on every run). Existing fixtures are
unchanged. After adding them to an existing server: `POST /Library/Refresh`, then `seed-userdata.sh`.

| Fixture | Where | What it is | Needed by |
| --- | --- | --- | --- |
| user `kids` | server user, password `vibeyfin-kids` (throwaway) | Not an admin. `MaxParentalRating` 10 (PG), `BlockedTags` `no-kids`, one access schedule Everyday 0 to 24 h (open all day, so a test never fails by the clock; edit the schedule in the Jellyfin dashboard to try a closed window) | Profile policy page, rating cap, blocked tag, schedule; the PIN flow |
| `kid-friendly` | `movies/Kid Friendly (2026)/` | Rated G | Visible to `kids` |
| `grown-up-only` | `movies/Grown Up Only (2026)/` | Rated R | Hidden from `kids` (above the cap) |
| `tagged-for-adults` | `movies/Tagged For Adults (2026)/` | Rated G, tag `no-kids` | Hidden from `kids` (blocked tag) |

`admin` is the user without limits. Notes: items with no rating (every older fixture) stay visible to `kids`, because
"block unrated" is off; Starlit Crown and Starlit Crown II are PG-13, so `kids` does not see them either. Level 10 is
named "PG" by the server (`GET /Localization/ParentalRatings`).

Using them with the PIN flow: sign in as `kids` (password `vibeyfin-kids`), open Settings, set a PIN on that profile, then
return to "Who's watching?" and sign in as `kids` again: the Settings tab asks for the PIN (a wrong PIN stays locked, the
right one opens). Home as `kids` lists Kid Friendly but not Grown Up Only or Tagged For Adults; as `admin` it lists all
three. Signing in as `kids` also needs the server's password prompt only once, as for any profile.

## A series that gains an episode (notifications)

For the Phase 6 new-title notifications. `make-media.sh` writes a series with one 3 s episode (the page fixtures' clip
copied, `.nfo` with `lockdata`). A flag adds its second episode later, as a title the server has just added. Existing
fixtures are unchanged.

| Fixture | Where | What it is | Needed by |
| --- | --- | --- | --- |
| `growing-e01` | `shows/Growing Show/Season 01/Growing Show S01E01.mkv` | 3 s, "First Arrival" (`.nfo`); the series "Growing Show" (2026, Drama) | The show the notification is about |
| `growing-e02` | `shows/Growing Show/Season 01/Growing Show S01E02.mkv` | 3 s, no `.nfo`, so the server titles it "Growing Show S01E02". Only after `make-media.sh --add-episode` (copies the file, then `POST /Library/Refresh`); `--remove-episode` deletes it and scans again | The new episode: one notification that opens it |

Checking a notification (Android emulator, `admin`):

1. Follow the show: add Growing Show to My List in the app (or choose "Everything new in the library" on the
   Notifications page). Switch Notifications on and allow the permission.
2. Run the work once before adding the episode (see below). A profile's first poll announces only what was added in the
   last day, so on a server set up today it may announce `growing-e01`; after that it is marked as notified.
3. `scripts/test-server/make-media.sh --add-episode`, wait for the scan (a few seconds), and run the work again: one
   notification "New episode of Growing Show", "Growing Show S01E02". Tapping it opens the episode's page.
4. To repeat: `--remove-episode`, then `--add-episode` again, then switch Notifications off and on. The server derives
   the item id from the path, so the episode comes back with the same id, which the profile has already been notified
   about; switching off forgets that, and the next poll is a first poll, which announces what was added in the last day.

Running the work now: `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler app.vibeyfin <job id>`,
with the id from `adb shell dumpsys jobscheduler | grep "JOB .*app.vibeyfin"` (the number after `u0a…/`). WorkManager
skips a periodic run forced before its period is up ("being executed before schedule" in logcat): the first run happens
when Notifications is switched on, and later ones need the clock moved past the 4 hours. On an emulator image without
root: `adb shell settings put global auto_time 0`, then `adb shell cmd alarm set-time <epoch ms 5 h ahead>`, force the
job, and `adb shell settings put global auto_time 1` afterwards. The work only runs while a profile has Notifications
on and a network.

## Download fixtures: large file, external subtitles, a user who may not download

Added for downloads and offline mode (plan 14). `make-media.sh` creates the media; `seed-userdata.sh` creates the user.

| Fixture | Path under `media/` | What it is | Needed by |
| --- | --- | --- | --- |
| `big-download` | `movies/Big Download (2026)/` | 60 s of random noise, 640x360 H.264 at a very high quality, 700 Hz AAC, about 283 MB (283,037,992 bytes). The server answers `Range` requests with `206` and a `Content-Range` | A download long enough to pause, kill the app, switch the screen off and resume. The emulator's own `network speed` does not slow traffic to the host, so use `throttle-proxy.py` (below) |
| `download-subs` | `movies/Download Subs (2026)/` | 3 s clip with poster, backdrop, logo and one cast member, and two external subtitle files next to it (`.en.srt` "English subtitle", `.de.srt` "Deutscher Untertitel") | That a download keeps the artwork, the cast and the external subtitles, which are separate files on the server (`DeliveryUrl`) |
| `download-languages` | `movies/Download Languages (2026)/` | 10 s 320x180 clip with two audio tracks told apart by pitch (`eng` "English" 440 Hz, the default; `ger` "Deutsch" 880 Hz) and three external subtitle files (`.en.srt` "English subtitle", `.de.srt` "Deutscher Untertitel", `.en.forced.srt` "Forced English subtitle") | That a download plays offline with the tracks the preferred audio and subtitle languages pick (plan 19). Only subtitle files come along with a download, so the Languages fixture's embedded ones can't show this. Its Technical info shows the external files as "English · SUBRIP · External" |
| user `nodownload` | (not a file; created by `seed-userdata.sh`) | A second user, password `vibeyfin-test`, with `EnableContentDownloading` off | `GET /Items/{id}/Download` answers 403 for it; `GET /Users/Me` has `Policy.EnableContentDownloading: false`, so the Download button is hidden |

What the server was seen to do (Jellyfin 12.2, checked while writing plan 14):

- `GET /Items/{id}/Download` with `Range: bytes=1000-1009` answers `206` with `Content-Range: bytes 1000-1009/283037992`. It sends `Accept-Ranges`
  and `Last-Modified`, but **no `ETag`**; the file's version is `MediaSources[0].ETag` in the item. So a resume checks `Last-Modified`.
- The same call without a token is `401`; with the `nodownload` user it is `403`.
- `POST /UserItems/{id}/UserData?userId=` takes `PlaybackPositionTicks`, `Played` and `LastPlayedDate` (`2026-10-20T18:00:00.0000000Z`) and
  the item then reports that `LastPlayedDate`. Leaving `LastPlayedDate` out keeps the old one, so the date a fixture was last given stays.

Issue #31 (download progress and notifications outside the app on iOS) uses `big-download` behind `throttle-proxy.py`: start a download, leave the
app and watch the system progress (iOS 26 and up); let it finish or remove the file on the server to see the finished and failed notifications.

### Slowing a download: `throttle-proxy.py`

`scripts/test-server/throttle-proxy.py <port> <bytes per second>` is a pass-through proxy to the server on `localhost:8196` that sends the
bodies of `/Download` answers at the given speed (`8197 600000` makes `big-download` take about 8 minutes). Everything else passes at full
speed. On the emulator add `http://10.0.2.2:8197` as the server's address (the app tries its best address first, and falls back to the
other when the proxy is stopped). Use it to turn the screen off, force Doze (`adb shell dumpsys deviceidle force-idle`), drop Wi-Fi
(`adb shell svc wifi disable`; reconnect with `adb shell cmd wifi connect-network AndroidWifi open`) or kill the process
(`adb shell run-as app.vibeyfin kill -9 <pid>`) in the middle of a download, and compare the file with the source (`md5sum`).

## Sign-in fixtures: who is listed, a user without a password, a hidden user, Quick Connect

Added for the sign-in pass (#52). `seed-userdata.sh` creates the users; `quick-connect.sh` drives Quick Connect.

| Fixture | What it is | Needed by |
| --- | --- | --- |
| who is listed | Jellyfin hides new users from the sign-in screen, so every seed run lists `admin`, `kids`, `nodownload` and `nopassword` (`Policy.IsHidden` false) and hides `hidden` | "Who's watching?" tiles from `GET /Users/Public` |
| user `nopassword` | Created without a password. Jellyfin 12 still reports `HasPassword: true` for every user, so the app shows its lock and the password sheet; an empty password signs in | The empty-password sign-in |
| user `hidden` | Password `vibeyfin-test`, `IsHidden` true: not in `/Users/Public` | Signing in with "Other user" |
| `quick-connect.sh approve <code> [user]` | Approves a code the app shows (`POST /QuickConnect/Authorize?code=&userId=` as admin), as `[user]` (default `admin`) | Quick Connect sign-in without a second device |
| `quick-connect.sh on` / `off` | Sets `QuickConnectAvailable` in `/System/Configuration` | The Quick Connect tile, and "turned off meanwhile" |
| `quick-connect.sh start` | Plays another device: starts a Quick Connect, prints its code and waits up to 5 minutes, then says who it signed in as | Settings › Quick Connect (approving a code from the app) |

What the server was seen to do (Jellyfin 12.2): with Quick Connect off, `GET /QuickConnect/Enabled` is `false` and `POST /QuickConnect/Initiate`
answers `401`; an approved code makes `GET /QuickConnect/Connect?secret=` report `Authenticated: true`, and
`POST /Users/AuthenticateWithQuickConnect` then signs in as the user who approved it. Approving from the app
(`POST /QuickConnect/Authorize?code=&userId=` with the profile's own id) works for a user who is not an admin; a code the
server never gave out answers `404`, and with Quick Connect off the approval answers `401`, which is why the app asks
`GET /QuickConnect/Enabled` first instead of letting that 401 end the profile's session.

## Seerr: a request server linked to the test Jellyfin

Added for the Seerr integration (plan 20, roadmap Phase 8). `start.sh` runs `seerr.sh` once Jellyfin is up (`SEERR=0 scripts/test-server/start.sh`
skips it; `scripts/test-server/seerr.sh` runs it alone). It is idempotent.

- URL: `http://localhost:5155` (from the Android emulator `http://10.0.2.2:5155`). Port 5155, not Seerr's usual 5055, so a real Seerr can run next to it
- Container: `vibeyfin-seerr`, image `ghcr.io/seerr-team/seerr:v3.5.1`, run as root inside the container so it can write its mounted
  folder; state in `scripts/test-server/data/seerr`
- Setup, through Seerr's API: the first sign-in as the Jellyfin `admin` links Seerr to `http://host.containers.internal:8196` (the host's
  Jellyfin, as the container sees it) and makes `admin` Seerr's administrator; the Shows and Movies libraries are enabled; a full library scan
  matches the library to TMDB
- **Needs an internet connection:** Seerr looks every title up on TMDB (search, discover, details), and so does its scan
- Sign in with any Jellyfin user. A user signing in for the first time gets Seerr's default permission, Request (no 4K, no quota). Quick Connect
  is on in the test Jellyfin, so the app signs in without a password
- Stop with `podman stop vibeyfin-seerr`. Reset with `podman rm -f vibeyfin-seerr && rm -rf scripts/test-server/data/seerr`

Seerr can only match titles that carry a TMDB id, and the other fixtures have none (Seerr logs "Unable to find TMDb ID" for each). These two do,
with `lockdata` so Jellyfin never looks anything up:

| Fixture | Path under `media/` | What it is | Needed by |
| --- | --- | --- | --- |
| `seerr-movie` | `movies/Night of the Living Dead (1968)/` | 3 s clip, `<tmdbid>10331</tmdbid>` (public domain). Seerr shows it as available (`mediaInfo.status` 5, `jellyfinMediaId` the Jellyfin item) | A Seerr title that is in the library: it opens the Jellyfin item, and search leaves it out of "Not in your library" |
| `seerr-show-e01` to `-e03` | `shows/Sherlock/Season 01/Sherlock S01E01.mkv` to `E03` | 3 s clips, the show with `<tmdbid>19885</tmdbid>`, only season 1 (all three of its episodes) | A partly available show: season 1 available, seasons 2 to 4 requestable, Specials off |

What Seerr 3.5.1 was seen to do (checked while writing plan 20):

- The silent sign-in works: `POST /api/v1/auth/jellyfin/quickconnect/initiate` gives `{code, secret}`; the app approves the code with
  `POST /QuickConnect/Authorize?code=&userId=` on Jellyfin with the user's token; `POST .../quickconnect/authenticate {secret}` answers `200` with
  the user and `Set-Cookie: connect.sid=...; Expires=<30 days>`. A code Jellyfin never issued answers `404` there, which is how a Seerr linked to
  another Jellyfin shows up.
- `/auth/me` and the sign-ins carry `jellyfinUserId` (32 hex digits, as Jellyfin writes it) and `displayName`, though the spec leaves them out.
- `GET /api/v1/search?query=` refuses (`400`) any reserved character in the query, even `+` for a space or a bare `'`: it must be percent-encoded.
- `POST /api/v1/request`: `201` with the request; a movie already requested `409`; a show whose asked-for seasons are all requested or
  available `202` `{"message":"No seasons available to request"}`; 4K without the permission `403`. An available movie can still be requested
  (`201`), so the app never offers it.
- A request the requester deletes (`DELETE /api/v1/request/{id}`, `204`) takes the title back to `mediaInfo.status` 1.
- `GET /api/v1/user/{id}/quota` without a limit is `{"movie":{"used":0,"restricted":false},"tv":{...}}` (no `limit`, no `remaining`).

## Checking it

`scripts/test-server/list-fixtures.sh` compares every fixture in the catalogue with what the running
server reports (container, codecs, subtitles, duration, chapters, segments, trickplay) and exits non-zero
on a difference. Trickplay and segments appear after the scan, so it retries for up to a minute.
`--dump` prints the actual properties of every item without checking. By hand:

```sh
URL=http://localhost:8196
AUTH='Authorization: MediaBrowser Client="curl", Device="cli", DeviceId="cli", Version="1"'
TOKEN=$(curl -s -H "$AUTH" -H 'Content-Type: application/json' -X POST $URL/Users/AuthenticateByName \
  -d '{"Username":"admin","Pw":"vibeyfin-test"}' | sed -E 's/.*"AccessToken":"([^"]+)".*/\1/')
AUTH="$AUTH, Token=\"$TOKEN\""
curl -s -H "$AUTH" "$URL/Items?Recursive=true&IncludeItemTypes=Episode,Movie&Fields=Trickplay"
curl -s -H "$AUTH" $URL/MediaSegments/<episodeId>        # Intro + Outro
curl -s -H "$AUTH" "$URL/Items/<hdrId>"                  # VideoRangeType: HDR10
```

Trickplay: each episode lists a `320` px entry (10x10 tiles, one thumbnail per 10 s, 18 thumbnails).
Generation runs after the scan and takes a few seconds for these files. If an item shows none, run
the "Generate Trickplay Images" scheduled task (Dashboard > Scheduled Tasks).
