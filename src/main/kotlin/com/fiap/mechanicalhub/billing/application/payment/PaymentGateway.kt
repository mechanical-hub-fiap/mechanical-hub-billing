package com.fiap.mechanicalhub.billing.application.payment

import com.fiap.mechanicalhub.billing.domain.payment.CancelOutcome
import com.fiap.mechanicalhub.billing.domain.payment.OrderLookup

/** Porta do provedor de pagamento; o domínio não conhece o Mercado Pago. */
interface PaymentGateway {
    /** Lança [TransientGatewayException] ou [PermanentGatewayException] em caso de falha. */
    fun createOrder(command: CreateOrderCommand): CreatedOrder

    fun getOrder(mpOrderId: String): OrderLookup

    fun cancelOrder(mpOrderId: String, idempotencyKey: String): CancelOutcome
}
