package com.wabbo.healthconnectcoach

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.example.healthcoach.score.contract.ScoreCoverage
import com.example.healthcoach.score.contract.ScoreResult
import com.example.healthcoach.score.contract.ScoreStatus
import com.example.healthcoach.score.contract.ScoreWindow
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The only score fields allowed to survive an app process or device restart. */
internal data class PersistedScoreSnapshot(
    val scoreId: String,
    val value0to100: Int,
    val status: ScoreStatus,
    val calculatedAt: Instant,
    val lastSuccessfulReadAt: Instant,
    val algorithmVersion: String,
)

/** Empty, invalid, unapproved, or unsupported score results are not persistable. */
internal fun scoreSnapshotCandidate(
    score: ScoreResult,
    readAt: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): PersistedScoreSnapshot? {
    if (score.scoreId !in LEGACY_PERSISTED_SCORE_IDS) return null
    val value = score.value0to100 ?: return null
    if (
        value !in 0..100 ||
        score.status !in PERSISTABLE_SCORE_STATUSES ||
        score.algorithmVersion != APPROVED_SCORE_ALGORITHM_VERSION
    ) return null
    return PersistedScoreSnapshot(
        scoreId = score.scoreId,
        value0to100 = value,
        status = score.status,
        calculatedAt = score.asOf.toInstant(),
        lastSuccessfulReadAt = readAt.atZone(zoneId).toInstant(),
        algorithmVersion = score.algorithmVersion,
    )
}

internal fun scoreSnapshotCandidate(
    score: ApprovedWellnessScoreEngine.ScoreResult,
    readAt: Instant,
): PersistedScoreSnapshot? {
    if (score.scoreId !in APPROVED_PERSISTED_SCORE_IDS) return null
    val value = score.score ?: return null
    val status = when (score.status) {
        ApprovedWellnessScoreEngine.Status.available -> ScoreStatus.AVAILABLE
        ApprovedWellnessScoreEngine.Status.provisional -> ScoreStatus.PROVISIONAL
        else -> return null
    }
    if (value < 0 || (score.scoreId != ApprovedWellnessScoreEngine.SESSION_LOAD_ID && value > 100) ||
        !approvedVersion(score.scoreId, score.algorithmVersion)) return null
    return PersistedScoreSnapshot(
        scoreId = score.scoreId,
        value0to100 = value,
        status = status,
        calculatedAt = score.asOf,
        lastSuccessfulReadAt = readAt,
        algorithmVersion = score.algorithmVersion,
    )
}

internal fun PersistedScoreSnapshot.featureIdOrNull(): String? =
    scoreId.takeIf { it in LEGACY_PERSISTED_SCORE_IDS }

internal data class RestoredApprovedScoreSnapshot(
    val score: ApprovedWellnessScoreEngine.ScoreResult,
    val lastSuccessfulReadAt: Instant,
)

internal fun PersistedScoreSnapshot.approvedScoreSnapshotOrNull(): RestoredApprovedScoreSnapshot? {
    if (scoreId !in APPROVED_PERSISTED_SCORE_IDS) return null
    val restoredStatus = when (status) {
        ScoreStatus.AVAILABLE, ScoreStatus.PROVISIONAL -> ApprovedWellnessScoreEngine.Status.stale
        else -> return null
    }
    if (!approvedVersion(scoreId, algorithmVersion)) return null
    return RestoredApprovedScoreSnapshot(
        score = ApprovedWellnessScoreEngine.ScoreResult(
            scoreId = scoreId,
            score = value0to100,
            status = restoredStatus,
            asOf = calculatedAt,
            coverage = ApprovedWellnessScoreEngine.Coverage(
                available = 0,
                expected = 0,
                missingReasons = listOf("restored_local_score_snapshot"),
            ),
            rawIndex = value0to100.toDouble().takeIf { scoreId == ApprovedWellnessScoreEngine.SESSION_LOAD_ID },
            algorithmVersion = algorithmVersion,
            stale = true,
        ),
        lastSuccessfulReadAt = lastSuccessfulReadAt,
    )
}

/** Restored score-only model: no source, component, record, or history data is reconstructed. */
internal fun PersistedScoreSnapshot.toScoreResult(zoneId: ZoneId = ZoneId.systemDefault()): ScoreResult =
    ScoreResult(
        scoreId = scoreId,
        value0to100 = value0to100,
        status = status,
        asOf = OffsetDateTime.ofInstant(calculatedAt, zoneId),
        window = ScoreWindow(meaning = "restored_local_score_snapshot"),
        components = emptyList(),
        dataSources = emptyList(),
        coverage = ScoreCoverage(availableComponents = 0, expectedComponents = 0, ratio = 0.0),
        missingReasons = listOf("restored_local_score_snapshot"),
        algorithmVersion = algorithmVersion,
    )

