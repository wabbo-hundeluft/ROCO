package com.wabbo.healthconnectcoach

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AppDiagnosticsTest {
    private val at=Instant.parse("2026-10-26T15:23:52.242Z").toEpochMilli()
    @Test fun cachedLowMemoryIsExplicitlyHistoricalAndBackgroundWithLocalTime() {
        val text=formatProcessExit(ApplicationExitInfo.REASON_LOW_MEMORY,ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,at,65536,ZoneId.of("Europe/Berlin"))
        assertTrue(text.startsWith("Vergangenes Android-Ereignis"));assertTrue(text.contains("ungenutzten Hintergrundprozess"));assertTrue(text.contains("16:23:52 MEZ")||text.contains("16:23:52 CET"));assertTrue(text.contains("64 MB"))
        assertFalse(text.contains("Beim Beenden: Vordergrund"))
    }
    @Test fun foregroundLowMemoryIsNeverPresentedAsCachedEviction() {
        val text=formatProcessExit(ApplicationExitInfo.REASON_LOW_MEMORY,ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,at,0,ZoneId.of("UTC"))
        assertTrue(text.contains("Beim Beenden: Vordergrund"));assertFalse(text.contains("ungenutzten Hintergrundprozess"));assertFalse(text.contains("Arbeitsspeicher (RSS)"))
    }
    @Test fun anrRemainsAnAnrEvenIfItHappenedInBackground() {
        val text=formatProcessExit(ApplicationExitInfo.REASON_ANR,ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,at,0,ZoneId.of("UTC"))
        assertTrue(text.contains("nicht rechtzeitig reagiert (ANR)"));assertFalse(text.contains("systemweitem Speicherdruck"))
    }
}
