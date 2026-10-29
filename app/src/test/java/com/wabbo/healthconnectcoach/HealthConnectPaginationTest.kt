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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HealthConnectPaginationTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun start() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
    }

    @Test
    fun revocationBetweenPagesStopsFurtherReadsAndClearsAccumulatedRecords() {
        val owner = TestLifecycleOwner().also { it.start() }
        val buffer = mutableListOf("synthetic-raw-record-1")
        var permissionChecks = 0
        var pageReads = 0
        val exception = assertThrows(HealthConnectPermissionRevokedException::class.java) {
            runSuspend {
                collectHealthConnectPages(
                    accumulator = buffer,
                    beforeAndAfterPage = {
                        assertTrue(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        permissionChecks += 1
                        if (permissionChecks >= 3) throw HealthConnectPermissionRevokedException()
                    },
                    readPage = { token ->
                        pageReads += 1
                        assertEquals(null, token)
                        HealthConnectPage(listOf("synthetic-raw-record-2"), "page-2")
                    },
                )
            }
        }
        assertTrue(exception.message!!.contains("discarded"))
        assertEquals(3, permissionChecks)
        assertEquals(1, pageReads)
        assertTrue("Raw health records must be erased on an abort.", buffer.isEmpty())
    }

    @Test
    fun lifecycleIsCheckedAfterEveryResponseBeforeItsRecordsAreKept() {
        val owner = TestLifecycleOwner().also { it.start() }
        val buffer = mutableListOf("synthetic-raw-record-1")
        var lifecycleChecks = 0
        var pageReads = 0
        assertThrows(HealthConnectForegroundRequiredException::class.java) {
            runSuspend {
                collectHealthConnectPages(
                    accumulator = buffer,
                    beforeAndAfterPage = {
                        lifecycleChecks += 1
                        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            throw HealthConnectForegroundRequiredException()
                        }
                    },
                    readPage = {
                        pageReads += 1
                        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                        HealthConnectPage(listOf("synthetic-provider-page"), "page-2")
                    },
                )
            }
        }
        assertEquals(2, lifecycleChecks)
        assertEquals(1, pageReads)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun revokedPermissionAfterFinalResponseAlsoDiscardsThatResponse() {
        val owner = TestLifecycleOwner().also { it.start() }
        val buffer = mutableListOf<String>()
        var permissionChecks = 0
        assertThrows(HealthConnectPermissionRevokedException::class.java) {
            runSuspend {
                collectHealthConnectPages(
                    accumulator = buffer,
                    beforeAndAfterPage = {
                        assertTrue(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        permissionChecks += 1
                        if (permissionChecks == 2) throw HealthConnectPermissionRevokedException()
                    },
                    readPage = { HealthConnectPage(listOf("synthetic-final-page"), null) },
                )
            }
        }
        assertEquals(2, permissionChecks)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun repeatedPageTokenAbortsAndClearsBuffer() {
        val owner = TestLifecycleOwner().also { it.start() }
        val buffer = mutableListOf<String>()
        var pages = 0
        assertThrows(IllegalStateException::class.java) {
            runSuspend {
                collectHealthConnectPages(
                    accumulator = buffer,
                    beforeAndAfterPage = {
                        assertTrue(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                    },
                    readPage = {
                        pages += 1
                        HealthConnectPage(listOf("synthetic-page-$pages"), "same-token")
                    },
                )
            }
        }
        assertEquals(2, pages)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun allPagesCompleteOnlyWhileLifecycleIsStarted() {
        val owner = TestLifecycleOwner().also { it.start() }
        var pages = 0
        val result = runSuspend {
            collectHealthConnectPages(
                beforeAndAfterPage = {
                    assertTrue(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                },
                readPage = { token ->
                    pages += 1
                    if (token == null) HealthConnectPage(listOf(1, 2), "next")
                    else HealthConnectPage(listOf(3), null)
                },
            )
        }
        assertEquals(listOf(1, 2, 3), result)
        assertEquals(2, pages)
        assertFalse(result.isEmpty())
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return requireNotNull(outcome) { "Test suspend block unexpectedly suspended." }.getOrThrow()
    }
}
