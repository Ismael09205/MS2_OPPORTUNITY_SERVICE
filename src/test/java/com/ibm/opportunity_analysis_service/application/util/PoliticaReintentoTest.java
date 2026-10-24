package com.ibm.opportunity_analysis_service.application.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Política de reintento de ERROR_EVALUACION (decisión del usuario 2026-10-24):
 * backoff 5m→15m→1h→4h→8h y tope de 6 intentos (≈1 día) con caso estancado sin
 * reprogramación. Solo se comprueba la política, sin BD ni LLM.
 */
class PoliticaReintentoTest {

    @Test
    void backoff_sigue_la_secuencia_acordada() {
        assertEquals(Duration.ofMinutes(5), PoliticaReintento.esperaTrasIntento(1));
        assertEquals(Duration.ofMinutes(15), PoliticaReintento.esperaTrasIntento(2));
        assertEquals(Duration.ofHours(1), PoliticaReintento.esperaTrasIntento(3));
        assertEquals(Duration.ofHours(4), PoliticaReintento.esperaTrasIntento(4));
        assertEquals(Duration.ofHours(8), PoliticaReintento.esperaTrasIntento(5));
    }

    @Test
    void el_tope_es_6_intentos_aprox_un_dia() {
        assertEquals(6, PoliticaReintento.MAX_INTENTOS,
                "El tope aprobado es 6 intentos ≈ 1 día de fallos seguidos");
        assertFalse(PoliticaReintento.agotado(5), "El quinto fallo aún se reprograma");
        assertTrue(PoliticaReintento.agotado(6), "El sexto fallo agota: caso estancado");
        assertTrue(PoliticaReintento.agotado(7), "Los fallos posteriores siguen agotados");
        assertFalse(PoliticaReintento.agotado(0), "Sin fallos no hay agotamiento");
    }

    @Test
    void tras_el_tope_no_se_reprograma() {
        assertNull(PoliticaReintento.proximoInstante(6),
                "Un caso estancado no debe tener próximo intento automático");
        assertThrows(IllegalStateException.class, () -> PoliticaReintento.esperaTrasIntento(6));
    }

    @Test
    void el_proximo_intento_programa_en_el_futuro() {
        var proximo = PoliticaReintento.proximoInstante(1);
        assertNotNull(proximo);
        assertTrue(proximo.isAfter(java.time.OffsetDateTime.now().plus(Duration.ofMinutes(4))),
                "Tras el primer fallo espera ~5 min");
    }

    @Test
    void intentos_invalidos_se_rechazan() {
        assertThrows(IllegalArgumentException.class, () -> PoliticaReintento.esperaTrasIntento(0));
        assertThrows(IllegalArgumentException.class, () -> PoliticaReintento.esperaTrasIntento(-1));
    }
}
