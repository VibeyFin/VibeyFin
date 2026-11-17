# Third-party notices

VibeyFin's own source is under the [Mozilla Public License 2.0](LICENSE). The apps also use the
components below, each under its own licence. Versions are pinned in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) and in the iOS project's Swift package
list.

## Android

| Component | Licence |
|---|---|
| AndroidX (Compose, Material 3, Navigation, Lifecycle, Room, DataStore, WorkManager, Media3) | Apache-2.0 |
| Coil, Ktor, Koin, kotlinx.coroutines, kotlinx.serialization, OkHttp | Apache-2.0 |
| [`org.jellyfin.media3:media3-ffmpeg-decoder`](https://github.com/jellyfin/jellyfin-androidx-media) (DTS, TrueHD and AC-3 audio decoding) | **GPL-3.0** |

The Jellyfin decoder is published under the GNU General Public License v3.0. An Android build that
includes it is distributed under the terms of that licence, and its source is this repository. MPL-2.0
is compatible with the GPL, so the two combine in one APK.

## iOS

| Component | Licence | Notes |
|---|---|---|
| [AetherEngine](https://github.com/superuser404notfound/AetherEngine) 7.56.0 | LGPL-3.0 with an App Store exception | Changes to the engine itself stay LGPL |
| FFmpeg (libavcodec, libavformat, libavutil, libswresample, libswscale, libavfilter), from [FFmpegBuild](https://github.com/superuser404notfound/FFmpegBuild) | LGPL-2.1-or-later | Linked dynamically, so the app can be relinked against another build |
| dav1d | BSD-2-Clause | Part of FFmpegBuild |
| zimg | WTFPL | Part of FFmpegBuild |
| libzvbi | LGPL-2.0-or-later | Part of FFmpegBuild |
| libdovi, from quietvoid's [dovi_tool](https://github.com/quietvoid/dovi_tool) | MIT | Copyright quietvoid and contributors |
| Anime4K kernels | MIT | Copyright bloc97 |

AetherEngine's own licensing notes, which list the exact obligations for each component, are in its
[`docs/licensing.md`](https://github.com/superuser404notfound/AetherEngine/blob/main/docs/licensing.md).
The full licence text of each component is in its upstream repository.

## Not covered by MPL-2.0

The VibeyFin name and logo, and the files in `design/logos/`, are not licensed for reuse.
