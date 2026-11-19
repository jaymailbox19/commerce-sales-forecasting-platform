package com.csfp.sales.dto;

import java.math.BigDecimal;

public record WeeklyPrediction(
        String week,
        BigDecimal predictedSales
) {
}