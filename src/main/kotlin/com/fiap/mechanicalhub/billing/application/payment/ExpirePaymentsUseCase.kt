package com.fiap.mechanicalhub.billing.application.payment

import com.fiap.mechanicalhub.billing.domain.payment.CancelOutcome
import com.fiap.mechanicalhub.billing.domain.payment.ExpirationDecision
import com.fiap.mechanicalhub.billing.domain.payment.ExpirationRules
import com.fiap.mechanicalhub.billing.domain.payment.OrderLookup
import com.fiap.mechanicalhub.billing.domain.payment.PaymentStatusEnum
import com.fiap.mechanicalhub.billing.domain.payment.PaymentTransition
import com.fiap.mechanicalhub.billing.logging.FlowLog
import com.fiap.mechanicalhub.billing.logging.FlowStep
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * Job de vencimento (td_payment_failed F2/F3): cancela a order vencida no MP e finaliza o pagamento.
 * Cada pagamento vencido é uma etapa `EXPIRE_PAYMENT` da ordem: `START` antes de chamar o Mercado Pago e
 * `END` ou `FAIL` com a decisão tomada (falha, pago no último instante, aguardar, alerta).
 */
@Suppress("TooGenericExceptionCaught")
@Service
class ExpirePaymentsUseCase(
    private val gateway: PaymentGateway,
    private val store: PaymentStore,
    private val outbox: EventOutbox,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(): ExpirationSummary {
        val results =
            store.findDue(clock.instant(), BATCH_SIZE).map { due ->
                MDC.put(FlowLog.ORDER_ID_KEY, due.orderId.toString())
                try {
                    expire(due)
                } finally {
                    MDC.remove(FlowLog.ORDER_ID_KEY)
                }
            }
        return ExpirationSummary(
            confirmed = results.count { it == ExpireResult.CONFIRMED },
            failed = results.count { it == ExpireResult.FAILED },
            waiting = results.count { it == ExpireResult.WAITING },
            alerts = results.count { it == ExpireResult.ALERT },
        )
    }

    private fun expire(due: PaymentRecord): ExpireResult {
        val step = FlowLog.begin(log, due.orderId, STATUS, clock = clock)
        step.start(
            "Prazo de pagamento vencido, cancelando a order no Mercado Pago",
            "paymentId" to due.paymentId,
            "mpOrderId" to due.mpOrderId,
        )
        val decided =
            try {
                decide(due)
            } catch (e: Exception) {
                step.errorFail("Falha ao processar o vencimento, nova tentativa no próximo ciclo", e)
                return ExpireResult.WAITING
            }
        return try {
            conclude(step, due, decided)
        } catch (e: Exception) {
            step.errorFail("Falha ao finalizar o vencimento do pagamento", e)
            throw e
        }
    }

    private fun conclude(step: FlowStep, due: PaymentRecord, decided: Decided): ExpireResult =
        when (val decision = decided.decision) {
            is ExpirationDecision.Fail -> {
                finish(step, due, decision, ExpireResult.FAILED)
            }

            is ExpirationDecision.Confirm -> {
                finish(step, due, decision, ExpireResult.CONFIRMED)
            }

            ExpirationDecision.Wait -> {
                if (decided.transientFailure) {
                    step.warnFail(
                        "Falha transitória no Mercado Pago, nova tentativa no próximo ciclo",
                        null,
                        "outcome" to "WAIT",
                    )
                } else {
                    step.end(
                        "Order sem decisão final no Mercado Pago, nova verificação no próximo ciclo",
                        "outcome" to "WAIT",
                    )
                }
                ExpireResult.WAITING
            }

            ExpirationDecision.Alert -> {
                step.errorFail(
                    "ALERTA: erro permanente ao cancelar ou consultar a order no Mercado Pago",
                    null,
                    "alert" to "MP_PERMANENT_ERROR",
                )
                ExpireResult.ALERT
            }
        }

    /** Grava a decisão; `false` de [finalize] significa que o webhook ou outro ciclo já decidiu. */
    private fun finish(
        step: FlowStep,
        due: PaymentRecord,
        decision: ExpirationDecision,
        result: ExpireResult,
    ): ExpireResult {
        if (!finalize(due, decision)) {
            step.end("Pagamento já finalizado por outro fluxo, nada a fazer", "outcome" to "ALREADY_FINALIZED")
            return ExpireResult.NONE
        }
        if (decision is ExpirationDecision.Fail) {
            step.end(
                "Pagamento não confirmado no prazo, evento paymentFailed gravado no outbox",
                "reason" to decision.reason,
                "paymentStatus" to PaymentStatusEnum.CANCELED,
            )
        } else {
            step.end(
                "Pagamento encontrado como pago no Mercado Pago, evento paymentConfirmed gravado no outbox",
                "paymentStatus" to PaymentStatusEnum.APPROVED,
            )
        }
        return result
    }

    /** Chamadas ao Mercado Pago ficam fora da transação. */
    private fun decide(due: PaymentRecord): Decided {
        val cancel = gateway.cancelOrder(due.mpOrderId, "${due.transactionId}:CANCEL_MP_ORDER")
        val lookup = if (cancel == CancelOutcome.Conflict) gateway.getOrder(due.mpOrderId) else null
        FlowLog.detail(
            "mpCancel" to cancel.javaClass.simpleName,
            "mpLookup" to lookup?.javaClass?.simpleName,
            "mpStatus" to (lookup as? OrderLookup.Found)?.status,
        )
        val transient = cancel == CancelOutcome.Transient || lookup == OrderLookup.Transient
        return Decided(ExpirationRules.decide(cancel, lookup), transient)
    }

    private fun finalize(due: PaymentRecord, decision: ExpirationDecision): Boolean =
        transactions.run {
            val record = store.findByMpOrderIdForUpdate(due.mpOrderId) ?: return@run false
            val now = clock.instant()
            val transition =
                when (decision) {
                    is ExpirationDecision.Confirm -> record.payment.approve(decision.mpStatus, now)
                    is ExpirationDecision.Fail -> record.payment.cancel(decision.mpStatus, now)
                    else -> PaymentTransition.Skipped
                }
            if (transition !is PaymentTransition.Applied) return@run false
            val updated = record.copy(payment = transition.payment)
            if (!store.updateIfPending(updated)) return@run false
            outbox.publish(message(updated, decision))
            true
        }

    private fun message(record: PaymentRecord, decision: ExpirationDecision): OutboxMessage {
        val base =
            mapOf(
                "transactionId" to record.transactionId.toString(),
                "orderId" to record.orderId.toString(),
            )
        return if (decision is ExpirationDecision.Fail) {
            OutboxMessage(
                PAYMENT_FAILED,
                record.orderId,
                record.transactionId,
                base + ("reason" to decision.reason.name),
            )
        } else {
            OutboxMessage(
                PAYMENT_CONFIRMED,
                record.orderId,
                record.transactionId,
                base + ("paymentId" to record.paymentId.toString()),
            )
        }
    }

    private data class Decided(val decision: ExpirationDecision, val transientFailure: Boolean)

    private enum class ExpireResult { CONFIRMED, FAILED, WAITING, ALERT, NONE }

    private companion object {
        const val STATUS = "EXPIRE_PAYMENT"
        const val BATCH_SIZE = 100
        const val PAYMENT_CONFIRMED = "paymentConfirmed"
        const val PAYMENT_FAILED = "paymentFailed"
        val log = LoggerFactory.getLogger(ExpirePaymentsUseCase::class.java)
    }
}
