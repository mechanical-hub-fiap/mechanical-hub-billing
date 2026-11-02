package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.domain.payment.PaymentDeadlines
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/** Calcula os prazos do pagamento; o prazo de vencimento é configurável (padrão 24h, reduzido só em demonstração). */
@Component
class PaymentDeadlineFactory(
    private val clock: Clock,
    @Value("\${billing.payment.due-delay:PT24H}") private val dueDelay: Duration = PaymentDeadlines.DUE_DELAY,
) {
    fun create(): PaymentDeadlines = PaymentDeadlines.from(clock.instant(), dueDelay)
}
