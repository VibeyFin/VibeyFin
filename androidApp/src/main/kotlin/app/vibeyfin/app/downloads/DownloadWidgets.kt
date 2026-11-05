package app.vibeyfin.app.downloads

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import app.vibeyfin.R
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.domain.Download
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.downloads.DownloadAction
import app.vibeyfin.ui.downloads.DownloadBadge
import app.vibeyfin.ui.downloads.DownloadPrompt
import app.vibeyfin.ui.downloads.RemovePrompt
import app.vibeyfin.ui.downloads.actions
import app.vibeyfin.ui.downloads.badge
import app.vibeyfin.ui.downloads.stateWord

/**
 * What a download looks like, in one picture: the download arrow when there is none, a ring that fills while it comes in (gray
 * and still while it waits), a pause bars, a warning, and a phone with a check once it is on the device. All in ink: aqua is for the
 * one primary action and playback progress, and never signals status. Drawn by hand, not with M3's spinner, which is for the
 * player only.
 */
@Composable
fun DownloadBadgeIcon(badge: DownloadBadge, progress: Float?, modifier: Modifier = Modifier, tint: Color = VibeyFinTokens.Colors.ink) {
    when (badge) {
        DownloadBadge.None -> Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = tint, modifier = modifier)

        DownloadBadge.Done -> Icon(painterResource(R.drawable.ic_download_done), contentDescription = null, tint = tint, modifier = modifier)

        DownloadBadge.Failed -> Icon(Icons.Filled.Warning, contentDescription = null, tint = tint, modifier = modifier)

        DownloadBadge.Paused -> Icon(painterResource(R.drawable.ic_pause), contentDescription = null, tint = tint, modifier = modifier)

        DownloadBadge.Waiting, DownloadBadge.Running -> ProgressRing(
            progress = if (badge == DownloadBadge.Running) progress ?: 0f else 0f,
            dimmed = badge == DownloadBadge.Waiting,
            tint = tint,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProgressRing(progress: Float, dimmed: Boolean, tint: Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val track = VibeyFinTokens.Colors.progressTrack

        Canvas(Modifier.matchParentSize()) {
            val stroke = size.minDimension / RING_STROKE_DIVISOR
            val inset = stroke / 2
            val box = Size(size.width - stroke, size.height - stroke)

            drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = box, style = Stroke(stroke))

            if (progress > 0f) {
                drawArc(tint, -90f, 360f * progress.coerceIn(0f, 1f), useCenter = false, topLeft = Offset(inset, inset), size = box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }

        Icon(
            painterResource(R.drawable.ic_download),
            contentDescription = null,
            tint = if (dimmed) tint.copy(alpha = DIMMED) else tint,
            modifier = Modifier.matchParentSize().padding(RING_PADDING),
        )
    }
}

private const val RING_STROKE_DIVISOR = 11f
private const val DIMMED = 0.55f
private val RING_PADDING = 6.dp

/** The icon of an action in a download's menu. */
fun DownloadAction.icon(): DownloadActionIcon = when (this) {
    DownloadAction.Pause -> DownloadActionIcon.Resource(R.drawable.ic_pause)
    DownloadAction.Resume -> DownloadActionIcon.Vector(Icons.Filled.PlayArrow)
    DownloadAction.Remove -> DownloadActionIcon.Vector(Icons.Filled.Delete)
}

sealed interface DownloadActionIcon {
    data class Vector(val icon: ImageVector) : DownloadActionIcon

    data class Resource(val id: Int) : DownloadActionIcon
}

fun DownloadAction.label(): String = when (this) {
    DownloadAction.Pause -> Strings.pauseDownload
    DownloadAction.Resume -> Strings.resumeDownload
    DownloadAction.Remove -> Strings.removeDownload
}

/** A menu row for a download action, with the icon the action has. */
@Composable
fun DownloadActionMenuItem(action: DownloadAction, onClick: () -> Unit) {
    when (val icon = action.icon()) {
        is DownloadActionIcon.Vector -> VibeyFinMenuItem(action.label(), icon.icon, onClick = onClick)
        is DownloadActionIcon.Resource -> VibeyFinMenuItem(action.label(), ImageVector.vectorResource(icon.id), onClick = onClick)
    }
}

/**
 * The question before a season or a show is downloaded: how many episodes, how much room, and "only episodes I haven't watched".
 * While the server is being asked it says so; if it says nothing is missing, there is nothing to confirm.
 */
@Composable
fun DownloadConfirmDialog(prompt: DownloadPrompt, onToggleUnwatched: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VibeyFinTokens.Colors.surface2,
        title = {
            Text(
                when {
                    prompt.loading -> prompt.title
                    prompt.nothingToDownload -> prompt.title
                    else -> Strings.downloadConfirmTitle(prompt.count)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
                when {
                    prompt.loading -> Text(Strings.searching, color = VibeyFinTokens.Colors.inkMuted)

                    prompt.nothingToDownload -> Text(Strings.downloadNothingNew, color = VibeyFinTokens.Colors.inkMuted)

                    else -> {
                        prompt.size?.let { Text(it, color = VibeyFinTokens.Colors.inkMuted) }
                        if (prompt.canChooseUnwatched) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Checkbox, onClick = onToggleUnwatched),
                            ) {
                                Checkbox(
                                    checked = prompt.unwatchedOnly,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = VibeyFinTokens.Colors.ink, checkmarkColor = VibeyFinTokens.Colors.bg, uncheckedColor = VibeyFinTokens.Colors.inkMuted),
                                )
                                Text(Strings.downloadUnwatchedOnly, modifier = Modifier.padding(start = VibeyFinTokens.Space.s2))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!prompt.loading && !prompt.nothingToDownload) {
                TextButton(onClick = onConfirm, enabled = prompt.count > 0) { Text(Strings.downloadConfirmAction, color = VibeyFinTokens.Colors.ink) }
            } else if (prompt.nothingToDownload) {
                TextButton(onClick = onDismiss) { Text(Strings.close, color = VibeyFinTokens.Colors.ink) }
            }
        },
        dismissButton = {
            if (!prompt.nothingToDownload) TextButton(onClick = onDismiss) { Text(Strings.cancel, color = VibeyFinTokens.Colors.inkMuted) }
        },
    )
}

/** The question before downloads are deleted: how many, and how much room that frees. */
@Composable
fun RemoveConfirmDialog(prompt: RemovePrompt, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VibeyFinTokens.Colors.surface2,
        title = { Text(Strings.removeConfirmTitle(prompt.count, prompt.title)) },
        text = { Text(Strings.removeConfirmBody(prompt.bytes), color = VibeyFinTokens.Colors.inkMuted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(Strings.removeConfirmAction, color = VibeyFinTokens.Colors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel, color = VibeyFinTokens.Colors.inkMuted) } },
    )
}

/**
 * Asks, once, for the permission to show the notification that downloads keep while they run: Android 13 and later need it, and
 * the downloads run without it (the notification just isn't shown). Call the result when a download is started: the system's
 * dialog then explains itself in context. It is asked once per install, so a "no" is not nagged about.
 */
@Composable
fun rememberDownloadNotificationPermission(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    return remember(launcher, context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                !context.asked()
            ) {
                context.markAsked()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private const val PREFS = "downloads"
private const val ASKED = "asked_for_notifications"

private fun Context.asked() = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ASKED, false)

private fun Context.markAsked() = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(ASKED, true) }

/** A square that holds a badge over a picture: a dark disc so the ink reads on any artwork. */
@Composable
fun BadgeOverPicture(modifier: Modifier = Modifier, badge: DownloadBadge, progress: Float?, size: Dp = 26.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(VibeyFinTokens.Colors.scrim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DownloadBadgeIcon(badge, progress, modifier = Modifier.size(size - 8.dp))
    }
}

/**
 * The download control of one episode: the arrow while there is none (a tap starts it), then the badge of its state (a tap opens a
 * menu to pause, resume or remove). [overPicture] puts it on a dark disc, for a card where it sits on the still. Nothing is drawn
 * for an episode with no download when the user may not download.
 */
@Composable
fun EpisodeDownloadButton(
    title: String,
    download: Download?,
    canDownload: Boolean,
    onStart: () -> Unit,
    onAction: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    overPicture: Boolean = false,
) {
    if (download == null && !canDownload) return

    var menu by remember { mutableStateOf(false) }
    val ensureNotifications = rememberDownloadNotificationPermission()
    val badge = download?.badge ?: DownloadBadge.None
    val description = if (download == null) Strings.downloadDescription(title) else Strings.downloadActionDescription(title, download.stateWord)

    Box(modifier) {
        IconButton(
            onClick = {
                if (download == null) {
                    ensureNotifications()
                    onStart()
                } else {
                    menu = true
                }
            },
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            if (overPicture) {
                BadgeOverPicture(badge = badge, progress = download?.progress, size = 32.dp)
            } else {
                DownloadBadgeIcon(badge, download?.progress, Modifier.size(26.dp), tint = if (badge == DownloadBadge.None) VibeyFinTokens.Colors.inkMuted else VibeyFinTokens.Colors.ink)
            }
        }

        VibeyFinMenu(expanded = menu, onDismiss = { menu = false }) {
            download?.actions?.forEach { action ->
                DownloadActionMenuItem(action) {
                    menu = false

                    onAction(action)
                }
            }
        }
    }
}
