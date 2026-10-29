package com.wabbo.healthconnectcoach

import android.content.Context
import android.content.SharedPreferences
import java.io.File

internal const val PRIMARY_SOURCE_BINDING_PREFERENCES = "primary_source_bindings"

internal fun createPrimarySourceBindingStore(context: Context): PrimarySourceBindingStore =
    PrimarySourceBindingStore(
        context.getSharedPreferences(PRIMARY_SOURCE_BINDING_PREFERENCES, Context.MODE_PRIVATE),
    )

/** Minimal local switch. It stores no health measurements and defaults to disconnected. */
internal interface LocalAccessStore {
    fun isConnected(): Boolean
    fun setConnected(connected: Boolean)
    fun clearLocalState()
}

internal class SharedPreferencesLocalAccessStore(
    private val preferences: SharedPreferences,
) : LocalAccessStore {
    override fun isConnected(): Boolean = preferences.getBoolean(KEY_CONNECTED, false)

    override fun setConnected(connected: Boolean) {
        check(preferences.edit().putBoolean(KEY_CONNECTED, connected).commit()) {
            "Could not update local connection state."
        }
    }

    override fun clearLocalState() {
        check(preferences.edit().clear().commit()) {
            "Could not clear local app preferences."
        }
    }

    private companion object {
        const val KEY_CONNECTED = "local_health_connect_access_enabled"
    }
}

/**
 * Controls only whether this app may initiate reads. Disconnecting never deletes
 * Health Connect records or revokes provider permissions; deletion clears local
 * app state only. The repository also checks this switch before every page.
 */
internal class LocalAccessController(
    private val store: LocalAccessStore,
) {
    fun isConnected(): Boolean = store.isConnected()

    fun connect() = store.setConnected(true)

    fun disconnect() = store.setConnected(false)

    fun deleteLocalAppData() = store.clearLocalState()
}

internal data class LocalDisconnectResult(
    val appAccessDisabled: Boolean,
    val scoreSnapshotCleared: Boolean,
    val sourceBindingsCleared: Boolean,
)

internal fun LocalAccessController.disconnectAndClearScoreSnapshot(
    snapshotStore: EncryptedScoreSnapshotStore,
    sourceBindingStore: PrimarySourceBindingStore,
): LocalDisconnectResult {
    val disconnected = runCatching { disconnect() }.isSuccess
    val snapshotCleared = runCatching { snapshotStore.clear() }.isSuccess
    val sourceBindingsCleared = runCatching { sourceBindingStore.clear() }.isSuccess
    return LocalDisconnectResult(disconnected, snapshotCleared, sourceBindingsCleared)
}

internal enum class LocalAppDataDirectory {
    FILES,
    CACHE,
    NO_BACKUP,
}

/** Operations used by the eraser; nullable listings mean that enumeration failed. */
internal interface LocalAppDataDeletionBackend {
    fun clearLocalPreferences(): Boolean
    fun clearEncryptedScoreSnapshot(): Boolean
    fun clearPrimarySourceBindings(): Boolean
    fun listChildren(directory: LocalAppDataDirectory): List<File>?
    fun deleteFileRecursively(file: File): Boolean
    fun listDatabases(): List<String>?
    fun deleteDatabase(name: String): Boolean
}

internal enum class LocalAppDataDeletionStatus {
    SUCCESS,
    PARTIAL_FAILURE,
    FAILURE,
}

internal data class LocalAppDataDeletionResult(
    val status: LocalAppDataDeletionStatus,
    val confirmedDeletions: Int,
    val failedDeletions: Int,
) {
    val isComplete: Boolean get() = status == LocalAppDataDeletionStatus.SUCCESS
    val retryRecommended: Boolean get() = status != LocalAppDataDeletionStatus.SUCCESS

    val userMessage: String
        get() = when (status) {
            LocalAppDataDeletionStatus.SUCCESS ->
                "Alle lokalen App-Daten wurden gelöscht. Health-Connect-Daten und Berechtigungen sind unverändert."
            LocalAppDataDeletionStatus.PARTIAL_FAILURE ->
                "Ein Teil der lokalen App-Daten wurde gelöscht, aber mindestens eine Löschung wurde nicht bestätigt. Tippen Sie auf „Erneut versuchen“. Health-Connect-Daten und Berechtigungen bleiben unverändert."
            LocalAppDataDeletionStatus.FAILURE ->
                "Keine Löschung konnte als erfolgreich bestätigt werden. Bitte versuchen Sie es erneut. Health-Connect-Daten und Berechtigungen bleiben unverändert."
        }
}

