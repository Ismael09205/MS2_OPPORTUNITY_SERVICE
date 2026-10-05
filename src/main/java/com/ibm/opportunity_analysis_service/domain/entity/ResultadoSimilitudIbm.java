package com.ibm.opportunity_analysis_service.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResultadoSimilitudIbm {

    private Long productoIbmId;
    private String nombreProducto;
    private Double similitud;
}