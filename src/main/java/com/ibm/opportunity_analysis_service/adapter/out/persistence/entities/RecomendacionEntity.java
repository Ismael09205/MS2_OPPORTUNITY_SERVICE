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
}
