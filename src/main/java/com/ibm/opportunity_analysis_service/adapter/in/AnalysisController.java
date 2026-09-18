package com.ibm.opportunity_analysis_service.adapter.in;

import com.ibm.opportunity_analysis_service.application.service.ObtenerProcesosSercopService;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AnalysisController {

    private final ObtenerProcesosSercopService service;

    public AnalysisController(ObtenerProcesosSercopService service) {
        this.service = service;
    }
        @GetMapping("/salud")
        public String salud (){
        return "Saludable";
    }

        @GetMapping("/obtener")
        public List<ProcesoSercop> obtenerTodos() {
            return service.obtenerTodos();
        }
}
