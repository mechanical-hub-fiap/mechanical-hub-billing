package com.fiap.mechanicalhub.billing.infrastructure.persistence

import com.fiap.mechanicalhub.billing.application.payment.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class SpringTransactionRunner(transactionManager: PlatformTransactionManager) : TransactionRunner {
    private val template = TransactionTemplate(transactionManager)

    override fun <T> run(block: () -> T): T = template.execute { block() }
}
