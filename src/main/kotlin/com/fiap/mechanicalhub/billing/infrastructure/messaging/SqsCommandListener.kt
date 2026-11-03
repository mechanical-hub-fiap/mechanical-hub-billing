package com.fiap.mechanicalhub.billing.infrastructure.messaging

import com.fiap.mechanicalhub.billing.application.command.PermanentCommandException
import com.fiap.mechanicalhub.billing.logging.FlowFields
import com.fiap.mechanicalhub.billing.logging.FlowLog
import com.fiap.mechanicalhub.billing.logging.LogLevel
import com.fiap.mechanicalhub.billing.logging.Stage
import io.awspring.cloud.sqs.annotation.SqsListener
import io.awspring.cloud.sqs.listener.SqsHeaders
import io.awspring.cloud.sqs.listener.Visibility
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import kotlin.math.min

/**
 * Consome `mechanical-hub-billing-commands.fifo` com retry bloqueante: a mensagem só é apagada quando o
 * método retorna (depois do COMMIT do caso de uso). Erro transitório relança e ajusta o visibility timeout
 * pela `ApproximateReceiveCount`; erro permanente vai direto à DLQ e a original é apagada.
 * O `START`/`END`/`FAIL` da etapa sai no [CommandProcessor]; aqui sai só o envio para a DLQ.
 */
@Component
@ConditionalOnProperty("billing.consumer.enabled", havingValue = "true", matchIfMissing = true)
class SqsCommandListener(
    private val processor: CommandProcessor,
    private val sqsTemplate: SqsTemplate,
    @Value("\${billing.queues.commands-dlq}") private val deadLetterQueue: String,
) {
    @SqsListener(
        value = ["\${billing.queues.commands}"],
        pollTimeoutSeconds = "20",
        messageVisibilitySeconds = "30",
    )
    @Suppress("TooGenericExceptionCaught")
    fun onMessage(message: Message<String>, visibility: Visibility) {
        val receiveCount = message.headers[SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT]
        val attempt = receiveCount?.toString()?.toIntOrNull() ?: 1
        try {
            processor.process(message.payload, attempt)
        } catch (e: PermanentCommandException) {
            sendToDeadLetterQueue(message)
            logDeadLetter(message, attempt, e)
        } catch (e: Exception) {
            visibility.changeTo(backoffSeconds(attempt))
            throw e
        }
    }

    private fun sendToDeadLetterQueue(message: Message<String>) {
        val group = groupId(message)
        val dedup =
            message.headers[SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER]?.toString()
                ?: message.headers[SqsHeaders.MessageSystemAttributes.MESSAGE_ID]?.toString()
        sqsTemplate.send { to ->
            to.queue(deadLetterQueue).payload(message.payload)
            group?.let { to.messageGroupId(it) }
            dedup?.let { to.messageDeduplicationId(it) }
        }
    }

    /** `MessageGroupId` é o `orderId`: a linha da DLQ também aparece no filtro pela ordem. */
    private fun logDeadLetter(message: Message<String>, attempt: Int, error: PermanentCommandException) {
        val orderId = groupId(message)
        orderId?.let { MDC.put(FlowLog.ORDER_ID_KEY, it) }
        try {
            FlowLog.write(
                log,
                LogLevel.ERROR,
                FlowFields(
                    orderId = orderId,
                    status = processor.statusOf(message.payload),
                    msg = "Comando enviado para a DLQ",
                    stage = Stage.FAIL,
                    attempt = attempt,
                    context = listOf("queue" to deadLetterQueue),
                    error = error,
                ),
            )
        } finally {
            MDC.remove(FlowLog.ORDER_ID_KEY)
        }
    }

    private fun groupId(message: Message<String>): String? =
        message.headers[SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER]?.toString()

    companion object {
        private const val MAX_BACKOFF_SECONDS = 8
        private const val MAX_BACKOFF_EXPONENT = 4
        private val log = LoggerFactory.getLogger(SqsCommandListener::class.java)

        /** 1 s, 2 s, 4 s e 8 s a partir da quarta tentativa. */
        fun backoffSeconds(receiveCount: Int): Int {
            val exponent = (receiveCount - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
            return min(1 shl exponent, MAX_BACKOFF_SECONDS)
        }
    }
}
