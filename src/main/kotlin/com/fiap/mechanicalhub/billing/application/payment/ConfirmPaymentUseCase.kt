package com.fiap.mechanicalhub.billing.application.payment

import com.fiap.mechanicalhub.billing.domain.payment.OrderLookup
import com.fiap.mechanicalhub.billing.domain.payment.PaymentStatusEnum
import com.fiap.mechanicalhub.billing.domain.payment.PaymentTransition
import com.fiap.mechanicalhub.billing.logging.FlowLog
import com.fiap.mechanicalhub.billing.logging.LogLevel
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock

enum class WebhookResult {
    /** Pagamento aprovado nesta chamada. */
    CONFIRMED,

    /** Notificação sem efeito (repetida, não paga ou desconhecida). */
    IGNORED,

    /** Falha transitória: o Mercado Pago deve reenviar. */
    RETRY,
}

/**
 * Confirma o pagamento a partir da notificação do Mercado Pago (TD_finishedOS 17). A etapa `CONFIRM_PAYMENT`
 * é aberta pelo webhook; aqui entram o `orderId` (assim que o pagamento é carregado), o contexto e o desfecho.
 */
@Service
class ConfirmPaymentUseCase(
    private val gateway: PaymentGateway,
    private val store: PaymentStore,
    private val outbox: EventOutbox,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val registry: MeterRegistry = Metrics.globalRegistry,
) {
    /** A consulta ao Mercado Pago acontece fora da transação; só a escrita final trava a linha. */
    fun execute(mpOrderId: String): WebhookResult =
        when (val lookup = gateway.getOrder(mpOrderId)) {
            OrderLookup.Transient -> {
                WebhookResult.RETRY
            }

            OrderLookup.Permanent -> {
                FlowLog.current()?.errorFail(
                    "ALERTA: consulta da order no Mercado Pago falhou de forma permanente, notificação ignorada",
                    null,
                    "alert" to "MP_LOOKUP_PERMANENT",
                )
                WebhookResult.IGNORED
            }

            is OrderLookup.Found -> {
                handle(mpOrderId, lookup)
            }
        }

    private fun handle(mpOrderId: String, order: OrderLookup.Found): WebhookResult =
        if (order.status == PROCESSED && order.statusDetail == ACCREDITED) {
            transactions.run { confirm(mpOrderId, order.status) }
        } else {
            FlowLog.detail("outcome" to "NOT_PAID", "mpStatus" to order.status, "mpStatusDetail" to order.statusDetail)
            FlowLog.endMessage("Order ainda não paga no Mercado Pago, notificação ignorada")
            WebhookResult.IGNORED
        }

    private fun confirm(mpOrderId: String, mpStatus: String): WebhookResult {
        val record = store.findByMpOrderIdForUpdate(mpOrderId)
        if (record == null) {
            FlowLog.note(log, LogLevel.WARN, "Notificação para order sem pagamento correspondente")
            FlowLog.detail("outcome" to "UNKNOWN_PAYMENT")
            FlowLog.endMessage("Notificação sem pagamento correspondente, ignorada")
            return WebhookResult.IGNORED
        }
        FlowLog.assignOrder(record.orderId)
        FlowLog.detail("paymentId" to record.paymentId, "transactionId" to record.transactionId)
        return when (val transition = record.payment.approve(mpStatus, clock.instant())) {
            PaymentTransition.Skipped -> {
                skipped(record)
                WebhookResult.IGNORED
            }

            is PaymentTransition.Applied -> {
                apply(record.copy(payment = transition.payment))
            }
        }
    }

    /** Pagamento já fora de PENDING: notificação repetida, ou inconsistência se o job já o cancelou (§3.1). */
    private fun skipped(record: PaymentRecord) {
        if (record.payment.status == PaymentStatusEnum.CANCELED) {
            registry.counter("billing_alerts_total", "type", "PAID_ON_CANCELED").increment()
            FlowLog.current()?.errorFail(
                "ALERTA: order paga no Mercado Pago mas pagamento já CANCELED",
                null,
                "paymentStatus" to record.payment.status,
                "alert" to "PAID_ON_CANCELED",
            )
        } else {
            FlowLog.detail("outcome" to "DUPLICATE", "paymentStatus" to record.payment.status)
            FlowLog.endMessage("Pagamento já finalizado, notificação repetida ignorada")
        }
    }

    private fun apply(approved: PaymentRecord): WebhookResult {
        if (!store.updateIfPending(approved)) {
            FlowLog.detail("outcome" to "ALREADY_FINALIZED")
            FlowLog.endMessage("Pagamento finalizado por outro fluxo, notificação ignorada")
            return WebhookResult.IGNORED
        }
        FlowLog.detail("paymentStatus" to approved.payment.status)
        FlowLog.endMessage("Pagamento confirmado, evento paymentConfirmed gravado no outbox")
        outbox.publish(
            OutboxMessage(
                type = PAYMENT_CONFIRMED,
                orderId = approved.orderId,
                transactionId = approved.transactionId,
                payload =
                    mapOf(
                        "transactionId" to approved.transactionId.toString(),
                        "orderId" to approved.orderId.toString(),
                        "paymentId" to approved.paymentId.toString(),
                    ),
            ),
        )
        return WebhookResult.CONFIRMED
    }

    private companion object {
        const val PROCESSED = "processed"
        const val ACCREDITED = "accredited"
        const val PAYMENT_CONFIRMED = "paymentConfirmed"
        val log = LoggerFactory.getLogger(ConfirmPaymentUseCase::class.java)
    }
}
