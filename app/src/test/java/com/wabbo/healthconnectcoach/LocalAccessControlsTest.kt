package com.wabbo.healthconnectcoach

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAccessControlsTest {
    private class FakeLocalAccessStore(initiallyConnected: Boolean) : LocalAccessStore {
        private var connectedState = initiallyConnected
        var clearCalls = 0

        override fun isConnected(): Boolean = connectedState
        override fun setConnected(connected: Boolean) {
            this.connectedState = connected
        }
        override fun clearLocalState() {
            clearCalls += 1
            connectedState = false
        }
    }

    private class FakeDeletionBackend(
        private val preferencesResult: Boolean = true,
        private val snapshotResult: Boolean = true,
        private val sourceBindingsResult: Boolean = true,
        private val children: Map<LocalAppDataDirectory, List<File>?> = emptyMap(),
        private val fileResults: Map<String, Boolean> = emptyMap(),
        private val databases: List<String>? = emptyList(),
        private val databaseResults: Map<String, Boolean> = emptyMap(),
    ) : LocalAppDataDeletionBackend {
        val listedDirectories = mutableListOf<LocalAppDataDirectory>()
        val deletedFiles = mutableListOf<String>()
        val deletedDatabases = mutableListOf<String>()
        var snapshotClearCalls = 0
        var sourceBindingClearCalls = 0

        override fun clearLocalPreferences(): Boolean = preferencesResult

        override fun clearEncryptedScoreSnapshot(): Boolean {
            snapshotClearCalls += 1
            return snapshotResult
        }

        override fun clearPrimarySourceBindings(): Boolean {
            sourceBindingClearCalls += 1
            return sourceBindingsResult
        }

        override fun listChildren(directory: LocalAppDataDirectory): List<File>? {
            listedDirectories += directory
            return children[directory] ?: if (directory in children) null else emptyList()
        }

        override fun deleteFileRecursively(file: File): Boolean {
            deletedFiles += file.path
            return fileResults[file.path] ?: true
        }

        override fun listDatabases(): List<String>? = databases

        override fun deleteDatabase(name: String): Boolean {
            deletedDatabases += name
            return databaseResults[name] ?: true
        }
    }

    @Test
    fun disconnectStopsFutureAppReadsWithoutClearingProviderData() {
        val store = FakeLocalAccessStore(initiallyConnected = true)
        val controller = LocalAccessController(store)
        val syntheticHealthConnectSourceData = mutableListOf("synthetic-provider-record")

        controller.disconnect()

        assertFalse(controller.isConnected())
        assertEquals(0, store.clearCalls)
        assertEquals(listOf("synthetic-provider-record"), syntheticHealthConnectSourceData)
    }

    @Test
    fun localDataDeletionClearsAppStateButDoesNotCallProviderOperations() {
        val store = FakeLocalAccessStore(initiallyConnected = true)
        val controller = LocalAccessController(store)
        val syntheticHealthConnectSourceData = mutableListOf("synthetic-provider-record")
        var providerPermissionRevocations = 0
        var providerRecordDeletions = 0

        controller.deleteLocalAppData()

        assertFalse(controller.isConnected())
        assertEquals(1, store.clearCalls)
        assertEquals(0, providerPermissionRevocations)
        assertEquals(0, providerRecordDeletions)
        assertEquals(listOf("synthetic-provider-record"), syntheticHealthConnectSourceData)
    }

    @Test
    fun localConnectionDefaultsAndReturnsToDisconnectedAfterClear() {
        val store = FakeLocalAccessStore(initiallyConnected = false)
        val controller = LocalAccessController(store)
        assertFalse(controller.isConnected())
        controller.connect()
        assertTrue(controller.isConnected())
        controller.deleteLocalAppData()
        assertFalse(controller.isConnected())
    }

    @Test
    fun deletionReportsSuccessOnlyAfterEveryFileAndDatabaseDeletionIsConfirmed() {
        val files = mapOf(
            LocalAppDataDirectory.FILES to listOf(File("files/report.bin")),
            LocalAppDataDirectory.CACHE to listOf(File("cache/temp.bin")),
            LocalAppDataDirectory.NO_BACKUP to listOf(File("no-backup/local.bin")),
        )
        val backend = FakeDeletionBackend(
            children = files,
            databases = listOf("app.db", "cache.db"),
        )

        val result = LocalAppDataEraser.erase(backend)

        assertEquals(LocalAppDataDeletionStatus.SUCCESS, result.status)
        assertTrue(result.isComplete)
        assertFalse(result.retryRecommended)
        assertEquals(8, result.confirmedDeletions)
        assertEquals(0, result.failedDeletions)
        assertEquals(1, backend.snapshotClearCalls)
        assertEquals(1, backend.sourceBindingClearCalls)
        assertEquals(LocalAppDataDirectory.entries, backend.listedDirectories)
        assertEquals(listOf("files/report.bin", "cache/temp.bin", "no-backup/local.bin"), backend.deletedFiles)
        assertEquals(listOf("app.db", "cache.db"), backend.deletedDatabases)
    }

    @Test
    fun partialDeletionFailureIsReportedWithRetryGuidanceAndOtherDeletesAreAttempted() {
        val backend = FakeDeletionBackend(
            children = mapOf(
                LocalAppDataDirectory.FILES to listOf(File("files/removed.bin"), File("files/blocked.bin")),
            ),
            fileResults = mapOf("files/blocked.bin" to false),
            databases = listOf("app.db"),
        )

        val result = LocalAppDataEraser.erase(backend)

        assertEquals(LocalAppDataDeletionStatus.PARTIAL_FAILURE, result.status)
        assertFalse(result.isComplete)
        assertTrue(result.retryRecommended)
        assertTrue(result.userMessage.contains("teilweise") || result.userMessage.contains("Teil"))
        assertTrue(result.userMessage.contains("Erneut versuchen"))
        assertEquals(5, result.confirmedDeletions)
        assertEquals(1, result.failedDeletions)
        assertEquals(1, backend.snapshotClearCalls)
        assertEquals(1, backend.sourceBindingClearCalls)
        assertEquals(listOf("files/removed.bin", "files/blocked.bin"), backend.deletedFiles)
        assertEquals(listOf("app.db"), backend.deletedDatabases)
        assertEquals(LocalAppDataDirectory.entries, backend.listedDirectories)
    }

    @Test
    fun totalDeletionFailureIsAnErrorAndIncludesRetryGuidance() {
        val backend = FakeDeletionBackend(
            preferencesResult = false,
            snapshotResult = false,
            sourceBindingsResult = false,
            children = mapOf(LocalAppDataDirectory.FILES to listOf(File("files/blocked.bin"))),
            fileResults = mapOf("files/blocked.bin" to false),
            databases = listOf("app.db"),
            databaseResults = mapOf("app.db" to false),
        )

        val result = LocalAppDataEraser.erase(backend)

        assertEquals(LocalAppDataDeletionStatus.FAILURE, result.status)
        assertFalse(result.isComplete)
        assertTrue(result.retryRecommended)
        assertTrue(result.userMessage.contains("bestätigt"))
        assertTrue(result.userMessage.contains("erneut"))
        assertEquals(0, result.confirmedDeletions)
        assertEquals(5, result.failedDeletions)
        assertEquals(1, backend.snapshotClearCalls)
        assertEquals(1, backend.sourceBindingClearCalls)
        assertEquals(listOf("files/blocked.bin"), backend.deletedFiles)
        assertEquals(listOf("app.db"), backend.deletedDatabases)
        assertEquals(LocalAppDataDirectory.entries, backend.listedDirectories)
    }

    @Test
    fun inabilityToEnumerateFilesOrDatabasesIsNotReportedAsSuccessfulDeletion() {
        val backend = FakeDeletionBackend(
            children = mapOf(LocalAppDataDirectory.FILES to null),
            databases = null,
        )

        val result = LocalAppDataEraser.erase(backend)

        assertEquals(LocalAppDataDeletionStatus.PARTIAL_FAILURE, result.status)
        assertEquals(3, result.confirmedDeletions)
        assertEquals(2, result.failedDeletions)
        assertTrue(result.retryRecommended)
    }

    @Test
    fun snapshotClearFailureIsReportedAndOtherLocalDeletesStillRun() {
        val backend = FakeDeletionBackend(snapshotResult = false)

        val result = LocalAppDataEraser.erase(backend)

        assertEquals(LocalAppDataDeletionStatus.PARTIAL_FAILURE, result.status)
        assertEquals(2, result.confirmedDeletions)
        assertEquals(1, result.failedDeletions)
        assertEquals(1, backend.snapshotClearCalls)
        assertEquals(1, backend.sourceBindingClearCalls)
        assertTrue(result.userMessage.contains("teilweise") || result.userMessage.contains("Teil"))
    }
}
