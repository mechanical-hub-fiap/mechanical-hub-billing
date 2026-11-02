package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.application.payment.CreateOrderCommand
import com.fiap.mechanicalhub.billing.application.payment.CreatedOrder
import com.fiap.mechanicalhub.billing.application.payment.OutboxMessage
import com.fiap.mechanicalhub.billing.application.payment.PaymentGateway
import com.fiap.mechanicalhub.billing.application.payment.TransactionRunner
import com.fiap.mechanicalhub.billing.domain.BusinessRuleException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeneratePaymentLinkUseCaseTest {
    private val now = Instant.parse("2026-01-01T10:00:00Z")
    private val gateway = mockk<PaymentGateway>()
    private val links = mockk<PaymentLinkStore>(relaxed = true)
    private val budgets = mockk<BudgetStore>()
    private val responder = mockk<InboxResponder>(relaxed = true)
    private val transactions =
        object : TransactionRunner {
            override fun <T> run(block: () -> T): T = block()
        }
    private val factory = PaymentDeadlineFactory(Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(24))
    private val useCase = GeneratePaymentLinkUseCase(gateway, links, budgets, responder, transactions, factory)

    private val command =
        GeneratePaymentLinkCommand(
            transactionId = UUID.randomUUID(),
            orderId = UUID.randomUUID(),
            idempotencyKey = "key-1",
            budgetId = UUID.randomUUID(),
            orderCost = BigDecimal("150.00"),
        )

    @Test
    fun `given duplicate message when executing then replays without calling the gateway`() {
        every { responder.replay("key-1") } returns true

        useCase.execute(command)

        verify(exactly = 0) { gateway.createOrder(any()) }
        verify(exactly = 0) { links.insert(any()) }
    }

    @Test
    fun `given unknown budget when executing then fails with business rule`() {
        every { responder.replay("key-1") } returns false
        every { budgets.exists(command.budgetId) } returns false

        assertFailsWith<BusinessRuleException> { useCase.execute(command) }

        verify(exactly = 0) { gateway.createOrder(any()) }
    }

    @Test
    fun `given pending payment for transaction when executing then reuses it without calling the gateway`() {
        val dueAt = now.plusSeconds(60)
        every { responder.replay("key-1") } returns false
        every { budgets.exists(command.budgetId) } returns true
        every { links.findPendingByTransaction(command.transactionId) } returns PaymentLink("mp-1", "https://pay", dueAt)
        every { responder.claimOrReplay("key-1") } returns true
        val message = slot<OutboxMessage>()

        useCase.execute(command)

        verify { responder.respond("key-1", capture(message)) }
        assertEquals("paymentLinkGenerated", message.captured.type)
        assertEquals("https://pay", message.captured.payload["paymentLink"])
        assertEquals(dueAt.toString(), message.captured.payload["expiresAt"])
        verify(exactly = 0) { gateway.createOrder(any()) }
    }

    @Test
    fun `given pending payment and lost claim when executing then does not respond again`() {
        every { responder.replay("key-1") } returns false
        every { budgets.exists(command.budgetId) } returns true
        every { links.findPendingByTransaction(command.transactionId) } returns PaymentLink("mp-1", "https://pay", now)
        every { responder.claimOrReplay("key-1") } returns false

        useCase.execute(command)

        verify(exactly = 0) { responder.respond(any(), any()) }
        verify(exactly = 0) { gateway.createOrder(any()) }
    }

    @Test
    fun `given no pending payment when executing then creates order inserts payment and responds`() {
        every { responder.replay("key-1") } returns false
        every { budgets.exists(command.budgetId) } returns true
        every { links.findPendingByTransaction(command.transactionId) } returns null
        every { responder.claimOrReplay("key-1") } returns true
        val order = slot<CreateOrderCommand>()
        every { gateway.createOrder(capture(order)) } returns CreatedOrder("mp-9", "https://checkout")
        val payment = slot<NewPayment>()
        val message = slot<OutboxMessage>()

        useCase.execute(command)

        assertEquals(command.orderId.toString(), order.captured.orderId)
        assertEquals(command.orderCost, order.captured.amount)
        verify { links.insert(capture(payment)) }
        assertEquals("mp-9", payment.captured.mpOrderId)
        assertEquals(now.plus(Duration.ofHours(24)), payment.captured.dueAt)
        verify { responder.respond("key-1", capture(message)) }
        assertEquals("https://checkout", message.captured.payload["paymentLink"])
        assertEquals(payment.captured.dueAt.toString(), message.captured.payload["expiresAt"])
    }

    @Test
    fun `given order created but claim lost when executing then does not insert payment`() {
        every { responder.replay("key-1") } returns false
        every { budgets.exists(command.budgetId) } returns true
        every { links.findPendingByTransaction(command.transactionId) } returns null
        every { gateway.createOrder(any()) } returns CreatedOrder("mp-9", "https://checkout")
        every { responder.claimOrReplay("key-1") } returns false

        useCase.execute(command)

        verify(exactly = 0) { links.insert(any()) }
        verify(exactly = 0) { responder.respond(any(), any()) }
    }
}
