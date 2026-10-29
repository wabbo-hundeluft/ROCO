package com.wabbo.healthconnectcoach

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the dashboard wiring against reintroducing the redundant legacy refresh path. */
class DashboardScorePipelineWiringTest {
    @Test
    fun refreshUsesOnlyApprovedPipelineAndReadsEachActiveRecordTypeOnce() {
        val source = readMainActivity()
        assertFalse("Legacy score reads must not run from MainActivity", source.contains("readOneScore("))
        assertEquals(
            1,
            Regex("""(?m)^\s*readApprovedScores\(currentClient,\s*granted\)""")
                .findAll(source)
                .count(),
        )

        val approvedReader = source.substringAfter("suspend fun readApprovedScores(")
            .substringBefore("suspend fun refreshScoreSnapshots(")
        val activeReads = listOf(
            "readSleepSessionsWithWindow",
            "readHrvRmssdWithWindow",
            "readRestingHeartRateWithWindow",
            "readExerciseSessionsWithWindow",
            "readHeartRateWithWindow",
            "readStepsWithWindow",
        )
        activeReads.forEach { method ->
            assertEquals("Expected one $method call in the active score reader", 1, Regex("""repository\.$method\(""").findAll(approvedReader).count())
        }
        assertTrue(approvedReader.contains("ApprovedWellnessScoreAdapter().score("))
    }

    private fun readMainActivity(): String {
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        val relativePath = "src/main/java/com/wabbo/healthconnectcoach/MainActivity.kt"
        val candidates = listOf(File(workingDirectory, relativePath), File(workingDirectory, "app/$relativePath"))
        val source = candidates.firstOrNull { it.isFile }
        check(source != null) { "Could not locate MainActivity.kt from ${workingDirectory.absolutePath}." }
        return source.readText()
    }
}
