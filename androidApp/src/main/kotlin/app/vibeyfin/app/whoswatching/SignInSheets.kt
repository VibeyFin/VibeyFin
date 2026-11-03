package app.vibeyfin.app.whoswatching

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vibeyfin.R
import app.vibeyfin.app.components.BusyButton
import app.vibeyfin.app.components.PasswordField
import app.vibeyfin.app.components.SheetColumn
import app.vibeyfin.app.components.SheetTextButton
import app.vibeyfin.app.components.SheetTitle
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.components.UsernameField
import app.vibeyfin.app.components.VibeyFinSheet
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.whoswatching.QuickConnectStop
import app.vibeyfin.ui.whoswatching.SignInSheet
import app.vibeyfin.ui.whoswatching.WhoIsWatchingUiState
import kotlinx.coroutines.launch

private const val EXPIRED_ALPHA = 0.5f

@Composable
internal fun SignInSheet(
    sheet: SignInSheet,
    state: WhoIsWatchingUiState,
    onSubmitPassword: (String) -> Unit,
    onSubmitOtherUser: (String, String) -> Unit,
    onQuickConnect: () -> Unit,
    onUsePassword: () -> Unit,
    onDismiss: () -> Unit,
) {
    VibeyFinSheet(onDismiss) {
        when (sheet) {
            is SignInSheet.QuickConnect -> QuickConnectContent(sheet, onQuickConnect, onUsePassword, onDismiss)
            is SignInSheet.Password, SignInSheet.OtherUser -> PasswordContent(sheet, state, onSubmitPassword, onSubmitOtherUser, onQuickConnect, onDismiss)
        }
    }
}

@Composable
private fun PasswordContent(
    sheet: SignInSheet,
    state: WhoIsWatchingUiState,
    onSubmitPassword: (String) -> Unit,
    onSubmitOtherUser: (String, String) -> Unit,
    onQuickConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var username by rememberSaveable(sheet) { mutableStateOf("") }
    var password by rememberSaveable(sheet) { mutableStateOf("") }
    val profile = (sheet as? SignInSheet.Password)?.profile
    val submit = {
        if (!state.signingIn) {
            if (profile != null) onSubmitPassword(password) else onSubmitOtherUser(username, password)
        }
    }

    SheetColumn(onCancel = onDismiss, footnote = Strings.passwordNotKept) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
            profile?.let { Avatar(it, 40.dp) }
            SheetTitle(profile?.let { Strings.signInAs(it.name) } ?: Strings.signInAsAnotherUser)
        }
        state.sheetNotice?.let { Note(it) }

        if (profile == null) UsernameField(username, { username = it }, marked = state.error != null)
        PasswordField(password, { password = it }, onGo = submit, error = state.error)
        BusyButton(
            label = Strings.signIn,
            onClick = submit,
            busy = state.signingIn,
            busyLabel = Strings.signingIn,
            enabled = profile != null || username.isNotBlank(),
        )
        if (state.quickConnectAvailable) SheetTextButton(Strings.useQuickConnectInstead, onQuickConnect)
    }
}

@Composable
private fun QuickConnectContent(sheet: SignInSheet.QuickConnect, onQuickConnect: () -> Unit, onUsePassword: () -> Unit, onDismiss: () -> Unit) {
    SheetColumn(onCancel = onDismiss.takeUnless { sheet.approved }) {
        SheetTitle(Strings.quickConnect)

        when {
            sheet.approved -> Approved()

            sheet.stop == QuickConnectStop.Off -> {
                Text(sheet.message.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                BusyButton(Strings.signInWithPassword, onUsePassword)
            }

            sheet.stop != null -> {
                if (sheet.stop == QuickConnectStop.Expired) {
                    Lead(Strings.quickConnectLead)
                    CodePanel(sheet)
                }
                Problem(sheet.message.orEmpty())
                BusyButton(if (sheet.stop == QuickConnectStop.Expired) Strings.quickConnectNewCode else Strings.tryAgain, onQuickConnect)
                SheetTextButton(Strings.signInWithPassword, onUsePassword)
            }

            else -> {
                Lead(Strings.quickConnectLead)
                Steps()
                CodePanel(sheet)
                Waiting(sheet.status ?: Strings.quickConnectGettingCode)
                SheetTextButton(Strings.signInWithPassword, onUsePassword)
            }
        }
    }
}

@Composable
private fun Lead(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Steps() {
    Column(verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        Strings.quickConnectSteps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3)) {
                Box(
                    modifier = Modifier.size(22.dp).clip(CircleShape).background(VibeyFinTokens.Colors.surface3),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun CodePanel(sheet: SignInSheet.QuickConnect) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val expired = sheet.stop == QuickConnectStop.Expired

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
            .background(VibeyFinTokens.Colors.surface2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val code = sheet.code
        if (code == null) {
            repeat(6) { index ->
                SkeletonBlock(
                    Modifier.padding(start = if (index == 3) 16.dp else 6.dp).size(width = 24.dp, height = 36.dp),
                    RoundedCornerShape(6.dp),
                    VibeyFinTokens.Colors.surface3,
                )
            }
        } else {
            Text(
                text = sheet.shownCode.orEmpty(),
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum", letterSpacing = 4.sp),
                color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EXPIRED_ALPHA) else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (expired) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f).padding(start = VibeyFinTokens.Size.touch).clearAndSetSemantics {
                    contentDescription = sheet.spokenCode.orEmpty()
                },
                textAlign = TextAlign.Center,
            )
            if (expired) {
                Box(Modifier.size(VibeyFinTokens.Size.touch))
            } else {
                IconButton(onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(Strings.quickConnect, code))) }
                    // From Android 13 the system shows what was copied itself
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, Strings.codeCopied, Toast.LENGTH_SHORT).show()
                }) {
                    Icon(painterResource(R.drawable.ic_copy), contentDescription = Strings.copyCode, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Waiting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Problem(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1)) {
        Icon(painterResource(R.drawable.ic_seerr_failed), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Approved() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = VibeyFinTokens.Space.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(VibeyFinTokens.Colors.surface3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = Strings.quickConnectApproved,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Waiting(Strings.quickConnectSigningIn)
    }
}
