package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;

import java.util.List;

public interface EmbeddingCadidatoPort {
    CandidatoTecnologico guardarEmbedding(EmbeddingCandidatoTecnologico embeddingCandidatoTecnologico);
    List<CandidatoTecnologico>  obtenerCandidatosTecnologicos();
}
