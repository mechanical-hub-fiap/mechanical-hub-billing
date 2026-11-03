package com.fiap.mechanicalhub.billing.application.payment

interface TransactionRunner {
    fun <T> run(block: () -> T): T
}
