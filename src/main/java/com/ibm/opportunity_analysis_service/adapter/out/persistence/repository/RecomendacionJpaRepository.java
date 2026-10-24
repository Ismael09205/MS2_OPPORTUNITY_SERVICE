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
    /**
     * Tarea 1.7 (decisión A): la lista principal exige estado=EVALUADA, de modo que una
     * preselección vectorial sin criterio jamás aparezca como recomendación (H1). Las filas sin
     * estado (legadas, previas a la Fase 1) quedan fuera: no son verificables.
     * También exige es_oportunidad_ibm=true (decisión D: los descartes no se listan).
     */
    @Query(value = """
        SELECT * FROM recomendacion
        WHERE es_oportunidad_ibm = true
          AND estado = 'EVALUADA'
        ORDER BY score_compuesto DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<RecomendacionEntity> findOportunidadesEvaluadas(@Param("limit") int limit, @Param("offset") int offset);

    /**
     * Tarea 1.7 (decisión A): preselecciones y errores de evaluación pendientes de juicio profundo.
     * Se exponen aparte, nunca mezclados con las evaluadas.
     */
    @Query(value = """
        SELECT * FROM recomendacion
        WHERE estado IN ('PRESELECCION_VECTORIAL', 'ERROR_EVALUACION')
        ORDER BY score_compuesto DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<RecomendacionEntity> findPendientes(@Param("limit") int limit, @Param("offset") int offset);

    /**
     * Cola humana de revisión (plan REVISAR): casos donde el LLM se abstuvo (REQUIERE_REVISION)
     * con su causa en justificacion. El orden es por abstención más antigua primero, que es la
     * cola de trabajo de Bob.
     */
    @Query(value = """
        SELECT * FROM recomendacion
        WHERE estado = 'REQUIERE_REVISION'
        ORDER BY fecha_evaluacion ASC NULLS FIRST
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<RecomendacionEntity> findColaRevision(@Param("limit") int limit, @Param("offset") int offset);
}
