package com.fiap.mechanicalhub.billing.application.payment

interface PaymentStore {
    /** Busca o pagamento travando a linha (`FOR UPDATE`); exige transação ativa. */
    fun findByMpOrderIdForUpdate(mpOrderId: String): PaymentRecord?

    /** Grava a transição apenas se a linha ainda estiver PENDING; devolve se alterou. */
    fun updateIfPending(record: PaymentRecord): Boolean

    /** Pagamentos PENDING vencidos (`due_at <= now`), sem lock de linha. */
    fun findDue(now: java.time.Instant, limit: Int): List<PaymentRecord>
}
