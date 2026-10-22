#!/usr/bin/env bash
# Generates test media with the ffmpeg inside the Jellyfin image (nothing needed on the host).
#   shows/Test Show/Season 01/Test Show S01E01.mkv   3 min, chapters Intro 0:10-0:40, Credits 2:30-end
#   shows/Test Show/Season 01/Test Show S01E02.mkv   second episode, same chapters
#   shows/Test Show/Season 01/Test Show S01E03.mkv   third and fourth: with "After 2 episodes" the still-watching
#   shows/Test Show/Season 01/Test Show S01E04.mkv   prompt appears in the third, because a fourth follows it
#   movies/HDR Sample (2026)/HDR Sample (2026).mkv   20 s HEVC HDR10 (10-bit, bt2020 / smpte2084)
#   failures/<Name> (2026)/<Name> (2026).mkv         20 s clips with one thing a player may refuse: Plain Clip (the
#                                                    baseline), DTS Audio, MKV VP9 Opus, Text Subtitles, Image Subtitles
#   failures/Truncated (2026)/Truncated (2026).mp4   an MP4 cut in half: no moov atom, nothing can be read from it
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

MEDIA="${DATA}/media"
EP_DIR="${MEDIA}/shows/Test Show/Season 01"
HDR_DIR="${MEDIA}/movies/HDR Sample (2026)"
mkdir -p "$EP_DIR" "$HDR_DIR" "${DATA}/work"

ff() {
  podman run --rm -v "${DATA}:/data" --entrypoint /usr/lib/jellyfin-ffmpeg/ffmpeg "$JF_IMAGE" -hide_banner -loglevel error -y "$@"
}

# Chapter metadata, in milliseconds.
cat > "${DATA}/work/chapters.txt" <<'META'
;FFMETADATA1
[CHAPTER]
TIMEBASE=1/1000
START=0
END=10000
title=Scene 1
[CHAPTER]
TIMEBASE=1/1000
START=10000
END=40000
title=Intro
[CHAPTER]
TIMEBASE=1/1000
START=40000
END=150000
title=Episode
[CHAPTER]
TIMEBASE=1/1000
START=150000
END=180000
title=Credits
META

episode() { # $1 = output file name, $2 = test pattern
  [ -f "${EP_DIR}/$1" ] && return 0
  ff -f lavfi -i "${2}=size=640x360:rate=24:duration=180" \
     -f lavfi -i "sine=frequency=440:duration=180" \
     -i /data/work/chapters.txt -map_metadata 2 -map_chapters 2 -map 0:v -map 1:a \
     -c:v libx264 -preset veryfast -pix_fmt yuv420p -g 48 -c:a aac -b:a 64k -shortest \
     "/data/media/shows/Test Show/Season 01/$1"
}
episode "Test Show S01E01.mkv" testsrc2
episode "Test Show S01E02.mkv" smptebars
episode "Test Show S01E03.mkv" rgbtestsrc
episode "Test Show S01E04.mkv" smptehdbars

if [ ! -f "${HDR_DIR}/HDR Sample (2026).mkv" ]; then
  ff -f lavfi -i "testsrc2=size=1280x720:rate=24:duration=20,format=yuv420p10le,setparams=color_primaries=bt2020:color_trc=smpte2084:colorspace=bt2020nc:range=tv" \
     -f lavfi -i "sine=frequency=330:duration=20" \
     -c:v libx265 -preset veryfast -pix_fmt yuv420p10le \
     -x265-params "hdr10=1:repeat-headers=1:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400" \
     -tag:v hvc1 -c:a aac -b:a 64k -shortest \
     "/data/media/movies/HDR Sample (2026)/HDR Sample (2026).mkv"
