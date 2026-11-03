package com.fiap.mechanicalhub.billing.application.command

import com.fiap.mechanicalhub.billing.domain.budget.Budget
import java.util.UUID

interface BudgetStore {
    fun save(orderId: UUID, transactionId: UUID, budget: Budget): UUID

    fun exists(budgetId: UUID): Boolean
}
