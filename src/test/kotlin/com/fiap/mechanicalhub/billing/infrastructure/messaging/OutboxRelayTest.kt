package com.fiap.mechanicalhub.billing.infrastructure.messaging

import com.fiap.mechanicalhub.billing.application.payment.OutboxMessageSender
import com.fiap.mechanicalhub.billing.application.payment.TransactionRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.util.UUID
import kotlin.test.assertEquals

class OutboxRelayTest {
    private val jdbc = mockk<JdbcClient>()
    private val spec = mockk<JdbcClient.StatementSpec>()
    private val leaderSpec = mockk<JdbcClient.MappedQuerySpec<Boolean?>>()
    private val rowsSpec = mockk<JdbcClient.MappedQuerySpec<Any>>()
    private val resultSet = mockk<ResultSet>()
    private val sender = mockk<OutboxMessageSender>(relaxed = true)
    private val transactions =
        object : TransactionRunner {
            override fun <T> run(block: () -> T): T = block()
        }
    private val relay = OutboxRelay(jdbc, transactions, sender)

    private val eventId = UUID.randomUUID()
    private val aggregateId = UUID.randomUUID()

    init {
        every { jdbc.sql(any<String>()) } returns spec
        every { spec.param(any<String>(), any<Any>()) } returns spec
        every { spec.update() } returns 1
        every { spec.query(Boolean::class.java) } returns leaderSpec
    }

    private fun pending(count: Int, eventType: String = "budgetGenerated") {
        every { resultSet.getLong("sequence") } returns 1L
        every { resultSet.getObject("event_id", UUID::class.java) } returns eventId
        every { resultSet.getObject("aggregate_id", UUID::class.java) } returns aggregateId
        every { resultSet.getString("event_type") } returns eventType
        every { resultSet.getString("payload") } returns "{}"
        val mapper = slot<RowMapper<Any>>()
        every { spec.query(capture(mapper)) } returns rowsSpec
        every { rowsSpec.list() } answers { List(count) { requireNotNull(mapper.captured.mapRow(resultSet, it)) } }
    }

    @Test
    fun `given another instance leading when relaying then sends nothing`() {
        every { leaderSpec.single() } returns false

        assertEquals(0, relay.relay())
        verify(exactly = 0) { sender.send(any(), any(), any()) }
    }

    @Test
    fun `given pending events when relaying then sends each and marks them sent`() {
        every { leaderSpec.single() } returns true
        pending(2)

        assertEquals(2, relay.relay())
        verify(exactly = 2) { sender.send(aggregateId, eventId, "{}") }
        verify(exactly = 2) { spec.update() }
    }

    @Test
    fun `given unmapped event type when relaying then still sends it`() {
        every { leaderSpec.single() } returns true
        pending(1, "somethingNew")

        assertEquals(1, relay.relay())
    }

    @Test
    fun `given send failure when relaying then marks attempt and stops the batch`() {
        every { leaderSpec.single() } returns true
        pending(3, "paymentConfirmed")
        every { sender.send(any(), any(), any()) } throws IllegalStateException("sqs down")

        assertEquals(0, relay.relay())
        verify(exactly = 1) { sender.send(any(), any(), any()) }
        verify(exactly = 1) { spec.param("error", "sqs down") }
    }

    @Test
    fun `given send failure without message when relaying then records the exception name`() {
        every { leaderSpec.single() } returns true
        pending(1)
        every { sender.send(any(), any(), any()) } throws IllegalStateException()

        relay.relay()

        verify { spec.param("error", IllegalStateException::class.java.name) }
    }

    @Test
    fun `given the cycle fails when scheduled then the error is logged and swallowed`() {
        every { jdbc.sql(any<String>()) } throws IllegalStateException("db down")

        relay.scheduled()
    }

    @Test
    fun `given the cycle succeeds when scheduled then relays once`() {
        every { leaderSpec.single() } returns false

        relay.scheduled()

        verify { leaderSpec.single() }
    }
}
