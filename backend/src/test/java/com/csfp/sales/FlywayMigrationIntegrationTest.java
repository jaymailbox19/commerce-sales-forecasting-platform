package com.csfp.sales;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real PostgreSQL migrations against isolated databases.
 *
 * The test connects to the PostgreSQL instance supplied by DB_URL. The
 * configured user must be allowed to create and drop databases (the local
 * PostgreSQL Docker image and the GitHub Actions service user both are).
 */
class FlywayMigrationIntegrationTest {

    private final String configuredUrl = System.getenv().getOrDefault(
            "DB_URL", "jdbc:postgresql://localhost:5432/commerce_sales");
    private final String username = System.getenv().getOrDefault("DB_USER", "csfp");
    private final String password = System.getenv().getOrDefault("DB_PASSWORD", "ci-only-password");

    @Test
    void freshDatabaseAppliesV1AndV2() throws Exception {
        String databaseName = newDatabaseName("fresh");

        try {
            createDatabase(databaseName);
            migrate(databaseUrl(databaseName));

            try (Connection connection = connect(databaseUrl(databaseName));
                 Statement statement = connection.createStatement()) {
                assertEquals(2, scalarInt(statement,
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE"));
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND type = 'SQL'"));
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND type = 'SQL'"));
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM tenants WHERE name = 'Demo Tenant'"));
                assertEquals(3, scalarInt(statement,
                        "SELECT COUNT(*) FROM information_schema.columns " +
                                "WHERE table_schema = 'public' AND table_name IN ('categories', 'products', 'sales') " +
                                "AND column_name = 'tenant_id' AND data_type = 'bigint' AND is_nullable = 'NO'"));
            }
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void existingPrototypeSchemaIsBaselinedAndUpgradedWithoutLosingSales() throws Exception {
        String databaseName = newDatabaseName("legacy");

        try {
            createDatabase(databaseName);
            createLegacyPrototypeSchema(databaseUrl(databaseName));
            migrate(databaseUrl(databaseName));

            try (Connection connection = connect(databaseUrl(databaseName));
                 Statement statement = connection.createStatement()) {
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM flyway_schema_history " +
                                "WHERE version = '1' AND type = 'BASELINE' AND success = TRUE"));
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM flyway_schema_history " +
                                "WHERE version = '2' AND type = 'SQL' AND success = TRUE"));
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM tenants WHERE name = 'Demo Tenant'"));
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM categories WHERE name = 'Beverages' AND tenant_id = 1"));
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM products WHERE name = 'Coffee' AND tenant_id = 1"));
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM sales WHERE amount = 19.99 AND tenant_id = 1"));
                assertEquals(1, scalarInt(statement,
                        "SELECT COUNT(*) FROM information_schema.columns " +
                                "WHERE table_schema = 'public' AND table_name = 'tenants' " +
                                "AND column_name = 'id' AND data_type = 'bigint'"));
            }
        } finally {
            dropDatabase(databaseName);
        }
    }

    private void migrate(String jdbcUrl) {
        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .baselineDescription("Existing CSFP prototype schema")
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    private void createLegacyPrototypeSchema(String jdbcUrl) throws Exception {
        try (Connection connection = connect(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE tenants (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL UNIQUE
                    )
                    """);
            statement.execute("INSERT INTO tenants (id, name) VALUES (1, 'Demo Tenant')");
            statement.execute("""
                    CREATE TABLE categories (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        CONSTRAINT categories_name_key UNIQUE (name)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE products (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        category_id BIGINT NOT NULL,
                        CONSTRAINT fk_products_category
                            FOREIGN KEY (category_id) REFERENCES categories(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE sales (
                        id BIGSERIAL PRIMARY KEY,
                        tenant_id INTEGER NOT NULL REFERENCES tenants(id),
                        product_id BIGINT NOT NULL,
                        sale_date DATE NOT NULL,
                        quantity INTEGER NOT NULL,
                        amount NUMERIC(15, 2) NOT NULL,
                        CONSTRAINT uk_sale_product_date_amount
                            UNIQUE (product_id, sale_date, amount),
                        CONSTRAINT fk_sales_product
                            FOREIGN KEY (product_id) REFERENCES products(id)
                    )
                    """);
            statement.execute("INSERT INTO categories (id, name) VALUES (10, 'Beverages')");
            statement.execute("INSERT INTO products (id, name, category_id) VALUES (20, 'Coffee', 10)");
            statement.execute("""
                    INSERT INTO sales (id, tenant_id, product_id, sale_date, quantity, amount)
                    VALUES (30, 1, 20, DATE '2026-09-01', 2, 19.99)
                    """);
        }
    }

    private void createDatabase(String databaseName) throws Exception {
        try (Connection connection = connect(adminDatabaseUrl());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + databaseName + "\"");
        }
    }

    private void dropDatabase(String databaseName) throws Exception {
        try (Connection connection = connect(adminDatabaseUrl());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS \"" + databaseName + "\" WITH (FORCE)");
        }
    }

    private Connection connect(String jdbcUrl) throws Exception {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private String adminDatabaseUrl() {
        return databaseUrl("postgres");
    }

    private String databaseUrl(String databaseName) {
        String url = configuredUrl;
        int queryStart = url.indexOf('?');
        if (queryStart >= 0) {
            url = url.substring(0, queryStart);
        }
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash < 0) {
            throw new IllegalArgumentException("DB_URL must be a PostgreSQL JDBC URL with a database name");
        }
        return url.substring(0, lastSlash + 1) + databaseName;
    }

    private String newDatabaseName(String suffix) {
        return "csfp_flyway_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private int scalarInt(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next(), "Expected a row for query: " + sql);
            return resultSet.getInt(1);
        }
    }
}
