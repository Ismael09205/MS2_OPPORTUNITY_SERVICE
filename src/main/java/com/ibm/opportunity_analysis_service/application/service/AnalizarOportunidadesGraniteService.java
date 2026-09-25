package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.GranitePort;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoAnalisisGranite;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnalizarOportunidadesGraniteService {

    private static final int TAMANO_PAGINA = 100;
    private static final int CANTIDAD_PROCESOS = 10;

    private final FiltrarProcesosSercopService filtrarProcesosSercopService;
    private final GranitePort granitePort;

    public AnalizarOportunidadesGraniteService(FiltrarProcesosSercopService filtrarProcesosSercopService, GranitePort granitePort) {
        this.filtrarProcesosSercopService = filtrarProcesosSercopService;
        this.granitePort = granitePort;
    }

    public List<ResultadoAnalisisGranite> analizar() {

        List<ProcesoSercop> procesos = new ArrayList<>();
        int numeroPagina = 0;

        while (procesos.size() < CANTIDAD_PROCESOS) {

            Slice<ProcesoSercop> pagina = filtrarProcesosSercopService.filtrarPagina(numeroPagina, TAMANO_PAGINA);

            for (ProcesoSercop proceso : pagina.getContent()) {

                if (procesos.size() >= CANTIDAD_PROCESOS) {
                    break;
                }

                procesos.add(proceso);
            }

            if (!pagina.hasNext()) {
                break;
            }

            numeroPagina++;
        }

        return granitePort.analizar(procesos);
    }
}