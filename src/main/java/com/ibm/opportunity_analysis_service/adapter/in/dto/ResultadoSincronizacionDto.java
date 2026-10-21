package com.ibm.opportunity_analysis_service.adapter.in.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoSincronizacionDto {
    private String mensaje;
    private boolean enEjecucion;
    private int totalProcesosSercop;
    private int procesadosExitosos;
    private int alDiaSinCambios;
    private int fallidos;
    private double duracionSegundos;
    private OffsetDateTime fechaFinalizacion;
    @Builder.Default
    private List<ErrorProcesoDto> errores = new ArrayList<>();

    public static ResultadoSincronizacionDto enEjecucion() {
        return ResultadoSincronizacionDto.builder()
                .mensaje("El pipeline de sincronización ya se encuentra en ejecución.")
                .enEjecucion(true)
                .totalProcesosSercop(0)
                .procesadosExitosos(0)
                .alDiaSinCambios(0)
                .fallidos(0)
                .duracionSegundos(0.0)
                .fechaFinalizacion(OffsetDateTime.now())
                .errores(new ArrayList<>())
                .build();
    }
}
