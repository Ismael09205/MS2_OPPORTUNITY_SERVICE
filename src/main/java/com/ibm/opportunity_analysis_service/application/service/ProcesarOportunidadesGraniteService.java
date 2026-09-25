package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.GranitePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoAnalisisGranite;
import org.springframework.data.domain.Slice;
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

        List<ResultadoAnalisisGranite> resultados = new ArrayList<>();
        List<ProcesoSercop> lote = new ArrayList<>();

        int tamanoPagina = configuracionGranite.getTamanoLote();
        int numeroPagina = 0;

        while (true) {

            Slice<ProcesoSercop> pagina = filtrarProcesosSercopService.filtrarPagina(numeroPagina, tamanoPagina);

            for (ProcesoSercop proceso : pagina.getContent()) {

                lote.add(proceso);

                if (lote.size() >= tamanoPagina) {

                    procesarLote(lote, resultados);
                    lote.clear();
                }
            }

            if (!pagina.hasNext()) {
                break;
            }

            numeroPagina++;
        }

        if (!lote.isEmpty()) {
            procesarLote(lote, resultados);
        }

        return resultados;
    }

    private void procesarLote(List<ProcesoSercop> lote, List<ResultadoAnalisisGranite> resultados) {

        List<ResultadoAnalisisGranite> resultadoLote = granitePort.analizar(lote);

        validarResultados(lote, resultadoLote);

        resultados.addAll(resultadoLote);
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