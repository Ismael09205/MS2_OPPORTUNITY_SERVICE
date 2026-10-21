package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;

import java.util.List;
import java.util.Optional;

public interface RecomendacionPersistencePort {

    /**
     * Persiste una recomendación generada por el LLM (cache).
     * Si ya existe para el OCID, la sobreescribe.
     */
    RecomendacionOportunidad guardarOActualizar(RecomendacionOportunidad recomendacion, Double scoreCompuesto);

    /**
     * Busca una recomendación ya generada por su OCID (cache hit).
     */
    Optional<RecomendacionOportunidad> buscarPorOcid(String ocid);

    /**
     * Paginacion sobre oportunidades reales (esOportunidadIbm=true).
     * El usuario es el disparador: offset aumenta a medida que pide mas.
     */
    List<RecomendacionOportunidad> obtenerOportunidadesIbm(int limit, int offset);

    /**
     * Limpia la tabla de recomendaciones para forzar una re-evaluación completa bajo nuevas reglas.
     */
    void eliminarTodas();
}
