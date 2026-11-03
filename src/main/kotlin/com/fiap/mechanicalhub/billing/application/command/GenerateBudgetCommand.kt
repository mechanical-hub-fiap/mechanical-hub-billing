package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.domain.budget.MaterialRequest
import java.util.UUID

/** Comando `generateBudget`: reparos do diagnóstico e materiais reservados. */
data class GenerateBudgetCommand(
    val transactionId: UUID,
    val orderId: UUID,
    val idempotencyKey: String,
    val repairIds: List<UUID>,
    val materials: List<MaterialRequest>,
)
