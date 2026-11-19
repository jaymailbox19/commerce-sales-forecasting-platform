package com.csfp.sales.dto;

import java.math.BigDecimal;

public record ProductSalesRanking(
        Long productId,
        String productName,
        String categoryName,
        BigDecimal totalSales,
        Long totalQuantity,
        Long rank
) {
}
