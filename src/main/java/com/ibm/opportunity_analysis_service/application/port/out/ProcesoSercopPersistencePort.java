package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.springframework.data.domain.Slice;

import java.util.List;

public interface ProcesoSercopPersistencePort {
    Slice<ProcesoSercop> obtenerPagina(int numeroPagina, int tamanoPagina);
    List<ProcesoSercop> obtenerTodos();
}