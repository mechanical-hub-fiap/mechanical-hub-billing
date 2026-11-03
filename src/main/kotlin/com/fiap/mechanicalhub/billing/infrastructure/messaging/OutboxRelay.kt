package com.fiap.mechanicalhub.billing.infrastructure.messaging

import com.fiap.mechanicalhub.billing.application.payment.OutboxMessageSender
import com.fiap.mechanicalhub.billing.application.payment.TransactionRunner
import com.fiap.mechanicalhub.billing.logging.FlowFields
import com.fiap.mechanicalhub.billing.logging.FlowLog
import com.fiap.mechanicalhub.billing.logging.LogLevel
import com.fiap.mechanicalhub.billing.logging.Stage
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Entrega os eventos pendentes do outbox, em ordem de `sequence`. Só uma instância trabalha por vez:
 * o lock consultivo é de transação, então fica preso à conexão do lote e é liberado no commit.
 */
@Suppress("TooGenericExceptionCaught")
@Component
@EnableScheduling
@ConditionalOnProperty("billing.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelay(
    private val jdbc: JdbcClient,
    private val transactions: TransactionRunner,
    private val sender: OutboxMessageSender,
) {
    private val log = LoggerFactory.getLogger(OutboxRelay::class.java)

    @Scheduled(fixedDelay = 1000)
    fun scheduled() {
        try {
            relay()
        } catch (e: Exception) {
            FlowLog.write(log, LogLevel.ERROR, FlowFields(null, null, "Falha no ciclo do relay do outbox", error = e))
        }
    }

    /** Roda um lote; devolve quantos eventos foram enviados. */
    fun relay(): Int =
        transactions.run {
            val leader =
                jdbc
                    .sql("SELECT pg_try_advisory_xact_lock(:key)")
                    .param("key", LOCK_KEY)
                    .query(Boolean::class.java)
                    .single()
            if (!leader) return@run 0
            val pending =
                jdbc
                    .sql(
                        """
                        SELECT sequence, event_id, aggregate_id, event_type, payload::text AS payload
                        FROM outbox_events
                        WHERE status = 'PENDING'
                        ORDER BY sequence
                        LIMIT :limit
                        """.trimIndent(),
                    ).param("limit", BATCH_SIZE)
                    .query { rs, _ ->
                        Pending(
                            rs.getLong("sequence"),
                            rs.getObject("event_id", UUID::class.java),
                            rs.getObject("aggregate_id", UUID::class.java),
                            rs.getString("event_type"),
                            rs.getString("payload"),
                        )
                    }.list()
            var sent = 0
            for (event in pending) {
                try {
                    sendWithMdc(event)
                } catch (e: Exception) {
                    logFailure(event, e)
                    jdbc
                        .sql(MARK_FAILED_SQL)
                        .param("error", e.message ?: e.javaClass.name)
                        .param("seq", event.sequence)
                        .update()
                    break
                }
                jdbc
                    .sql("UPDATE outbox_events SET status = 'SENT' WHERE sequence = :sequence")
                    .param("sequence", event.sequence)
                    .update()
                sent++
            }
            sent
        }

    /** O MDC é repopulado a partir da linha do outbox (logging-standard §8) e vale também para o log do envio. */
    private fun sendWithMdc(event: Pending) {
        withMdc(event) {
            sender.send(event.aggregateId, event.eventId, event.payload)
            FlowLog.write(
                log,
                LogLevel.INFO,
                FlowFields(
                    orderId = event.aggregateId,
                    status = statusOf(event.eventType),
                    msg = "Evento ${event.eventType} publicado na fila do orquestrador",
                    context = listOf("eventType" to event.eventType),
                ),
            )
        }
    }

    /** Falha no envio interrompe o lote; o mesmo evento é reenviado no próximo ciclo (failure_handling §4). */
    private fun logFailure(event: Pending, error: Exception) {
        withMdc(event) {
            FlowLog.write(
                log,
                LogLevel.WARN,
                FlowFields(
                    orderId = event.aggregateId,
                    status = statusOf(event.eventType),
                    msg = "Falha ao publicar evento ${event.eventType}, nova tentativa no próximo ciclo",
                    stage = Stage.FAIL,
                    context = listOf("eventType" to event.eventType),
                    error = error,
                ),
            )
        }
    }

    private fun withMdc(event: Pending, block: () -> Unit) {
        MDC.putCloseable("eventId", event.eventId.toString()).use {
            MDC.putCloseable(FlowLog.ORDER_ID_KEY, event.aggregateId.toString()).use { block() }
        }
    }

    /** Etapa da saga a que o evento pertence, para o filtro por `status` mostrar a publicação. */
    private fun statusOf(eventType: String): String = EVENT_STATUS[eventType] ?: FlowLog.statusOf(eventType)

    private data class Pending(
        val sequence: Long,
        val eventId: UUID,
        val aggregateId: UUID,
        val eventType: String,
        val payload: String,
    )

    private companion object {
        const val LOCK_KEY = 7_000_001L
        const val BATCH_SIZE = 100
        const val MARK_FAILED_SQL =
            "UPDATE outbox_events SET attempts = attempts + 1, last_error = :error WHERE sequence = :seq"
        val EVENT_STATUS =
            mapOf(
                "budgetGenerated" to "GENERATE_BUDGET",
                "paymentLinkGenerated" to "GENERATE_PAYMENT_LINK",
                "paymentConfirmed" to "CONFIRM_PAYMENT",
                "paymentFailed" to "EXPIRE_PAYMENT",
            )
    }
}
