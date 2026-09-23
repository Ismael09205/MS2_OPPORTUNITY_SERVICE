package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.GranitePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoAnalisisGranite;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProcesarOportunidadesGraniteService {

    private final FiltrarProcesosSercopService filtrarProcesosSercopService;
    private final GranitePort granitePort;
    private final ConfiguracionGranite configuracionGranite;

    public ProcesarOportunidadesGraniteService(FiltrarProcesosSercopService filtrarProcesosSercopService, GranitePort granitePort, ConfiguracionGranite configuracionGranite) {
        this.filtrarProcesosSercopService = filtrarProcesosSercopService;
        this.granitePort = granitePort;
        this.configuracionGranite = configuracionGranite;
    }

    public List<ResultadoAnalisisGranite> procesar() {

        List<ProcesoSercop> procesos = filtrarProcesosSercopService.filtrarProcesos();

        List<ResultadoAnalisisGranite> resultados = new ArrayList<>();

        int tamanoLote = configuracionGranite.getTamanoLote();

        for (int inicio = 0; inicio < procesos.size(); inicio += tamanoLote) {

            int fin = Math.min(inicio + tamanoLote, procesos.size());
            List<ProcesoSercop> lote = procesos.subList(inicio, fin);
            List<ResultadoAnalisisGranite> resultadoLote = granitePort.analizar(lote);
            validarResultados(lote, resultadoLote);
            resultados.addAll(resultadoLote);
        }

        return resultados;
    }

    private void validarResultados(List<ProcesoSercop> lote, List<ResultadoAnalisisGranite> resultados) {

        if (resultados.size() != lote.size()) {
            throw new IllegalStateException(
                    "Granite devolvió " + resultados.size() +
                            " resultados, pero se enviaron " + lote.size() + " procesos."
            );
        }
    }
}