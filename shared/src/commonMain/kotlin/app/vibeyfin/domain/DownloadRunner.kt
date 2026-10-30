package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.DownloadSink
import app.vibeyfin.data.api.DownloadStart
import app.vibeyfin.data.api.JellyfinApi
import app.vibeyfin.data.api.SinkFailure
import app.vibeyfin.data.api.download
import app.vibeyfin.data.api.fetch
import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.data.db.DOWNLOAD_FAILED
import app.vibeyfin.data.db.DOWNLOAD_QUEUED
import app.vibeyfin.data.db.DOWNLOAD_RUNNING
import app.vibeyfin.data.db.DOWNLOAD_WAITING_FOR_NETWORK
import app.vibeyfin.data.db.DownloadDao
import app.vibeyfin.data.db.DownloadEntity
import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Does the downloading: takes the downloads still to do, one at a time, oldest first, and for each fetches its pictures and
 * subtitle files, then the video, into the downloads folder. The platform calls [run] from its background mechanism (Android: a
 * WorkManager worker in a foreground service), possibly in a process that was started just for this, so it reads everything
 * it needs from the database and goes to the server as the download's own profile ([ConnectionManager.asProfile]), never
 * through the signed-in state.
 *
 * Whatever stops a download is written down, so a later run goes on from there: a lost connection keeps the bytes already
 * fetched and the next run asks for the rest with a `Range`; the user's pause and a network the profile may not use leave
 * their state. A run that is itself cancelled (the system stopping the worker) leaves a download as it was, and it is
 * picked up again. Only the profile's mobile-data setting, the storage limit and the free space decide which download goes.
 */
