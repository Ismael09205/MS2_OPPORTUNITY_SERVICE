package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.SimilitudProcesoProducto;

import java.util.List;
import java.util.Optional;

public interface SimilitudPersistencePort {

    /**
     * Inserta o actualiza (upsert) la similitud entre un proceso SERCOP y un producto IBM.
     */
    SimilitudProcesoProducto guardarOActualizar(SimilitudProcesoProducto similitud);

    /**
     * Devuelve el hash de contenido guardado para el OCID, o empty si nunca se calculó.
     * Permite comparar con el hash actual del proceso para decidir si recalcular.
     */
    Optional<String> obtenerHashPorOcid(String ocid);

    /**
     * Devuelve todas las similitudes asociadas a un OCID concreto.
     */
    List<SimilitudProcesoProducto> buscarPorOcid(String ocid);

    /**
     * Elimina todas las similitudes persistidas de un OCID (tarea 1.4): cuando el proceso deja de
     * ser elegible, sus similitudes huérfanas ya no deben sostener una preselección.
     *
     * @return número de filas eliminadas
     */
    int eliminarPorOcid(String ocid);

    /**
     * Devuelve los siguientes N OCIDs con mayor score compuesto que aun no tienen
     * recomendacion generada. Usado para lazy generation: Granite solo trabaja
     * cuando el usuario pide mas resultados.
     */
    List<String> obtenerSiguientesAEvaluar(int limit);

    /**
     * Tarea 1.5: OCIDs pendientes de evaluación profunda (sin fila, PRESELECCION_VECTORIAL
     * o ERROR_EVALUACION). Es la cola que usa el scheduler para reintentar fallos de LLM.
     */
    List<String> obtenerPendientesDeEvaluacion(int limit);

    /**
     * Tarea 1.8: total de OCIDs pendientes de evaluación (para exponer `pendientesCount`).
     */
    long contarPendientesDeEvaluacion();

    /**
     * Devuelve el total de OCIDs distintos con similitud calculada.
     */
    long contarProcesosConSimilitud();
}
