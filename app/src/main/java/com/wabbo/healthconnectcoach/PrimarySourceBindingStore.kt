package com.wabbo.healthconnectcoach

import android.content.SharedPreferences
import androidx.health.connect.client.records.metadata.DataOrigin

/** The only two binding choices that may be persisted. */
enum class PrimarySourceBindingStatus {
    AUTO_BOUND,
    USER_SELECTED,
}

data class PrimarySourceBinding(
    val recordType: String,
    val packageName: String,
    val status: PrimarySourceBindingStatus,
)

enum class PrimarySourceAutoBindResult {
    BOUND,
    ALREADY_BOUND,
    MISSING,
    CONFLICT,
}

/** The deliberately small persistence surface used by [PrimarySourceBindingStore]. */
interface PrimarySourceBindingPreferences {
    fun getString(key: String): String?
    fun keys(): Set<String>
    fun putString(key: String, value: String)
    fun remove(key: String)
}

private class SharedPreferencesPrimarySourceBindingPreferences(
    private val preferences: SharedPreferences,
) : PrimarySourceBindingPreferences {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun keys(): Set<String> = preferences.all.keys

    override fun putString(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) {
            "Could not persist primary source binding."
        }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) {
            "Could not remove primary source binding."
        }
    }
}

/**
 * Persists only a record type, its selected origin package, and the binding status.
 * Existing bindings are authoritative until explicitly replaced or removed.
 */
class PrimarySourceBindingStore internal constructor(
    private val preferences: PrimarySourceBindingPreferences,
) {
    constructor(sharedPreferences: SharedPreferences) : this(
        SharedPreferencesPrimarySourceBindingPreferences(sharedPreferences),
    )

    fun bind(
        recordType: String,
        origin: DataOrigin,
        status: PrimarySourceBindingStatus = PrimarySourceBindingStatus.USER_SELECTED,
    ): PrimarySourceBinding = bind(recordType, origin.packageName, status)

    /** Explicitly selects or replaces the origin for [recordType]. */
    fun bind(
        recordType: String,
        packageName: String,
        status: PrimarySourceBindingStatus = PrimarySourceBindingStatus.USER_SELECTED,
    ): PrimarySourceBinding {
        require(recordType.isNotBlank()) { "recordType must not be blank." }
        require(packageName.isNotBlank()) { "packageName must not be blank." }
        val binding = PrimarySourceBinding(recordType, packageName, status)
        preferences.putString(packageKey(recordType), packageName)
        preferences.putString(statusKey(recordType), status.name)
        return binding
    }

    /**
     * Binds only when no binding exists and exactly one origin is available. A
     * missing or conflicting candidate set never changes an existing binding.
     */
    fun autoBind(
        recordType: String,
        origins: Collection<DataOrigin>,
    ): PrimarySourceAutoBindResult {
        require(recordType.isNotBlank()) { "recordType must not be blank." }
        if (get(recordType) != null) return PrimarySourceAutoBindResult.ALREADY_BOUND
        return when (origins.map { it.packageName }.distinct().size) {
            0 -> PrimarySourceAutoBindResult.MISSING
            1 -> {
                bind(recordType, origins.first().packageName, PrimarySourceBindingStatus.AUTO_BOUND)
                PrimarySourceAutoBindResult.BOUND
            }
            else -> PrimarySourceAutoBindResult.CONFLICT
        }
    }

    fun get(recordType: String): PrimarySourceBinding? {
        val packageName = preferences.getString(packageKey(recordType)) ?: return null
        val status = preferences.getString(statusKey(recordType))
            ?.let { value -> runCatching { PrimarySourceBindingStatus.valueOf(value) }.getOrNull() }
            ?: return null
        return PrimarySourceBinding(recordType, packageName, status)
    }

    fun clear() {
        preferences.keys()
            .filter { it.startsWith(KEY_PREFIX) }
            .forEach(preferences::remove)
    }

    fun remove(recordType: String) {
        preferences.remove(packageKey(recordType))
        preferences.remove(statusKey(recordType))
    }

    fun allBindings(): List<PrimarySourceBinding> =
        preferences.keys()
            .asSequence()
            .filter { it.startsWith(KEY_PREFIX) && it.endsWith(PACKAGE_SUFFIX) }
            .mapNotNull { key -> get(key.removePrefix(KEY_PREFIX).removeSuffix(PACKAGE_SUFFIX)) }
            .sortedBy { it.recordType }
            .toList()

    private fun packageKey(recordType: String): String = "$KEY_PREFIX$recordType$PACKAGE_SUFFIX"

    private fun statusKey(recordType: String): String = "$KEY_PREFIX$recordType$STATUS_SUFFIX"

    private companion object {
        const val KEY_PREFIX = "primary_source_binding."
        const val PACKAGE_SUFFIX = ".packageName"
        const val STATUS_SUFFIX = ".status"
    }
}
