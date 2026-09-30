package com.ibm.opportunity_analysis_service.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class ProductoIbm {

    private Long id;
    private String nombre;
    private String descripcion;
    private List<String> categorias;
    private List<String> capacidades;
    private List<String> casosUso;
    private List<String> tecnologias;
    private List<String> integraciones;
    private List<String> tags;
    private List<String> keywords;
    private String sourceUrl;
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaActualizacion;
}