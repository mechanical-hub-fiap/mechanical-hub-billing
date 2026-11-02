package com.fiap.mechanicalhub.billing.infrastructure.persistence

import com.fiap.mechanicalhub.billing.application.command.BudgetStore
import com.fiap.mechanicalhub.billing.domain.budget.Budget
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JdbcBudgetStore(private val jdbc: JdbcClient) : BudgetStore {
    override fun save(orderId: UUID, transactionId: UUID, budget: Budget): UUID {
        val budgetId = UUID.randomUUID()
        jdbc
            .sql(
                """
                INSERT INTO budgets (budget_id, order_id, transaction_id, labor_total, material_total, total)
                VALUES (:budgetId, :orderId, :transactionId, :laborTotal, :materialTotal, :total)
                """.trimIndent(),
            ).param("budgetId", budgetId)
            .param("orderId", orderId)
            .param("transactionId", transactionId)
            .param("laborTotal", budget.laborTotal)
            .param("materialTotal", budget.materialTotal)
            .param("total", budget.orderCost)
            .update()
        budget.items.forEach { item ->
            jdbc
                .sql(
                    """
                    INSERT INTO budget_items (id, budget_id, item_type, item_ref, quantity, unit_price, total_price)
                    VALUES (:id, :budgetId, :type, :itemRef, :quantity, :unitPrice, :totalPrice)
                    """.trimIndent(),
                ).param("id", UUID.randomUUID())
                .param("budgetId", budgetId)
                .param("type", item.type.name)
                .param("itemRef", item.itemRef)
                .param("quantity", item.quantity.intValueExact())
                .param("unitPrice", item.unitPrice)
                .param("totalPrice", item.totalPrice)
                .update()
        }
        return budgetId
    }

    override fun exists(budgetId: UUID): Boolean =
        jdbc
            .sql("SELECT EXISTS (SELECT 1 FROM budgets WHERE budget_id = :budgetId)")
            .param("budgetId", budgetId)
            .query(Boolean::class.java)
            .single()
}
