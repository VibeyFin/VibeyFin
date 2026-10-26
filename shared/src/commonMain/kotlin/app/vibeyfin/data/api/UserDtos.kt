package app.vibeyfin.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `UserDto` from the server's OpenAPI spec, with only the fields we read. */
@Serializable
data class UserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("HasPassword") val hasPassword: Boolean? = null,
    /** What the server lets this user do; only sent for a signed-in user. */
    @SerialName("Policy") val policy: UserPolicyDto? = null,
)

/** `UserPolicy`, with only the fields we read: the limits an administrator can put on a user, and whether the user may download. */
@Serializable
data class UserPolicyDto(
    /** Whether the user may download media. The server's own default, when it leaves the field out, is that they may. */
    @SerialName("EnableContentDownloading") val enableContentDownloading: Boolean = true,
    @SerialName("MaxParentalRating") val maxParentalRating: Int? = null,
    @SerialName("BlockedTags") val blockedTags: List<String>? = null,
    @SerialName("AccessSchedules") val accessSchedules: List<AccessScheduleDto>? = null,
)

/** `AccessSchedule`: [dayOfWeek] is Jellyfin's `DynamicDayOfWeek` (Sunday to Saturday, Everyday, Weekday, Weekend). */
@Serializable
data class AccessScheduleDto(
    @SerialName("DayOfWeek") val dayOfWeek: String? = null,
    @SerialName("StartHour") val startHour: Double = 0.0,
    @SerialName("EndHour") val endHour: Double = 0.0,
)

@Serializable
data class AuthenticateByNameDto(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String,
)

@Serializable
data class AuthenticationResultDto(
    @SerialName("User") val user: UserDto,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
)

@Serializable
data class QuickConnectResultDto(
    @SerialName("Authenticated") val authenticated: Boolean = false,
    @SerialName("Secret") val secret: String,
    @SerialName("Code") val code: String,
)

@Serializable
data class QuickConnectRequestDto(
    @SerialName("Secret") val secret: String,
)

/** `ParentalRating`: a rating name and the level it stands for. Several names can share a level. */
@Serializable
data class ParentalRatingDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("Value") val value: Int? = null,
)
