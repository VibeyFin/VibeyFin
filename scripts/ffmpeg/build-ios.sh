#!/bin/sh
# Builds the small FFmpeg the iOS app uses to remux downloads (MKV and friends to MP4) on the device.
# LGPL only: no GPL or nonfree parts, static libraries, no network, no video decoding or encoding (video is copied). Output goes to
# iosApp/Vendor/ffmpeg/<iphoneos|iphonesimulator>/{include,lib}, which is git-ignored; Xcode finds it from there.
#
#   scripts/ffmpeg/build-ios.sh          # builds what is missing
#   scripts/ffmpeg/build-ios.sh --force  # rebuilds everything
#
# Needs Xcode's command line tools. Takes a few minutes the first time. No nasm: both targets are arm64.
set -eu
cd "$(dirname "$0")/../.."

VERSION="n9.0.2"
MIN_IOS="16.0"
ROOT="$PWD"
OUT="$ROOT/iosApp/Vendor/ffmpeg"
WORK="${FFMPEG_WORK:-$ROOT/build/ffmpeg}"
SOURCE="$WORK/src-$VERSION"

[ "${1:-}" = "--force" ] && rm -rf "$OUT" "$WORK/build"

if [ ! -d "$SOURCE" ]; then
  mkdir -p "$WORK"
  git clone --depth 1 --branch "$VERSION" https://github.com/FFmpeg/FFmpeg.git "$SOURCE"
fi

# Only what remuxing needs: read MKV, MP4/MOV, AVI and TS; decode the audio AVPlayer can't play; encode AAC; write MP4.
DEMUXERS="matroska,mov,avi,mpegts"
# The h264 and hevc decoders are never run on frames: opening the start of the stream is how FFmpeg learns how far its frames are
# reordered, which the MP4 writer needs to put the right decode times on them.
DECODERS="h264,hevc,dca,truehd,mlp,opus,vorbis,flac,ac3,eac3,aac,aac_latm,mp3,mp2,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le,pcm_s16be,pcm_s24be,wmav2,alac"
PARSERS="h264,hevc,av1,aac,aac_latm,ac3,dca,flac,opus,vorbis,mpegaudio"
BSFS="av1_metadata,av1_frame_merge,av1_frame_split,aac_adtstoasc,h264_mp4toannexb,hevc_mp4toannexb,extract_extradata"

build() {
  sdk="$1"; target="$2"
  prefix="$OUT/$sdk"
  [ -f "$prefix/lib/libavformat.a" ] && { echo "$sdk: already built"; return; }
  dir="$WORK/build/$sdk"
  rm -rf "$dir"; mkdir -p "$dir"
  sysroot="$(xcrun --sdk "$sdk" --show-sdk-path)"
  flags="-target $target -isysroot $sysroot -Os"
  echo "$sdk: configuring"
  (
    cd "$dir"
    "$SOURCE/configure" \
      --prefix="$prefix" \
      --enable-cross-compile --target-os=darwin --arch=arm64 \
      --cc="xcrun -sdk $sdk clang" --as="xcrun -sdk $sdk clang" --ld="xcrun -sdk $sdk clang" \
      --ar="$(xcrun --sdk "$sdk" -f ar)" --ranlib="$(xcrun --sdk "$sdk" -f ranlib)" --strip="$(xcrun --sdk "$sdk" -f strip)" \
      --extra-cflags="$flags" --extra-ldflags="$flags" \
      --enable-static --disable-shared --enable-pic \
      --disable-programs --disable-doc --disable-debug --disable-autodetect --disable-network \
      --disable-avdevice --disable-avfilter --disable-swscale \
      --disable-everything \
      --enable-zlib \
      --enable-protocol=file \
      --enable-demuxer="$DEMUXERS" \
      --enable-decoder="$DECODERS" \
      --enable-parser="$PARSERS" \
      --enable-bsf="$BSFS" \
      --enable-encoder=aac \
      --enable-muxer=mp4 \
      --enable-swresample
    echo "$sdk: building"
    make -j"$(sysctl -n hw.ncpu)"
    make install
  )
}

# The app also carries Aether's own FFmpeg (a dynamic framework), and this one's static libraries would answer its calls
# first, with this build's missing encoders and decoders. So FFRemux and the libraries are joined into one object whose
# only global symbols are the three ff_ entry points; every av_ and avcodec_ symbol in it turns local.
isolate() {
  sdk="$1"; target="$2"
  prefix="$OUT/$sdk"
  [ -f "$prefix/lib/libffremux.a" ] && [ "$prefix/lib/libffremux.a" -nt "$ROOT/scripts/ffmpeg/FFRemux.c" ] && { echo "$sdk: already isolated"; return; }
  dir="$WORK/isolate/$sdk"
  rm -rf "$dir"; mkdir -p "$dir"
  sysroot="$(xcrun --sdk "$sdk" --show-sdk-path)"
  printf '_ff_remux\n_ff_set_av1_playable\n_ff_can_play_video\n' > "$dir/exports.txt"
  xcrun -sdk "$sdk" clang -target "$target" -isysroot "$sysroot" -Os -fPIC \
    -I"$prefix/include" -I"$ROOT/iosApp/iosApp/Remux" -c "$ROOT/scripts/ffmpeg/FFRemux.c" -o "$dir/FFRemux.o"
  xcrun -sdk "$sdk" clang -target "$target" -isysroot "$sysroot" -nostdlib -r \
    "$dir/FFRemux.o" "$prefix/lib/libavformat.a" "$prefix/lib/libavcodec.a" "$prefix/lib/libswresample.a" "$prefix/lib/libavutil.a" \
    -o "$dir/combined.o"
  xcrun nmedit -s "$dir/exports.txt" "$dir/combined.o"
  rm -f "$prefix/lib/libffremux.a"
  xcrun -sdk "$sdk" ar rcs "$prefix/lib/libffremux.a" "$dir/combined.o"
  xcrun -sdk "$sdk" ranlib "$prefix/lib/libffremux.a"
}

build iphoneos "arm64-apple-ios$MIN_IOS"
build iphonesimulator "arm64-apple-ios$MIN_IOS-simulator"
isolate iphoneos "arm64-apple-ios$MIN_IOS"
isolate iphonesimulator "arm64-apple-ios$MIN_IOS-simulator"

echo "FFmpeg $VERSION is in $OUT"
