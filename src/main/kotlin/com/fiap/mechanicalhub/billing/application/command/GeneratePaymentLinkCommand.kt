package com.fiap.mechanicalhub.billing.application.command

import java.math.BigDecimal
import java.util.UUID

/** Comando `generatePaymentLink`: orçamento aprovado a cobrar. */
data class GeneratePaymentLinkCommand(
    val transactionId: UUID,
    val orderId: UUID,
    val idempotencyKey: String,
    val budgetId: UUID,
    val orderCost: BigDecimal,
)
