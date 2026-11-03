package com.fiap.mechanicalhub.billing.application.command

import java.math.BigDecimal
import java.util.UUID

interface PriceCatalog {
    fun laborPrices(repairIds: Collection<UUID>): Map<UUID, BigDecimal>

    fun materialPrices(materialIds: Collection<UUID>): Map<UUID, BigDecimal>
}
