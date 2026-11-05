package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.BaseItemDto
import app.vibeyfin.data.api.ItemsResultDto
import app.vibeyfin.data.api.episodesForDownload
import app.vibeyfin.data.api.itemsForDownload
import app.vibeyfin.data.api.seasonEpisodesForDownload
import app.vibeyfin.data.db.DOWNLOAD_DONE
import app.vibeyfin.data.db.DOWNLOAD_FAILED
import app.vibeyfin.data.db.DOWNLOAD_PAUSED
import app.vibeyfin.data.db.DOWNLOAD_QUEUED
import app.vibeyfin.data.db.DOWNLOAD_RUNNING
import app.vibeyfin.data.db.DOWNLOAD_WAITING_FOR_NETWORK
import app.vibeyfin.data.db.DownloadDao
import app.vibeyfin.data.db.DownloadEntity
import app.vibeyfin.data.db.DownloadItemEntity
import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The running downloads by item id, with their byte count and speed as of the last second. For the ring and the time left
 * of whatever shows a download: [DownloadRepository.observeStates] says what a download is doing, this says how far it is, calmly.
 */
@OptIn(FlowPreview::class)
fun DownloadRepository.observeProgress(profile: ProfileRef): Flow<Map<String, Download>> = observeAll(profile)
    .map { list -> list.filter { it.state == DownloadState.Running }.associateBy { it.itemId } }
    .distinctUntilChanged()
    .sample(DOWNLOAD_SAMPLE_INTERVAL)
    .flowOn(Dispatchers.Default)

/** The states of the downloads still to do: the ones [DownloadRunner] picks up. */
internal val ACTIVE_DOWNLOAD_STATES = listOf(DOWNLOAD_RUNNING, DOWNLOAD_QUEUED, DOWNLOAD_WAITING_FOR_NETWORK)

private val DOWNLOAD_SAMPLE_INTERVAL: Duration = 1.seconds

enum class DownloadState { Queued, Running, WaitingForNetwork, Paused, Failed, Done }

/** One movie or episode the profile has asked for, as the screens show it. */
data class Download(
    val profile: ProfileRef,
    val itemId: String,
    val state: DownloadState,
    val bytesDone: Long,
    /** The file's size; null until the server has said. */
    val bytesTotal: Long?,
    val failure: DownloadFailure?,
    val seriesId: String?,
    val seasonId: String?,
    /** The item from the saved copy, with its pictures on the device. */
    val item: MediaItem,
    /** How fast it is coming in right now; null unless it is running and has been measured. */
    val bytesPerSecond: Long? = null,
) {
    /** How long the rest of the file takes at the current speed; null when that is not known. */
    val timeLeft: Duration?
        get() = if (bytesTotal != null && bytesPerSecond != null && bytesPerSecond > 0) ((bytesTotal - bytesDone).coerceAtLeast(0) / bytesPerSecond).seconds else null

    /** How much of the file is here, 0 to 1; null when the size is not known. */
    val progress: Float?
        get() = bytesTotal?.takeIf { it > 0 }?.let { (bytesDone.toFloat() / it).coerceIn(0f, 1f) }
}

/** What to download: one movie or episode, a season's episodes, or every episode of a show. */
sealed interface DownloadTarget {
    data class Item(val itemId: String) : DownloadTarget

    data class Season(val seriesId: String, val seasonId: String) : DownloadTarget

    data class Series(val seriesId: String) : DownloadTarget
}

/** A movie or episode that could be downloaded, with what the confirmation shows. */
class DownloadCandidate internal constructor(
    val itemId: String,
    val title: String,
    val kind: ItemKind,
    /** The file's size, when the server says. */
    val bytes: Long?,
    /** The server says the user has watched it. */
    val played: Boolean,
    /** The profile has it already, or it is on its way (any state but failed or paused). */
    val alreadyHave: Boolean,
    internal val dto: BaseItemDto,
    internal val baseUrl: String,
)

/** What a [DownloadTarget] comes to: the files to fetch, and how much room they take. */
class DownloadPlan internal constructor(val candidates: List<DownloadCandidate>) {
    /** Only what isn't here yet. */
    val missing: List<DownloadCandidate> get() = candidates.filterNot { it.alreadyHave }
    val unwatched: List<DownloadCandidate> get() = missing.filterNot { it.played }

