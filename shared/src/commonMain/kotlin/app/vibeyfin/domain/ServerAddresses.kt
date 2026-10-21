package app.vibeyfin.domain

/** Turns what the user typed into the addresses worth probing. */
object ServerAddresses {
    private const val HTTP_DEFAULT_JELLYFIN_PORT = 8096
    private const val HTTPS_DEFAULT_JELLYFIN_PORT = 8920

    /**
     * Candidate base URLs for [input], best guess first, or empty when it can't be an address.
     *
     * - With a scheme, only that scheme is tried; without one, `https` then `http`.
     * - Without a port, the bare host (80/443, for reverse proxies) is tried first, then
     *   Jellyfin's own default: 8920 for https, 8096 for http. A given port is used as is.
     * - Results are normalised: lower-case host, default ports dropped, no trailing slash, and
     *   no `/web` or `/web/index.html` on the end. A base path such as `/jellyfin` is kept.
     */
    fun candidates(input: String): List<String> {
        val parsed = parse(input) ?: return emptyList()
        val schemes = parsed.scheme?.let(::listOf) ?: listOf("https", "http")
        return schemes.flatMap { scheme ->
            val ports = parsed.port?.let(::listOf) ?: listOf(null, defaultJellyfinPort(scheme))
            ports.map { port -> parsed.toUrl(scheme, port) }
        }.distinct()
    }

    private class Parsed(val scheme: String?, val host: String, val port: Int?, val path: List<String>) {
        fun toUrl(scheme: String, port: Int?): String = buildString {
            append(scheme).append("://").append(if (':' in host) "[$host]" else host)
            val explicit = port?.takeUnless { it == defaultPort(scheme) }
            if (explicit != null) append(':').append(explicit)
            path.forEach { append('/').append(it) }
        }
    }

    private fun parse(input: String): Parsed? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null

        val schemeEnd = text.indexOf("://")
        val scheme = if (schemeEnd >= 0) text.substring(0, schemeEnd).lowercase() else null
        if (scheme != null && scheme != "http" && scheme != "https") return null

        val rest = (if (schemeEnd >= 0) text.substring(schemeEnd + 3) else text)
            .substringBefore('#')
            .substringBefore('?')
        val authority = rest.substringBefore('/')
        if ('@' in authority) return null

        val (host, port) = parseAuthority(authority) ?: return null
        return Parsed(scheme, host, port, normalisePath(rest.substringAfter('/', missingDelimiterValue = "")))
    }

    private fun parseAuthority(authority: String): Pair<String, Int?>? {
        val host: String
        val portText: String?
        if (authority.startsWith('[')) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            host = authority.substring(1, close).lowercase()
            val after = authority.substring(close + 1)
            portText = when {
                after.isEmpty() -> null
                after.startsWith(':') -> after.substring(1)
                else -> return null
            }
            if (host.isEmpty() || host.any { !(it.isDigit() || it in 'a'..'f' || it == ':' || it == '.') }) return null
        } else {
            if (authority.count { it == ':' } > 1) return null
            host = authority.substringBefore(':').trimEnd('.').lowercase()
            portText = if (':' in authority) authority.substringAfter(':') else null
            if (host.isEmpty() || host.startsWith('.')) return null
            if (host.any { !(it in 'a'..'z' || it.isDigit() || it == '-' || it == '.' || it == '_') }) return null
        }
        val port = portText?.let { text ->
            text.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        }
        return host to port
    }

    private fun normalisePath(path: String): List<String> {
        val segments = path.split('/').filter { it.isNotEmpty() }.toMutableList()
        if (segments.lastOrNull().equals("index.html", ignoreCase = true)) segments.removeLast()
        if (segments.lastOrNull().equals("web", ignoreCase = true)) segments.removeLast()
        return segments
    }

    private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80

    private fun defaultJellyfinPort(scheme: String) =
        if (scheme == "https") HTTPS_DEFAULT_JELLYFIN_PORT else HTTP_DEFAULT_JELLYFIN_PORT
}
