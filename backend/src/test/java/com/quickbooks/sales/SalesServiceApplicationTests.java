package com.quickbooks.sales;

import com.quickbooks.sales.dto.CategorySalesResponse;
import com.quickbooks.sales.dto.DailyCategorySalesResponse;
import com.quickbooks.sales.dto.ProductSalesRanking;
import com.quickbooks.sales.model.Period;
import com.quickbooks.sales.repository.SaleRepository;
import com.quickbooks.sales.service.SalesService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SalesServiceApplicationTests {

    private static final Long TENANT_ID = 1L;

    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final SalesService salesService = new SalesService(saleRepository);

    @Test
    void getSalesByCategory_shouldGroupAndSortSalesDescending() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);

        when(saleRepository.findSalesByCategoryAndDateBetween(
                TENANT_ID, startDate, endDate))
                .thenReturn(List.of(
                        new Object[]{"Electronics", LocalDate.of(2026, 1, 5),
                                new BigDecimal("100.00")},
                        new Object[]{"Books", LocalDate.of(2026, 1, 10),
                                new BigDecimal("50.00")},
                        new Object[]{"Electronics", LocalDate.of(2026, 1, 15),
                                new BigDecimal("200.00")}
                ));

        List<CategorySalesResponse> result =
                salesService.getSalesByCategory(TENANT_ID, startDate, endDate, null);

        assertEquals(2, result.size());
        assertEquals("Electronics", result.get(0).category());
        assertEquals(new BigDecimal("300.00"), result.get(0).totalSales());
        assertEquals("Books", result.get(1).category());
        assertEquals(new BigDecimal("50.00"), result.get(1).totalSales());

        verify(saleRepository).findSalesByCategoryAndDateBetween(
                TENANT_ID, startDate, endDate);
    }

    @Test
    void getSalesByCategory_shouldRejectInvalidDateRange() {
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salesService.getSalesByCategory(
                        TENANT_ID, startDate, endDate, null));

        assertEquals("Start date must be before or equal to end date",
                exception.getMessage());
        verifyNoInteractions(saleRepository);
    }

    @Test
    void getSalesByCategory_shouldRejectNullDates() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salesService.getSalesByCategory(
                        TENANT_ID, null, LocalDate.of(2026, 1, 31), null));

        assertEquals("Start date and end date are required", exception.getMessage());
        verifyNoInteractions(saleRepository);
    }

    @Test
    void getSalesByCategory_shouldReturnPeriodSales() {
        when(saleRepository.findSalesByCategory(eq(TENANT_ID), any(LocalDate.class)))
                .thenReturn(List.of(
                        new Object[]{"Electronics", new BigDecimal("1000.00")},
                        new Object[]{"Books", new BigDecimal("500.00")}
                ));

        List<CategorySalesResponse> result =
                salesService.getSalesByCategory(TENANT_ID, Period.WEEK);

        assertEquals(2, result.size());
        assertEquals("Electronics", result.get(0).category());
        assertEquals(new BigDecimal("1000.00"), result.get(0).totalSales());
        verify(saleRepository).findSalesByCategory(TENANT_ID, any(LocalDate.class));
    }

    @Test
    void getProductSalesRanking_shouldMapRowsAndAssignRanks() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);

        when(saleRepository.findProductSalesRanking(
                TENANT_ID, startDate, endDate.plusDays(1), "Electronics"))
                .thenReturn(List.of(
                        new Object[]{101L, "Laptop", "Electronics",
                                new BigDecimal("2500.00"), 5L},
                        new Object[]{102L, "Monitor", "Electronics",
                                new BigDecimal("1200.00"), 4L}
                ));

        List<ProductSalesRanking> result = salesService.getProductSalesRanking(
                TENANT_ID, startDate, endDate, "Electronics");

        assertEquals(2, result.size());
        assertEquals(101L, result.get(0).productId());
        assertEquals("Laptop", result.get(0).productName());
        assertEquals(1L, result.get(0).rank());
        assertEquals(102L, result.get(1).productId());
        assertEquals("Monitor", result.get(1).productName());
        assertEquals(2L, result.get(1).rank());

        verify(saleRepository).findProductSalesRanking(
                TENANT_ID, startDate, endDate.plusDays(1), "Electronics");
    }

    @Test
    void getProductSalesRanking_shouldRejectNullDates() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salesService.getProductSalesRanking(
                        TENANT_ID, null, LocalDate.of(2026, 1, 31), "Electronics"));

        assertEquals("Start date and end date are required", exception.getMessage());
        verifyNoInteractions(saleRepository);
    }

    @Test
    void getDailySalesByCategory_shouldFilterCategoryAndMapRows() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 3);

        when(saleRepository.findSalesByCategoryAndDateBetween(
                TENANT_ID, startDate, endDate))
                .thenReturn(List.of(
                        new Object[]{"Electronics", LocalDate.of(2026, 1, 1),
                                new BigDecimal("1200.00")},
                        new Object[]{"Clothing", LocalDate.of(2026, 1, 2),
                                new BigDecimal("500.00")},
                        new Object[]{"Electronics", LocalDate.of(2026, 1, 3),
                                new BigDecimal("1800.00")}
                ));

        List<DailyCategorySalesResponse> result =
                salesService.getDailySalesByCategory(
                        TENANT_ID, "Electronics", startDate, endDate);

        assertEquals(2, result.size());
        assertEquals("Electronics", result.get(0).category());
        assertEquals(LocalDate.of(2026, 1, 1), result.get(0).date());
        assertEquals(new BigDecimal("1200.00"), result.get(0).amount());
        assertEquals(LocalDate.of(2026, 1, 3), result.get(1).date());
        assertEquals(new BigDecimal("1800.00"), result.get(1).amount());

        verify(saleRepository).findSalesByCategoryAndDateBetween(
                TENANT_ID, startDate, endDate);
    }

    @Test
    void getDailySalesByCategory_shouldRejectBlankCategory() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salesService.getDailySalesByCategory(
                        TENANT_ID, " ", LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31)));

        assertEquals("Category is required", exception.getMessage());
        verifyNoInteractions(saleRepository);
    }

    @Test
    void getDailySalesByCategory_shouldRejectNullDates() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salesService.getDailySalesByCategory(
                        TENANT_ID, "Electronics", null,
                        LocalDate.of(2026, 1, 31)));

        assertEquals("Start date and end date are required", exception.getMessage());
        verifyNoInteractions(saleRepository);
    }

    @Test
    void getDailySalesByCategory_shouldRejectStartDateAfterEndDate() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salesService.getDailySalesByCategory(
                        TENANT_ID, "Electronics",
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 31)));

        assertEquals("Start date must be before or equal to end date",
                exception.getMessage());
        verifyNoInteractions(saleRepository);
    }
}
