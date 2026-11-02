package com.fiap.mechanicalhub.billing.application.payment

/** Grava o evento no outbox na transação corrente. */
interface EventOutbox {
    fun publish(message: OutboxMessage)
}
