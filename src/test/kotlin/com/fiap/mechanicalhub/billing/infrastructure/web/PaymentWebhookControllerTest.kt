package com.fiap.mechanicalhub.billing.infrastructure.web

import com.fiap.mechanicalhub.billing.application.payment.ConfirmPaymentUseCase
import com.fiap.mechanicalhub.billing.application.payment.WebhookResult
import com.fiap.mechanicalhub.billing.infrastructure.mercadopago.WebhookSignatureValidator
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.dao.DataRetrievalFailureException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentWebhookControllerTest {
    private val validator = mockk<WebhookSignatureValidator>()
    private val confirmPayment = mockk<ConfirmPaymentUseCase>()
    private val registry = SimpleMeterRegistry()
    private val controller = PaymentWebhookController(validator, confirmPayment, registry)

    private fun counter(status: String) = registry.counter("billing_webhook_total", "result", status).count()

    private fun body(id: String) = """{"data":{"id":"$id"}}"""

    @Test
    fun `given valid signature and confirmed payment when receiving then 200`() {
        every { validator.isValid("sig", "req", "ORD1") } returns true
        every { confirmPayment.execute("ORD1") } returns WebhookResult.CONFIRMED

        val response = controller.receive(body("ORD1"), "sig", "req", null)

        assertEquals(200, response.statusCode.value())
        assertEquals(1.0, counter("200"))
    }

    @Test
    fun `given ignored result when receiving then 200`() {
        every { validator.isValid(any(), any(), any()) } returns true
        every { confirmPayment.execute("ORD1") } returns WebhookResult.IGNORED

        assertEquals(200, controller.receive(body("ORD1"), "sig", "req", null).statusCode.value())
    }

    @Test
    fun `given retry result when receiving then 500 so Mercado Pago resends`() {
        every { validator.isValid(any(), any(), any()) } returns true
        every { confirmPayment.execute("ORD1") } returns WebhookResult.RETRY

        val response = controller.receive(body("ORD1"), "sig", "req", null)

        assertEquals(500, response.statusCode.value())
        assertEquals(1.0, counter("500"))
    }

    @Test
    fun `given invalid signature when receiving then 401 and never confirms`() {
        every { validator.isValid(any(), any(), any()) } returns false

        val response = controller.receive(body("ORD1"), "bad", "req", null)

        assertEquals(401, response.statusCode.value())
        assertEquals(1.0, counter("401"))
        verify(exactly = 0) { confirmPayment.execute(any()) }
    }

    @Test
    fun `given no body and no query id when receiving then 200 without confirming`() {
        every { validator.isValid(any(), any(), null) } returns true

        val response = controller.receive(null, "sig", "req", null)

        assertEquals(200, response.statusCode.value())
        verify(exactly = 0) { confirmPayment.execute(any()) }
    }

    @Test
    fun `given invalid json body when receiving then falls back to the query id`() {
        every { validator.isValid("sig", "req", "ORD2") } returns true
        every { confirmPayment.execute("ORD2") } returns WebhookResult.CONFIRMED

        val response = controller.receive("not json", "sig", "req", "ORD2")

        assertEquals(200, response.statusCode.value())
        verify { confirmPayment.execute("ORD2") }
    }

    @Test
    fun `given body without data id and blank query id when receiving then ignores`() {
        every { validator.isValid(any(), any(), null) } returns true

        val response = controller.receive("""{"data":{}}""", "sig", "req", " ")

        assertEquals(200, response.statusCode.value())
        verify(exactly = 0) { confirmPayment.execute(any()) }
    }

    @Test
    fun `given database failure when receiving then 500`() {
        every { validator.isValid(any(), any(), any()) } returns true
        every { confirmPayment.execute("ORD1") } throws DataRetrievalFailureException("db down")

        val response = controller.receive(body("ORD1"), "sig", "req", null)

        assertEquals(500, response.statusCode.value())
    }

    @Test
    fun `given unexpected failure when receiving then rethrows`() {
        every { validator.isValid(any(), any(), any()) } returns true
        every { confirmPayment.execute("ORD1") } throws IllegalStateException("boom")

        assertFailsWith<IllegalStateException> { controller.receive(body("ORD1"), "sig", "req", null) }
    }
}
