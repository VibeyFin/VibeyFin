package app.vibeyfin.ui.seerr

import app.vibeyfin.data.api.ApiError
import kotlin.test.Test
import kotlin.test.assertEquals

class SeerrMessagesTest {
    @Test
    fun eachWayAReadFailsSaysWhatToDo() {
        assertEquals(SeerrMessages.UNREACHABLE_NOW, SeerrMessages.forError(ApiError.Network))
        assertEquals(SeerrMessages.SIGNED_OUT, SeerrMessages.forError(ApiError.Unauthorized))
        assertEquals(SeerrMessages.FORBIDDEN, SeerrMessages.forError(ApiError.Forbidden))
        assertEquals(SeerrMessages.TITLE_NOT_FOUND, SeerrMessages.forError(ApiError.NotFound))
        assertEquals("Seerr answered with an error (HTTP 502). Try again in a minute.", SeerrMessages.forError(ApiError.Server(502)))
        assertEquals(SeerrMessages.UNEXPECTED, SeerrMessages.forError(ApiError.BadResponse))
        assertEquals(SeerrMessages.UNEXPECTED, SeerrMessages.forError(ApiError.Unexpected(418)))
    }

    @Test
    fun theRequestLimitIsWordedWithTheCountsOfTheSharedStrings() {
        assertEquals("Your request limit is reached. You can request 1 movie a day.", SeerrMessages.quotaReached(1, 1, movies = true))
        assertEquals("Your request limit is reached. You can request 3 seasons a week.", SeerrMessages.quotaReached(3, 7, movies = false))
        assertEquals("Your request limit is reached. You can request 2 movies every 30 days.", SeerrMessages.quotaReached(2, 30, movies = true))
        assertEquals("Your request limit is reached. Try again later.", SeerrMessages.quotaReached(null, null, movies = true))
    }
}
