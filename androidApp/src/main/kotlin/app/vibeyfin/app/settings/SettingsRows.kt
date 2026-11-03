package app.vibeyfin.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.CaptionStyleCompat
import app.vibeyfin.app.components.VibeyFinMenu
import app.vibeyfin.app.components.VibeyFinMenuItem
import app.vibeyfin.app.player.SUBTITLE_SHADOW
import app.vibeyfin.app.player.SUBTITLE_WHITE
import app.vibeyfin.app.player.SubtitleViewStyle
import app.vibeyfin.app.theme.VibeyFinTokens
import app.vibeyfin.ui.common.AvatarTint
import app.vibeyfin.ui.settings.ActionItem
import app.vibeyfin.ui.settings.ChoiceItem
import app.vibeyfin.ui.settings.InfoItem
import app.vibeyfin.ui.settings.LinkItem
import app.vibeyfin.ui.settings.SettingKey
import app.vibeyfin.ui.settings.SettingOption
import app.vibeyfin.ui.settings.ToggleItem

private const val DISABLED_ALPHA = 0.38f

private fun Modifier.dimmedUnless(enabled: Boolean) = if (enabled) this else alpha(DISABLED_ALPHA)

private val RowShape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md)

/** A group's title above its card, in aqua text (`android.md`). */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4),
    )
}

/** The help text under a group's card. */
@Composable
fun SectionFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = VibeyFinTokens.Space.s4),
    )
}

/** A group's rounded tonal card. */
@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(content = { content() })
    }
}

@Composable
internal fun RowText(title: String, help: String?, modifier: Modifier = Modifier, titleColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1 / 2)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = titleColor)

        if (help != null) Text(text = help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RowFrame(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = VibeyFinTokens.Size.touch + VibeyFinTokens.Space.s2).padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s4),
    ) { content() }
}

/** A title with its value at the right, such as a server's version. */
@Composable
internal fun ValueRow(title: String, value: String) {
    RowFrame {
        RowText(title, null)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = VibeyFinTokens.Space.s4),
        )
    }
}

/** A row that does one thing when tapped, in red when it can't be undone. */
@Composable
internal fun ButtonRow(title: String, onClick: () -> Unit, destructive: Boolean = false, leading: (@Composable () -> Unit)? = null) {
    RowFrame(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        leading?.invoke()
        RowText(title, null, Modifier.weight(1f), titleColor = if (destructive) VibeyFinTokens.Colors.danger else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun IconCircle(icon: ImageVector) {
    Box(
        modifier = Modifier.size(VibeyFinTokens.Size.button).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

private fun iconFor(key: SettingKey): ImageVector? = when (key) {
    SettingKey.ProfileCard -> Icons.Filled.Person

    SettingKey.Quality -> Icons.Filled.Star

    SettingKey.AudioSubtitles -> Icons.Filled.Face

    SettingKey.AudioLanguage, SettingKey.SubtitleLanguage -> null

    SettingKey.Skipping -> Icons.Filled.PlayArrow

    SettingKey.BufferAhead -> Icons.Filled.Settings

    SettingKey.HomeGenreRows, SettingKey.HomeHeroItems -> Icons.Filled.Home

    SettingKey.Notifications -> Icons.Filled.Notifications

    SettingKey.KidsProfile, SettingKey.Pin -> Icons.Filled.Lock

    SettingKey.Seerr -> Icons.Filled.Add

    // Its icon is the menu's; on the Profile page it sits under Switch profile, which has none
    SettingKey.QuickConnect -> null

    else -> Icons.Filled.Info
}

@Composable
fun ToggleRow(item: ToggleItem, onToggle: (Boolean) -> Unit) {
    RowFrame(
        // The whole row toggles, not just the switch, so the label is a target too
        modifier = Modifier.dimmedUnless(item.enabled).toggleable(value = item.checked, enabled = item.enabled, role = Role.Switch, onValueChange = onToggle),
    ) {
        RowText(item.title, item.help, Modifier.weight(1f))

        // Aqua is for the one primary action, so the switch is light on dark instead
        Switch(
            checked = item.checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VibeyFinTokens.Colors.bg,
                checkedTrackColor = VibeyFinTokens.Colors.ink,
                uncheckedThumbColor = VibeyFinTokens.Colors.inkMuted,
                uncheckedTrackColor = VibeyFinTokens.Colors.surface3,
                uncheckedBorderColor = VibeyFinTokens.Colors.lineStrong,
            ),
        )
    }
}

/** A row with its value at the right; a menu of the options hangs from the value. */
@Composable
fun ChoiceRow(item: ChoiceItem, onChoose: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }

    RowFrame(modifier = Modifier.dimmedUnless(item.enabled).clickable(enabled = item.enabled, role = Role.DropdownList) { open = true }) {
        RowText(item.title, item.help, Modifier.weight(1f))

        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.valueLabel()?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            VibeyFinMenu(expanded = open, onDismiss = { open = false }) {
                item.options.forEach { option ->
                    VibeyFinMenuItem(option.label, icon = null, selected = option.value == item.value) {
                        open = false

                        onChoose(option.value)
                    }
                }
            }
        }
    }
}

/** One option of an inline checklist: the chosen one is an `accent-soft` pill, with no tick. */
@Composable
fun ChecklistRow(option: SettingOption, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = VibeyFinTokens.Space.s2, vertical = VibeyFinTokens.Space.s1)) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (selected) VibeyFinTokens.Colors.onAccentSoft else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VibeyFinTokens.Size.touch)
                .clip(CircleShape)
                .background(if (selected) VibeyFinTokens.Colors.accentSoft else Color.Transparent)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = VibeyFinTokens.Space.s4, vertical = VibeyFinTokens.Space.s3),
        )
    }
}

