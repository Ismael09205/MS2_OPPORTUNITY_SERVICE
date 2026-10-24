package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.SimilitudProcesoProductoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SimilitudProcesoProductoJpaRepository extends JpaRepository<SimilitudProcesoProductoEntity, Long> {

    Optional<SimilitudProcesoProductoEntity> findByOcidAndProductoIbmId(String ocid, Long productoIbmId);

    List<SimilitudProcesoProductoEntity> findByOcid(String ocid);

    /**
     * Borra todas las similitudes de un OCID (tarea 1.4, hallazgo H4): un proceso que deja de ser
     * elegible no debe conservar similitudes que alimenten una preselección obsoleta.
     */
    @Transactional
    void deleteByOcid(String ocid);

    /**
     * Devuelve el hash de contenido del primer registro encontrado para el OCID.
     * Todos los registros del mismo OCID comparten el mismo hash (mismos campos fuente).
     */
    @Query("SELECT s.hashContenido FROM SimilitudProcesoProductoEntity s WHERE s.ocid = :ocid ORDER BY s.id ASC LIMIT 1")
    Optional<String> findHashContenidoByOcid(@Param("ocid") String ocid);

    @Query("SELECT COUNT(DISTINCT s.ocid) FROM SimilitudProcesoProductoEntity s")
    long countDistinctOcid();

    /**
     * OCIDs con mayor score compuesto que aun no tienen recomendacion generada.
     * Se cruza con la tabla recomendacion para excluir los ya evaluados.
     * Se usa para el relleno perezoso de preselecciones: solo procesos sin fila.
     */
    @Query(value = """
        SELECT s.ocid
        FROM similitud_proceso_producto s
        WHERE s.ocid NOT IN (SELECT ocid FROM recomendacion)
        GROUP BY s.ocid
        ORDER BY MAX(s.score_compuesto) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<String> findSiguientesAEvaluar(@Param("limit") int limit);

    /**
     * Tarea 1.5: OCIDs pendientes de evaluación profunda con LLM. Incluye los que no tienen fila
     * y los que tienen un estado NO definitivo:
     *  - ERROR_EVALUACION: reintento tras un fallo del LLM (H3) — antes quedaban excluidos para
     *    siempre porque el cruce era "cualquier fila en recomendacion".
     *  - PRESELECCION_VECTORIAL: afinidad vectorial que aún no pasó por el criterio (H1).
     * Excluye EVALUADA y DESCARTADA_ALCANCE, que son definitivos, y REQUIERE_REVISION, que está
     * en la cola humana.
     *
     * Política de reintento (2026-10-24, ver application.util.PoliticaReintento): un caso en
     * ERROR_EVALUACION solo sale en su ventana programada (backoff 5m→15m→1h→4h→8h) y, tras
     * MAX_INTENTOS=6 fallos seguidos, queda estancado (sin reprogramación + WARN). Así un túnel
     * caído no provoca llamadas fallidas continuas ni oculta casos que necesitan revisión manual.
     */
    @Query(value = """
        SELECT s.ocid
        FROM similitud_proceso_producto s
        LEFT JOIN recomendacion r ON r.ocid = s.ocid
        WHERE r.ocid IS NULL
           OR (r.estado IN ('PRESELECCION_VECTORIAL', 'ERROR_EVALUACION')
               AND COALESCE(r.intentos_reintento, 0) < 6
               AND (r.proximo_intento_at IS NULL OR r.proximo_intento_at <= now()))
        GROUP BY s.ocid, r.estado
        ORDER BY MAX(s.score_compuesto) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<String> findPendientesDeEvaluacion(@Param("limit") int limit);

    /**
     * Tarea 1.8 (conteo de pendientes): cuántos OCIDs con similitud están sin evaluar o en un
     * estado no definitivo reintentable. Usa el mismo filtro de backoff que
     * {@link #findPendientesDeEvaluacion} para que el conteo coincida con lo que se va a evaluar.
     * Un caso estancado no cuenta: no está "pendiente" de nada automático, necesita revisión.
     */
    @Query(value = """
        SELECT COUNT(*) FROM (
            SELECT s.ocid
            FROM similitud_proceso_producto s
            LEFT JOIN recomendacion r ON r.ocid = s.ocid
            WHERE r.ocid IS NULL
               OR (r.estado IN ('PRESELECCION_VECTORIAL', 'ERROR_EVALUACION')
                   AND COALESCE(r.intentos_reintento, 0) < 6
                   AND (r.proximo_intento_at IS NULL OR r.proximo_intento_at <= now()))
            GROUP BY s.ocid
        ) pendientes
        """, nativeQuery = true)
    long countPendientesDeEvaluacion();
}
