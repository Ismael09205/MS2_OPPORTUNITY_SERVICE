package com.ibm.opportunity_analysis_service.adapter.in.dto;

import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Datos crudos para que Bob (u otra capa de inteligencia externa) razone
 * sobre la afinidad entre un proceso SERCOP y los productos IBM candidatos.
 * No contiene evaluación ni decisión — eso lo hace el evaluador externo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandidatosRecomendacionDto {

    private ProcesoSercop proceso;
    private List<CandidatoConDetalle> candidatos;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidatoConDetalle {
        private Long productoIbmId;
        private String nombre;
        private String descripcion;
        private List<String> capacidades;
        private List<String> tecnologias;
        private double similitud;
    }
}
