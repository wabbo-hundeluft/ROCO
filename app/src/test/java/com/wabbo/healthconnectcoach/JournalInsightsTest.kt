package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.random.Random
import kotlin.math.abs

class JournalInsightsTest {
    private val today=LocalDate.of(2026,10,26)
    private val zone=ZoneId.of("UTC")
    private val now=today.atTime(12,0).toInstant(ZoneOffset.UTC)
    private fun data(n: Int=60,source: (Boolean)->String={"Google Health"},constant: Boolean=false,overlap: Boolean=false): Pair<WellnessJournal,List<DayInsight>> {
        val random=Random(932)
        val entries=(0 until n).map {index->val yes=if(n<=10) index%2==0 else random.nextBoolean()
            JournalDay(today.minusDays((n-index).toLong()),mapOf("relaxation" to JournalAnswer(yes),"late_meal" to JournalAnswer(if(overlap) yes else random.nextBoolean())),now,setOf("relaxation","late_meal"))}
        val days=entries.map {entry->val yes=entry.answers.getValue("relaxation").yes!!;val d=entry.date.plusDays(1)
            DayInsight(d,d.atTime(12,0).toInstant(ZoneOffset.UTC),false,emptyMap(),metrics=mapOf("hrv" to if(constant) 50.0 else if(yes) 80.0 else 40.0,"workout_minutes" to random.nextInt(0,60).toDouble()),sources=mapOf("_connector" to source(yes),"hrv" to "watch"))}
        return WellnessJournal(activeIds=listOf("relaxation"),entries=entries).validated() to days
    }
    private fun insight(pair: Pair<WellnessJournal,List<DayInsight>>)=journalInsights(pair.first,pair.second,today,listOf(JournalOutcome.HRV),zone=zone).single()
    @Test fun pairsTheBehaviorWithFollowingDayRatherThanSameDay() {
        val i=insight(data());assertTrue(i.effect!!>50);assertTrue(i.yesMean!!>i.noMean!!);assertEquals(60,i.yesCount+i.noCount)
        assertEquals("consistent",i.quality)
    }
    @Test fun requiresFiveActualObservationsOnEachSide() {
        val i=insight(data(9));assertNull(i.effect);assertEquals("learning",i.quality)
    }
    @Test fun sourceSwitchCannotBecomeAnApparentBehaviorEffect() {
        val i=insight(data(source={if(it) "Google Health" else "Health Connect"}))
        assertNull(i.effect);assertTrue(i.yesCount==0||i.noCount==0)
    }
    @Test fun alwaysCoOccurringHabitsAreFlaggedInsteadOfClaimingAnIndividualCause() {
        val i=insight(data(overlap=true));assertEquals("overlapping",i.quality);assertTrue(i.overlapping.contains("Spät gegessen"))
    }
    @Test fun constantOutcomesDoNotUnlockAFalseConsistentSignal() {
        val i=insight(data(constant=true));assertEquals(0.0,i.effect!!,.01);assertNotEquals("consistent",i.quality)
    }
    @Test fun missingOrSkippedAnswersAreExcludedFromBothGroups() {
        val pair=data();val entries=pair.first.entries.mapIndexed {index,e->if(index<20) e.copy(answers=e.answers+("relaxation" to JournalAnswer(skipped=true))) else e}
        val i=insight(pair.first.copy(entries=entries) to pair.second)
        assertEquals(40,i.yesCount+i.noCount)
    }
    @Test fun quantityQuestionsComparePersonalHigherAndLowerAmounts() {
        val pair=data();val h=JournalHabit("amount","Menge","Wie viel?","Eigene",JournalQuestionKind.NUMBER,"Minuten",true)
        val entries=pair.first.entries.mapIndexed {index,e->e.copy(answers=mapOf("amount" to JournalAnswer(true,(index%11+1).toDouble())),questionIds=setOf("amount"))}
        val j=WellnessJournal(habits=pair.first.habits+h,activeIds=listOf("amount"),entries=entries).validated()
        val i=journalInsights(j,pair.second,today,listOf(JournalOutcome.HRV),zone=zone).single()
        assertNotNull(i.threshold);assertTrue(i.yesCount>=5&&i.noCount>=5)
    }
    @Test fun staleEnergySnapshotsNeverBecomeFreshMorningRecoveryObservations() {
        val pair=data();val days=pair.second.map {d->d.copy(scores=mapOf(E.BODY_BATTERY_ID to E.ScoreResult(E.BODY_BATTERY_ID,55,E.Status.stale,d.calculatedAt,E.Coverage(4,4),components=mapOf("START" to 80),algorithmVersion=E.MODEL_VERSION+"/energy",stale=true)))}
        val i=journalInsights(pair.first,days,today,listOf(JournalOutcome.MORNING_ENERGY),zone=zone).single()
        assertEquals(0,i.yesCount+i.noCount)
    }
    @Test fun controllingTrainingExplainsAwayASpuriousUnadjustedDifference() {
        val random=Random(124);val start=today.minusDays(70)
        val exposures=(0 until 70).map {random.nextBoolean()}
        val load=exposures.map {if(it) 50.0+random.nextDouble()*30 else 20.0+random.nextDouble()*30}
        val entries=exposures.mapIndexed {i,yes->JournalDay(start.plusDays(i.toLong()),mapOf("relaxation" to JournalAnswer(yes)),now,setOf("relaxation"))}
        val days=(0..70).map {i->val date=start.plusDays(i.toLong());val at=date.atTime(12,0).toInstant(ZoneOffset.UTC);val recovery=if(i==0) 70 else (95-load[i-1]*.6).toInt()
            DayInsight(date,at,false,mapOf(E.BODY_BATTERY_ID to E.ScoreResult(E.BODY_BATTERY_ID,recovery,E.Status.available,at,E.Coverage(4,4),components=mapOf("START" to recovery),algorithmVersion=E.MODEL_VERSION+"/energy")),metrics=mapOf("workout_minutes" to if(i<70) load[i] else 0.0),sources=mapOf("_connector" to "Google Health"))}
        val i=journalInsights(WellnessJournal(activeIds=listOf("relaxation"),entries=entries),days,today,listOf(JournalOutcome.MORNING_ENERGY),zone=zone).single()
        assertTrue(abs(i.yesMean!!-i.noMean!!)>10);assertTrue(abs(i.effect!!)<3)
        assertTrue(i.controls.contains("Training am Verhaltenstag"));assertNotEquals("consistent",i.quality)
    }
}