internal fun shouldReadScoresOnResume(locallyConnected: Boolean, readsPaused: Boolean): Boolean =
    locallyConnected && !readsPaused

internal interface SnapshotBytesStore {
    fun read(): ByteArray?
    fun writeAtomically(bytes: ByteArray)
    fun delete()
}

internal interface SnapshotKeyProvider {
    fun keyForDecryption(): SecretKey?
    fun keyForEncryption(): SecretKey
    fun deleteKey()
}

/** Stores only encoded score metadata; encryption and persistence are injected for JVM tests. */
internal class EncryptedScoreSnapshotStore(
    private val bytesStore: SnapshotBytesStore,
    private val keyProvider: SnapshotKeyProvider,
) {
    @Synchronized
    fun readAll(): List<PersistedScoreSnapshot> {
        val encrypted = bytesStore.read() ?: return emptyList()
        val key = keyProvider.keyForDecryption()
            ?: throw IOException("Encrypted score snapshot key is unavailable.")
        return ScoreSnapshotCodec.decode(SnapshotCipher.decrypt(encrypted, key))
    }

    @Synchronized
    fun upsert(snapshot: PersistedScoreSnapshot) {
        require(isValidSnapshot(snapshot))
        val merged = readAll().associateBy { it.scoreId }.toMutableMap()
        merged[snapshot.scoreId] = snapshot
        writeSnapshots(merged.values)
    }

    /** Removes only this approved score, preserving the other score if it exists. */
    @Synchronized
    fun remove(scoreId: String) {
        require(scoreId in ALLOWED_PERSISTED_SCORE_IDS)
        val existing = readAll()
        val remaining = existing.filterNot { it.scoreId == scoreId }
        if (remaining.size == existing.size) return
        if (remaining.isEmpty()) {
            bytesStore.delete()
        } else {
            writeSnapshots(remaining)
        }
    }

    /** Attempts both deletions even if one fails, so a file error cannot leave the key untouched. */
    @Synchronized
    fun clear() {
        var deletionFailure: Exception? = null
        try {
            bytesStore.delete()
        } catch (error: Exception) {
            deletionFailure = error
        }
        try {
            keyProvider.deleteKey()
        } catch (error: Exception) {
            if (deletionFailure == null) deletionFailure = error
        }
        deletionFailure?.let {
            throw IOException("Could not completely clear the encrypted score snapshot.", it)
        }
    }

    private fun writeSnapshots(snapshots: Collection<PersistedScoreSnapshot>) {
        require(snapshots.size <= MAX_SNAPSHOT_COUNT)
        val key = keyProvider.keyForEncryption()
        val encoded = ScoreSnapshotCodec.encode(snapshots)
        bytesStore.writeAtomically(SnapshotCipher.encrypt(encoded, key))
    }
}

internal fun EncryptedScoreSnapshotStore.upsertEligibleScore(
    score: ScoreResult,
    readAt: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): PersistedScoreSnapshot? {
    val candidate = scoreSnapshotCandidate(score, readAt, zoneId) ?: return null
    upsert(candidate)
    return candidate
}

internal fun EncryptedScoreSnapshotStore.upsertEligibleScore(
    score: ApprovedWellnessScoreEngine.ScoreResult,
    readAt: Instant,
): PersistedScoreSnapshot? {
    val candidate = scoreSnapshotCandidate(score, readAt) ?: return null
    upsert(candidate)
    return candidate
}

internal fun createEncryptedScoreSnapshotStore(context: Context): EncryptedScoreSnapshotStore {
    val file = File(context.noBackupFilesDir, SCORE_SNAPSHOT_FILE_NAME)
    return EncryptedScoreSnapshotStore(
        bytesStore = AtomicFileSnapshotBytesStore(file),
        keyProvider = AndroidKeystoreSnapshotKeyProvider(),
    )
}

