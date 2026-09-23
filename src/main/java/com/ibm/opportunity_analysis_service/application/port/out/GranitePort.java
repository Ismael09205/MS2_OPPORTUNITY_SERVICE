package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoAnalisisGranite;

import java.util.List;

public interface GranitePort {
    List<ResultadoAnalisisGranite> analizar(List<ProcesoSercop> procesos);
}