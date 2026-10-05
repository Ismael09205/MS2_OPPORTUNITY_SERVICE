package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;

import java.util.List;
import java.util.Optional;

public interface EmbeddingCadidatoPort {

    List<CandidatoTecnologico> obtenerCandidatosTecnologicos();

    Optional<EmbeddingCandidatoTecnologico> buscarPorOcidYModelo(String ocid, String modelo);

    List<EmbeddingCandidatoTecnologico> guardarEmbeddings(List<EmbeddingCandidatoTecnologico> embeddings);
}