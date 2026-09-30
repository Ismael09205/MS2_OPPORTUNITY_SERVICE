package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.ProductoIbmEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoIbmJpaRepository extends JpaRepository<ProductoIbmEntity, Long> {
}