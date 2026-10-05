package com.ibm.opportunity_analysis_service.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
@Getter
@Setter
public class EmbeddingCandidatoTecnologico {

    private Long id;
    private String ocid;
    private String modelo;
    private String textoEmbedding;
    private float[] embedding;
    private OffsetDateTime fechaGeneracion;
}