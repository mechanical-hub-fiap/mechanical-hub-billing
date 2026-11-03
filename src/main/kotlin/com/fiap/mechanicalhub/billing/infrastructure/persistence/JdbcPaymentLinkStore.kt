package com.fiap.mechanicalhub.billing.infrastructure.persistence

import com.fiap.mechanicalhub.billing.application.command.NewPayment
import com.fiap.mechanicalhub.billing.application.command.PaymentLink
import com.fiap.mechanicalhub.billing.application.command.PaymentLinkStore
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JdbcPaymentLinkStore(private val jdbc: JdbcClient) : PaymentLinkStore {
    override fun findPendingByTransaction(transactionId: UUID): PaymentLink? =
        jdbc
            .sql(
                """
                SELECT mp_order_id, checkout_url, due_at FROM payments
                WHERE transaction_id = :transactionId AND status = 'PENDING'
                ORDER BY created_at LIMIT 1
                """.trimIndent(),
            ).param("transactionId", transactionId)
            .query { rs, _ ->
                PaymentLink(
                    mpOrderId = rs.getString("mp_order_id"),
                    checkoutUrl = rs.getString("checkout_url"),
                    dueAt = rs.getObject("due_at", OffsetDateTime::class.java).toInstant(),
                )
            }.optional()
            .orElse(null)

    override fun insert(payment: NewPayment) {
        jdbc
            .sql(
                """
                INSERT INTO payments (payment_id, order_id, transaction_id, budget_id, mp_order_id, checkout_url,
                                      amount, status, due_at, expires_at)
                VALUES (:paymentId, :orderId, :transactionId, :budgetId, :mpOrderId, :checkoutUrl,
                        :amount, 'PENDING', :dueAt, :expiresAt)
                """.trimIndent(),
            ).param("paymentId", payment.paymentId)
            .param("orderId", payment.orderId)
            .param("transactionId", payment.transactionId)
            .param("budgetId", payment.budgetId)
            .param("mpOrderId", payment.mpOrderId)
            .param("checkoutUrl", payment.checkoutUrl)
            .param("amount", payment.amount)
            .param("dueAt", payment.dueAt.atOffset(ZoneOffset.UTC))
            .param("expiresAt", payment.expiresAt.atOffset(ZoneOffset.UTC))
            .update()
    }
}
