package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.ProcesoContratacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcesoSercopJpaRepository extends JpaRepository<ProcesoContratacionEntity, Long> {

    /**
     * Tarea 1.6 (H5): lookup directo por OCID. El código original comparaba con
     * {@code ocid.equalsIgnoreCase(p.getOcid())} tras cargar los 381 procesos.
     */
    Optional<ProcesoContratacionEntity> findByOcidIgnoreCase(String ocid);
}