/** [selected] is for the tablet's list pane: the open page is an `accent-soft` pill. Null (the phone) draws no pill. */
@Composable
fun LinkRow(item: LinkItem, selected: Boolean? = null, onOpen: () -> Unit) {
    val content: @Composable (Modifier) -> Unit = { modifier ->
        RowFrame(modifier = modifier) {
            iconFor(item.key)?.let { IconCircle(it) }
            RowText(
                item.title,
                item.help,
                Modifier.weight(1f),
                titleColor = if (selected == true) VibeyFinTokens.Colors.onAccentSoft else MaterialTheme.colorScheme.onSurface,
            )
            item.value?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (selected == null) {
        content(Modifier.dimmedUnless(item.enabled).clickable(enabled = item.enabled, role = Role.Button, onClick = onOpen))
    } else {
        Box(Modifier.padding(horizontal = VibeyFinTokens.Space.s2, vertical = VibeyFinTokens.Space.s1)) {
            content(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) VibeyFinTokens.Colors.accentSoft else Color.Transparent)
                    .selectable(selected = selected, role = Role.Button, onClick = onOpen),
            )
        }
    }
}

@Composable
fun ActionRow(item: ActionItem, onClick: () -> Unit) {
    RowFrame(modifier = Modifier.dimmedUnless(item.enabled).clickable(enabled = item.enabled, role = Role.Button, onClick = onClick)) {
        RowText(
            item.title,
            item.help,
            Modifier.weight(1f),
            titleColor = if (item.destructive) VibeyFinTokens.Colors.danger else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// A longer value is drawn under its title, where it has the whole row to wrap in
private const val INLINE_VALUE_MAX = 16

@Composable
fun InfoRow(item: InfoItem) {
    val value = @Composable { modifier: Modifier, align: TextAlign ->
        Text(
            item.value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = modifier,
        )
    }

    if (item.value.length > INLINE_VALUE_MAX) {
        RowFrame {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s1 / 2)) {
                RowText(item.title, item.help)
                value(Modifier, TextAlign.Start)
            }
        }
    } else {
        RowFrame {
            RowText(item.title, item.help, Modifier.weight(1f))
            value(Modifier.weight(1f, fill = false), TextAlign.End)
        }
    }
}

/** The profile page's top row: the initial on the profile's color, the name and the server it is on. */
@Composable
fun ProfileHeaderRow(name: String, server: String?) {
    RowFrame {
        Box(
            modifier = Modifier.size(VibeyFinTokens.Size.buttonLarge).clip(CircleShape).background(VibeyFinTokens.Colors.avatars[AvatarTint.of(name)]),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = VibeyFinTokens.Colors.ink)
        }

        RowText(name, server, Modifier.weight(1f))
    }
}

// Captions mimic subtitles over video, so they are white and black like the player's, not brand tokens
private val CaptionText = Color(SUBTITLE_WHITE)
private val CaptionShadow = Color(SUBTITLE_SHADOW)

private val PreviewHeight = 120.dp

/** A standalone card: a sample line at the bottom of a dark ground, drawn with the style the profile has chosen. */
@Composable
internal fun SubtitlePreviewCard(sample: String, style: SubtitleViewStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .clip(RowShape)
            .background(Brush.verticalGradient(listOf(VibeyFinTokens.Colors.surface3, VibeyFinTokens.Colors.bg)))
            .padding(VibeyFinTokens.Space.s4),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // The system style is the platform's to draw; the preview shows the default look for it
        val size = if (style.followSystem) 0.0533f else style.sizeFraction
        val color = if (style.followSystem) CaptionText else Color(style.foreground)
        val box = if (style.followSystem) Color.Transparent else Color(style.background)

        Text(
            text = sample,
            color = color,
            style = TextStyle(
                fontSize = (size * PREVIEW_PLAYER_HEIGHT).sp,
                shadow = if (!style.followSystem && style.edgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) Shadow(CaptionShadow, blurRadius = 6f) else null,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.background(box, RoundedCornerShape(VibeyFinTokens.Radius.m3Xs)).padding(horizontal = VibeyFinTokens.Space.s1),
        )
    }
}

private const val PREVIEW_PLAYER_HEIGHT = 360f

/** The PIN row: its title and four lit dots, read out as one description. */
@Composable
fun PinDotsItemRow(title: String, description: String?) {
    RowFrame {
        RowText(title, null, Modifier.weight(1f))
        PinDotsRow(filled = 4, description = description ?: title)
    }
}
