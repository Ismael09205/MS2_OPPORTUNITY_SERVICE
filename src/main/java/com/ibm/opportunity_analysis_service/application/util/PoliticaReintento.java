package com.ibm.opportunity_analysis_service.application.util;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Política de reintento de ERROR_EVALUACION (control REVISAR, decisión del usuario 2026-10-24).
 *
 * <p>Un ERROR_EVALUACION es un fallo de infraestructura (túnel LLM caído, timeout, JSON
 * inválido), nunca un veredicto sobre el proceso, así que se reintenta — pero con espera
 * creciente para no martillar un servicio caído:</p>
 *
 * <pre>
 * intento 1  (inmediato, ya ejecutado)  → espera 5 min
 * intento 2                             → espera 15 min
 * intento 3                             → espera 1 h
 * intento 4                             → espera 4 h
 * intento 5                             → espera 8 h
 * intento 6  (último, ≈13h20m de fallos seguidos ≈ 1 día)
 *   → caso ESTANCADO: sin reprogramación + WARN; espera revisión manual
 *     o un cambio de información del proceso.
 * </pre>
 *
 * <p>Los fallos transitorios (el túnel se reinicia) se recuperan en los 2-3 primeros intentos.
 * El contador vuelve a 0 en cuanto un intento produce veredicto. Solo aplica a
 * ERROR_EVALUACION: EVALUADA y DESCARTADA_ALCANCE son definitivos, y REQUIERE_REVISION está
 * en la cola humana (se re-evalúa solo si cambió la firma, Opción C).</p>
 *
 * <p>La exclusión de los casos agotados se hace en la query
 * {@code SimilitudProcesoProductoJpaRepository.findPendientesDeEvaluacion}
 * ({@code intentos < MAX_INTENTOS} y {@code proximo_intento_at IS NULL OR <= now()}).</p>
 */
public final class PoliticaReintento {

    /** Fallos seguidos a partir de los cuales el caso se considera estancado. */
    public static final int MAX_INTENTOS = 6;

    /**
     * Espera tras cada intento fallido, en orden (índice intento-1). La sexta posición es
     * informativa: tras el intento 6 no se reprograma.
     */
    private static final Duration[] ESPERAS = {
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            Duration.ofHours(4),
            Duration.ofHours(8)
    };

    private PoliticaReintento() {
    }

    /** true si ya se agotaron los intentos: no se reprograma y se avisa con WARN. */
    public static boolean agotado(int intentosRealizados) {
        return intentosRealizados >= MAX_INTENTOS;
    }

    /**
     * Espera antes del siguiente intento tras {@code intentosRealizados} fallos seguidos.
     *
     * @throws IllegalArgumentException si intentosRealizados < 1 o el caso está agotado
     */
    public static Duration esperaTrasIntento(int intentosRealizados) {
        if (intentosRealizados < 1) {
            throw new IllegalArgumentException(
                    "Los intentos se cuentan desde 1 (recibido: " + intentosRealizados + ")");
        }
        if (agotado(intentosRealizados)) {
            throw new IllegalStateException(
                    "Caso estancado tras " + intentosRealizados + " intentos: no se reprograma.");
        }
        return ESPERAS[intentosRealizados - 1];
    }

    /** Instante del próximo reintento, o null si el caso está agotado. */
    public static OffsetDateTime proximoInstante(int intentosRealizados) {
        if (agotado(intentosRealizados)) {
            return null;
        }
        return OffsetDateTime.now().plus(esperaTrasIntento(intentosRealizados));
    }
}
