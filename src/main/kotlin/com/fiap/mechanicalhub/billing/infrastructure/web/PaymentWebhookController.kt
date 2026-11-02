package com.fiap.mechanicalhub.billing.infrastructure.web

import com.fiap.mechanicalhub.billing.application.payment.ConfirmPaymentUseCase
import com.fiap.mechanicalhub.billing.application.payment.WebhookResult
import com.fiap.mechanicalhub.billing.infrastructure.mercadopago.WebhookSignatureValidator
import com.fiap.mechanicalhub.billing.logging.FlowLog
import com.fiap.mechanicalhub.billing.logging.FlowStep
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper

@RestController
@RequestMapping("/webhooks/payment")
class PaymentWebhookController(
    private val signatureValidator: WebhookSignatureValidator,
    private val confirmPayment: ConfirmPaymentUseCase,
    private val registry: MeterRegistry = Metrics.globalRegistry,
) {
    private val mapper = JsonMapper.builder().build()

    @PostMapping
    fun receive(
        @RequestBody(required = false) body: String?,
        @RequestHeader("x-signature", required = false) signature: String?,
        @RequestHeader("x-request-id", required = false) requestId: String?,
        @RequestParam("data.id", required = false) queryDataId: String?,
    ): ResponseEntity<Void> {
        val response = handle(body, signature, requestId, queryDataId)
        registry.counter("billing_webhook_total", "result", response.statusCode.value().toString()).increment()
        return response
    }

    /** Etapa `CONFIRM_PAYMENT`: o `orderId` só é conhecido quando o pagamento é carregado do banco (§8). */
    @Suppress("TooGenericExceptionCaught")
    private fun handle(
        body: String?,
        signature: String?,
        requestId: String?,
        queryDataId: String?,
    ): ResponseEntity<Void> {
        val dataId = extractDataId(body) ?: queryDataId?.takeIf { it.isNotBlank() }
        dataId?.let { MDC.put(MP_ORDER_ID, it) }
        val step = FlowLog.begin(log, null, STATUS)
        return try {
            step.start("Notificação de pagamento recebida", "mpOrderId" to dataId)
            val response = confirm(step, dataId, signature, requestId)
            val httpStatus = response.statusCode.value()
            if (FlowLog.current() === step) step.end("Notificação processada", "httpStatus" to httpStatus)
            response
        } catch (exception: DataAccessException) {
            step.warnFail(RETRY_MESSAGE, exception, "httpStatus" to SERVER_ERROR)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        } catch (exception: Exception) {
            step.errorFail("Falha inesperada ao processar a notificação", exception, "httpStatus" to SERVER_ERROR)
            throw exception
        } finally {
            MDC.remove(MP_ORDER_ID)
            MDC.remove(FlowLog.ORDER_ID_KEY)
        }
    }

    private fun confirm(
        step: FlowStep,
        dataId: String?,
        signature: String?,
        requestId: String?,
    ): ResponseEntity<Void> {
        if (!signatureValidator.isValid(signature, requestId, dataId)) {
            step.warnFail("Assinatura do webhook inválida, notificação recusada", null, "httpStatus" to UNAUTHORIZED)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        if (dataId == null) {
            step.endMessage("Notificação sem id da order, ignorada")
            return ResponseEntity.ok().build()
        }
        return when (confirmPayment.execute(dataId)) {
            WebhookResult.CONFIRMED, WebhookResult.IGNORED -> {
                ResponseEntity.ok().build()
            }

            WebhookResult.RETRY -> {
                step.warnFail(
                    "Falha transitória ao consultar a order no Mercado Pago; o Mercado Pago vai reenviar",
                    null,
                    "httpStatus" to SERVER_ERROR,
                )
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }
        }
    }

    private fun extractDataId(body: String?): String? =
        body
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { mapper.readTree(it) }.getOrNull() }
            ?.path("data")
            ?.path("id")
            ?.asString("")
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val STATUS = "CONFIRM_PAYMENT"
        const val MP_ORDER_ID = "mpOrderId"
        const val UNAUTHORIZED = 401
        const val SERVER_ERROR = 500
        const val RETRY_MESSAGE = "Falha transitória ao processar a notificação; o Mercado Pago vai reenviar"
        val log = LoggerFactory.getLogger(PaymentWebhookController::class.java)
    }
}
