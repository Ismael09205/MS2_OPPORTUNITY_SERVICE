package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.ProcesoSercopPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ObtenerProcesosSercopService {

    private final ProcesoSercopPersistencePort persistencePort;

    public ObtenerProcesosSercopService(ProcesoSercopPersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    public List<ProcesoSercop> obtenerTodos() {
        return persistencePort.obtenerTodos();
    }
}