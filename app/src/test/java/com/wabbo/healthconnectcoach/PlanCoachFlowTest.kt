package com.wabbo.healthconnectcoach

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlanCoachFlowTest {
    private val today=LocalDate.of(2026,10,26)
    private fun args()=JSONObject().put("movement_minutes",160).put("session_minutes",35).put("training_days",JSONArray(listOf(2,4,7))).put("intensity",1.5).put("sleep_minutes",495)
    private fun call(name: String,args: JSONObject)=JSONObject().put("type","function").put("function",JSONObject().put("name",name).put("arguments",args.toString()))
    private fun response(vararg calls: JSONObject)=JSONObject().put("tool_calls",JSONArray(calls.toList()))
    private fun plan()=TrainingPlanLogic.create(TrainingGoals(days=setOf(1,3,5)),today)

    @Test fun initialToolProducesOnlyProposalWithExplicitGoals() {
        val reply=parsePlanCoachReply(response(call("propose_initial_plan",args())),null,today)
        assertNull(reply.updatedPlan);assertTrue(reply.changes.isEmpty())
        val p=reply.proposedPlan!!
        assertEquals(160,p.goals.movementMinutes);assertEquals(35,p.goals.sessionMinutes)
        assertEquals(495,p.goals.sleepMinutes);assertEquals(setOf(2,4,7),p.goals.days)
        assertTrue(p.sessions.all {!it.completed&&it.recordedActivity==null})
        assertEquals(p,validateInitialPlanProposal(p,today))
    }
    @Test fun missingOrExtraGoalsAndDuplicateWeekdaysAreRejected() {
        val missing=args();missing.remove("sleep_minutes")
        assertThrows(IllegalArgumentException::class.java) {initialPlanFromArguments(missing,today)}
        assertThrows(IllegalArgumentException::class.java) {initialPlanFromArguments(args().put("completed",true),today)}
        assertThrows(IllegalArgumentException::class.java) {initialPlanFromArguments(args().put("training_days",JSONArray(listOf(2,2))),today)}
    }
    @Test fun decimalsStringsAndOutOfRangeValuesNeverSilentlyBecomeGoals() {
        assertThrows(IllegalArgumentException::class.java) {initialPlanFromArguments(args().put("session_minutes",35.9),today)}
        assertThrows(IllegalArgumentException::class.java) {initialPlanFromArguments(args().put("movement_minutes","160"),today)}
        assertThrows(IllegalArgumentException::class.java) {initialPlanFromArguments(args().put("intensity",3.0),today)}
    }
    @Test fun noPlanAllowsOnlyOneInitialProposalNotMutationOrCompletion() {
        assertThrows(IllegalArgumentException::class.java) {parsePlanCoachReply(response(call("delete_session",JSONObject().put("session_id","a"))),null,today)}
        assertThrows(IllegalArgumentException::class.java) {parsePlanCoachReply(response(call("propose_initial_plan",args()),call("propose_initial_plan",args())),null,today)}
    }
    @Test fun initialProposalCannotOverwriteExistingPlan() {
        assertThrows(IllegalStateException::class.java) {parsePlanCoachReply(response(call("propose_initial_plan",args())),plan(),today)}
    }
    @Test fun ordinaryTextDoesNotCreatePlanOrFakeChanges() {
        val reply=parsePlanCoachReply(JSONObject().put("content","Welche Tage passen?"),null,today)
        assertNull(reply.proposedPlan);assertNull(reply.updatedPlan);assertTrue(reply.changes.isEmpty())
    }
    @Test fun staleAndCompletedProposalsCannotBeAccepted() {
        val proposal=initialPlanFromArguments(args(),today)
        assertThrows(IllegalArgumentException::class.java) {validateInitialPlanProposal(proposal,today.plusWeeks(2))}
        assertThrows(IllegalArgumentException::class.java) {validateInitialPlanProposal(proposal.copy(sessions=proposal.sessions.map {it.copy(completed=true)}),today)}
    }
    @Test fun proposalSurvivesChatRoundTripWithoutBeingMarkedSaved() {
        val proposal=initialPlanFromArguments(args(),today)
        val message=CoachMessage("assistant","Bitte prüfen",planProposal=proposal)
        val restored=decodeCoachMessages(encodeCoachMessages(listOf(message))).single()
        assertEquals(message,restored);assertTrue(restored.planChanges.isEmpty())
    }
    @Test fun legacyChatStillLoadsAndUserMessagesCannotCarryAProposal() {
        assertEquals(listOf(CoachMessage("user","Hallo")),decodeCoachMessages("""[{"role":"user","text":"Hallo"}]"""))
        val forged=CoachMessage("user","x",planProposal=initialPlanFromArguments(args(),today))
        assertThrows(IllegalArgumentException::class.java) {decodeCoachMessages(encodeCoachMessages(listOf(forged)))}
    }
    @Test fun newToolsAreExposedOnlyForTheirAppropriateStage() {
        val initial=planTools(true).toString();val existing=planTools(false).toString()
        assertTrue(initial.contains("propose_initial_plan"));assertFalse(initial.contains("delete_session"))
        assertFalse(existing.contains("propose_initial_plan"));assertTrue(existing.contains("set_session_intensity"))
    }
    @Test fun deleteOpenUnitKeepsHistoryAndDeletedDateDoesNotReappearOnExtension() {
        val p=plan();val id=p.sessions.first().id
        val next=applyPlanTool(p,"delete_session",JSONObject().put("session_id",id),today).first
        assertFalse(next.sessions.any {it.id==id});assertEquals(p.sessions.size-1,next.sessions.size)
        assertFalse(TrainingPlanLogic.extend(next,today.plusWeeks(1)).sessions.any {it.date==today})
        assertEquals(p,decodePlan(encodePlan(p)))
    }
    @Test fun pastOrCompletedSessionsCannotBeDeletedMovedOrReweighted() {
        val p=plan();val id=p.sessions.first().id
        val done=TrainingPlanLogic.complete(p,id,true,today)
        for(candidate in listOf(done,p.copy(sessions=p.sessions.map {it.copy(date=it.date.minusWeeks(10))}))) {
            assertThrows(IllegalArgumentException::class.java) {applyPlanTool(candidate,"delete_session",JSONObject().put("session_id",id),today)}
            assertThrows(IllegalArgumentException::class.java) {applyPlanTool(candidate,"set_session_intensity",JSONObject().put("session_id",id).put("intensity",2.0),today)}
            assertThrows(IllegalArgumentException::class.java) {TrainingPlanLogic.move(candidate,id,today.plusDays(1),today)}
        }
    }
    @Test fun individualIntensityChangesNeitherCompletionNorWeeklyGoal() {
        val p=plan();val id=p.sessions.first().id
        val next=applyPlanTool(p,"set_session_intensity",JSONObject().put("session_id",id).put("intensity",2.0),today).first
        assertEquals(2.0,next.sessions.first {it.id==id}.intensity,0.0)
        assertEquals(p.goals,next.goals);assertFalse(next.sessions.first {it.id==id}.completed)
    }
    @Test fun responseBatchIsAtomicWhenLaterActionFails() {
        val p=plan();val id=p.sessions.first().id
        val calls=response(call("delete_session",JSONObject().put("session_id",id)),call("set_session_intensity",JSONObject().put("session_id",id).put("intensity",2.0)))
        assertThrows(IllegalStateException::class.java) {parsePlanCoachReply(calls,p,today)}
        assertEquals(18,p.sessions.size)
    }
    @Test fun malformedModelPayloadHasActionableErrorAndCannotMutatePlan() {
        val p=plan()
        val malformed=JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("function",JSONObject().put("name","delete_session").put("arguments","not-json"))))
        val error=assertThrows(IllegalArgumentException::class.java) {parsePlanCoachReply(malformed,p,today)}
        assertTrue(error.message.orEmpty().contains("unverändert"));assertEquals(18,p.sessions.size)
    }
    @Test fun fractionalMinutesAndHiddenCompletionArgumentAreRejected() {
        val p=plan();val id=p.sessions.first().id
        assertThrows(IllegalArgumentException::class.java) {applyPlanTool(p,"update_session",JSONObject().put("session_id",id).put("title","Lauf").put("minutes",30.9),today)}
        assertThrows(IllegalArgumentException::class.java) {applyPlanTool(p,"update_session",JSONObject().put("session_id",id).put("title","Lauf").put("minutes",30).put("completed",true),today)}
    }
}
