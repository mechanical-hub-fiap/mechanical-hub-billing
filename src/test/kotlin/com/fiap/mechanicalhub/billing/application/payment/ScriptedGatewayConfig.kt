package com.fiap.mechanicalhub.billing.application.payment

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class ScriptedGatewayConfig {
    @Bean
    @Primary
    fun scriptedGateway() = ScriptedGateway()
}
