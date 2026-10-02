package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.EmbeddingCandidatoTecnologicoJpaRepository;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCadidatoPort;
import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmbeddingCandidatoTecnologicoPersistenceAdapter implements EmbeddingCadidatoPort {
    private EmbeddingCandidatoTecnologicoJpaRepository jpaRepository;
    private CandidatoTecnologico candidatoTecnologico;

    public EmbeddingCandidatoTecnologicoPersistenceAdapter(EmbeddingCandidatoTecnologicoJpaRepository jpaRepository, CandidatoTecnologico candidatoTecnologico) {
        this.jpaRepository = jpaRepository;
        this.candidatoTecnologico = candidatoTecnologico;
    }

    @Override
    public CandidatoTecnologico guardarEmbedding(EmbeddingCandidatoTecnologico embeddingCandidatoTecnologico) {
        jpaRepository.save(embeddingCandidatoTecnologico);
        return null;
    }

    @Override
    public List<CandidatoTecnologico> obtenerCandidatosTecnologicos() {
        List<CandidatoTecnologico> candidatos =jpaRepository.findAll();
        if(candidatos.isEmpty()){
            System.out.println("No existen candidatos persistidos");
        }

        return candidatos;
    }
}
