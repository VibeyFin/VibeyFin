package app.vibeyfin.data.download

import app.vibeyfin.data.api.ClientInfoProvider
import app.vibeyfin.data.api.authorizationHeader
import app.vibeyfin.domain.DownloadFailure
import app.vibeyfin.domain.ExternalTransferRequest
import app.vibeyfin.domain.ExternalTransferResult
import app.vibeyfin.domain.ExternalVideoTransfer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionDownloadTaskResumeData
import platform.Foundation.NSURLSessionTask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.setValue
import platform.Foundation.writeToFile
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Videos through a background `URLSession`, which the system carries on with while the app is suspended or even closed, and wakes the
 * app for when the transfer ends. The runner hands over one video and waits ([transfer]); if the app is gone by the time it ends, the
 * delegate has already moved the file to its part path, which is all the runner needs to complete the download at the next start.
 *
 * What a task is: a `GET /Items/{id}/Download` with the profile's token in its headers, whose `taskDescription` is the part path (so the
 * delegate, after a relaunch with no runner, still knows where the file goes). A stop (pause, removal, a network the profile may not use)
 * cancels the task *producing resume data*, kept next to the part, so the next try goes on from the byte it reached. A task the app
 * left running is picked up again instead of started twice.
 *
 * Not run on a device or a simulator yet: Xcode is not installed on the machine this was written on. It compiles against the real
 * Foundation bindings.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBackgroundTransfer(
    private val files: IosDownloadFiles,
    private val clientInfo: ClientInfoProvider,
) : ExternalVideoTransfer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val delegate = TransferDelegate(files, scope)

    private val session: NSURLSession by lazy {
        val config = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(SESSION_ID)
        // The system wakes the app when the transfers are done, and starts them at once (not when it finds it convenient).
        config.sessionSendsLaunchEvents = true
        config.discretionary = false
        config.waitsForConnectivity = true
        NSURLSession.sessionWithConfiguration(config, delegate = delegate, delegateQueue = NSOperationQueue.mainQueue)
    }

    /** The system woke the app for the session [identifier]: make sure it exists, so its delegate gets what happened, and say when that is all. */
    fun handleBackgroundEvents(identifier: String, completion: () -> Unit) {
        if (identifier != SESSION_ID) return completion()
        delegate.finishEvents = completion
        session
    }

    override suspend fun transfer(
        request: ExternalTransferRequest,
        stop: StateFlow<Boolean>,
        onProgress: suspend (done: Long, total: Long?) -> Unit,
    ): ExternalTransferResult = coroutineScope {
        val running = runningTask(request.partPath)
        val task = running ?: newTask(request)
        val watcher = launch {
            stop.first { it }
            task.cancelByProducingResumeData { data -> files.saveResumeData(request.partPath, data) }
        }
        try {
            suspendCancellableCoroutine { continuation ->
                delegate.wait(request.partPath, continuation, onProgress)
                task.resume()
            }
        } finally {
            watcher.cancel()
        }
    }

    private suspend fun newTask(request: ExternalTransferRequest): NSURLSessionDownloadTask {
        val url = checkNotNull(NSURL.URLWithString(request.baseUrl.trimEnd('/') + "/Items/${request.itemId}/Download")) { "Not an address: ${request.baseUrl}" }
        val http = NSMutableURLRequest.requestWithURL(url)
        http.setValue(clientInfo.current().authorizationHeader(request.token), forHTTPHeaderField = "Authorization")
        http.setAllowsCellularAccess(request.allowsCellular)
        val resume = files.readResumeData(request.partPath)
        // What came in before a stop is in the system's own file, which the resume data points at; the part file starts over if there is none.
        if (resume == null) files.delete(request.partPath)
        val task = if (resume != null) session.downloadTaskWithResumeData(resume) else session.downloadTaskWithRequest(http)
        task.setTaskDescription(request.partPath)
        files.discardResumeData(request.partPath)
        return task
    }

    /** A task the app started earlier and the system kept going. */
    private suspend fun runningTask(partPath: String): NSURLSessionDownloadTask? = suspendCancellableCoroutine { continuation ->
        session.getAllTasksWithCompletionHandler { tasks ->
            continuation.resume(tasks?.firstOrNull { (it as? NSURLSessionTask)?.taskDescription == partPath } as? NSURLSessionDownloadTask)
        }
    }

    private companion object {
        const val SESSION_ID = "app.vibeyfin.downloads"
    }
}

