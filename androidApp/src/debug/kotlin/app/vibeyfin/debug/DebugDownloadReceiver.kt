package app.vibeyfin.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.vibeyfin.di.ApplicationScope
import app.vibeyfin.domain.AuthRepository
import app.vibeyfin.domain.ConnectionManager
import app.vibeyfin.domain.ConnectionState
import app.vibeyfin.domain.DevicePreferences
import app.vibeyfin.domain.DownloadAttempt
import app.vibeyfin.domain.DownloadRepository
import app.vibeyfin.domain.DownloadTarget
import app.vibeyfin.domain.ResolveResult
import app.vibeyfin.domain.ServerRepository
import app.vibeyfin.domain.ServerResolver
import app.vibeyfin.domain.SignInResult
import app.vibeyfin.domain.model.ProfileRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Debug builds only. Until the Downloads screens exist, this lets `adb shell am broadcast` add the test server and sign in,
 * start, pause, resume and remove downloads, and print their state, to check the background work on a device.
 * Output goes to logcat under the tag `DebugDownload`.
 *
 *   -a app.vibeyfin.debug.SIGN_IN   --es url http://10.0.2.2:8196 --es user admin --es password vibeyfin-test
 *   -a app.vibeyfin.debug.DOWNLOAD  --es item <id>   (or --es season <id> --es series <id>, or --es series <id> alone)
 *   -a app.vibeyfin.debug.PAUSE | RESUME | REMOVE   --es item <id>
 *   -a app.vibeyfin.debug.STATUS
 */
class DebugDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val koin = GlobalContext.get()
        koin.get<CoroutineScope>(ApplicationScope).launch {
            try {
                handle(koin, intent)
            } catch (e: Exception) {
                Log.e(TAG, "failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(koin: org.koin.core.Koin, intent: Intent) {
        if (intent.action == "app.vibeyfin.debug.SIGN_IN") {
            val found = koin.get<ServerResolver>().resolve(intent.getStringExtra("url")!!)
            val server = (found as? ResolveResult.Found)?.server ?: run {
                Log.e(TAG, "server not found: $found")
                return
            }
            koin.get<ServerRepository>().save(server.id, server.name, server.version, server.address, server.otherAddresses)
            val result = koin.get<AuthRepository>().signIn(server.id, intent.getStringExtra("user")!!, intent.getStringExtra("password").orEmpty())
            Log.i(TAG, "sign in: $result")
            return
        }
        val connection = koin.get<ConnectionManager>()
        if (connection.state.value !is ConnectionState.Connected) {
            // A fresh process: reopen the profile used last, as the app does at start.
            koin.get<DevicePreferences>().lastProfile.first()?.let { connection.resume(it.serverId, it.userId) }
        }
        val connected = connection.state.value as? ConnectionState.Connected
            ?: run {
                Log.e(TAG, "nobody is signed in")
                return
            }
        val profile = ProfileRef(connected.server.id, connected.account.userId)
        val repository = koin.get<DownloadRepository>()
        val item = intent.getStringExtra("item")
        when (intent.action) {
            "app.vibeyfin.debug.DOWNLOAD" -> {
                val target = when {
                    item != null -> DownloadTarget.Item(item)
                    intent.hasExtra("season") -> DownloadTarget.Season(intent.getStringExtra("series")!!, intent.getStringExtra("season")!!)
                    else -> DownloadTarget.Series(intent.getStringExtra("series")!!)
                }
                when (val plan = repository.plan(profile, target)) {
                    is DownloadAttempt.Failure -> Log.e(TAG, "plan failed: $plan")
                    is DownloadAttempt.Success -> {
                        Log.i(TAG, "plan: ${plan.value.candidates.size} files, ${plan.value.bytes()} bytes")
                        Log.i(TAG, "enqueue: ${repository.enqueue(profile, plan.value)}")
                    }
                }
            }
            "app.vibeyfin.debug.PAUSE" -> repository.pause(profile, item!!)
            "app.vibeyfin.debug.RESUME" -> repository.resume(profile, item!!)
            "app.vibeyfin.debug.REMOVE" -> repository.remove(profile, item!!)
        }
        repository.observeAll(profile).first().forEach {
            Log.i(TAG, "${it.itemId} ${it.item.title}: ${it.state} ${it.bytesDone}/${it.bytesTotal} ${it.failure ?: ""}")
        }
    }

    private companion object {
        const val TAG = "DebugDownload"
    }
}
