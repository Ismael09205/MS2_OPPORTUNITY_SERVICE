package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCandidatoPort;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.EvaluadorOportunidadLlmPort;
import com.ibm.opportunity_analysis_service.application.port.out.ProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.RecomendacionPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.SimilitudPersistencePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionCriteriosOportunidad;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.application.util.FirmaInfoRevision;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Control REVISAR en el servicio (plan aprobado 2026-10-23):
 *  - el REVISAR del LLM se persiste como REQUIERE_REVISION (sin promoverse a EVALUADA);
 *  - Opción C: reintento automático SOLO si cambió la información del proceso (firma distinta);
 *  - resolución humana de la cola: SI(ALTO|MEDIO)→EVALUADA, NO→DESCARTADA_ALCANCE.
 * Tests unitarios con mocks: no tocan la base de datos ni Ollama.
 */
@ExtendWith(MockitoExtension.class)
class ColaRevisionTest {

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

    // ---------------------------------------------------------------- Opción C

    @Test
    void revisar_del_llm_se_persiste_como_requiere_revision_sin_promoverse() {
        ProcesoSercop p = procesoElegible();
        stubCascada(p);

        RecomendacionOportunidad abstencion = new RecomendacionOportunidad();
        abstencion.setEsOportunidadIbm(false);
        abstencion.setNivelConfianza("N/A");
        abstencion.setJustificacion("Renovación de nube sin indicar proveedor.");
        abstencion.setEstado(EstadoRecomendacion.REQUIERE_REVISION);
        abstencion.setFirmaInfoRevision(FirmaInfoRevision.calcular(p));
        when(evaluadorOportunidadLlmPort.evaluarOportunidad(any(), any(), any())).thenReturn(abstencion);

        Optional<RecomendacionOportunidad> resultado = servicio.recomendarPorOcid(p.getOcid());

        assertTrue(resultado.isPresent());
        assertEquals(EstadoRecomendacion.REQUIERE_REVISION, resultado.get().getEstado(),
                "El REVISAR del LLM debe ir a la cola humana, no promoverse a EVALUADA");

        ArgumentCaptor<RecomendacionOportunidad> captor = ArgumentCaptor.forClass(RecomendacionOportunidad.class);
        verify(recomendacionPersistencePort).guardarOActualizar(captor.capture(), any());
        assertEquals(EstadoRecomendacion.REQUIERE_REVISION, captor.getValue().getEstado());
        assertEquals(FirmaInfoRevision.calcular(p), captor.getValue().getFirmaInfoRevision(),
                "La firma debe persistirse para habilitar la Opción C");
    }

    @Test
    void opcion_c_con_informacion_sin_cambios_no_reevalua() {
        ProcesoSercop p = procesoElegible();
        RecomendacionOportunidad enCola = enColaRevision(p, FirmaInfoRevision.calcular(p));
        when(obtenerProcesosSercopService.buscarPorOcid(p.getOcid())).thenReturn(Optional.of(p));
        when(recomendacionPersistencePort.buscarPorOcid(p.getOcid())).thenReturn(Optional.of(enCola));

        Optional<RecomendacionOportunidad> resultado = servicio.recomendarPorOcid(p.getOcid());

        assertTrue(resultado.isPresent());
        assertEquals(EstadoRecomendacion.REQUIERE_REVISION, resultado.get().getEstado(),
                "Sin información nueva el caso sigue en la cola humana");
        verify(evaluadorOportunidadLlmPort, never()).evaluarOportunidad(any(), any(), any());
        verify(recomendacionPersistencePort, never()).guardarOActualizar(any(), any());
    }

    @Test
    void opcion_c_con_informacion_cambiada_reevalua() {
        ProcesoSercop p = procesoElegible();
        // Firma guardada = de una versión anterior de la descripción (p.ej. antes de re-ingestar el pliego)
        when(obtenerProcesosSercopService.buscarPorOcid(p.getOcid())).thenReturn(Optional.of(p));
        when(recomendacionPersistencePort.buscarPorOcid(p.getOcid()))
                .thenReturn(Optional.of(enColaRevision(p, "firma-de-version-anterior")));
        stubCascada(p);
        when(evaluadorOportunidadLlmPort.evaluarOportunidad(any(), any(), any()))
                .thenReturn(evaluada(p, "ALTO"));

        Optional<RecomendacionOportunidad> resultado = servicio.recomendarPorOcid(p.getOcid());

        assertTrue(resultado.isPresent());
        assertEquals(EstadoRecomendacion.EVALUADA, resultado.get().getEstado(),
                "Con información nueva el caso se resuelve solo (Opción C)");
        verify(evaluadorOportunidadLlmPort, times(1)).evaluarOportunidad(any(), any(), any());
    }

    // ---------------------------------------------------------------- resolución humana

