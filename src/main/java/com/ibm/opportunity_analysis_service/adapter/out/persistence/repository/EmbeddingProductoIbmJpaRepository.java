package com.ibm.opportunity_analysis_service.adapter.out.persistence.repository;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.EmbeddingProductoIbmJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmbeddingProductoIbmJpaRepository extends JpaRepository<EmbeddingProductoIbmJpaEntity, Long> {

    Optional<EmbeddingProductoIbmJpaEntity> findByProductoIbmIdAndModelo(Long productoIbmId, String modelo);
    @Query(value = """
        SELECT p.id,
               p.nombre,
               1 - (e.embedding <=> CAST(:embedding AS vector)) AS similitud
        FROM embedding_producto_ibm e
        INNER JOIN producto_ibm p ON p.id = e.producto_ibm_id
        ORDER BY e.embedding <=> CAST(:embedding AS vector)
        LIMIT :limite
        """, nativeQuery = true)
    List<Object[]> buscarSimilares(@Param("embedding") String embedding, @Param("limite") int limite);
}
