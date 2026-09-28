package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.CandidatoTecnologicoPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class GuardarCandidatoTecnologicoService {

    private final CandidatoTecnologicoPersistencePort persistencePort;

    public GuardarCandidatoTecnologicoService(CandidatoTecnologicoPersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    public CandidatoTecnologico guardar(CandidatoTecnologico candidatoTecnologico) {

        return persistencePort.buscarPorOcid(candidatoTecnologico.getOcid())
                .map(candidatoExistente -> {
                    candidatoExistente.setTitulo(candidatoTecnologico.getTitulo());
                    candidatoExistente.setDescripcion(candidatoTecnologico.getDescripcion());
                    candidatoExistente.setFechaDeteccion(OffsetDateTime.now());
                    candidatoExistente.setEstado(candidatoTecnologico.getEstado());

                    return persistencePort.guardar(candidatoExistente);
                })
                .orElseGet(() -> persistencePort.guardar(candidatoTecnologico));
    }
}