package app.vibeyfin.domain.model

/** Someone from the cast: what the server knows about them, for their page. */
data class Person(
    val id: String,
    val name: String,
    /** The biography; null when no metadata provider supplied one. */
    val overview: String?,
    /** The year they were born, when known. */
    val bornYear: Int?,
    val birthplace: String?,
    val imageUrl: String?,
    /** The BlurHash of [imageUrl]'s picture. */
    val imageBlurHash: String?,
)