private fun isValidSnapshot(snapshot: PersistedScoreSnapshot): Boolean =
    snapshot.scoreId in ALLOWED_PERSISTED_SCORE_IDS &&
        snapshot.value0to100 >= 0 && (snapshot.scoreId == ApprovedWellnessScoreEngine.SESSION_LOAD_ID || snapshot.value0to100 <= 100) &&
        snapshot.status in PERSISTABLE_SCORE_STATUSES &&
        when (snapshot.scoreId) {
            in LEGACY_PERSISTED_SCORE_IDS -> snapshot.algorithmVersion == APPROVED_SCORE_ALGORITHM_VERSION
            in APPROVED_PERSISTED_SCORE_IDS -> approvedVersion(snapshot.scoreId, snapshot.algorithmVersion)
            else -> false
        }

private fun approvedVersion(id: String, version: String): Boolean = version == id ||
    version == ApprovedWellnessScoreEngine.MODEL_VERSION + "/" + id ||
    version == "health-coach-v2/" + id ||
    (id == ApprovedWellnessScoreEngine.SESSION_LOAD_ID && version == "health-coach-v2/weekly") ||
    (id == ApprovedWellnessScoreEngine.SESSION_LOAD_ID && version in setOf(ApprovedWellnessScoreEngine.MODEL_VERSION + "/weekly",ApprovedWellnessScoreEngine.WEEKLY_MODEL_VERSION))

private class AtomicFileSnapshotBytesStore(baseFile: File) : SnapshotBytesStore {
    private val atomicFile = AtomicFile(baseFile)

    override fun read(): ByteArray? {
        if (!atomicFile.baseFile.exists()) return null
        atomicFile.openRead().use { input ->
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_ENCRYPTED_FILE_BYTES) throw IOException("Encrypted score snapshot is too large.")
                result.write(buffer, 0, count)
            }
            return result.toByteArray()
        }
    }

    override fun writeAtomically(bytes: ByteArray) {
        require(bytes.size <= MAX_ENCRYPTED_FILE_BYTES)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    override fun delete() {
        val baseFile = atomicFile.baseFile
        atomicFile.delete()
        if (
            baseFile.exists() ||
            File(baseFile.path + ".bak").exists() ||
            File(baseFile.path + ".new").exists()
        ) {
            throw IOException("Could not delete encrypted score snapshot files.")
        }
    }
}

private class AndroidKeystoreSnapshotKeyProvider : SnapshotKeyProvider {
    override fun keyForDecryption(): SecretKey? {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(SCORE_SNAPSHOT_KEY_ALIAS)) return null
        return keyStore.getKey(SCORE_SNAPSHOT_KEY_ALIAS, null) as? SecretKey
    }

    override fun keyForEncryption(): SecretKey {
        keyForDecryption()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                SCORE_SNAPSHOT_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override fun deleteKey() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(SCORE_SNAPSHOT_KEY_ALIAS)) {
            keyStore.deleteEntry(SCORE_SNAPSHOT_KEY_ALIAS)
            if (keyStore.containsAlias(SCORE_SNAPSHOT_KEY_ALIAS)) {
                throw IOException("Could not delete encrypted score snapshot key.")
            }
        }
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
}

private object ScoreSnapshotCodec {
    fun encode(snapshots: Collection<PersistedScoreSnapshot>): ByteArray {
        require(snapshots.size <= MAX_SNAPSHOT_COUNT)
        require(snapshots.all(::isValidSnapshot))
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(SNAPSHOT_PLAINTEXT_MAGIC)
            output.writeInt(SNAPSHOT_FORMAT_VERSION)
            output.writeInt(snapshots.size)
            snapshots.sortedBy { it.scoreId }.forEach { snapshot ->
                output.writeUTF(snapshot.scoreId)
                output.writeInt(snapshot.value0to100)
                output.writeUTF(snapshot.status.name)
                output.writeLong(snapshot.calculatedAt.toEpochMilli())
                output.writeLong(snapshot.lastSuccessfulReadAt.toEpochMilli())
                output.writeUTF(snapshot.algorithmVersion)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<PersistedScoreSnapshot> {
        if (bytes.size > MAX_PLAINTEXT_BYTES) throw IOException("Score snapshot payload is too large.")
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != SNAPSHOT_PLAINTEXT_MAGIC) throw IOException("Invalid score snapshot format.")
            if (input.readInt() != SNAPSHOT_FORMAT_VERSION) throw IOException("Unsupported score snapshot format.")
            val count = input.readInt()
            if (count !in 0..MAX_SNAPSHOT_COUNT) throw IOException("Invalid score snapshot entry count.")
            val snapshots = buildList {
                repeat(count) {
                    val scoreId = input.readUTF()
                    val value = input.readInt()
                    val status = runCatching { ScoreStatus.valueOf(input.readUTF()) }
                        .getOrElse { throw IOException("Invalid score status.", it) }
                    val calculatedAt = Instant.ofEpochMilli(input.readLong())
                    val lastReadAt = Instant.ofEpochMilli(input.readLong())
                    val algorithmVersion = input.readUTF()
                    val snapshot = PersistedScoreSnapshot(
                        scoreId = scoreId,
                        value0to100 = value,
                        status = status,
                        calculatedAt = calculatedAt,
                        lastSuccessfulReadAt = lastReadAt,
                        algorithmVersion = algorithmVersion,
                    )
                    if (!isValidSnapshot(snapshot)) throw IOException("Invalid score snapshot entry.")
                    add(snapshot)
                }
            }
            if (input.available() != 0) throw IOException("Unexpected score snapshot data.")
            if (snapshots.map { it.scoreId }.toSet().size != snapshots.size) {
                throw IOException("Duplicate score snapshot entries.")
            }
            return snapshots
        }
    }
}

