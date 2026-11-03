package com.fiap.mechanicalhub.billing.infrastructure.mercadopago

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("mercado-pago")
data class MercadoPagoProperties(
    val baseUrl: String,
    val accessToken: String = "",
    val webhookSecret: String = "",
)
