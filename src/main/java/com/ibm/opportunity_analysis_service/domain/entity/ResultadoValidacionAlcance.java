package com.ibm.opportunity_analysis_service.domain.entity;

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
public class ResultadoValidacionAlcance {

    /**
     * Indica si el proceso supera los filtros duros y cae dentro del alcance tecnológico de IBM.
     */
    private boolean esElegible;

    /**
     * Motivo o razón técnica/comercial en caso de descarte o alerta de alcance.
     */
    private String motivoDescarte;

    /**
     * Indica si se detectaron términos o requerimientos explícitos de tecnología enterprise.
     */
    private boolean tienePalabrasEnterprise;

    /**
     * Puntaje de bonificación o factor de ajuste por alineación de alcance (0.0 a 0.15).
     */
    private double bonusAlcance;

    public static ResultadoValidacionAlcance elegible(boolean tienePalabrasEnterprise, double bonusAlcance) {
        return ResultadoValidacionAlcance.builder()
                .esElegible(true)
                .motivoDescarte(null)
                .tienePalabrasEnterprise(tienePalabrasEnterprise)
                .bonusAlcance(bonusAlcance)
                .build();
    }

    public static ResultadoValidacionAlcance descartado(String motivo) {
        return ResultadoValidacionAlcance.builder()
                .esElegible(false)
                .motivoDescarte(motivo)
                .tienePalabrasEnterprise(false)
                .bonusAlcance(0.0)
                .build();
    }
}
