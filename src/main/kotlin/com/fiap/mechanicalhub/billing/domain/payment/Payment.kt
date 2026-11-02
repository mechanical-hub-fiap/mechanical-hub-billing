package com.fiap.mechanicalhub.billing.domain.payment

import java.time.Instant

data class Payment(
    val status: PaymentStatusEnum,
    val mpStatus: String?,
    val paidAt: Instant? = null,
    val canceledAt: Instant? = null,
) {
    fun approve(mpStatus: String, now: Instant): PaymentTransition =
        if (status != PaymentStatusEnum.PENDING) {
            PaymentTransition.Skipped
        } else {
            PaymentTransition.Applied(copy(status = PaymentStatusEnum.APPROVED, mpStatus = mpStatus, paidAt = now))
        }

    fun cancel(mpStatus: String, now: Instant): PaymentTransition =
        if (status != PaymentStatusEnum.PENDING) {
            PaymentTransition.Skipped
        } else {
            PaymentTransition.Applied(copy(status = PaymentStatusEnum.CANCELED, mpStatus = mpStatus, canceledAt = now))
        }
}
