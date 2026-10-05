package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.EmbeddingCandidatoTecEntity;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.CandidatoTecnologicoJpaRepository;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.EmbeddingCandidatoTecnologicoJpaRepository;
import com.ibm.opportunity_analysis_service.application.mapper.CandidatoTecnologicoMapper;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCadidatoPort;
import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EmbeddingCandidatoTecnologicoPersistenceAdapter implements EmbeddingCadidatoPort {

    private final CandidatoTecnologicoJpaRepository candidatoTecnologicoJpaRepository;
    private final EmbeddingCandidatoTecnologicoJpaRepository embeddingCandidatoTecnologicoJpaRepository;
    private final CandidatoTecnologicoMapper candidatoTecnologicoMapper;

    public EmbeddingCandidatoTecnologicoPersistenceAdapter(CandidatoTecnologicoJpaRepository candidatoTecnologicoJpaRepository, EmbeddingCandidatoTecnologicoJpaRepository embeddingCandidatoTecnologicoJpaRepository, CandidatoTecnologicoMapper candidatoTecnologicoMapper) {
        this.candidatoTecnologicoJpaRepository = candidatoTecnologicoJpaRepository;
        this.embeddingCandidatoTecnologicoJpaRepository = embeddingCandidatoTecnologicoJpaRepository;
        this.candidatoTecnologicoMapper = candidatoTecnologicoMapper;
    }

    @Override
    public List<CandidatoTecnologico> obtenerCandidatosTecnologicos() {

        return candidatoTecnologicoJpaRepository.findAll()
                .stream()
                .map(candidatoTecnologicoMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<EmbeddingCandidatoTecnologico> buscarPorOcidYModelo(String ocid, String modelo) {

        return embeddingCandidatoTecnologicoJpaRepository
                .findByOcidAndModelo(ocid, modelo)
                .map(this::toDomain);
    }

    @Override
    public List<EmbeddingCandidatoTecnologico> guardarEmbeddings(List<EmbeddingCandidatoTecnologico> embeddings) {

        List<EmbeddingCandidatoTecEntity> entities = embeddings.stream()
                .map(this::toEntity)
                .toList();

        List<EmbeddingCandidatoTecEntity> guardados =
                embeddingCandidatoTecnologicoJpaRepository.saveAll(entities);

        return guardados.stream()
                .map(this::toDomain)
                .toList();
    }

    private EmbeddingCandidatoTecEntity toEntity(EmbeddingCandidatoTecnologico embedding) {

        EmbeddingCandidatoTecEntity entity = new EmbeddingCandidatoTecEntity();

        entity.setId(embedding.getId());
        entity.setOcid(embedding.getOcid());
        entity.setModelo(embedding.getModelo());
        entity.setTextoEmbedding(embedding.getTextoEmbedding());
        entity.setEmbedding(embedding.getEmbedding());
        entity.setFechaGeneracion(embedding.getFechaGeneracion());

        return entity;
    }

    private EmbeddingCandidatoTecnologico toDomain(EmbeddingCandidatoTecEntity entity) {

        EmbeddingCandidatoTecnologico embedding = new EmbeddingCandidatoTecnologico();

        embedding.setId(entity.getId());
        embedding.setOcid(entity.getOcid());
        embedding.setModelo(entity.getModelo());
        embedding.setTextoEmbedding(entity.getTextoEmbedding());
        embedding.setEmbedding(entity.getEmbedding());
        embedding.setFechaGeneracion(entity.getFechaGeneracion());

        return embedding;
    }
}