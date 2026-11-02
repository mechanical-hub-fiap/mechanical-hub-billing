package com.fiap.mechanicalhub.billing.application.command

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class RecordingGatewayConfig {
    @Bean
    @Primary
    fun recordingGateway() = RecordingGateway()
}
