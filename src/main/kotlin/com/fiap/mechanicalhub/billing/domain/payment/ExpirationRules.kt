package com.fiap.mechanicalhub.billing.domain.payment

object ExpirationRules {
    private const val CANCELED = "canceled"
    private const val PROCESSED = "processed"
    private const val FAILED = "failed"
    private const val ACCREDITED = "accredited"

    /** Decisão do job após o cancelamento; [lookup] só é usado no caso B (conflito). */
    fun decide(cancel: CancelOutcome, lookup: OrderLookup? = null): ExpirationDecision =
        when (cancel) {
            CancelOutcome.Canceled -> ExpirationDecision.Fail(PaymentFailureReason.EXPIRED, CANCELED)
            CancelOutcome.Transient -> ExpirationDecision.Wait
            CancelOutcome.Permanent -> ExpirationDecision.Alert
            CancelOutcome.Conflict -> lookup?.let(::decideAfterConflict) ?: ExpirationDecision.Wait
        }

    private fun decideAfterConflict(lookup: OrderLookup): ExpirationDecision =
        when (lookup) {
            OrderLookup.Transient -> ExpirationDecision.Wait
            OrderLookup.Permanent -> ExpirationDecision.Alert
            is OrderLookup.Found -> decideByStatus(lookup)
        }

    private fun decideByStatus(order: OrderLookup.Found): ExpirationDecision =
        when {
            order.status == PROCESSED && order.statusDetail == ACCREDITED -> ExpirationDecision.Confirm(PROCESSED)
            order.status == CANCELED -> ExpirationDecision.Fail(PaymentFailureReason.EXPIRED, CANCELED)
            order.status == FAILED -> ExpirationDecision.Fail(PaymentFailureReason.FAILED, FAILED)
            else -> ExpirationDecision.Wait
        }
}
