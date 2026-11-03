package com.fiap.mechanicalhub.billing.infrastructure.messaging

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class RecordingSenderConfig {
    @Bean
    @Primary
    fun recordingSender() = RecordingSender()
}
