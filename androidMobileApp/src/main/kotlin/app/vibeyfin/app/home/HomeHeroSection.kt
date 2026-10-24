package app.vibeyfin.app.home

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.vibeyfin.R
import app.vibeyfin.app.components.Artwork
import app.vibeyfin.app.components.SkeletonBlock
import app.vibeyfin.app.navigation.LocalTileLongPress
import app.vibeyfin.app.theme.animationsEnabled
import app.vibeyfin.design.VibeyFinTokens
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import app.vibeyfin.ui.home.HERO_ADVANCE_MILLIS
import app.vibeyfin.ui.home.heroFacts
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

// A banner rather than a full poster, so Continue watching stays near the top
private const val HERO_ASPECT = 4f / 3f
private const val HERO_ASPECT_WIDE = 2.6f

private const val HERO_WIDE_FROM_DP = 600

// A size smaller than the page's large pills: they sit on a picture
private val HERO_BUTTON_HEIGHT = 40.dp

/**
 * The one featured item, above Continue watching, as a rounded card inside the page's margins: its backdrop under a scrim, the Logo
 * as the title, a line saying what it is and its genres, then Play (the one aqua button) beside My List. Tapping the picture opens the
 * item's page. Play plays: a movie, or the episode a show is up to.
 */
@Composable
fun HomeHeroSection(
    item: MediaItem,
    wide: Boolean,
    onPlay: () -> Unit,
    onOpen: (String) -> Unit,
    onMyList: () -> Unit,
    description: String = Strings.featured(item.title),
    onLongPressed: () -> Unit = {},
) {
    val longPress = LocalTileLongPress.current
    val shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md)

    Box(
        Modifier
            // The grid around it already has the page margin, so the card runs from margin to margin
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, VibeyFinTokens.Colors.surface3, shape)
            .combinedClickable(
                onLongClickLabel = if (longPress != null) Strings.showOptions else null,
                onLongClick = longPress?.let { press ->
                    {
                        onLongPressed()
                        press(item)
                    }
                },
                onClick = { onOpen(item.id) },
            )
            .semantics { contentDescription = description },
    ) {
        Artwork(
            url = item.backdropUrl,
            title = item.title,
            aspectRatio = if (wide) HERO_ASPECT_WIDE else HERO_ASPECT,
            blurHash = item.backdropBlurHash,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Transparent, 0.75f to VibeyFinTokens.Colors.scrim, 1f to VibeyFinTokens.Colors.scrimStrong),
            ),
        )

        val align = if (wide) Alignment.Start else Alignment.CenterHorizontally

        Column(
            Modifier.align(if (wide) Alignment.BottomStart else Alignment.BottomCenter).fillMaxWidth().padding(horizontal = VibeyFinTokens.Space.s3).padding(bottom = VibeyFinTokens.Space.s3),
            horizontalAlignment = align,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        ) {
            AsyncImage(
                model = item.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = if (wide) Alignment.CenterStart else Alignment.Center,
                modifier = Modifier.fillMaxWidth(if (wide) 0.3f else 0.6f).heightIn(max = if (wide) 72.dp else 52.dp),
            )

            val facts = item.heroFacts

            if (facts.isNotEmpty()) {
                // "Show · Offbeat · Heartfelt · Comedy": what it is, then its genres
                Text(
                    Strings.factsLine(facts),
                    style = MaterialTheme.typography.labelLarge,
                    color = VibeyFinTokens.Colors.ink,
                    textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                    maxLines = 1,
                )
            }

            Row(Modifier.then(if (wide) Modifier.width(360.dp) else Modifier.fillMaxWidth()), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f).height(HERO_BUTTON_HEIGHT),
                ) {
                    Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null)
                    Spacer(Modifier.width(VibeyFinTokens.Space.s2))
                    Text(Strings.play, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }

                // Not aqua: aqua is the one primary action. A soft, see-through button over the picture.
                Button(
                    onClick = onMyList,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = VibeyFinTokens.Colors.ink),
                    modifier = Modifier.weight(1f).height(HERO_BUTTON_HEIGHT).semantics { contentDescription = Strings.myListAction(item.isFavorite) },
                ) {
                    Icon(if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null)
                    Spacer(Modifier.width(VibeyFinTokens.Space.s2))
                    Text(Strings.myList, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        }
    }
}

/**
 * The featured items: one card, or from two a pager of them with page dots under it. The pager moves on by itself every
 * [HERO_ADVANCE_MILLIS], round and round; a swipe or a long press stops that until Home is left, a finger on it pauses it, and it
 * never moves with animations removed, with TalkBack on, or while Home is not the screen in front. Play and My List act on their page.
 */
