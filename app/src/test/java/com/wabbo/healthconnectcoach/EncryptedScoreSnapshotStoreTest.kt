package com.wabbo.healthconnectcoach

import com.example.healthcoach.score.contract.ScoreCoverage
import com.example.healthcoach.score.contract.ScoreResult
import com.example.healthcoach.score.contract.ScoreStatus
import com.example.healthcoach.score.contract.ScoreWindow
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedScoreSnapshotStoreTest {
    private class MemoryBytesStore : SnapshotBytesStore {
        var storedBytes: ByteArray? = null
        var failWrites = false
        var failDeletes = false

        override fun read(): ByteArray? = storedBytes?.copyOf()

        override fun writeAtomically(bytes: ByteArray) {
            if (failWrites) throw IOException("synthetic atomic write failure")
            storedBytes = bytes.copyOf()
        }

        override fun delete() {
            if (failDeletes) throw IOException("synthetic file deletion failure")
            storedBytes = null
        }
    }

    private class MemoryKeyProvider : SnapshotKeyProvider {
        private var key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
        var keyPresent = true
        var failDeletion = false
        var deleteCalls = 0

        override fun keyForDecryption() = key.takeIf { keyPresent }

        override fun keyForEncryption() = if (keyPresent) key else {
            key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
            keyPresent = true
            key
        }

        override fun deleteKey() {
            deleteCalls += 1
            if (failDeletion) throw IOException("synthetic key deletion failure")
            keyPresent = false
        }
    }

    private class MemoryLocalAccessStore(initiallyConnected: Boolean) : LocalAccessStore {
        var connectedState = initiallyConnected

        override fun isConnected() = connectedState
        override fun setConnected(connected: Boolean) {
            this.connectedState = connected
        }
        override fun clearLocalState() {
            connectedState = false
        }
    }

    private val readAt = LocalDateTime.parse("2026-10-24T12:00:00")
    private val zone = ZoneId.of("UTC")

    private fun snapshot(id: String = "sleep", value: Int = 84) = PersistedScoreSnapshot(
        scoreId = id,
        value0to100 = value,
        status = ScoreStatus.AVAILABLE,
        calculatedAt = Instant.parse("2026-10-24T11:45:00Z"),
        lastSuccessfulReadAt = Instant.parse("2026-10-24T12:00:00Z"),
        algorithmVersion = "hc-score-v0.3",
    )

    private fun score(
        scoreId: String = "sleep",
        value: Int? = 84,
        status: ScoreStatus = ScoreStatus.AVAILABLE,
        algorithmVersion: String = "hc-score-v0.3",
    ) = ScoreResult(
        scoreId = scoreId,
        value0to100 = value,
        status = status,
        asOf = OffsetDateTime.parse("2026-10-24T11:45:00Z"),
        window = ScoreWindow(meaning = "synthetic"),
        components = emptyList(),
        dataSources = emptyList(),
        coverage = ScoreCoverage(availableComponents = 0, expectedComponents = 0, ratio = 0.0),
        missingReasons = emptyList(),
        algorithmVersion = algorithmVersion,
    )

    private fun approvedScore(
        scoreId: String = ApprovedWellnessScoreEngine.SLEEP_ID,
        value: Int? = 84,
        status: ApprovedWellnessScoreEngine.Status = ApprovedWellnessScoreEngine.Status.available,
        algorithmVersion: String = scoreId,
    ) = ApprovedWellnessScoreEngine.ScoreResult(
        scoreId = scoreId,
        score = value,
        status = status,
        asOf = Instant.parse("2026-10-24T11:45:00Z"),
        coverage = ApprovedWellnessScoreEngine.Coverage(
            available = 4,
            expected = 4,
            sources = setOf("private.source.package"),
            missingReasons = listOf("example_missing_reason"),
            sourcesByType = mapOf(ApprovedWellnessScoreEngine.RecordType.STEPS to setOf("private.source.package")),
        ),
        components = mapOf("private_component" to 91),
        rating0to100 = value,
        algorithmVersion = algorithmVersion,
    )

    private fun store(bytes: MemoryBytesStore, keys: MemoryKeyProvider) =
        EncryptedScoreSnapshotStore(bytes, keys)

    @Test
    fun encryptedSnapshotRoundTripsAcrossStoreInstancesWithOnlyApprovedFields() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        val expected = snapshot()

        store(bytes, keys).upsert(expected)
        val restored = store(bytes, keys).readAll()

        assertEquals(listOf(expected), restored)
        assertNotNull(bytes.storedBytes)
        assertTrue(String(bytes.storedBytes!!, Charsets.ISO_8859_1).contains("hc-score-v0.3").not())
        assertEquals(expected, expected.copy())
        assertEquals(expected.toScoreResult(zone).value0to100, 84)
        assertEquals(expected.toScoreResult(zone).components, emptyList<com.example.healthcoach.score.contract.ScoreComponent>())
    }

    @Test
    fun authenticatedEncryptionRejectsCiphertextTampering() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        store(bytes, keys).upsert(snapshot())
        val tampered = requireNotNull(bytes.storedBytes).copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        bytes.storedBytes = tampered

        val failure = runCatching { store(bytes, keys).readAll() }.exceptionOrNull()

        assertNotNull(failure)
    }

    @Test
    fun emptyInvalidOrUnapprovedScoresDoNotBecomeSnapshotCandidates() {
        assertNull(scoreSnapshotCandidate(score(value = null, status = ScoreStatus.INSUFFICIENT_DATA), readAt, zone))
        assertNull(scoreSnapshotCandidate(score("training_recovery_estimate", 50), readAt, zone))
        assertNull(scoreSnapshotCandidate(score(algorithmVersion = "hc-score-v0.4"), readAt, zone))
        assertNull(scoreSnapshotCandidate(score(value = 101), readAt, zone))
        assertNull(scoreSnapshotCandidate(score(status = ScoreStatus.SOURCE_CONFLICT), readAt, zone))

        val candidate = scoreSnapshotCandidate(score(status = ScoreStatus.PROVISIONAL), readAt, zone)
        assertNotNull(candidate)
        assertEquals(Instant.parse("2026-10-24T11:45:00Z"), candidate?.calculatedAt)
        assertEquals(Instant.parse("2026-10-24T12:00:00Z"), candidate?.lastSuccessfulReadAt)
    }

    @Test
    fun onlyThreeDScoreIdsPersistAndRestoreAsStaleScoreOnlySnapshots() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        val snapshotStore = store(bytes, keys)
        val readAtInstant = Instant.parse("2026-10-24T12:00:00Z")
        val ids = setOf(
            ApprovedWellnessScoreEngine.SLEEP_ID,
            ApprovedWellnessScoreEngine.BODY_BATTERY_ID,
            ApprovedWellnessScoreEngine.SESSION_LOAD_ID,
        )

        ids.forEachIndexed { index, id ->
            assertNotNull(snapshotStore.upsertEligibleScore(approvedScore(scoreId = id, value = 70 + index), readAtInstant))
        }
        assertNull(scoreSnapshotCandidate(approvedScore(scoreId = "unapproved-score-v1"), readAtInstant))
        assertNull(scoreSnapshotCandidate(approvedScore(status = ApprovedWellnessScoreEngine.Status.insufficient_data), readAtInstant))
        assertNull(scoreSnapshotCandidate(approvedScore(algorithmVersion = "unexpected-version"), readAtInstant))

        val persisted = snapshotStore.readAll()
        assertEquals(ids, persisted.map { it.scoreId }.toSet())
        persisted.forEach { snapshot ->
            val restored = requireNotNull(snapshot.approvedScoreSnapshotOrNull())
            assertEquals(ApprovedWellnessScoreEngine.Status.stale, restored.score.status)
            assertTrue(restored.score.stale)
            assertTrue(restored.score.components.isEmpty())
            assertTrue(restored.score.coverage.sources.isEmpty())
            assertTrue(restored.score.coverage.sourcesByType.isEmpty())
            assertEquals(listOf("restored_local_score_snapshot"), restored.score.coverage.missingReasons)
            assertEquals(Instant.parse("2026-10-24T11:45:00Z"), restored.score.asOf)
            assertEquals(readAtInstant, restored.lastSuccessfulReadAt)
        }
        assertFalse(persisted.any { it.scoreId !in ids })
    }

    @Test
    fun emptyReadDoesNotUpdatePreviouslyPersistedValueOrReadTime() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        val snapshotStore = store(bytes, keys)
        val previous = snapshot()
        snapshotStore.upsert(previous)

        val candidate = snapshotStore.upsertEligibleScore(
            score(value = null, status = ScoreStatus.INSUFFICIENT_DATA),
            readAt.plusHours(3),
            zone,
        )

        assertNull(candidate)
        assertEquals(listOf(previous), snapshotStore.readAll())
        val retained = snapshotStore.readAll().single()
        assertEquals(previous.calculatedAt, retained.calculatedAt)
        assertEquals(previous.lastSuccessfulReadAt, retained.lastSuccessfulReadAt)
    }

    @Test
    fun failedAtomicWriteLeavesPreviousEncryptedSnapshotReadable() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        val snapshotStore = store(bytes, keys)
        val original = snapshot()
        snapshotStore.upsert(original)
        val originalCiphertext = requireNotNull(bytes.storedBytes).copyOf()
        bytes.failWrites = true

        val failure = runCatching { snapshotStore.upsert(snapshot("readiness", 72)) }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(originalCiphertext.contentEquals(requireNotNull(bytes.storedBytes)))
        assertEquals(listOf(original), snapshotStore.readAll())
    }

    @Test
    fun removingRevokedScorePreservesOtherScoreAndClearRemovesFileAndKey() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        val snapshotStore = store(bytes, keys)
        val sleep = snapshot("sleep", 84)
        val readiness = snapshot("readiness", 72)
        snapshotStore.upsert(sleep)
        snapshotStore.upsert(readiness)

        snapshotStore.remove("sleep")
        assertEquals(listOf(readiness), snapshotStore.readAll())
        snapshotStore.remove("readiness")
        assertNull(bytes.storedBytes)
        assertTrue(keys.keyPresent)

        snapshotStore.upsert(sleep)
        snapshotStore.clear()
        assertNull(bytes.storedBytes)
        assertFalse(keys.keyPresent)
        assertEquals(1, keys.deleteCalls)
        assertTrue(store(bytes, keys).readAll().isEmpty())
    }

    @Test
    fun disconnectClearsSnapshotFileAndKeyAcrossRestart() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        val snapshotStore = store(bytes, keys)
        snapshotStore.upsert(snapshot())
        val accessStore = MemoryLocalAccessStore(initiallyConnected = true)
        val accessController = LocalAccessController(accessStore)
        val bindingValues = linkedMapOf<String, String>()
        val bindingStore = PrimarySourceBindingStore(object : PrimarySourceBindingPreferences {
            override fun getString(key: String) = bindingValues[key]
            override fun keys() = bindingValues.keys.toSet()
            override fun putString(key: String, value: String) { bindingValues[key] = value }
            override fun remove(key: String) { bindingValues.remove(key) }
        })
        bindingStore.bind("SleepSessionRecord", "com.synthetic.sleep")

        val result = accessController.disconnectAndClearScoreSnapshot(snapshotStore, bindingStore)

        assertTrue(result.appAccessDisabled)
        assertTrue(result.scoreSnapshotCleared)
        assertTrue(result.sourceBindingsCleared)
        assertFalse(accessController.isConnected())
        assertTrue(bindingStore.allBindings().isEmpty())
        assertNull(bytes.storedBytes)
        assertFalse(keys.keyPresent)
        assertTrue(store(bytes, keys).readAll().isEmpty())
    }

    @Test
    fun localDataEraserClearsSnapshotFileAndKeyAcrossRestart() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        val snapshotStore = store(bytes, keys)
        snapshotStore.upsert(snapshot())
        val backend = object : LocalAppDataDeletionBackend {
            override fun clearLocalPreferences() = true
            override fun clearEncryptedScoreSnapshot(): Boolean {
                snapshotStore.clear()
                return true
            }
            override fun clearPrimarySourceBindings() = true
            override fun listChildren(directory: LocalAppDataDirectory) = emptyList<java.io.File>()
            override fun deleteFileRecursively(file: java.io.File) = true
            override fun listDatabases() = emptyList<String>()
            override fun deleteDatabase(name: String) = true
        }

        val result = LocalAppDataEraser.erase(backend)

        assertTrue(result.isComplete)
        assertEquals(3, result.confirmedDeletions)
        assertNull(bytes.storedBytes)
        assertFalse(keys.keyPresent)
        assertTrue(store(bytes, keys).readAll().isEmpty())
    }

    @Test
    fun pauseRetainsPersistedSnapshotAcrossRestartAndBlocksResumeRead() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        val expected = snapshot()
        store(bytes, keys).upsert(expected)

        assertFalse(shouldReadScoresOnResume(locallyConnected = true, readsPaused = true))
        assertTrue(shouldReadScoresOnResume(locallyConnected = true, readsPaused = false))
        assertFalse(shouldReadScoresOnResume(locallyConnected = false, readsPaused = false))
        assertEquals(listOf(expected), store(bytes, keys).readAll())
    }

    @Test
    fun clearFailureStillAttemptsBothFileAndKeyDeletionAndReportsFailure() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        val snapshotStore = store(bytes, keys)
        snapshotStore.upsert(snapshot())
        bytes.failDeletes = true

        val failure = runCatching { snapshotStore.clear() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(1, keys.deleteCalls)
        assertFalse(keys.keyPresent)
        assertNotNull(bytes.storedBytes)
    }

    @Test
    fun keyDeletionFailureStillDeletesFileAndReportsFailure() {
        val bytes = MemoryBytesStore()
        val keys = MemoryKeyProvider()
        val snapshotStore = store(bytes, keys)
        snapshotStore.upsert(snapshot())
        keys.failDeletion = true

        val failure = runCatching { snapshotStore.clear() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(1, keys.deleteCalls)
        assertNull(bytes.storedBytes)
        assertTrue(keys.keyPresent)
    }
}
