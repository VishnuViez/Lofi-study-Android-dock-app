package com.vishnu.lofidock

import com.vishnu.lofidock.core.Checklist
import com.vishnu.lofidock.core.CountdownTimer
import com.vishnu.lofidock.core.CountdownTimer.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownTimerTest {
    private val t0 = 1_800_000_000_000L

    @Test fun runsDownFromItsLength() {
        val timer = CountdownTimer().start(1_500.0, t0)
        assertEquals(900.0, timer.remainingSeconds(t0 + 600_000), 0.0)
        assertEquals(0.5, timer.fractionRemaining(t0 + 750_000), 0.0)
    }

    @Test fun pauseFreezesAndResumeContinues() {
        val paused = CountdownTimer().start(300.0, t0).pause(t0 + 100_000)
        assertEquals(200.0, paused.remainingSeconds(t0 + 5_000_000), 0.0)
        val resumed = paused.resume(t0 + 1_000_000)
        assertEquals(150.0, resumed.remainingSeconds(t0 + 1_050_000), 0.0)
    }

    @Test fun finishesOnceItsEndPasses() {
        val running = CountdownTimer().start(60.0, t0)
        assertSame(running, running.updated(t0 + 59_000))
        val finished = running.updated(t0 + 61_000)
        assertEquals(Phase.Finished(t0 + 60_000), finished.phase)
        assertSame(finished, finished.updated(t0 + 62_000))   // reports the transition only once
        assertEquals(0.0, finished.remainingSeconds(t0 + 62_000), 0.0)
    }

    @Test fun pausedTimerNeverFinishes() {
        val paused = CountdownTimer().start(60.0, t0).pause(t0 + 10_000)
        assertTrue(paused.updated(t0 + 3_600_000).isPaused)
    }

    @Test fun zeroLengthDoesNotStart() {
        assertEquals(Phase.Idle, CountdownTimer().start(0.0, t0).phase)
    }

    @Test fun cancelReturnsToIdleAndIgnoresStrayCalls() {
        val idle = CountdownTimer()
        assertEquals(Phase.Idle, idle.resume(t0).pause(t0).phase)
        assertEquals(Phase.Idle, idle.start(60.0, t0).cancel().phase)
    }
}

class ChecklistTest {
    @Test fun addsTrimmedTasksAndIgnoresBlankOnes() {
        val list = Checklist().add("   \n ").add("  Read chapter 4 \n")
        assertEquals(listOf("Read chapter 4"), list.items.map { it.title })
        assertEquals(false, list.items[0].isDone)
    }

    @Test fun capsVeryLongTitles() {
        assertEquals(Checklist.MAX_TITLE_LENGTH, Checklist().add("a".repeat(500)).items[0].title.length)
    }

    @Test fun togglesRemovesAndClearsDone() {
        var list = Checklist().add("A", id = "a").add("B", id = "b").add("C", id = "c")
        list = list.toggle("a").toggle("c")
        assertEquals(2, list.doneCount)
        assertEquals(2.0 / 3.0, list.progress, 1e-9)
        list = list.toggle("c").remove("b")
        assertEquals(listOf("A", "C"), list.items.map { it.title })
        list = list.clearDone()
        assertEquals(listOf("C"), list.items.map { it.title })
        assertEquals(1, list.toggle("unknown").items.size)
    }

    @Test fun emptyListHasNoProgress() {
        assertEquals(0.0, Checklist().progress, 0.0)
    }

    @Test fun survivesJson() {
        val list = Checklist().add("Flashcards", now = 5, id = "x").toggle("x")
        assertEquals(list, Checklist.fromJson(list.toJson()))
    }

    @Test fun unreadableJsonBecomesAnEmptyList() {
        assertEquals(Checklist(), Checklist.fromJson("{not json"))
        assertEquals(Checklist(), Checklist.fromJson(null))
    }
}
