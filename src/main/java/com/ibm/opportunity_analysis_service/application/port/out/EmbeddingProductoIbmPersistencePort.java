package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoSimilitudIbm;

import java.util.List;
import java.util.Optional;

public interface EmbeddingProductoIbmPersistencePort {

    EmbeddingProductoIbm guardar(EmbeddingProductoIbm embeddingProductoIbm);

    List<EmbeddingProductoIbm> guardarTodos(List<EmbeddingProductoIbm> embeddings);

    List<EmbeddingProductoIbm> buscarTodos();

    Optional<EmbeddingProductoIbm> buscarPorProductoYModelo(Long productoIbmId, String modelo);

    List<ResultadoSimilitudIbm> buscarSimilares(float[] embedding, int limite);
}