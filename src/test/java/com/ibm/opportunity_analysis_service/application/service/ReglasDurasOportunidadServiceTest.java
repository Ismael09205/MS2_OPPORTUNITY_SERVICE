package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Espejo JUnit de {@code experimentos/validacion_reglas.py} (Paso A): las 5 reglas duras con la
 * MISMA semántica validada sobre los 120 casos etiquetados (33 muertes, 33/33 NO correctos,
 * 0 fugas, 0 REVISAR muertos). Si este test cambia sin re-validar en Python, se rompe la
 * garantía medida de la corrida 6 (12/12 aciertos por regla, 27,5% del split sin tocar el LLM).
 */
class ReglasDurasOportunidadServiceTest {

    private final ReglasDurasOportunidadService servicio = new ReglasDurasOportunidadService();

    private ProcesoSercop proceso(String titulo, String descripcion, String categoria) {
        ProcesoSercop p = new ProcesoSercop();
        p.setOcid("ocid-test");
        p.setTitulo(titulo);
        p.setDescripcion(descripcion);
        p.setCategoriaCpc(categoria);
        return p;
    }

    private Optional<String> aplicar(String titulo, String descripcion, String categoria) {
        return servicio.aplicar(proceso(titulo, descripcion, categoria));
    }

    // ---------------------------------------------------------------- R1: categoría consultoría

    @Test
    void r1_categoria_desarrollo_consultoria_ti_dispara() {
        assertTrue(aplicar("Proyecto X", "Desc", "DESARROLLO_CONSULTORIA_TI").isPresent());
    }

    @Test
    void r1_ignora_espacios_y_mayusculas() {
        assertTrue(aplicar("Proyecto X", "Desc", "  Desarrollo_Consultoria_TI ").isPresent());
    }

    @Test
    void r1_otra_categoria_no_dispara() {
        assertTrue(aplicar("Proyecto X", "Desc", "CLOUD_COMPUTING").isEmpty());
        assertTrue(aplicar("Proyecto X", "Desc", null).isEmpty());
    }

    // ---------------------------------------------------------------- R2: antivirus

    @Test
    void r2_antivirus_en_titulo_dispara() {
        assertEquals("R2_ANTIVIRUS", aplicar("Renovación de antivirus corporativo", "Suministro de licencias", null).orElseThrow());
    }

    @Test
    void r2_antivirus_en_descripcion_dispara_con_mayusculas_y_tildes() {
        assertTrue(aplicar("Seguridad", "Adquisición de ANTIVÍRUS para la entidad", null).isPresent());
    }

    @Test
    void r2_sin_antivirus_no_dispara() {
        assertTrue(aplicar("Seguridad perimetral", "Solución SIEM enterprise", null).isEmpty());
    }

    // ---------------------------------------------------------------- R3: CAD/BIM solo en descripción

    @Test
    void r3_cad_bim_en_descripcion_dispara() {
        assertTrue(aplicar("Servicio de digitalización", "Ingeniería inversa CAD y modelado BIM del campus", null).isPresent());
    }

    @Test
    void r3_excepcion_del_usuario_cad_bim_en_titulo_NO_descarta() {
        // El título puede mencionar CAD/BIM como referencia sin ser el objeto del contrato.
        assertTrue(aplicar("Consultoría CAD y BIM para el Ministerio", "Asesoría técnica especializada", null).isEmpty());
    }

    @Test
    void r3_no_dispara_dentro_de_palabra() {
        assertTrue(aplicar("Servicio", "Abimado de estructuras", null).isEmpty());
    }

    // ---------------------------------------------------------------- R4: contenido de terceros

    @Test
    void r4_contenido_terceros_dispara() {
        assertTrue(aplicar("Suscripción SciELO", "Acceso a base de datos", null).isPresent());
        assertTrue(aplicar("Acceso Web of Science", "Colección de citas", null).isPresent());
    }

    @Test
    void r4_otras_palabras_no_dispara() {
        assertTrue(aplicar("Base de datos empresarial", "Db2 y gobierno de datos", null).isEmpty());
    }

    // ---------------------------------------------------------------- R5: renovación monomarca ajena

    @Test
    void r5_renovacion_mas_marca_tercero_sin_ibm_dispara() {
        assertTrue(aplicar("Renovación de licencias Oracle", "Soporte anual de la suite de base de datos", null).isPresent());
    }

    @Test
    void r5_con_tilde_tambien_dispara() {
        assertTrue(aplicar("Renovación de licencias Fortinet", "Renovación anual del firewall", null).isPresent());
    }

    @Test
    void r5_si_se_nombra_ibm_NO_dispara() {
        // Mismo contexto de renovación, pero el pliego también nombra IBM: podría ser mixto.
        assertTrue(aplicar("Renovación de licencias Oracle", "Migración a DB2 e integración IBM", null).isEmpty());
    }

    @Test
    void r5_sin_renovacion_NO_dispara() {
        assertTrue(aplicar("Adquisición de licencias Oracle", "Compra inicial de la suite", null).isEmpty());
    }

    // ---------------------------------------------------------------- general

    @Test
    void proceso_limpio_no_dispara_ninguna() {
        assertTrue(aplicar("Plataforma de observabilidad en nube híbrida",
                "Implementación de APM y trazabilidad con OpenShift", "CLOUD_COMPUTING").isEmpty());
    }

    @Test
    void normalizacion_es_identica_al_script_python() {
        assertEquals("renovacion de licencias oracle", ReglasDurasOportunidadService.normalizar("Renovación de licencias Oracle"));
        assertEquals("antivirus corporativo", ReglasDurasOportunidadService.normalizar("ANTIVÍRUS corporativo"));
        assertEquals("", ReglasDurasOportunidadService.normalizar(null));
    }
}
