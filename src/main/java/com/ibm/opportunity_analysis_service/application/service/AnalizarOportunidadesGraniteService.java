package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.GranitePort;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoAnalisisGranite;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalizarOportunidadesGraniteService {

    private final FiltrarProcesosSercopService filtrarProcesosSercopService;
    private final GranitePort granitePort;

    public AnalizarOportunidadesGraniteService(FiltrarProcesosSercopService filtrarProcesosSercopService, GranitePort granitePort) {
        this.filtrarProcesosSercopService = filtrarProcesosSercopService;
        this.granitePort = granitePort;
    }

    public List<ResultadoAnalisisGranite> analizar() {

        List<ProcesoSercop> procesos = filtrarProcesosSercopService.filtrarProcesos();

        List<ProcesoSercop> lote = procesos.stream()
                .limit(10)
                .toList();

        return granitePort.analizar(lote);
    }
}