package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCandidatoPort;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.EvaluadorOportunidadLlmPort;
import com.ibm.opportunity_analysis_service.application.port.out.ProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.RecomendacionPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.SimilitudPersistencePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionCriteriosOportunidad;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.EstadoRecomendacion;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reintentos con backoff de ERROR_EVALUACION (control REVISAR, decisión 2026-10-24):
 *  - un fallo reprograma el próximo intento con espera creciente (5m, 15m, ...);
 *  - a los 6 fallos seguidos el caso queda ESTANCADO (sin reprogramación) con WARN;
 *  - un veredicto posterior limpia el contador a 0.
 * Tests unitarios con mocks: no tocan la base de datos ni el túnel LLM.
 */
@ExtendWith(MockitoExtension.class)
class ReintentoErrorTest {

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

    private RecomendarOportunidadesService servicio;

    @BeforeEach
    void setUp() {
        var validador = new ValidadorAlcanceIbmService(new ConfiguracionCriteriosOportunidad());
        servicio = new RecomendarOportunidadesService(
                obtenerProcesosSercopService,
                embeddingCandidatoPort,
                generarEmbeddingCandidatoService,
                embeddingProductoIbmPersistencePort,
                productoIbmPersistencePort,
                evaluadorOportunidadLlmPort,
                similitudPersistencePort,
                recomendacionPersistencePort,
                validador,
                new ReglasDurasOportunidadService(),
                configuracionGranite,
                r -> { });
    }

    @Test
    void primer_fallo_se_reprograma_a_5_minutos() {
        ProcesoSercop p = procesoElegible();
        stubCascadaConFallo(p, Optional.empty());

        servicio.recomendarPorOcid(p.getOcid());

        RecomendacionOportunidad guardada = capturarGuardado();
        assertEquals(EstadoRecomendacion.ERROR_EVALUACION, guardada.getEstado(),
                "Un fallo del LLM es un estado, no un veredicto");
        assertEquals(1, guardada.getIntentosReintento(), "Primer fallo → intento 1");
        assertNotNull(guardada.getProximoIntentoAt(), "Debe haber próximo intento programado");
        assertTrue(guardada.getProximoIntentoAt().isAfter(OffsetDateTime.now().plus(Duration.ofMinutes(4))),
                "Tras el primer fallo espera ~5 min (backoff)");
    }

    @Test
    void fallo_repetido_incrementa_y_espera_mas() {
        ProcesoSercop p = procesoElegible();
        stubCascadaConFallo(p, Optional.of(filaEnError(p, 1)));

        servicio.recomendarPorOcid(p.getOcid());

        RecomendacionOportunidad guardada = capturarGuardado();
        assertEquals(2, guardada.getIntentosReintento(), "Segundo fallo acumulado");
        assertTrue(guardada.getProximoIntentoAt().isAfter(OffsetDateTime.now().plus(Duration.ofMinutes(14))),
                "El segundo intento espera 15 min");
    }

    @Test
    void al_sextimo_fallo_el_caso_queda_estancado_sin_reprogramacion() {
        ProcesoSercop p = procesoElegible();
        stubCascadaConFallo(p, Optional.of(filaEnError(p, 5)));

        servicio.recomendarPorOcid(p.getOcid());

        RecomendacionOportunidad guardada = capturarGuardado();
        assertEquals(6, guardada.getIntentosReintento(), "Sexto fallo seguido");
        assertNull(guardada.getProximoIntentoAt(),
                "Caso estancado: sin reprogramación automática (queda para revisión manual)");
    }

