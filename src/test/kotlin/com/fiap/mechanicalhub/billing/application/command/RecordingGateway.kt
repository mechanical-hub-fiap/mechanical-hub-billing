package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.application.payment.CreateOrderCommand
import com.fiap.mechanicalhub.billing.application.payment.CreatedOrder
import com.fiap.mechanicalhub.billing.application.payment.PaymentGateway
import com.fiap.mechanicalhub.billing.domain.payment.CancelOutcome
import com.fiap.mechanicalhub.billing.domain.payment.OrderLookup

class RecordingGateway : PaymentGateway {
    val created = mutableListOf<CreateOrderCommand>()

    @Volatile
    var failure: RuntimeException? = null

    override fun createOrder(command: CreateOrderCommand): CreatedOrder {
        failure?.let { throw it }
        created += command
        return CreatedOrder("ORD-${command.idempotencyKey}", "https://checkout/${command.orderId}")
    }

    override fun getOrder(mpOrderId: String): OrderLookup = error("not used")

    override fun cancelOrder(mpOrderId: String, idempotencyKey: String): CancelOutcome = error("not used")
}
