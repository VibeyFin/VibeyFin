package app.vibeyfin.domain

import app.vibeyfin.data.db.DOWNLOAD_DONE
import app.vibeyfin.data.db.DownloadDao
import app.vibeyfin.data.db.DownloadEntity
import app.vibeyfin.data.db.DownloadItemEntity
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.domain.model.SearchKind
import app.vibeyfin.domain.model.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A downloaded item ready to play from the device: the item as saved, with where the user got to, and the plan the player opens. */
class LocalPlayable(val item: MediaItem, val plan: PlaybackPlan)

/**
 * What is on the device, as the screens and the player read it without the server: the downloaded movies and episodes with their
 * pictures, where the profile got to in each, what to continue with, what to play next, and a search of the lot. Only downloads
 * that are done count, and only if their file is still there.
 */
class OfflineLibrary(
    private val dao: DownloadDao,
    private val files: DownloadFiles,
    private val languages: LanguagePreferences,
    private val capabilities: PlaybackCapabilitiesSource,
) {
    /** The downloaded [itemId] ready to play, or null when it is not (fully) on the device. */
    suspend fun playable(profile: ProfileRef, itemId: String): LocalPlayable? {
        val row = dao.find(profile.serverId, profile.userId, itemId)?.takeIf { it.isPlayable() } ?: return null
        val saved = dao.findItem(profile.serverId, profile.userId, itemId) ?: return null
        val snapshot = OfflineSnapshot.decode(saved.snapshotJson)
        val item = snapshot.toItem(saved)
        // Only the subtitle tracks that came as files can be shown from the device; an image or embedded one cannot.
        val withFiles = snapshot.subtitles.map { it.index }.toSet()
        val subtitleTracks = snapshot.subtitleTracks.filter { it.index in withFiles }
        // The profile's language preferences pick the tracks, as the server would; without them, the ones it picked when downloading.
        val prefs = languages.saved(profile)?.takeIf { snapshot.tracksDescribed }
        val choice = prefs?.let { chooseTracks(snapshot.audioTracks, subtitleTracks, it) }
            ?: TrackChoice(snapshot.audioTracks.firstOrNull { it.selected }?.index, subtitleTracks.firstOrNull { it.selected }?.index)
        val plan = PlaybackPlan(
            itemId = itemId,
            mediaSourceId = row.mediaSourceId.orEmpty(),
            playSessionId = LOCAL_SESSION_PREFIX + itemId,
            method = PlayMethod.DirectPlay,
            url = files.uri(row.filePath!!),
            headers = emptyMap(),
            runtimeMs = item.runtimeMs,
            audioTracks = snapshot.audioTracks.map { it.copy(selected = it.index == choice.audioIndex) },
            subtitleTracks = subtitleTracks.map { it.copy(selected = it.index == choice.subtitleIndex) },
            subtitleFiles = snapshot.subtitles.map { SubtitleFile(it.index, files.uri(it.file), it.mimeType, it.language, it.label) },
            subtitleBurnedIn = false,
            audioStreamIndex = choice.audioIndex,
            subtitleStreamIndex = choice.subtitleIndex,
            videoFormat = item.videoFormat,
            engine = engineFor(row.filePath, snapshot),
        )
        return LocalPlayable(item, plan)
    }

    // The first player that opens the stored file, in the order the platform lists them (AVPlayer before Aether). The file is as the
    // server sent it, so its container is the one in its name and its codecs, level and bit depth are the ones saved with it, held to
    // the same limits streaming states to the server; a file no player lists goes to the last, the widest, which tries it.
    private fun engineFor(path: String, snapshot: OfflineSnapshot): PlaybackEngineChoice {
        val container = path.substringAfterLast('.', "").lowercase()
        val video = snapshot.videoCodec
        val audio = snapshot.audioTracks.mapNotNull { it.codec?.lowercase() }
        val players = capabilities.candidates()
        val opens = players.firstOrNull { (_, can) ->
            container in can.containers && audio.all { it in can.audioCodecs } &&
                (video == null || (video in can.videoCodecs && withinVideoLimits(video, snapshot.videoLevel, snapshot.videoBitDepth)))
        }
        return (opens ?: players.last()).engine
    }

    /** The episode downloaded after [current] in its show, by season and episode number; null when there is none. */
    suspend fun nextAfter(profile: ProfileRef, current: MediaItem): MediaItem? {
        val seriesId = current.seriesId ?: return null
        val (season, episode) = (current.seasonNumber ?: 0) to (current.episodeNumber ?: return null)
        val done = dao.all(profile.serverId, profile.userId).filter { it.isPlayable() && it.seriesId == seriesId }.map { it.itemId }.toSet()
        val next = dao.allItems(profile.serverId, profile.userId)
            .filter { it.itemId in done && it.kind == ItemKind.Episode.name }
            .filter { order(it) > season * ORDER_STEP + episode }
            .minByOrNull { order(it) } ?: return null
        return OfflineSnapshot.decode(next.snapshotJson).toItem(next)
    }

    /** What to continue with: items started and not finished, most recently watched first, then the next downloaded episode of shows being watched. */
    fun continueWatching(profile: ProfileRef): Flow<List<MediaItem>> = playableItems(profile).map { items ->
        val started = items.filter { it.item.resumePositionMs > 0 && !it.item.played }.sortedByDescending { it.watchedAt }
        val startedShows = started.mapNotNull { it.item.seriesId }.toSet()
        val nextUp = items.filter { it.item.kind == ItemKind.Episode && it.item.seriesId != null }
            .groupBy { it.item.seriesId!! }
            .filterKeys { it !in startedShows }
            .mapNotNull { (_, episodes) ->
                val ordered = episodes.sortedBy { it.order }
                val lastPlayed = ordered.lastOrNull { it.item.played } ?: return@mapNotNull null
                ordered.firstOrNull { it.order > lastPlayed.order && !it.item.played }
            }
            .sortedByDescending { it.watchedAt }
        (started + nextUp).map { it.item }
    }.distinctUntilChanged()

    /** The movies on the device, by title. */
    fun movies(profile: ProfileRef): Flow<List<MediaItem>> = playableItems(profile).map { items ->
        items.filter { it.item.kind == ItemKind.Movie }.map { it.item }.sortedBy { it.title.lowercase() }
    }.distinctUntilChanged()

    /** The shows that have an episode on the device, by name, as the show was saved (its picture is the show's own). */
    fun shows(profile: ProfileRef): Flow<List<MediaItem>> = combine(playableItems(profile), dao.observeItems(profile.serverId, profile.userId)) { items, rows ->
        val ids = items.mapNotNull { it.item.seriesId }.toSet()
        rows.filter { it.kind == ItemKind.Series.name && it.itemId in ids }
            .map { OfflineSnapshot.decode(it.snapshotJson).mediaItem(files) }
            .sortedBy { it.title.lowercase() }
    }.distinctUntilChanged()

    /** The downloaded movies and episodes whose title, show or cast contain [text]; the same shape as a search on the server. */
    suspend fun search(profile: ProfileRef, text: String): List<SearchResult> {
        val pattern = "%" + text.trim().lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return dao.searchDone(profile.serverId, profile.userId, pattern).mapNotNull { row ->
            val item = OfflineSnapshot.decode(row.snapshotJson).mediaItem(files)
            when (item.kind) {
                ItemKind.Movie -> SearchResult(
                    id = item.id,
                    title = item.title,
                    kind = SearchKind.Movie,
                    subtitle = item.year?.toString(),
                    imageUrl = item.posterUrl,
                    aspectRatio = TALL,
                )

                ItemKind.Episode -> SearchResult(
                    id = item.id,
                    title = item.title,
                    kind = SearchKind.Episode,
                    subtitle = listOfNotNull(item.seriesName, episodeCode(item.seasonNumber, item.episodeNumber)).joinToString(" · ").ifEmpty { null },
                    imageUrl = item.landscapeUrl ?: item.posterUrl,
                    aspectRatio = WIDE,
                )

                else -> null
            }
        }
    }

    private class Saved(val item: MediaItem, val watchedAt: Long, val order: Int)

    private fun playableItems(profile: ProfileRef): Flow<List<Saved>> = combine(dao.observeAll(profile.serverId, profile.userId), dao.observeItems(profile.serverId, profile.userId)) { downloads, items ->
        val byId = items.associateBy { it.itemId }
        downloads.filter { it.isPlayable() }.mapNotNull { row ->
            byId[row.itemId]?.takeIf { it.kind == ItemKind.Movie.name || it.kind == ItemKind.Episode.name }?.let { saved ->
                Saved(OfflineSnapshot.decode(saved.snapshotJson).toItem(saved), saved.userDataAt, order(saved))
            }
        }
    }

    // Done, with a file that is still there.
    private fun DownloadEntity.isPlayable() = state == DOWNLOAD_DONE && filePath != null && files.exists(filePath)

    // The saved item with the profile's own record of it, which is kept up to date on the device.
    private fun OfflineSnapshot.toItem(saved: DownloadItemEntity): MediaItem = mediaItem(files).copy(
        resumePositionMs = if (saved.played) 0 else saved.positionTicks / TICKS_PER_MILLISECOND,
        played = saved.played,
    )

    private fun order(saved: DownloadItemEntity) = (saved.seasonNumber ?: 0) * ORDER_STEP + (saved.episodeNumber ?: 0)

    private companion object {
        const val ORDER_STEP = 10_000
        const val TALL = 2f / 3f
        const val WIDE = 16f / 9f
    }
}

/** Marks a play session that did not come from the server, so nothing mistakes it for one to report to it. */
internal const val LOCAL_SESSION_PREFIX = "local-"
