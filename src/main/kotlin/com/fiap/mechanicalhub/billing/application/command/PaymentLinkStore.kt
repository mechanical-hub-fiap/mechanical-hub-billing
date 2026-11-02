package com.fiap.mechanicalhub.billing.application.command

import java.util.UUID

interface PaymentLinkStore {
    fun findPendingByTransaction(transactionId: UUID): PaymentLink?

    fun insert(payment: NewPayment)
}
