package com.ibm.opportunity_analysis_service.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class CandidatoTecnologico {

    private Long id;
    private String ocid;
    private String titulo;
    private String descripcion;
    private OffsetDateTime fechaDeteccion;
    private String estado;
}