package com.wabbo.healthconnectcoach

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class WellnessJournalTest {
    private val today=LocalDate.of(2026,10,26)
    private val now=Instant.parse("2026-10-26T12:00:00Z")
    private fun journal()=WellnessJournal(activeIds=listOf("relaxation"))
    @Test fun noIsARealCompletedCheckInButMissingIsNotNo() {
        val saved=journal().withEntry(today,mapOf("relaxation" to JournalAnswer(yes=false)),true,today,now)
        assertEquals(false,saved.entries.single().answers.getValue("relaxation").yes)
        assertEquals(1,journalStreak(saved,today));assertNotNull(saved.entries.single().completedAt)
        assertThrows(IllegalArgumentException::class.java) {journal().withEntry(today,emptyMap(),true,today,now)}
    }
    @Test fun skippedOnlyCannotCreateAStreakOrFakeNegativeObservation() {
        assertThrows(IllegalArgumentException::class.java) {journal().withEntry(today,mapOf("relaxation" to JournalAnswer(skipped=true)),true,today,now)}
        val draft=journal().withEntry(today,mapOf("relaxation" to JournalAnswer(skipped=true)),false,today,now)
        assertEquals(0,journalStreak(draft,today));assertNull(draft.entries.single().answers.getValue("relaxation").yes)
    }
    @Test fun streakUsesConsecutiveJournalDatesAndAllowsTodayToRemainOpenUntilTomorrow() {
        var j=journal()
        for(offset in 1L..3L) j=j.withEntry(today.minusDays(offset),mapOf("relaxation" to JournalAnswer(yes=true)),true,today,now)
        assertEquals(3,journalStreak(j,today));assertEquals(0,journalStreak(j,today.plusDays(1)))
        val done=j.withEntry(today,mapOf("relaxation" to JournalAnswer(yes=false)),true,today,now)
        assertEquals(4,journalStreak(done,today))
    }
    @Test fun addingAQuestionDoesNotInvalidateEarlierCompletedCheckIns() {
        val saved=journal().withEntry(today,mapOf("relaxation" to JournalAnswer(yes=false)),true,today,now)
        val changed=saved.copy(activeIds=listOf("relaxation","late_meal")).validated()
        assertEquals(1,journalStreak(changed,today));assertEquals(setOf("relaxation"),changed.entries.single().questionIds)
    }
    @Test fun explicitDraftCanClearAnAnswerWithoutReusingOldYes() {
        val saved=journal().withEntry(today,mapOf("relaxation" to JournalAnswer(yes=true)),true,today,now)
        val cleared=saved.withEntry(today,emptyMap(),false,today,now)
        assertTrue(cleared.entries.single().answers.isEmpty());assertEquals(0,journalStreak(cleared,today))
    }
    @Test fun roundTripPreservesCustomQuestionsQuantitiesZeroAndSkippedValues() {
        val h=JournalHabit("custom_test","Lesen","Wie lange hast du gelesen?","Eigene",JournalQuestionKind.NUMBER,"Minuten",true)
        val j=journal().copy(habits=journal().habits+h,activeIds=listOf("custom_test","late_meal"))
            .withEntry(today,mapOf("custom_test" to JournalAnswer(false,0.0),"late_meal" to JournalAnswer(skipped=true)),true,today,now)
        assertEquals(j,decodeWellnessJournal(encodeWellnessJournal(j)))
    }
    @Test fun homePromptTargetsPreviousDayInMorningAndTodayInEvening() {
        assertEquals(today.minusDays(1),journalPromptDate(journal(),today,LocalTime.of(10,0)))
        val saved=journal().withEntry(today.minusDays(1),mapOf("relaxation" to JournalAnswer(yes=true)),true,today,now)
        assertNull(journalPromptDate(saved,today,LocalTime.of(10,0)))
        assertEquals(today,journalPromptDate(saved,today,LocalTime.of(18,0)))
    }
    @Test fun futureEntriesAndDuplicateQuestionIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {journal().withEntry(today.plusDays(1),mapOf("relaxation" to JournalAnswer(true)),true,today,now)}
        assertThrows(IllegalArgumentException::class.java) {journal().copy(habits=journal().habits+journal().habits.first()).validated()}
    }
    @Test fun journalCelebrationIsSavedOnceAndNotReplayedForEdits() {
        val before=journal()
        val next=before.withEntry(today,mapOf("relaxation" to JournalAnswer(false)),true,today,now)
        val first=prepareJournalSave(before,next,today)
        assertTrue(first.celebrate);assertTrue(first.journal.celebrated.contains(today.toString()))
        val edited=first.journal.withEntry(today,mapOf("relaxation" to JournalAnswer(true)),true,today,now.plusSeconds(60))
        assertFalse(prepareJournalSave(first.journal,edited,today).celebrate)
        assertThrows(IllegalArgumentException::class.java) {prepareJournalSave(first.journal,next,today)}
    }
    @Test fun libraryFooterSavesCustomDraftAndSelectionTogether() {
        val before=journal()
        val next=prepareJournalLibraryUpdate(before,listOf("late_meal"),true,"","Hast du gelesen?",false,"",id="custom_read")
        val restored=decodeWellnessJournal(encodeWellnessJournal(prepareJournalSave(before,next,today).journal))
        assertEquals(listOf("late_meal","custom_read"),restored.activeIds)
        assertEquals("Hast du gelesen?",restored.habits.last().title)
    }
    @Test fun fullSelectionCannotSilentlyStoreAnInactiveCustomQuestion() {
        val j=journal().copy(activeIds=journalHabitLibrary.take(10).map {it.id})
        assertThrows(IllegalArgumentException::class.java) {prepareJournalLibraryUpdate(j,j.activeIds,true,"Lesen","Gelesen?",false,"")}
        assertThrows(IllegalArgumentException::class.java) {prepareJournalLibraryUpdate(j,listOf("relaxation"),true,"Menge","Wie viel?",true,"")}
    }
}
