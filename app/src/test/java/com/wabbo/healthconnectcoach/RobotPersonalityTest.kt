package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RobotPersonalityTest {
    private val date=LocalDate.of(2026,10,27)
    @Test fun nightSleepsAndMorningWakeOccursOnlyOncePerDay() {
        assertEquals(RobotMood.SLEEPING,robotMoment(23,null,date));assertEquals(RobotMood.SLEEPING,robotMoment(4,date.minusDays(1).toString(),date))
        assertEquals(RobotMood.WAKING,robotMoment(8,date.minusDays(1).toString(),date))
        assertNull(robotMoment(8,date.toString(),date));assertNull(robotMoment(13,null,date))
    }
    @Test fun overloadedOrEmptyBatteryNeverTriggersCelebration() {
        assertEquals(RobotMood.CAREFUL,robotScoreMood(E.SESSION_LOAD_ID,263))
        assertEquals(RobotMood.RECOVER,robotScoreMood(E.BODY_BATTERY_ID,12))
        assertEquals(RobotMood.PROUD,robotScoreMood(E.SESSION_LOAD_ID,100))
        assertEquals(RobotMood.CURIOUS,robotScoreMood(E.SLEEP_ID,null))
    }
    @Test fun allExpressionsHaveDistinctFiniteGeometryAndAsymmetricWave() {
        val poses=RobotMood.entries.map {robotPose(it,.35f)}
        assertTrue(poses.distinct().size>=10)
        poses.forEach {assertTrue(it.eyes>0&&it.eyes<=30);assertTrue(it.tilt.isFinite()&&it.bob.isFinite())}
        val wave=robotPose(RobotMood.WAVE,.35f);assertTrue(wave.rightArm<wave.leftArm)
        assertEquals(3f,robotPose(RobotMood.SLEEPING,.3f).eyes,.01f)
        assertTrue(robotPose(RobotMood.WAKING,1f).eyes>robotPose(RobotMood.WAKING,0f).eyes)
    }
    @Test fun cargoItemsAreCollectedMonotonicallyAndOnlyAfterPassingThem() {
        assertEquals(0,collectedRobotItems(.1f));assertEquals(1,collectedRobotItems(.2f));assertEquals(4,collectedRobotItems(1f))
        val counts=(0..100).map {collectedRobotItems(it/100f)}
        assertTrue(counts.zipWithNext().all {it.first<=it.second})
    }
    @Test fun spontaneousActionsPreserveRecoveryAndDoNotEncourageOverload() {
        for(beat in 0..12) {
            assertTrue(robotAmbientMood(RobotMood.RECOVER,beat) in setOf(RobotMood.COMFORT,RobotMood.NOD,RobotMood.WINK,RobotMood.SHY,RobotMood.CURIOUS))
            assertTrue(robotAmbientMood(RobotMood.CAREFUL,beat) in setOf(RobotMood.COMFORT,RobotMood.NOD,RobotMood.WINK,RobotMood.SHY,RobotMood.CURIOUS))
            assertEquals(RobotMood.SLEEPING,robotAmbientMood(RobotMood.SLEEPING,beat))
        }
        assertTrue((0..5).map {robotAmbientMood(RobotMood.IDLE,it)}.distinct().size>=3)
    }
    @Test fun sleepFullyRetractsAndWakingUnfoldsBeforeStretching() {
        assertEquals(1f,robotPose(RobotMood.SLEEPING,.5f).fold,0f)
        assertEquals(1f,robotPose(RobotMood.WAKING,0f).fold,0f)
        assertEquals(0f,robotPose(RobotMood.WAKING,1f).fold,0f)
        val mid=robotPose(RobotMood.WAKING,.7f)
        assertTrue(mid.leftArm<120&&mid.rightArm<120)
        RobotMood.entries.forEach {mood->(0..100).forEach {i->
            val p=robotPose(mood,i/100f)
            assertTrue(listOf(p.tilt,p.leftArm,p.rightArm,p.eyes,p.look,p.bob,p.shift,p.lift,p.lean,p.squash,p.fold,p.leftHandX,p.rightHandX,p.headY,p.rightEye).all {it.isFinite()})
            assertTrue(p.squash in .85f..1.15f&&p.fold in 0f..1f)
        }}
    }
    @Test fun pickupRequiresAGripBeforeItCanEnterTheCargoBay() {
        val frames=(0..5000).map {robotRoadFrame(it/100f)}
        assertTrue(frames.zipWithNext().all {it.second.position>=it.first.position-.0001f&&it.second.stored>=it.first.stored})
        assertEquals((0..3).toSet(),frames.filter(::robotHoldsItem).map {it.item}.toSet())
        for(item in 0..3) {
            val phases=frames.filter {it.item==item}
            assertTrue(phases.map {it.stage}.containsAll(listOf(RobotPickupStage.LOOK,RobotPickupStage.REACH,RobotPickupStage.GRIP,RobotPickupStage.LIFT,RobotPickupStage.ADMIRE,RobotPickupStage.STOW,RobotPickupStage.RETURN)))
            assertTrue(phases.filter {it.stage==RobotPickupStage.REACH}.none(::robotHoldsItem))
            assertEquals(item+1,robotRoadFrame(robotPickupEnd(item)+.01f).stored)
        }
        val waiting=robotRoadFrame(ROBOT_ROAD_SECONDS+100)
        assertEquals(RobotPickupStage.WAIT,waiting.stage)
        assertEquals(4,waiting.stored)
        assertTrue("A long import must not look finished",waiting.position<1f)
    }
    @Test fun reactionsDistinguishAttentionAchievementErrorsAndAffectionateTaps() {
        assertEquals(RobotMood.NOD,robotEventMood(RobotEvent.INPUT,RobotMood.LISTENING))
        assertEquals(RobotMood.NOD,robotEventMood(RobotEvent.ANSWERED,RobotMood.LISTENING))
        assertEquals(RobotMood.HIGH_FIVE,robotEventMood(RobotEvent.SUCCESS,RobotMood.PROUD))
        assertEquals(RobotMood.COMFORT,robotEventMood(RobotEvent.ERROR,RobotMood.CHEERFUL))
        assertEquals(RobotMood.WAKING,robotTapMood(RobotMood.SLEEPING,1))
        assertEquals(6,(1..6).map {robotTapMood(RobotMood.IDLE,it)}.distinct().size)
        assertTrue((1..6).map {robotTapMood(RobotMood.RECOVER,it)}.none {it==RobotMood.DANCE||it==RobotMood.CELEBRATE})
        assertTrue(robotPose(RobotMood.WINK,.4f).rightEye<.2f)
        assertTrue(robotPose(RobotMood.COMFORT,.5f).leftHandX>95)
    }
}