/** Receives what the session does, on the main queue, with or without a runner waiting. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class TransferDelegate(
    private val files: IosDownloadFiles,
    private val scope: CoroutineScope,
) : NSObject(), NSURLSessionDownloadDelegateProtocol {
    private class Waiter(val continuation: CancellableContinuation<ExternalTransferResult>, val onProgress: suspend (Long, Long?) -> Unit)

    private val waiters = mutableMapOf<String, Waiter>()

    /** The tasks whose file has been moved into place, by part path. */
    private val moved = mutableSetOf<String>()

    /** What the system gave the app when it woke it for the session; called once the delegate has been told everything. */
    var finishEvents: (() -> Unit)? = null

    fun wait(partPath: String, continuation: CancellableContinuation<ExternalTransferResult>, onProgress: suspend (Long, Long?) -> Unit) {
        waiters[partPath] = Waiter(continuation, onProgress)
    }

    override fun URLSession(session: NSURLSession, downloadTask: NSURLSessionDownloadTask, didWriteData: Long, totalBytesWritten: Long, totalBytesExpectedToWrite: Long) {
        val partPath = downloadTask.taskDescription ?: return
        val waiter = waiters[partPath] ?: return
        val total = totalBytesExpectedToWrite.takeIf { it > 0 }
        scope.launch { waiter.onProgress(totalBytesWritten, total) }
    }

    override fun URLSession(session: NSURLSession, downloadTask: NSURLSessionDownloadTask, didFinishDownloadingToURL: NSURL) {
        val partPath = downloadTask.taskDescription ?: return
        // An error answer comes as a "file" too: only a success is a video. The file must be moved now: it is deleted when this returns.
        val status = (downloadTask.response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        if (status !in 200..299) return
        val target = NSURL.fileURLWithPath(files.absolute(partPath))
        files.delete(partPath)
        val folder = files.absolute(partPath).substringBeforeLast('/')
        NSFileManager.defaultManager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
        if (NSFileManager.defaultManager.moveItemAtURL(didFinishDownloadingToURL, toURL = target, error = null)) moved += partPath
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        val partPath = task.taskDescription ?: return
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        val error = didCompleteWithError
        // What came in before an interruption is kept to go on from.
        (error?.userInfo?.get(NSURLSessionDownloadTaskResumeData) as? NSData)?.let { files.saveResumeData(partPath, it) }
        val result = when {
            error == null && partPath in moved -> ExternalTransferResult.Finished
            error == null -> status.toFailure()
            error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled -> ExternalTransferResult.Stopped
            status in 400..599 -> status.toFailure()
            // Any other trouble is the connection: the transfer comes back when there is one.
            else -> ExternalTransferResult.Interrupted
        }
        moved -= partPath
        waiters.remove(partPath)?.continuation?.resume(result)
    }

    override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
        finishEvents?.invoke()
        finishEvents = null
    }

    private fun Int.toFailure(): ExternalTransferResult = when (this) {
        401 -> ExternalTransferResult.Failed(DownloadFailure.SignIn)
        403 -> ExternalTransferResult.Failed(DownloadFailure.Forbidden)
        404 -> ExternalTransferResult.Failed(DownloadFailure.NotOnServer)
        in 500..599 -> ExternalTransferResult.ServerError
        0 -> ExternalTransferResult.Interrupted
        else -> ExternalTransferResult.Failed(DownloadFailure.Unknown)
    }
}

private fun IosDownloadFiles.resumePath(partPath: String) = partPath.removeSuffix(".part") + ".resume"

internal fun IosDownloadFiles.saveResumeData(partPath: String, data: NSData?) {
    if (data == null) return
    data.writeToFile(absolute(resumePath(partPath)), atomically = true)
}

internal fun IosDownloadFiles.readResumeData(partPath: String): NSData? =
    if (exists(resumePath(partPath))) NSData.dataWithContentsOfFile(absolute(resumePath(partPath))) else null

internal fun IosDownloadFiles.discardResumeData(partPath: String) = delete(resumePath(partPath))
