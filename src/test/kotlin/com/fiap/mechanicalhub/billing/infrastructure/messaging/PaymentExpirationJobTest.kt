package com.fiap.mechanicalhub.billing.infrastructure.messaging

import com.fiap.mechanicalhub.billing.application.payment.ExpirationSummary
import com.fiap.mechanicalhub.billing.application.payment.ExpirePaymentsUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PaymentExpirationJobTest {
    private val dataSource = mockk<DataSource>()
    private val connection = mockk<Connection>(relaxed = true)
    private val statement = mockk<Statement>(relaxed = true)
    private val resultSet = mockk<ResultSet>(relaxed = true)
    private val useCase = mockk<ExpirePaymentsUseCase>()
    private val job = PaymentExpirationJob(dataSource, useCase)

    init {
        every { dataSource.connection } returns connection
        every { connection.createStatement() } returns statement
        every { statement.executeQuery(any()) } returns resultSet
        every { resultSet.next() } returns true
    }

    private fun leader(isLeader: Boolean) {
        every { resultSet.getBoolean(1) } returns isLeader
    }

    @Test
    fun `given another instance leading when running then skips the cycle`() {
        leader(false)

        assertNull(job.run())
        verify(exactly = 0) { useCase.execute() }
    }

    @Test
    fun `given leadership when running then executes the use case and releases the lock`() {
        leader(true)
        val summary = ExpirationSummary(confirmed = 1, failed = 2, waiting = 3, alerts = 0)
        every { useCase.execute() } returns summary

        assertEquals(summary, job.run())
        verify { statement.executeQuery(match { it.contains("pg_advisory_unlock") }) }
    }

    @Test
    fun `given an empty cycle when running then returns the zero summary`() {
        leader(true)
        every { useCase.execute() } returns ExpirationSummary(0, 0, 0, 0)

        assertEquals(ExpirationSummary(0, 0, 0, 0), job.run())
    }

    @Test
    fun `given the use case fails when running then still releases the lock`() {
        leader(true)
        every { useCase.execute() } throws IllegalStateException("boom")

        assertFailsWith<IllegalStateException> { job.run() }
        verify { statement.executeQuery(match { it.contains("pg_advisory_unlock") }) }
    }

    @Test
    fun `given the cycle fails when scheduled then the error is swallowed`() {
        every { dataSource.connection } throws IllegalStateException("no connection")

        job.scheduled()
    }

    @Test
    fun `given the cycle succeeds when scheduled then runs the use case`() {
        leader(true)
        every { useCase.execute() } returns ExpirationSummary(0, 0, 0, 0)

        job.scheduled()

        verify { useCase.execute() }
    }
}
