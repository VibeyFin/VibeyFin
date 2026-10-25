package app.vibeyfin.domain

import app.vibeyfin.data.api.ApiError
import app.vibeyfin.data.api.ApiResult
import app.vibeyfin.testing.FakeServer
import app.vibeyfin.testing.Reply
import app.vibeyfin.testing.TestApp
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ProfilePolicyTest {
    private val app = TestApp()
    private val policy = ProfilePolicy(app.connection)

    @AfterTest
    fun tearDown() = runTest { app.close() }

    private suspend fun load(): PolicySummary =
        assertIs<ApiResult.Success<PolicySummary>>(policy.load()).value

    private fun FakeServer.user(policyJson: String) =
        on("GET", "/Users/Me", Reply.json("""{"Id":"u1","Name":"Alex","Policy":$policyJson}"""))

    private fun FakeServer.ratings(json: String) = on("GET", "/Localization/ParentalRatings", Reply.json(json))

    @Test
    fun `a user with a rating cap and blocked tags is summarised`() = runTest {
        val server = app.signedIn()
        server.user(
            """{"MaxParentalRating":10,"BlockedTags":["horror","gore"],
            |"AccessSchedules":[{"DayOfWeek":"Weekday","StartHour":8.0,"EndHour":20.5}]}""".trimMargin(),
        )
        server.ratings("""[{"Name":"G","Value":5},{"Name":"PG","Value":10},{"Name":"TV-PG","Value":10}]""")

        val summary = load()

        assertEquals(10, summary.maxRatingLevel)
        assertEquals("PG", summary.ratingName)
        assertEquals(listOf("horror", "gore"), summary.blockedTags)
        assertEquals(listOf(AccessWindow(AccessDays.Weekday, 480, 1230)), summary.schedules)
    }

    @Test
    fun `a user with no limits summarises as unlimited`() = runTest {
        val server = app.signedIn()
        server.user("""{"IsAdministrator":true}""")

        val summary = load()

        assertNull(summary.maxRatingLevel)
        assertNull(summary.ratingName)
        assertEquals(emptyList(), summary.blockedTags)
        assertEquals(emptyList(), summary.schedules)
    }

    @Test
    fun `a failed load is an error and not an empty policy`() = runTest {
        val server = app.signedIn()
        server.on("GET", "/Users/Me", Reply.status(HttpStatusCode.InternalServerError))

        val failure = assertIs<ApiResult.Failure>(policy.load())
        assertEquals(ApiError.Server(500), failure.error)
    }

    @Test
    fun `nobody signed in is unauthorized`() = runTest {
        val failure = assertIs<ApiResult.Failure>(policy.load())
        assertEquals(ApiError.Unauthorized, failure.error)
    }

    @Test
    fun `level 0 is a cap and the strictest one`() = runTest {
        val server = app.signedIn()
        server.user("""{"MaxParentalRating":0}""")
        server.ratings("""[{"Name":"G","Value":5}]""")

        val summary = load()

        assertEquals(0, summary.maxRatingLevel)
        assertNull(summary.ratingName)
    }

    @Test
    fun `a level the server has no name for has no name`() = runTest {
        val server = app.signedIn()
        server.user("""{"MaxParentalRating":12}""")
        server.ratings("""[{"Name":"G","Value":5}]""")

        val summary = load()

        assertEquals(12, summary.maxRatingLevel)
        assertNull(summary.ratingName)
    }

    @Test
    fun `a failing ratings list falls back to the static names and does not fail the load`() = runTest {
        val server = app.signedIn()
        server.user("""{"MaxParentalRating":13}""")
        server.on("GET", "/Localization/ParentalRatings", Reply.status(HttpStatusCode.InternalServerError))

        assertEquals("PG-13", load().ratingName)
    }

    @Test
    fun `schedules keep their order and an overnight range and an unknown day`() = runTest {
        val server = app.signedIn()
        server.user(
            """{"AccessSchedules":[
            |{"DayOfWeek":"Saturday","StartHour":22.0,"EndHour":6.0},
            |{"StartHour":0.0,"EndHour":24.0},
            |{"DayOfWeek":"Someday","StartHour":9.25,"EndHour":17.0}]}""".trimMargin(),
        )

        assertEquals(
            listOf(
                AccessWindow(AccessDays.Saturday, 1320, 360),
                AccessWindow(AccessDays.Everyday, 0, 1440),
                AccessWindow(AccessDays.Everyday, 555, 1020),
            ),
            load().schedules,
        )
    }
}
