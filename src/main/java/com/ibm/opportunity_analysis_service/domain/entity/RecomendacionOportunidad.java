package com.ibm.opportunity_analysis_service.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecomendacionOportunidad {

    private String ocid;
    private String titulo;
    private String descripcion;
    private String comprador;
    private java.math.BigDecimal montoReferencial;
    private boolean esOportunidadIbm;
    private String productoRecomendado;
    private String nivelConfianza; // ALTO, MEDIO, BAJO, N/A
    private String justificacion;
    private String estrategiaComercial;

    /**
     * Origen del registro (Fase 1, tarea 1.1): distingue una preselección vectorial sin evaluar
     * de una evaluación definitiva. null solo en filas legadas previas a la Fase 1.
     */
    private EstadoRecomendacion estado;

    /**
     * Firma de la información del proceso en el momento de abstenerse (REQUIERE_REVISION):
     * hash SHA-256 (hex, primeros 16 chars) de título + descripción normalizados. Es la base de
     * la Opción C aprobada: si la firma actual difiere de la guardada, la información cambió
     * (p.ej. llegó el pliego) y el caso se re-evalúa solo; si es igual, se queda en la cola.
     */
    private String firmaInfoRevision;

    /** Momento en que se resolvió el estado (evaluación, descarte o error). null en preselecciones. */
    private java.time.OffsetDateTime fechaEvaluacion;

    /**
     * Fallos seguidos de evaluación LLM (política de reintento, 2026-10-24). 0 = sin fallos;
     * se incrementa al persistir ERROR_EVALUACION y vuelve a 0 con cualquier veredicto.
     * {@code >= PoliticaReintento.MAX_INTENTOS} = caso estancado (WARN y sin reprogramación).
     */
    private int intentosReintento = 0;

    /**
     * Próximo reintento programado (backoff 5m→15m→1h→4h→8h). null = sin reprogramación
     * pendiente (sin fallo, o caso estancado que agotó sus intentos).
     */
    private java.time.OffsetDateTime proximoIntentoAt;
}
