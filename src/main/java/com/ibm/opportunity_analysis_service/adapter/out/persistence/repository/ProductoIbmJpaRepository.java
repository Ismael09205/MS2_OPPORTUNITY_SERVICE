package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.ProductoIbmEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoIbmJpaRepository extends JpaRepository<ProductoIbmEntity, Long> {

    List<ProductoIbmEntity> findByNombre(String nombre);
}