@Composable
fun HomeHeroCarousel(
    items: List<MediaItem>,
    wide: Boolean,
    onPlay: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMyList: (String) -> Unit,
) {
    if (items.size == 1) {
        val item = items.single()

        HomeHeroSection(item, wide, { onPlay(item.id) }, onOpen, { onMyList(item.id) })

        return
    }

    // The pages run round: a swipe past the last item comes to the first, and back from the first to the last. The pager has
    // many copies of the items and starts on a first item in the middle, so either way has room.
    val pager = rememberPagerState(initialPage = HERO_LOOP_START - HERO_LOOP_START % items.size) { HERO_LOOP_PAGES }
    val context = LocalContext.current
    val animations = animationsEnabled()
    val screenReaderOn = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }

    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)

    // A swipe, or a long press that opens the menu, stops the carousel for this visit; a new pick starts it again
    var swiped by remember(items.map { it.id }) { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val dragged by pager.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(dragged) { if (dragged) swiped = true }

    LaunchedEffect(resumed) { if (resumed) swiped = false }

    // Keyed on the settled page, not the current one: the current page changes halfway through the move, and restarting here then
    // would cancel the move and leave the card half way. After the last item comes the first, as with a swipe.
    LaunchedEffect(pager.settledPage, swiped, held, resumed) {
        val advanceBlocked = !animations || screenReaderOn || swiped || held || !resumed

        if (advanceBlocked) return@LaunchedEffect

        delay(HERO_ADVANCE_MILLIS)
        pager.animateScrollToPage(pager.settledPage + 1)
    }

    Column {
        HorizontalPager(
            state = pager,
            pageSpacing = VibeyFinTokens.Space.s2,
            modifier = Modifier.pointerInput(Unit) {
                // Seen before the pager takes the gesture, so a finger resting on the card pauses it without a swipe
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    held = true
                    waitForUpOrCancellation(PointerEventPass.Initial)
                    held = false
                }
            },
        ) { page ->
            val index = page % items.size
            val item = items[index]

            HomeHeroSection(
                item,
                wide,
                { onPlay(item.id) },
                onOpen,
                { onMyList(item.id) },
                description = Strings.featuredPage(item.title, index + 1, items.size),
                onLongPressed = { swiped = true },
            )
        }

        HeroPageDots(count = items.size, current = pager.currentPage % items.size)
    }
}

@Composable
private fun HeroPageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().height(HERO_DOTS_HEIGHT).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (index == current) VibeyFinTokens.Colors.ink else VibeyFinTokens.Colors.inkSubtle),
            )
        }
    }
}

private const val HERO_LOOP_PAGES = 10_000

private const val HERO_LOOP_START = HERO_LOOP_PAGES / 2

// The skeleton keeps it too, so the card does not move
private val HERO_DOTS_HEIGHT = 24.dp

/**
 * The hero's place while it is being picked: the same card, rounded and the same height, with gray stand-ins for the logo, the line and
 * the two buttons; with [dots], the dots' row under it is kept empty.
 */
@Composable
fun HomeHeroSkeleton(wide: Boolean, dots: Boolean = false) {
    Column {
        HeroSkeletonCard(wide)

        if (dots) Spacer(Modifier.height(HERO_DOTS_HEIGHT))
    }
}

@Composable
private fun HeroSkeletonCard(wide: Boolean) {
    val shape = RoundedCornerShape(VibeyFinTokens.Radius.m3Md)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, VibeyFinTokens.Colors.surface3, shape)
            .background(VibeyFinTokens.Colors.surface2)
            .semantics { contentDescription = Strings.loadingPage },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (wide) HERO_ASPECT_WIDE else HERO_ASPECT))
        Column(
            Modifier.align(if (wide) Alignment.BottomStart else Alignment.BottomCenter).fillMaxWidth().padding(horizontal = VibeyFinTokens.Space.s3).padding(bottom = VibeyFinTokens.Space.s3),
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2),
        ) {
            SkeletonBlock(Modifier.width(if (wide) 160.dp else 180.dp).height(40.dp), RoundedCornerShape(VibeyFinTokens.Radius.m3Xs), VibeyFinTokens.Colors.surface3)
            SkeletonBlock(Modifier.width(150.dp).height(16.dp), RoundedCornerShape(VibeyFinTokens.Radius.m3Xs), VibeyFinTokens.Colors.surface3)
            Row(Modifier.then(if (wide) Modifier.width(360.dp) else Modifier.fillMaxWidth()), horizontalArrangement = Arrangement.spacedBy(VibeyFinTokens.Space.s2)) {
                SkeletonBlock(Modifier.weight(1f).height(HERO_BUTTON_HEIGHT), RoundedCornerShape(50), VibeyFinTokens.Colors.surface3)
                SkeletonBlock(Modifier.weight(1f).height(HERO_BUTTON_HEIGHT), RoundedCornerShape(50), VibeyFinTokens.Colors.surface3)
            }
        }
    }
}
