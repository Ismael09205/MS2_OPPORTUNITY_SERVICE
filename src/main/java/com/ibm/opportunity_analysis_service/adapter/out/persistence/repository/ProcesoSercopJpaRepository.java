package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.ProcesoContratacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcesoSercopJpaRepository extends JpaRepository<ProcesoContratacionEntity, Long> {
}