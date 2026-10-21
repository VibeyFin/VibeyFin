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

## What `start.sh` sets up

The first-run wizard is done through the Startup API (culture en-US, the admin user above, remote
access on). Two libraries, both with trickplay extraction on (`EnableTrickplayImageExtraction` and
`ExtractTrickplayImagesDuringLibraryScan`, set through the library options API):

| Library | Path | Content |
| --- | --- | --- |
| Shows | `/media/shows` | "Test Show" S01E01 to S01E04 (3 min each, 640x360 H.264; four so "Are you still watching?" can be reached with the setting at After 2 episodes: two start by themselves, and the prompt replaces the countdown in the third because a fourth follows; about 8 minutes hands-off) with chapters `Scene 1` 0:00, `Intro` 0:10-0:40, `Episode` 0:40, `Credits` 2:30-end |
| Movies | `/media/movies` | "HDR Sample (2026)": 20 s HEVC 10-bit, bt2020 / smpte2084 with mastering metadata |

`make-media.sh` creates these with the ffmpeg inside the Jellyfin image (no host ffmpeg). Jellyfin
names the first episode "Episode 1" and the HDR movie "HDR" because there is no online metadata
match; the file names are what the scripts own.

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
| `movie-hdr` | `movies/HDR Sample (2026)/HDR Sample (2026).mkv` | 20 s, 1280x720 HEVC 10-bit, bt2020 / smpte2084, mastering metadata, 330 Hz AAC | HDR10 badge, HDR to SDR handling in negotiation |

## Adding a fixture

1. Add it to `make-media.sh`, skipping the file if it already exists (so `start.sh` stays idempotent).
   Generate it with the ffmpeg in the Jellyfin image and pick values you can assert exactly: known
   duration, known chapter times, one distinguishing pattern or tone.
2. Put it under a library path `start.sh` already adds (`shows` or `movies`), or add the library in
   `start.sh`. Name files the way Jellyfin parses them (`Name SxxEyy.mkv`, `Name (Year).mkv`).
3. Add a row to the catalogue above: stable name, path, what it contains, and the feature that needs
   it. Update the library table and the header comment of `make-media.sh` too.
4. Run `scripts/test-server/start.sh` against a fresh `data/` and check the item in Jellyfin with the
   commands under "Checking it".
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

## Checking it

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
