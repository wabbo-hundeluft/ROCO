package com.wabbo.healthconnectcoach

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant

class HealthCoachApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(RocoLocales.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        RocoStrings.initialize(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                // Stack locations only: exception messages may contain personal/provider data.
                File(noBackupFilesDir, "last_failure.txt").writeText(buildString {
                    appendLine("Zeit: ${Instant.now()}")
                    appendLine("Typ: ${error.javaClass.name}")
                    appendLine("Thread: ${if (thread.name == "main") "UI" else "Hintergrund"}")
                    error.stackTrace.take(18).forEach { appendLine("${it.className}.${it.methodName}:${it.lineNumber}") }
                })
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
internal fun readAppDiagnostics(context: Context): String {
    val local = runCatching { File(context.noBackupFilesDir, "last_failure.txt").takeIf { it.exists() }?.readText()?.take(4000) }.getOrNull()
    val system = if (Build.VERSION.SDK_INT >= 30) runCatching {
        val exits = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getHistoricalProcessExitReasons(context.packageName, 0, 3)
        exits.firstOrNull { it.reason in setOf(android.app.ApplicationExitInfo.REASON_ANR, android.app.ApplicationExitInfo.REASON_LOW_MEMORY, android.app.ApplicationExitInfo.REASON_CRASH, android.app.ApplicationExitInfo.REASON_CRASH_NATIVE, android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE) }?.let {
            formatProcessExit(it.reason,it.importance,it.timestamp,it.rss)
        }
    }.getOrNull() else null
    return listOfNotNull(system, local).joinToString("\n\n").ifBlank { "Noch kein Absturzbericht auf diesem Gerät vorhanden." }
}


/** Historical system event; never imply that a cached-process eviction was a foreground crash. */
internal fun formatProcessExit(reason: Int,importance: Int,timestamp: Long,rssKb: Long,
    zone: java.time.ZoneId=java.time.ZoneId.systemDefault()): String {
    val cached=importance>=ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
    val foreground=importance==ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    val detail=when(reason) {
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> if(cached)
            "Android hat einen ungenutzten Hintergrundprozess bei systemweitem Speicherdruck beendet. Das allein belegt keinen Absturz der sichtbaren App."
            else "Android hat den Prozess bei systemweitem Speicherdruck beendet."
        android.app.ApplicationExitInfo.REASON_ANR -> "Die App hat nicht rechtzeitig reagiert (ANR)."
        android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Android hat den Prozess wegen zu hoher Ressourcenlast beendet."
        else -> "Android hat einen Prozessabsturz gemeldet."
    }
    val state=when {cached->"Hintergrund · zwischengespeichert";foreground->"Vordergrund";importance>0->"Prozessstatus: $importance";else->"Prozessstatus unbekannt"}
    val time=Instant.ofEpochMilli(timestamp).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("d. MMM yyyy · HH:mm:ss z",java.util.Locale.getDefault()))
    return "Vergangenes Android-Ereignis\n$detail\nZeit: $time\nBeim Beenden: $state"+(if(rssKb>0) "\nArbeitsspeicher (RSS): ${rssKb/1024} MB" else "")
}
