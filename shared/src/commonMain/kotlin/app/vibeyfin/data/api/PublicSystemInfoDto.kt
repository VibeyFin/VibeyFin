package app.vibeyfin.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /System/Info/Public`, which needs no sign-in. Field names and nullability follow the
 * server's OpenAPI spec (`PublicSystemInfo`); the spec marks every field nullable.
 */
@Serializable
data class PublicSystemInfoDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("ProductName") val productName: String? = null,
)
