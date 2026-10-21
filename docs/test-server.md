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
