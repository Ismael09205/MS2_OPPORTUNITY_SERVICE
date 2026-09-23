package com.ibm.opportunity_analysis_service.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class ResultadoFiltroSercop {

    private String ocid;
    private String titulo;
    private String descripcion;
    private String tipoInterno;
    private Set<String> categorias;
}