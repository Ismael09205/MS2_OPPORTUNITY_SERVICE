package com.ibm.opportunity_analysis_service.adapter.out.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "proceso_contratacion")
@Getter
@Setter
public class ProcesoContratacionEntity {

        @Id
        private Long id;
        @Column(name = "anio", nullable = false)
        private int anio;
        private String comprador;
        @Column(columnDefinition = "TEXT")
        private String descripcion;
        private OffsetDateTime fecha;
        @Column(name = "identificador_sercop")
        private Long identificadorSercop;
        private String localidad;
        @Column(name = "mes", nullable = false)
        private int mes;
        private String metodo;
        private BigDecimal monto;
        @Column(nullable = false)
        private String ocid;
        private BigDecimal presupuesto;
        @Column(columnDefinition = "TEXT")
        private String proveedores;
        private String region;
        @Column(name = "tipo_interno", columnDefinition = "TEXT")
        private String tipoInterno;
        private String titulo;

}
