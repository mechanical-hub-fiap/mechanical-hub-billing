package com.fiap.mechanicalhub.billing.application.command

/** Erro que não adianta repetir: a mensagem vai direto à DLQ. */
class PermanentCommandException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
