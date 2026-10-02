package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;

import java.util.Optional;

public interface CandidatoTecnologicoPersistencePort {

    CandidatoTecnologico guardar(CandidatoTecnologico candidatoTecnologico);
    Optional<CandidatoTecnologico> buscarPorOcid(String ocid);

}