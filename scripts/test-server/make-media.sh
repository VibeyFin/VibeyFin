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
#   failures/<Name> (2026)/<Name> (2026).mkv         20 s clips with one thing a player may refuse: Plain Clip (the
#                                                    baseline), DTS Audio, MKV VP9 Opus, Text Subtitles, Image Subtitles
#   failures/Truncated (2026)/Truncated (2026).mp4   an MP4 cut in half: no moov atom, nothing can be read from it
#   Page fixtures (3 s, 320x180 clips, small artwork, .nfo with lockdata): shows/Saga Show (Specials, Season 1 with
#     8 episodes, Season 2; backdrop and logo), shows/Solo Season (one season, poster only), movies/Starlit Crown
#     (2026) and II (2027) (artwork, cast, genres), movies/Bare Item (2026) (no artwork). User data: seed-userdata.sh
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

# ---- Page fixtures (docs/test-server.md "Page fixtures"): series, artwork, people, collection ----
# Tiny clips (3 s, 320x180, one clip copied many times), small artwork, metadata in .nfo files.
MINI="${DATA}/work/mini.mkv"
if [ ! -f "$MINI" ]; then
  ff -f lavfi -i "testsrc2=size=320x180:rate=12:duration=3" -f lavfi -i "sine=frequency=600:duration=3" \
     "${H264[@]}" -c:a aac -b:a 32k -shortest /data/work/mini.mkv
fi
clip() { mkdir -p "$(dirname "$1")"; [ -f "$1" ] || cp "$MINI" "$1"; }
# image <out relative to media> <lavfi source>: one JPEG frame. Skipped when it exists.
image() {
  [ -f "${MEDIA}/$1" ] && return 0
  mkdir -p "$(dirname "${MEDIA}/$1")"
  ff -f lavfi -i "$2" -frames:v 1 -update 1 -q:v 6 "/data/media/$1"
}
# logo <out relative to media>: transparent PNG with white bars (a stand-in wordmark).
logo() {
  [ -f "${MEDIA}/$1" ] && return 0
  mkdir -p "$(dirname "${MEDIA}/$1")"
  ff -f lavfi -i "color=c=black@0.0:s=600x200,format=rgba,drawbox=x=20:y=40:w=560:h=40:color=white@1:t=fill,drawbox=x=120:y=110:w=360:h=40:color=white@1:t=fill" \
     -frames:v 1 -update 1 "/data/media/$1"
}
artwork() { # $1 = folder relative to media, $2 = seed pattern (poster, backdrop, logo)
  image "$1/poster.jpg" "${2}=size=400x600:rate=1"
  image "$1/backdrop.jpg" "${2}=size=1280x720:rate=1"
  logo "$1/logo.png"
}

# Saga Show: Specials (season 0, 2 episodes), Season 1 (8 episodes, so a phone list scrolls), Season 2 (3 episodes).
# Poster, backdrop and logo (qualifies for the Home hero). Season 1 poster too. Episodes 1, 2, 5 of season 1 have a
# still; 1, 2, 4 have a synopsis; the others have neither.
SAGA="${MEDIA}/shows/Saga Show"
mkdir -p "$SAGA"
artwork "shows/Saga Show" testsrc2
image "shows/Saga Show/season01-poster.jpg" "smptebars=size=400x600:rate=1"
cat > "${SAGA}/tvshow.nfo" <<'NFO'
<tvshow><title>Saga Show</title><lockdata>true</lockdata><year>2024</year><studio>Neon</studio><genre>Drama</genre><genre>Science Fiction</genre>
<plot>A long-running saga with specials and two seasons, to exercise the series page, season picker and episode list.</plot>
<actor><name>Ada Vance</name><role>Captain Mira</role><type>Actor</type></actor>
<actor><name>Ben Okoro</name><role>Dr. Hale</role><type>Actor</type></actor></tvshow>
NFO
episode_nfo() { # $1 = file base (no extension), $2 = title, $3 = season, $4 = episode, $5 = plot (may be empty)
  local plot=""
  [ -n "$5" ] && plot="<plot>$5</plot>"
  echo "<episodedetails><title>$2</title><lockdata>true</lockdata><season>$3</season><episode>$4</episode>${plot}</episodedetails>" > "${1}.nfo"
}
for n in 1 2; do
  clip "${SAGA}/Season 00/Saga Show S00E0${n}.mkv"
  episode_nfo "${SAGA}/Season 00/Saga Show S00E0${n}" "Special ${n}" 0 "$n" "Behind the scenes, part ${n}."
