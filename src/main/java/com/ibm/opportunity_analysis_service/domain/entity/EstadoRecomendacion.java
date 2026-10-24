package com.ibm.opportunity_analysis_service.domain.entity;

/**
 * Origen / estado de una fila de la tabla {@code recomendacion}.
 *
 * Fase 1 (tarea 1.1): el campo existe para que {@code es_oportunidad_ibm=true} signifique algo.
 * Antes de este cambio, una preselección vectorial sin evaluar era indistinguible de una
 * recomendación evaluada por el LLM (hallazgo H1) y el único test era un
 * {@code justificacion.startsWith("Preselección...")} frágil (H2).
 *
 * Valores:
 *  - PRESELECCION_VECTORIAL: afinidad vectorial pura, SIN pasar por validador ni LLM.
 *    Nunca debe presentarse como veredicto.
 *  - EVALUADA: evaluación profunda completada (LLM Granite o Bob) con resultado definitivo.
 *  - DESCARTADA_ALCANCE: el {@code ValidadorAlcanceIbmService} la descartó (motivo en justificacion).
 *    También es el destino del "NO" del LLM (3 vías: SI/NO/REVISAR — decisión del usuario 2026-10-23).
 *  - ERROR_EVALUACION: la evaluación falló (LLM cao/timeout/respuesta ilegible).
 *    NO es definitiva: permite reintento en {@code recomendarPorOcid} y en el scheduler.
 *  - REQUIERE_REVISION: el LLM se abstuvo (decision=REVISAR) con causa escrita en justificacion.
 *    NO es definitiva ni entra en el scheduler: va a la cola humana (herramienta MCP en Bob).
 *    Reintento automático SOLO si cambió la información del proceso (Opción C, aprobada por el
 *    usuario 2026-10-23): se compara {@code firmaInfoRevision} (hash de título+descripción)
 *    guardada al abstenerse contra la firma actual; si es igual, no se gasta llamada al LLM.
 */
public enum EstadoRecomendacion {

    PRESELECCION_VECTORIAL,
    EVALUADA,
    DESCARTADA_ALCANCE,
    ERROR_EVALUACION,
    REQUIERE_REVISION;

    /**
     * true si la fila refleja un juicio definitivo (no una evaluación pendiente o fallida).
     * Una fila sin estado (legada, previa a la Fase 1) se considera NO definitiva.
     * REQUIERE_REVISION no es definitiva: la cola humana decide, o el reintento si cambia la información.
     */
    public static boolean esDefinitivo(EstadoRecomendacion estado) {
        return estado == EVALUADA || estado == DESCARTADA_ALCANCE;
    }
}
