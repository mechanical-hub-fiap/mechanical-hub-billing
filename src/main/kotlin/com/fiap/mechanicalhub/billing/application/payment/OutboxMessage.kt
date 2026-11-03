package com.fiap.mechanicalhub.billing.application.payment

import java.util.UUID

/** Evento a publicar na saga; entregue ao orquestrador pelo relay do outbox. */
data class OutboxMessage(
    val type: String,
    val orderId: UUID,
    val transactionId: UUID,
    val payload: Map<String, Any>,
)
