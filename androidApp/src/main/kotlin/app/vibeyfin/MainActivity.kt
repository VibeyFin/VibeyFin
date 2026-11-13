package app.vibeyfin

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.vibeyfin.app.VibeyFinApp
import app.vibeyfin.app.notifications.NewTitleTap
import app.vibeyfin.app.notifications.NewTitleTapGate
import app.vibeyfin.data.db.AccountDao
import app.vibeyfin.i18n.AppLocale
import app.vibeyfin.ui.notifications.NotificationOpenViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val notificationOpen: NotificationOpenViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        // VibeyFin is dark-only: light system-bar icons over transparent bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        get<AppLocale>().deviceLanguageChanged()

        // A recreated activity (rotation is handled without one, but process death is not) has handled its intent already;
        // the extras are also taken off once read, and a Recents relaunch is never read as a tap (NewTitleTap.from).
        if (savedInstanceState == null) openFrom(intent)

        setContent { VibeyFinApp(notificationOpen) }
    }

    /** A notification tapped while the app runs comes here (the intent is single top). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    // Extras come from outside the app, so only a tap that [NewTitleTapGate] accepts is acted on, once
    private fun openFrom(intent: Intent?) {
        val tap = NewTitleTap.from(intent) ?: return

        intent?.let(NewTitleTap::consume)

        val gate = NewTitleTapGate.from(get<AccountDao>())

        lifecycleScope.launch {
            if (gate.accept(tap)) notificationOpen.onNotificationTapped(tap.serverId, tap.userId, tap.itemId)
        }
    }
}
