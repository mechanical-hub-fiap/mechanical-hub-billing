package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.application.payment.OutboxMessage

/** Entrada da inbox; [response] é a resposta já gravada no outbox pela primeira execução. */
data class InboxEntry(val response: OutboxMessage?)