    /** The size of [items], counting the files whose size the server didn't say as 0. */
    fun bytes(items: List<DownloadCandidate> = missing): Long = items.sumOf { it.bytes ?: 0L }
}

sealed interface DownloadAttempt<out T> {
    data class Success<T>(val value: T) : DownloadAttempt<T>

    data class Failure(val failure: DownloadFailure?, val unreachable: Boolean = false) : DownloadAttempt<Nothing>
}

/**
 * The profile's downloads: what to fetch, what is on the device and how far each is. It records the wish and the saved copy
 * of each item in the database and asks the platform to run [DownloadRunner] in the background. The work itself is the
 * runner's. Everything is keyed by profile, and runs as that profile through [ConnectionManager.asProfile], so it does not
 * need the profile to be the one signed in.
 */
class DownloadRepository(
    private val dao: DownloadDao,
    private val connection: ConnectionManager,
    private val files: DownloadFiles,
    private val scheduler: DownloadScheduler,
    private val wake: DownloadWake,
    private val languages: LanguagePreferences,
    private val clock: Clock,
    private val speeds: DownloadSpeeds = DownloadSpeeds(),
) {
    /** The profile's downloads, oldest request first. */
    fun observeAll(profile: ProfileRef): Flow<List<Download>> = combine(dao.observeAll(profile.serverId, profile.userId), dao.observeItems(profile.serverId, profile.userId), speeds.speeds) { downloads, items, speed ->
        val byId = items.associateBy { it.itemId }
        downloads.mapNotNull { row -> byId[row.itemId]?.let { row.toDownload(it, speed[folder(profile, row.itemId)]) } }
    }.distinctUntilChanged()

    /**
     * [observeAll], but it only emits when a download comes, goes or changes state, not for its byte count or speed. For screens
     * that show what a download is doing but not how far it is: a running download would otherwise rebuild them several times a second.
     * The numbers in what it emits are those of the moment it last changed.
     */
    fun observeStates(profile: ProfileRef): Flow<List<Download>> = observeAll(profile).distinctUntilChanged { old, new ->
        old.size == new.size && old.indices.all { old[it].itemId == new[it].itemId && old[it].state == new[it].state && old[it].failure == new[it].failure }
    }

    /** The shows and seasons the profile's downloaded episodes belong to, by id, as they were saved: the name and picture of a show. */
    fun observeParents(profile: ProfileRef): Flow<Map<String, MediaItem>> = dao.observeItems(profile.serverId, profile.userId).map { rows ->
        rows.filter { it.kind == ItemKind.Series.name || it.kind == ItemKind.Season.name }
            .associate { it.itemId to OfflineSnapshot.decode(it.snapshotJson).mediaItem(files) }
    }.distinctUntilChanged()

    /** One download, or null when the profile has none for [itemId]. */
    fun observe(profile: ProfileRef, itemId: String): Flow<Download?> = combine(dao.observe(profile.serverId, profile.userId, itemId), dao.observeItems(profile.serverId, profile.userId), speeds.speeds) { row, items, speed ->
        row?.let { r -> items.firstOrNull { it.itemId == itemId }?.let { r.toDownload(it, speed[folder(profile, itemId)]) } }
    }.distinctUntilChanged()

    /** The bytes the profile's downloads take on the device, partial ones included. */
    fun observeBytesUsed(profile: ProfileRef): Flow<Long> = dao.observeBytesUsed(profile.serverId, profile.userId)

    /** Whether the server lets the profile download. Unknown (null) when it can't be asked. */
    suspend fun canDownload(profile: ProfileRef): Boolean? = when (val call = connection.asProfile(profile.serverId, profile.userId) { it.me() }) {
        is ProfileCall.Answered -> (call.result as? ApiResult.Success)?.value?.policy?.enableContentDownloading
        else -> null
    }

    /** Reads what [target] comes to from the server, for the confirmation and for [enqueue]. */
    suspend fun plan(profile: ProfileRef, target: DownloadTarget): DownloadAttempt<DownloadPlan> {
        val call = connection.asProfile(profile.serverId, profile.userId) { api ->
            val result: ApiResult<ItemsResultDto> = when (target) {
                is DownloadTarget.Item -> api.itemsForDownload(profile.userId, listOf(target.itemId))
                is DownloadTarget.Season -> api.seasonEpisodesForDownload(profile.userId, target.seriesId, target.seasonId)
                is DownloadTarget.Series -> api.episodesForDownload(profile.userId, target.seriesId)
            }
            when (result) {
                is ApiResult.Success -> ApiResult.Success(api.baseUrl to result.value.items)
                is ApiResult.Failure -> result
            }
        }
        val (baseUrl, items) = when (val answer = call.unwrap()) {
            is DownloadAttempt.Failure -> return answer
            is DownloadAttempt.Success -> answer.value
        }
        // The downloads play from the device with the language preferences as they are now, as the server would pick.
        languages.refresh(profile)
        val candidates = items
            .filter { (it.type == "Movie" || it.type == "Episode") && it.mediaSources.isNotEmpty() }
            .map { dto ->
                val existing = dao.find(profile.serverId, profile.userId, dto.id)
                DownloadCandidate(
                    itemId = dto.id,
                    title = dto.name.orEmpty(),
                    kind = if (dto.type == "Movie") ItemKind.Movie else ItemKind.Episode,
                    bytes = dto.mediaSources.first().size,
                    played = dto.userData?.played == true,
                    alreadyHave = existing != null && existing.state != DOWNLOAD_FAILED && existing.state != DOWNLOAD_PAUSED,
                    dto = dto,
                    baseUrl = baseUrl,
                )
            }
        return DownloadAttempt.Success(DownloadPlan(candidates))
    }

    /**
     * Puts [items] (by default what [plan] says is missing) in the queue and starts the background work. A paused or failed
     * one goes back to waiting its turn. Returns how many were queued. The show and season an episode belongs to are read now
     * and kept with it, so the Downloads screen can group episodes without the server.
     */
    suspend fun enqueue(profile: ProfileRef, plan: DownloadPlan, items: List<DownloadCandidate> = plan.missing): DownloadAttempt<Int> {
        if (items.isEmpty()) return DownloadAttempt.Success(0)
        val now = clock.now().toEpochMilliseconds()
        val parents = when (val read = readParents(profile, items)) {
            is DownloadAttempt.Failure -> return read
            is DownloadAttempt.Success -> read.value
        }
        // Only what is not kept yet: a saved copy that has fetched its pictures must not start over, nor lose its position.
        val fresh = parents.map { (dto, baseUrl) -> dto.toSnapshotEntity(profile, baseUrl, now) } +
            items.filter { dao.findItem(profile.serverId, profile.userId, it.itemId) == null }
                .map { it.dto.toSnapshotEntity(profile, it.baseUrl, now) }
        dao.upsertItems(fresh)
        var queued = 0
        for (candidate in items) {
            val dto = candidate.dto
            val source = dto.mediaSources.first()
            val existing = dao.find(profile.serverId, profile.userId, dto.id)
            if (existing != null && existing.state != DOWNLOAD_FAILED && existing.state != DOWNLOAD_PAUSED) continue
            dao.upsert(
                DownloadEntity(
                    serverId = profile.serverId,
                    userId = profile.userId,
                    itemId = dto.id,
                    kind = candidate.kind.name,
                    seriesId = dto.seriesId,
                    seasonId = dto.seasonId,
                    state = DOWNLOAD_QUEUED,
                    bytesDone = existing?.bytesDone ?: 0,
                    bytesTotal = source.size ?: -1,
                    mediaSourceId = source.id,
                    container = source.container,
                    etag = existing?.etag,
                    filePath = null,
                    error = null,
                    addedAt = existing?.addedAt ?: now,
                    updatedAt = now,
                    completedAt = null,
                ),
            )
            queued++
        }
        schedule()
        return DownloadAttempt.Success(queued)
    }

    /** Stops [itemId] where it is; the part already fetched is kept for [resume]. */
    suspend fun pause(profile: ProfileRef, itemId: String) {
        val row = dao.find(profile.serverId, profile.userId, itemId) ?: return
        if (row.state in ACTIVE_DOWNLOAD_STATES) {
            dao.setState(profile.serverId, profile.userId, itemId, DOWNLOAD_PAUSED, null, clock.now().toEpochMilliseconds())
        }
    }

    /** Puts a paused or failed download back in the queue and starts the background work. */
    suspend fun resume(profile: ProfileRef, itemId: String) {
        val row = dao.find(profile.serverId, profile.userId, itemId) ?: return
        if (row.state == DOWNLOAD_PAUSED || row.state == DOWNLOAD_FAILED) {
            dao.setState(profile.serverId, profile.userId, itemId, DOWNLOAD_QUEUED, null, clock.now().toEpochMilliseconds())
            schedule()
        }
    }

    /** Deletes the download, its file and its saved copy, and the show and season too when nothing of them is left. */
    suspend fun remove(profile: ProfileRef, itemId: String) {
        val row = dao.find(profile.serverId, profile.userId, itemId)
        dao.remove(profile.serverId, profile.userId, itemId)
        files.delete(folder(profile, itemId))
        if (row != null) {
            row.seasonId?.takeIf { dao.countInSeason(profile.serverId, profile.userId, it) == 0 }?.let { dropParent(profile, it) }
            row.seriesId?.takeIf { dao.countInSeries(profile.serverId, profile.userId, it) == 0 }?.let { dropParent(profile, it) }
        }
    }

    /** Deletes every download of the profile. */
    suspend fun removeAll(profile: ProfileRef) {
        dao.observeAll(profile.serverId, profile.userId).first().forEach { remove(profile, it.itemId) }
    }

    /**
     * Deletes everything of the profile on the device: every download, and the whole of its folder, so the metadata of downloads that
     * never finished and any file that no row knows about go too. On a platform that shows the folder to the user, that is the clean-up.
     */
    suspend fun purge(profile: ProfileRef) {
        removeAll(profile)
        files.delete("${profile.serverId}/${profile.userId}")
    }

    /** Starts the background work again after the app starts, for downloads an earlier run left unfinished. */
    suspend fun resumePending() = schedule()

    /**
     * Asks the platform to run the downloads still to do, and a run that is already waiting to look again. The run waits for
     * Wi-Fi or a network itself, so no network is asked of the platform.
     */
    suspend fun schedule() {
        wake.wake()
        if (!scheduler.isSupported) return
        if (dao.inStates(ACTIVE_DOWNLOAD_STATES).isEmpty()) return
        scheduler.schedule(DownloadNetwork.None)
    }

    private suspend fun dropParent(profile: ProfileRef, id: String) {
        dao.deleteItem(profile.serverId, profile.userId, id)
        files.delete(folder(profile, id))
    }

    // The show and the season of each episode in [items], once each, unless the profile already has them.
    private suspend fun readParents(profile: ProfileRef, items: List<DownloadCandidate>): DownloadAttempt<List<Pair<BaseItemDto, String>>> {
        val wanted = items.flatMap { listOfNotNull(it.dto.seriesId, it.dto.seasonId) }.distinct()
            .filter { dao.findItem(profile.serverId, profile.userId, it) == null }
        if (wanted.isEmpty()) return DownloadAttempt.Success(emptyList())
        val call = connection.asProfile(profile.serverId, profile.userId) { api ->
            when (val result = api.itemsForDownload(profile.userId, wanted)) {
                is ApiResult.Success -> ApiResult.Success(result.value.items.map { it to api.baseUrl })
                is ApiResult.Failure -> result
            }
        }
        return call.unwrap()
    }

    private fun DownloadEntity.toDownload(item: DownloadItemEntity, speed: Long?): Download = Download(
        profile = ProfileRef(serverId, userId),
        itemId = itemId,
        state = when (state) {
            DOWNLOAD_RUNNING -> DownloadState.Running
            DOWNLOAD_QUEUED -> DownloadState.Queued
            DOWNLOAD_WAITING_FOR_NETWORK -> DownloadState.WaitingForNetwork
            DOWNLOAD_PAUSED -> DownloadState.Paused
            DOWNLOAD_DONE -> DownloadState.Done
            else -> DownloadState.Failed
        },
        bytesDone = bytesDone,
        bytesTotal = bytesTotal.takeIf { it > 0 },
        failure = DownloadFailure.fromCode(error),
        seriesId = seriesId,
        seasonId = seasonId,
        item = OfflineSnapshot.decode(item.snapshotJson).mediaItem(files),
        bytesPerSecond = speed.takeIf { state == DOWNLOAD_RUNNING },
    )

    private fun <T> ProfileCall<T>.unwrap(): DownloadAttempt<T> = when (this) {
        ProfileCall.NoSession -> DownloadAttempt.Failure(DownloadFailure.SignIn)

        ProfileCall.Unreachable -> DownloadAttempt.Failure(null, unreachable = true)

        is ProfileCall.Answered -> when (val result = this.result) {
            is ApiResult.Success -> DownloadAttempt.Success(result.value)
            is ApiResult.Failure -> result.error.toAttempt()
        }
    }

    private fun ApiError.toAttempt(): DownloadAttempt.Failure = when (this) {
        ApiError.Network -> DownloadAttempt.Failure(null, unreachable = true)
        ApiError.Unauthorized -> DownloadAttempt.Failure(DownloadFailure.SignIn)
        ApiError.Forbidden -> DownloadAttempt.Failure(DownloadFailure.Forbidden)
        ApiError.NotFound -> DownloadAttempt.Failure(DownloadFailure.NotOnServer)
        is ApiError.Server -> DownloadAttempt.Failure(null, unreachable = true)
        is ApiError.Unexpected, ApiError.BadResponse -> DownloadAttempt.Failure(DownloadFailure.Unknown)
    }
}

