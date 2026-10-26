package app.vibeyfin.domain

import app.vibeyfin.data.db.NotifiedItemEntity
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NewTitlesPollerTest {
    private val app = TestApp()
    private val dao = app.database.accountDao()
    private val poller = NewTitlesPoller(app.connection, dao, app.clock)

    @AfterTest
    fun tearDown() = runTest { app.close() }

    /** The fake clock's now is 2023-11-14T22:13:20Z. */
    private val twoHoursAgo = "2023-11-14T20:13:20.0000000Z"
    private val threeDaysAgo = "2023-11-11T22:13:20.0000000Z"
    private val threeWeeksAgo = "2023-10-24T22:13:20.0000000Z"

    private fun minutesAgo(n: Int) = (app.clock.now() - n.minutes).toString()

    private fun episode(id: String, seriesId: String, seriesName: String, created: String = twoHoursAgo) =
        """{"Id":"$id","Name":"Episode $id","Type":"Episode","SeriesId":"$seriesId","SeriesName":"$seriesName","DateCreated":"$created"}"""

    private fun movie(id: String, created: String = twoHoursAgo) =
        """{"Id":"$id","Name":"Movie $id","Type":"Movie","DateCreated":"$created"}"""

    private fun page(items: List<String>) = """{"Items":${items.joinToString(",", "[", "]")},"TotalRecordCount":${items.size}}"""

    /**
     * Answers `GET /Items`: My List (`filters=IsFavorite`) with [myList], anything else with a page of [added]
     * (newest first, as the server sorts them by `DateCreated`).
     */
    private fun FakeServer.library(added: List<String>, myList: List<String> = emptyList()) = on("GET", "/Items") { request ->
        if (request.query["filters"] == "IsFavorite") return@on Reply.json(page(myList))
        val start = request.query["startIndex"]!!.toInt()
        val limit = request.query["limit"]!!.toInt()
        Reply.json(page(added.drop(start).take(limit)))
    }

    private fun FakeServer.nextUp(vararg items: String) = on("GET", "/Shows/NextUp", Reply.json(page(items.toList())))

    private fun FakeServer.resume(vararg items: String) = on("GET", "/UserItems/Resume", Reply.json(page(items.toList())))

    private fun addedRequests() = app.jellyfin.requestsTo("GET", "/Items").filter { it.query["filters"] != "IsFavorite" }

    private suspend fun enable(userId: String = "u1", scope: Int = ProfileSettings.NOTIFY_FOLLOWED) {
        dao.setNotificationsEnabled("srv-1", userId, true)
        dao.setNotificationScope("srv-1", userId, scope)
    }

    /** Marks something old as notified, so the profile is past its first poll. */
    private suspend fun pastFirstPoll(userId: String = "u1") =
        dao.markNotified(NotifiedItemEntity("srv-1", userId, "seen-before", app.clock.now().toEpochMilliseconds()))

    /** Alex is signed in and follows Severance (s1) through Next Up. */
    private suspend fun followedServer(): FakeServer = app.signedIn().also {
        it.resume()
        it.nextUp(episode("e0", "s1", "Severance", threeWeeksAgo))
    }

    @Test
    fun `a new episode of a followed show is reported once`() = runTest {
        val server = followedServer()
        server.library(listOf(episode("e5", "s1", "Severance"), episode("x1", "s9", "Other show")))
        enable()

        assertEquals(listOf(NewTitle("e5", "Episode e5", "Severance", isMovie = false)), poller.poll("srv-1", "u1"))
        assertEquals(emptyList(), poller.poll("srv-1", "u1"))
    }

    @Test
    fun `an episode of a show that is not followed stays unmarked`() = runTest {
        val server = followedServer()
        server.library(listOf(episode("x1", "s9", "Other show"), movie("m1")))
        enable()

        poller.poll("srv-1", "u1")

        assertFalse(dao.wasNotified("srv-1", "u1", "x1"))
    }

    @Test
    fun `a show on my list or in continue watching counts as followed`() = runTest {
        val server = app.signedIn()
        server.nextUp()
        server.resume(episode("r1", "s2", "Dark", threeWeeksAgo))
        server.library(
            added = listOf(episode("e2", "s2", "Dark"), episode("e3", "s3", "Andor")),
            myList = listOf("""{"Id":"s3","Name":"Andor","Type":"Series"}"""),
        )
        enable()

        assertEquals(listOf("e2", "e3"), poller.poll("srv-1", "u1").map { it.itemId })
    }

    @Test
    fun `new titles are read newest first from the whole library one entry per episode`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m1")))
        enable()

        poller.poll("srv-1", "u1")

        val request = addedRequests().single()
        assertEquals("true", request.query["recursive"])
        assertEquals("Movie,Episode", request.query["includeItemTypes"])
        assertEquals("DateCreated", request.query["sortBy"])
        assertEquals("Descending", request.query["sortOrder"])
        assertEquals("DateCreated", request.query["fields"])
    }

    @Test
    fun `a new movie is reported and a movie already notified is not`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m-old"), movie("m-new")))
        enable()
        dao.markNotified(NotifiedItemEntity("srv-1", "u1", "m-old", app.clock.now().toEpochMilliseconds()))

        assertEquals(listOf(NewTitle("m-new", "Movie m-new", null, isMovie = true)), poller.poll("srv-1", "u1"))
        assertTrue(dao.wasNotified("srv-1", "u1", "m-new"))
    }

    @Test
    fun `everything new covers shows that are not followed`() = runTest {
        val server = app.signedIn()
        server.library(listOf(episode("x1", "s9", "Other show"), movie("m1")))
        enable(scope = ProfileSettings.NOTIFY_EVERYTHING)

        assertEquals(listOf("x1", "m1"), poller.poll("srv-1", "u1").map { it.itemId })
        assertTrue(app.jellyfin.requestsTo("GET", "/Shows/NextUp").isEmpty())
    }

    @Test
    fun `more than a page of new titles in the window are all reported`() = runTest {
        val server = followedServer()
        val movies = (1..250).map { movie("m$it", minutesAgo(it)) }
        server.library(movies)
        enable()
        pastFirstPoll()

        assertEquals(250, poller.poll("srv-1", "u1").size)
        assertEquals(listOf("0", "100", "200"), addedRequests().map { it.query["startIndex"] })
    }

    @Test
    fun `paging stops at the first page that reaches past the window`() = runTest {
        val server = followedServer()
        val fresh = (1..99).map { movie("m$it", minutesAgo(it)) }
        val old = (1..150).map { movie("old$it", threeWeeksAgo) }
        server.library(fresh + old)
        enable()
        pastFirstPoll()

        assertEquals(99, poller.poll("srv-1", "u1").size)
        assertEquals(1, addedRequests().size)
    }

    @Test
    fun `paging stops after ten pages`() = runTest {
        val server = followedServer()
        server.library((1..2000).map { movie("m$it", minutesAgo(1)) })
        enable()
        pastFirstPoll()

        assertEquals(1000, poller.poll("srv-1", "u1").size)
        assertEquals(10, addedRequests().size)
    }

    @Test
    fun `two polls at once report a title once`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m1"), movie("m2")))
        enable()
        pastFirstPoll()
        val gate = server.hold("GET", "/Items")

        val reported = withContext(Dispatchers.Default) {
            val polls = List(2) { async { poller.poll("srv-1", "u1") } }
            withTimeout(5.seconds) { while (addedRequests().size < 2) delay(5) }
            gate.open()
            polls.awaitAll().flatten()
        }

        assertEquals(listOf("m1", "m2"), reported.map { it.itemId }.sorted())
    }

    @Test
    fun `a failure partway through marks nothing`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m1")))
        server.on("GET", "/UserItems/Resume", Reply.status(HttpStatusCode.InternalServerError))
        enable()

        assertEquals(emptyList(), poller.poll("srv-1", "u1"))
        assertFalse(dao.hasNotified("srv-1", "u1"))
    }

    @Test
    fun `a 401 locks the profile and reports nothing`() = runTest {
        val server = app.signedIn()
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        server.on("GET", "/Items", Reply.status(HttpStatusCode.Unauthorized))
        enable(userId = "u2")

        assertEquals(emptyList(), poller.poll("srv-1", "u2"))
        assertFalse(app.accounts.account("srv-1", "u2")!!.hasSession)
        assertIs<ConnectionState.Connected>(app.connection.state.value)

        // Locked now: the next poll doesn't ask the server.
        assertEquals(emptyList(), poller.poll("srv-1", "u2"))
        assertEquals(1, app.jellyfin.requestsTo("GET", "/Items").size)
    }

    @Test
    fun `a 401 for the signed-in profile ends its session and reports nothing`() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Items", Reply.status(HttpStatusCode.Unauthorized))
        enable()

        assertEquals(emptyList(), poller.poll("srv-1", "u1"))

        val state = withContext(Dispatchers.Default) { withTimeout(5.seconds) { app.connection.state.first { it is ConnectionState.Expired } } }
        assertEquals(ConnectionState.Expired("srv-1", "u1"), state)
        assertFalse(app.accounts.account("srv-1", "u1")!!.hasSession)
        assertFalse(dao.hasNotified("srv-1", "u1"))
    }

    @Test
    fun `a kids or signed-out profile reports nothing`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m1")))
        enable()
        dao.setKidsProfile("srv-1", "u1", true)
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        enable(userId = "u2")
        app.accounts.expire("srv-1", "u2")

        assertEquals(emptyList(), poller.poll("srv-1", "u1"))
        assertEquals(emptyList(), poller.poll("srv-1", "u2"))
        assertEquals(emptyList(), poller.poll("srv-1", "nobody"))
        assertTrue(app.jellyfin.requestsTo("GET", "/Items").isEmpty())
    }

    @Test
    fun `notifications switched off report nothing and ask nothing`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m1")))

        assertEquals(emptyList(), poller.poll("srv-1", "u1"))
        assertTrue(app.jellyfin.requestsTo("GET", "/Items").isEmpty())
    }

    @Test
    fun `the first poll reports only what was added in the last day and keeps the rest quiet`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m-today"), movie("m-earlier", threeDaysAgo), episode("e4", "s1", "Severance", threeDaysAgo)))
        enable()

        assertEquals(listOf("m-today"), poller.poll("srv-1", "u1").map { it.itemId })
        // The older ones were taken as already known, so they never notify later.
        assertTrue(dao.wasNotified("srv-1", "u1", "m-earlier"))
        assertEquals(emptyList(), poller.poll("srv-1", "u1"))
    }

    @Test
    fun `after the first poll a title from days ago is still reported but one from weeks ago is not`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m-days", threeDaysAgo), movie("m-weeks", threeWeeksAgo), """{"Id":"m-undated","Name":"Undated","Type":"Movie"}"""))
        enable()
        pastFirstPoll()

        assertEquals(listOf("m-days"), poller.poll("srv-1", "u1").map { it.itemId })
    }

    @Test
    fun `switching notifications off and on again starts with the first-poll rule`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m1")))
        app.profileSettings.setNotificationsEnabled(true)
        poller.poll("srv-1", "u1")
        assertTrue(dao.hasNotified("srv-1", "u1"))

        app.profileSettings.setNotificationsEnabled(false)
        assertFalse(dao.hasNotified("srv-1", "u1"))

        // Back on three days later: what came in meanwhile is known, not announced.
        app.clock.advance(3.days)
        server.library(listOf(movie("m2", minutesAgo(2 * 24 * 60)), movie("m1")))
        app.profileSettings.setNotificationsEnabled(true)
        assertEquals(emptyList(), poller.poll("srv-1", "u1"))
    }

    @Test
    fun `changing the scope forgets what was notified and keeping it does not`() = runTest {
        app.signedIn()
        pastFirstPoll()

        app.profileSettings.setNotificationScope(ProfileSettings.NOTIFY_FOLLOWED)
        assertTrue(dao.hasNotified("srv-1", "u1"))

        app.profileSettings.setNotificationScope(ProfileSettings.NOTIFY_EVERYTHING)
        assertFalse(dao.hasNotified("srv-1", "u1"))
    }

    @Test
    fun `old notified rows are pruned at the end of a poll`() = runTest {
        val server = followedServer()
        server.library(emptyList())
        enable()
        pastFirstPoll()
        app.clock.advance(91.days)

        poller.poll("srv-1", "u1")

        assertFalse(dao.wasNotified("srv-1", "u1", "seen-before"))
    }

    @Test
    fun `pollAll polls every profile that has notifications on and skips the others`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m1")))
        enable()
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        enable(userId = "u2")
        app.accounts.saveSession("srv-1", "u3", "Pat", null, "token-3")

        val all = poller.pollAll()

        assertEquals(listOf("u1", "u2"), all.map { it.userId }.sorted())
        assertEquals(2, addedRequests().size)
    }

    @Test
    fun `one profile failing does not stop the others`() = runTest {
        val server = followedServer()
        server.on("GET", "/Items") { request ->
            if (request.authorization?.contains("token-2") == true) Reply.status(HttpStatusCode.Unauthorized)
            else Reply.json(page(listOf(movie("m1"))))
        }
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        enable(userId = "u2")
        enable()

        val all = poller.pollAll()

        assertEquals(listOf("u1"), all.map { it.userId })
        assertFalse(app.accounts.account("srv-1", "u2")!!.hasSession)
    }

    @Test
    fun `a signed out or kids profile is skipped`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m1")))
        enable()
        dao.setKidsProfile("srv-1", "u1", true)
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        enable(userId = "u2")
        app.accounts.expire("srv-1", "u2")

        assertEquals(emptyList(), poller.pollAll())
        assertTrue(app.jellyfin.requestsTo("GET", "/Items").isEmpty())
    }

    @Test
    fun `titles are reported per profile`() = runTest {
        val server = followedServer()
        server.library(listOf(movie("m1")))
        enable(scope = ProfileSettings.NOTIFY_EVERYTHING)
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        enable(userId = "u2", scope = ProfileSettings.NOTIFY_EVERYTHING)

        val all = poller.pollAll().sortedBy { it.userId }

        assertEquals(listOf("Alex", "Sam"), all.map { it.profileName })
        assertEquals(listOf("srv-1", "srv-1"), all.map { it.serverId })
        assertEquals(listOf(listOf(NewTitle("m1", "Movie m1", null, true))), all.map { it.titles }.distinct())
    }

    @Test
    fun `the count of profiles to poll follows the toggle and the session`() = runTest {
        app.signedIn()
        assertEquals(0, dao.observeNotifiableProfileCount().first())
        enable()
        app.accounts.saveSession("srv-1", "u2", "Sam", null, "token-2")
        enable(userId = "u2")
        assertEquals(2, dao.observeNotifiableProfileCount().first())

        app.accounts.expire("srv-1", "u2")
        dao.setNotificationsEnabled("srv-1", "u1", false)

        assertEquals(0, dao.observeNotifiableProfileCount().first())
    }
}
