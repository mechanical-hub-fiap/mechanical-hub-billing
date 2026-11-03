package com.fiap.mechanicalhub.billing.infrastructure.messaging

import com.fiap.mechanicalhub.billing.application.command.GenerateBudgetCommand
import com.fiap.mechanicalhub.billing.application.command.GeneratePaymentLinkCommand
import com.fiap.mechanicalhub.billing.domain.budget.MaterialRequest
import tools.jackson.databind.JsonNode
import java.util.UUID

/** Converte o envelope JSON de cada tipo de comando no comando de aplicação correspondente. */
class CommandEnvelopeParser {
    fun budgetCommand(root: JsonNode): GenerateBudgetCommand {
        val transactionId = uuid(root, "transactionId")
        return GenerateBudgetCommand(
            transactionId = transactionId,
            orderId = uuid(root, "orderId"),
            idempotencyKey = idempotencyKey(root, transactionId, "GENERATE_BUDGET"),
            repairIds = root.path("repairs").values().map { repairId(it) },
            materials =
                root.path("reservedMaterials").values().map {
                    MaterialRequest(UUID.fromString(text(it, "materialId")), it.path("quantity").decimalValue())
                },
        )
    }

    fun paymentLinkCommand(root: JsonNode): GeneratePaymentLinkCommand {
        val transactionId = uuid(root, "transactionId")
        return GeneratePaymentLinkCommand(
            transactionId = transactionId,
            orderId = uuid(root, "orderId"),
            idempotencyKey = idempotencyKey(root, transactionId, "GENERATE_PAYMENT_LINK"),
            budgetId = uuid(root, "budgetId"),
            orderCost = root.path("orderCost").decimalValue(),
        )
    }

    /** O reparo chega como UUID puro ou como objeto `{repairId}`. */
    private fun repairId(node: JsonNode): UUID =
        UUID.fromString(if (node.isObject) text(node, "repairId") else node.asString())

    private fun idempotencyKey(root: JsonNode, transactionId: UUID, step: String): String =
        root.path("idempotencyKey").asString("").ifBlank { "$transactionId:$step" }

    private fun uuid(node: JsonNode, field: String): UUID = UUID.fromString(text(node, field))

    fun text(node: JsonNode, field: String): String =
        node.path(field).asString("").also { require(it.isNotBlank()) { "Campo obrigatório ausente: $field" } }
}
