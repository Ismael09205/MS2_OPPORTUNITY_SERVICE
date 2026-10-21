package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.EmbeddingProductoIbmJpaEntity;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.EmbeddingProductoIbmJpaRepository;
import com.ibm.opportunity_analysis_service.application.mapper.EmbeddingProductoIbmMapper;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoSimilitudIbm;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmbeddingProductoIbmPersistenceAdapter implements EmbeddingProductoIbmPersistencePort {

    private final EmbeddingProductoIbmJpaRepository embeddingProductoIbmJpaRepository;
    private final EmbeddingProductoIbmMapper embeddingProductoIbmMapper;

    public EmbeddingProductoIbmPersistenceAdapter(EmbeddingProductoIbmJpaRepository embeddingProductoIbmJpaRepository, EmbeddingProductoIbmMapper embeddingProductoIbmMapper) {
        this.embeddingProductoIbmJpaRepository = embeddingProductoIbmJpaRepository;
        this.embeddingProductoIbmMapper = embeddingProductoIbmMapper;
    }

    @Override
    public List<EmbeddingProductoIbm> guardarTodos(List<EmbeddingProductoIbm> embeddings) {
        List<EmbeddingProductoIbmJpaEntity> aGuardar = embeddings.stream()
                .map(e -> {
                    EmbeddingProductoIbmJpaEntity entity = embeddingProductoIbmJpaRepository
                            .findByProductoIbmIdAndModelo(e.getProductoIbmId(), e.getModelo())
                            .orElseGet(() -> embeddingProductoIbmMapper.toEntity(e));

                    entity.setProductoIbmId(e.getProductoIbmId());
                    entity.setModelo(e.getModelo());
                    entity.setTextoEmbedding(e.getTextoEmbedding());
                    entity.setEmbedding(e.getEmbedding());
                    entity.setFechaGeneracion(e.getFechaGeneracion());
                    return entity;
                })
                .toList();

        List<EmbeddingProductoIbmJpaEntity> guardados = embeddingProductoIbmJpaRepository.saveAll(aGuardar);

        return guardados.stream()
                .map(embeddingProductoIbmMapper::toDomain)
                .toList();
    }

    @Override
    public List<EmbeddingProductoIbm> buscarTodos() {
        return embeddingProductoIbmJpaRepository.findAll()
                .stream()
                .map(embeddingProductoIbmMapper::toDomain)
                .toList();
    }

    @Override
    public List<ResultadoSimilitudIbm> buscarSimilares(float[] embedding, int limite) {

        String vector = convertirVector(embedding);

        return embeddingProductoIbmJpaRepository
                .buscarPorSimilitudCoseno(vector, limite)
                .stream()
                .map(resultado -> new ResultadoSimilitudIbm(
                        ((Number) resultado[0]).longValue(),
                        (String) resultado[1],
                        ((Number) resultado[2]).doubleValue()
                ))
                .toList();
    }

    private String convertirVector(float[] embedding) {

        StringBuilder vector = new StringBuilder("[");

        for (int i = 0; i < embedding.length; i++) {

            if (i > 0) {
                vector.append(",");
            }

            vector.append(embedding[i]);
        }

        vector.append("]");

        return vector.toString();
    }
}