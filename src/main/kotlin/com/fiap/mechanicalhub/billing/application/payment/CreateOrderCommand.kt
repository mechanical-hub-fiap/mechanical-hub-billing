package com.fiap.mechanicalhub.billing.application.payment

import java.math.BigDecimal
import java.time.Duration

data class CreateOrderCommand(
    val orderId: String,
    val amount: BigDecimal,
    /** Duração até a order expirar no MP (ISO 8601, ex.: PT24H5M); a API não aceita timestamp. */
    val expiresIn: Duration,
    val idempotencyKey: String,
)
