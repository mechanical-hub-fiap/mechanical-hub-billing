package com.fiap.mechanicalhub.billing.infrastructure.persistence

import com.fiap.mechanicalhub.billing.application.command.InboxEntry
import com.fiap.mechanicalhub.billing.application.command.InboxStore
import com.fiap.mechanicalhub.billing.application.payment.OutboxMessage
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JdbcInboxStore(private val jdbc: JdbcClient) : InboxStore {
    private val mapper = JsonMapper.builder().build()

    override fun limitLockWait() {
        jdbc.sql("SET LOCAL lock_timeout = '5s'").update()
    }

    override fun claim(key: String): Boolean =
        jdbc
            .sql("INSERT INTO inbox_events (message_key) VALUES (:key) ON CONFLICT (message_key) DO NOTHING")
            .param("key", key)
            .update() == 1

    override fun find(key: String): InboxEntry? =
        jdbc
            .sql("SELECT response_payload::text AS response FROM inbox_events WHERE message_key = :key")
            .param("key", key)
            .query { rs, _ -> InboxEntry(rs.getString("response")?.let(::fromJson)) }
            .optional()
            .orElse(null)

    override fun saveResponse(key: String, response: OutboxMessage) {
        jdbc
            .sql("UPDATE inbox_events SET response_payload = CAST(:payload AS jsonb) WHERE message_key = :key")
            .param("payload", toJson(response))
            .param("key", key)
            .update()
    }

    override fun deleteProcessedBefore(before: Instant): Int =
        jdbc
            .sql("DELETE FROM inbox_events WHERE processed_at < :before")
            .param("before", before.atOffset(ZoneOffset.UTC))
            .update()

    private fun toJson(message: OutboxMessage): String =
        mapper.writeValueAsString(
            mapOf(
                "type" to message.type,
                "orderId" to message.orderId.toString(),
                "transactionId" to message.transactionId.toString(),
                "payload" to message.payload,
            ),
        )

    @Suppress("UNCHECKED_CAST")
    private fun fromJson(json: String): OutboxMessage {
        val node = mapper.readValue(json, Map::class.java) as Map<String, Any>
        return OutboxMessage(
            type = node.getValue("type") as String,
            orderId = UUID.fromString(node.getValue("orderId") as String),
            transactionId = UUID.fromString(node.getValue("transactionId") as String),
            payload = node.getValue("payload") as Map<String, Any>,
        )
    }
}
