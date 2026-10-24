package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;

import java.util.List;
import java.util.Optional;

public interface RecomendacionPersistencePort {

    /**
     * Persiste una recomendación generada por el LLM (cache).
     * Si ya existe para el OCID, la sobreescribe.
     * {@code scoreCompuesto = null} conserva el score existente (caso de la resolución humana
     * de la cola REQUIERE_REVISION, que no debe destruir el orden de la lista principal).
     */
    RecomendacionOportunidad guardarOActualizar(RecomendacionOportunidad recomendacion, Double scoreCompuesto);

    /**
     * Busca una recomendación ya generada por su OCID (cache hit).
     */
    Optional<RecomendacionOportunidad> buscarPorOcid(String ocid);

    /**
     * Paginacion de recomendaciones EVALUADAS y con esOportunidadIbm=true (decisión A + D).
     * El usuario es el disparador: offset aumenta a medida que pide mas.
     */
    List<RecomendacionOportunidad> obtenerOportunidadesIbm(int limit, int offset);

    /**
     * Tarea 1.7: preselecciones vectoriales y errores de evaluación, expuestos aparte de la
     * lista principal (decisión A). Nunca deben presentarse como veredicto.
     */
    List<RecomendacionOportunidad> obtenerPendientes(int limit, int offset);

    /**
     * Cola humana de revisión: casos REQUIERE_REVISION (el LLM se abstuvo) con su causa,
     * ordenados por abstención más antigua primero.
     */
    List<RecomendacionOportunidad> obtenerColaRevision(int limit, int offset);

    /**
     * Limpia la tabla de recomendaciones para forzar una re-evaluación completa bajo nuevas reglas.
     */
    void eliminarTodas();
}