class DownloadRunner(
    private val dao: DownloadDao,
    private val accounts: AccountDao,
    private val connection: ConnectionManager,
    private val files: DownloadFiles,
    private val network: NetworkMonitor,
    private val wake: DownloadWake,
    private val clock: Clock,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val speeds: DownloadSpeeds = DownloadSpeeds(),
    /** On a platform that transfers videos itself; null where the runner does (Android). */
    private val external: ExternalVideoTransfer? = null,
    /** On a platform whose player cannot open every file (iOS); null where it can (Android). */
    private val converter: VideoConverter? = null,
) {
    /** What the platform should do after [run] returns. */
    enum class Next {
        /** Nothing is left to do. */
        Done,

        /** What is left may only use Wi-Fi: run again when an unmetered network is there. */
        WhenUnmetered,

        /** There is no network: run again when there is one. */
        WhenConnected,

        /** There is a network but the server did not answer well (or the connection broke): try again after a delay. */
        Retry,
    }

    /** What a run is waiting for while nothing can go. */
    enum class Waiting { Network, Wifi, Server }

    /**
     * How far the download in hand is, for the notification. [remaining] counts the downloads still to do, this one included.
     * [waiting] is set while the run is waiting and not downloading.
     */
    class Progress(
        val itemId: String,
        val title: String,
        val bytesDone: Long,
        val bytesTotal: Long?,
        val remaining: Int,
        val waiting: Waiting? = null,
        /** How fast it is coming in; null before there is a measure. */
        val bytesPerSecond: Long? = null,
        /** The finished file is being made playable on the device; [bytesDone] then counts how far that is. */
        val converting: Boolean = false,
    ) {
        /** How long the rest of this file takes at this speed; null when the size or the speed is not known. */
        val timeLeft: Duration?
            get() = if (bytesTotal != null && bytesPerSecond != null && bytesPerSecond > 0) ((bytesTotal - bytesDone).coerceAtLeast(0) / bytesPerSecond).seconds else null
    }

    private enum class Outcome { Finished, Stopped, Waiting, Retry }

    /**
     * Runs until nothing is left to do. [onProgress] is told how the download in hand is doing, about twice a second.
     *
     * When nothing can go right now (no network, only mobile data, a server that does not answer) the run does not end at
     * once: it waits up to [patience] in all for that to change, and goes on when it does. That matters on Android, which lets
     * a background service be started only while the app is visible, so a run that stays alive keeps its foreground service
     * where a run that ends and is started again later would not have it. With no [patience] it returns instead, and says in
     * [Next] when to run again.
     */
    suspend fun run(patience: Duration = Duration.ZERO, onProgress: suspend (Progress) -> Unit = {}): Next {
        val started = timeSource.markNow()
        var failures = 0

        /** Waits for [reason] to pass; false when the patience is used up and the run should end. */
        suspend fun stall(reason: Next, waiting: Waiting, title: String): Boolean {
            val left = patience - started.elapsedNow()
            if (left <= Duration.ZERO) return false
            onProgress(Progress("", title, 0, null, 0, waiting))
            val backoff = (RETRY_BACKOFF * (1 shl failures.coerceAtMost(MAX_BACKOFF_STEPS))).coerceAtMost(left)
            withTimeoutOrNull(left) { awaitChange(reason, backoff) }
            // Looks again whether something changed or the wait ran out; with no patience left the next stall ends the run.
            return true
        }
        while (true) {
            val pending = dao.inStates(ACTIVE_DOWNLOAD_STATES)
            if (pending.isEmpty()) return Next.Done
            val title = pending.first().let { dao.findItem(it.serverId, it.userId, it.itemId)?.title.orEmpty() }
            val selection = select(pending, network.state.value)
            val chosen = selection.allowed.firstOrNull()
            if (chosen == null) {
                val reason = selection.blocked ?: return Next.Done
                if (!stall(reason, if (reason == Next.WhenConnected) Waiting.Network else Waiting.Wifi, title)) return reason
                continue
            }
            when (transfer(chosen, selection.allowed.size, onProgress)) {
                Outcome.Finished -> failures = 0

                Outcome.Stopped -> Unit

                Outcome.Waiting, Outcome.Retry -> {
                    val lost = network.state.value == NetworkState.None
                    val reason = if (lost) Next.WhenConnected else Next.Retry
                    if (!lost) failures++
                    if (!stall(reason, if (lost) Waiting.Network else Waiting.Server, title)) return reason
                }
            }
        }
    }

    // What may run on the network there is, and what the rest waits for; those that wait are marked so
    private suspend fun select(pending: List<DownloadEntity>, net: NetworkState): Selection {
        if (net == NetworkState.None) {
            pending.forEach { markWaiting(it) }
            return Selection(emptyList(), blocked = Next.WhenConnected)
        }

        val allowed = pending.filter { net == NetworkState.Unmetered || allowsMobile(it) }
        val held = pending.filterNot { it in allowed }
        held.forEach { markWaiting(it) }

        return Selection(allowed, blocked = Next.WhenUnmetered.takeIf { held.isNotEmpty() })
    }

    // Returns when [reason] has passed or something woke the runner
    private suspend fun awaitChange(reason: Next, backoff: Duration) {
        merge(
            flow {
                when (reason) {
                    Next.WhenConnected -> network.state.first { it != NetworkState.None }
                    Next.WhenUnmetered -> network.state.first { it == NetworkState.Unmetered }
                    else -> delay(backoff)
                }
                emit(Unit)
            },
            wake.signal,
        ).first()
    }

    private class Selection(val allowed: List<DownloadEntity>, val blocked: Next?)

    private suspend fun transfer(row: DownloadEntity, remaining: Int, onProgress: suspend (Progress) -> Unit): Outcome = coroutineScope {
        setState(row, DOWNLOAD_RUNNING)
        val stop = MutableStateFlow(false)
        val mobileAllowed = allowsMobile(row)
        // Ends the transfer at its next bytes when the user pauses or removes it, or the network becomes one it may not use.
        val watcher = launch {
            combine(dao.observe(row.serverId, row.userId, row.itemId), network.state) { current, net ->
                current == null || current.state != DOWNLOAD_RUNNING || (net == NetworkState.Metered && !mobileAllowed)
            }.collect { if (it) stop.value = true }
        }
        try {
            val outcome = Transfer(row, stop, remaining, onProgress).run()
            if (outcome == Outcome.Stopped) {
                // A network rule stopped it (a pause or a removal already changed the state): wait for a better network.
                dao.find(row.serverId, row.userId, row.itemId)?.takeIf { it.state == DOWNLOAD_RUNNING }?.let { markWaiting(it) }
            }
            outcome
        } finally {
            watcher.cancel()
        }
    }

    /** One download, start to finish. */
    private inner class Transfer(
        val row: DownloadEntity,
        val stop: MutableStateFlow<Boolean>,
        val remaining: Int,
        val onProgress: suspend (Progress) -> Unit,
    ) {
        private val profile = ProfileRef(row.serverId, row.userId)
        private val base = folder(profile, row.itemId)
        private val part = "$base/video.part"
        private var title = ""

        suspend fun run(): Outcome {
            val snapshot = dao.findItem(row.serverId, row.userId, row.itemId)?.let { OfflineSnapshot.decode(it.snapshotJson) }
            title = snapshot?.item?.title.orEmpty()
            checkStorageRules()?.let { return fail(it) }
            // No point fetching gigabytes the player will not open.
            if (converter != null && snapshot?.videoCodec?.let { !converter.canPlayVideo(it) } == true) return fail(DownloadFailure.Unplayable)
            fetchAssets()?.let { return it }
            return downloadVideo()
        }

        // The storage limit and the free space, before any bytes are fetched.
        private suspend fun checkStorageRules(): DownloadFailure? {
            val total = row.bytesTotal.takeIf { it > 0 } ?: return null
            val need = (total - files.length(part)).coerceAtLeast(0)
            val limitMb = accounts.observeStorageLimitMb(row.serverId, row.userId).first() ?: 0
            if (limitMb > 0 && dao.observeBytesUsed(row.serverId, row.userId).first() + need > limitMb * BYTES_PER_MB) {
                return DownloadFailure.StorageLimit
            }
            if (files.freeBytes() < need + FREE_SPACE_MARGIN) return DownloadFailure.NoSpace
            return null
        }

        // The pictures and subtitle files of the item, its season and its show. Null when done; else how this download ends.
        private suspend fun fetchAssets(): Outcome? {
            val pending = listOfNotNull(row.itemId, row.seasonId, row.seriesId).distinct().mapNotNull { id ->
                dao.findItem(row.serverId, row.userId, id)?.let { OfflineSnapshot.decode(it.snapshotJson) }
                    ?.takeIf { !it.assetsDone }?.let { id to it }
            }
            if (pending.isEmpty()) return null
            val attempt = attempt { api ->
                val fetched = mutableMapOf<String, OfflineSnapshot>()
                for ((id, snapshot) in pending) {
                    when (val result = fetchAssetsOf(api, id, snapshot)) {
                        is ApiResult.Success -> fetched[id] = result.value
                        is ApiResult.Failure -> return@attempt result
                    }
                }
                ApiResult.Success(fetched.toMap())
            }
            return when (attempt) {
                is Attempt.Ok -> {
                    attempt.value.forEach { (id, snapshot) -> dao.setSnapshot(row.serverId, row.userId, id, snapshot.encode()) }
                    null
                }

                else -> end(attempt)
            }
        }

        private suspend fun fetchAssetsOf(api: JellyfinApi, id: String, snapshot: OfflineSnapshot): ApiResult<OfflineSnapshot> {
            val dir = folder(profile, id)
            val images = snapshot.images.toMutableMap()
            for ((role, path) in snapshot.sources) {
                if (role in images) continue
                val file = "$dir/images/${role.replace(':', '-')}.img"
                when (val result = fetchFile(api, path, file)) {
                    is ApiResult.Success -> images[role] = file

                    // A picture the server no longer has is left out, not a reason to stop.
                    is ApiResult.Failure -> if (result.error != ApiError.NotFound) return result
                }
            }
            val subtitles = snapshot.subtitles.toMutableList()
            for (source in snapshot.subtitleSources) {
                if (subtitles.any { it.index == source.index }) continue
                val file = "$dir/subs/${source.index}.${subtitleExtension(source.mimeType)}"
                when (val result = fetchFile(api, source.path, file)) {
                    is ApiResult.Success -> subtitles += OfflineSubtitle(source.index, file, source.mimeType, source.language, source.label)
                    is ApiResult.Failure -> if (result.error != ApiError.NotFound) return result
                }
            }
            return ApiResult.Success(snapshot.copy(images = images, subtitles = subtitles, assetsDone = true))
        }

        private suspend fun fetchFile(api: JellyfinApi, path: String, file: String): ApiResult<Unit> {
            val sink = WholeFileSink(files, file)
            return try {
                api.fetch(path, sink).also { if (it is ApiResult.Failure) files.delete(file) }
            } finally {
                sink.close()
            }
        }

        private suspend fun downloadVideo(): Outcome {
            external?.let { return downloadExternally(it) }
            var restarts = 0
            while (true) {
                val sink = VideoSink()
                val attempt = attempt { api -> api.download(row.itemId, files.length(part), sink) }
                sink.close()
                when (attempt) {
                    is Attempt.Ok -> {
                        val length = files.length(part)
                        val expected = sink.total ?: row.bytesTotal.takeIf { it > 0 }
                        // The stream ended early without an error: the rest comes with the next try.
                        if (expected != null && length != expected) return waiting()
                        return complete(length)
                    }

                    is Attempt.Sink -> when (attempt.cause) {
                        is StopTransfer -> return Outcome.Stopped

                        is NoSpaceLeft -> return fail(DownloadFailure.NoSpace)

                        is RestartRequired -> {
                            // The file changed on the server since the first bytes: what is here belongs to the old one.
                            files.delete(part)
                            dao.setValidator(row.serverId, row.userId, row.itemId, null)
                            if (++restarts > 1) return fail(DownloadFailure.Unknown)
                        }

                        else -> return fail(if (files.freeBytes() < FREE_SPACE_MARGIN) DownloadFailure.NoSpace else DownloadFailure.Unknown)
                    }

                    Attempt.Unsatisfiable -> {
                        // The range starts at or after the end: the part is the whole file, or it is wrong.
                        val length = files.length(part)
                        if (length > 0 && length == row.bytesTotal.takeIf { it > 0 }) return complete(length)
                        files.delete(part)
                        if (++restarts > 1) return fail(DownloadFailure.Unknown)
                    }

                    else -> return end(attempt)
                }
            }
        }

        // The video through the platform's own transfer: it may have finished while the app was away, and otherwise is started or picked up.
        private suspend fun downloadExternally(transfer: ExternalVideoTransfer): Outcome {
            val known = row.bytesTotal.takeIf { it > 0 }
            if (known != null && files.length(part) == known) return complete(known)
            val address = when (val found = attempt { api -> ApiResult.Success(api.baseUrl to api.token) }) {
                is Attempt.Ok -> found.value
                else -> return end(found)
            }
            // The platform reports on every chunk it receives, which on a fast link is many times a second.
            val throttle = ProgressThrottle()
            var done = files.length(part)
            var total = known
            val result = transfer.transfer(
                ExternalTransferRequest(base, address.first, address.second, row.itemId, part, allowsMobile(row)),
                stop,
            ) { reached, size ->
                total = size ?: known
                throttle.advance(reached, total, count = reached - done)
                done = maxOf(done, reached)
            }
            throttle.flush(done, total)
            speeds.clear(base)
            return when (result) {
                ExternalTransferResult.Finished -> complete(files.length(part))
                ExternalTransferResult.Stopped -> Outcome.Stopped
                ExternalTransferResult.Interrupted -> waiting()
                ExternalTransferResult.ServerError -> end(Attempt.ServerError)
                is ExternalTransferResult.Failed -> fail(result.failure)
            }
        }

        private suspend fun complete(length: Long): Outcome {
            var extension = row.container?.substringBefore(',')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) } ?: "bin"
            val converted = "$base/video.converting"
            var isConverted = false
            if (converter != null) {
                speeds.setConverting(base, 0f)
                val result = try {
                    converter.convert(part, converted, stop) { fraction ->
                        speeds.setConverting(base, fraction)
                        onProgress(Progress(row.itemId, title, (length * fraction).toLong(), length, remaining, converting = true))
                    }
                } finally {
                    speeds.clearConverting(base)
                }
                when (result) {
                    VideoConversion.NotNeeded -> Unit

                    VideoConversion.Converted -> {
                        isConverted = true
                        extension = "mp4"
                    }

                    VideoConversion.Stopped -> {
                        files.delete(converted)
                        return Outcome.Stopped
                    }

                    VideoConversion.Unsupported -> {
                        files.delete(part)
                        return fail(DownloadFailure.Unplayable)
                    }

                    VideoConversion.Failed -> {
                        files.delete(converted)
                        return fail(DownloadFailure.Unknown)
                    }
                }
            }
            val finalPath = "$base/video.$extension"
            if (isConverted) {
                files.move(converted, finalPath)
                files.delete(part)
            } else {
                files.move(part, finalPath)
            }
            dao.complete(row.serverId, row.userId, row.itemId, files.length(finalPath), finalPath, clock.now().toEpochMilliseconds())
            return Outcome.Finished
        }

        private suspend fun fail(failure: DownloadFailure): Outcome {
            dao.setState(row.serverId, row.userId, row.itemId, DOWNLOAD_FAILED, failure.name, clock.now().toEpochMilliseconds())
            return Outcome.Finished
        }

        private suspend fun waiting(): Outcome {
            setState(row, DOWNLOAD_WAITING_FOR_NETWORK)
            return Outcome.Waiting
        }

        // How a download ends for an [Attempt] that did not succeed.
        private suspend fun end(attempt: Attempt<*>): Outcome = when (attempt) {
            is Attempt.Ok -> Outcome.Finished

            is Attempt.Fail -> fail(attempt.failure)

            is Attempt.Sink -> if (attempt.cause is StopTransfer) Outcome.Stopped else fail(DownloadFailure.Unknown)

            Attempt.Waiting -> waiting()

            Attempt.ServerError -> {
                setState(row, DOWNLOAD_QUEUED)
                Outcome.Retry
            }

            Attempt.Unsatisfiable -> fail(DownloadFailure.Unknown)
        }

        // Runs [call] as the download's profile, on whichever of the server's addresses answers.
        private suspend fun <T> attempt(call: suspend (JellyfinApi) -> ApiResult<T>): Attempt<T> = try {
            when (val answer = connection.asProfile(row.serverId, row.userId, call)) {
                ProfileCall.NoSession -> Attempt.Fail(DownloadFailure.SignIn)

                ProfileCall.Unreachable -> Attempt.Waiting

                is ProfileCall.Answered -> when (val result = answer.result) {
                    is ApiResult.Success -> Attempt.Ok(result.value)
                    is ApiResult.Failure -> classify(result.error)
                }
            }
        } catch (e: SinkFailure) {
            Attempt.Sink(e.cause)
        }

        private fun classify(error: ApiError): Attempt<Nothing> = when (error) {
            ApiError.Network -> Attempt.Waiting
            ApiError.Unauthorized -> Attempt.Fail(DownloadFailure.SignIn)
            ApiError.Forbidden -> Attempt.Fail(DownloadFailure.Forbidden)
            ApiError.NotFound -> Attempt.Fail(DownloadFailure.NotOnServer)
            is ApiError.Server -> Attempt.ServerError
            is ApiError.Unexpected -> if (error.status == RANGE_NOT_SATISFIABLE) Attempt.Unsatisfiable else Attempt.Fail(DownloadFailure.Unknown)
            ApiError.BadResponse -> Attempt.Fail(DownloadFailure.Unknown)
        }

        /** Writes the video to the `.part` file, keeps the database and the notification up to date, and obeys [stop]. */
        inner class VideoSink : DownloadSink {
            private var output: DownloadOutput? = null
            private var done = 0L
            var total: Long? = row.bytesTotal.takeIf { it > 0 }
                private set
            private var validator: String? = row.etag
            private val throttle = ProgressThrottle()

            override suspend fun begin(start: DownloadStart) {
                output?.close()
                output = null
                val offset = files.length(part)
                if (start.resumed && offset > 0 && changedSince(start)) throw RestartRequired()
                val append = start.resumed && offset > 0
                output = files.open(part, append)
                done = if (append) offset else 0
                total = start.totalBytes ?: total
                validator = start.validator ?: validator
                total?.let { if (files.freeBytes() < it - done + FREE_SPACE_MARGIN) throw NoSpaceLeft() }
                dao.setValidator(row.serverId, row.userId, row.itemId, validator)
                report()
            }

            // The server's copy is no longer the one the saved part came from
            private fun changedSince(start: DownloadStart): Boolean {
                val saved = validator ?: return false
                val now = start.validator ?: return false

                return now != saved
            }

            override suspend fun write(bytes: ByteArray, count: Int) {
                if (stop.value) throw StopTransfer()
                output!!.write(bytes, count)
                done += count
                throttle.advance(done, total, count.toLong())
            }

            suspend fun close() {
                output?.close()
                output = null
                if (done > 0) report()
                speeds.clear(base)
            }

            private suspend fun report() = throttle.report(done, total)
        }

        /**
         * Saves the download's progress to the database, the speed readout and [onProgress], but only once per
         * [PROGRESS_STEP_BYTES] or [PROGRESS_STEP_TIME]. Every write wakes everything that watches the downloads,
         * so a fast link must not cause one per chunk.
         */
        inner class ProgressThrottle {
            private var lastReport = clock.now()
            private var unreported = 0L
            private var reported = -1L
            private var speed: Double? = null

            /** [count] more bytes have arrived, [done] in all. */
            suspend fun advance(done: Long, total: Long?, count: Long) {
                if (count > 0) unreported += count
                if (unreported >= PROGRESS_STEP_BYTES || clock.now() - lastReport >= PROGRESS_STEP_TIME) report(done, total)
            }

            /** Reports [done] unless it is what the last report said, so the end of a transfer is never lost between two steps. */
            suspend fun flush(done: Long, total: Long?) {
                if (done != reported) report(done, total)
            }

            suspend fun report(done: Long, total: Long?) {
                val now = clock.now()
                val seconds = (now - lastReport).toDouble(DurationUnit.SECONDS)
                // A smoothed speed, so the time left does not jump with every burst: new measures count for a third.
                if (unreported > 0 && seconds > 0) {
                    val instant = unreported / seconds
                    speed = speed?.let { it * (1 - SPEED_WEIGHT) + instant * SPEED_WEIGHT } ?: instant
                }
                unreported = 0
                lastReport = now
                reported = done
                dao.setProgress(row.serverId, row.userId, row.itemId, done, total ?: -1, now.toEpochMilliseconds())
                val bytesPerSecond = speed?.toLong()?.takeIf { it > 0 }
                if (bytesPerSecond != null) speeds.set(base, bytesPerSecond) else speeds.clear(base)
                onProgress(Progress(row.itemId, title, done, total, remaining, bytesPerSecond = bytesPerSecond))
            }
        }
    }

    private sealed interface Attempt<out T> {
        class Ok<T>(val value: T) : Attempt<T>

        class Fail(val failure: DownloadFailure) : Attempt<Nothing>

        /** The sink ended the transfer, with [cause]. */
        class Sink(val cause: Throwable?) : Attempt<Nothing>

        data object Waiting : Attempt<Nothing>

        data object ServerError : Attempt<Nothing>

        data object Unsatisfiable : Attempt<Nothing>
    }

    private class StopTransfer : Exception("The download was stopped.")

    private class RestartRequired : Exception("The file changed on the server.")

    private class NoSpaceLeft : Exception("There is no room for the file.")

    /** Writes one small file in one go, starting it empty. */
    private class WholeFileSink(private val files: DownloadFiles, private val path: String) : DownloadSink {
        private var output: DownloadOutput? = null

        override suspend fun begin(start: DownloadStart) {
            output = files.open(path, append = false)
        }

        override suspend fun write(bytes: ByteArray, count: Int) {
            output!!.write(bytes, count)
        }

        fun close() {
            output?.close()
            output = null
        }
    }

    private suspend fun setState(row: DownloadEntity, state: String) = dao.setState(row.serverId, row.userId, row.itemId, state, null, clock.now().toEpochMilliseconds())

    private suspend fun markWaiting(row: DownloadEntity) {
        if (row.state != DOWNLOAD_WAITING_FOR_NETWORK) setState(row, DOWNLOAD_WAITING_FOR_NETWORK)
    }

    private suspend fun allowsMobile(row: DownloadEntity): Boolean = accounts.observeDownloadOverMobile(row.serverId, row.userId).first() == true

    private companion object {
        const val BYTES_PER_MB = 1_000_000L
        const val FREE_SPACE_MARGIN = 50L * BYTES_PER_MB
        const val PROGRESS_STEP_BYTES = 2L * BYTES_PER_MB
        val PROGRESS_STEP_TIME = 500.milliseconds
        const val RANGE_NOT_SATISFIABLE = 416
        const val SPEED_WEIGHT = 0.3
        val RETRY_BACKOFF = 30.seconds
        const val MAX_BACKOFF_STEPS = 4

        fun subtitleExtension(mimeType: String) = when (mimeType) {
            "text/vtt" -> "vtt"
            "application/x-subrip" -> "srt"
            else -> "ass"
        }
    }
}
