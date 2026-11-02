package com.fiap.mechanicalhub.billing.infrastructure.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpringTransactionRunnerTest {
    private val status = mockk<TransactionStatus>(relaxed = true)
    private val manager = mockk<PlatformTransactionManager>(relaxed = true)
    private val runner = SpringTransactionRunner(manager)

    init {
        every { manager.getTransaction(any()) } returns status
    }

    @Test
    fun `when running then returns the block result and commits`() {
        assertEquals(42, runner.run { 42 })

        verify { manager.commit(status) }
    }

    @Test
    fun `given the block fails when running then rolls back and rethrows`() {
        assertFailsWith<IllegalStateException> { runner.run { error("boom") } }

        verify { manager.rollback(status) }
    }

    @Test
    fun `clock bean is UTC`() {
        assertEquals(ZoneOffset.UTC, ClockConfig().clock().zone)
    }
}
