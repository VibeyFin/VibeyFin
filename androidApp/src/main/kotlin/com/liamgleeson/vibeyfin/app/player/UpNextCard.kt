package com.liamgleeson.vibeyfin.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liamgleeson.vibeyfin.R
import com.liamgleeson.vibeyfin.app.components.Artwork
import com.liamgleeson.vibeyfin.app.theme.VibeyFinTokens
import com.liamgleeson.vibeyfin.ui.common.Strings
import com.liamgleeson.vibeyfin.ui.playback.UpNext

/**
 * Offers the next episode in the last seconds of this one: its picture, "S1 E2 · title", how many seconds
 * until it starts on its own, "Play now" (the one aqua action while it is up) and "Watch credits", which
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
                    text = listOfNotNull(upNext.label, upNext.title).joinToString(" · "),
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
            Button(onClick = onPlayNow, modifier = Modifier.weight(1f)) {
                Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(Strings.playNow, maxLines = 1)
            }
            FilledTonalButton(onClick = onWatchCredits, modifier = Modifier.weight(1f)) {
                Text(Strings.watchCredits, maxLines = 1)
            }
        }
    }
}

private val UP_NEXT_WIDTH = 340.dp
