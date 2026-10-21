package com.ibm.opportunity_analysis_service.adapter.in;

import com.ibm.opportunity_analysis_service.adapter.in.dto.EstadoPipelineDto;
import com.ibm.opportunity_analysis_service.adapter.in.dto.ResultadoSincronizacionDto;
import com.ibm.opportunity_analysis_service.application.service.AnalizarSimilitudService;
import com.ibm.opportunity_analysis_service.application.service.CargarCatalogoIbmService;
import com.ibm.opportunity_analysis_service.application.service.GenerarEmbeddingCandidatoService;
import com.ibm.opportunity_analysis_service.application.service.GenerarEmbeddingProductosIbmService;
import com.ibm.opportunity_analysis_service.application.service.GenerarEmbeddingService;
import com.ibm.opportunity_analysis_service.application.service.ProcesoSercopPipelineService;
import com.ibm.opportunity_analysis_service.application.service.RecomendarOportunidadesService;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class AnalysisController {

    private final GenerarEmbeddingService generarEmbeddingService;
    private final CargarCatalogoIbmService cargarCatalogoIbmService;
    private final GenerarEmbeddingProductosIbmService generarEmbeddingProductosIbmService;
    private final AnalizarSimilitudService analizarSimilitudService;
    private final GenerarEmbeddingCandidatoService generarEmbeddingCandidatoService;
    private final RecomendarOportunidadesService recomendarOportunidadesService;
    private final com.ibm.opportunity_analysis_service.application.service.ObtenerProcesosSercopService obtenerProcesosSercopService;
    private final ProcesoSercopPipelineService procesoSercopPipelineService;

    public AnalysisController(
            GenerarEmbeddingService generarEmbeddingService,
            CargarCatalogoIbmService cargarCatalogoIbmService,
            GenerarEmbeddingProductosIbmService generarEmbeddingProductosIbmService,
            AnalizarSimilitudService analizarSimilitudService,
            GenerarEmbeddingCandidatoService generarEmbeddingCandidatoService,
            RecomendarOportunidadesService recomendarOportunidadesService,
            com.ibm.opportunity_analysis_service.application.service.ObtenerProcesosSercopService obtenerProcesosSercopService,
            ProcesoSercopPipelineService procesoSercopPipelineService) {
        this.generarEmbeddingService = generarEmbeddingService;
        this.cargarCatalogoIbmService = cargarCatalogoIbmService;
        this.generarEmbeddingProductosIbmService = generarEmbeddingProductosIbmService;
        this.analizarSimilitudService = analizarSimilitudService;
        this.generarEmbeddingCandidatoService = generarEmbeddingCandidatoService;
        this.recomendarOportunidadesService = recomendarOportunidadesService;
        this.obtenerProcesosSercopService = obtenerProcesosSercopService;
        this.procesoSercopPipelineService = procesoSercopPipelineService;
    }

    @GetMapping("/procesos")
    public List<com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop> listarProcesos() {
        return obtenerProcesosSercopService.obtenerTodos();
    }

    /**
     * Endpoint para sincronizar y procesar de forma automática todos los procesos SERCOP.
     * Puede ser llamado por el microservicio sercop_ingestion_service al finalizar la ingesta.
     */
    @PostMapping("/procesos/sincronizar")
    public ResponseEntity<ResultadoSincronizacionDto> sincronizarProcesos() {
        ResultadoSincronizacionDto resultado = procesoSercopPipelineService.sincronizarProcesos();
        return ResponseEntity.ok(resultado);
    }

    /**
     * Devuelve el estado actual y métricas del pipeline y sus componentes.
     */
    @GetMapping("/procesos/estado-pipeline")
    public ResponseEntity<EstadoPipelineDto> obtenerEstadoPipeline() {
        EstadoPipelineDto estado = procesoSercopPipelineService.obtenerEstadoPipeline();
        return ResponseEntity.ok(estado);
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
                "mensaje", "Embeddings de procesos SERCOP generados correctamente"
        );
    }

    @GetMapping("/recomendaciones")
    public List<RecomendacionOportunidad> obtenerOportunidadesIbm(
            @RequestParam(defaultValue = "6") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return recomendarOportunidadesService.obtenerOportunidadesIbm(limit, offset);
    }

    @PostMapping("/recomendaciones/precalcular")
    public Map<String, Object> precalcularRecomendaciones(
            @RequestParam(defaultValue = "50") int topN) {
        int generadas = recomendarOportunidadesService.precalcularRecomendaciones(topN);
        return Map.of(
                "mensaje", "Precálculo completado",
                "nuevasRecomendaciones", generadas,
                "topN", topN
        );
    }

    /**
     * Limpia la caché persistida de recomendaciones para forzar su re-evaluación
     * bajo las nuevas reglas de rigor técnico.
     */
    @PostMapping("/recomendaciones/limpiar-cache")
    public Map<String, Object> limpiarCacheRecomendaciones() {
        recomendarOportunidadesService.limpiarRecomendaciones();
        return Map.of(
                "mensaje", "Caché de recomendaciones eliminada exitosamente. Las próximas consultas se evaluarán con las nuevas reglas."
        );
    }

    @GetMapping("/recomendaciones/{ocid}")
    public ResponseEntity<RecomendacionOportunidad> recomendarOportunidadPorOcid(@PathVariable String ocid) {
        return recomendarOportunidadesService.recomendarPorOcid(ocid)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Devuelve los datos crudos del proceso SERCOP + top 5 candidatos IBM con sus scores
     * vectoriales, sin evaluación. Diseñado para que Bob razone sobre ellos como capa de
     * inteligencia externa.
     */
    @GetMapping("/candidatos/{ocid}")
    public ResponseEntity<com.ibm.opportunity_analysis_service.adapter.in.dto.CandidatosRecomendacionDto> obtenerCandidatosCrudos(
            @PathVariable String ocid) {
        return recomendarOportunidadesService.obtenerCandidatosCrudos(ocid)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
