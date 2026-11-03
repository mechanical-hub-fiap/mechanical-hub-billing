package com.fiap.mechanicalhub.billing.infrastructure.messaging

import com.fiap.mechanicalhub.billing.application.payment.OutboxMessageSender
import java.util.UUID

class RecordingSender : OutboxMessageSender {
    val sent = mutableListOf<Triple<UUID, UUID, String>>()

    @Volatile
    var failOn: Int? = null

    override fun send(groupId: UUID, deduplicationId: UUID, body: String) {
        if (failOn == sent.size + 1) error("sqs indisponivel")
        sent += Triple(groupId, deduplicationId, body)
    }
}
