package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.application.payment.OutboxMessage
import java.time.Instant

interface InboxStore {
    /** Limita a espera por locks (lock_timeout de 5 s) na transação corrente. */
    fun limitLockWait()

    /** Registra a chave; devolve `false` se já existia (duplicata). */
    fun claim(key: String): Boolean

    fun find(key: String): InboxEntry?

    fun saveResponse(key: String, response: OutboxMessage)

    /** Apaga entradas processadas antes de [before]; devolve quantas. */
    fun deleteProcessedBefore(before: Instant): Int
}
