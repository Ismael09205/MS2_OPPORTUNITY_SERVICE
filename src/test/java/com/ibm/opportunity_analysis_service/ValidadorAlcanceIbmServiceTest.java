package com.ibm.opportunity_analysis_service;

import com.ibm.opportunity_analysis_service.application.service.ValidadorAlcanceIbmService;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionCriteriosOportunidad;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoValidacionAlcance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorAlcanceIbmServiceTest {

    private ValidadorAlcanceIbmService validador;

    @BeforeEach
    void setUp() {
        ConfiguracionCriteriosOportunidad config = new ConfiguracionCriteriosOportunidad();
        validador = new ValidadorAlcanceIbmService(config);
    }

    @Test
    void testDescartarPorTerminoExcluido_cableado() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-1");
        p.setTitulo("Instalación de cableado estructurado para edificio central");
        p.setDescripcion("Puntos de red y fibra óptica");
        p.setMonto(new BigDecimal("80000"));

        ResultadoValidacionAlcance res = validador.validar(p);
        assertNotNull(res);
        assertFalse(res.isEsElegible());
        assertTrue(res.getMotivoDescarte().contains("cableado estructurado"));
    }

    @Test
    void testDescartarPorTerminoExcluido_toner() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-2");
        p.setTitulo("Suministro de tóner e insumos de impresión");
        p.setDescripcion("Recargas y cartuchos");
        p.setMonto(new BigDecimal("30000"));

        ResultadoValidacionAlcance res = validador.validar(p);
        assertNotNull(res);
        assertFalse(res.isEsElegible());
        assertTrue(res.getMotivoDescarte().toLowerCase().contains("toner"));
    }

    @Test
    void testDescartarPorMontoInsuficienteSinEnterprise() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-3");
        p.setTitulo("Mantenimiento correctivo menor");
        p.setDescripcion("Arreglo puntual de equipos varios");
        p.setMonto(new BigDecimal("3000")); // Debajo de 15,000 USD

        ResultadoValidacionAlcance res = validador.validar(p);
        assertNotNull(res);
        assertFalse(res.isEsElegible());
        assertTrue(res.getMotivoDescarte().contains("Presupuesto"));
    }

    @Test
    void testAprobarOportunidadEnterprise() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-4");
        p.setTitulo("Adquisición de plataforma de observabilidad y monitoreo de microservicios");
        p.setDescripcion("Implementación de APM, análisis de logs y trazabilidad en nube híbrida con OpenShift");
        p.setMonto(new BigDecimal("180000"));
        p.setMetodo("open");

        ResultadoValidacionAlcance res = validador.validar(p);
        assertNotNull(res);
        assertTrue(res.isEsElegible());
        assertTrue(res.isTienePalabrasEnterprise());
        assertTrue(res.getBonusAlcance() > 0.0);
    }

    // ---------------------------------------------------------------- 1.11 (H18)

    /**
     * Tarea 1.11 (H18): antes {@code texto.contains("ia")} casaba dentro de *licen<b>cia</b>s*,
     * *licen<b>cia</b>miento*, *ofimá<b>tica</b>*, *capacitación* o *hidrocar<b>ibu</b>ros*.
     * Medido el 2026-10-22 sobre los 381 procesos: <b>252 (66 %)</b> ganaban la keyword "ia" sin
     * mencionar IA. Con límites de palabra, esa falsa coincidencia desaparece.
     */
    @Test
    void testH18_ia_embebida_en_otras_palabras_no_cuenta() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-h18-licencias");
        p.setTitulo("Renovación de licencias de ofimática y capacitación de usuarios");
        p.setDescripcion("Suministro de licenciamiento de software de oficina y curso de capacitación");
        p.setMonto(new BigDecimal("180000")); // monto alto: no entra en el filtro Q2, aisla el efecto de "ia"
        p.setMetodo("open");

        ResultadoValidacionAlcance res = validador.validar(p);

        // "software" es keyword real; "ia" embebida en licencias/ofimática/capacitación no debe sumar.
        assertTrue(res.isEsElegible());
        assertTrue(res.isTienePalabrasEnterprise(), "\"software\" sí es keyword enterprise");
        // 1 keyword real → bonus 0.02. Si "ia" contara, serían ≥2 → 0.04 o más.
        assertEquals(0.02, res.getBonusAlcance(), 1e-9,
                "Solo debe contar 'software'; 'ia' embebida en licencias/ofimática/capacitación no cuenta");
    }

    @Test
    void testH18_ia_como_palabra_independiente_si_cuenta() {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-h18-ia-real");
        p.setTitulo("Implementación de IA generativa y plataforma de datos en la nube");
        p.setDescripcion("Proyecto de inteligencia artificial aplicada a analítica de clientes");
        p.setMonto(new BigDecimal("180000"));
        p.setMetodo("open");

        ResultadoValidacionAlcance res = validador.validar(p);

        assertTrue(res.isEsElegible());
        assertTrue(res.isTienePalabrasEnterprise());
        // keywords reales distintas: "ia", "plataforma", "nube", "inteligencia artificial", "analitica"
        assertTrue(res.getBonusAlcance() >= 0.10,
                "Un texto con IA real debe acumular bonus máximo: " + res.getBonusAlcance());
    }

    @Test
    void testH18_palabras_con_ia_embebida_real_no_cuentan() {
        // Falsos positivos reales medidos en el gold set: "subsidiarias" (38), "consultoría" (16),
        // "especializada" (14), "mediante" (10) contienen "ia" embebida. Con el contains() viejo
        // este proceso ganaba la keyword "ia" y su bonusAlcance subía a 0.02 indebidamente.
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-h18-consultoria");
        p.setTitulo("Consultoría especializada mediante personal subsidiario en planta");
        p.setDescripcion("Servicio de asesoría de procesos para la empresa pública");
        p.setMonto(new BigDecimal("180000")); // monto alto: aísla el efecto de la keyword

        ResultadoValidacionAlcance res = validador.validar(p);

        assertTrue(res.isEsElegible());
        assertFalse(res.isTienePalabrasEnterprise(),
                "'consultoría', 'especializada', 'mediante' y 'subsidiarias' contienen 'ia' "
                        + "embebida pero no son la keyword 'ia' como palabra independiente");
        assertEquals(0.0, res.getBonusAlcance(), 1e-9,
                "Ninguna keyword enterprise real: el bonus debe ser 0");
    }

    @Test
    void testH18_q2_monto_bajo_pide_dos_keywords_reales() {
        // Q2: monto < 15k con ≥2 keywords reales sobrevive; con solo coincidencias embebidas no.
        ProcesoSercop conSoloEmbebidas = new ProcesoSercop();
        conSoloEmbebidas.setOcid("ocid-h18-q2-falso");
        conSoloEmbebidas.setTitulo("Renovación de licencias de ofimática y suministro de capacitación");
        conSoloEmbebidas.setDescripcion("Contratación de licenciamiento y programa de formación de usuarios");
        conSoloEmbebidas.setMonto(new BigDecimal("14500")); // < 15k

        ResultadoValidacionAlcance falso = validador.validar(conSoloEmbebidas);
        assertFalse(falso.isEsElegible(),
                "Q2 no debe sobrevivir con 'ia' embebida en licencias/ofimática/capacitación");

        ProcesoSercop conDosReales = new ProcesoSercop();
        conDosReales.setOcid("ocid-h18-q2-real");
        conDosReales.setTitulo("Adquisición de software de plataforma empresarial");
        conDosReales.setDescripcion("Suministro de licenciamiento para el área de usuarios");
        conDosReales.setMonto(new BigDecimal("14500")); // < 15k

        ResultadoValidacionAlcance real = validador.validar(conDosReales);
        assertTrue(real.isEsElegible(),
                "Q2 sí debe sobrevivir con 2 keywords enterprise reales y monto bajo");
        assertEquals(0.04, real.getBonusAlcance(), 1e-9,
                "Deben contar exactamente 2 keywords reales (software, plataforma)");
    }

    @Test
    void testH18_variantes_acentuadas_cuentan_una_sola_vez() {
        // "analítica" y "analitica" son la misma keyword tras normalizar: deduplicar evita
        // inflar bonusAlcance (y por tanto score_compuesto).
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-h18-acento");
        p.setTitulo("Servicio de analítica avanzada de datos y gobernanza de datos corporativos");
        p.setDescripcion("Plataforma de analitica, gobierno de datos y arquitectura empresarial");
        p.setMonto(new BigDecimal("180000"));
        p.setMetodo("open");

        ResultadoValidacionAlcance res = validador.validar(p);

        assertTrue(res.isEsElegible());
        // Keywords distintas: analitica (dedup de "analítica"+"analitica"), plataforma,
        // gobierno de datos, arquitectura empresarial = 4 → 0.08
        assertEquals(0.08, res.getBonusAlcance(), 1e-9,
                "Las variantes acentuadas de una keyword deben deduplicarse: " + res.getBonusAlcance());
    }
}
