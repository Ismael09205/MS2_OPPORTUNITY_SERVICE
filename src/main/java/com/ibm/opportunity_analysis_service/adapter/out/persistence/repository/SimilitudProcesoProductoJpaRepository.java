package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.SimilitudProcesoProductoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SimilitudProcesoProductoJpaRepository extends JpaRepository<SimilitudProcesoProductoEntity, Long> {

    Optional<SimilitudProcesoProductoEntity> findByOcidAndProductoIbmId(String ocid, Long productoIbmId);

    List<SimilitudProcesoProductoEntity> findByOcid(String ocid);

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
}