    @Test
    void un_veredicto_correcto_limpia_el_contador_de_fallos() {
        ProcesoSercop p = procesoElegible();
        when(obtenerProcesosSercopService.buscarPorOcid(p.getOcid())).thenReturn(Optional.of(p));
        when(recomendacionPersistencePort.buscarPorOcid(p.getOcid()))
                .thenReturn(Optional.of(filaEnError(p, 3)));
        when(similitudPersistencePort.buscarPorOcid(p.getOcid()))
                .thenReturn(List.of(new SimilitudProcesoProducto(1L, p.getOcid(), 7L, "Instana", 0.81, 0.72, "hash", null)));
        when(productoIbmPersistencePort.buscarPorId(7L)).thenReturn(Optional.of(producto("Instana")));
        when(configuracionGranite.getModelo()).thenReturn("granite-embedding:278m");
        RecomendacionOportunidad bien = new RecomendacionOportunidad();
        bien.setEsOportunidadIbm(true);
        bien.setNivelConfianza("ALTO");
        bien.setJustificacion("Encaje directo.");
        bien.setEstado(EstadoRecomendacion.EVALUADA);
        when(evaluadorOportunidadLlmPort.evaluarOportunidad(any(), any(), any())).thenReturn(bien);

        servicio.recomendarPorOcid(p.getOcid());

        RecomendacionOportunidad guardada = capturarGuardado();
        assertEquals(EstadoRecomendacion.EVALUADA, guardada.getEstado());
        assertEquals(0, guardada.getIntentosReintento(),
                "Un veredicto válido reinicia la cuenta de fallos");
        assertNull(guardada.getProximoIntentoAt());
    }

    // ---------------------------------------------------------------- fixtures

    /** Stub de la ruta profunda con el LLM caído (excepción, como el túnel apagado). */
    private void stubCascadaConFallo(ProcesoSercop p, Optional<RecomendacionOportunidad> filaPrevia) {
        when(obtenerProcesosSercopService.buscarPorOcid(p.getOcid())).thenReturn(Optional.of(p));
        when(recomendacionPersistencePort.buscarPorOcid(p.getOcid())).thenReturn(filaPrevia);
        when(similitudPersistencePort.buscarPorOcid(p.getOcid()))
                .thenReturn(List.of(new SimilitudProcesoProducto(1L, p.getOcid(), 7L, "Instana", 0.81, 0.72, "hash", null)));
        when(productoIbmPersistencePort.buscarPorId(7L)).thenReturn(Optional.of(producto("Instana")));
        when(configuracionGranite.getModelo()).thenReturn("granite-embedding:278m");
        when(evaluadorOportunidadLlmPort.evaluarOportunidad(any(), any(), any()))
                .thenThrow(new RuntimeException("túnel LLM no responde"));
    }

    private RecomendacionOportunidad filaEnError(ProcesoSercop p, int intentos) {
        RecomendacionOportunidad r = new RecomendacionOportunidad();
        r.setOcid(p.getOcid());
        r.setTitulo(p.getTitulo());
        r.setDescripcion(p.getDescripcion());
        r.setEsOportunidadIbm(false);
        r.setNivelConfianza("N/A");
        r.setJustificacion("Evaluación no disponible por error en el LLM.");
        r.setEstado(EstadoRecomendacion.ERROR_EVALUACION);
        r.setIntentosReintento(intentos);
        return r;
    }

    private RecomendacionOportunidad capturarGuardado() {
        ArgumentCaptor<RecomendacionOportunidad> captor = ArgumentCaptor.forClass(RecomendacionOportunidad.class);
        verify(recomendacionPersistencePort).guardarOActualizar(captor.capture(), any());
        return captor.getValue();
    }

    private ProcesoSercop procesoElegible() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-reintento");
        p.setTitulo("Plataforma de observabilidad en nube híbrida");
        p.setDescripcion("Implementación de APM, análisis de logs y trazabilidad con OpenShift y software de plataforma");
        p.setMonto(new BigDecimal("180000"));
        p.setMetodo("open");
        return p;
    }

    private ProductoIbm producto(String nombre) {
        ProductoIbm p = new ProductoIbm();
        p.setId(7L);
        p.setNombre(nombre);
        return p;
    }
}
