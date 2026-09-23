package com.ibm.opportunity_analysis_service.adapter.in;

import com.ibm.opportunity_analysis_service.application.service.AnalizarOportunidadesGraniteService;
import com.ibm.opportunity_analysis_service.application.service.FiltrarProcesosSercopService;
import com.ibm.opportunity_analysis_service.application.service.ObtenerProcesosSercopService;
import com.ibm.opportunity_analysis_service.domain.entity.MuestraCategoriaSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoAnalisisGranite;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoFiltroSercop;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AnalysisController {

    private final ObtenerProcesosSercopService service;
    private final FiltrarProcesosSercopService filtrarProcesosSercopService;
    private final AnalizarOportunidadesGraniteService analizarOportunidadesGraniteService;

    public AnalysisController(ObtenerProcesosSercopService service, FiltrarProcesosSercopService filtrarProcesosSercopService, AnalizarOportunidadesGraniteService analizarOportunidadesGraniteService) {
        this.service = service;
        this.filtrarProcesosSercopService = filtrarProcesosSercopService;
        this.analizarOportunidadesGraniteService = analizarOportunidadesGraniteService;
    }

    @GetMapping("/salud")
    public String salud (){
        return "Saludable";
    }

    @GetMapping("/obtener")
    public List<ProcesoSercop> obtenerTodos() {
            return service.obtenerTodos();
        }

    @GetMapping("/filtrar")
    public int filtrarProcesos() {
        return filtrarProcesosSercopService.filtrarProcesos().size();
    }

    @GetMapping("/muestra")
    public List<ResultadoFiltroSercop> obtenerMuestra() {
        return filtrarProcesosSercopService.obtenerMuestraFiltrada(20);
    }

    @GetMapping("/categorias")
    public Map<String, Integer> contarCategorias() {
        return filtrarProcesosSercopService.contarCategorias();
    }


    @GetMapping("/muestras-categorias")
    public Map<String, List<MuestraCategoriaSercop>> obtenerMuestrasPorCategoria() {
        return filtrarProcesosSercopService.obtenerMuestrasPorCategoria(10);
    }

    @GetMapping("/analizar-granite")
    public List<ResultadoAnalisisGranite> analizarGranite() {
        return analizarOportunidadesGraniteService.analizar();
    }


}
