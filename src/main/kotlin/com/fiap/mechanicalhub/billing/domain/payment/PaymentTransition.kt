package com.fiap.mechanicalhub.billing.domain.payment

/** Resultado de uma transição condicional; [Skipped] significa que o pagamento já não estava PENDING. */
sealed interface PaymentTransition {
    data class Applied(val payment: Payment) : PaymentTransition

    data object Skipped : PaymentTransition
}
