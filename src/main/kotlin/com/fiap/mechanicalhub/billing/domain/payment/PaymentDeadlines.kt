package com.fiap.mechanicalhub.billing.domain.payment

import java.time.Duration
import java.time.Instant

/** Prazos do pagamento: negócio (24h) e expiração da order no Mercado Pago (24h05). */
data class PaymentDeadlines(val dueAt: Instant, val expiresAt: Instant, val expiresIn: Duration) {
    companion object {
        val DUE_DELAY: Duration = Duration.ofHours(24)
        val EXPIRATION_DELAY: Duration = Duration.ofHours(24).plusMinutes(5)

        val EXPIRATION_GRACE: Duration = Duration.ofMinutes(5)

        fun from(
            createdAt: Instant,
            dueDelay: Duration = DUE_DELAY,
            grace: Duration = EXPIRATION_GRACE,
        ) = PaymentDeadlines(createdAt + dueDelay, createdAt + dueDelay + grace, dueDelay + grace)
    }
}
