package com.fiap.mechanicalhub.billing.application.command

import java.time.Instant

/** Pagamento já criado no Mercado Pago, necessário para responder `paymentLinkGenerated`. */
data class PaymentLink(val mpOrderId: String, val checkoutUrl: String, val dueAt: Instant)
