package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.ProcesoSercopPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ObtenerProcesosSercopService {

    private final ProcesoSercopPersistencePort persistencePort;

    public ObtenerProcesosSercopService(ProcesoSercopPersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    public List<ProcesoSercop> obtenerTodos() {
        return persistencePort.obtenerTodos();
    }

    /**
     * Tarea 1.6 (H5): lookup por OCID sin cargar toda la tabla.
     * Sustituye los tres {@code obtenerTodos() + stream().filter(...)} del servicio de
     * recomendaciones, que hacían latencia O(n) por request y rompían la promesa de 1-2 s.
     */
    public Optional<ProcesoSercop> buscarPorOcid(String ocid) {
        return persistencePort.buscarPorOcid(ocid);
    }
}
