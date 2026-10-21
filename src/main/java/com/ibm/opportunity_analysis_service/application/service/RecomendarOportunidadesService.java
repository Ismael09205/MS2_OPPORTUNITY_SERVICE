package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.adapter.in.dto.CandidatosRecomendacionDto;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCandidatoPort;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.EvaluadorOportunidadLlmPort;
import com.ibm.opportunity_analysis_service.application.port.out.ProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.RecomendacionPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.SimilitudPersistencePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoSimilitudIbm;
import com.ibm.opportunity_analysis_service.domain.entity.SimilitudProcesoProducto;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RecomendarOportunidadesService {

    private final ObtenerProcesosSercopService obtenerProcesosSercopService;
    private final EmbeddingCandidatoPort embeddingCandidatoPort;
    private final GenerarEmbeddingCandidatoService generarEmbeddingCandidatoService;
    private final EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort;
    private final ProductoIbmPersistencePort productoIbmPersistencePort;
    private final EvaluadorOportunidadLlmPort evaluadorOportunidadLlmPort;
    private final SimilitudPersistencePort similitudPersistencePort;
    private final RecomendacionPersistencePort recomendacionPersistencePort;
    private final ValidadorAlcanceIbmService validadorAlcanceIbmService;
    private final ConfiguracionGranite configuracionGranite;

    public RecomendarOportunidadesService(
            ObtenerProcesosSercopService obtenerProcesosSercopService,
            EmbeddingCandidatoPort embeddingCandidatoPort,
            GenerarEmbeddingCandidatoService generarEmbeddingCandidatoService,
            EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort,
            ProductoIbmPersistencePort productoIbmPersistencePort,
            EvaluadorOportunidadLlmPort evaluadorOportunidadLlmPort,
            SimilitudPersistencePort similitudPersistencePort,
            RecomendacionPersistencePort recomendacionPersistencePort,
            ValidadorAlcanceIbmService validadorAlcanceIbmService,
            ConfiguracionGranite configuracionGranite) {
        this.obtenerProcesosSercopService = obtenerProcesosSercopService;
        this.embeddingCandidatoPort = embeddingCandidatoPort;
        this.generarEmbeddingCandidatoService = generarEmbeddingCandidatoService;
        this.embeddingProductoIbmPersistencePort = embeddingProductoIbmPersistencePort;
        this.productoIbmPersistencePort = productoIbmPersistencePort;
        this.evaluadorOportunidadLlmPort = evaluadorOportunidadLlmPort;
        this.similitudPersistencePort = similitudPersistencePort;
        this.recomendacionPersistencePort = recomendacionPersistencePort;
        this.validadorAlcanceIbmService = validadorAlcanceIbmService;
        this.configuracionGranite = configuracionGranite;
    }

    /**
     * Devuelve las siguientes `limit` oportunidades IBM reales paginadas por offset.
     *
     * Flujo Instantáneo No Bloqueante:
     * 1. Lee de DB las recomendaciones ya generadas con esOportunidadIbm=true (cache hit, instantáneo).
     * 2. Si faltan registros para cubrir el offset+limit, genera preselecciones rápidas
     *    directamente desde la matriz vectorial (cero espera / sin timeouts de red).
     * 3. La evaluación profunda y exhaustiva con LLM se reserva para cuando el usuario
     *    profundiza en un OCID específico (o mediante precálculo en background).
     */
    public List<RecomendacionOportunidad> obtenerOportunidadesIbm(int limit, int offset) {
        List<RecomendacionOportunidad> enCache = recomendacionPersistencePort.obtenerOportunidadesIbm(limit, offset);

        if (enCache.size() >= limit) {
            return enCache;
        }

        // Cache miss parcial: generamos preselecciones rápidas instantáneas desde la matriz vectorial
        int batchSize = limit * 3;
        List<String> pendientes = similitudPersistencePort.obtenerSiguientesAEvaluar(batchSize);

        for (String ocid : pendientes) {
            try {
                generarRecomendacionRapidaVectorial(ocid);
            } catch (Exception e) {
                log.error("Fallo al preseleccionar rápidamente OCID {}: {}", ocid, e.getMessage());
            }
        }

        // Releer de DB tras la generación instantánea
        return recomendacionPersistencePort.obtenerOportunidadesIbm(limit, offset);
    }

    private Optional<RecomendacionOportunidad> generarRecomendacionRapidaVectorial(String ocid) {
        List<ProcesoSercop> procesos = obtenerProcesosSercopService.obtenerTodos();
        Optional<ProcesoSercop> procesoOpt = procesos.stream()
                .filter(p -> ocid.equalsIgnoreCase(p.getOcid()))
                .findFirst();

        if (procesoOpt.isEmpty()) {
            return Optional.empty();
        }

        ProcesoSercop proceso = procesoOpt.get();
        List<SimilitudProcesoProducto> similitudes = similitudPersistencePort.buscarPorOcid(ocid);

        if (similitudes.isEmpty()) {
            return generarYPersistirRecomendacion(ocid, null);
        }

        SimilitudProcesoProducto mejor = similitudes.stream()
                .max(java.util.Comparator.comparingDouble(SimilitudProcesoProducto::getScoreCompuesto))
                .orElse(similitudes.get(0));

        double maxScore = similitudes.stream().mapToDouble(SimilitudProcesoProducto::getScoreVectorial).max().orElse(0.0);
        double minScore = similitudes.stream().mapToDouble(SimilitudProcesoProducto::getScoreVectorial).min().orElse(0.0);
        double delta = maxScore - minScore;

        String nivelConfianza;
        if (maxScore >= 0.75 && delta >= 0.04) {
            nivelConfianza = "ALTO";
        } else if (maxScore >= 0.68 || delta >= 0.02) {
            nivelConfianza = "MEDIO";
        } else {
            nivelConfianza = "BAJO";
        }

        RecomendacionOportunidad rec = new RecomendacionOportunidad();
        rec.setOcid(proceso.getOcid());
        rec.setTitulo(proceso.getTitulo() != null ? proceso.getTitulo() : "Sin título");
        rec.setDescripcion(proceso.getDescripcion() != null ? proceso.getDescripcion() : "");
        rec.setComprador(proceso.getComprador() != null ? proceso.getComprador() : "Entidad no especificada");
        rec.setMontoReferencial(proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto());
        rec.setEsOportunidadIbm(true);
        rec.setProductoRecomendado(mejor.getNombreProducto());
        rec.setNivelConfianza(nivelConfianza);
        rec.setJustificacion(String.format("Preselección por afinidad vectorial (Score: %.4f, Delta: %.4f).", maxScore, delta));
        rec.setEstrategiaComercial("Consultar detalle del caso para análisis profundo.");

        recomendacionPersistencePort.guardarOActualizar(rec, mejor.getScoreCompuesto());
        return Optional.of(rec);
    }

    /**
     * Precalcula y persiste las recomendaciones LLM para los top N procesos por score compuesto.
     * Operacion offline opcional: util para warmup inicial o cuando llegan muchos procesos nuevos.
     */
    public int precalcularRecomendaciones(int topN) {
        List<String> pendientes = similitudPersistencePort.obtenerSiguientesAEvaluar(topN);

        if (pendientes.isEmpty()) {
            return 0;
        }

        for (String ocid : pendientes) {
            try {
                generarYPersistirRecomendacion(ocid, null);
            } catch (Exception e) {
                log.error("Fallo aislado al precalcular recomendación para OCID {}: {}", ocid, e.getMessage());
            }
        }
        return pendientes.size();
    }

    /**
     * Versión asíncrona no bloqueante para precalcular en segundo plano sin detener flujos de sincronización.
     */
    @org.springframework.scheduling.annotation.Async
    public java.util.concurrent.CompletableFuture<Integer> precalcularRecomendacionesAsync(int topN) {
        log.info("Iniciando evaluación asíncrona priorizada de top {} oportunidades con Granite...", topN);
        int generadas = precalcularRecomendaciones(topN);
        log.info("Evaluación asíncrona completada: {} oportunidades procesadas.", generadas);
        return java.util.concurrent.CompletableFuture.completedFuture(generadas);
    }

    public void limpiarRecomendaciones() {
        recomendacionPersistencePort.eliminarTodas();
    }

    /**
     * Recomendación on-demand para un OCID concreto.
     * Respeta el mismo patrón lazy: devuelve cache si existe, genera si no.
     */
    public Optional<RecomendacionOportunidad> recomendarPorOcid(String ocid) {
        Optional<RecomendacionOportunidad> cached = recomendacionPersistencePort.buscarPorOcid(ocid);

        // Si ya fue evaluada a fondo por el LLM (no es solo preselección preliminar)
        if (cached.isPresent() && !cached.get().getJustificacion().startsWith("Preselección por afinidad vectorial")) {
            return cached;
        }

        // Ejecutar evaluación profunda con LLM para este OCID individual
        return generarYPersistirRecomendacion(ocid, null);
    }

    private Optional<RecomendacionOportunidad> generarYPersistirRecomendacion(
            String ocid, List<SimilitudProcesoProducto> similitudesPreCargadas) {

        List<ProcesoSercop> procesos = obtenerProcesosSercopService.obtenerTodos();
        Optional<ProcesoSercop> procesoOpt = procesos.stream()
                .filter(p -> ocid.equalsIgnoreCase(p.getOcid()))
                .findFirst();

        if (procesoOpt.isEmpty()) {
            return Optional.empty();
        }

        ProcesoSercop proceso = procesoOpt.get();

        // Validar primero si el proceso entra en el alcance corporativo de IBM
        var validacion = validadorAlcanceIbmService.validar(proceso);
        if (!validacion.isEsElegible()) {
            RecomendacionOportunidad rec = new RecomendacionOportunidad();
            rec.setOcid(proceso.getOcid());
            rec.setTitulo(proceso.getTitulo() != null ? proceso.getTitulo() : "Sin título");
            rec.setDescripcion(proceso.getDescripcion() != null ? proceso.getDescripcion() : "");
            rec.setComprador(proceso.getComprador() != null ? proceso.getComprador() : "No especificado");
            rec.setMontoReferencial(proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto());
            rec.setEsOportunidadIbm(false);
            rec.setProductoRecomendado(null);
            rec.setNivelConfianza("N/A");
            rec.setJustificacion(validacion.getMotivoDescarte());
            rec.setEstrategiaComercial("Descartado: El requerimiento se encuentra fuera del alcance del portafolio tecnológico corporativo de IBM.");
            recomendacionPersistencePort.guardarOActualizar(rec, 0.0);
            return Optional.of(rec);
        }

        String modelo = configuracionGranite.getModelo();

        // Obtener top similitudes para este OCID (de las pre-cargadas o consultando la DB)
        List<SimilitudProcesoProducto> similitudesDelOcid;
        if (similitudesPreCargadas != null) {
            similitudesDelOcid = similitudesPreCargadas.stream()
                    .filter(s -> ocid.equalsIgnoreCase(s.getOcid()))
                    .toList();
        } else {
            similitudesDelOcid = similitudPersistencePort.buscarPorOcid(ocid);
        }

        // Si no hay similitudes persistidas, calcularlas en el momento (caso on-demand)
        List<ResultadoSimilitudIbm> topSimilares;
        if (similitudesDelOcid.isEmpty()) {
            EmbeddingCandidatoTecnologico embeddingObj =
                    embeddingCandidatoPort.buscarPorOcidYModelo(ocid, modelo)
                            .orElseGet(() -> generarEmbeddingCandidatoService.generarOActualizarEmbedding(proceso));

            topSimilares = embeddingProductoIbmPersistencePort.buscarSimilares(
                    embeddingObj.getEmbedding(), 3);
        } else {
            topSimilares = similitudesDelOcid.stream()
                    .map(s -> new ResultadoSimilitudIbm(
                            s.getProductoIbmId(), s.getNombreProducto(), s.getScoreVectorial()))
                    .toList();
        }

        List<ProductoIbm> candidatosTop = topSimilares.stream()
                .map(sim -> productoIbmPersistencePort.buscarPorId(sim.getProductoIbmId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<Double> scoresVectoriales = topSimilares.stream()
                .map(ResultadoSimilitudIbm::getSimilitud)
                .toList();

        RecomendacionOportunidad recomendacion;
        try {
            recomendacion = evaluadorOportunidadLlmPort.evaluarOportunidad(proceso, candidatosTop, scoresVectoriales);
        } catch (Exception e) {
            log.error("Error al evaluar con LLM para OCID {}: {}. Usando fallback.", ocid, e.getMessage());
            recomendacion = new RecomendacionOportunidad();
            recomendacion.setEsOportunidadIbm(!candidatosTop.isEmpty());
            recomendacion.setProductoRecomendado(!candidatosTop.isEmpty() ? candidatosTop.get(0).getNombre() : null);
            recomendacion.setNivelConfianza("BAJO");
            recomendacion.setJustificacion("Evaluación fallback por error en el LLM.");
        }

        recomendacion.setOcid(proceso.getOcid());
        recomendacion.setTitulo(proceso.getTitulo() != null ? proceso.getTitulo() : "Sin título");
        recomendacion.setDescripcion(proceso.getDescripcion() != null ? proceso.getDescripcion() : "");
        recomendacion.setComprador(proceso.getComprador() != null ? proceso.getComprador() : "Entidad no especificada");
        recomendacion.setMontoReferencial(
                proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto());

        // Tomar el score compuesto del mejor candidato para este OCID
        double scoreCompuesto = similitudesDelOcid.stream()
                .mapToDouble(SimilitudProcesoProducto::getScoreCompuesto)
                .max()
                .orElse(topSimilares.isEmpty() ? 0.0 : topSimilares.get(0).getSimilitud());

        recomendacionPersistencePort.guardarOActualizar(recomendacion, scoreCompuesto);

        return Optional.of(recomendacion);
    }

    /**
     * Devuelve los datos crudos del proceso + top candidatos IBM con sus scores vectoriales,
     * sin ninguna evaluación. Pensado para que Bob (u otro evaluador externo) razone sobre ellos.
     */
    public Optional<CandidatosRecomendacionDto> obtenerCandidatosCrudos(String ocid) {
        List<ProcesoSercop> procesos = obtenerProcesosSercopService.obtenerTodos();
        Optional<ProcesoSercop> procesoOpt = procesos.stream()
                .filter(p -> ocid.equalsIgnoreCase(p.getOcid()))
                .findFirst();

        if (procesoOpt.isEmpty()) {
            return Optional.empty();
        }

        ProcesoSercop proceso = procesoOpt.get();
        String modelo = configuracionGranite.getModelo();

        EmbeddingCandidatoTecnologico embeddingObj =
                embeddingCandidatoPort.buscarPorOcidYModelo(proceso.getOcid(), modelo)
                        .orElseGet(() -> generarEmbeddingCandidatoService.generarOActualizarEmbedding(proceso));

        List<ResultadoSimilitudIbm> topSimilares =
                embeddingProductoIbmPersistencePort.buscarSimilares(
                        embeddingObj.getEmbedding(),
                        5
                );

        List<CandidatosRecomendacionDto.CandidatoConDetalle> candidatos = topSimilares.stream()
                .map(sim -> productoIbmPersistencePort.buscarPorId(sim.getProductoIbmId())
                        .map(p -> new CandidatosRecomendacionDto.CandidatoConDetalle(
                                p.getId(),
                                p.getNombre(),
                                p.getDescripcion(),
                                p.getCapacidades(),
                                p.getTecnologias(),
                                sim.getSimilitud()
                        ))
                        .orElse(null))
                .filter(c -> c != null)
                .toList();

        return Optional.of(new CandidatosRecomendacionDto(proceso, candidatos));
    }

}
