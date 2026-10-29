package app.vibeyfin.ui.downloads

import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.Download
import app.vibeyfin.domain.DownloadAttempt
import app.vibeyfin.domain.DownloadCandidate
import app.vibeyfin.domain.DownloadPlan
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.DownloadTarget
import app.vibeyfin.domain.model.ProfileRef
import app.vibeyfin.ui.common.LoadMessages
import app.vibeyfin.ui.common.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The question before a season or a show is downloaded: how many episodes, how much room they take, and whether to leave out
 * the ones already watched. [plan] is null while the server is being asked.
 */
data class DownloadPrompt(
    val target: DownloadTarget,
    /** What is being downloaded, such as a show's or a season's name. */
    val title: String,
    val loading: Boolean = true,
    /** Why the server could not be asked, with what to try. */
    val message: String? = null,
    val plan: DownloadPlan? = null,
    /** Only the episodes not watched yet. */
    val unwatchedOnly: Boolean = false,
) {
    /** What would be downloaded: what is missing here, or of that only the unwatched. */
    val candidates: List<DownloadCandidate> get() = plan?.let { if (unwatchedOnly) it.unwatched else it.missing }.orEmpty()
    val count: Int get() = candidates.size
    val bytes: Long get() = plan?.bytes(candidates) ?: 0L

    /** "They take about 4.1 GB."; null when the server did not say how big the files are. */
    val size: String? get() = Strings.downloadConfirmSize(bytes, count)

    /** Whether to offer "only episodes I haven't watched": only when some of what is missing was watched. */
    val canChooseUnwatched: Boolean get() = plan?.missing?.any { it.played } == true

    /** Everything asked for is on the device already. */
    val nothingToDownload: Boolean get() = plan != null && plan.missing.isEmpty()
}

/** The question before downloads are deleted: how many, and how much room they free. */
data class RemovePrompt(val title: String, val itemIds: List<String>, val bytes: Long) {
    val count: Int get() = itemIds.size
}

/**
 * What a screen needs to start, pause, resume and remove downloads: the confirmations and the messages for what could not be
 * done. A screen's ViewModel owns one, on its own [scope], and shows what it exposes. [profile] is the profile that is signed in.
 */
class DownloadFlow(
    private val scope: CoroutineScope,
    private val downloads: DownloadRepository,
    private val profile: () -> ProfileRef?,
) {
    private val _prompt = MutableStateFlow<DownloadPrompt?>(null)
    val prompt: StateFlow<DownloadPrompt?> = _prompt

    private val _removal = MutableStateFlow<RemovePrompt?>(null)
    val removal: StateFlow<RemovePrompt?> = _removal

    /** A download that could not be started, with what to try. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var last: Triple<DownloadTarget, String, Boolean>? = null

    /**
     * Downloads [target]. With [ask] the user is first shown how many files and how much room (for a season or a show); without,
     * it goes straight into the queue (for one movie or episode, whose size the notification shows).
     */
    fun start(target: DownloadTarget, title: String, ask: Boolean) {
        val profile = profile() ?: return
        last = Triple(target, title, ask)
        _message.value = null
        if (ask) _prompt.value = DownloadPrompt(target, title)
        scope.launch {
            when (val plan = downloads.plan(profile, target)) {
                is DownloadAttempt.Failure -> {
                    _prompt.value = null
                    _message.value = plan.text()
                }
                is DownloadAttempt.Success -> if (ask) {
                    _prompt.update { it?.copy(loading = false, plan = plan.value) }
                } else if (plan.value.candidates.isEmpty()) {
                    _message.value = LoadMessages.DOWNLOAD_NO_FILE
                } else {
                    enqueue(profile, plan.value, plan.value.missing)
                }
            }
        }
    }

    /** Flips "only episodes I haven't watched". */
    fun toggleUnwatchedOnly() = _prompt.update { it?.copy(unwatchedOnly = !it.unwatchedOnly) }

    /** The user said yes: puts what the prompt shows in the queue. */
    fun confirm() {
        val prompt = _prompt.value ?: return
        val plan = prompt.plan ?: return
        val profile = profile() ?: return
        _prompt.value = null
        if (prompt.candidates.isEmpty()) return
        scope.launch { enqueue(profile, plan, prompt.candidates) }
    }

    fun dismiss() {
        _prompt.value = null
    }

    private suspend fun enqueue(profile: ProfileRef, plan: DownloadPlan, items: List<DownloadCandidate>) {
        val result = downloads.enqueue(profile, plan, items)
        if (result is DownloadAttempt.Failure) _message.value = result.text()
    }

    /** Pauses or resumes [download] at once; removing asks first. */
    fun act(download: Download, action: DownloadAction) {
        val profile = profile() ?: return
        when (action) {
            DownloadAction.Pause -> scope.launch { downloads.pause(profile, download.itemId) }
            DownloadAction.Resume -> scope.launch { downloads.resume(profile, download.itemId) }
            DownloadAction.Remove -> askRemove(listOf(download), download.item.title)
        }
    }

    /** Asks before deleting [items]; [title] names them when there is one. */
    fun askRemove(items: List<Download>, title: String) {
        if (items.isEmpty()) return
        // What it frees is what is on the device now, not what the file will come to.
        _removal.value = RemovePrompt(title, items.map { it.itemId }, items.sumOf { it.bytesDone })
    }

    fun confirmRemove() {
        val removal = _removal.value ?: return
        val profile = profile() ?: return
        _removal.value = null
        scope.launch { removal.itemIds.forEach { downloads.remove(profile, it) } }
    }

    fun dismissRemoval() {
        _removal.value = null
    }

    /** "Try again" on a message: asks for the same download again. */
    fun retry() {
        val (target, title, ask) = last ?: return
        start(target, title, ask)
    }

    fun dismissMessage() {
        _message.value = null
    }

    private fun DownloadAttempt.Failure.text(): String = failure?.message() ?: unreachableMessage()
}

/** What a screen with download buttons observes besides its own state: the profile's downloads, and the flow's questions and message. */
internal class DownloadParts(
    val byId: Map<String, Download>,
    val canDownload: Boolean,
    val prompt: DownloadPrompt?,
    val removal: RemovePrompt?,
    val message: String?,
)

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * The [DownloadParts] of whoever is signed in; [canDownload] is whether the server lets them download. While [live] is false the downloads
 * only change with their state (see [DownloadRepository.observeStates]), so a screen that shows no progress is not rebuilt by one.
 */
internal fun downloadParts(
    connection: ConnectionManager,
    downloads: DownloadRepository,
    flow: DownloadFlow,
    canDownload: Flow<Boolean>,
    live: Flow<Boolean> = flowOf(false),
): Flow<DownloadParts> = combine(
    combine(connection.profile, live.distinctUntilChanged(), ::Pair).flatMapLatest { (profile, live) ->
        when {
            profile == null -> flowOf(emptyList<Download>())
            live -> downloads.observeAll(profile)
            else -> downloads.observeStates(profile)
        }.map { list -> list.associateBy { it.itemId } }
    },
    canDownload,
    flow.prompt,
    flow.removal,
    flow.message,
) { byId, allowed, prompt, removal, message -> DownloadParts(byId, allowed, prompt, removal, message) }
