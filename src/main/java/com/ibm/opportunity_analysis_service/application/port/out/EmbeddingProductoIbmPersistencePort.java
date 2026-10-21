package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoSimilitudIbm;

import java.util.List;

public interface EmbeddingProductoIbmPersistencePort {

    List<EmbeddingProductoIbm> guardarTodos(List<EmbeddingProductoIbm> embeddings);

    List<EmbeddingProductoIbm> buscarTodos();

    List<ResultadoSimilitudIbm> buscarSimilares(float[] embedding, int limite);
}
