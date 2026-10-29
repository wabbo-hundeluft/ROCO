package com.wabbo.healthconnectcoach

import androidx.health.connect.client.records.metadata.DataOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimarySourceBindingStoreTest {
    private class FakePreferences : PrimarySourceBindingPreferences {
        val values = linkedMapOf<String, String>()

        override fun getString(key: String): String? = values[key]
        override fun keys(): Set<String> = values.keys.toSet()
        override fun putString(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private fun store(preferences: FakePreferences = FakePreferences()) =
        PrimarySourceBindingStore(preferences) to preferences

    private fun origin(packageName: String) = DataOrigin(packageName)

    @Test
    fun uniqueOriginAutoBindsWithAutoStatus() {
        val (store, _) = store()

        assertEquals(
            PrimarySourceAutoBindResult.BOUND,
            store.autoBind("sleep", listOf(origin("com.sleep"))),
        )
        assertEquals(
            PrimarySourceBinding("sleep", "com.sleep", PrimarySourceBindingStatus.AUTO_BOUND),
            store.get("sleep"),
        )
    }

    @Test
    fun multipleOriginsRemainUnboundAsConflict() {
        val (store, preferences) = store()

        assertEquals(
            PrimarySourceAutoBindResult.CONFLICT,
            store.autoBind("sleep", listOf(origin("com.one"), origin("com.two"))),
        )
        assertTrue(preferences.values.isEmpty())
    }

    @Test
    fun existingBindingIsStableAndDoesNotFallbackWhenCandidatesChange() {
        val (store, _) = store()
        store.bind("sleep", "com.selected")

        assertEquals(
            PrimarySourceAutoBindResult.ALREADY_BOUND,
            store.autoBind("sleep", listOf(origin("com.other"))),
        )
        assertEquals("com.selected", store.get("sleep")?.packageName)
        assertEquals(
            PrimarySourceAutoBindResult.ALREADY_BOUND,
            store.autoBind("sleep", emptyList()),
        )
        assertEquals("com.selected", store.get("sleep")?.packageName)
    }

    @Test
    fun explicitBindReplacesAnExistingBinding() {
        val (store, _) = store()
        store.bind("sleep", "com.auto", PrimarySourceBindingStatus.AUTO_BOUND)

        val rebound = store.bind("sleep", origin("com.user"))

        assertEquals(
            PrimarySourceBinding("sleep", "com.user", PrimarySourceBindingStatus.USER_SELECTED),
            rebound,
        )
        assertEquals(rebound, store.get("sleep"))
    }

    @Test
    fun clearRemovesAllBindingsButDoesNotTouchUnrelatedPreferences() {
        val (store, preferences) = store()
        store.bind("sleep", "com.sleep")
        store.bind("readiness", "com.readiness")
        preferences.values["unrelated"] = "keep"

        store.clear()

        assertTrue(store.allBindings().isEmpty())
        assertEquals(mapOf("unrelated" to "keep"), preferences.values)
    }

    @Test
    fun persistedKeysAndValuesContainOnlyTypePackageAndStatus() {
        val (store, preferences) = store()
        store.bind("android睡眠", "com.example.health", PrimarySourceBindingStatus.USER_SELECTED)

        assertEquals(
            mapOf(
                "primary_source_binding.android睡眠.packageName" to "com.example.health",
                "primary_source_binding.android睡眠.status" to "USER_SELECTED",
            ),
            preferences.values,
        )
        assertNull(preferences.values["recordId"])
        assertNull(preferences.values["timestamp"])
        assertNull(preferences.values["candidate"])
        assertEquals(
            listOf(
                PrimarySourceBinding(
                    "android睡眠",
                    "com.example.health",
                    PrimarySourceBindingStatus.USER_SELECTED,
                ),
            ),
            store.allBindings(),
        )
    }
}
