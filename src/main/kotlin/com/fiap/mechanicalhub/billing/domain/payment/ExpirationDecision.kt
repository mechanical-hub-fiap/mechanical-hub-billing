package com.fiap.mechanicalhub.billing.domain.payment

sealed interface ExpirationDecision {
    /** Finaliza como falha (F3). */
    data class Fail(val reason: PaymentFailureReason, val mpStatus: String) : ExpirationDecision

    /** Finaliza como sucesso (F3'). */
    data class Confirm(val mpStatus: String) : ExpirationDecision

    /** Nada a fazer; próximo ciclo. */
    data object Wait : ExpirationDecision

    /** Erro permanente: log de erro e alerta. */
    data object Alert : ExpirationDecision
}