/** Clears app-owned local files/caches/databases plus the app's local access preference. */
internal object LocalAppDataEraser {
    fun erase(context: Context, accessController: LocalAccessController): LocalAppDataDeletionResult =
        erase(
            context,
            accessController,
            createEncryptedScoreSnapshotStore(context),
            createPrimarySourceBindingStore(context),
        )

    fun erase(
        context: Context,
        accessController: LocalAccessController,
        snapshotStore: EncryptedScoreSnapshotStore,
        sourceBindingStore: PrimarySourceBindingStore,
    ): LocalAppDataDeletionResult =
        erase(ContextLocalAppDataDeletionBackend(context, accessController, snapshotStore, sourceBindingStore))

    internal fun erase(backend: LocalAppDataDeletionBackend): LocalAppDataDeletionResult {
        var confirmed = 0
        var failed = 0

        fun attempt(deletion: () -> Boolean) {
            val succeeded = try {
                deletion()
            } catch (_: Exception) {
                false
            }
            if (succeeded) confirmed += 1 else failed += 1
        }

        attempt(backend::clearLocalPreferences)
        attempt(backend::clearEncryptedScoreSnapshot)
        attempt(backend::clearPrimarySourceBindings)

        LocalAppDataDirectory.entries.forEach { directory ->
            val children = try {
                backend.listChildren(directory)
            } catch (_: Exception) {
                null
            }
            if (children == null) {
                failed += 1
            } else {
                children.forEach { child -> attempt { backend.deleteFileRecursively(child) } }
            }
        }

        val databases = try {
            backend.listDatabases()
        } catch (_: Exception) {
            null
        }
        if (databases == null) {
            failed += 1
        } else {
            databases.forEach { name -> attempt { backend.deleteDatabase(name) } }
        }

        val status = when {
            failed == 0 -> LocalAppDataDeletionStatus.SUCCESS
            confirmed > 0 -> LocalAppDataDeletionStatus.PARTIAL_FAILURE
            else -> LocalAppDataDeletionStatus.FAILURE
        }
        return LocalAppDataDeletionResult(status, confirmed, failed)
    }

    private class ContextLocalAppDataDeletionBackend(
        private val context: Context,
        private val accessController: LocalAccessController,
        private val snapshotStore: EncryptedScoreSnapshotStore,
        private val sourceBindingStore: PrimarySourceBindingStore,
    ) : LocalAppDataDeletionBackend {
        override fun clearLocalPreferences(): Boolean {
            accessController.deleteLocalAppData()
            check(context.getSharedPreferences("weekly_journey", Context.MODE_PRIVATE).edit().clear().commit())
            check(context.getSharedPreferences("robot_companion", Context.MODE_PRIVATE).edit().clear().commit())
            SecureLocalDocument(context, "ai_configuration").delete()
            SecureLocalDocument(context, "score_journal").delete()
            SecureLocalDocument(context, "wellness_journal").delete()
            SecureLocalDocument(context, "training_plan").delete()
            SecureLocalDocument(context, "personal_score_profile").delete()
            SecureLocalDocument(context, "coach_memory").delete()
            SecureLocalDocument(context, "sleep_planner").delete()
            SecureLocalDocument(context, "day_analysis").delete()
            SecureLocalDocument(context, "coach_conversation").delete()
            SecureLocalDocument(context, "google_health_oauth").delete()
            GoogleHealthSession.setEnabled(context, false)
            return true
        }

        override fun clearEncryptedScoreSnapshot(): Boolean {
            snapshotStore.clear()
            return true
        }

        override fun clearPrimarySourceBindings(): Boolean {
            sourceBindingStore.clear()
            return true
        }

        override fun listChildren(directory: LocalAppDataDirectory): List<File>? {
            val root = when (directory) {
                LocalAppDataDirectory.FILES -> context.filesDir
                LocalAppDataDirectory.CACHE -> context.cacheDir
                LocalAppDataDirectory.NO_BACKUP -> context.noBackupFilesDir
            }
            return root.listFiles()?.toList()
        }

        override fun deleteFileRecursively(file: File): Boolean = file.deleteRecursively()

        override fun listDatabases(): List<String> = context.databaseList().toList()

        override fun deleteDatabase(name: String): Boolean = context.deleteDatabase(name)
    }
}
