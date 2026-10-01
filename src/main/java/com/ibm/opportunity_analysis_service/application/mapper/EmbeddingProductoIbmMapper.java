package com.ibm.opportunity_analysis_service.application.mapper;

import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingProductoIbm;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.EmbeddingProductoIbmJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingProductoIbmMapper {

    public EmbeddingProductoIbmJpaEntity toEntity(EmbeddingProductoIbm embeddingProductoIbm) {
        EmbeddingProductoIbmJpaEntity entity = new EmbeddingProductoIbmJpaEntity();

        entity.setId(embeddingProductoIbm.getId());
        entity.setProductoIbmId(embeddingProductoIbm.getProductoIbmId());
        entity.setModelo(embeddingProductoIbm.getModelo());
        entity.setTextoEmbedding(embeddingProductoIbm.getTextoEmbedding());
        entity.setEmbedding(embeddingProductoIbm.getEmbedding());
        entity.setFechaGeneracion(embeddingProductoIbm.getFechaGeneracion());

        return entity;
    }

    public EmbeddingProductoIbm toDomain(EmbeddingProductoIbmJpaEntity entity) {
        EmbeddingProductoIbm embeddingProductoIbm = new EmbeddingProductoIbm();

        embeddingProductoIbm.setId(entity.getId());
        embeddingProductoIbm.setProductoIbmId(entity.getProductoIbmId());
        embeddingProductoIbm.setModelo(entity.getModelo());
        embeddingProductoIbm.setTextoEmbedding(entity.getTextoEmbedding());
        embeddingProductoIbm.setEmbedding(entity.getEmbedding());
        embeddingProductoIbm.setFechaGeneracion(entity.getFechaGeneracion());

        return embeddingProductoIbm;
    }
}