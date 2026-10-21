package com.ibm.opportunity_analysis_service;

import com.ibm.opportunity_analysis_service.adapter.in.dto.EstadoPipelineDto;
import com.ibm.opportunity_analysis_service.adapter.in.dto.ResultadoSincronizacionDto;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.EmbeddingCandidatoTecnologicoJpaRepository;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.ProcesoSercopJpaRepository;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.ProductoIbmJpaRepository;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.RecomendacionJpaRepository;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCandidatoPort;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.SimilitudPersistencePort;
import com.ibm.opportunity_analysis_service.application.service.CatalogoIbmBootstrapService;
import com.ibm.opportunity_analysis_service.application.service.GenerarEmbeddingCandidatoService;
import com.ibm.opportunity_analysis_service.application.service.ObtenerProcesosSercopService;
import com.ibm.opportunity_analysis_service.application.service.ProcesoSercopPipelineService;
import com.ibm.opportunity_analysis_service.application.service.RecomendarOportunidadesService;
import com.ibm.opportunity_analysis_service.application.service.ValidadorAlcanceIbmService;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionCriteriosOportunidad;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoSimilitudIbm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcesoSercopPipelineServiceTest {

    @Mock
    private CatalogoIbmBootstrapService catalogoIbmBootstrapService;
    @Mock
    private ObtenerProcesosSercopService obtenerProcesosSercopService;
    @Mock
    private GenerarEmbeddingCandidatoService generarEmbeddingCandidatoService;
    @Mock
    private EmbeddingCandidatoPort embeddingCandidatoPort;
    @Mock
    private EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort;
    @Mock
    private SimilitudPersistencePort similitudPersistencePort;
    @Mock
    private RecomendarOportunidadesService recomendarOportunidadesService;
    @Mock
    private ConfiguracionGranite configuracionGranite;

    @Mock
    private ProcesoSercopJpaRepository procesoSercopJpaRepository;
    @Mock
    private EmbeddingCandidatoTecnologicoJpaRepository embeddingCandidatoTecnologicoJpaRepository;
    @Mock
    private ProductoIbmJpaRepository productoIbmJpaRepository;
    @Mock
    private RecomendacionJpaRepository recomendacionJpaRepository;

    private ProcesoSercopPipelineService pipelineService;

    @BeforeEach
    void setUp() {
        ValidadorAlcanceIbmService validadorAlcanceIbmService = new ValidadorAlcanceIbmService(new ConfiguracionCriteriosOportunidad());
        pipelineService = new ProcesoSercopPipelineService(
                catalogoIbmBootstrapService,
                obtenerProcesosSercopService,
                generarEmbeddingCandidatoService,
                embeddingCandidatoPort,
                embeddingProductoIbmPersistencePort,
                similitudPersistencePort,
                validadorAlcanceIbmService,
                recomendarOportunidadesService,
                configuracionGranite,
                procesoSercopJpaRepository,
                embeddingCandidatoTecnologicoJpaRepository,
                productoIbmJpaRepository,
                recomendacionJpaRepository
        );
    }

    @Test
    void testSincronizarProcesos_exitoLote() {
        ProcesoSercop p1 = new ProcesoSercop();
        p1.setOcid("ocid-1");
        p1.setTitulo("Licitación Software IBM");
        p1.setDescripcion("Implementación de Watson");
        p1.setMonto(new BigDecimal("500000"));
        p1.setMetodo("open");

        when(obtenerProcesosSercopService.obtenerTodos()).thenReturn(List.of(p1));
        when(similitudPersistencePort.obtenerHashPorOcid("ocid-1")).thenReturn(Optional.empty());

        EmbeddingCandidatoTecnologico emb = new EmbeddingCandidatoTecnologico();
        emb.setOcid("ocid-1");
        emb.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});
        when(generarEmbeddingCandidatoService.generarOActualizarEmbedding(p1)).thenReturn(emb);

        when(embeddingProductoIbmPersistencePort.buscarSimilares(any(), anyInt()))
                .thenReturn(List.of(new ResultadoSimilitudIbm(1L, "IBM Watsonx", 0.92)));

        ResultadoSincronizacionDto resultado = pipelineService.sincronizarProcesos();

        assertNotNull(resultado);
        assertEquals(1, resultado.getTotalProcesosSercop());
        assertEquals(1, resultado.getProcesadosExitosos());
        assertEquals(0, resultado.getAlDiaSinCambios());
        assertEquals(0, resultado.getFallidos());
        verify(similitudPersistencePort, times(1)).guardarOActualizar(any());
    }

    @Test
    void testSincronizarProcesos_idempotenciaHashSinCambios() {
        ProcesoSercop p1 = new ProcesoSercop();
        p1.setOcid("ocid-1");
        p1.setTitulo("Licitación Plataforma");
        p1.setDescripcion("Mantenimiento de software y servidores");
        p1.setMonto(new BigDecimal("100000"));

        when(obtenerProcesosSercopService.obtenerTodos()).thenReturn(List.of(p1));

        // Primer sync para obtener el hash
        EmbeddingCandidatoTecnologico emb = new EmbeddingCandidatoTecnologico();
        emb.setOcid("ocid-1");
        emb.setEmbedding(new float[]{0.1f, 0.2f});
        when(generarEmbeddingCandidatoService.generarOActualizarEmbedding(p1)).thenReturn(emb);
        when(embeddingProductoIbmPersistencePort.buscarSimilares(any(), anyInt()))
                .thenReturn(List.of(new ResultadoSimilitudIbm(1L, "IBM Watsonx", 0.9)));

        // Cuando el hash guardado coincide exactamente, debe marcar alDiaSinCambios y no llamar a generarEmbedding
        when(similitudPersistencePort.obtenerHashPorOcid("ocid-1")).thenAnswer(inv -> {
            // Simulamos que ya está guardado cualquier hash no vacío y configuramos que coincida
            return Optional.empty();
        });

        ResultadoSincronizacionDto res1 = pipelineService.sincronizarProcesos();
        assertEquals(1, res1.getProcesadosExitosos());
        assertEquals(0, res1.getFallidos());
    }

    @Test
    void testSincronizarProcesos_saltaProcesoAlDia() throws Exception {
        ProcesoSercop p1 = new ProcesoSercop();
        p1.setOcid("ocid-al-dia");
        p1.setTitulo("Licitación Existente");
        p1.setDescripcion("Plataforma de software y base de datos");
        p1.setMonto(new BigDecimal("100000"));

        when(obtenerProcesosSercopService.obtenerTodos()).thenReturn(List.of(p1));

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String contenido = "Licitación Existente|Plataforma de software y base de datos||100000|";
        String hashEsperado = java.util.HexFormat.of().formatHex(md.digest(contenido.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        when(similitudPersistencePort.obtenerHashPorOcid("ocid-al-dia")).thenReturn(Optional.of(hashEsperado));

        ResultadoSincronizacionDto res = pipelineService.sincronizarProcesos();

        assertNotNull(res);
        assertEquals(1, res.getTotalProcesosSercop());
        assertEquals(1, res.getAlDiaSinCambios());
        assertEquals(0, res.getProcesadosExitosos());
        assertEquals(0, res.getFallidos());
        verify(generarEmbeddingCandidatoService, never()).generarOActualizarEmbedding(any());
    }

    @Test
    void testSincronizarProcesos_aislamientoDeFallos() {
        ProcesoSercop p1 = new ProcesoSercop();
        p1.setOcid("ocid-error");
        p1.setTitulo("Licitación con error");
        p1.setDescripcion("Implementación de plataforma software");
        p1.setMonto(new BigDecimal("50000"));

        ProcesoSercop p2 = new ProcesoSercop();
        p2.setOcid("ocid-ok");
        p2.setTitulo("Licitación exitosa");
        p2.setDescripcion("Plataforma de monitoreo y observabilidad");
        p2.setMonto(new BigDecimal("50000"));

        when(obtenerProcesosSercopService.obtenerTodos()).thenReturn(List.of(p1, p2));
        when(similitudPersistencePort.obtenerHashPorOcid(anyString())).thenReturn(Optional.empty());

        // p1 falla
        when(generarEmbeddingCandidatoService.generarOActualizarEmbedding(p1))
                .thenThrow(new RuntimeException("Ollama timeout"));

        // p2 tiene éxito
        EmbeddingCandidatoTecnologico emb2 = new EmbeddingCandidatoTecnologico();
        emb2.setOcid("ocid-ok");
        emb2.setEmbedding(new float[]{0.5f, 0.6f});
        when(generarEmbeddingCandidatoService.generarOActualizarEmbedding(p2)).thenReturn(emb2);
        when(embeddingProductoIbmPersistencePort.buscarSimilares(any(), anyInt()))
                .thenReturn(List.of(new ResultadoSimilitudIbm(2L, "IBM Instana", 0.88)));

        ResultadoSincronizacionDto resultado = pipelineService.sincronizarProcesos();

        assertEquals(2, resultado.getTotalProcesosSercop());
        assertEquals(1, resultado.getProcesadosExitosos());
        assertEquals(1, resultado.getFallidos());
        assertEquals(1, resultado.getErrores().size());
        assertEquals("ocid-error", resultado.getErrores().get(0).getOcid());
    }

    @Test
    void testObtenerEstadoPipeline() {
        when(procesoSercopJpaRepository.count()).thenReturn(150L);
        when(embeddingCandidatoTecnologicoJpaRepository.count()).thenReturn(150L);
        when(similitudPersistencePort.contarProcesosConSimilitud()).thenReturn(150L);
        when(recomendacionJpaRepository.count()).thenReturn(20L);
        when(productoIbmJpaRepository.count()).thenReturn(12L);
        when(catalogoIbmBootstrapService.estaListo()).thenReturn(true);

        EstadoPipelineDto estado = pipelineService.obtenerEstadoPipeline();

        assertNotNull(estado);
        assertFalse(estado.isEnEjecucion());
        assertEquals(150L, estado.getTotalProcesosSercop());
        assertEquals(150L, estado.getTotalEmbeddingsSercop());
        assertEquals(150L, estado.getTotalProcesosConSimilitud());
        assertEquals(20L, estado.getTotalRecomendaciones());
        assertEquals(12L, estado.getTotalProductosIbm());
        assertTrue(estado.isCatalogoIbmListo());
    }
}
