package com.ibm.opportunity_analysis_service.adapter.in;

import com.ibm.opportunity_analysis_service.application.service.*;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
        private final GenerarEmbeddingProductosIbmService generarEmbeddingProductosIbmService;
        private final AnalizarSimilitudService analizarSimilitudService;
    private final GenerarEmbeddingCandidatoService generarEmbeddingCandidatoService;

    public AnalysisController(ObtenerProcesosSercopService service, FiltrarProcesosSercopService filtrarProcesosSercopService, GenerarEmbeddingService generarEmbeddingService, CalcularSimilitudService calcularSimilitudService, CargarCatalogoIbmService cargarCatalogoIbmService, GenerarEmbeddingProductosIbmService generarEmbeddingProductosIbmService, AnalizarSimilitudService analizarSimilitudService, GenerarEmbeddingCandidatoService generarEmbeddingCandidatoService) {
        this.service = service;
        this.filtrarProcesosSercopService = filtrarProcesosSercopService;
        this.generarEmbeddingService = generarEmbeddingService;
        this.calcularSimilitudService = calcularSimilitudService;
        this.cargarCatalogoIbmService = cargarCatalogoIbmService;
        this.generarEmbeddingProductosIbmService = generarEmbeddingProductosIbmService;
        this.analizarSimilitudService = analizarSimilitudService;
        this.generarEmbeddingCandidatoService = generarEmbeddingCandidatoService;
    }
    @PostMapping("/similitud")
    public Map<String, Object> analizarSimilitud() {

        analizarSimilitudService.analizar();

        return Map.of(
                "mensaje",
                "Similitudes calculadas correctamente"
        );
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
    @PostMapping("/embeddings/productos-ibm")
    public List<EmbeddingProductoIbm> generarEmbeddingsProductosIbm() {
        return generarEmbeddingProductosIbmService.generarTodos();
    }
    @GetMapping("/cargar-catalogo-ibm")
    public Map<String, Object> cargarCatalogoIbm() throws IOException {

        List<ProductoIbm> productos = cargarCatalogoIbmService.cargar();

        return Map.of(
                "mensaje", "Catalogo IBM cargado correctamente",
                "totalProductos", productos.size()
        );
    }

    @PostMapping("/embeddings/candidatos")
    public Map<String, Object> generarEmbeddingsCandidatos() {

        generarEmbeddingCandidatoService.generarEmbeddings();

        return Map.of(
                "mensaje", "Embeddings de candidatos generados correctamente"
        );
    }
    }


