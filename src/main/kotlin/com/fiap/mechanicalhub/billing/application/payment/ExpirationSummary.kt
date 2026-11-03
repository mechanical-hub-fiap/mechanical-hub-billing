package com.fiap.mechanicalhub.billing.application.payment

/** Resumo de um ciclo do job de vencimento. */
data class ExpirationSummary(val confirmed: Int, val failed: Int, val waiting: Int, val alerts: Int)
