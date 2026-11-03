package com.fiap.mechanicalhub.billing.application.payment

/** Falha transitória (timeout, 5xx, 429): a mensagem deve ser tentada de novo. */
class TransientGatewayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
