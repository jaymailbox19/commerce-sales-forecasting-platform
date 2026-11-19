package com.csfp.sales.dto;

import java.math.BigDecimal;

public record TopCategoryPredictionResponse(
        String category,
        BigDecimal predictedSales
) {}
