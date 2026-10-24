package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;

import java.util.List;
import java.util.Optional;

public interface ProcesoSercopPersistencePort {
    List<ProcesoSercop> obtenerTodos();

    /**
     * Tarea 1.6 (H5): busca un proceso por su OCID sin cargar la tabla entera.
     * Case-insensitive para preservar el comportamiento del filtro en memoria que reemplaza.
     */
    Optional<ProcesoSercop> buscarPorOcid(String ocid);
}
