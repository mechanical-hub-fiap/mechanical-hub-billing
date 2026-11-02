package com.fiap.mechanicalhub.billing.application.command

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class NewPayment(
    val paymentId: UUID,
    val orderId: UUID,
    val transactionId: UUID,
    val budgetId: UUID,
    val mpOrderId: String,
    val checkoutUrl: String,
    val amount: BigDecimal,
    val dueAt: Instant,
    val expiresAt: Instant,
)