fi
# Failure fixtures: 20 s clips, no chapters (so no segments), in the Failures library (no trickplay).
failure() { # $1 = title, $2 = extension, rest = ffmpeg arguments after the two generated inputs
  local title="$1" ext="$2" out
  shift 2
  out="failures/${title} (2026)/${title} (2026).${ext}"
  [ -f "${MEDIA}/${out}" ] && return 0
  mkdir -p "$(dirname "${MEDIA}/${out}")"
  ff -f lavfi -i "testsrc2=size=640x360:rate=24:duration=20" \
     -f lavfi -i "sine=frequency=440:duration=20" "$@" -t 20 "/data/media/${out}"
}
H264=(-c:v libx264 -preset veryfast -pix_fmt yuv420p)

# Two text subtitle tracks, and one image (PGS) track: ffmpeg cannot encode PGS, so a .sup with a
# single white bar from 1 s to 15 s is written by hand (segments: PCS, WDS, PDS, ODS, END).
printf '1\n00:00:01,000 --> 00:00:15,000\nEnglish subtitle\n' > "${DATA}/work/english.srt"
printf '1\n00:00:01,000 --> 00:00:15,000\nDeutscher Untertitel\n' > "${DATA}/work/german.srt"
python3 -I - "${DATA}/work/image.sup" <<'SUP'
import struct, sys

def segment(ms, kind, payload):
    return b"PG" + struct.pack(">IIBH", ms * 90, 0, kind, len(payload)) + payload

W, H, X, Y = 60, 16, 290, 300
rle = (b"\x00" + bytes([0x80 | W, 1]) + b"\x00\x00") * H  # W pixels of colour 1, then end of line
out = b""
for ms, shown in ((1000, True), (15000, False)):
    out += segment(ms, 0x16, struct.pack(">HHBHBBBB", 640, 360, 0x10, 1 if shown else 2, 0x80, 0, 0, 1 if shown else 0)
                   + (struct.pack(">HBBHH", 0, 0, 0, X, Y) if shown else b""))
    if shown:
        out += segment(ms, 0x17, struct.pack(">BBHHHH", 1, 0, X, Y, W, H))
        out += segment(ms, 0x14, struct.pack(">BB", 0, 0) + bytes([0, 16, 128, 128, 0]) + bytes([1, 235, 128, 128, 255]))
        out += segment(ms, 0x15, struct.pack(">HBB", 0, 0, 0xC0) + struct.pack(">I", len(rle) + 4)[1:]
                       + struct.pack(">HH", W, H) + rle)
    out += segment(ms, 0x80, b"")
open(sys.argv[1], "wb").write(out)
SUP

failure "Plain Clip" mkv "${H264[@]}" -c:a aac -b:a 64k
failure "DTS Audio" mkv "${H264[@]}" -c:a dts -strict -2
failure "MKV VP9 Opus" mkv -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -c:a libopus
failure "Text Subtitles" mkv -i /data/work/english.srt -i /data/work/german.srt \
  -map 0:v -map 1:a -map 2 -map 3 "${H264[@]}" -c:a aac -b:a 64k -c:s srt \
  -metadata:s:s:0 language=eng -metadata:s:s:1 language=deu
failure "Image Subtitles" mkv -i /data/work/image.sup \
  -map 0:v -map 1:a -map 2 "${H264[@]}" -c:a aac -b:a 64k -c:s copy

# An MP4 written normally (moov atom at the end) and cut in half: nothing can be read from it.
TRUNCATED="${MEDIA}/failures/Truncated (2026)/Truncated (2026).mp4"
if [ ! -f "$TRUNCATED" ]; then
  mkdir -p "$(dirname "$TRUNCATED")"
  ff -f lavfi -i "testsrc2=size=640x360:rate=24:duration=20" -f lavfi -i "sine=frequency=440:duration=20" \
     "${H264[@]}" -c:a aac -b:a 64k -shortest /data/work/truncated.mp4
  head -c "$(( $(wc -c < "${DATA}/work/truncated.mp4") / 2 ))" "${DATA}/work/truncated.mp4" > "$TRUNCATED"
fi
echo "Media ready under ${MEDIA}"
