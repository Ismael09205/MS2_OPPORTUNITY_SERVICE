package com.ibm.opportunity_analysis_service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCandidatoPort;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.EvaluadorOportunidadLlmPort;
import com.ibm.opportunity_analysis_service.application.port.out.ProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.RecomendacionPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.SimilitudPersistencePort;
import com.ibm.opportunity_analysis_service.application.service.AnalizarSimilitudService;
import com.ibm.opportunity_analysis_service.application.service.GenerarEmbeddingCandidatoService;
import com.ibm.opportunity_analysis_service.application.service.ObtenerProcesosSercopService;
import com.ibm.opportunity_analysis_service.application.service.RecomendarOportunidadesService;
import com.ibm.opportunity_analysis_service.application.service.ValidadorAlcanceIbmService;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionCriteriosOportunidad;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.EstadoRecomendacion;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;
import com.ibm.opportunity_analysis_service.domain.entity.SimilitudProcesoProducto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 1, Bloque A (tareas 1.1-1.5) — criterios de aceptación (a), (b) y (c):
 *
 *  (a) una preselección nunca se escribe sin pasar por el {@code ValidadorAlcanceIbmService};
 *  (b) un proceso hoy-elegible que deja de serlo pierde sus similitudes (se materializa el descarte);
 *  (c) un error del LLM no produce {@code esOportunidadIbm=true} (queda ERROR_EVALUACION, reintentable).
 *
 * Tests unitarios con mocks: no tocan la base de datos ni Ollama.
 */
@ExtendWith(MockitoExtension.class)
class IntegridadSenalFase1Test {

    @Mock
    private ObtenerProcesosSercopService obtenerProcesosSercopService;
    @Mock
    private EmbeddingCandidatoPort embeddingCandidatoPort;
    @Mock
    private GenerarEmbeddingCandidatoService generarEmbeddingCandidatoService;
    @Mock
    private EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort;
    @Mock
    private ProductoIbmPersistencePort productoIbmPersistencePort;
    @Mock
    private EvaluadorOportunidadLlmPort evaluadorOportunidadLlmPort;
    @Mock
    private SimilitudPersistencePort similitudPersistencePort;
    @Mock
    private RecomendacionPersistencePort recomendacionPersistencePort;
    @Mock
    private ConfiguracionGranite configuracionGranite;
    @Mock
    private com.ibm.opportunity_analysis_service.adapter.out.persistence.repository
            .RecomendacionJpaRepository recomendacionJpaRepository;

    private ValidadorAlcanceIbmService validador;
    private RecomendarOportunidadesService recomendarOportunidadesService;
    private AnalizarSimilitudService analizarSimilitudService;

