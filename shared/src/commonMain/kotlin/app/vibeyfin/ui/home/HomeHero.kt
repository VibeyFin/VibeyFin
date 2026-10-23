package app.vibeyfin.ui.home

import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import kotlin.random.Random

/**
 * The one item Home features this session: from what was added most recently and from My List, only those with both a `Backdrop`
 * and a `Logo` picture (the Logo is the title on the hero, so one without it does not qualify) and that the user has not watched. Never a carousel: one pick, kept
 * until the session ends. Null when nothing qualifies, and Home is as it would be without a hero.
 */
internal fun pickHero(candidates: List<MediaItem>, random: Random): MediaItem? =
    candidates
        .distinctBy { it.id }
        // Something already watched is not worth featuring.
        .filter { it.backdropUrl != null && it.logoUrl != null && !it.played }
        .let { qualified -> if (qualified.isEmpty()) null else qualified[random.nextInt(qualified.size)] }

/** The line under the hero's logo, as on Netflix: what it is ("Movie", "Show"), then up to four of its genres. */
val MediaItem.heroFacts: List<String>
    get() = listOfNotNull(if (kind == ItemKind.Series) Strings.heroShow else if (kind == ItemKind.Movie) Strings.heroMovie else null) + genres.take(4)
