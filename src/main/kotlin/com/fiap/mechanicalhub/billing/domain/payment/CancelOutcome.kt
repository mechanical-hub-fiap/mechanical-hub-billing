package com.fiap.mechanicalhub.billing.domain.payment

/** Resposta do Mercado Pago ao cancelamento da order (F2). */
sealed interface CancelOutcome {
    data object Canceled : CancelOutcome

    data object Conflict : CancelOutcome

    data object Transient : CancelOutcome

    data object Permanent : CancelOutcome
}
