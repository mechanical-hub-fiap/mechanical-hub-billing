package com.fiap.mechanicalhub.billing.application.payment

import com.fiap.mechanicalhub.billing.domain.payment.CancelOutcome
import com.fiap.mechanicalhub.billing.domain.payment.OrderLookup

class ScriptedGateway : PaymentGateway {
    @Volatile var cancel: CancelOutcome = CancelOutcome.Canceled

    @Volatile var lookup: OrderLookup = OrderLookup.Found("processed", "accredited")

    override fun createOrder(command: CreateOrderCommand): CreatedOrder = error("not used")

    override fun getOrder(mpOrderId: String): OrderLookup = lookup

    override fun cancelOrder(mpOrderId: String, idempotencyKey: String): CancelOutcome = cancel
}
