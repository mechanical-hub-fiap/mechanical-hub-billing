package com.fiap.mechanicalhub.billing.application.payment

import com.fiap.mechanicalhub.billing.domain.payment.Payment
import java.util.UUID

/** Pagamento persistido: o estado do domínio mais os identificadores usados nas mensagens. */
data class PaymentRecord(
    val paymentId: UUID,
    val orderId: UUID,
    val transactionId: UUID,
    val mpOrderId: String,
    val payment: Payment,
)
