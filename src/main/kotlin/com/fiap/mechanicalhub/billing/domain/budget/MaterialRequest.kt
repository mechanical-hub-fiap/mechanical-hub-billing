package com.fiap.mechanicalhub.billing.domain.budget

import java.math.BigDecimal
import java.util.UUID

data class MaterialRequest(val materialId: UUID, val quantity: BigDecimal)
