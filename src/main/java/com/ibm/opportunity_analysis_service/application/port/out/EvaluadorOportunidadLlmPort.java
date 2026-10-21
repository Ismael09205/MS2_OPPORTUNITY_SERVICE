package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;

import java.util.List;

public interface EvaluadorOportunidadLlmPort {

    RecomendacionOportunidad evaluarOportunidad(
            ProcesoSercop proceso,
            List<ProductoIbm> candidatosTop,
            List<Double> scoresVectoriales
    );

    default RecomendacionOportunidad evaluarOportunidad(ProcesoSercop proceso, List<ProductoIbm> candidatosTop) {
        return evaluarOportunidad(proceso, candidatosTop, List.of());
    }
}
