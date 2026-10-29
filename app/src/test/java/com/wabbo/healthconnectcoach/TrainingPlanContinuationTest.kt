package com.wabbo.healthconnectcoach

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrainingPlanContinuationTest {
    private val start=LocalDate.of(2026,10,26)
    private val goals=TrainingGoals(days=setOf(1,3,5))
    @Test fun aPlanContinuesBeyondItsInitialSixWeeks() {
        val old=TrainingPlanLogic.create(goals,start)
        val extended=TrainingPlanLogic.extend(old,start.plusDays(45))
        assertEquals(start.plusDays(86),extended.scheduledThrough)
        assertTrue(extended.sessions.any {it.date>=start.plusDays(45)})
        assertTrue(extended.sessions.all {it.date.dayOfWeek.value in goals.days})
        assertTrue(old.sessions.all {it in extended.sessions})
    }
    @Test fun repeatedMaintenanceOnTheSameDayDoesNotDuplicateOrRewriteEntries() {
        val once=TrainingPlanLogic.extend(TrainingPlanLogic.create(goals,start),start.plusDays(10))
        assertEquals(once,TrainingPlanLogic.extend(once,start.plusDays(10)))
        assertEquals(once.sessions.size,once.sessions.map {it.id}.distinct().size)
    }
    @Test fun movedDatesAndManuallyEditedSessionsArePreserved() {
        val original=TrainingPlanLogic.create(goals,start)
        val moved=TrainingPlanLogic.move(original,original.sessions.first().id,start.plusDays(80),start)
        val edited=moved.copy(sessions=moved.sessions.map {if(it.id==original.sessions[1].id) it.copy(title="Mein Krafttraining",minutes=45) else it})
        val extended=TrainingPlanLogic.extend(edited,start.plusDays(20))
        assertFalse(extended.sessions.any {it.date==start})
        assertEquals(start.plusDays(80),extended.sessions.single {it.id==original.sessions.first().id}.date)
        assertEquals("Mein Krafttraining",extended.sessions.single {it.id==original.sessions[1].id}.title)
        assertTrue(extended.sessions.any {it.date in start.plusDays(42)..start.plusDays(61)})
    }
    @Test fun aDeletedDateInsideTheExistingHorizonIsNotSilentlyRecreated() {
        val original=TrainingPlanLogic.create(goals,start)
        val deletedDate=original.sessions[2].date
        val edited=original.copy(sessions=original.sessions.filter {it.date!=deletedDate})
        assertFalse(TrainingPlanLogic.extend(edited,start.plusDays(15)).sessions.any {it.date==deletedDate})
    }
    @Test fun completionAndCelebrationHistorySurviveExtensionAndSerialization() {
        val original=TrainingPlanLogic.create(goals,start)
        val done=TrainingPlanLogic.complete(original,original.sessions.first().id,true,start).copy(celebrated=setOf(start.toString()))
        val extended=TrainingPlanLogic.extend(done,start.plusDays(4))
        assertEquals(extended,decodePlan(encodePlan(extended)))
        assertTrue(extended.sessions.single {it.id==done.sessions.first().id}.completed)
        assertEquals(done.celebrated,extended.celebrated)
    }
    @Test fun missingRecurringDaysAreNotRecordedAsCompletedTraining() {
        val original=TrainingPlanLogic.create(goals,start)
        val allDone=original.copy(sessions=original.sessions.map {it.copy(completed=true)})
        val extended=TrainingPlanLogic.extend(allDone,start.plusDays(90))
        assertTrue(extended.sessions.any {it.date>original.scheduledThrough&&it.date<start.plusDays(90)&&!it.completed})
        assertEquals(0,TrainingPlanLogic.streak(extended,start.plusDays(90)))
    }
    @Test fun legacyDocumentsWithoutTheHorizonCanStillLoadAndContinue() {
        val original=TrainingPlanLogic.create(goals,start)
        val json=encodePlan(original);json.remove("scheduledThrough")
        val legacy=decodePlan(json)
        assertNull(legacy.scheduledThrough)
        val extended=TrainingPlanLogic.extend(legacy,start.plusDays(50))
        assertNotNull(extended.scheduledThrough)
        assertTrue(original.sessions.all {it in extended.sessions})
    }
    @Test fun moreThanFourHundredHistoricSessionsRemainReadable() {
        val original=TrainingPlanLogic.create(goals,start)
        val rows=(0L..699).map {PlannedSession("history-$it",start.minusDays(it),"Training",30,1.5,true)}
        val plan=original.copy(sessions=rows.sortedBy {it.date})
        assertEquals(plan,decodePlan(encodePlan(plan)))
    }
}
