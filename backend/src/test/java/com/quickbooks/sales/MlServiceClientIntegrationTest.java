package com.quickbooks.sales;

import com.quickbooks.sales.client.MlServiceClient;
import com.quickbooks.sales.dto.CategoryPredictionResponse;
import com.quickbooks.sales.dto.ForecastRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MlServiceClientIntegrationTest {

    @Test
    void forecast_shouldSendAuthenticatedRequestAndDeserializeResponse() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();

        HttpServer mockMlService = HttpServer.create(
                new InetSocketAddress("localhost", 0), 0);
        mockMlService.createContext("/forecast", exchange -> {
            receivedToken.set(exchange.getRequestHeaders()
                    .getFirst("X-ML-Service-Token"));
            receivedBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));

            byte[] response = """
                    {
                      "category": "Electronics",
                      "predictedSales": 12500.00,
                      "historicalThroughDate": "2026-08-03",
                      "weeklyPredictions": [],
                      "historicalActuals": [],
                      "productForecasts": []
                    }
                    """.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add(
                    "Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        mockMlService.start();

        try {
            String baseUrl = "http://localhost:"
                    + mockMlService.getAddress().getPort();
            MlServiceClient client = new MlServiceClient(baseUrl, "test-ml-token");

            ForecastRequest request = new ForecastRequest(
                    1L,
                    "Electronics",
                    List.of(
                            new ForecastRequest.WeeklySales(
                                    LocalDate.of(2026, 6, 15),
                                    new BigDecimal("9000")),
                            new ForecastRequest.WeeklySales(
                                    LocalDate.of(2026, 6, 22),
                                    new BigDecimal("11000"))
                    ),
                    1
            );

            CategoryPredictionResponse response = client.forecast(request);

            assertNotNull(response);
            assertEquals("Electronics", response.category());
            assertEquals(new BigDecimal("12500.00"), response.predictedSales());
            assertEquals("test-ml-token", receivedToken.get());
            assertTrue(receivedBody.get().contains("\"category\":\"Electronics\""));
        } finally {
            mockMlService.stop(0);
        }
    }
}
