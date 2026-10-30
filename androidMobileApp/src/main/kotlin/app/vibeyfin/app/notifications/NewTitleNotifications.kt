package app.vibeyfin.app.notifications

import app.vibeyfin.domain.ProfileNewTitles
import app.vibeyfin.ui.common.Strings

/**
 * One notification to post. [tag] is unique per notification (posting the same tag again replaces it); episodes of one
 * show share a [group] with a [summary] over them. Tapping opens [itemId] as the profile it belongs to.
 */
data class PlannedNotification(
    val tag: String,
    val title: String,
    val body: String,
    val group: String?,
    val summary: Boolean,
    val serverId: String,
    val userId: String,
    val itemId: String,
)

/**
 * The notifications for what a poll found: one per new movie, and one per new episode grouped by show under a summary
 * that each poll posts again for the shows it found (one summary per show, replaced each time). With [nameProfiles] (more than one profile on this device gets notifications) each says whose it is.
 */
fun planNotifications(found: List<ProfileNewTitles>, nameProfiles: Boolean): List<PlannedNotification> = found.flatMap { profile ->
    val who = profile.profileName.takeIf { nameProfiles }
    val prefix = "${profile.serverId}/${profile.userId}"

    fun planned(tag: String, title: String, body: String, group: String?, summary: Boolean, itemId: String) = PlannedNotification(tag, title, body, group, summary, profile.serverId, profile.userId, itemId)

    val movies = profile.titles.filter { it.isMovie }.map {
        planned("$prefix/${it.itemId}", Strings.addedMovieNotificationTitle, Strings.addedTitleNotificationBody(it.title, who), null, false, it.itemId)
    }

    val shows = profile.titles.filterNot { it.isMovie }.groupBy { it.seriesName }.flatMap { (series, episodes) ->
        val group = "$prefix/show/${series.orEmpty()}"
        val each = episodes.map {
            planned("$prefix/${it.itemId}", Strings.addedEpisodeNotificationTitle(series), Strings.addedTitleNotificationBody(it.title, who), group, false, it.itemId)
        }

        // Every check that posts to a show's group posts its summary too, under one tag per show, so it replaces the last one
        // and the group holds together across checks (Android shows group children without a summary one by one).
        each + planned(group, Strings.addedEpisodesSummary(series), Strings.addedEpisodesSummaryBody(episodes.map { it.title }, who), group, true, episodes.first().itemId)
    }

    movies + shows
}
