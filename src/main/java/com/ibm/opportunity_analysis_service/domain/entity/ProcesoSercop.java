package com.ibm.opportunity_analysis_service.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class ProcesoSercop {

    private Long id;
    private int anio;
    private String comprador;
    private String descripcion;
    private OffsetDateTime fecha;
    private Long identificadorSercop;
    private String localidad;
    private int mes;
    private String metodo;
    private BigDecimal monto;
    private String ocid;
    private BigDecimal presupuesto;
    private String proveedores;
    private String region;
    private String tipoInterno;
    private String titulo;
}