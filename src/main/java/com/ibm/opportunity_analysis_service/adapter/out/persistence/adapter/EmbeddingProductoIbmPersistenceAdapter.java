package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.EmbeddingProductoIbmJpaEntity;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.EmbeddingProductoIbmJpaRepository;
import com.ibm.opportunity_analysis_service.application.mapper.EmbeddingProductoIbmMapper;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingProductoIbm;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EmbeddingProductoIbmPersistenceAdapter implements EmbeddingProductoIbmPersistencePort {

    private final EmbeddingProductoIbmJpaRepository embeddingProductoIbmJpaRepository;
    private final EmbeddingProductoIbmMapper embeddingProductoIbmMapper;

    public EmbeddingProductoIbmPersistenceAdapter(EmbeddingProductoIbmJpaRepository embeddingProductoIbmJpaRepository, EmbeddingProductoIbmMapper embeddingProductoIbmMapper) {
        this.embeddingProductoIbmJpaRepository = embeddingProductoIbmJpaRepository;
        this.embeddingProductoIbmMapper = embeddingProductoIbmMapper;
    }

    @Override
    public EmbeddingProductoIbm guardar(EmbeddingProductoIbm embeddingProductoIbm) {
        EmbeddingProductoIbmJpaEntity entity = embeddingProductoIbmMapper.toEntity(embeddingProductoIbm);

        EmbeddingProductoIbmJpaEntity guardado = embeddingProductoIbmJpaRepository.save(entity);

        return embeddingProductoIbmMapper.toDomain(guardado);
    }

    @Override
    public List<EmbeddingProductoIbm> guardarTodos(List<EmbeddingProductoIbm> embeddings) {
        List<EmbeddingProductoIbmJpaEntity> entities = embeddings.stream()
                .map(embeddingProductoIbmMapper::toEntity)
                .toList();

        List<EmbeddingProductoIbmJpaEntity> guardados = embeddingProductoIbmJpaRepository.saveAll(entities);

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
    public Optional<EmbeddingProductoIbm> buscarPorProductoYModelo(Long productoIbmId, String modelo) {
        return embeddingProductoIbmJpaRepository.findByProductoIbmIdAndModelo(productoIbmId, modelo)
                .map(embeddingProductoIbmMapper::toDomain);
    }
}