package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.application.payment.EventOutbox
import com.fiap.mechanicalhub.billing.application.payment.OutboxMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InboxResponderTest {
    private val inbox = mockk<InboxStore>(relaxed = true)
    private val outbox = mockk<EventOutbox>(relaxed = true)
    private val responder = InboxResponder(inbox, outbox)
    private val message = OutboxMessage("budgetGenerated", UUID.randomUUID(), UUID.randomUUID(), mapOf("a" to "b"))

    @Test
    fun `when limiting lock wait then delegates to the inbox`() {
        responder.limitLockWait()

        verify { inbox.limitLockWait() }
    }

    @Test
    fun `given unknown key when replaying then returns false without publishing`() {
        every { inbox.find("k") } returns null

        assertFalse(responder.replay("k"))
        verify(exactly = 0) { outbox.publish(any()) }
    }

    @Test
    fun `given known key with stored response when replaying then republishes it`() {
        every { inbox.find("k") } returns InboxEntry(message)

        assertTrue(responder.replay("k"))
        verify { outbox.publish(message) }
    }

    @Test
    fun `given known key without response when replaying then returns true without publishing`() {
        every { inbox.find("k") } returns InboxEntry(null)

        assertTrue(responder.replay("k"))
        verify(exactly = 0) { outbox.publish(any()) }
    }

    @Test
    fun `given new key when claiming then returns true`() {
        every { inbox.claim("k") } returns true

        assertTrue(responder.claimOrReplay("k"))
        verify(exactly = 0) { inbox.find(any()) }
    }

    @Test
    fun `given duplicate key when claiming then replays and returns false`() {
        every { inbox.claim("k") } returns false
        every { inbox.find("k") } returns InboxEntry(message)

        assertFalse(responder.claimOrReplay("k"))
        verify { outbox.publish(message) }
    }

    @Test
    fun `when responding then publishes and stores the response`() {
        responder.respond("k", message)

        verify { outbox.publish(message) }
        verify { inbox.saveResponse("k", message) }
    }

    @Test
    fun `inbox entry exposes its response`() {
        assertEquals(message, InboxEntry(message).response)
    }
}
