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
     * Devuelve los siguientes N OCIDs con mayor score compuesto que aun no tienen
     * recomendacion generada. Usado para lazy generation: Granite solo trabaja
     * cuando el usuario pide mas resultados.
     */
    List<String> obtenerSiguientesAEvaluar(int limit);

    /**
     * Devuelve el total de OCIDs distintos con similitud calculada.
     */
    long contarProcesosConSimilitud();
}
