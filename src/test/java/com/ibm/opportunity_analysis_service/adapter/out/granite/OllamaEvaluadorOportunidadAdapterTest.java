package com.ibm.opportunity_analysis_service.adapter.out.granite;

import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.application.util.FirmaInfoRevision;
import com.ibm.opportunity_analysis_service.domain.entity.EstadoRecomendacion;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del contrato de la configuración oficial C4 (corrida 6) en el adaptador Ollama:
 * el mapeo 3 vías → estados (SI→EVALUADA, NO→DESCARTADA_ALCANCE, REVISAR→REQUIERE_REVISION),
 * las reglas de coherencia del prompt (SI sin nivel = respuesta inválida, no veredicto) y la
 * carga del recurso few-shot estático de los 11 ejemplos medidos. No se hace ninguna llamada
 * al modelo: solo se prueba el parseo de la respuesta.
 */
class OllamaEvaluadorOportunidadAdapterTest {

    private final OllamaEvaluadorOportunidadAdapter adapter = crearAdapter();

    private static OllamaEvaluadorOportunidadAdapter crearAdapter() {
        ConfiguracionGranite cfg = new ConfiguracionGranite();
        cfg.setUrl("http://localhost:11435");   // solo para construir el RestClient: no se conecta
        cfg.setModeloLlm("granite3.1-dense:8b");
        return new OllamaEvaluadorOportunidadAdapter(cfg, JsonMapper.builder().build());
    }

