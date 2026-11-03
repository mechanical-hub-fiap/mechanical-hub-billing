package com.fiap.mechanicalhub.billing.infrastructure.web

import com.fiap.mechanicalhub.billing.application.payment.CreateOrderCommand
import com.fiap.mechanicalhub.billing.application.payment.CreatedOrder
import com.fiap.mechanicalhub.billing.application.payment.PaymentGateway
import com.fiap.mechanicalhub.billing.domain.payment.CancelOutcome
import com.fiap.mechanicalhub.billing.domain.payment.OrderLookup

class FakeGateway : PaymentGateway {
    @Volatile
    var lookup: OrderLookup = OrderLookup.Found("processed", "accredited")

    override fun createOrder(command: CreateOrderCommand): CreatedOrder = error("not used")

    override fun getOrder(mpOrderId: String): OrderLookup = lookup

    override fun cancelOrder(mpOrderId: String, idempotencyKey: String): CancelOutcome = error("not used")
}
