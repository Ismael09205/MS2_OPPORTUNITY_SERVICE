package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.CandidatoTecnologicoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidatoTecnologicoJpaRepository extends JpaRepository<CandidatoTecnologicoEntity, Long> {

    Optional<CandidatoTecnologicoEntity> findByOcid(String ocid);
}