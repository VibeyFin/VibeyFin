package com.liamgleeson.vibeyfin.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `UserDto` from the server's OpenAPI spec, with only the fields we read. */
@Serializable
data class UserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("HasPassword") val hasPassword: Boolean? = null,
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
