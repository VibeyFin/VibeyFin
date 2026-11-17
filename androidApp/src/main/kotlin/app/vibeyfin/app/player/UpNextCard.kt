package app.vibeyfin.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibeyfin.R
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.playback.UpNext

/**
 * Offers the next episode when the credits start: its picture, "S1 E2 · title", how many seconds
 * until it starts on its own, "Play now" and "Watch credits", which
 * dismisses it. It sits on the scrim, over the video.
 */
@Composable
fun UpNextCard(upNext: UpNext, onPlayNow: () -> Unit, onWatchCredits: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(UP_NEXT_WIDTH)
            .clip(RoundedCornerShape(VibeyFinTokens.Radius.m3Md))
            .background(VibeyFinTokens.Colors.scrimStrong)
            .padding(VibeyFinTokens.Space.s3),
        verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                url = upNext.imageUrl,
                title = upNext.title,
                aspectRatio = 16f / 9f,
                blurHash = upNext.imageBlurHash,
                modifier = Modifier.width(112.dp).clip(RoundedCornerShape(VibeyFinTokens.Radius.artwork)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = Strings.upNext,
                    style = MaterialTheme.typography.labelMedium,
                    color = VibeyFinTokens.Colors.inkMuted,
                )
                Text(
                    text = Strings.taggedTitle(upNext.label, upNext.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = VibeyFinTokens.Colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = Strings.upNextIn(upNext.secondsLeft),
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeyFinTokens.Colors.inkMuted,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onPlayNow, contentPadding = UP_NEXT_BUTTON_PADDING, modifier = Modifier.weight(1f).heightIn(min = UP_NEXT_BUTTON_HEIGHT)) {
                Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(Strings.playNow, maxLines = 2, textAlign = TextAlign.Center)
            }

            FilledTonalButton(onClick = onWatchCredits, contentPadding = UP_NEXT_BUTTON_PADDING, modifier = Modifier.weight(1f).heightIn(min = UP_NEXT_BUTTON_HEIGHT)) {
                Text(Strings.watchCredits, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

private val UP_NEXT_WIDTH = 340.dp
private val UP_NEXT_BUTTON_HEIGHT = 44.dp
private val UP_NEXT_BUTTON_PADDING = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
