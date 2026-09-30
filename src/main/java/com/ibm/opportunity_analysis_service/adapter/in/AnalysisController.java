package com.ibm.opportunity_analysis_service.adapter.in;

import com.ibm.opportunity_analysis_service.application.service.*;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoAnalisisGranite;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoFiltroSercop;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
    public class AnalysisController {

        private final ObtenerProcesosSercopService service;
        private final FiltrarProcesosSercopService filtrarProcesosSercopService;
        private final GenerarEmbeddingService generarEmbeddingService;
        private final CalcularSimilitudService calcularSimilitudService;
        private final CargarCatalogoIbmService cargarCatalogoIbmService;

    public AnalysisController(ObtenerProcesosSercopService service, FiltrarProcesosSercopService filtrarProcesosSercopService, GenerarEmbeddingService generarEmbeddingService, CalcularSimilitudService calcularSimilitudService, CargarCatalogoIbmService cargarCatalogoIbmService) {
        this.service = service;
        this.filtrarProcesosSercopService = filtrarProcesosSercopService;
        this.generarEmbeddingService = generarEmbeddingService;
        this.calcularSimilitudService = calcularSimilitudService;
        this.cargarCatalogoIbmService = cargarCatalogoIbmService;
    }

    @GetMapping("/muestra")
        public List<ResultadoFiltroSercop> obtenerMuestra() {
        return filtrarProcesosSercopService.obtenerMuestraFiltrada(20, 100);
        }

        @GetMapping("/prueba-embedding")
        public Map<String, Object> probarEmbedding(@RequestParam String texto) {
            List<Float> embedding = generarEmbeddingService.generar(texto);

            return Map.of(
                    "texto", texto,
                    "dimensiones", embedding.size(),
                    "embedding", embedding
            );
        }


        @GetMapping("/oportunidad-tecnologica")
        public Map<String, Object> persistirCandidatosTecnologicos() {
        int totalCandidatos = filtrarProcesosSercopService.filtrarYPersistirCandidatos(100);
        return Map.of(
                "totalCandidatosProcesados", totalCandidatos
        );
    }
    @GetMapping("/prueba-similitud")
    public List<Map<String, Object>> probarSimilitud() {

        String textoSercop = "Servicio de desarrollo e implementación de una plataforma de software para gestión empresarial";

        List<String> textosIbm = List.of(
                "Plataforma de software para automatización y desarrollo de aplicaciones empresariales",
                "Soluciones de infraestructura tecnológica y servicios de computación en la nube",
                "Orden de compra para adquirir neumáticos para vehículos todo terreno",
                "Servicio de limpieza y mantenimiento de instalaciones",
                "Soluciones de inteligencia artificial para automatización y análisis de datos"
        );

        List<Float> embeddingSercop = generarEmbeddingService.generar(textoSercop);

        List<Map<String, Object>> resultados = textosIbm.stream()
                .map(textoIbm -> {
                    List<Float> embeddingIbm = generarEmbeddingService.generar(textoIbm);

                    double similitud = calcularSimilitudService.calcular(embeddingSercop, embeddingIbm);

                    return Map.<String, Object>of(
                            "textoIbm", textoIbm,
                            "dimensiones", embeddingIbm.size(),
                            "similitudCoseno", similitud
                    );
                })
                .toList();

        return resultados;
    }
    @GetMapping("/cargar-catalogo-ibm")
    public Map<String, Object> cargarCatalogoIbm() throws IOException {

        List<ProductoIbm> productos = cargarCatalogoIbmService.cargar();

        return Map.of(
                "mensaje", "Catalogo IBM cargado correctamente",
                "totalProductos", productos.size()
        );
    }
    }


