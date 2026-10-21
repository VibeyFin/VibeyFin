package com.liamgleeson.vibeyfin.domain

import com.liamgleeson.vibeyfin.data.api.MediaSegmentDto

/** What a marked part of an item is. */
enum class SegmentType { Intro, Outro, Recap, Preview, Commercial, Unknown }

/** A part of an item the server has marked, from [startMs] to [endMs]. */
data class MediaSegment(val type: SegmentType, val startMs: Long, val endMs: Long)

internal fun MediaSegmentDto.toMediaSegment(): MediaSegment = MediaSegment(
    type = when (type) {
        "Intro" -> SegmentType.Intro
        "Outro" -> SegmentType.Outro
        "Recap" -> SegmentType.Recap
        "Preview" -> SegmentType.Preview
        "Commercial" -> SegmentType.Commercial
        else -> SegmentType.Unknown
    },
    startMs = startTicks / TICKS_PER_MILLISECOND,
    endMs = endTicks / TICKS_PER_MILLISECOND,
)
