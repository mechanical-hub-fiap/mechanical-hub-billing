package com.fiap.mechanicalhub.billing.infrastructure.messaging

import com.fiap.mechanicalhub.billing.application.command.GenerateBudgetCommand
import com.fiap.mechanicalhub.billing.application.command.GenerateBudgetUseCase
import com.fiap.mechanicalhub.billing.application.command.GeneratePaymentLinkCommand
import com.fiap.mechanicalhub.billing.application.command.GeneratePaymentLinkUseCase
import com.fiap.mechanicalhub.billing.application.command.PermanentCommandException
import com.fiap.mechanicalhub.billing.application.payment.PermanentGatewayException
import com.fiap.mechanicalhub.billing.domain.BusinessRuleException
import com.fiap.mechanicalhub.billing.domain.budget.MaterialRequest
import com.fiap.mechanicalhub.billing.logging.FlowLog
import com.fiap.mechanicalhub.billing.logging.FlowStep
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/**
 * Interpreta o envelope JSON do comando e chama o caso de uso. Erros que não adiantam repetir
 * viram [PermanentCommandException]; qualquer outra exceção é tratada como transitória.
 * Cada comando é uma etapa do log (logging-standard §4): `START` antes do caso de uso, `END` depois do COMMIT
 * e `FAIL` com WARN (vai ser reprocessado) ou ERROR (vai para a DLQ).
 */
@Component
class CommandProcessor(
    private val generateBudget: GenerateBudgetUseCase,
    private val generatePaymentLink: GeneratePaymentLinkUseCase,
) {
    private val mapper = JsonMapper.builder().build()

    @Suppress("TooGenericExceptionCaught")
    fun process(body: String, attempt: Int = 1) {
        val root = runCatching { mapper.readTree(body) }.getOrNull()
        val type = root?.path("type")?.asString("").orEmpty()
        MDC_FIELDS.forEach { field ->
            val value = root?.path(field)?.asString("").orEmpty()
            if (value.isNotBlank()) MDC.put(field, value)
        }
        val orderId = root?.path("orderId")?.asString("")?.ifBlank { null }
        val step = FlowLog.begin(log, orderId, statusFor(type), attempt)
        try {
            step.start(START_MESSAGES[type] ?: "Iniciando comando")
            dispatch(body)
            step.end(END_MESSAGES[type] ?: "Comando processado")
        } catch (e: Exception) {
            throw translate(e).also { logFailure(step, e, it) }
        } finally {
            MDC_FIELDS.forEach { MDC.remove(it) }
        }
    }

    private fun logFailure(step: FlowStep, cause: Exception, translated: Exception) {
        if (translated is PermanentCommandException) {
            step.errorFail("Falha permanente no comando, será enviado para a DLQ", cause)
        } else {
            val retryIn = SqsCommandListener.backoffSeconds(step.attempt ?: 1)
            step.warnFail("Falha transitória no comando, será reprocessado", cause, "retryInSeconds" to retryIn)
        }
    }

    fun statusOf(body: String): String? {
        val type = runCatching { mapper.readTree(body).path("type").asString("") }.getOrDefault("")
        return statusFor(type)
    }

    private fun statusFor(type: String): String? = type.ifBlank { null }?.let(FlowLog::statusOf)

    private fun dispatch(body: String) {
        val root = mapper.readTree(body)
        when (val type = text(root, "type")) {
            "generateBudget" -> generateBudget.execute(budgetCommand(root))
            "generatePaymentLink" -> generatePaymentLink.execute(paymentLinkCommand(root))
            else -> throw PermanentCommandException("Comando desconhecido: $type")
        }
    }

    private fun translate(e: Exception): Exception =
        if (isPermanent(e) && e !is PermanentCommandException) {
            PermanentCommandException(e.message ?: e.javaClass.name, e)
        } else {
            e
        }

    private fun isPermanent(e: Exception) =
        e is PermanentCommandException ||
            e is BusinessRuleException ||
            e is PermanentGatewayException ||
            e is IllegalArgumentException ||
            e is ArithmeticException ||
            e is JacksonException

    private fun budgetCommand(root: JsonNode): GenerateBudgetCommand {
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

    private fun paymentLinkCommand(root: JsonNode): GeneratePaymentLinkCommand {
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

    private fun text(node: JsonNode, field: String): String =
        node.path(field).asString("").also { require(it.isNotBlank()) { "Campo obrigatório ausente: $field" } }

    private companion object {
        val MDC_FIELDS = listOf("orderId", "transactionId", "eventId")
        val log = LoggerFactory.getLogger(CommandProcessor::class.java)
        val START_MESSAGES =
            mapOf(
                "generateBudget" to "Iniciando geração de orçamento",
                "generatePaymentLink" to "Iniciando criação de link de pagamento",
            )
        val END_MESSAGES =
            mapOf(
                "generateBudget" to "Orçamento da ordem gerado",
                "generatePaymentLink" to "Link de pagamento gerado",
            )
    }
}
