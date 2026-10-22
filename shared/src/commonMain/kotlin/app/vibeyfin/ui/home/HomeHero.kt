package app.vibeyfin.ui.home

import app.vibeyfin.domain.model.MediaItem
import kotlin.random.Random

/**
 * The one item Home features this session: from what was added most recently and from My List, only those with both a `Backdrop`
 * and a `Logo` picture (the Logo is the title on the hero, so one without it does not qualify). Never a carousel: one pick, kept
 * until the session ends. Null when nothing qualifies, and Home is as it would be without a hero.
 */
internal fun pickHero(candidates: List<MediaItem>, random: Random): MediaItem? =
    candidates
        .distinctBy { it.id }
        .filter { it.backdropUrl != null && it.logoUrl != null }
        .let { qualified -> if (qualified.isEmpty()) null else qualified[random.nextInt(qualified.size)] }

/** The facts under the hero's logo: the year and up to two genres. */
val MediaItem.heroFacts: List<String>
    get() = listOfNotNull(year?.toString()) + genres.take(2)
