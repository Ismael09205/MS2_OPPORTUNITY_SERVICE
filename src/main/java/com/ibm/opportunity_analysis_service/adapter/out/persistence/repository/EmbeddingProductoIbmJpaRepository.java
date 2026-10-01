package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.EmbeddingProductoIbmJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmbeddingProductoIbmJpaRepository extends JpaRepository<EmbeddingProductoIbmJpaEntity, Long> {

    Optional<EmbeddingProductoIbmJpaEntity> findByProductoIbmIdAndModelo(Long productoIbmId, String modelo);
}