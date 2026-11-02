package com.fiap.mechanicalhub.billing.application.payment

/** Falha permanente (400, 401, 403, 404, resposta inválida): não adianta repetir. */
class PermanentGatewayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
