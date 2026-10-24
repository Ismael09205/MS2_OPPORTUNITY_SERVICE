package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.adapter.in.dto.CandidatosRecomendacionDto;
import com.ibm.opportunity_analysis_service.adapter.in.dto.PaginaRecomendacionesDto;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCandidatoPort;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.EvaluadorOportunidadLlmPort;
import com.ibm.opportunity_analysis_service.application.port.out.ProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.RecomendacionPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.SimilitudPersistencePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.application.util.PoliticaReintento;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.EstadoRecomendacion;
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
    private final ReglasDurasOportunidadService reglasDurasOportunidadService;
    private final ConfiguracionGranite configuracionGranite;
    /**
     * Executor del contenedor para la tarea 1.8: la evaluación LLM no puede correr en el hilo del
     * request (hasta 90 s por OCID), y una llamada interna con {@code @Async} no pasaría por el
     * proxy de Spring, así que se usa explícitamente.
     */
    private final org.springframework.core.task.TaskExecutor taskExecutor;

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
            ReglasDurasOportunidadService reglasDurasOportunidadService,
            ConfiguracionGranite configuracionGranite,
            @org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor")
            org.springframework.core.task.TaskExecutor taskExecutor) {
        this.obtenerProcesosSercopService = obtenerProcesosSercopService;
        this.embeddingCandidatoPort = embeddingCandidatoPort;
        this.generarEmbeddingCandidatoService = generarEmbeddingCandidatoService;
        this.embeddingProductoIbmPersistencePort = embeddingProductoIbmPersistencePort;
        this.productoIbmPersistencePort = productoIbmPersistencePort;
        this.evaluadorOportunidadLlmPort = evaluadorOportunidadLlmPort;
        this.similitudPersistencePort = similitudPersistencePort;
        this.recomendacionPersistencePort = recomendacionPersistencePort;
        this.validadorAlcanceIbmService = validadorAlcanceIbmService;
        this.reglasDurasOportunidadService = reglasDurasOportunidadService;
        this.configuracionGranite = configuracionGranite;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Devuelve las siguientes `limit` oportunidades IBM reales paginadas por offset.
     *
     * Fase 1, tareas 1.7 y 1.8 (decisión A): la lista contiene SOLO filas estado=EVALUADA.
     * Ante un cache miss ya no se devuelven preselecciones como si fueran veredictos:
     *  1. se rellenan preselecciones rápidas (sin red, con validador — tarea 1.3) para que sean
     *     visibles en `GET /recomendaciones/pendientes`;
     *  2. se dispara la evaluación profunda con LLM en segundo plano (no bloquea la respuesta);
     *  3. se devuelve lo disponible + `pendientesCount`, para que una lista corta se explique
     *     por sí sola en vez de parecer que no hay oportunidades.
     */
    public PaginaRecomendacionesDto obtenerOportunidadesIbm(int limit, int offset) {
        List<RecomendacionOportunidad> evaluadas = recomendacionPersistencePort.obtenerOportunidadesIbm(limit, offset);

        if (evaluadas.size() < limit) {
            int batchSize = limit * 3;
            List<String> sinFila = similitudPersistencePort.obtenerSiguientesAEvaluar(batchSize);

            for (String ocid : sinFila) {
                try {
                    generarRecomendacionRapidaVectorial(ocid);
                } catch (Exception e) {
                    log.error("Fallo al preseleccionar rápidamente OCID {}: {}", ocid, e.getMessage());
                }
            }

            // Tarea 1.8: la evaluación LLM pasa a segundo plano; la request nunca espera a Ollama.
            long porEvaluar = similitudPersistencePort.contarPendientesDeEvaluacion();
            if (porEvaluar > 0) {
                evaluarEnSegundoPlano(batchSize);
            }
        }

        long pendientesCount = similitudPersistencePort.contarPendientesDeEvaluacion();
        return new PaginaRecomendacionesDto(evaluadas, limit, offset, pendientesCount);
    }

    /**
     * Tarea 1.7 (decisión A): preselecciones y errores de evaluación, expuestos aparte de la
     * lista principal para que el usuario (y el MCP) sepan qué está pendiente de juicio profundo.
     */
    public List<RecomendacionOportunidad> obtenerPendientes(int limit, int offset) {
        return recomendacionPersistencePort.obtenerPendientes(limit, offset);
    }

    /**
     * Cola humana de revisión (plan REVISAR): casos donde el LLM se abstuvo, con la causa del
     * modelo en justificacion y el orden de trabajo (abstención más antigua primero).
     */
    public List<RecomendacionOportunidad> obtenerColaRevision(int limit, int offset) {
        return recomendacionPersistencePort.obtenerColaRevision(limit, offset);
    }

    /**
     * Resolución humana de un caso de la cola REQUIERE_REVISION (herramienta MCP de Bob).
     * El revisor decide el veredicto definitivo: SI con nivel ALTO/MEDIO (queda EVALUADA y entra
     * a la lista principal) o NO (queda DESCARTADA_ALCANCE). La fila sale de la cola y de la
     * Opción C (se limpia la firma de información).
     *
     * @return la fila resuelta, o vacío si el OCID no está en la cola de revisión.
     * @throws IllegalArgumentException si la decisión es desconocida o SI sin nivel ALTO/MEDIO
     *         (el CHECK de BD exige una confianza que acredite la oportunidad).
     */
    public Optional<RecomendacionOportunidad> resolverRevision(String ocid, String decision, String nivel) {
        Optional<RecomendacionOportunidad> cached = recomendacionPersistencePort.buscarPorOcid(ocid);
        if (cached.isEmpty() || cached.get().getEstado() != EstadoRecomendacion.REQUIERE_REVISION) {
            return Optional.empty();
        }

        RecomendacionOportunidad rec = cached.get();
        String causaOriginal = rec.getJustificacion() != null ? rec.getJustificacion() : "";
        rec.setFirmaInfoRevision(null);
        rec.setFechaEvaluacion(java.time.OffsetDateTime.now());

        if ("SI".equalsIgnoreCase(decision)) {
            String n = nivel != null ? nivel.trim().toUpperCase(java.util.Locale.ROOT) : "";
            if (!"ALTO".equals(n) && !"MEDIO".equals(n)) {
                throw new IllegalArgumentException(
                        "Para decision=SI el nivel debe ser ALTO o MEDIO; recibido: " + nivel);
            }
            rec.setEsOportunidadIbm(true);
            rec.setNivelConfianza(n);
            rec.setEstado(EstadoRecomendacion.EVALUADA);
            rec.setJustificacion(causaOriginal + " [Resuelto por revisión humana: SI (" + n + ").]");
        } else if ("NO".equalsIgnoreCase(decision)) {
            rec.setEsOportunidadIbm(false);
            rec.setNivelConfianza("N/A");
            rec.setProductoRecomendado(null);
            rec.setEstado(EstadoRecomendacion.DESCARTADA_ALCANCE);
            rec.setJustificacion(causaOriginal + " [Resuelto por revisión humana: NO.]");
        } else {
            throw new IllegalArgumentException("decision debe ser SI o NO; recibido: " + decision);
        }

        // scoreCompuesto=null: conserva el score original (la resolución no debe alterar el orden).
        return Optional.of(recomendacionPersistencePort.guardarOActualizar(rec, null));
    }

    /**
     * Tarea 1.8: dispara la evaluación profunda sin bloquear la petición HTTP.
     * Se usa el TaskExecutor del contenedor en vez de {@code @Async} porque una llamada
     * interna al propio bean no pasa por el proxy y ejecutaría Ollama en el hilo del request
     * (hasta 90 s por OCID).
     */
    private void evaluarEnSegundoPlano(int topN) {
        try {
            taskExecutor.execute(() -> {
                try {
                    int evaluadas = precalcularRecomendaciones(topN);
                    if (evaluadas > 0) {
                        log.info("Evaluación en segundo plano completada: {} OCIDs procesados.", evaluadas);
                    }
                } catch (Exception e) {
                    log.error("Error en la evaluación en segundo plano: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            // Si no hay executor disponible, la evaluación no es crítica: el scheduler la retoma.
            log.warn("No se pudo programar la evaluación en segundo plano: {}", e.getMessage());
        }
    }

    private Optional<RecomendacionOportunidad> generarRecomendacionRapidaVectorial(String ocid) {
        // Tarea 1.6 (H5): lookup por OCID en BD, no carga de la tabla entera.
        Optional<ProcesoSercop> procesoOpt = obtenerProcesosSercopService.buscarPorOcid(ocid);

        if (procesoOpt.isEmpty()) {
            return Optional.empty();
        }

        ProcesoSercop proceso = procesoOpt.get();

        // Tarea 1.3: la preselección NO puede escribir esOportunidadIbm=true sin pasar por el
        // validador de alcance. Sin esto, un proceso inelegible (monto, exclusiones, método)
        // se publicaba como oportunidad con confianza ALTO/MEDIO/BAJO (H1, H4).
        var validacion = validadorAlcanceIbmService.validar(proceso);
        if (!validacion.isEsElegible()) {
            RecomendacionOportunidad descartada = construirDescarteAlcance(proceso, validacion.getMotivoDescarte());
            recomendacionPersistencePort.guardarOActualizar(descartada, 0.0);
            return Optional.of(descartada);
        }

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
        // Tarea 1.1: la preselección se marca como tal. No es un veredicto: solo afinidad vectorial.
        rec.setEstado(EstadoRecomendacion.PRESELECCION_VECTORIAL);

        recomendacionPersistencePort.guardarOActualizar(rec, mejor.getScoreCompuesto());
        return Optional.of(rec);
    }

    /**
     * Fila de descarte por validador de alcance (tareas 1.3 y 1.4): estado explícito y motivo
     * reales, sin confianza inventada. Útil tanto para la preselección como para la evaluación.
     */
    private RecomendacionOportunidad construirDescarteAlcance(ProcesoSercop proceso, String motivo) {
        RecomendacionOportunidad rec = new RecomendacionOportunidad();
        rec.setOcid(proceso.getOcid());
        rec.setTitulo(proceso.getTitulo() != null ? proceso.getTitulo() : "Sin título");
        rec.setDescripcion(proceso.getDescripcion() != null ? proceso.getDescripcion() : "");
        rec.setComprador(proceso.getComprador() != null ? proceso.getComprador() : "No especificado");
        rec.setMontoReferencial(proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto());
        rec.setEsOportunidadIbm(false);
        rec.setProductoRecomendado(null);
        rec.setNivelConfianza("N/A");
        rec.setJustificacion(motivo);
        rec.setEstrategiaComercial("Descartado: El requerimiento se encuentra fuera del alcance del portafolio tecnológico corporativo de IBM.");
        rec.setEstado(EstadoRecomendacion.DESCARTADA_ALCANCE);
        rec.setFechaEvaluacion(java.time.OffsetDateTime.now());
        return rec;
    }

    /**
     * Precalcula y persiste las recomendaciones LLM para los top N procesos por score compuesto.
     * Operacion offline opcional: util para warmup inicial o cuando llegan muchos procesos nuevos.
     *
     * Tarea 1.5: usa la cola de pendientes (no la de "sin fila"), de modo que también reintenta
     * OCIDs con ERROR_EVALUACION y promueve a evaluación profunda las PRESELECCION_VECTORIAL.
     */
    public int precalcularRecomendaciones(int topN) {
        List<String> pendientes = similitudPersistencePort.obtenerPendientesDeEvaluacion(topN);

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
     * Solo considera definitiva una fila con estado EVALUADA o DESCARTADA_ALCANCE (tarea 1.2):
     * una preselección vectorial o un ERROR_EVALUACION vuelven a evaluación profunda, lo que
     * también habilita el reintento tras un fallo del LLM (tarea 1.5).
     */
    public Optional<RecomendacionOportunidad> recomendarPorOcid(String ocid) {
        Optional<RecomendacionOportunidad> cached = recomendacionPersistencePort.buscarPorOcid(ocid);

        if (cached.isPresent() && EstadoRecomendacion.esDefinitivo(cached.get().getEstado())) {
            return cached;
        }

        // Ejecutar evaluación profunda con LLM para este OCID individual
        return generarYPersistirRecomendacion(ocid, null);
    }

    private Optional<RecomendacionOportunidad> generarYPersistirRecomendacion(
            String ocid, List<SimilitudProcesoProducto> similitudesPreCargadas) {

        // Tarea 1.6 (H5): lookup por OCID en BD, no carga de la tabla entera.
        Optional<ProcesoSercop> procesoOpt = obtenerProcesosSercopService.buscarPorOcid(ocid);

        if (procesoOpt.isEmpty()) {
            return Optional.empty();
        }

        ProcesoSercop proceso = procesoOpt.get();

        // Opción C (aprobada por el usuario 2026-10-23): un caso REQUIERE_REVISION solo se
        // re-evalúa si cambió la información del proceso (firma de título+descripción distinta
        // de la guardada al abstenerse). Si es igual, sigue en la cola humana sin gastar LLM.
        Optional<RecomendacionOportunidad> filaPrevia = recomendacionPersistencePort.buscarPorOcid(ocid);
        String firmaActual = com.ibm.opportunity_analysis_service.application.util.FirmaInfoRevision.calcular(proceso);
        if (filaPrevia.isPresent()
                && filaPrevia.get().getEstado() == EstadoRecomendacion.REQUIERE_REVISION
                && firmaActual.equals(filaPrevia.get().getFirmaInfoRevision())) {
            log.info("OCID {} en cola REQUIERE_REVISION con información sin cambios: no se re-evalúa (Opción C).", ocid);
            return filaPrevia;
        }

        // Validar primero si el proceso entra en el alcance corporativo de IBM
        var validacion = validadorAlcanceIbmService.validar(proceso);
        if (!validacion.isEsElegible()) {
            RecomendacionOportunidad descartada = construirDescarteAlcance(proceso, validacion.getMotivoDescarte());
            recomendacionPersistencePort.guardarOActualizar(descartada, 0.0);
            return Optional.of(descartada);
        }

        // Cascada oficial (corrida 6): reglas duras ANTES del LLM. Si una dispara, es un NO
        // determinista (validado 33/33 sin fugas) y no se gasta una llamada al modelo.
        Optional<String> reglaDisparada = reglasDurasOportunidadService.aplicar(proceso);
        if (reglaDisparada.isPresent()) {
            RecomendacionOportunidad descartada = construirDescarteAlcance(proceso,
                    "Descarte por regla dura (" + reglaDisparada.get()
                            + "): criterio determinista validado sobre el gold set (33/33 aciertos, 0 fugas); "
                            + "no requiere evaluación LLM.");
            recomendacionPersistencePort.guardarOActualizar(descartada, 0.0);
            return Optional.of(descartada);
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
            if (recomendacion == null) {
                throw new IllegalStateException("El evaluador devolvió una recomendación nula");
            }
            // Tarea 1.5: un fallo del LLM reportado por el adapter (ERROR_EVALUACION) no se
            // convierte en veredicto: se conserva el estado de error y esOportunidadIbm=false.
            if (recomendacion.getEstado() == EstadoRecomendacion.ERROR_EVALUACION) {
                recomendacion.setOcid(proceso.getOcid());
                log.warn("Evaluación fallida para OCID {} conservada como ERROR_EVALUACION (permite reintento).", ocid);
            } else {
                // El estado lo decide el adaptador según la vía del veredicto 3-vías:
                // EVALUADA (SI) / DESCARTADA_ALCANCE (NO) / REQUIERE_REVISION (REVISAR, cola humana).
                // Solo se rellena si un evaluador legado no fijó estado.
                if (recomendacion.getEstado() == null) {
                    recomendacion.setEstado(EstadoRecomendacion.EVALUADA);
                }
                if (recomendacion.getFechaEvaluacion() == null) {
                    recomendacion.setFechaEvaluacion(java.time.OffsetDateTime.now());
                }
            }
        } catch (Exception e) {
            // Tarea 1.5 (H3): antes el fallback escribía esOportunidadIbm = !candidatosTop.isEmpty(),
            // lo que convertía un error de Ollama en un falso positivo cacheado. Ahora el error es
            // un estado explícito, no definitivo, y permite reintento en recomendarPorOcid/scheduler.
            log.error("Error al evaluar con LLM para OCID {}: {}. Se persiste ERROR_EVALUACION.", ocid, e.getMessage());
            recomendacion = new RecomendacionOportunidad();
            recomendacion.setEsOportunidadIbm(false);
            recomendacion.setProductoRecomendado(null);
            recomendacion.setNivelConfianza("N/A");
            recomendacion.setJustificacion("Evaluación no disponible por error en el LLM: " + e.getMessage()
                    + ". No es un descarte definitivo; se reintentará.");
            recomendacion.setEstrategiaComercial("Evaluación pendiente de reintento.");
            recomendacion.setEstado(EstadoRecomendacion.ERROR_EVALUACION);
            recomendacion.setFechaEvaluacion(java.time.OffsetDateTime.now());
        }

        // Política de reintento (control REVISAR, 2026-10-24): el fallo NO es un veredicto,
        // se reprograma con backoff y un caso estancado (MAX_INTENTOS fallos seguidos) deja de
        // reintentarse con WARN en lugar de martillar un LLM caído. Ver PoliticaReintento.
        if (recomendacion.getEstado() == EstadoRecomendacion.ERROR_EVALUACION) {
            int fallosPrevios = filaPrevia.map(RecomendacionOportunidad::getIntentosReintento).orElse(0);
            int intentos = fallosPrevios + 1;
            recomendacion.setIntentosReintento(intentos);
            if (PoliticaReintento.agotado(intentos)) {
                recomendacion.setProximoIntentoAt(null);
                log.warn("OCID {} ESTANCADO: {} fallos seguidos de evaluación LLM. Sin reprogramación "
                                + "automática; revisar el servicio LLM o relanzar a mano con precalcular.",
                        ocid, intentos);
            } else {
                java.time.Duration espera = PoliticaReintento.esperaTrasIntento(intentos);
                recomendacion.setProximoIntentoAt(java.time.OffsetDateTime.now().plus(espera));
                log.warn("OCID {}: fallo de evaluación {} de {}, reprogramado en {} min.",
                        ocid, intentos, PoliticaReintento.MAX_INTENTOS, espera.toMinutes());
            }
        } else {
            // Cualquier veredicto (o descarte por validador/reglas) limpia el contador de fallos.
            recomendacion.setIntentosReintento(0);
            recomendacion.setProximoIntentoAt(null);
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
        // Tarea 1.6 (H5): lookup por OCID en BD, no carga de la tabla entera.
        Optional<ProcesoSercop> procesoOpt = obtenerProcesosSercopService.buscarPorOcid(ocid);

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
