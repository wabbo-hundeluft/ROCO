package com.wabbo.healthconnectcoach

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HealthConnectDisconnectPaginationTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private class StartedOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).also {
            it.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun disconnectBetweenPagesPreventsFurtherReadsAndDiscardsTheBuffer() {
        val lifecycle = StartedOwner().lifecycle
        var connected = true
        var checks = 0
        var pageReads = 0
        val buffer = mutableListOf<String>()

        runSuspendTest {
            try {
                collectHealthConnectPages(
                    accumulator = buffer,
                    beforeAndAfterPage = {
                        checks += 1
                        assertTrue(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        if (checks == 3) connected = false
                        if (!connected) throw HealthConnectAppDisconnectedException()
                    },
                    readPage = { token ->
                        pageReads += 1
                        assertEquals(null, token)
                        HealthConnectPage(listOf("synthetic-private-record"), "next-page")
                    },
                )
            } catch (_: HealthConnectAppDisconnectedException) {
                // Expected: local disconnect is checked before the second page.
            }
        }

        assertFalse(connected)
        assertEquals(1, pageReads)
        assertTrue(buffer.isEmpty())
    }

    private fun <T> runSuspendTest(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return requireNotNull(outcome).getOrThrow()
    }
}
