package com.fiap.mechanicalhub.billing.application.command

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class InboxCleanupTest {
    private val now = Instant.parse("2026-02-01T03:00:00Z")
    private val inbox = mockk<InboxStore>()
    private val cleanup = InboxCleanup(inbox, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `when cleaning up then removes entries older than 30 days`() {
        every { inbox.deleteProcessedBefore(now.minus(Duration.ofDays(30))) } returns 4

        assertEquals(4, cleanup.cleanup())
    }

    @Test
    fun `when the schedule fires then runs the cleanup`() {
        every { inbox.deleteProcessedBefore(any()) } returns 0

        cleanup.scheduled()

        verify { inbox.deleteProcessedBefore(now.minus(Duration.ofDays(30))) }
    }
}
