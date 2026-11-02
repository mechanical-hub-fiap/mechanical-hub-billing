package com.fiap.mechanicalhub.billing.domain.budget

import java.math.BigDecimal
import java.util.UUID

data class BudgetItem(
    val type: BudgetItemType,
    val itemRef: UUID,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal,
)
