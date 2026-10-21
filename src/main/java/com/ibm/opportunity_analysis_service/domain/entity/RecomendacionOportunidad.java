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
}