    @Test
    void resolver_si_medio_convierte_en_evaluada() {
        ProcesoSercop p = procesoElegible();
        when(recomendacionPersistencePort.buscarPorOcid(p.getOcid()))
                .thenReturn(Optional.of(enColaRevision(p, "firma-x")));
        when(recomendacionPersistencePort.guardarOActualizar(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        Optional<RecomendacionOportunidad> resultado = servicio.resolverRevision(p.getOcid(), "SI", "MEDIO");

        assertTrue(resultado.isPresent());
        assertEquals(EstadoRecomendacion.EVALUADA, resultado.get().getEstado());
        assertTrue(resultado.get().isEsOportunidadIbm());
        assertEquals("MEDIO", resultado.get().getNivelConfianza());
        assertNull(resultado.get().getFirmaInfoRevision(), "Resuelto: sale de la Opción C");
        assertTrue(resultado.get().getJustificacion().contains("Resuelto por revisión humana"),
                "La causa original del LLM debe conservarse y anotarse la resolución");

        ArgumentCaptor<RecomendacionOportunidad> captor = ArgumentCaptor.forClass(RecomendacionOportunidad.class);
        verify(recomendacionPersistencePort).guardarOActualizar(captor.capture(), org.mockito.ArgumentMatchers.isNull());
        assertEquals(EstadoRecomendacion.EVALUADA, captor.getValue().getEstado());
    }

    @Test
    void resolver_si_sin_nivel_es_rechazado() {
        ProcesoSercop p = procesoElegible();
        when(recomendacionPersistencePort.buscarPorOcid(p.getOcid()))
                .thenReturn(Optional.of(enColaRevision(p, "firma-x")));

        // El CHECK de BD exige una confianza que acredite la oportunidad.
        assertThrows(IllegalArgumentException.class,
                () -> servicio.resolverRevision(p.getOcid(), "SI", null));
    }

    @Test
    void resolver_no_convierte_en_descartada_alcance() {
        ProcesoSercop p = procesoElegible();
        when(recomendacionPersistencePort.buscarPorOcid(p.getOcid()))
                .thenReturn(Optional.of(enColaRevision(p, "firma-x")));
        when(recomendacionPersistencePort.guardarOActualizar(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        Optional<RecomendacionOportunidad> resultado = servicio.resolverRevision(p.getOcid(), "NO", null);

        assertTrue(resultado.isPresent());
        assertEquals(EstadoRecomendacion.DESCARTADA_ALCANCE, resultado.get().getEstado());
        assertFalse(resultado.get().isEsOportunidadIbm());
        assertEquals("N/A", resultado.get().getNivelConfianza());
    }

    @Test
    void resolver_decision_desconocida_es_rechazada() {
        ProcesoSercop p = procesoElegible();
        when(recomendacionPersistencePort.buscarPorOcid(p.getOcid()))
                .thenReturn(Optional.of(enColaRevision(p, "firma-x")));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.resolverRevision(p.getOcid(), "TAL VEZ", null));
    }

    @Test
    void resolver_ocid_fuera_de_la_cola_no_hace_nada() {
        ProcesoSercop p = procesoElegible();
        RecomendacionOportunidad yaResuelta = new RecomendacionOportunidad();
        yaResuelta.setOcid(p.getOcid());
        yaResuelta.setEstado(EstadoRecomendacion.EVALUADA);
        when(recomendacionPersistencePort.buscarPorOcid(p.getOcid())).thenReturn(Optional.of(yaResuelta));

        assertTrue(servicio.resolverRevision(p.getOcid(), "SI", "ALTO").isEmpty());
        verify(recomendacionPersistencePort, never()).guardarOActualizar(any(), any());
    }

    @Test
    void obtenerColaRevision_delega_en_el_puerto() {
        when(recomendacionPersistencePort.obtenerColaRevision(20, 0)).thenReturn(List.of());

        assertTrue(servicio.obtenerColaRevision(20, 0).isEmpty());
        verify(recomendacionPersistencePort).obtenerColaRevision(20, 0);
    }

    // ---------------------------------------------------------------- fixtures

    /** Proceso elegible para validador y reglas duras (sin palabras de descarte). */
    private ProcesoSercop procesoElegible() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-cola");
        p.setTitulo("Plataforma de observabilidad en nube híbrida");
        p.setDescripcion("Implementación de APM, análisis de logs y trazabilidad con OpenShift y software de plataforma");
        p.setMonto(new BigDecimal("180000"));
        p.setMetodo("open");
        return p;
    }

    private RecomendacionOportunidad enColaRevision(ProcesoSercop p, String firma) {
        RecomendacionOportunidad r = new RecomendacionOportunidad();
        r.setOcid(p.getOcid());
        r.setTitulo(p.getTitulo());
        r.setDescripcion(p.getDescripcion());
        r.setEsOportunidadIbm(false);
        r.setNivelConfianza("N/A");
        r.setJustificacion("Renovación de nube sin indicar proveedor.");
        r.setEstado(EstadoRecomendacion.REQUIERE_REVISION);
        r.setFirmaInfoRevision(firma);
        return r;
    }

    private RecomendacionOportunidad evaluada(ProcesoSercop p, String nivel) {
        RecomendacionOportunidad r = new RecomendacionOportunidad();
        r.setOcid(p.getOcid());
        r.setEsOportunidadIbm(true);
        r.setNivelConfianza(nivel);
        r.setJustificacion("Encaje directo.");
        r.setEstado(EstadoRecomendacion.EVALUADA);
        return r;
    }

    /** Stub de la ruta de evaluación profunda hasta el LLM (proceso + similitudes persistidas). */
    private void stubCascada(ProcesoSercop p) {
        when(obtenerProcesosSercopService.buscarPorOcid(p.getOcid())).thenReturn(Optional.of(p));
        when(recomendacionPersistencePort.buscarPorOcid(p.getOcid())).thenReturn(Optional.empty());
        when(similitudPersistencePort.buscarPorOcid(p.getOcid()))
                .thenReturn(List.of(new SimilitudProcesoProducto(1L, p.getOcid(), 7L, "Instana", 0.81, 0.72, "hash", null)));
        when(productoIbmPersistencePort.buscarPorId(7L)).thenReturn(Optional.of(producto("Instana")));
        when(configuracionGranite.getModelo()).thenReturn("granite-embedding:278m");
    }

    private ProductoIbm producto(String nombre) {
        ProductoIbm p = new ProductoIbm();
        p.setId(7L);
        p.setNombre(nombre);
        return p;
    }
}
