package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.EmbeddingCandidatoTecEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmbeddingCandidatoTecnologicoJpaRepository extends JpaRepository<EmbeddingCandidatoTecEntity, Long> {

    Optional<EmbeddingCandidatoTecEntity> findByOcidAndModelo(String ocid, String modelo);

}