#!/usr/bin/env bash
# Generates test media with the ffmpeg inside the Jellyfin image (nothing needed on the host).
#   shows/Test Show/Season 01/Test Show S01E01.mkv   3 min, chapters Intro 0:10-0:40, Credits 2:30-end
#   shows/Test Show/Season 01/Test Show S01E02.mkv   second episode, same chapters
#   shows/Test Show/Season 01/Test Show S01E03.mkv   third and fourth: with "After 2 episodes" the still-watching
#   shows/Test Show/Season 01/Test Show S01E04.mkv   prompt appears in the third, because a fourth follows it
#   movies/HDR Sample (2026)/HDR Sample (2026).mkv   20 s HEVC HDR10 (10-bit, bt2020 / smpte2084)
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
echo "Media ready under ${MEDIA}"
