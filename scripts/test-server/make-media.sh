#!/usr/bin/env bash
# Generates test media with the ffmpeg inside the Jellyfin image (nothing needed on the host).
#   shows/Test Show/Season 01/Test Show S01E01.mkv   3 min, chapters Intro 0:10-0:40, Credits 2:30-end
#   shows/Test Show/Season 01/Test Show S01E02.mkv   second episode, same chapters
#   shows/Test Show/Season 01/Test Show S01E03.mkv   third and fourth: with "After 2 episodes" the still-watching
#   shows/Test Show/Season 01/Test Show S01E04.mkv   prompt appears in the third, because a fourth follows it
#   movies/HDR Sample (2026)/HDR Sample (2026).mkv   20 s HEVC HDR10 (10-bit, bt2020 / smpte2084)
#   movies/Neon Night (2026), Quiet Harbor (2025), Old Classic (1999)   5 s clips with a movie.nfo each, for the Home
#     category buttons: genres Drama, Thriller and Science Fiction, and release years 2026 to 1999 for "New".
#     Jellyfin reads the .nfo as local metadata (no online lookup).
#   movies/Filler 01 (2003) ... Filler 24 (2026)   copies of one clip, genres in turn, to fill a category page
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
# Home category buttons (docs/test-server.md "Fixtures"): small clips with studio and genre set through an .nfo.
nfo_movie() { # $1 = folder name, $2 = title, $3 = year, $4 = studio, $5.. = genres
  local name="$1" title="$2" year="$3" studio="$4"; shift 4
  local dir="${MEDIA}/movies/${name}"
  mkdir -p "$dir"
  if [ ! -f "${dir}/${name}.mkv" ]; then
    ff -f lavfi -i "testsrc2=size=640x360:rate=24:duration=5" -f lavfi -i "sine=frequency=520:duration=5" \
       -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac -b:a 64k -shortest "/data/media/movies/${name}/${name}.mkv"
  fi
  {
    echo "<movie><title>${title}</title><year>${year}</year><studio>${studio}</studio>"
    for genre in "$@"; do echo "<genre>${genre}</genre>"; done
    echo "</movie>"
  } > "${dir}/movie.nfo"
}
nfo_movie "Neon Night (2026)" "Neon Night" 2026 "A24" "Drama" "Thriller"
nfo_movie "Quiet Harbor (2025)" "Quiet Harbor" 2025 "Neon" "Drama"
nfo_movie "Old Classic (1999)" "Old Classic" 1999 "Neon" "Science Fiction"
touch -t "$(date -v-60d +%Y%m%d%H%M 2>/dev/null || date -d '60 days ago' +%Y%m%d%H%M)" "${MEDIA}/movies/Old Classic (1999)/Old Classic (1999).mkv"
cat > "${MEDIA}/shows/Test Show/tvshow.nfo" <<'NFO'
<tvshow><title>Test Show</title><studio>A24</studio><genre>Drama</genre></tvshow>
NFO
# Filler: 24 more movies (one clip copied, so cheap), 2003 to 2026, genres Drama, Thriller, Action and Comedy in turn.
# Enough for a category page to scroll, to jump between years, and for the genre sheet's counts to differ.
genres=(Drama Thriller Action Comedy)
for n in $(seq 1 24); do
  year=$((2002 + n)); genre="${genres[$(((n - 1) % 4))]}"; name="Filler $(printf '%02d' "$n") ($year)"
  dir="${MEDIA}/movies/${name}"
  mkdir -p "$dir"
  [ -f "${dir}/${name}.mkv" ] || cp "${MEDIA}/movies/Quiet Harbor (2025)/Quiet Harbor (2025).mkv" "${dir}/${name}.mkv"
  echo "<movie><title>Filler $(printf '%02d' "$n")</title><year>${year}</year><genre>${genre}</genre></movie>" > "${dir}/movie.nfo"
done
# A title long enough to wrap onto two lines in a poster tile, to check a row keeps one height as it scrolls.
nfo_movie "The Extraordinarily Long Title Of A Film (2026)" "The Extraordinarily Long Title Of A Film That Keeps Going" 2026 "A24" "Drama"
echo "Media ready under ${MEDIA}"
