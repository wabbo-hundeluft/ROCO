package com.wabbo.healthconnectcoach

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrainingAndMarkdownTest {
    private val monday=LocalDate.of(2026,10,19)
    private fun plan()=TrainingPlanLogic.create(TrainingGoals(days=setOf(1,3,5)),monday)
    @Test fun selectedDaysDetermineThePlanAndRestDaysAreNotScheduled() {val p=plan();assertTrue(p.sessions.all {it.date.dayOfWeek.value in setOf(1,3,5)});assertFalse(p.sessions.any {it.completed})}
    @Test fun plannedRestDayDoesNotBreakTheStreak() {var p=plan();p=TrainingPlanLogic.complete(p,p.sessions[0].id,true,monday);assertEquals(1,TrainingPlanLogic.streak(p,monday.plusDays(1)));p=TrainingPlanLogic.complete(p,p.sessions[1].id,true,monday.plusDays(2));assertEquals(2,TrainingPlanLogic.streak(p,monday.plusDays(3)))}
    @Test fun missedTrainingDayBreaksTheStreak() {var p=plan();p=TrainingPlanLogic.complete(p,p.sessions[0].id,true,monday);assertEquals(0,TrainingPlanLogic.streak(p,monday.plusDays(3)))}
    @Test fun futureCompletionIsRejected() {val p=plan();assertThrows(IllegalArgumentException::class.java) {TrainingPlanLogic.complete(p,p.sessions[1].id,true,monday)}}
    @Test fun planAndCompletionHistorySurviveSerialization() {val initial=plan();val p=TrainingPlanLogic.complete(initial,initial.sessions.first().id,true,monday);assertEquals(p,decodePlan(encodePlan(p)))}
    @Test fun toolCannotMarkTrainingAsCompleted() {val p=plan();assertThrows(IllegalStateException::class.java) {applyPlanTool(p,"complete_session",JSONObject(),monday)}}
    @Test fun invalidToolArgumentsDoNotMutateTheOriginalPlan() {val p=plan();assertThrows(IllegalArgumentException::class.java) {applyPlanTool(p,"update_session",JSONObject().put("session_id",p.sessions.first().id).put("title","Test").put("minutes",999),monday)};assertEquals(30,p.sessions.first().minutes)}
    @Test fun moveToolUsesConcreteIdsAndValidDates() {val p=plan();val next=applyPlanTool(p,"move_session",JSONObject().put("session_id",p.sessions.first().id).put("date",monday.plusDays(1).toString()),monday).first;assertEquals(monday.plusDays(1),next.sessions.first().date);assertTrue(next.revision>p.revision)}
    @Test fun markdownBlocksRecognizeHeadingsListsAndFencedCode() {val blocks=markdownBlocks("## Erholung\n\n- **Schlaf** zählt\n\n```\na < b\n```");assertEquals(listOf("heading","list","code"),blocks.map {it.kind});assertEquals("a < b",blocks.last().text)}
    @Test fun boldAndItalicMarkersBecomeStylesRatherThanVisibleCharacters() {val result=markdownInline("**Gut** und *ruhig*");assertEquals("Gut und ruhig",result.text);assertTrue(result.spanStyles.size>=2)}
    @Test fun escapedMarkersAndInlineCodeRemainLiteral() {assertEquals("**Text**",markdownInline("\\*\\*Text\\*\\*").text);assertEquals("a_b",markdownInline("`a_b`").text)}
    @Test fun dangerousLinksAreNeverClickable() {assertFalse(safeChatLink("javascript:alert(1)"));assertFalse(safeChatLink("file:///sdcard/a"));assertFalse(safeChatLink("https://name:secret@example.com"));assertTrue(safeChatLink("https://example.com/path"))}
}
