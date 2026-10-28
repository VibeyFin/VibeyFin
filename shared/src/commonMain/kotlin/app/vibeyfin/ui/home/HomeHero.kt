package app.vibeyfin.ui.home

import app.vibeyfin.domain.model.ItemKind
import app.vibeyfin.domain.model.MediaItem
import app.vibeyfin.ui.common.Strings
import kotlin.random.Random

/**
 * The items Home features this session, at most [count] and never one twice: from what was added most recently and from My List, only
 * those with both a `Backdrop` and a `Logo` picture (the Logo is the title on the hero, so one without it does not qualify) and that
 * the user has not watched, in a random order. Picked once and kept until the session ends. Fewer qualify: only those; none, and Home
 * is as it would be without a hero.
 */
internal fun pickHeroes(candidates: List<MediaItem>, count: Int, random: Random): List<MediaItem> =
    candidates
        .distinctBy { it.id }
        // Something already watched is not worth featuring.
        .filter { it.backdropUrl != null && it.logoUrl != null && !it.played }
        .shuffled(random)
        .take(count)

/**
 * How long a page of the hero's carousel shows before it moves on by itself, in milliseconds. It goes through once and rests on the
 * last item; both apps stop it on a swipe and never run it under Reduce Motion or with a screen reader on (`guardrails.md`).
 */
const val HERO_ADVANCE_MILLIS = 8_000L

/** The line under the hero's logo, as on Netflix: what it is ("Movie", "Show"), then up to four of its genres. */
val MediaItem.heroFacts: List<String>
    get() = listOfNotNull(if (kind == ItemKind.Series) Strings.heroShow else if (kind == ItemKind.Movie) Strings.heroMovie else null) + genres.take(4)
