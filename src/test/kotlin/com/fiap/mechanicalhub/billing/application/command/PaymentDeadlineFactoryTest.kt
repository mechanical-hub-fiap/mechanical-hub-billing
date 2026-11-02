package com.fiap.mechanicalhub.billing.application.command

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class PaymentDeadlineFactoryTest {
    private val now = Instant.parse("2026-01-01T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `given default delay when creating then due in 24h and expires 5 minutes later`() {
        val deadlines = PaymentDeadlineFactory(clock).create()

        assertEquals(now.plus(Duration.ofHours(24)), deadlines.dueAt)
        assertEquals(now.plus(Duration.ofHours(24)).plus(Duration.ofMinutes(5)), deadlines.expiresAt)
    }

    @Test
    fun `given custom delay when creating then applies it`() {
        val deadlines = PaymentDeadlineFactory(clock, Duration.ofMinutes(10)).create()

        assertEquals(now.plus(Duration.ofMinutes(10)), deadlines.dueAt)
        assertEquals(Duration.ofMinutes(15), deadlines.expiresIn)
    }
}