done
for n in 1 2 3 4 5 6 7 8; do
  base="${SAGA}/Season 01/Saga Show S01E0${n}"
  clip "${base}.mkv"
  case "$n" in
    1|2|4) episode_nfo "$base" "Chapter ${n}" 1 "$n" "Synopsis of chapter ${n}: the crew faces a new problem and a longer sentence follows so the text can wrap on a phone." ;;
    *) episode_nfo "$base" "Chapter ${n}" 1 "$n" "" ;;
  esac
  case "$n" in 1|2|5) image "shows/Saga Show/Season 01/Saga Show S01E0${n}-thumb.jpg" "testsrc2=size=640x360:rate=1" ;; esac
done
for n in 1 2 3; do
  base="${SAGA}/Season 02/Saga Show S02E0${n}"
  clip "${base}.mkv"
  episode_nfo "$base" "Return ${n}" 2 "$n" "Season two, episode ${n}."
done

# Solo Season: a series with a single season of two episodes, poster only (no backdrop, no logo).
SOLO="${MEDIA}/shows/Solo Season"
mkdir -p "$SOLO"
echo "<tvshow><title>Solo Season</title><lockdata>true</lockdata><year>2025</year><genre>Comedy</genre><plot>One season only.</plot></tvshow>" > "${SOLO}/tvshow.nfo"
image "shows/Solo Season/poster.jpg" "rgbtestsrc=size=400x600:rate=1"
for n in 1 2; do
  clip "${SOLO}/Season 01/Solo Season S01E0${n}.mkv"
  episode_nfo "${SOLO}/Season 01/Solo Season S01E0${n}" "Only ${n}" 1 "$n" ""
done

# Starlit Crown (2026) and Starlit Crown II (2027): poster, backdrop, logo, cast, director, genres, plot.
# Both go into the "Crown Collection" box set (made by seed-userdata.sh through the API).
crown() { # $1 = folder name, $2 = title, $3 = year, $4 = pattern
  local dir="${MEDIA}/movies/$1"
  clip "${dir}/$1.mkv"
  artwork "movies/$1" "$4"
  cat > "${dir}/movie.nfo" <<NFO
<movie><title>$2</title><lockdata>true</lockdata><year>$3</year><studio>A24</studio><genre>Drama</genre><genre>Science Fiction</genre><genre>Thriller</genre>
<tagline>Every crown has a cost.</tagline><plot>A rich test movie with a plot, a tagline, a director and a cast, for the movie detail page.</plot>
<mpaa>PG-13</mpaa><rating>7.8</rating><director>Cleo Marsh</director>
<actor><name>Ada Vance</name><role>Queen Ilsa</role><type>Actor</type><order>0</order></actor>
<actor><name>Ben Okoro</name><role>Marshal Teo</role><type>Actor</type><order>1</order></actor>
<actor><name>Dara Quinn</name><role>The Envoy</role><type>Actor</type><order>2</order></actor>
<actor><name>Eli Park</name><role>Page</role><type>Actor</type><order>3</order></actor></movie>
NFO
}
crown "Starlit Crown (2026)" "Starlit Crown" 2026 smptehdbars
crown "Starlit Crown II (2027)" "Starlit Crown II" 2027 rgbtestsrc

# Bare Item: a movie with no artwork of any kind (no poster, backdrop or logo), no cast.
BARE="${MEDIA}/movies/Bare Item (2026)"
clip "${BARE}/Bare Item (2026).mkv"
echo "<movie><title>Bare Item</title><lockdata>true</lockdata><year>2026</year></movie>" > "${BARE}/movie.nfo"

echo "Media ready under ${MEDIA}"
