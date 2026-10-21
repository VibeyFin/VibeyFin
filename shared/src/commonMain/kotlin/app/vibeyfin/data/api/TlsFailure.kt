package app.vibeyfin.data.api

/**
 * True when [this] (or something it wraps) is a TLS problem, such as a certificate that isn't
 * trusted or a plain-HTTP port answering an HTTPS request. Each HTTP engine reports these
 * differently, so the platform decides.
 */
expect fun Throwable.isTlsFailure(): Boolean
