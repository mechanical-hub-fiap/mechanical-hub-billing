package com.fiap.mechanicalhub.billing.application.payment

enum class WebhookResult {
    /** Pagamento aprovado nesta chamada. */
    CONFIRMED,

    /** Notificação sem efeito (repetida, não paga ou desconhecida). */
    IGNORED,

    /** Falha transitória: o Mercado Pago deve reenviar. */
    RETRY,
}
