package com.wabbo.healthconnectcoach

import org.junit.Assert.*
import org.junit.Test

class PixelDebugRegressionTest {
    @Test fun personalMetricQuestionsDoNotImplyAPlanChange() {
        listOf("Bitte erkläre meine HRV. Keine Planänderung.","Nichts am Plan ändern, bitte.","Lass uns meinen Plan besprechen.","Was bedeutet mein Schlafscore?").forEach {assertFalse(it,planChangeRequested(it))}
        listOf("Bitte ändere meinen Trainingsplan.","Verschiebe meine Einheit auf morgen.","Kein Lauftraining. Aber ändere meinen Plan auf Radfahren.").forEach {assertTrue(it,planChangeRequested(it))}
    }
    @Test fun pipeTablesBecomeStructuredRowsWithInlineStyles() {
        val blocks=markdownBlocks("| Kennzahl | Wert |\n|:---|---:|\n| **HRV** | 36 ms |\n\nDanach Text.")
        assertEquals(listOf("table","paragraph"),blocks.map {it.kind})
        assertEquals(listOf("**HRV**","36 ms"),blocks.first().rows[1])
        assertEquals("HRV",markdownInline(blocks.first().rows[1][0]).text)
    }
    @Test fun escapedPipesAndCodeFencesDoNotCorruptTables() {
        assertEquals(listOf("A | B","C"),markdownTableCells("| A \\| B | C |"))
        assertEquals("code",markdownBlocks("```\n| A | B |\n|---|---|\n```").single().kind)
        assertEquals("paragraph",markdownBlocks("A | B\nnot a separator").single().kind)
    }
    @Test fun promptExplicitlyDisallowsReassuranceFromHrvAlone() {
        assertTrue(COACH_SYSTEM_PROMPT.contains("Daraus allein darfst du weder"))
        assertTrue(COACH_SYSTEM_PROMPT.contains("Fehlen von Überlastung"))
        assertEquals("health-coach-6",COACH_PROMPT_VERSION)
    }
}
