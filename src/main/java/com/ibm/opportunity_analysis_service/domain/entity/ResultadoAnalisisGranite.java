package com.ibm.opportunity_analysis_service.domain.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResultadoAnalisisGranite {

    private String ocid;
    private boolean aprobado;
    private String categoria;
    private String justificacion;
}

