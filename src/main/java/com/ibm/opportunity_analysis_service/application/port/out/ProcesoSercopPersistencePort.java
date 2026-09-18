package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;

import java.util.List;

public interface ProcesoSercopPersistencePort {
    List<ProcesoSercop> obtenerTodos();
}