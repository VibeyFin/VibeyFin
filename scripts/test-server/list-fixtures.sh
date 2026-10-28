#!/usr/bin/env bash
# Lists every fixture with the Jellyfin item properties it should have, next to what the running
# server reports, and exits non-zero when one differs. Trickplay and segments are generated after
# the scan, so a mismatch is retried for up to a minute before it counts.
#   list-fixtures.sh          check against the expectations below
#   list-fixtures.sh --dump   print the actual properties of every item, no checking
# The expectations are the catalogue in docs/test-server.md; keep the two in step.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
export JF_URL JF_USER JF_PASS AUTH_HDR

exec python3 -I - "$@" <<'PY'
import json, os, sys, time, urllib.request

URL, USER, PASS = os.environ["JF_URL"], os.environ["JF_USER"], os.environ["JF_PASS"]
AUTH = os.environ["AUTH_HDR"].split(": ", 1)[1]


def call(path, token=None, body=None):
    headers = {"Authorization": AUTH + (f', Token="{token}"' if token else ""), "Content-Type": "application/json"}
    data = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(urllib.request.Request(URL + path, data=data, headers=headers), timeout=30) as r:
        return json.load(r)


# name -> (path under /media, expected properties). Only the keys listed for a fixture are checked.
EPISODE = dict(container="mkv", video="h264", audio=["aac"], subtitles=[], seconds=180,
               chapters=4, segments=["Intro", "Outro"], trickplay=True)
CLIP = dict(container="mkv", video="h264", audio=["aac"], subtitles=[], seconds=20,
            chapters=0, segments=[], trickplay=False)
FIXTURES = {
    "show-e01": ("shows/Test Show/Season 01/Test Show S01E01.mkv", EPISODE),
    "show-e02": ("shows/Test Show/Season 01/Test Show S01E02.mkv", EPISODE),
    "show-e03": ("shows/Test Show/Season 01/Test Show S01E03.mkv", EPISODE),
    "show-e04": ("shows/Test Show/Season 01/Test Show S01E04.mkv", EPISODE),
    "movie-hdr": ("movies/HDR Sample (2026)/HDR Sample (2026).mkv",
                  dict(CLIP, video="hevc", trickplay=True, range="HDR10")),
    "plain": ("failures/Plain Clip (2026)/Plain Clip (2026).mkv", CLIP),
    "dts-audio": ("failures/DTS Audio (2026)/DTS Audio (2026).mkv", dict(CLIP, audio=["dts"])),
    "mkv-vp9-opus": ("failures/MKV VP9 Opus (2026)/MKV VP9 Opus (2026).mkv", dict(CLIP, video="vp9", audio=["opus"])),
    "subs-text": ("failures/Text Subtitles (2026)/Text Subtitles (2026).mkv", dict(CLIP, subtitles=["subrip", "subrip"])),
    "subs-image": ("failures/Image Subtitles (2026)/Image Subtitles (2026).mkv", dict(CLIP, subtitles=["PGSSUB"])),
    "languages": ("failures/Languages (2026)/Languages (2026).mkv",
                  dict(CLIP, audio=["aac", "aac"], subtitles=["subrip", "subrip", "subrip"])),
    "big-download": ("movies/Big Download (2026)/Big Download (2026).mkv",
                     dict(CLIP, seconds=60, trickplay=True)),  # the Movies library makes trickplay
    "download-subs": ("movies/Download Subs (2026)/Download Subs (2026).mkv",
                      dict(CLIP, seconds=3, subtitles=["subrip", "subrip"])),  # external .srt files; too short for trickplay
    "download-languages": ("movies/Download Languages (2026)/Download Languages (2026).mkv",
                           dict(CLIP, seconds=10, audio=["aac", "aac"], subtitles=["subrip", "subrip", "subrip"], trickplay=True)),  # external .srt files
    "truncated": ("failures/Truncated (2026)/Truncated (2026).mp4",
                  dict(container="mp4", video=None, audio=[], subtitles=[], seconds=0, chapters=0,
                       segments=[], trickplay=False)),  # in the library, but nothing can be read from it
}


def actual(token, item):
    source = (item.get("MediaSources") or [{}])[0]
    streams = item.get("MediaStreams") or []
    video = next((s for s in streams if s["Type"] == "Video"), {})
    segments = sorted(s["Type"] for s in call(f"/MediaSegments/{item['Id']}", token).get("Items", []))
    return dict(
        container=source.get("Container"),
        video=video.get("Codec"),
        audio=[s.get("Codec") for s in streams if s["Type"] == "Audio"],
        subtitles=[s.get("Codec") for s in streams if s["Type"] == "Subtitle"],
        seconds=round((item.get("RunTimeTicks") or 0) / 1e7),
        chapters=len(item.get("Chapters") or []),
        segments=segments,
        trickplay=bool(item.get("Trickplay")),
        range=video.get("VideoRangeType"),
    )


def differences(want, got):
    out = []
    for key, expected in want.items():
        if key == "seconds":
            ok = abs(got[key] - expected) <= 1
        else:
            ok = got[key] == expected
        if not ok:
            out.append(f"{key}: expected {expected!r}, got {got[key]!r}")
    return out


def fetch(token):
    fields = "Path,MediaStreams,MediaSources,Chapters,Trickplay"
    items = call(f"/Items?Recursive=true&IncludeItemTypes=Episode,Movie&Fields={fields}", token)["Items"]
    return {i["Path"].split("/media/", 1)[1]: i for i in items}


token = call("/Users/AuthenticateByName", body={"Username": USER, "Pw": PASS})["AccessToken"]

if "--dump" in sys.argv:
    for path, item in sorted(fetch(token).items()):
        print(f"{path}\n  {actual(token, item)}")
    sys.exit(0)

deadline = time.time() + 60
while True:
    items = fetch(token)
    report, failed = [], False
    for name, (path, want) in FIXTURES.items():
        item = items.get(path)
        if item is None:
            report.append((name, path, "missing from the server", ["not in the library"]))
            failed = True
            continue
        problems = differences(want, actual(token, item))
        failed = failed or bool(problems)
        report.append((name, path, want, problems))
    if not failed or time.time() > deadline:
        break
    time.sleep(3)

for name, path, want, problems in report:
    print(f"{'FAIL' if problems else 'ok  '} {name}  ({path})")
    print(f"       expects {want}")
    for p in problems:
        print(f"       - {p}")
sys.exit(1 if failed else 0)
PY
