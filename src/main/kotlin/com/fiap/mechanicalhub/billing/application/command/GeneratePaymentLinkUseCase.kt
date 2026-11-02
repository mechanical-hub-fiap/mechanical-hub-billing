package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.application.payment.CreateOrderCommand
import com.fiap.mechanicalhub.billing.application.payment.OutboxMessage
import com.fiap.mechanicalhub.billing.application.payment.PaymentGateway
import com.fiap.mechanicalhub.billing.application.payment.TransactionRunner
import com.fiap.mechanicalhub.billing.domain.BusinessRuleException
import com.fiap.mechanicalhub.billing.domain.payment.PaymentDeadlines
import com.fiap.mechanicalhub.billing.logging.FlowLog
import com.fiap.mechanicalhub.billing.logging.LogLevel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * TD_finishedOS 14. A chamada ao Mercado Pago fica fora de qualquer transação: antes dela uma
 * transação curta trata duplicata e reaproveita o pagamento PENDING; depois dela, outra grava tudo.
 */
@Service
class GeneratePaymentLinkUseCase(
    private val gateway: PaymentGateway,
    private val links: PaymentLinkStore,
    private val budgets: BudgetStore,
    private val responder: InboxResponder,
    private val transactions: TransactionRunner,
    private val deadlineFactory: PaymentDeadlineFactory,
) {
    fun execute(command: GeneratePaymentLinkCommand) {
        val handled = transactions.run { reuseExisting(command) }
        if (handled) return

        val deadlines = deadlineFactory.create()
        FlowLog.note(log, LogLevel.INFO, "Criando order no Mercado Pago", "budgetId" to command.budgetId)
        val created =
            gateway.createOrder(
                CreateOrderCommand(
                    orderId = command.orderId.toString(),
                    amount = command.orderCost,
                    expiresIn = deadlines.expiresIn,
                    idempotencyKey = command.idempotencyKey,
                ),
            )
        transactions.run {
            responder.limitLockWait()
            if (!responder.claimOrReplay(command.idempotencyKey)) return@run
            val paymentId = UUID.randomUUID()
            links.insert(
                NewPayment(
                    paymentId = paymentId,
                    orderId = command.orderId,
                    transactionId = command.transactionId,
                    budgetId = command.budgetId,
                    mpOrderId = created.mpOrderId,
                    checkoutUrl = created.checkoutUrl,
                    amount = command.orderCost,
                    dueAt = deadlines.dueAt,
                    expiresAt = deadlines.expiresAt,
                ),
            )
            respond(command, created.checkoutUrl, deadlines.dueAt.toString())
            FlowLog.detail(
                "budgetId" to command.budgetId,
                "orderCost" to command.orderCost,
                "paymentId" to paymentId,
                "mpOrderId" to created.mpOrderId,
                "paymentStatus" to "PENDING",
                "dueAt" to deadlines.dueAt,
            )
            FlowLog.endMessage("Order criada no Mercado Pago, evento paymentLinkGenerated gravado no outbox")
        }
    }

    /** Devolve `true` quando a mensagem já foi resolvida sem chamar o Mercado Pago. */
    private fun reuseExisting(command: GeneratePaymentLinkCommand): Boolean {
        responder.limitLockWait()
        if (responder.replay(command.idempotencyKey)) return true
        if (!budgets.exists(command.budgetId)) {
            throw BusinessRuleException("Orçamento não encontrado: ${command.budgetId}")
        }
        val pending = links.findPendingByTransaction(command.transactionId) ?: return false
        if (responder.claimOrReplay(command.idempotencyKey)) {
            respond(command, pending.checkoutUrl, pending.dueAt.toString())
            FlowLog.detail("outcome" to "REUSED_PENDING_PAYMENT", "mpOrderId" to pending.mpOrderId)
            FlowLog.endMessage("Pagamento PENDING reaproveitado, evento paymentLinkGenerated gravado no outbox")
        }
        return true
    }

    private fun respond(command: GeneratePaymentLinkCommand, paymentLink: String, expiresAt: String) =
        responder.respond(
            command.idempotencyKey,
            OutboxMessage(
                type = "paymentLinkGenerated",
                orderId = command.orderId,
                transactionId = command.transactionId,
                payload =
                    mapOf(
                        "transactionId" to command.transactionId.toString(),
                        "orderId" to command.orderId.toString(),
                        "paymentLink" to paymentLink,
                        "expiresAt" to expiresAt,
                    ),
            ),
        )

    private companion object {
        val log = LoggerFactory.getLogger(GeneratePaymentLinkUseCase::class.java)
    }
}

/** Calcula os prazos do pagamento; o prazo de vencimento é configurável (padrão 24h, reduzido só em demonstração). */
@Component
class PaymentDeadlineFactory(
    private val clock: Clock,
    @Value("\${billing.payment.due-delay:PT24H}") private val dueDelay: Duration = PaymentDeadlines.DUE_DELAY,
) {
    fun create(): PaymentDeadlines = PaymentDeadlines.from(clock.instant(), dueDelay)
}
