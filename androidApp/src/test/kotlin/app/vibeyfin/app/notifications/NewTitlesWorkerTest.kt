package app.vibeyfin.app.notifications

import android.content.ContextWrapper
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.testing.TestListenableWorkerBuilder
import app.vibeyfin.domain.NewTitle
import app.vibeyfin.domain.ProfileNewTitles
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class NewTitlesWorkerTest {
    private val posted = mutableListOf<Pair<List<ProfileNewTitles>, Boolean>>()
    private var polls = 0
    private var kept: PeriodicWorkRequest? = null
    private val logged = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        NewTitlesWork.override = null
    }

    private fun movie(id: String) = NewTitle(id, "Movie $id", null, isMovie = true)

    private fun episode(id: String, show: String) = NewTitle(id, "Episode $id", show, isMovie = false)

    private fun profile(vararg titles: NewTitle, userId: String = "u1", name: String = "Alex") = ProfileNewTitles("srv-1", userId, name, titles.toList())

    private fun runWorker(
        found: List<ProfileNewTitles>,
        canPost: Boolean = true,
        notifyingProfiles: Int = 1,
        poll: suspend () -> List<ProfileNewTitles> = { found },
    ): ListenableWorker.Result {
        NewTitlesWork.override = NewTitlesDependencies(
            pollAll = {
                polls++
                poll()
            },
            notifyingProfiles = { notifyingProfiles },
            canPost = { canPost },
            publisher = { titles, nameProfiles -> posted += titles to nameProfiles },
            log = { logged += it },
        )

        // The worker never touches its context when the dependencies are set; it only has to be non-null
        val worker = TestListenableWorkerBuilder.from(ContextWrapper(null), NewTitlesWorker::class.java).build() as NewTitlesWorker

        return runBlocking { worker.doWork() }
    }

    private fun postedNotifications() = posted.flatMap { (found, names) -> planNotifications(found, names) }

    @Test
    fun `the worker posts one notification per new title and none when there is nothing new`() {
        assertEquals(ListenableWorker.Result.success(), runWorker(listOf(profile(movie("m1"), episode("e1", "Severance")))))
        assertEquals(listOf("srv-1/u1/m1", "srv-1/u1/e1"), postedNotifications().filterNot { it.summary }.map { it.tag })

        posted.clear()
        assertEquals(ListenableWorker.Result.success(), runWorker(emptyList()))
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `episodes of one show are grouped under one summary`() {
        runWorker(listOf(profile(episode("e1", "Severance"), episode("e2", "Severance"), movie("m1"), episode("e3", "Andor"))))

        val notifications = postedNotifications()
        val severance = notifications.filter { it.group == "srv-1/u1/show/Severance" }

        assertEquals(listOf("e1", "e2", "e1"), severance.map { it.itemId })
        assertEquals(listOf(false, false, true), severance.map { it.summary })
        assertEquals("New episodes of Severance", severance.last().title)
        assertEquals("Episode e1, Episode e2", severance.last().body)
        assertEquals("New episode of Severance", severance.first().title)

        val movie = notifications.single { it.itemId == "m1" }

        assertEquals(null, movie.group)
        assertEquals("New movie", movie.title)
    }

    @Test
    fun `a single episode posts its show's summary so a later one joins the same group`() {
        runWorker(listOf(profile(episode("e3", "Andor"))))
        posted.clear()
        runWorker(listOf(profile(episode("e4", "Andor"))))

        val andor = postedNotifications()

        assertEquals(listOf(false, true), andor.map { it.summary })
        assertEquals(listOf("srv-1/u1/show/Andor"), andor.mapNotNull { it.group }.distinct())

        // One tag per show: the summary of the second check replaces the first one
        assertEquals("srv-1/u1/show/Andor", andor.last().tag)
    }

    @Test
    fun `the profile is named only when more than one profile gets notifications`() {
        runWorker(listOf(profile(movie("m1"))), notifyingProfiles = 1)
        assertEquals("Movie m1", postedNotifications().single().body)

        posted.clear()
        runWorker(listOf(profile(movie("m1"))), notifyingProfiles = 2)
        assertEquals("Movie m1, for Alex", postedNotifications().single().body)
    }

    @Test
    fun `without permission nothing is polled or posted`() {
        assertEquals(ListenableWorker.Result.success(), runWorker(listOf(profile(movie("m1"))), canPost = false))

        assertEquals(0, polls)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `a failed poll succeeds without posting so the work is never retried`() {
        val result = runWorker(emptyList(), poll = { error("server down") })

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(posted.isEmpty())
        assertEquals(listOf("New-title check failed: IllegalStateException"), logged)
    }

    @Test
    fun `the scheduler updates four hourly work that needs a network and cancels it`() {
        val calls = mutableListOf<String>()
        val work = object : UniqueWork {
            override fun enqueue(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest) {
                calls += "$policy $name"
                kept = request
            }

            override fun cancel(name: String) {
                calls += "cancel $name"
            }
        }

        NewTitlesScheduler.apply(work, enabled = true)
        NewTitlesScheduler.apply(work, enabled = false)

        assertEquals(listOf("UPDATE new-titles", "cancel new-titles"), calls)

        val spec = kept!!.workSpec

        assertEquals(4.hours, spec.intervalDuration.milliseconds)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(NewTitlesWorker::class.java.name, spec.workerClassName)
    }

    @Test
    fun `the work follows whether any profile gets notifications and only when that changes`() {
        val changes = runBlocking { NewTitlesScheduler.changes(flowOf(0, 2, 3, 1, 0, 0, 1)).toList() }

        assertEquals(listOf(false, true, false, true), changes)
    }
}
