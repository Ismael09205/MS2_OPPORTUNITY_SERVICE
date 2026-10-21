package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.RecomendacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecomendacionJpaRepository extends JpaRepository<RecomendacionEntity, Long> {

    Optional<RecomendacionEntity> findByOcid(String ocid);

    /**
     * Paginacion sobre oportunidades reales: solo las que Granite marcó esOportunidadIbm=true.
     * El usuario es el disparador — se consulta a medida que navega.
     */
    @Query(value = """
        SELECT * FROM recomendacion
        WHERE es_oportunidad_ibm = true
        ORDER BY score_compuesto DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<RecomendacionEntity> findOportunidadesIbm(@Param("limit") int limit, @Param("offset") int offset);
}
