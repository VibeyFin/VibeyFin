package app.vibeyfin.testing

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/** A Seerr user as `/auth/me` and the sign-in calls send it. */
fun seerrUserJson(id: Int = 7, jellyfinUserId: String = "u1", name: String = "Alex", permissions: Long = 32) = """{"id":$id,"displayName":"$name","jellyfinUsername":"$name","jellyfinUserId":"$jellyfinUserId","permissions":$permissions,"email":"$name"}"""

/**
 * A working Seerr at [server], linked to the Jellyfin at [jellyfin]: it starts Quick Connect sign-ins whose [code] that
 * Jellyfin approves for any signed-in user, hands out a new session cookie for each sign-in (`sess-1`, `sess-2`, ...) and
 * accepts it until [expireSessions]. Calls a test adds with [onSignedIn] answer 401 without a valid session.
 */
class FakeSeerr(val server: FakeServer, private val jellyfin: FakeServer) {
    /**
     * The user a sign-in returns, by the Jellyfin user it signs in as: by default that user, with Seerr id 7 for `u1` and
     * 8 for anyone else. Set it to sign everyone in as someone else.
     */
    var user: String? = null

    /** Whether Quick Connect is on in the Jellyfin server's settings. */
    var quickConnectOnJellyfin = true

    /** The code Seerr's Quick Connect hands out; a Jellyfin that never issued it answers 404. */
    var code = "123456"

    /** The one password [server] accepts on `POST /auth/jellyfin`. */
    var password = "secret"

    // Session -> the user it belongs to.
    private val valid = mutableMapOf<String, String>()
    private var issued = 0

    // The Jellyfin user who approved the current Quick Connect code; null until someone does.
    private var approvedBy: String? = null

    /** Every session handed out so far, in order. */
    val sessions = mutableListOf<String>()

    init {
        server.on("GET", "/api/v1/status", Reply.json("""{"version":"3.5.1","commitTag":"abc"}"""))
        server.on("GET", "/api/v1/settings/public", Reply.json("""{"initialized":true,"mediaServerType":2,"movie4kEnabled":false,"series4kEnabled":false}"""))
        server.on("POST", "/api/v1/auth/jellyfin/quickconnect/initiate") {
            approvedBy = null
            Reply.json("""{"code":"$code","secret":"secret-of-$code"}""")
        }
        server.on("POST", "/api/v1/auth/jellyfin/quickconnect/authenticate") { approvedBy?.let(::issue) ?: Reply.status(HttpStatusCode.Forbidden) }
        // The fake's only Jellyfin user with a password is Alex (u1).
        server.on("POST", "/api/v1/auth/jellyfin") { request ->
            if (request.body.contains("\"password\":\"$password\"")) issue("u1") else Reply.status(HttpStatusCode.Forbidden)
        }
        server.on("GET", "/api/v1/auth/me") { request ->
            sessionOf(request)?.let(valid::get)?.let { Reply.json(it) } ?: Reply.status(HttpStatusCode.Unauthorized)
        }
        server.on("POST", "/api/v1/auth/logout") { request ->
            sessionOf(request)?.let(valid::remove)
            Reply.json("""{"status":"ok"}""")
        }
        jellyfin.on("GET", "/QuickConnect/Enabled") { Reply.json("$quickConnectOnJellyfin") }
        jellyfin.on("POST", "/QuickConnect/Authorize") { request ->
            if (request.query["code"] == code) {
                approvedBy = request.query["userId"]
                Reply.json("true")
            } else {
                Reply.status(HttpStatusCode.NotFound)
            }
        }
    }

    /** Answers `method path` (relative to Seerr's root, with `/api/v1`) with [reply] when the request is signed in, 401 otherwise. */
    fun onSignedIn(method: String, path: String, reply: (RecordedRequest) -> Reply) {
        server.on(method, path) { request -> if (signedIn(request)) reply(request) else Reply.status(HttpStatusCode.Unauthorized) }
    }

    /** Ends every session, as a Seerr restart or an expired cookie does. */
    fun expireSessions() = valid.clear()

    private fun issue(jellyfinUserId: String): Reply {
        approvedBy = null
        issued++
        val session = "sess-$issued"
        val json = user ?: seerrUserJson(id = if (jellyfinUserId == "u1") 7 else 8, jellyfinUserId = jellyfinUserId, name = if (jellyfinUserId == "u1") "Alex" else jellyfinUserId)
        valid[session] = json
        sessions += session
        return Reply(HttpStatusCode.OK, json, "application/json", mapOf(HttpHeaders.SetCookie to "connect.sid=$session; Path=/; Expires=Mon, 30 Nov 2026 15:37:12 GMT; HttpOnly; SameSite=Lax"))
    }

    private fun sessionOf(request: RecordedRequest): String? = request.cookie?.removePrefix("connect.sid=")

    private fun signedIn(request: RecordedRequest) = sessionOf(request)?.let(valid::containsKey) == true
}
