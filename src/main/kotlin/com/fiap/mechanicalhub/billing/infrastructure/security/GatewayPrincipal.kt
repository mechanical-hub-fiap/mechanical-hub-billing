package com.fiap.mechanicalhub.billing.infrastructure.security

import java.util.UUID

/** Funcionario autenticado pelo API Gateway (Lambda `authorize`). */
data class GatewayPrincipal(
    val id: UUID,
    val name: String?,
    val role: String,
) {
    override fun toString(): String = id.toString()
}
