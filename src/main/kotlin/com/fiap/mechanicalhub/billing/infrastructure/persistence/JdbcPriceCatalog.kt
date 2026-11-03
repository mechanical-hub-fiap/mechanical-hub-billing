package com.fiap.mechanicalhub.billing.infrastructure.persistence

import com.fiap.mechanicalhub.billing.application.command.PriceCatalog
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.UUID

@Repository
class JdbcPriceCatalog(private val jdbc: JdbcClient) : PriceCatalog {
    override fun laborPrices(repairIds: Collection<UUID>): Map<UUID, BigDecimal> =
        prices(
            "SELECT service_id AS id, labor_price AS price FROM service_prices WHERE service_id IN (:ids)",
            repairIds,
        )

    override fun materialPrices(materialIds: Collection<UUID>): Map<UUID, BigDecimal> =
        prices(
            "SELECT material_id AS id, unit_price AS price FROM material_prices WHERE material_id IN (:ids)",
            materialIds,
        )

    private fun prices(sql: String, ids: Collection<UUID>): Map<UUID, BigDecimal> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            jdbc
                .sql(sql)
                .param("ids", ids.distinct())
                .query { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getBigDecimal("price") }
                .list()
                .toMap()
        }
}
