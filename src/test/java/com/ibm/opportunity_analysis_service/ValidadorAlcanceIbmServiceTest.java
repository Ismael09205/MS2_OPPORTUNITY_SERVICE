package com.ibm.opportunity_analysis_service;

import com.ibm.opportunity_analysis_service.application.service.ValidadorAlcanceIbmService;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionCriteriosOportunidad;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoValidacionAlcance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
}
