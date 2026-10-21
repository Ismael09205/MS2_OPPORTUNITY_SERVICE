package com.ibm.opportunity_analysis_service.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimilitudProcesoProducto {

    private Long id;
    private String ocid;
    private Long productoIbmId;
    private String nombreProducto;
    private Double scoreVectorial;
    private Double scoreCompuesto;
    private String hashContenido;
    private OffsetDateTime fechaCalculo;
}
