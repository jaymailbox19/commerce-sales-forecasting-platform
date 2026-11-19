package com.csfp.sales.repository;

import com.csfp.sales.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByNameIgnoreCase(String name);
}