private object SnapshotCipher {
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        if (iv.size != GCM_IV_BYTES) throw GeneralSecurityException("Unexpected AES-GCM IV length.")
        cipher.updateAAD(SNAPSHOT_CIPHER_AAD)
        val ciphertext = cipher.doFinal(plaintext)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(SNAPSHOT_CIPHER_MAGIC)
            data.writeByte(SNAPSHOT_FORMAT_VERSION)
            data.writeByte(iv.size)
            data.write(iv)
            data.write(ciphertext)
        }
        return output.toByteArray()
    }

    fun decrypt(encrypted: ByteArray, key: SecretKey): ByteArray {
        if (encrypted.size !in MIN_ENCRYPTED_FILE_BYTES..MAX_ENCRYPTED_FILE_BYTES) {
            throw IOException("Invalid encrypted score snapshot size.")
        }
        DataInputStream(ByteArrayInputStream(encrypted)).use { input ->
            if (input.readInt() != SNAPSHOT_CIPHER_MAGIC) throw IOException("Invalid encrypted score snapshot header.")
            if (input.readUnsignedByte() != SNAPSHOT_FORMAT_VERSION) throw IOException("Unsupported encrypted score snapshot version.")
            val ivSize = input.readUnsignedByte()
            if (ivSize != GCM_IV_BYTES) throw IOException("Invalid encrypted score snapshot IV.")
            val iv = ByteArray(ivSize)
            input.readFully(iv)
            val ciphertext = input.readBytes()
            if (ciphertext.size < GCM_TAG_BYTES) throw IOException("Encrypted score snapshot is truncated.")
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(SNAPSHOT_CIPHER_AAD)
            return cipher.doFinal(ciphertext)
        }
    }
}

private val LEGACY_PERSISTED_SCORE_IDS = setOf("sleep", "readiness")
private val APPROVED_PERSISTED_SCORE_IDS = setOf(
    ApprovedWellnessScoreEngine.SLEEP_ID,
    ApprovedWellnessScoreEngine.BODY_BATTERY_ID,
    ApprovedWellnessScoreEngine.SESSION_LOAD_ID,
)
private val ALLOWED_PERSISTED_SCORE_IDS = LEGACY_PERSISTED_SCORE_IDS + APPROVED_PERSISTED_SCORE_IDS
private val PERSISTABLE_SCORE_STATUSES = setOf(ScoreStatus.AVAILABLE, ScoreStatus.PROVISIONAL)
private const val APPROVED_SCORE_ALGORITHM_VERSION = "hc-score-v0.3"
private const val SCORE_SNAPSHOT_FILE_NAME = "health_coach_score_snapshot.enc"
private const val SCORE_SNAPSHOT_KEY_ALIAS = "health_coach_score_snapshot_aes_gcm_v1"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
private const val GCM_IV_BYTES = 12
private val SNAPSHOT_CIPHER_AAD = "health_coach_score_snapshot:v1".toByteArray(Charsets.UTF_8)
private const val SNAPSHOT_CIPHER_MAGIC = 0x48435345 // HCSE
private const val SNAPSHOT_PLAINTEXT_MAGIC = 0x48435350 // HCSP
private const val SNAPSHOT_FORMAT_VERSION = 1
private const val MAX_SNAPSHOT_COUNT = 5
private const val MAX_PLAINTEXT_BYTES = 8 * 1024
private const val MAX_ENCRYPTED_FILE_BYTES = MAX_PLAINTEXT_BYTES + 64
private const val MIN_ENCRYPTED_FILE_BYTES = 4 + 1 + 1 + GCM_IV_BYTES + GCM_TAG_BYTES
