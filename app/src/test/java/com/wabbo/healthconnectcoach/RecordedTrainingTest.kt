package com.wabbo.healthconnectcoach

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RecordedTrainingTest {
    private val date=LocalDate.of(2026,10,26)
    private val zone=ZoneId.of("Europe/Berlin")
    private val start=date.atTime(18,0).atZone(zone).toInstant()
    private val session=PlannedSession("plan",date,"Laufen",30,1.5)
    private fun plan(vararg entries: PlannedSession)=TrainingPlan(TrainingGoals(),entries.toList())
    private fun activity(id: String="watch",title: String="Running",minutes: Double=30.0,at: Instant=start)=ActivityInsight(id,title,at,at.plusSeconds((minutes*60).toLong()),minutes,null,null,null,emptyList(),true)
    private fun day(activities: List<ActivityInsight>,connector: String="Google Health",at: LocalDate=date,minutes: Double?=30.0,calculated: Instant=start.plusSeconds(3600))=DayInsight(at,calculated,false,emptyMap(),activities=activities,sources=mapOf("_connector" to connector),metrics=minutes?.let {mapOf("workout_minutes" to it)}.orEmpty())
    private fun suggestions(p: TrainingPlan=plan(session),days: List<DayInsight> = listOf(day(listOf(activity()))))=recordedSuggestions(p,session.id,days,"Google Health",date,zone)

    @Test fun offersOnlySameConnectorDayAndPlausibleDuration() {
        val rows=listOf(day(listOf(activity())),day(listOf(activity("other")),"Health Connect"),day(listOf(activity("short",minutes=5.0))),day(listOf(activity("yesterday",at=start.minusSeconds(86400)))))
        assertEquals(listOf("watch"),suggestions(days=rows).map {it.id})
    }
    @Test fun knownSportMismatchDoesNotOfferCyclingForRunning() {
        assertTrue(suggestions(days=listOf(day(listOf(activity(title="Cycling"))))).isEmpty())
        assertEquals(1,suggestions(days=listOf(day(listOf(activity(title="Training"))))).size)
    }
    @Test fun requiresLocalStartDayNotUtcDate() {
        val midnight=date.atTime(0,15).atZone(zone).toInstant()
        assertEquals(1,suggestions(days=listOf(day(listOf(activity(at=midnight))))).size)
    }
    @Test fun noFutureOrAlreadyCompletedSuggestions() {
        assertTrue(suggestions(plan(session.copy(date=date.plusDays(1)))).isEmpty())
        assertTrue(suggestions(plan(session.copy(completed=true))).isEmpty())
    }
    @Test fun explicitLinkStoresReferenceAndNeverChangesMeasuredDay() {
        val rows=listOf(day(listOf(activity())))
        val reference=suggestions(days=rows).single()
        val linked=linkRecordedTraining(plan(session),session.id,reference,rows,"Google Health",date,zone)
        assertTrue(linked.sessions.single().completed)
        assertEquals("Google Health",linked.sessions.single().recordedActivity?.connector)
        assertEquals(30.0,rows.single().metrics["workout_minutes"]!!,0.0)
        assertEquals(linked,decodePlan(encodePlan(linked)))
    }
    @Test fun sameRecordingCannotCompleteTwoUnitsEvenAfterConnectorFallback() {
        val other=session.copy(id="second")
        val rows=listOf(day(listOf(activity())))
        val linked=linkRecordedTraining(plan(session,other),session.id,suggestions(days=rows).single(),rows,"Google Health",date,zone)
        val fallback=listOf(day(listOf(activity("reimported")),"Health Connect"))
        assertTrue(recordedSuggestions(linked,other.id,fallback,"Health Connect",date,zone).isEmpty())
    }
    @Test fun revisionsAndDuplicateDayRowsProduceOnlyOneSuggestion() {
        val rows=listOf(day(listOf(activity())),day(listOf(activity("changed-id",at=start.plusSeconds(60)))))
        assertEquals(1,suggestions(days=rows).size)
    }
    @Test fun nonOverlappingTrainingCanCompleteAnotherUnit() {
        val a=RecordedTraining("one","Google Health","Lauf",start,start.plusSeconds(1800),30.0)
        assertFalse(sameRecordedTraining(a,a.copy(id="two",start=start.plusSeconds(3600),end=start.plusSeconds(5400))))
    }
    @Test fun clearingCompletionFreesRecordingAndLegacyManualPlansStillLoad() {
        val rows=listOf(day(listOf(activity())))
        val linked=linkRecordedTraining(plan(session),session.id,suggestions(days=rows).single(),rows,"Google Health",date,zone)
        val reset=TrainingPlanLogic.complete(linked,session.id,false,date)
        assertNull(reset.sessions.single().recordedActivity)
        assertEquals(1,suggestions(reset,rows).size)
        assertEquals(plan(session),decodePlan(encodePlan(plan(session))))
    }
    @Test fun staleSuggestionCannotBeLinkedAfterRemovalOrSourceChange() {
        val ref=suggestions().single()
        assertThrows(IllegalArgumentException::class.java) {linkRecordedTraining(plan(session),session.id,ref,emptyList(),"Google Health",date,zone)}
        assertThrows(IllegalArgumentException::class.java) {linkRecordedTraining(plan(session),session.id,ref,listOf(day(listOf(activity()))),"Health Connect",date,zone)}
    }
    @Test fun invalidSensorDurationIsNotOffered() {
        assertTrue(suggestions(days=listOf(day(listOf(activity().copy(minutes=Double.NaN))))).isEmpty())
        assertTrue(suggestions(days=listOf(day(listOf(activity().copy(end=start))))).isEmpty())
    }
    @Test fun removedRecordingInNewerSnapshotCannotBeOfferedAgain() {
        val rows=listOf(day(listOf(activity())),day(emptyList(),calculated=start.plusSeconds(7200)))
        assertTrue(suggestions(days=rows).isEmpty())
    }
    @Test fun recordingThatEndsAfterSnapshotIsNotOffered() {
        assertTrue(suggestions(days=listOf(day(listOf(activity()),calculated=start))).isEmpty())
    }
    @Test fun summaryUsesLatestDayOfCurrentConnectorOnce() {
        val rows=listOf(day(emptyList(),minutes=30.0),day(emptyList(),minutes=40.0,calculated=start.plusSeconds(3601)),day(emptyList(),"Health Connect",minutes=100.0))
        assertEquals(WeekTrainingSummary(40.0,1,1),weekTrainingSummary(rows,date,date,"Google Health"))
    }
    @Test fun summaryKeepsMissingDaysAndUnknownValuesDistinctFromMeasuredZero() {
        val rows=listOf(day(emptyList(),minutes=0.0),day(emptyList(),at=date.plusDays(1),minutes=Double.NaN),day(emptyList(),at=date.plusDays(2),minutes=null))
        assertEquals(WeekTrainingSummary(0.0,1,3),weekTrainingSummary(rows,date,date.plusDays(2),"Google Health"))
    }
    @Test fun futureWeekHasNoFictitiousObservations() {
        assertEquals(WeekTrainingSummary(0.0,0,0),weekTrainingSummary(listOf(day(emptyList())),date.plusWeeks(1),date,"Google Health"))
    }
}
