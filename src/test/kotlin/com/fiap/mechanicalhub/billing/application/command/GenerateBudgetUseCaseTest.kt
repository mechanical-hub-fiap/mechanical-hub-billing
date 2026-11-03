package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.application.payment.OutboxMessage
import com.fiap.mechanicalhub.billing.application.payment.TransactionRunner
import com.fiap.mechanicalhub.billing.domain.BusinessRuleException
import com.fiap.mechanicalhub.billing.domain.budget.Budget
import com.fiap.mechanicalhub.billing.domain.budget.MaterialRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GenerateBudgetUseCaseTest {
    private val prices = mockk<PriceCatalog>()
    private val budgets = mockk<BudgetStore>()
    private val inbox = mockk<InboxStore>(relaxed = true)
    private val responder = mockk<InboxResponder>(relaxed = true)
    private val transactions =
        object : TransactionRunner {
            override fun <T> run(block: () -> T): T = block()
        }
    private val useCase = GenerateBudgetUseCase(prices, budgets, inbox, responder, transactions)

    private val repairId = UUID.randomUUID()
    private val materialId = UUID.randomUUID()
    private val command =
        GenerateBudgetCommand(
            transactionId = UUID.randomUUID(),
            orderId = UUID.randomUUID(),
            idempotencyKey = "key-1",
            repairIds = listOf(repairId),
            materials = listOf(MaterialRequest(materialId, BigDecimal("3"))),
        )

    @Test
    fun `given new command when executing then saves budget and responds budgetGenerated`() {
        every { responder.claimOrReplay("key-1") } returns true
        every { prices.laborPrices(listOf(repairId)) } returns mapOf(repairId to BigDecimal("100"))
        every { prices.materialPrices(listOf(materialId)) } returns mapOf(materialId to BigDecimal("10"))
        val budgetId = UUID.randomUUID()
        every { budgets.save(command.orderId, command.transactionId, any()) } returns budgetId
        val message = slot<OutboxMessage>()

        useCase.execute(command)

        verify { inbox.limitLockWait() }
        verify { responder.respond("key-1", capture(message)) }
        assertEquals("budgetGenerated", message.captured.type)
        assertEquals(command.orderId, message.captured.orderId)
        assertEquals(budgetId.toString(), message.captured.payload["budgetId"])
        assertEquals(BigDecimal("130.00"), message.captured.payload["orderCost"])
    }

    @Test
    fun `given duplicate command when executing then does not calculate or save`() {
        every { responder.claimOrReplay("key-1") } returns false

        useCase.execute(command)

        verify(exactly = 0) { budgets.save(any(), any(), any<Budget>()) }
        verify(exactly = 0) { responder.respond(any(), any()) }
    }

    @Test
    fun `given repair without price when executing then fails with business rule`() {
        every { responder.claimOrReplay("key-1") } returns true
        every { prices.laborPrices(any()) } returns emptyMap()
        every { prices.materialPrices(any()) } returns mapOf(materialId to BigDecimal("10"))

        assertFailsWith<BusinessRuleException> { useCase.execute(command) }

        verify(exactly = 0) { budgets.save(any(), any(), any<Budget>()) }
    }
}
