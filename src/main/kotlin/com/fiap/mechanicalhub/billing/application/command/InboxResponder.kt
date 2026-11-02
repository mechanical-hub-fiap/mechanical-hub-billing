package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.application.payment.EventOutbox
import com.fiap.mechanicalhub.billing.application.payment.OutboxMessage
import com.fiap.mechanicalhub.billing.logging.FlowLog
import org.springframework.stereotype.Component

/** Deduplicação por `idempotencyKey`: duplicata regrava no outbox a resposta da primeira execução. */
@Component
class InboxResponder(
    private val inbox: InboxStore,
    private val outbox: EventOutbox,
) {
    fun limitLockWait() = inbox.limitLockWait()

    /** Se a chave já existe, regrava a resposta guardada (se houver) e devolve `true`. */
    fun replay(key: String): Boolean {
        val entry = inbox.find(key) ?: return false
        entry.response?.let { outbox.publish(it) }
        // Não é erro (failure_handling §1): a etapa termina em END com o desfecho da deduplicação.
        FlowLog.detail("outcome" to "DUPLICATE", "responseReplayed" to (entry.response != null))
        FlowLog.endMessage("Comando duplicado, resposta original regravada no outbox")
        return true
    }

    /** Reivindica a chave; em caso de duplicata, regrava a resposta e devolve `false`. */
    fun claimOrReplay(key: String): Boolean {
        if (inbox.claim(key)) return true
        replay(key)
        return false
    }

    fun respond(key: String, response: OutboxMessage) {
        outbox.publish(response)
        inbox.saveResponse(key, response)
    }
}
