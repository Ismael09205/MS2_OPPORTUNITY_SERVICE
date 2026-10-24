package com.ibm.opportunity_analysis_service.adapter.out.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "recomendacion",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_recomendacion_ocid",
        columnNames = {"ocid"}
    )
)
@Getter
@Setter
public class RecomendacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ocid;

    @Column
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column
    private String comprador;

    @Column(name = "monto_referencial")
    private java.math.BigDecimal montoReferencial;

    @Column(name = "es_oportunidad_ibm", nullable = false)
    private boolean esOportunidadIbm;

    @Column(name = "producto_recomendado")
    private String productoRecomendado;

    @Column(name = "nivel_confianza")
    private String nivelConfianza;

    @Column(columnDefinition = "TEXT")
    private String justificacion;

    @Column(name = "estrategia_comercial", columnDefinition = "TEXT")
    private String estrategiaComercial;

    @Column(name = "score_compuesto", nullable = false)
    private Double scoreCompuesto;

    @Column(name = "fecha_generacion", nullable = false)
    private OffsetDateTime fechaGeneracion;

    /**
     * Origen del registro (Fase 1, tarea 1.1). Guardado como String (VARCHAR) para que el
     * CHECK de la migración pueda validar los valores y un despliegue viejo no rompa ante un
     * valor nuevo. null solo en filas legadas previas a la Fase 1.
     */
    @Column(name = "estado", length = 32)
    private String estado;

    @Column(name = "fecha_evaluacion")
    private OffsetDateTime fechaEvaluacion;

    /**
     * Firma de la información del proceso al abstenerse (REQUIERE_REVISION, Opción C):
     * hash de título+descripción para detectar si llegó información nueva antes de re-evaluar.
     * null en filas que no están (o nunca estuvieron) en la cola de revisión.
     */
    @Column(name = "firma_info_revision", length = 64)
    private String firmaInfoRevision;

    /**
     * Fallos seguidos de evaluación LLM (política de reintento, 2026-10-24). Se incrementa al
     * persistir ERROR_EVALUACION y vuelve a 0 con cualquier veredicto. {@code >= 6} = caso
     * estancado, excluido de la cola de reintentos hasta revisión manual.
     * Ver {@code application.util.PoliticaReintento}.
     */
    @Column(name = "intentos_reintento", nullable = false)
    private Integer intentosReintento;

    /**
     * Próximo intento de evaluación programado (backoff 5m→15m→1h→4h→8h). null = sin
     * reprogramación: o bien no hay fallo, o bien el caso agotó sus intentos.
     */
    @Column(name = "proximo_intento_at")
    private OffsetDateTime proximoIntentoAt;
}
