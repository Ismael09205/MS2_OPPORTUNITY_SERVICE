package com.ibm.opportunity_analysis_service.adapter.in.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoPipelineDto {
    private boolean enEjecucion;
    private long totalProcesosSercop;
    private long totalEmbeddingsSercop;
    private long totalProcesosConSimilitud;
    private long totalRecomendaciones;
    private boolean catalogoIbmListo;
    private long totalProductosIbm;
    private ResultadoSincronizacionDto ultimaSincronizacion;
}