    /** Executor síncrono: en unitario no queremos hilos sueltos evaluando con LLM. */
    private final List<Runnable> tareasEnSegundoPlano = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        validador = new ValidadorAlcanceIbmService(new ConfiguracionCriteriosOportunidad());
        recomendarOportunidadesService = new RecomendarOportunidadesService(
                obtenerProcesosSercopService,
                embeddingCandidatoPort,
                generarEmbeddingCandidatoService,
                embeddingProductoIbmPersistencePort,
                productoIbmPersistencePort,
                evaluadorOportunidadLlmPort,
                similitudPersistencePort,
                recomendacionPersistencePort,
                validador,
                new com.ibm.opportunity_analysis_service.application.service.ReglasDurasOportunidadService(),
                configuracionGranite,
                tareasEnSegundoPlano::add);
        analizarSimilitudService = new AnalizarSimilitudService(
                obtenerProcesosSercopService,
                embeddingCandidatoPort,
                embeddingProductoIbmPersistencePort,
                similitudPersistencePort,
                recomendacionPersistencePort,
                validador,
                configuracionGranite);
    }

    // ---------------------------------------------------------------- helpers

    /** Proceso inelegible por el validador (término excluido: cableado). */
    private ProcesoSercop procesoInelegible() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-inelegible");
        p.setTitulo("Instalación de cableado estructurado para edificio central");
        p.setDescripcion("Puntos de red y fibra óptica");
        p.setMonto(new BigDecimal("80000"));
        p.setMetodo("open");
        return p;
    }

    /** Proceso elegible según el validador (plataforma enterprise, monto suficiente). */
    private ProcesoSercop procesoElegible() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-elegible");
        p.setTitulo("Adquisición de plataforma de observabilidad y monitoreo en nube híbrida con OpenShift");
        p.setDescripcion("Implementación de APM, análisis de logs y trazabilidad con OpenShift y software de plataforma");
        p.setMonto(new BigDecimal("180000"));
        p.setMetodo("open");
        return p;
    }

    private SimilitudProcesoProducto similitud(String ocid) {
        return new SimilitudProcesoProducto(1L, ocid, 7L, "watsonx", 0.81, 0.72, "hash", null);
    }

    // ---------------------------------------------------------------- (a)

    @Test
    void a_preseleccion_nunca_se_escribe_sin_pasar_por_el_validador() {
        ProcesoSercop ineq = procesoInelegible();

        when(recomendacionPersistencePort.obtenerOportunidadesIbm(anyInt(), anyInt()))
                .thenReturn(List.of(), List.of());
        when(similitudPersistencePort.obtenerSiguientesAEvaluar(anyInt())).thenReturn(List.of(ineq.getOcid()));
        when(similitudPersistencePort.contarPendientesDeEvaluacion()).thenReturn(0L);
        when(obtenerProcesosSercopService.buscarPorOcid(ineq.getOcid())).thenReturn(Optional.of(ineq));

        recomendarOportunidadesService.obtenerOportunidadesIbm(6, 0);

        ArgumentCaptor<RecomendacionOportunidad> captor = ArgumentCaptor.forClass(RecomendacionOportunidad.class);
        verify(recomendacionPersistencePort).guardarOActualizar(captor.capture(), eq(0.0));

        RecomendacionOportunidad guardada = captor.getValue();
        assertFalse(guardada.isEsOportunidadIbm(),
                "Un proceso inelegible jamás debe escribirse con esOportunidadIbm=true (criterio a)");
        assertEquals(EstadoRecomendacion.DESCARTADA_ALCANCE, guardada.getEstado(),
                "El descarte debe quedar con estado explícito DESCARTADA_ALCANCE");
        assertTrue(guardada.getJustificacion().contains("cableado estructurado"),
                "El motivo debe ser el real del validador");
    }

    @Test
    void a2_preseleccion_validada_se_marca_como_PRESELECCION_VECTORIAL_no_como_veredicto() {
        ProcesoSercop eleg = procesoElegible();

        when(recomendacionPersistencePort.obtenerOportunidadesIbm(anyInt(), anyInt()))
                .thenReturn(List.of(), List.of());
        when(similitudPersistencePort.obtenerSiguientesAEvaluar(anyInt())).thenReturn(List.of(eleg.getOcid()));
        when(similitudPersistencePort.contarPendientesDeEvaluacion()).thenReturn(0L);
        when(obtenerProcesosSercopService.buscarPorOcid(eleg.getOcid())).thenReturn(Optional.of(eleg));
        when(similitudPersistencePort.buscarPorOcid(eleg.getOcid())).thenReturn(List.of(similitud(eleg.getOcid())));

        recomendarOportunidadesService.obtenerOportunidadesIbm(6, 0);

        ArgumentCaptor<RecomendacionOportunidad> captor = ArgumentCaptor.forClass(RecomendacionOportunidad.class);
        verify(recomendacionPersistencePort).guardarOActualizar(captor.capture(), any());

        RecomendacionOportunidad guardada = captor.getValue();
        assertTrue(guardada.isEsOportunidadIbm());
        assertEquals(EstadoRecomendacion.PRESELECCION_VECTORIAL, guardada.getEstado(),
                "Debe quedar marcada como preselección (H1), no como evaluada");
    }

    @Test
    void a3_recomendarPorOcid_no_devuelve_preseleccion_como_si_fuera_veredicto() {
        ProcesoSercop eleg = procesoElegible();
        RecomendacionOportunidad preseleccion = new RecomendacionOportunidad();
        preseleccion.setOcid(eleg.getOcid());
        preseleccion.setEsOportunidadIbm(true);
        preseleccion.setNivelConfianza("ALTO");
        preseleccion.setEstado(EstadoRecomendacion.PRESELECCION_VECTORIAL);
        preseleccion.setJustificacion("Preselección por afinidad vectorial (Score: 0.8000, Delta: 0.1000).");

        when(recomendacionPersistencePort.buscarPorOcid(eleg.getOcid())).thenReturn(Optional.of(preseleccion));
        when(obtenerProcesosSercopService.buscarPorOcid(eleg.getOcid())).thenReturn(Optional.of(eleg));
        when(similitudPersistencePort.buscarPorOcid(eleg.getOcid())).thenReturn(List.of(similitud(eleg.getOcid())));
        when(productoIbmPersistencePort.buscarPorId(7L)).thenReturn(Optional.of(producto("watsonx")));
        when(evaluadorOportunidadLlmPort.evaluarOportunidad(any(), any(), any()))
                .thenReturn(evaluada(eleg, true, "ALTO"));

        Optional<RecomendacionOportunidad> resultado = recomendarOportunidadesService.recomendarPorOcid(eleg.getOcid());

        assertTrue(resultado.isPresent());
        assertEquals(EstadoRecomendacion.EVALUADA, resultado.get().getEstado(),
                "La preselección debe promoverse a evaluación profunda (tarea 1.2, reemplaza startsWith)");
        verify(evaluadorOportunidadLlmPort).evaluarOportunidad(any(), any(), any());
    }

    @Test
    void a4_recomendarPorOcid_si_es_EVALUADA_no_vuelve_a_llamar_al_llm() {
        ProcesoSercop eleg = procesoElegible();
        RecomendacionOportunidad yaEvaluada = evaluada(eleg, false, "N/A");
        yaEvaluada.setEstado(EstadoRecomendacion.EVALUADA);

        when(recomendacionPersistencePort.buscarPorOcid(eleg.getOcid())).thenReturn(Optional.of(yaEvaluada));

        Optional<RecomendacionOportunidad> resultado = recomendarOportunidadesService.recomendarPorOcid(eleg.getOcid());

        assertTrue(resultado.isPresent());
        assertFalse(resultado.get().isEsOportunidadIbm());
        verify(evaluadorOportunidadLlmPort, never()).evaluarOportunidad(any(), any(), any());
    }

    // ---------------------------------------------------------------- (b)

    @Test
    void b_proceso_que_deja_de_ser_elegible_pierde_sus_similitudes_y_el_descarte_se_materializa() {
        ProcesoSercop ineq = procesoInelegible();

        when(obtenerProcesosSercopService.obtenerTodos()).thenReturn(List.of(ineq));
        when(similitudPersistencePort.buscarPorOcid(ineq.getOcid())).thenReturn(List.of(similitud(ineq.getOcid())));
        when(recomendacionPersistencePort.buscarPorOcid(ineq.getOcid())).thenReturn(Optional.empty());
        when(similitudPersistencePort.eliminarPorOcid(ineq.getOcid())).thenReturn(1);

        analizarSimilitudService.analizar();

        verify(similitudPersistencePort).eliminarPorOcid(ineq.getOcid());

        ArgumentCaptor<RecomendacionOportunidad> captor = ArgumentCaptor.forClass(RecomendacionOportunidad.class);
        verify(recomendacionPersistencePort).guardarOActualizar(captor.capture(), eq(0.0));

        RecomendacionOportunidad guardada = captor.getValue();
        assertFalse(guardada.isEsOportunidadIbm());
        assertEquals(EstadoRecomendacion.DESCARTADA_ALCANCE, guardada.getEstado(),
                "El descarte debe materializarse con estado explícito (H4)");
        assertTrue(guardada.getJustificacion().contains("cableado estructurado"));
    }

    @Test
    void b2_proceso_inelegible_sin_rastro_no_crea_filas_nuevas() {
        ProcesoSercop ineq = procesoInelegible();

        when(obtenerProcesosSercopService.obtenerTodos()).thenReturn(List.of(ineq));
        when(similitudPersistencePort.buscarPorOcid(ineq.getOcid())).thenReturn(List.of());
        when(recomendacionPersistencePort.buscarPorOcid(ineq.getOcid())).thenReturn(Optional.empty());

        analizarSimilitudService.analizar();

        // ~300 procesos nunca fueron candidatos: no conviene generar una fila de descarte para cada uno
        verify(similitudPersistencePort, never()).eliminarPorOcid(anyString());
        verify(recomendacionPersistencePort, never()).guardarOActualizar(any(), any());
    }

    // ---------------------------------------------------------------- (c)

    @Test
    void c_error_del_llm_no_produce_esOportunidadIbm_true() {
        ProcesoSercop eleg = procesoElegible();

        when(obtenerProcesosSercopService.buscarPorOcid(eleg.getOcid())).thenReturn(Optional.of(eleg));
        when(similitudPersistencePort.buscarPorOcid(eleg.getOcid())).thenReturn(List.of(similitud(eleg.getOcid())));
        when(productoIbmPersistencePort.buscarPorId(7L)).thenReturn(Optional.of(producto("watsonx")));
        when(evaluadorOportunidadLlmPort.evaluarOportunidad(any(), any(), any()))
                .thenThrow(new RuntimeException("Ollama timeout"));

        Optional<RecomendacionOportunidad> resultado = recomendarOportunidadesService.recomendarPorOcid(eleg.getOcid());

        assertTrue(resultado.isPresent());
        ArgumentCaptor<RecomendacionOportunidad> captor = ArgumentCaptor.forClass(RecomendacionOportunidad.class);
        verify(recomendacionPersistencePort).guardarOActualizar(captor.capture(), any());

        RecomendacionOportunidad guardada = captor.getValue();
        assertFalse(guardada.isEsOportunidadIbm(),
                "Un error del LLM no puede convertirse en oportunidad (criterio c, H3)");
        assertEquals(EstadoRecomendacion.ERROR_EVALUACION, guardada.getEstado(),
                "Debe quedar como ERROR_EVALUACION, que es un estado NO definitivo");
        assertFalse(EstadoRecomendacion.esDefinitivo(guardada.getEstado()),
                "ERROR_EVALUACION no es definitivo: debe permitir reintento");
    }

    @Test
    void c2_error_reportado_por_el_adapter_no_se_promueve_a_EVALUADA() {
        ProcesoSercop eleg = procesoElegible();

        when(obtenerProcesosSercopService.buscarPorOcid(eleg.getOcid())).thenReturn(Optional.of(eleg));
        when(similitudPersistencePort.buscarPorOcid(eleg.getOcid())).thenReturn(List.of(similitud(eleg.getOcid())));
        when(productoIbmPersistencePort.buscarPorId(7L)).thenReturn(Optional.of(producto("watsonx")));

        // El adapter devuelve su fallback con estado ERROR_EVALUACION (tarea 1.5) en vez de lanzar
        RecomendacionOportunidad fallback = new RecomendacionOportunidad();
        fallback.setOcid(eleg.getOcid());
        fallback.setEsOportunidadIbm(false);
        fallback.setNivelConfianza("N/A");
        fallback.setEstado(EstadoRecomendacion.ERROR_EVALUACION);
        fallback.setJustificacion("Evaluación no disponible: el LLM no respondió.");
        when(evaluadorOportunidadLlmPort.evaluarOportunidad(any(), any(), any())).thenReturn(fallback);

        Optional<RecomendacionOportunidad> resultado = recomendarOportunidadesService.recomendarPorOcid(eleg.getOcid());

        assertTrue(resultado.isPresent());
        assertEquals(EstadoRecomendacion.ERROR_EVALUACION, resultado.get().getEstado(),
                "El estado de error reportado por el adapter no debe sobrescribirse a EVALUADA");
        assertFalse(resultado.get().isEsOportunidadIbm());
    }

    @Test
    void c3_estado_definitivo_EVALUADA_y_DESCARTADA_si_no_PRESELECCION_ni_ERROR() {
        assertTrue(EstadoRecomendacion.esDefinitivo(EstadoRecomendacion.EVALUADA));
        assertTrue(EstadoRecomendacion.esDefinitivo(EstadoRecomendacion.DESCARTADA_ALCANCE));
        assertFalse(EstadoRecomendacion.esDefinitivo(EstadoRecomendacion.PRESELECCION_VECTORIAL));
        assertFalse(EstadoRecomendacion.esDefinitivo(EstadoRecomendacion.ERROR_EVALUACION));
        assertFalse(EstadoRecomendacion.esDefinitivo(null), "Una fila legada sin estado no es definitiva");
    }

    // ---------------------------------------------------------------- (d)

    @Test
    void d_lookup_por_ocid_no_carga_la_tabla_entera() {
        ProcesoSercop eleg = procesoElegible();
        // Fila PRESELECCION → fuerza la ruta de evaluación profunda, que es la que
        // antes hacía obtenerTodos()+filter sobre los 381 procesos.
        RecomendacionOportunidad preseleccion = new RecomendacionOportunidad();
        preseleccion.setOcid(eleg.getOcid());
        preseleccion.setEsOportunidadIbm(true);
        preseleccion.setNivelConfianza("MEDIO");
        preseleccion.setEstado(EstadoRecomendacion.PRESELECCION_VECTORIAL);

        when(recomendacionPersistencePort.buscarPorOcid(eleg.getOcid())).thenReturn(Optional.of(preseleccion));
        when(obtenerProcesosSercopService.buscarPorOcid(eleg.getOcid())).thenReturn(Optional.of(eleg));
        when(similitudPersistencePort.buscarPorOcid(eleg.getOcid())).thenReturn(List.of(similitud(eleg.getOcid())));
        when(productoIbmPersistencePort.buscarPorId(7L)).thenReturn(Optional.of(producto("watsonx")));
        when(evaluadorOportunidadLlmPort.evaluarOportunidad(any(), any(), any()))
                .thenReturn(evaluada(eleg, true, "ALTO"));

        recomendarOportunidadesService.recomendarPorOcid(eleg.getOcid());

        // Criterio (d): la latencia no puede escalar con el total de procesos. La única fuente
        // de ese escalado eran los tres obtenerTodos()+filter; ahora todo pasa por buscarPorOcid.
        verify(obtenerProcesosSercopService, never()).obtenerTodos();
        verify(obtenerProcesosSercopService).buscarPorOcid(eleg.getOcid());
    }

    @Test
    void d2_obtenerCandidatosCrudos_tambien_es_lookup_por_ocid() {
        ProcesoSercop eleg = procesoElegible();
        EmbeddingCandidatoTecnologico embedding = new EmbeddingCandidatoTecnologico();
        embedding.setEmbedding(new float[]{0.1f, 0.2f});

        when(obtenerProcesosSercopService.buscarPorOcid(eleg.getOcid())).thenReturn(Optional.of(eleg));
        when(configuracionGranite.getModelo()).thenReturn("nomic-embed-text");
        when(embeddingCandidatoPort.buscarPorOcidYModelo(eleg.getOcid(), "nomic-embed-text"))
                .thenReturn(Optional.of(embedding));
        when(embeddingProductoIbmPersistencePort.buscarSimilares(any(), anyInt())).thenReturn(List.of());

        recomendarOportunidadesService.obtenerCandidatosCrudos(eleg.getOcid());

        verify(obtenerProcesosSercopService, never()).obtenerTodos();
        verify(obtenerProcesosSercopService).buscarPorOcid(eleg.getOcid());
    }

    // ---------------------------------------------------------------- (e)

    @Test
    void e_getRecomendaciones_devuelvePendientesCount_yNuncaPreselecciones() {
        RecomendacionOportunidad evaluada = evaluada(procesoElegible(), true, "MEDIO");

        when(recomendacionPersistencePort.obtenerOportunidadesIbm(anyInt(), anyInt()))
                .thenReturn(List.of(evaluada));
        when(similitudPersistencePort.contarPendientesDeEvaluacion()).thenReturn(7L);

        var pagina = recomendarOportunidadesService.obtenerOportunidadesIbm(6, 0);

        assertEquals(1, pagina.recomendaciones().size());
        assertEquals(7, pagina.pendientesCount(),
                "Con menos de 'limit' filas la respuesta debe explicar cuántas quedan por evaluar (1.8)");
        assertTrue(pagina.recomendaciones().stream()
                        .allMatch(r -> r.getEstado() == EstadoRecomendacion.EVALUADA),
                "La lista principal solo puede contener EVALUADAS (decisión A, criterio e)");
    }

    @Test
    void e2_consultasSql_filtran_por_estado_verificable() throws NoSuchMethodException {
        // Criterio (e) a nivel de SQL: aunque el servicio filtre, la query es quien garantiza que
        // una PRESELECCION_VECTORIAL (o una fila legada sin estado) jamás salga en la lista.
        var repo = com.ibm.opportunity_analysis_service.adapter.out.persistence.repository
                .RecomendacionJpaRepository.class;

        String lista = repo.getMethod("findOportunidadesEvaluadas", int.class, int.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class)
                .value();
        assertTrue(lista.contains("estado = 'EVALUADA'"),
                "La lista principal debe exigir estado EVALUADA: " + lista);
        assertTrue(lista.contains("es_oportunidad_ibm = true"),
                "La lista principal debe seguir excluyendo descartes (decisión D): " + lista);

        String pendientes = repo.getMethod("findPendientes", int.class, int.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class)
                .value();
        assertTrue(pendientes.contains("'PRESELECCION_VECTORIAL'") && pendientes.contains("'ERROR_EVALUACION'"),
                "El endpoint de pendientes debe exponer preselecciones y errores: " + pendientes);
        assertTrue(!pendientes.contains("'EVALUADA'"),
                "El endpoint de pendientes no debe mezclar filas ya evaluadas: " + pendientes);
    }

    @Test
    void e3_obtenerPendientes_delegaEnElPort() {
        RecomendacionOportunidad preseleccion = new RecomendacionOportunidad();
        preseleccion.setOcid("ocid-x");
        preseleccion.setEstado(EstadoRecomendacion.PRESELECCION_VECTORIAL);
        when(recomendacionPersistencePort.obtenerPendientes(6, 0)).thenReturn(List.of(preseleccion));

        var pendientes = recomendarOportunidadesService.obtenerPendientes(6, 0);

        assertEquals(1, pendientes.size());
        assertEquals(EstadoRecomendacion.PRESELECCION_VECTORIAL, pendientes.get(0).getEstado());
    }

    // ---------------------------------------------------------------- (1.9 / H14)

    @Test
    void h14_fila_verdadera_na_no_se_publica_como_oportunidad() {
        var adapter = new com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter
                .RecomendacionPersistenceAdapter(recomendacionJpaRepository);

        // Reproduce exactamente el hallazgo H14: el LLM (o Bob) respondió es_oportunidad=true
        // con nivel_confianza='N/A', que las propias reglas del prompt definen como descarte.
        RecomendacionOportunidad inconsistente = new RecomendacionOportunidad();
        inconsistente.setOcid("ocid-h14");
        inconsistente.setTitulo("Compra menor de insumos");
        inconsistente.setEsOportunidadIbm(true);
        inconsistente.setNivelConfianza("N/A");
        inconsistente.setJustificacion("Respuesta del LLM inconsistente");
        inconsistente.setEstado(EstadoRecomendacion.EVALUADA);

        when(recomendacionJpaRepository.findByOcid("ocid-h14")).thenReturn(Optional.empty());

        adapter.guardarOActualizar(inconsistente, 0.5);

        ArgumentCaptor<com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.RecomendacionEntity> captor =
                ArgumentCaptor.forClass(com.ibm.opportunity_analysis_service.adapter.out.persistence.entities
                        .RecomendacionEntity.class);
        verify(recomendacionJpaRepository).save(captor.capture());

        var guardada = captor.getValue();
        assertFalse(guardada.isEsOportunidadIbm(),
                "H14: una fila con confianza N/A jamás puede persistirse como oportunidad");
        assertEquals("ERROR_EVALUACION", guardada.getEstado(),
                "Debe persistirse como ERROR_EVALUACION para que permita reevaluación");
        assertNotNull(guardada.getFechaEvaluacion(), "El error debe quedar fechado");
        assertTrue(guardada.getJustificacion().contains("Requiere reevaluación"),
                "El motivo de la inconsistencia debe quedar registrado en la justificación");
    }

    @Test
    void h14_variante_confianza_vacia_tambien_se_normaliza() {
        var adapter = new com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter
                .RecomendacionPersistenceAdapter(recomendacionJpaRepository);

        RecomendacionOportunidad inconsistente = new RecomendacionOportunidad();
        inconsistente.setOcid("ocid-h14-vacio");
        inconsistente.setEsOportunidadIbm(true);
        inconsistente.setNivelConfianza("   "); // vacío tras trim: también es confianza que no acredita
        inconsistente.setEstado(EstadoRecomendacion.EVALUADA);

        when(recomendacionJpaRepository.findByOcid("ocid-h14-vacio")).thenReturn(Optional.empty());

        adapter.guardarOActualizar(inconsistente, 0.5);

        ArgumentCaptor<com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.RecomendacionEntity> captor =
                ArgumentCaptor.forClass(com.ibm.opportunity_analysis_service.adapter.out.persistence.entities
                        .RecomendacionEntity.class);
        verify(recomendacionJpaRepository).save(captor.capture());

        assertFalse(captor.getValue().isEsOportunidadIbm());
        assertEquals("ERROR_EVALUACION", captor.getValue().getEstado());
    }

    @Test
    void h14_no_toca_las_filas_consistentes() {
        var adapter = new com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter
                .RecomendacionPersistenceAdapter(recomendacionJpaRepository);

        RecomendacionOportunidad oportunidadReal = new RecomendacionOportunidad();
        oportunidadReal.setOcid("ocid-ok-alto");
        oportunidadReal.setEsOportunidadIbm(true);
        oportunidadReal.setNivelConfianza("ALTO");
        oportunidadReal.setEstado(EstadoRecomendacion.EVALUADA);
        when(recomendacionJpaRepository.findByOcid("ocid-ok-alto")).thenReturn(Optional.empty());

        // Un descarte legítimo con N/A tampoco debe tocarse: false+N/A es consistente.
        RecomendacionOportunidad descarte = new RecomendacionOportunidad();
        descarte.setOcid("ocid-ok-descarte");
        descarte.setEsOportunidadIbm(false);
        descarte.setNivelConfianza("N/A");
        descarte.setEstado(EstadoRecomendacion.DESCARTADA_ALCANCE);
        when(recomendacionJpaRepository.findByOcid("ocid-ok-descarte")).thenReturn(Optional.empty());

        adapter.guardarOActualizar(oportunidadReal, 0.9);
        adapter.guardarOActualizar(descarte, 0.1);

        ArgumentCaptor<com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.RecomendacionEntity> captor =
                ArgumentCaptor.forClass(com.ibm.opportunity_analysis_service.adapter.out.persistence.entities
                        .RecomendacionEntity.class);
        verify(recomendacionJpaRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        var filaOportunidad = captor.getAllValues().get(0);
        assertTrue(filaOportunidad.isEsOportunidadIbm(), "Una oportunidad con ALTO no debe alterarse");
        assertEquals("EVALUADA", filaOportunidad.getEstado());

        var filaDescarte = captor.getAllValues().get(1);
        assertFalse(filaDescarte.isEsOportunidadIbm());
        assertEquals("DESCARTADA_ALCANCE", filaDescarte.getEstado(),
                "Un descarte con N/A es consistente y no debe convertirse en ERROR_EVALUACION");
    }

    // ---------------------------------------------------------------- fixtures

    private ProductoIbm producto(String nombre) {
        ProductoIbm p = new ProductoIbm();
        p.setId(7L);
        p.setNombre(nombre);
        return p;
    }

    private RecomendacionOportunidad evaluada(ProcesoSercop p, boolean oportunidad, String confianza) {
        RecomendacionOportunidad r = new RecomendacionOportunidad();
        r.setOcid(p.getOcid());
        r.setEsOportunidadIbm(oportunidad);
        r.setNivelConfianza(confianza);
        r.setJustificacion("Evaluación profunda");
        r.setEstado(EstadoRecomendacion.EVALUADA);
        r.setFechaEvaluacion(java.time.OffsetDateTime.now());
        return r;
    }
}