    private ProcesoSercop proceso() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-qa");
        p.setTitulo("Plataforma de observabilidad en nube híbrida");
        p.setDescripcion("Implementación de APM y trazabilidad con OpenShift y software de plataforma");
        p.setMonto(new BigDecimal("180000"));
        return p;
    }

    // ---------------------------------------------------------------- mapeo 3 vías

    @Test
    void si_con_nivel_alto_se_persiste_como_evaluada_oportunidad() {
        String json = """
                {"decision": "SI", "nivel": "ALTO", "producto_recomendado": "Instana",
                 "justificacion": "Encaje directo con APM enterprise."}""";

        RecomendacionOportunidad rec = adapter.parsearRespuesta(proceso(), json);

        assertEquals(EstadoRecomendacion.EVALUADA, rec.getEstado());
        assertTrue(rec.isEsOportunidadIbm());
        assertEquals("ALTO", rec.getNivelConfianza());
        assertEquals("Instana", rec.getProductoRecomendado());
        assertNotNull(rec.getFechaEvaluacion());
    }

    @Test
    void no_del_llm_se_persiste_como_descartada_alcance() {
        // Decisión del usuario 2026-10-23: el NO del modelo es un descarte de alcance.
        String json = """
                {"decision": "NO", "nivel": "NA", "producto_recomendado": null,
                 "justificacion": "Contenido propietario de terceros (SciELO)."}""";

        RecomendacionOportunidad rec = adapter.parsearRespuesta(proceso(), json);

        assertEquals(EstadoRecomendacion.DESCARTADA_ALCANCE, rec.getEstado());
        assertFalse(rec.isEsOportunidadIbm());
        assertEquals("N/A", rec.getNivelConfianza());
        assertTrue(EstadoRecomendacion.esDefinitivo(rec.getEstado()),
                "El descarte del LLM es un juicio definitivo");
        assertEquals("Contenido propietario de terceros (SciELO).", rec.getJustificacion());
    }

    @Test
    void revisar_se_persiste_con_causa_y_firma_para_opcion_c() {
        String json = """
                {"decision": "REVISAR", "nivel": "NA", "producto_recomendado": null,
                 "justificacion": "Renovación de nube sin indicar proveedor; podría ser monomarca ajena."}""";

        RecomendacionOportunidad rec = adapter.parsearRespuesta(proceso(), json);

        assertEquals(EstadoRecomendacion.REQUIERE_REVISION, rec.getEstado());
        assertFalse(rec.isEsOportunidadIbm(), "La abstención no es una oportunidad confirmada");
        assertEquals("N/A", rec.getNivelConfianza());
        assertEquals("Renovación de nube sin indicar proveedor; podría ser monomarca ajena.",
                rec.getJustificacion(), "La causa del modelo es la causa visible de la cola");
        assertEquals(FirmaInfoRevision.calcular(proceso()), rec.getFirmaInfoRevision(),
                "La firma habilita el reintento solo si cambia la información (Opción C)");
        assertFalse(EstadoRecomendacion.esDefinitivo(rec.getEstado()),
                "REQUIERE_REVISION no es definitivo: va a la cola humana");
    }

    // ---------------------------------------------------------------- coherencia C4

    @Test
    void si_sin_nivel_valido_es_respuesta_invalida_no_veredicto() {
        // Regla de coherencia del prompt C4: SI exige nivel ALTO o MEDIO (fallo medido en C3).
        String json = """
                {"decision": "SI", "nivel": "NA", "producto_recomendado": "watsonx",
                 "justificacion": "Parece interesante."}""";

        RecomendacionOportunidad rec = adapter.parsearRespuesta(proceso(), json);

        assertEquals(EstadoRecomendacion.ERROR_EVALUACION, rec.getEstado());
        assertFalse(rec.isEsOportunidadIbm());
    }

    @Test
    void decision_desconocida_es_error_no_veredicto() {
        String json = """
                {"decision": "TAL VEZ", "nivel": "NA", "justificacion": "dudoso"}""";

        RecomendacionOportunidad rec = adapter.parsearRespuesta(proceso(), json);

        assertEquals(EstadoRecomendacion.ERROR_EVALUACION, rec.getEstado());
        assertFalse(rec.isEsOportunidadIbm());
    }

    @Test
    void json_ilegible_es_error_no_veredicto() {
        RecomendacionOportunidad rec = adapter.parsearRespuesta(proceso(), "esto no es json");

        assertEquals(EstadoRecomendacion.ERROR_EVALUACION, rec.getEstado());
        assertFalse(rec.isEsOportunidadIbm());
    }

    @Test
    void si_medio_es_oportunidad_con_nivel_medio() {
        String json = """
                {"decision": "SI", "nivel": "MEDIO", "producto_recomendado": "Cloud Pak",
                 "justificacion": "Alcance mixto: integración + plataforma."}""";

        RecomendacionOportunidad rec = adapter.parsearRespuesta(proceso(), json);

        assertEquals(EstadoRecomendacion.EVALUADA, rec.getEstado());
        assertEquals("MEDIO", rec.getNivelConfianza());
        assertTrue(rec.isEsOportunidadIbm());
    }

    @Test
    void no_y_revisar_no_admiten_producto_recomendado() {
        RecomendacionOportunidad no = adapter.parsearRespuesta(proceso(),
                "{\"decision\": \"NO\", \"nivel\": \"NA\", \"producto_recomendado\": \"QRadar\", \"justificacion\": \"x\"}");
        assertNull(no.getProductoRecomendado(), "C4: producto_recomendado solo si decision=SI");

        RecomendacionOportunidad rev = adapter.parsearRespuesta(proceso(),
                "{\"decision\": \"REVISAR\", \"nivel\": \"NA\", \"producto_recomendado\": \"QRadar\", \"justificacion\": \"x\"}");
        assertNull(rev.getProductoRecomendado());
    }

    // ---------------------------------------------------------------- few-shot estático

    @Test
    void carga_los_11_ejemplos_few_shot_medidos() {
        assertEquals(11, adapter.ejemplosFewShot().size(),
                "La corrida 6 midió 82,6% con exactamente estos 11 ejemplos; "
                        + "si el número cambia, hay que regenerar y re-medir el experimento");
        assertTrue(adapter.ejemplosFewShot().stream().allMatch(e -> !e.prompt().isBlank()),
                "Cada ejemplo debe traer su prompt de proceso completo");
    }
}
