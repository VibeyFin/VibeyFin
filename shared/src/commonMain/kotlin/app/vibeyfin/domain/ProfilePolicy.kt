package app.vibeyfin.domain

import app.vibeyfin.data.api.AccessScheduleDto
import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.data.api.valueOrNull
import kotlin.math.roundToInt

/** The days an [AccessWindow] covers: Jellyfin's `DynamicDayOfWeek`. */
enum class AccessDays { Everyday, Weekday, Weekend, Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday }

/**
 * One access schedule: the hours of the day, in minutes after midnight, when the user may watch. A window whose
 * [startMinutes] is after [endMinutes] runs overnight; [endMinutes] is 1440 for the end of the day.
 */
data class AccessWindow(val days: AccessDays, val startMinutes: Int, val endMinutes: Int)

/**
 * What the Jellyfin administrator allows the signed-in user, as data; the screens word it. [maxRatingLevel] is the
 * rating cap (0 is the strictest) and [ratingName] the server's name for it when it has one; `null` means no cap.
 */
data class PolicySummary(
    val maxRatingLevel: Int?,
    val ratingName: String?,
    val blockedTags: List<String>,
    val schedules: List<AccessWindow>,
)

/** Reads the signed-in user's Jellyfin policy (`GET /Users/Me`) so the app can show the limits it already obeys. */
class ProfilePolicy(private val connection: ConnectionManager) {
    suspend fun load(): ApiResult<PolicySummary> {
        return connection.asConnected<ApiResult<PolicySummary>>(ApiResult.Failure(ApiError.Unauthorized)) { connected ->
            val user = when (val me = connected.api.me()) {
                is ApiResult.Success -> me.value
                is ApiResult.Failure -> return@asConnected me
            }
            val policy = user.policy
            val level = policy?.maxParentalRating
            // The names are a nicety: if the list can't be had, the static map and then no name stand in.
            val name = level?.let { ratingName(it, connected.api.parentalRatings().valueOrNull()?.map { r -> r.name to r.value }) }
            ApiResult.Success(
                PolicySummary(
                    maxRatingLevel = level,
                    ratingName = name,
                    blockedTags = policy?.blockedTags.orEmpty(),
                    schedules = policy?.accessSchedules.orEmpty().map { it.toWindow() },
                ),
            )
        }
    }

    private companion object {
        /** Approximate US names, used only when the server's own list can't be read. Several names share a level. */
        val FALLBACK = mapOf(5 to "G", 7 to "TV-Y7", 10 to "PG", 13 to "PG-13", 14 to "TV-14", 17 to "R", 18 to "NC-17")

        fun ratingName(level: Int, server: List<Pair<String?, Int?>>?): String? = server?.firstOrNull { it.second == level && !it.first.isNullOrBlank() }?.first ?: FALLBACK[level]

        fun AccessScheduleDto.toWindow() = AccessWindow(
            days = AccessDays.entries.firstOrNull { it.name.equals(dayOfWeek, ignoreCase = true) } ?: AccessDays.Everyday,
            startMinutes = (startHour * 60).roundToInt(),
            endMinutes = (endHour * 60).roundToInt(),
        )
    }
}
