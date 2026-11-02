package com.fiap.mechanicalhub.billing.domain.payment

/** Resposta da consulta da order (caso B). [Transient] cobre timeout, 5xx e 429. */
sealed interface OrderLookup {
    data class Found(val status: String, val statusDetail: String?) : OrderLookup

    data object Transient : OrderLookup

    data object Permanent : OrderLookup
}