/** The folder one item's files are in, relative to the downloads folder. */
internal fun folder(profile: ProfileRef, itemId: String) = "${profile.serverId}/${profile.userId}/$itemId"

// The saved copy of an item: the columns the lists use, and its [OfflineSnapshot] as JSON.
private fun BaseItemDto.toSnapshotEntity(profile: ProfileRef, baseUrl: String, now: Long): DownloadItemEntity {
    val snapshot = toSnapshot(baseUrl)
    val item = snapshot.item
    val isSeason = type == "Season"
    return DownloadItemEntity(
        serverId = profile.serverId,
        userId = profile.userId,
        itemId = id,
        kind = item.kind.name,
        title = item.title,
        seriesId = seriesId,
        seriesName = seriesName,
        seasonId = seasonId,
        seasonNumber = if (isSeason) indexNumber else parentIndexNumber,
        episodeNumber = if (isSeason) null else indexNumber,
        searchText = (listOfNotNull(item.title, seriesName) + item.cast.map { it.name }).joinToString(" ").lowercase(),
        positionTicks = userData?.playbackPositionTicks ?: 0,
        played = userData?.played == true,
        userDataAt = now,
        snapshotJson = snapshot.encode(),
    )
}

/** Where each picture and subtitle file is on the server, relative to its address, and the tracks the player will offer. */
internal fun BaseItemDto.toSnapshot(baseUrl: String): OfflineSnapshot {
    val item = toMediaItem(baseUrl)
    val root = baseUrl.trimEnd('/')

    fun String.relative() = removePrefix(root)
    val sources = buildMap {
        item.posterUrl?.let { put(OfflineSnapshot.POSTER, it.relative()) }
        item.backdropUrl?.let { put(OfflineSnapshot.BACKDROP, it.relative()) }
        item.landscapeUrl?.let { put(OfflineSnapshot.LANDSCAPE, it.relative()) }
        item.logoUrl?.let { put(OfflineSnapshot.LOGO, it.relative()) }
        item.cast.forEach { member -> member.imageUrl?.let { put(OfflineSnapshot.castRole(member.id), it.relative()) } }
    }
    val source = mediaSources.firstOrNull()
    val tracks = source?.tracks(baseUrl)
    val video = source?.mediaStreams.orEmpty().firstOrNull { it.type == "Video" }
    // The item lists a subtitle file next to the video without a delivery path (that comes with playback), so its route is built here.
    val subtitleSources = source?.mediaStreams.orEmpty().filter { it.type == "Subtitle" && it.isExternal }.mapNotNull { stream ->
        val (extension, mimeType) = when (stream.codec?.lowercase()) {
            "subrip", "srt" -> "srt" to "application/x-subrip"
            "ass", "ssa" -> "ass" to "text/x-ssa"
            "webvtt", "vtt" -> "vtt" to "text/vtt"
            else -> return@mapNotNull null
        }
        SubtitleSource(stream.index, "/Videos/$id/${source!!.id}/Subtitles/${stream.index}/0/Stream.$extension", mimeType, stream.language?.takeIf { it.isNotBlank() }, stream.name())
    }
    return OfflineSnapshot(
        item = item,
        sources = sources,
        subtitleSources = subtitleSources,
        audioTracks = tracks?.audio.orEmpty(),
        subtitleTracks = tracks?.subtitles.orEmpty(),
        // Nothing to fetch for an item with no pictures and no subtitle files.
        assetsDone = sources.isEmpty() && subtitleSources.isEmpty(),
        videoCodec = video?.codec?.lowercase(),
        videoLevel = video?.level?.toInt(),
        videoBitDepth = video?.bitDepth,
        tracksDescribed = true,
    )
}
