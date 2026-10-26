#ifndef FFRemux_h
#define FFRemux_h

#include <stddef.h>

/// What `ff_remux` did. Matches the codes `NativeVideoConverter` in `shared` expects, in this order.
typedef enum {
    FFRemuxNotNeeded = 0,
    FFRemuxConverted = 1,
    FFRemuxUnsupportedVideo = 2,
    FFRemuxStopped = 3,
    FFRemuxFailed = 4,
} FFRemuxResult;

/// Called with how far the work is, 0 to 1. Returns non-zero to stop.
typedef int (*FFRemuxProgress)(void *context, double fraction);

/// Says whether this device decodes AV1 (only chips with a hardware decoder do). Until told, AV1 counts as unplayable.
void ff_set_av1_playable(int playable);

/// Whether AVPlayer decodes a video stream of this codec name (`h264`, `hevc`, ...).
int ff_can_play_video(const char *codec_name);

/// Writes an MP4 AVPlayer can open from the file at `source` into `target`, when the file needs it: the video is copied, audio AVPlayer
/// can play (AAC, AC-3, E-AC-3, ALAC, MP3) is copied, any other audio is decoded and written as AAC, subtitles are left out (they are
/// separate files). Returns `FFRemuxNotNeeded` and writes nothing when the file is an MP4 or MOV with only such streams.
FFRemuxResult ff_remux(const char *source, const char *target, FFRemuxProgress progress, void *context, char *message, size_t message_size);

#endif
